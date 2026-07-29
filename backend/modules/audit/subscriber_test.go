package audit_test

import (
	"context"
	"testing"
	"time"

	"github.com/google/uuid"
	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgconn"
	"github.com/matheussouza/inframap/internal/platform/eventbus"
	"github.com/matheussouza/inframap/modules/audit"
)

type mockDBTX struct {
	onQueryRow func(sql string, args []any)
}

func (m *mockDBTX) Exec(_ context.Context, _ string, _ ...any) (pgconn.CommandTag, error) {
	return pgconn.NewCommandTag("INSERT 0 1"), nil
}

func (m *mockDBTX) Query(_ context.Context, _ string, _ ...any) (pgx.Rows, error) {
	return nil, nil
}

func (m *mockDBTX) QueryRow(_ context.Context, sql string, args ...any) pgx.Row {
	if m.onQueryRow != nil {
		m.onQueryRow(sql, args)
	}
	return mockRow{}
}

type mockRow struct{}

func (m mockRow) Scan(_ ...any) error {
	return nil
}

func TestAuditSubscriber_HandleEvent(t *testing.T) {
	ch := make(chan string, 1)

	db := &mockDBTX{
		onQueryRow: func(_ string, args []any) {
			if len(args) >= 4 {
				if action, ok := args[3].(string); ok {
					ch <- action
				}
			}
		},
	}

	subscriber := audit.NewSubscriber(db)
	bus := eventbus.NewInMemoryEventBus(1, 10)
	defer func() { _ = bus.Close() }()

	if err := subscriber.Register(bus); err != nil {
		t.Fatalf("failed to register subscriber: %v", err)
	}

	event := eventbus.NewBaseEvent("device.created", map[string]string{
		"hostname": "switch-core-01",
	})

	err := bus.Publish(context.Background(), event)
	if err != nil {
		t.Fatalf("failed to publish event: %v", err)
	}

	select {
	case action := <-ch:
		if action != "device.created" {
			t.Errorf("expected audit action 'device.created', got %q", action)
		}
	case <-time.After(5 * time.Second):
		t.Fatal("timed out waiting for audit event")
	}
}

type badEventIDEvent struct {
	eventbus.BaseEvent
}

func (e badEventIDEvent) EventID() string { return "not-a-uuid" }

func TestAuditSubscriber_HandleEvent_InvalidEventID(t *testing.T) {
	var capturedID uuid.UUID

	db := &mockDBTX{
		onQueryRow: func(_ string, args []any) {
			if len(args) > 0 {
				if id, ok := args[0].(uuid.UUID); ok {
					capturedID = id
				}
			}
		},
	}

	subscriber := audit.NewSubscriber(db)
	event := badEventIDEvent{
		BaseEvent: eventbus.NewBaseEvent("test.invalid.id", map[string]string{"key": "val"}),
	}

	err := subscriber.HandleEvent(context.Background(), event)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}

	if capturedID == uuid.Nil {
		t.Error("expected fallback UUID to be non-nil when EventID is unparseable")
	}
}

type unmarshalablePayloadEvent struct {
	eventbus.BaseEvent
}

func (e unmarshalablePayloadEvent) Payload() any { return make(chan int) }

func TestAuditSubscriber_HandleEvent_UnmarshalablePayload(t *testing.T) {
	var capturedChanges []byte

	db := &mockDBTX{
		onQueryRow: func(_ string, args []any) {
			if len(args) > 6 {
				if changes, ok := args[6].([]byte); ok {
					capturedChanges = changes
				}
			}
		},
	}

	subscriber := audit.NewSubscriber(db)
	event := unmarshalablePayloadEvent{
		BaseEvent: eventbus.NewBaseEvent("test.bad.payload", nil),
	}

	err := subscriber.HandleEvent(context.Background(), event)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}

	if string(capturedChanges) != "{}" {
		t.Errorf("expected fallback changes '{}', got %q", string(capturedChanges))
	}
}
