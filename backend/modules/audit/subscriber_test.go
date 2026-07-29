package audit_test

import (
	"context"
	"sync"
	"testing"

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
	var wg sync.WaitGroup
	var capturedAction string
	wg.Add(1)

	db := &mockDBTX{
		onQueryRow: func(_ string, args []any) {
			defer wg.Done()
			if len(args) >= 4 {
				if action, ok := args[3].(string); ok {
					capturedAction = action
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

	wg.Wait()

	if capturedAction != "device.created" {
		t.Errorf("expected audit action 'device.created', got %q", capturedAction)
	}
}

type badEventIDEvent struct {
	eventbus.BaseEvent
}

func (e badEventIDEvent) EventID() string { return "not-a-uuid" }

func TestAuditSubscriber_HandleEvent_InvalidEventID(t *testing.T) {
	var wg sync.WaitGroup
	wg.Add(1)

	db := &mockDBTX{
		onQueryRow: func(_ string, _ []any) {
			defer wg.Done()
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
	wg.Wait()
}

type unmarshalablePayloadEvent struct {
	eventbus.BaseEvent
}

func (e unmarshalablePayloadEvent) Payload() any { return make(chan int) }

func TestAuditSubscriber_HandleEvent_UnmarshalablePayload(t *testing.T) {
	var wg sync.WaitGroup
	wg.Add(1)

	db := &mockDBTX{
		onQueryRow: func(_ string, _ []any) {
			defer wg.Done()
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
	wg.Wait()
}
