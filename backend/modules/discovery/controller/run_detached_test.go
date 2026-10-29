package controller_test

import (
	"context"
	"log/slog"
	"net/http"
	"net/http/httptest"
	"testing"
	"time"

	"github.com/google/uuid"
	"github.com/matheussouza/inframap/internal/platform/eventbus"
	"github.com/matheussouza/inframap/modules/discovery/controller"
	"github.com/matheussouza/inframap/modules/discovery/dto"
	"github.com/matheussouza/inframap/modules/discovery/scheduler"
)

// blockingRunUseCase holds every TriggerRun until release is closed, and reports on finished
// whether the run's own context was still alive when it ended.
type blockingRunUseCase struct {
	*mockDiscoveryUseCase
	started  chan struct{}
	release  chan struct{}
	finished chan error
}

func (b *blockingRunUseCase) TriggerRun(ctx context.Context, idStr string) (*dto.DiscoverySourceResponse, error) {
	b.started <- struct{}{}
	select {
	case <-b.release:
	case <-ctx.Done():
	}
	b.finished <- ctx.Err()
	if ctx.Err() != nil {
		return nil, ctx.Err()
	}
	return b.mockDiscoveryUseCase.TriggerRun(ctx, idStr)
}

type noopStatusUpdater struct{}

func (noopStatusUpdater) UpdateSourceStatus(_ context.Context, _ uuid.UUID, _ string) (*dto.DiscoverySourceResponse, error) {
	return &dto.DiscoverySourceResponse{}, nil
}

func newDetachedRunFixture(t *testing.T) (*blockingRunUseCase, *http.ServeMux, uuid.UUID) {
	t.Helper()
	uc := &blockingRunUseCase{
		mockDiscoveryUseCase: &mockDiscoveryUseCase{},
		started:              make(chan struct{}, 4),
		release:              make(chan struct{}),
		finished:             make(chan error, 4),
	}
	src, err := uc.CreateSource(context.Background(), &dto.CreateDiscoverySourceRequest{Name: "Detached", Type: "icmp_sweep"})
	if err != nil {
		t.Fatalf("seed source: %v", err)
	}

	bus := eventbus.NewInMemoryEventBus(1, 16)
	t.Cleanup(func() { _ = bus.Close() })
	runner := scheduler.New(uc, noopStatusUpdater{}, bus, slog.Default())
	if err := runner.Start(context.Background()); err != nil {
		t.Fatalf("start scheduler: %v", err)
	}
	t.Cleanup(runner.Stop)

	ctrl := controller.NewDiscoveryController(uc)
	ctrl.WithRunner(runner)
	mux := http.NewServeMux()
	mux.HandleFunc("POST /api/v1/discovery/sources/{id}/run", ctrl.TriggerRun)
	return uc, mux, src.ID
}

func waitSignal[T any](t *testing.T, ch <-chan T, what string) T {
	t.Helper()
	select {
	case v := <-ch:
		return v
	case <-time.After(3 * time.Second):
		t.Fatalf("%s did not happen within 3s", what)
	}
	var zero T
	return zero
}

func TestTriggerRun_RunOutlivesADisconnectedClient(t *testing.T) {
	uc, mux, id := newDetachedRunFixture(t)

	reqCtx, disconnect := context.WithCancel(context.Background())
	req := httptest.NewRequest(http.MethodPost, "/api/v1/discovery/sources/"+id.String()+"/run", nil).WithContext(reqCtx)
	served := make(chan struct{})
	go func() {
		mux.ServeHTTP(httptest.NewRecorder(), req)
		close(served)
	}()

	waitSignal(t, uc.started, "run start")
	disconnect()
	waitSignal(t, served, "handler return after the client left")

	close(uc.release)
	if runErr := waitSignal(t, uc.finished, "run end"); runErr != nil {
		t.Fatalf("run was cancelled with the request: %v", runErr)
	}
}

func TestTriggerRun_SecondRunOfARunningSourceAnswers409(t *testing.T) {
	uc, mux, id := newDetachedRunFixture(t)

	first := httptest.NewRecorder()
	served := make(chan struct{})
	go func() {
		mux.ServeHTTP(first, httptest.NewRequest(http.MethodPost, "/api/v1/discovery/sources/"+id.String()+"/run", nil))
		close(served)
	}()
	waitSignal(t, uc.started, "first run start")

	second := httptest.NewRecorder()
	mux.ServeHTTP(second, httptest.NewRequest(http.MethodPost, "/api/v1/discovery/sources/"+id.String()+"/run", nil))
	if second.Code != http.StatusConflict {
		t.Fatalf("second run: expected 409, got %d: %s", second.Code, second.Body.String())
	}

	close(uc.release)
	waitSignal(t, served, "first run response")
	if first.Code != http.StatusOK {
		t.Fatalf("first run: expected 200, got %d: %s", first.Code, first.Body.String())
	}
}

func TestTriggerRun_WithRunnerRejectsAnInvalidUUID(t *testing.T) {
	_, mux, _ := newDetachedRunFixture(t)

	rec := httptest.NewRecorder()
	mux.ServeHTTP(rec, httptest.NewRequest(http.MethodPost, "/api/v1/discovery/sources/not-a-uuid/run", nil))
	if rec.Code != http.StatusBadRequest {
		t.Fatalf("expected 400, got %d", rec.Code)
	}
}

func TestTriggerRun_WithRunnerAnswers404ForAnUnknownSource(t *testing.T) {
	uc, mux, _ := newDetachedRunFixture(t)
	close(uc.release)

	rec := httptest.NewRecorder()
	mux.ServeHTTP(rec, httptest.NewRequest(http.MethodPost, "/api/v1/discovery/sources/"+uuid.New().String()+"/run", nil))
	if rec.Code != http.StatusNotFound {
		t.Fatalf("expected 404, got %d: %s", rec.Code, rec.Body.String())
	}
}

func TestTriggerRun_LongRunOutlivesTheServerWriteTimeout(t *testing.T) {
	uc, mux, id := newDetachedRunFixture(t)
	srv := httptest.NewUnstartedServer(mux)
	srv.Config.WriteTimeout = 200 * time.Millisecond
	srv.Start()
	defer srv.Close()

	type result struct {
		status int
		err    error
	}
	done := make(chan result, 1)
	go func() {
		resp, err := http.Post(srv.URL+"/api/v1/discovery/sources/"+id.String()+"/run", "application/json", nil)
		if err != nil {
			done <- result{err: err}
			return
		}
		_ = resp.Body.Close()
		done <- result{status: resp.StatusCode}
	}()

	waitSignal(t, uc.started, "run start")
	time.Sleep(500 * time.Millisecond) // past the server's WriteTimeout
	close(uc.release)

	res := waitSignal(t, done, "run response")
	if res.err != nil {
		t.Fatalf("a run longer than the server's WriteTimeout failed on the client: %v", res.err)
	}
	if res.status != http.StatusOK {
		t.Fatalf("expected 200, got %d", res.status)
	}
}
