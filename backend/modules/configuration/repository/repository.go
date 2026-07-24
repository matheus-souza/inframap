// Package repository provides database persistence for system configuration and onboarding.
package repository

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"

	"github.com/google/uuid"
	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgtype"
	"github.com/jackc/pgx/v5/pgxpool"
	"github.com/matheussouza/inframap/internal/platform/db"
)

// ErrStateNotFound indicates no system state record exists.
var ErrStateNotFound = errors.New("system state not found")

// OnboardParams holds parameters required to execute single-shot onboarding.
type OnboardParams struct {
	AdminUsername     string
	AdminEmail        string
	AdminPasswordHash string
	AdminFullName     string
	TelemetryEnabled  bool
	InstalledVersion  string
}

// SetupRepository defines persistence operations for system state and onboarding.
type SetupRepository interface {
	GetState(ctx context.Context) (*db.SystemState, error)
	EnsureInitialState(ctx context.Context, instanceID uuid.UUID) (*db.SystemState, error)
	Onboard(ctx context.Context, params OnboardParams) (*db.SystemState, *db.User, error)
}

// PgSetupRepository implements SetupRepository backed by PostgreSQL via pgxpool and sqlc.
type PgSetupRepository struct {
	pool *pgxpool.Pool
}

// NewPgSetupRepository creates a new PgSetupRepository instance.
func NewPgSetupRepository(pool *pgxpool.Pool) *PgSetupRepository {
	return &PgSetupRepository{pool: pool}
}

// GetState retrieves the singleton system_state record.
func (r *PgSetupRepository) GetState(ctx context.Context) (*db.SystemState, error) {
	queries := db.New(r.pool)
	state, err := queries.GetSystemState(ctx)
	if err != nil {
		if errors.Is(err, pgx.ErrNoRows) {
			return nil, ErrStateNotFound
		}
		return nil, fmt.Errorf("failed to get system state: %w", err)
	}
	return &state, nil
}

// EnsureInitialState auto-seeds a system_state record if empty on startup.
func (r *PgSetupRepository) EnsureInitialState(ctx context.Context, instanceID uuid.UUID) (*db.SystemState, error) {
	queries := db.New(r.pool)
	state, err := queries.GetSystemState(ctx)
	if err == nil {
		return &state, nil
	}

	if !errors.Is(err, pgx.ErrNoRows) {
		return nil, fmt.Errorf("error checking system state: %w", err)
	}

	// Create initial un-onboarded state
	id, err := uuid.NewV7()
	if err != nil {
		return nil, err
	}

	newState, err := queries.CreateSystemState(ctx, db.CreateSystemStateParams{
		ID:                   id,
		OnboardingCompleted: false,
		OnboardingCompletedAt: pgtype.Timestamptz{Valid: false},
		SystemInstanceID:    instanceID,
		TelemetryEnabled:    false,
		Metadata:            []byte("{}"),
	})
	if err != nil {
		return nil, fmt.Errorf("failed to create initial system state: %w", err)
	}

	return &newState, nil
}

// Onboard executes atomic transaction seeding standard roles, creating admin user, and updating system state.
func (r *PgSetupRepository) Onboard(ctx context.Context, params OnboardParams) (*db.SystemState, *db.User, error) {
	tx, err := r.pool.Begin(ctx)
	if err != nil {
		return nil, nil, fmt.Errorf("failed to begin transaction: %w", err)
	}
	defer func() { _ = tx.Rollback(ctx) }()

	qtx := db.New(tx)

	state, err := qtx.GetSystemState(ctx)
	if err != nil {
		return nil, nil, fmt.Errorf("failed to retrieve system state in onboarding: %w", err)
	}

	if state.OnboardingCompleted {
		return nil, nil, errors.New("system onboarding is already completed")
	}

	// 1. Seed standard roles (admin, operator, viewer)
	adminRole, err := seedRole(ctx, qtx, "admin", "Full system administrator access", true)
	if err != nil {
		return nil, nil, err
	}
	_, _ = seedRole(ctx, qtx, "operator", "Operational read/write access to inventory and discovery", true)
	_, _ = seedRole(ctx, qtx, "viewer", "Read-only access to topology and inventory", true)

	// 2. Create admin user
	userID, err := uuid.NewV7()
	if err != nil {
		return nil, nil, err
	}

	user, err := qtx.CreateUser(ctx, db.CreateUserParams{
		ID:           userID,
		Username:     params.AdminUsername,
		Email:        params.AdminEmail,
		PasswordHash: params.AdminPasswordHash,
		FullName:     params.AdminFullName,
		IsActive:     true,
	})
	if err != nil {
		return nil, nil, fmt.Errorf("failed to create admin user: %w", err)
	}

	// 3. Assign admin role
	err = qtx.AssignUserRole(ctx, db.AssignUserRoleParams{
		UserID: user.ID,
		RoleID: adminRole.ID,
	})
	if err != nil {
		return nil, nil, fmt.Errorf("failed to assign admin role: %w", err)
	}

	// 4. Update system_state
	metadataMap := map[string]string{
		"installed_version": params.InstalledVersion,
	}
	metadataBytes, _ := json.Marshal(metadataMap)

	err = qtx.UpdateSystemStateOnboarding(ctx, state.ID)
	if err != nil {
		return nil, nil, fmt.Errorf("failed to update system state onboarding: %w", err)
	}

	if err := tx.Commit(ctx); err != nil {
		return nil, nil, fmt.Errorf("failed to commit onboarding transaction: %w", err)
	}

	// Fetch updated state
	updatedState, err := r.GetState(ctx)
	if err != nil {
		return nil, nil, err
	}

	_ = metadataBytes

	return updatedState, &user, nil
}

func seedRole(ctx context.Context, q *db.Queries, name, description string, isSystem bool) (db.Role, error) {
	role, err := q.GetRoleByName(ctx, name)
	if err == nil {
		return role, nil
	}

	id, err := uuid.NewV7()
	if err != nil {
		return db.Role{}, err
	}

	return q.CreateRole(ctx, db.CreateRoleParams{
		ID:          id,
		Name:        name,
		Description: pgtype.Text{String: description, Valid: true},
		IsSystem:    isSystem,
	})
}
