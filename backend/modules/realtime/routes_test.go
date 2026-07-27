package realtime_test

import (
	"context"
	"net/http"
	"net/http/httptest"
	"testing"
	"time"

	"github.com/matheussouza/inframap/internal/platform/eventbus"
	"github.com/matheussouza/inframap/modules/realtime"
	"github.com/matheussouza/inframap/modules/realtime/controller"
	"github.com/matheussouza/inframap/modules/realtime/gateway"
)

func TestRegisterRoutes(t *testing.T) {
	bus := eventbus.NewInMemoryEventBus(1, 10)
	defer func() {
		_ = bus.Close()
	}()

	gw := gateway.NewGateway(bus)
	ctrl := controller.NewSSEController(gw)
	mux := http.NewServeMux()

	realtime.RegisterRoutes(mux, ctrl)

	ctx, cancel := context.WithCancel(context.Background())
	req := httptest.NewRequest(http.MethodGet, "/api/v1/events/stream", nil).WithContext(ctx)
	rec := httptest.NewRecorder()

	done := make(chan bool)
	go func() {
		mux.ServeHTTP(rec, req)
		done <- true
	}()

	time.Sleep(50 * time.Millisecond)
	cancel()

	select {
	case <-done:
	case <-time.After(1 * time.Second):
		t.Fatal("timeout waiting for route test")
	}

	if rec.Header().Get("Content-Type") != "text/event-stream" {
		t.Errorf("expected Content-Type text/event-stream, got %s", rec.Header().Get("Content-Type"))
	}
}
