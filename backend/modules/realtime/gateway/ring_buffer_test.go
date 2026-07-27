package gateway_test

import (
	"testing"

	"github.com/matheussouza/inframap/modules/realtime/gateway"
)

func TestEventMessage_FormatSSE(t *testing.T) {
	msg := gateway.EventMessage{
		ID:    "evt-12345",
		Event: "topology.updated",
		Data: map[string]string{
			"action": "link_created",
		},
	}

	formatted := msg.FormatSSE()
	expected := "id: evt-12345\nevent: topology.updated\ndata: {\"action\":\"link_created\"}\n\n"
	if formatted != expected {
		t.Errorf("expected:\n%s\ngot:\n%s", expected, formatted)
	}
}

func TestRingBuffer(t *testing.T) {
	rb := gateway.NewRingBuffer(5)

	t.Run("Empty Buffer Returns Nil", func(t *testing.T) {
		events := rb.GetEventsAfter("any-id")
		if len(events) != 0 {
			t.Errorf("expected empty list, got %d", len(events))
		}
	})

	t.Run("Push and Replay Events After LastID", func(t *testing.T) {
		m1 := gateway.EventMessage{ID: "id-1", Event: "ev1"}
		m2 := gateway.EventMessage{ID: "id-2", Event: "ev2"}
		m3 := gateway.EventMessage{ID: "id-3", Event: "ev3"}

		rb.Add(m1)
		rb.Add(m2)
		rb.Add(m3)

		replayed := rb.GetEventsAfter("id-1")
		if len(replayed) != 2 {
			t.Fatalf("expected 2 replayed events, got %d", len(replayed))
		}
		if replayed[0].ID != "id-2" || replayed[1].ID != "id-3" {
			t.Errorf("unexpected replayed events order: %v", replayed)
		}

		replayedLatest := rb.GetEventsAfter("id-3")
		if len(replayedLatest) != 0 {
			t.Errorf("expected 0 events after latest id, got %d", len(replayedLatest))
		}
	})

	t.Run("Circular Wrapping", func(t *testing.T) {
		smallRB := gateway.NewRingBuffer(3)
		smallRB.Add(gateway.EventMessage{ID: "i1"})
		smallRB.Add(gateway.EventMessage{ID: "i2"})
		smallRB.Add(gateway.EventMessage{ID: "i3"})
		smallRB.Add(gateway.EventMessage{ID: "i4"})

		replayed := smallRB.GetEventsAfter("i2")
		if len(replayed) != 2 {
			t.Fatalf("expected 2 replayed events after wrapping, got %d", len(replayed))
		}
		if replayed[0].ID != "i3" || replayed[1].ID != "i4" {
			t.Errorf("unexpected replayed events: %v", replayed)
		}
	})
}
