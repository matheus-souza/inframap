package gateway_test

import (
	"context"
	"testing"
	"time"

	"github.com/matheussouza/inframap/internal/platform/eventbus"
	"github.com/matheussouza/inframap/modules/realtime/gateway"
)

func TestGateway(t *testing.T) {
	bus := eventbus.NewInMemoryEventBus(1, 10)
	defer func() {
		_ = bus.Close()
	}()

	gw := gateway.NewGateway(bus)
	ctx, cancel := context.WithTimeout(context.Background(), 2*time.Second)
	defer cancel()

	if err := gw.Start(ctx); err != nil {
		t.Fatalf("expected nil error on Gateway Start, got %v", err)
	}

	t.Run("Subscribe Unsubscribe Cycle", func(t *testing.T) {
		ch, unsub := gw.Subscribe()
		if gw.ActiveSubscribersCount() != 1 {
			t.Errorf("expected 1 active subscriber, got %d", gw.ActiveSubscribersCount())
		}

		unsub()
		if gw.ActiveSubscribersCount() != 0 {
			t.Errorf("expected 0 active subscribers after unsub, got %d", gw.ActiveSubscribersCount())
		}

		// Double unsub should be safe
		unsub()

		// Channel should be closed
		_, ok := <-ch
		if ok {
			t.Error("expected channel to be closed")
		}
	})

	t.Run("Broadcast and EventBus Forwarding", func(t *testing.T) {
		ch, unsub := gw.Subscribe()
		defer unsub()

		_ = bus.Publish(ctx, eventbus.NewBaseEvent("discovery.progress", map[string]interface{}{"progress": 50}))

		select {
		case msg := <-ch:
			if msg.Event != "discovery.progress" {
				t.Errorf("expected event discovery.progress, got %s", msg.Event)
			}
		case <-time.After(1 * time.Second):
			t.Error("timeout waiting for EventBus forwarded message")
		}
	})

	t.Run("GetEventsAfter Delegates to RingBuffer", func(t *testing.T) {
		m1 := gateway.EventMessage{ID: "gw-id-1", Event: "e1"}
		m2 := gateway.EventMessage{ID: "gw-id-2", Event: "e2"}
		gw.Broadcast(m1)
		gw.Broadcast(m2)

		replayed := gw.GetEventsAfter("gw-id-1")
		if len(replayed) != 1 || replayed[0].ID != "gw-id-2" {
			t.Errorf("expected 1 replayed event gw-id-2, got %v", replayed)
		}
	})

	t.Run("Start with Nil EventBus", func(t *testing.T) {
		nilGw := gateway.NewGateway(nil)
		if err := nilGw.Start(ctx); err != nil {
			t.Errorf("expected nil error when starting Gateway with nil bus, got %v", err)
		}
	})
}
