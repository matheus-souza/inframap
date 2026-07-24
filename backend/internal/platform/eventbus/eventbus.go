package eventbus

import (
	"context"
	"errors"
	"fmt"
	"log"
	"sync"
)

var (
	ErrBufferFull = errors.New("event bus buffer full: event dropped due to backpressure")
	ErrBusClosed  = errors.New("event bus is closed")
)

type EventHandler func(ctx context.Context, event DomainEvent) error

type EventBus interface {
	Publish(ctx context.Context, event DomainEvent) error
	Subscribe(eventType string, handler EventHandler) error
	Close() error
}

type InMemoryEventBus struct {
	mu          sync.RWMutex
	subscribers map[string][]EventHandler
	eventChan   chan DomainEvent
	workers     int
	wg          sync.WaitGroup
	ctx         context.Context
	cancel      context.CancelFunc
	closed      bool
}

const DefaultWorkers = 5

func NewInMemoryEventBus(workers int, bufferSize int) *InMemoryEventBus {
	if workers < 1 {
		workers = DefaultWorkers
	}
	if bufferSize < 1 {
		bufferSize = 1000
	}

	ctx, cancel := context.WithCancel(context.Background())
	bus := &InMemoryEventBus{
		subscribers: make(map[string][]EventHandler),
		eventChan:   make(chan DomainEvent, bufferSize),
		workers:     workers,
		ctx:         ctx,
		cancel:      cancel,
	}

	bus.startWorkers()
	return bus
}

func (b *InMemoryEventBus) startWorkers() {
	for i := 0; i < b.workers; i++ {
		b.wg.Add(1)
		go func() {
			defer b.wg.Done()
			for event := range b.eventChan {
				b.dispatch(event)
			}
		}()
	}
}

func (b *InMemoryEventBus) dispatch(event DomainEvent) {
	b.mu.RLock()
	handlers := append([]EventHandler(nil), b.subscribers[event.EventType()]...)
	wildcardHandlers := append([]EventHandler(nil), b.subscribers["*"]...)
	b.mu.RUnlock()

	allHandlers := append(handlers, wildcardHandlers...)

	for _, handler := range allHandlers {
		func(h EventHandler) {
			defer func() {
				if r := recover(); r != nil {
					log.Printf("[eventbus] panic recovered in handler for event %s: %v", event.EventType(), r)
				}
			}()
			if err := h(b.ctx, event); err != nil {
				log.Printf("[eventbus] handler error for event %s: %v", event.EventType(), err)
			}
		}(handler)
	}
}

func (b *InMemoryEventBus) Subscribe(eventType string, handler EventHandler) error {
	b.mu.Lock()
	defer b.mu.Unlock()

	if b.closed {
		return ErrBusClosed
	}

	b.subscribers[eventType] = append(b.subscribers[eventType], handler)
	return nil
}

func (b *InMemoryEventBus) Publish(ctx context.Context, event DomainEvent) error {
	b.mu.RLock()
	defer b.mu.RUnlock()

	if b.closed {
		return ErrBusClosed
	}

	select {
	case b.eventChan <- event:
		return nil
	default:
		log.Printf("[eventbus] backpressure: buffer full, dropping event %s", event.EventType())
		return fmt.Errorf("%w (event: %s)", ErrBufferFull, event.EventType())
	}
}

func (b *InMemoryEventBus) Close() error {
	b.mu.Lock()
	if b.closed {
		b.mu.Unlock()
		return nil
	}
	b.closed = true
	b.mu.Unlock()

	close(b.eventChan)
	b.wg.Wait()
	b.cancel()
	return nil
}
