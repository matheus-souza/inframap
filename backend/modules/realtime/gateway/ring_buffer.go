package gateway

import (
	"sync"
)

// DefaultRingBufferCapacity is the default number of recent SSE events kept for reconnection replay.
const DefaultRingBufferCapacity = 100

// RingBuffer is a thread-safe circular buffer storing recent SSE event messages.
type RingBuffer struct {
	mu       sync.RWMutex
	capacity int
	events   []EventMessage
	head     int
	count    int
}

// NewRingBuffer constructs a new RingBuffer with specified capacity.
func NewRingBuffer(capacity int) *RingBuffer {
	if capacity <= 0 {
		capacity = DefaultRingBufferCapacity
	}
	return &RingBuffer{
		capacity: capacity,
		events:   make([]EventMessage, capacity),
	}
}

// Add pushes a new EventMessage into the ring buffer.
func (rb *RingBuffer) Add(msg EventMessage) {
	rb.mu.Lock()
	defer rb.mu.Unlock()

	rb.events[rb.head] = msg
	rb.head = (rb.head + 1) % rb.capacity
	if rb.count < rb.capacity {
		rb.count++
	}
}

// GetEventsAfter returns all events added after the specified lastEventID.
func (rb *RingBuffer) GetEventsAfter(lastEventID string) []EventMessage {
	if lastEventID == "" {
		return nil
	}
	rb.mu.RLock()
	defer rb.mu.RUnlock()

	if rb.count == 0 {
		return nil
	}

	startIndex := (rb.head - rb.count + rb.capacity) % rb.capacity
	foundIdx := -1

	for i := 0; i < rb.count; i++ {
		idx := (startIndex + i) % rb.capacity
		if rb.events[idx].ID == lastEventID {
			foundIdx = i
			break
		}
	}

	if foundIdx == -1 || foundIdx == rb.count-1 {
		return nil
	}

	replayCount := rb.count - 1 - foundIdx
	result := make([]EventMessage, 0, replayCount)

	for i := foundIdx + 1; i < rb.count; i++ {
		idx := (startIndex + i) % rb.capacity
		result = append(result, rb.events[idx])
	}

	return result
}
