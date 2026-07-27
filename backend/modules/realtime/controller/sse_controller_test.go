package controller_test

import (
	"context"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
	"time"

	"github.com/matheussouza/inframap/internal/platform/eventbus"
	"github.com/matheussouza/inframap/modules/realtime/controller"
	"github.com/matheussouza/inframap/modules/realtime/gateway"
)

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

		// Give connection time to initialize
		time.Sleep(50 * time.Millisecond)

		gw.Broadcast(gateway.EventMessage{
			ID:    "evt-test-1",
			Event: "topology.updated",
			Data:  map[string]string{"link": "created"},
		})

		time.Sleep(50 * time.Millisecond)
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

	t.Run("Replay Events via Last-Event-ID", func(t *testing.T) {
		m1 := gateway.EventMessage{ID: "evt-100", Event: "e1", Data: "d1"}
		m2 := gateway.EventMessage{ID: "evt-101", Event: "e2", Data: "d2"}
		gw.Broadcast(m1)
		gw.Broadcast(m2)

		ctx, cancel := context.WithCancel(context.Background())
		req := httptest.NewRequest(http.MethodGet, "/api/v1/events/stream?last_event_id=evt-100", nil).WithContext(ctx)
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
			t.Fatal("timeout waiting for handler to complete")
		}

		body := rec.Body.String()
		if !strings.Contains(body, "id: evt-101") {
			t.Errorf("expected replayed evt-101 in stream, got %s", body)
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
}

type nonFlusherResponseWriter struct {
	rec *httptest.ResponseRecorder
}

func (n *nonFlusherResponseWriter) Header() http.Header { return n.rec.Header() }
func (n *nonFlusherResponseWriter) Write(b []byte) (int, error) { return n.rec.Write(b) }
func (n *nonFlusherResponseWriter) WriteHeader(statusCode int) { n.rec.WriteHeader(statusCode) }
