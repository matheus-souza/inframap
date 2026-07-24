// Package usecase implements business orchestration for the configuration module.
package usecase

import (
	"context"
	"errors"
	"fmt"
	"log/slog"

	"github.com/google/uuid"
	"github.com/matheussouza/inframap/internal/platform/eventbus"
	"github.com/matheussouza/inframap/modules/configuration/dto"
	"github.com/matheussouza/inframap/modules/configuration/repository"
	"golang.org/x/crypto/bcrypt"
)

// AppVersion defines the technical installation version.
const AppVersion = "v1.0.0-rc.7"

// ErrAlreadyOnboarded indicates that onboarding has already been completed.
var ErrAlreadyOnboarded = errors.New("system onboarding is already completed")

// SetupUseCase defines application use cases for system configuration and onboarding.
type SetupUseCase interface {
	GetStatus(ctx context.Context) (*dto.StatusResponse, error)
	Onboard(ctx context.Context, req dto.OnboardRequest) (*dto.OnboardResponse, error)
}

// DefaultSetupUseCase implements SetupUseCase.
type DefaultSetupUseCase struct {
	repo     repository.SetupRepository
	eventBus eventbus.EventBus
	logger   *slog.Logger
}

// NewDefaultSetupUseCase creates a new DefaultSetupUseCase instance.
func NewDefaultSetupUseCase(repo repository.SetupRepository, bus eventbus.EventBus, logger *slog.Logger) *DefaultSetupUseCase {
	return &DefaultSetupUseCase{
		repo:     repo,
		eventBus: bus,
		logger:   logger,
	}
}

// GetStatus returns the current onboarding status.
func (uc *DefaultSetupUseCase) GetStatus(ctx context.Context) (*dto.StatusResponse, error) {
	state, err := uc.repo.GetState(ctx)
	if err != nil {
		if errors.Is(err, repository.ErrStateNotFound) {
			// Auto-seed instance if state row missing
			instanceID, genErr := uuid.NewV7()
			if genErr != nil {
				return nil, genErr
			}
			state, err = uc.repo.EnsureInitialState(ctx, instanceID)
			if err != nil {
				return nil, err
			}
		} else {
			return nil, err
		}
	}

	return &dto.StatusResponse{
		OnboardingCompleted: state.OnboardingCompleted,
		SystemInstanceID:    state.SystemInstanceID.String(),
	}, nil
}

// Onboard executes single-shot system onboarding.
func (uc *DefaultSetupUseCase) Onboard(ctx context.Context, req dto.OnboardRequest) (*dto.OnboardResponse, error) {
	state, err := uc.repo.GetState(ctx)
	if err != nil && !errors.Is(err, repository.ErrStateNotFound) {
		return nil, fmt.Errorf("failed to check system state: %w", err)
	}

	if state != nil && state.OnboardingCompleted {
		return nil, ErrAlreadyOnboarded
	}

	// Hash password with bcrypt cost 12
	hashedBytes, err := bcrypt.GenerateFromPassword([]byte(req.AdminPassword), 12)
	if err != nil {
		return nil, fmt.Errorf("failed to hash password: %w", err)
	}

	updatedState, adminUser, err := uc.repo.Onboard(ctx, repository.OnboardParams{
		AdminUsername:     req.AdminUsername,
		AdminEmail:        req.AdminEmail,
		AdminPasswordHash: string(hashedBytes),
		AdminFullName:     req.AdminFullName,
		TelemetryEnabled:  req.TelemetryEnabled,
		InstalledVersion:  AppVersion,
	})
	if err != nil {
		return nil, err
	}

	// Publish system.onboarded event to EventBus
	if uc.eventBus != nil {
		evt := eventbus.NewBaseEvent("system.onboarded", map[string]string{
			"instance_id":   updatedState.SystemInstanceID.String(),
			"admin_user_id": adminUser.ID.String(),
			"admin_email":   adminUser.Email,
		})
		if pubErr := uc.eventBus.Publish(ctx, evt); pubErr != nil {
			if uc.logger != nil {
				uc.logger.Error("failed to publish system.onboarded event", slog.Any("error", pubErr))
			}
		}
	}

	return &dto.OnboardResponse{
		OnboardingCompleted: updatedState.OnboardingCompleted,
		SystemInstanceID:    updatedState.SystemInstanceID.String(),
		AdminUserID:         adminUser.ID.String(),
	}, nil
}
