package usecase_test

import (
	"context"
	"errors"
	"testing"
	"time"

	"github.com/google/uuid"
	"github.com/jackc/pgx/v5/pgtype"
	"github.com/matheussouza/inframap/internal/platform/db"
	"github.com/matheussouza/inframap/internal/platform/eventbus"
	"github.com/matheussouza/inframap/modules/configuration/dto"
	"github.com/matheussouza/inframap/modules/configuration/repository"
	"github.com/matheussouza/inframap/modules/configuration/usecase"
)

type mockRepo struct {
	state       *db.SystemState
	onboardErr  error
	getErr      error
	ensureState *db.SystemState
}

func (m *mockRepo) GetState(_ context.Context) (*db.SystemState, error) {
	if m.getErr != nil {
		return nil, m.getErr
	}
	if m.state == nil {
		return nil, repository.ErrStateNotFound
	}
	return m.state, nil
}

func (m *mockRepo) EnsureInitialState(_ context.Context, instanceID uuid.UUID) (*db.SystemState, error) {
	if m.ensureState != nil {
		return m.ensureState, nil
	}
	st := &db.SystemState{
		ID:                  uuid.New(),
		OnboardingCompleted: false,
		SystemInstanceID:    instanceID,
	}
	m.state = st
	return st, nil
}

func (m *mockRepo) Onboard(_ context.Context, params repository.OnboardParams) (*db.SystemState, *db.User, error) {
	if m.onboardErr != nil {
		return nil, nil, m.onboardErr
	}
	if m.state == nil {
		m.state = &db.SystemState{
			ID:               uuid.New(),
			SystemInstanceID: uuid.New(),
		}
	}
	m.state.OnboardingCompleted = true
	m.state.OnboardingCompletedAt = pgtype.Timestamptz{Time: time.Now(), Valid: true}

	user := &db.User{
		ID:       uuid.New(),
		Username: params.AdminUsername,
		Email:    params.AdminEmail,
	}

	return m.state, user, nil
}

func TestSetupUseCase_GetStatus(t *testing.T) {
	instanceID := uuid.New()
	repo := &mockRepo{
		state: &db.SystemState{
			OnboardingCompleted: false,
			SystemInstanceID:    instanceID,
		},
	}
	bus := eventbus.NewInMemoryEventBus(1, 10)
	defer func() { _ = bus.Close() }()

	uc := usecase.NewDefaultSetupUseCase(repo, bus, nil)

	status, err := uc.GetStatus(context.Background())
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}

	if status.OnboardingCompleted {
		t.Error("expected onboarding_completed to be false")
	}
	if status.SystemInstanceID != instanceID.String() {
		t.Errorf("expected instance ID %s, got %s", instanceID.String(), status.SystemInstanceID)
	}
}

func TestSetupUseCase_Onboard(t *testing.T) {
	instanceID := uuid.New()
	repo := &mockRepo{
		state: &db.SystemState{
			OnboardingCompleted: false,
			SystemInstanceID:    instanceID,
		},
	}
	bus := eventbus.NewInMemoryEventBus(1, 10)
	defer func() { _ = bus.Close() }()

	uc := usecase.NewDefaultSetupUseCase(repo, bus, nil)

	req := dto.OnboardRequest{
		AdminUsername:    "admin",
		AdminEmail:       "admin@example.com",
		AdminPassword:    "correct-horse-battery-staple-passphrase",
		AdminFullName:    "System Admin",
		TelemetryEnabled: false,
	}

	resp, err := uc.Onboard(context.Background(), req)
	if err != nil {
		t.Fatalf("unexpected onboard error: %v", err)
	}

	if !resp.OnboardingCompleted {
		t.Error("expected onboarding_completed to be true after onboard")
	}

	// Second onboard attempt must return ErrAlreadyOnboarded
	_, err = uc.Onboard(context.Background(), req)
	if !errors.Is(err, usecase.ErrAlreadyOnboarded) {
		t.Errorf("expected ErrAlreadyOnboarded on second attempt, got %v", err)
	}
}

func TestSetupUseCase_OnboardFreshDatabase(t *testing.T) {
	repo := &mockRepo{state: nil}
	bus := eventbus.NewInMemoryEventBus(1, 10)
	defer func() { _ = bus.Close() }()

	uc := usecase.NewDefaultSetupUseCase(repo, bus, nil)

	req := dto.OnboardRequest{
		AdminUsername:    "admin",
		AdminEmail:       "admin@example.com",
		AdminPassword:    "correct-horse-battery-staple-passphrase",
		AdminFullName:    "System Admin",
		TelemetryEnabled: false,
	}

	resp, err := uc.Onboard(context.Background(), req)
	if err != nil {
		t.Fatalf("unexpected onboard error on fresh DB: %v", err)
	}

	if !resp.OnboardingCompleted {
		t.Error("expected onboarding_completed to be true")
	}
}
