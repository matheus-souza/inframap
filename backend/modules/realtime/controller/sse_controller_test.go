package controller_test

import (
	"context"
	"errors"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
	"time"

	"github.com/matheussouza/inframap/internal/platform/eventbus"
	"github.com/matheussouza/inframap/modules/realtime/controller"
	"github.com/matheussouza/inframap/modules/realtime/gateway"
)

func waitForSubscriber(gw *gateway.Gateway, expectedCount int, timeout time.Duration) bool {
	deadline := time.Now().Add(timeout)
	for time.Now().Before(deadline) {
		if gw.ActiveSubscribersCount() == expectedCount {
			return true
		}
		time.Sleep(5 * time.Millisecond)
	}
	return false
}

func TestSSEController_StreamEvents(t *testing.T) {
	bus := eventbus.NewInMemoryEventBus(1, 10)
	defer func() {
		_ = bus.Close()
	}()

	gw := gateway.NewGateway(bus)
	ctrl := controller.NewSSEController(gw)

	mux := http.NewServeMux()
	mux.HandleFunc("GET /api/v1/events/stream", ctrl.StreamEvents)

	t.Run("Establish SSE Stream & Receive Events", func(t *testing.T) {
		ctx, cancel := context.WithCancel(context.Background())
		req := httptest.NewRequest(http.MethodGet, "/api/v1/events/stream", nil).WithContext(ctx)
		rec := httptest.NewRecorder()

		done := make(chan bool)
		go func() {
			mux.ServeHTTP(rec, req)
			done <- true
		}()

		if !waitForSubscriber(gw, 1, 1*time.Second) {
			t.Fatal("timeout waiting for subscriber to connect")
		}

		gw.Broadcast(gateway.EventMessage{
			ID:    "evt-test-1",
			Event: "topology.updated",
			Data:  map[string]string{"link": "created"},
		})

		time.Sleep(20 * time.Millisecond)
		cancel()

		select {
		case <-done:
		case <-time.After(1 * time.Second):
			t.Fatal("timeout waiting for handler to complete")
		}

		body := rec.Body.String()
		if !strings.Contains(body, ": connected") {
			t.Errorf("expected connected ack, got %s", body)
		}
		if !strings.Contains(body, "event: topology.updated") {
			t.Errorf("expected topology.updated event in SSE stream, got %s", body)
		}
	})

	t.Run("Replay Events via Last-Event-ID Header", func(t *testing.T) {
		m1 := gateway.EventMessage{ID: "hdr-100", Event: "e1", Data: "d1"}
		m2 := gateway.EventMessage{ID: "hdr-101", Event: "e2", Data: "d2"}
		gw.Broadcast(m1)
		gw.Broadcast(m2)

		ctx, cancel := context.WithCancel(context.Background())
		req := httptest.NewRequest(http.MethodGet, "/api/v1/events/stream", nil).WithContext(ctx)
		req.Header.Set("Last-Event-ID", "hdr-100")
		rec := httptest.NewRecorder()

		done := make(chan bool)
		go func() {
			mux.ServeHTTP(rec, req)
			done <- true
		}()

		if !waitForSubscriber(gw, 1, 1*time.Second) {
			t.Fatal("timeout waiting for subscriber to connect")
		}

		time.Sleep(20 * time.Millisecond)
		cancel()

		select {
		case <-done:
		case <-time.After(1 * time.Second):
			t.Fatal("timeout waiting for handler to complete")
		}

		body := rec.Body.String()
		if !strings.Contains(body, "id: hdr-101") {
			t.Errorf("expected replayed hdr-101 in stream, got %s", body)
		}
	})

	t.Run("Replay Events via Query Parameter", func(t *testing.T) {
		m1 := gateway.EventMessage{ID: "qry-100", Event: "e1", Data: "d1"}
		m2 := gateway.EventMessage{ID: "qry-101", Event: "e2", Data: "d2"}
		gw.Broadcast(m1)
		gw.Broadcast(m2)

		ctx, cancel := context.WithCancel(context.Background())
		req := httptest.NewRequest(http.MethodGet, "/api/v1/events/stream?last_event_id=qry-100", nil).WithContext(ctx)
		rec := httptest.NewRecorder()

		done := make(chan bool)
		go func() {
			mux.ServeHTTP(rec, req)
			done <- true
		}()

		if !waitForSubscriber(gw, 1, 1*time.Second) {
			t.Fatal("timeout waiting for subscriber to connect")
		}

		time.Sleep(20 * time.Millisecond)
		cancel()

		select {
		case <-done:
		case <-time.After(1 * time.Second):
			t.Fatal("timeout waiting for handler to complete")
		}

		body := rec.Body.String()
		if !strings.Contains(body, "id: qry-101") {
			t.Errorf("expected replayed qry-101 in stream, got %s", body)
		}
	})

	t.Run("Non-Flusher Response Writer Failure", func(t *testing.T) {
		req := httptest.NewRequest(http.MethodGet, "/api/v1/events/stream", nil)
		nonFlusherWriter := &nonFlusherResponseWriter{httptest.NewRecorder()}
		mux.ServeHTTP(nonFlusherWriter, req)

		if nonFlusherWriter.rec.Code != http.StatusBadRequest {
			t.Errorf("expected 400 Bad Request for non-flusher, got %d", nonFlusherWriter.rec.Code)
		}
	})

	t.Run("Initial Write Failure Returns Immediately", func(_ *testing.T) {
		req := httptest.NewRequest(http.MethodGet, "/api/v1/events/stream", nil)
		failingWriter := &failingResponseWriter{rec: httptest.NewRecorder(), failOnInit: true}
		mux.ServeHTTP(failingWriter, req)
	})

	t.Run("Replay Write Failure Returns Immediately", func(_ *testing.T) {
		m1 := gateway.EventMessage{ID: "rfail-100", Event: "e1"}
		m2 := gateway.EventMessage{ID: "rfail-101", Event: "e2"}
		gw.Broadcast(m1)
		gw.Broadcast(m2)

		req := httptest.NewRequest(http.MethodGet, "/api/v1/events/stream?last_event_id=rfail-100", nil)
		failingWriter := &failingResponseWriter{rec: httptest.NewRecorder(), failOnReplay: true}
		mux.ServeHTTP(failingWriter, req)
	})

	t.Run("Subscriber Event Write Failure Returns Immediately", func(t *testing.T) {
		ctx, cancel := context.WithCancel(context.Background())
		defer cancel()

		req := httptest.NewRequest(http.MethodGet, "/api/v1/events/stream", nil).WithContext(ctx)
		failingWriter := &failingResponseWriter{rec: httptest.NewRecorder(), failOnEvent: true}

		done := make(chan bool)
		go func() {
			mux.ServeHTTP(failingWriter, req)
			done <- true
		}()

		if !waitForSubscriber(gw, 1, 1*time.Second) {
			t.Fatal("timeout waiting for subscriber to connect")
		}

		gw.Broadcast(gateway.EventMessage{ID: "event-fail-1", Event: "e1"})

		select {
		case <-done:
		case <-time.After(1 * time.Second):
			t.Fatal("timeout waiting for handler to complete on event write failure")
		}
	})
}

type nonFlusherResponseWriter struct {
	rec *httptest.ResponseRecorder
}

func (n *nonFlusherResponseWriter) Header() http.Header { return n.rec.Header() }
func (n *nonFlusherResponseWriter) Write(b []byte) (int, error) { return n.rec.Write(b) }
func (n *nonFlusherResponseWriter) WriteHeader(statusCode int) { n.rec.WriteHeader(statusCode) }

type failingResponseWriter struct {
	rec          *httptest.ResponseRecorder
	failOnInit   bool
	failOnReplay bool
	failOnEvent  bool
	writes       int
}

func (f *failingResponseWriter) Header() http.Header { return f.rec.Header() }

func (f *failingResponseWriter) Write(b []byte) (int, error) {
	f.writes++
	if f.failOnInit && f.writes == 1 {
		return 0, errors.New("initial write failed")
	}
	if f.failOnReplay && f.writes == 2 {
		return 0, errors.New("replay write failed")
	}
	if f.failOnEvent && f.writes >= 2 {
		return 0, errors.New("event write failed")
	}
	return f.rec.Write(b)
}

func (f *failingResponseWriter) WriteHeader(statusCode int) { f.rec.WriteHeader(statusCode) }
func (f *failingResponseWriter) Flush()                     {}
