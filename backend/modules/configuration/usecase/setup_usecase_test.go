package usecase_test

import (
	"context"
	"errors"
	"testing"

	"github.com/google/uuid"
	"github.com/matheussouza/inframap/internal/platform/db"
	"github.com/matheussouza/inframap/internal/platform/eventbus"
	"github.com/matheussouza/inframap/modules/configuration/dto"
	"github.com/matheussouza/inframap/modules/configuration/repository"
	"github.com/matheussouza/inframap/modules/configuration/usecase"
)

type mockSetupRepo struct {
	state      *db.SystemState
	adminUser  *db.User
	getStateErr error
	onboardErr error
}

func (m *mockSetupRepo) GetState(_ context.Context) (*db.SystemState, error) {
	if m.getStateErr != nil {
		return nil, m.getStateErr
	}
	if m.state == nil {
		return nil, repository.ErrStateNotFound
	}
	return m.state, nil
}

func (m *mockSetupRepo) EnsureInitialState(_ context.Context, instanceID uuid.UUID) (*db.SystemState, error) {
	if m.getStateErr != nil {
		return nil, m.getStateErr
	}
	m.state = &db.SystemState{
		SystemInstanceID:    instanceID,
		OnboardingCompleted: false,
	}
	return m.state, nil
}

func (m *mockSetupRepo) Onboard(_ context.Context, params repository.OnboardParams) (*db.SystemState, *db.User, error) {
	if m.onboardErr != nil {
		return nil, nil, m.onboardErr
	}
	if m.state == nil {
		m.state = &db.SystemState{SystemInstanceID: uuid.New()}
	}
	m.state.OnboardingCompleted = true
	m.adminUser = &db.User{
		ID:       uuid.New(),
		Username: params.AdminUsername,
		Email:    params.AdminEmail,
	}
	return m.state, m.adminUser, nil
}

func TestSetupUseCase_GetStatus(t *testing.T) {
	repo := &mockSetupRepo{
		state: &db.SystemState{
			SystemInstanceID:    uuid.New(),
			OnboardingCompleted: false,
		},
	}
	uc := usecase.NewDefaultSetupUseCase(repo, nil, nil)

	status, err := uc.GetStatus(context.Background())
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}

	if status.OnboardingCompleted {
		t.Errorf("expected onboarding_completed false, got true")
	}
}

func TestSetupUseCase_GetStatus_AutoSeed(t *testing.T) {
	repo := &mockSetupRepo{state: nil}
	uc := usecase.NewDefaultSetupUseCase(repo, nil, nil)

	status, err := uc.GetStatus(context.Background())
	if err != nil {
		t.Fatalf("unexpected error on autoseed: %v", err)
	}

	if status.OnboardingCompleted {
		t.Error("expected onboarding_completed false after autoseed")
	}
}

func TestSetupUseCase_Onboard(t *testing.T) {
	repo := &mockSetupRepo{
		state: &db.SystemState{
			SystemInstanceID:    uuid.New(),
			OnboardingCompleted: false,
		},
	}
	bus := eventbus.NewInMemoryEventBus(1, 10)
	defer func() { _ = bus.Close() }()

	uc := usecase.NewDefaultSetupUseCase(repo, bus, nil)

	req := dto.OnboardRequest{
		AdminUsername:    "admin",
		AdminEmail:       "admin@example.com",
		AdminPassword:    "correct-horse-battery-staple-passphrase",
		AdminFullName:    "System Administrator",
		TelemetryEnabled: true,
	}

	resp, err := uc.Onboard(context.Background(), req)
	if err != nil {
		t.Fatalf("unexpected error during onboard: %v", err)
	}

	if !resp.OnboardingCompleted {
		t.Error("expected onboarding_completed true")
	}

	// Test Already Onboarded error
	_, err = uc.Onboard(context.Background(), req)
	if !errors.Is(err, usecase.ErrAlreadyOnboarded) {
		t.Errorf("expected ErrAlreadyOnboarded, got %v", err)
	}
}
