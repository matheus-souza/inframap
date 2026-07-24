package eventbus_test

import (
	"context"
	"errors"
	"sync"
	"sync/atomic"
	"testing"
	"time"

	"github.com/matheussouza/inframap/internal/platform/eventbus"
)

func TestEventBus_PublishSubscribe(t *testing.T) {
	bus := eventbus.NewInMemoryEventBus(5, 100)
	defer bus.Close()

	var receivedCount int32
	var wg sync.WaitGroup
	wg.Add(1)

	err := bus.Subscribe("device.created", func(_ context.Context, _ eventbus.DomainEvent) error {
		atomic.AddInt32(&receivedCount, 1)
		wg.Done()
		return nil
	})
	if err != nil {
		t.Fatalf("failed to subscribe: %v", err)
	}

	event := eventbus.NewBaseEvent("device.created", map[string]string{"hostname": "server-01"})
	err = bus.Publish(context.Background(), event)
	if err != nil {
		t.Fatalf("failed to publish: %v", err)
	}

	wg.Wait()

	if atomic.LoadInt32(&receivedCount) != 1 {
		t.Errorf("expected 1 received event, got %d", atomic.LoadInt32(&receivedCount))
	}
}

func TestEventBus_PanicRecoveryInHandler(t *testing.T) {
	bus := eventbus.NewInMemoryEventBus(2, 10)
	defer bus.Close()

	var wg sync.WaitGroup
	wg.Add(1)

	// Handler that panics
	_ = bus.Subscribe("test.panic", func(_ context.Context, _ eventbus.DomainEvent) error {
		defer wg.Done()
		panic("handler panic testing recovery")
	})

	event := eventbus.NewBaseEvent("test.panic", "data")
	err := bus.Publish(context.Background(), event)
	if err != nil {
		t.Fatalf("publish failed: %v", err)
	}

	wg.Wait()
	// Bus must remain operational after worker panic recovery
	time.Sleep(50 * time.Millisecond)
}

func TestEventBus_WildcardSubscriber(t *testing.T) {
	bus := eventbus.NewInMemoryEventBus(2, 10)
	defer bus.Close()

	var receivedCount int32
	var wg sync.WaitGroup
	wg.Add(2)

	_ = bus.Subscribe("*", func(_ context.Context, _ eventbus.DomainEvent) error {
		atomic.AddInt32(&receivedCount, 1)
		wg.Done()
		return nil
	})

	_ = bus.Publish(context.Background(), eventbus.NewBaseEvent("evt.one", "1"))
	_ = bus.Publish(context.Background(), eventbus.NewBaseEvent("evt.two", "2"))

	wg.Wait()

	if atomic.LoadInt32(&receivedCount) != 2 {
		t.Errorf("expected 2 wildcard events received, got %d", atomic.LoadInt32(&receivedCount))
	}
}

func TestEventBus_BackpressureOverflow(t *testing.T) {
	bus := eventbus.NewInMemoryEventBus(1, 1)
	defer bus.Close()

	blockCh := make(chan struct{})
	defer close(blockCh)

	// Subscriber blocks worker
	_ = bus.Subscribe("overflow.test", func(_ context.Context, _ eventbus.DomainEvent) error {
		<-blockCh
		return nil
	})

	// First event is picked up by worker and blocks in handler
	_ = bus.Publish(context.Background(), eventbus.NewBaseEvent("overflow.test", "1"))
	time.Sleep(10 * time.Millisecond)

	// Second event fills the channel buffer of size 1
	_ = bus.Publish(context.Background(), eventbus.NewBaseEvent("overflow.test", "2"))

	// Third publish should overflow non-blockingly returning ErrBufferFull
	err := bus.Publish(context.Background(), eventbus.NewBaseEvent("overflow.test", "3"))
	if !errors.Is(err, eventbus.ErrBufferFull) {
		t.Errorf("expected ErrBufferFull on channel overflow, got %v", err)
	}
}
