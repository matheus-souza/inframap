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
}
