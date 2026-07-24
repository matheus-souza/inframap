// Package usecase implements authentication, session handling, and RBAC business logic.
package usecase

import (
	"context"
	"errors"
	"fmt"
	"log/slog"
	"sync"
	"time"

	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"
	"github.com/matheussouza/inframap/internal/platform/db"
	"github.com/matheussouza/inframap/internal/platform/eventbus"
	"github.com/matheussouza/inframap/modules/identity/dto"
	"github.com/matheussouza/inframap/modules/identity/repository"
	"golang.org/x/crypto/bcrypt"
)

var (
	// ErrInvalidCredentials indicates incorrect username or password.
	ErrInvalidCredentials = errors.New("invalid username or password")

	// ErrAccountLocked indicates temporary account lockout due to brute-force attempts.
	ErrAccountLocked = errors.New("account is temporarily locked due to excessive failed login attempts; please try again later")

	// ErrUserInactive indicates an inactive user account.
	ErrUserInactive = errors.New("user account is inactive")
)

// IdentityUseCase defines application logic for authentication and profile inspection.
type IdentityUseCase interface {
	Login(ctx context.Context, req dto.LoginRequest, userAgent, ipAddress string) (*dto.LoginResponse, error)
	Logout(ctx context.Context, token string) error
	GetMe(ctx context.Context, token string) (*dto.UserMeResponse, error)
}

type failedAttemptTracker struct {
	count     int
	firstFail time.Time
	lockedUntil time.Time
}

// DefaultIdentityUseCase implements IdentityUseCase.
type DefaultIdentityUseCase struct {
	pool       *pgxpool.Pool
	sessionRepo repository.SessionRepository
	eventBus   eventbus.EventBus
	logger     *slog.Logger

	mu       sync.Mutex
	lockouts map[string]*failedAttemptTracker
}

// NewDefaultIdentityUseCase creates a new DefaultIdentityUseCase.
func NewDefaultIdentityUseCase(pool *pgxpool.Pool, sessionRepo repository.SessionRepository, bus eventbus.EventBus, logger *slog.Logger) *DefaultIdentityUseCase {
	return &DefaultIdentityUseCase{
		pool:        pool,
		sessionRepo: sessionRepo,
		eventBus:    bus,
		logger:      logger,
		lockouts:    make(map[string]*failedAttemptTracker),
	}
}

// Login authenticates credentials, applies brute-force defense, creates session, and emits audit events.
func (uc *DefaultIdentityUseCase) Login(ctx context.Context, req dto.LoginRequest, userAgent, ipAddress string) (*dto.LoginResponse, error) {
	usernameKey := fmt.Sprintf("%s:%s", req.Username, ipAddress)

	uc.mu.Lock()
	tracker, exists := uc.lockouts[usernameKey]
	if exists && time.Now().Before(tracker.lockedUntil) {
		uc.mu.Unlock()
		return nil, ErrAccountLocked
	}
	uc.mu.Unlock()

	queries := db.New(uc.pool)
	
	// Lookup user by username or email
	var user db.User
	var err error

	// Try lookup by username first
	userRow, lookupErr := uc.pool.Query(ctx, "SELECT id, username, email, password_hash, full_name, is_active FROM users WHERE username = $1 OR email = $1 LIMIT 1", req.Username)
	if lookupErr == nil && userRow.Next() {
		err = userRow.Scan(&user.ID, &user.Username, &user.Email, &user.PasswordHash, &user.FullName, &user.IsActive)
		userRow.Close()
	} else {
		err = pgx.ErrNoRows
	}

	if err != nil {
		uc.recordFailedAttempt(ctx, usernameKey, req.Username, ipAddress, "user not found")
		time.Sleep(100 * time.Millisecond) // Progressive delay
		return nil, ErrInvalidCredentials
	}

	if !user.IsActive {
		return nil, ErrUserInactive
	}

	// Verify password
	if bcryptErr := bcrypt.CompareHashAndPassword([]byte(user.PasswordHash), []byte(req.Password)); bcryptErr != nil {
		uc.recordFailedAttempt(ctx, usernameKey, req.Username, ipAddress, "password mismatch")
		time.Sleep(100 * time.Millisecond) // Progressive delay
		return nil, ErrInvalidCredentials
	}

	// Reset failed attempts on success
	uc.mu.Lock()
	delete(uc.lockouts, usernameKey)
	uc.mu.Unlock()

	// Generate stateful session token
	token, err := uc.sessionRepo.GenerateToken()
	if err != nil {
		return nil, fmt.Errorf("failed to generate token: %w", err)
	}

	session, err := uc.sessionRepo.CreateSession(ctx, user.ID, token, userAgent, ipAddress)
	if err != nil {
		return nil, err
	}

	perms, _ := uc.sessionRepo.GetUserPermissions(ctx, user.ID)

	// Publish user.login_success event
	if uc.eventBus != nil {
		evt := eventbus.NewBaseEvent("user.login_success", map[string]string{
			"user_id":    user.ID.String(),
			"username":   user.Username,
			"ip_address": ipAddress,
			"user_agent": userAgent,
		})
		_ = uc.eventBus.Publish(ctx, evt)
	}

	_ = queries

	return &dto.LoginResponse{
		Token:       token,
		UserID:      user.ID.String(),
		Username:    user.Username,
		Email:       user.Email,
		FullName:    user.FullName,
		Permissions: perms,
		ExpiresAt:   session.ExpiresAt,
	}, nil
}

func (uc *DefaultIdentityUseCase) recordFailedAttempt(ctx context.Context, key, username, ipAddress, reason string) {
	uc.mu.Lock()
	defer uc.mu.Unlock()

	now := time.Now()
	tracker, exists := uc.lockouts[key]
	if !exists || now.Sub(tracker.firstFail) > 5*time.Minute {
		tracker = &failedAttemptTracker{count: 1, firstFail: now}
		uc.lockouts[key] = tracker
	} else {
		tracker.count++
	}

	if tracker.count >= 5 {
		tracker.lockedUntil = now.Add(15 * time.Minute)
		if uc.logger != nil {
			uc.logger.Warn("account temporarily locked due to brute-force attempts",
				slog.String("username", sanitizeLogInput(username)),
				slog.String("ip_address", sanitizeLogInput(ipAddress)),
			)
		}
		if uc.eventBus != nil {
			evt := eventbus.NewBaseEvent("user.account_locked", map[string]string{
				"username":   username,
				"ip_address": ipAddress,
				"reason":     "5 failed attempts in 5 minutes",
			})
			_ = uc.eventBus.Publish(ctx, evt)
		}
	} else {
		if uc.eventBus != nil {
			evt := eventbus.NewBaseEvent("user.login_failed", map[string]string{
				"username":   username,
				"ip_address": ipAddress,
				"reason":     reason,
			})
			_ = uc.eventBus.Publish(ctx, evt)
		}
	}
}

// Logout revokes active session.
func (uc *DefaultIdentityUseCase) Logout(ctx context.Context, token string) error {
	if token == "" {
		return nil
	}
	return uc.sessionRepo.RevokeSession(ctx, token)
}

// GetMe resolves user profile and permissions from active session.
func (uc *DefaultIdentityUseCase) GetMe(ctx context.Context, token string) (*dto.UserMeResponse, error) {
	session, err := uc.sessionRepo.GetSessionByToken(ctx, token)
	if err != nil {
		return nil, err
	}

	perms, _ := uc.sessionRepo.GetUserPermissions(ctx, session.UserID)

	return &dto.UserMeResponse{
		ID:          session.UserID.String(),
		Username:    session.Username,
		Email:       session.Email,
		FullName:    session.FullName,
		IsActive:    session.IsActive,
		Permissions: perms,
	}, nil
}

func sanitizeLogInput(input string) string {
	escaped := strings.ReplaceAll(input, "\n", "")
	escaped = strings.ReplaceAll(escaped, "\r", "")
	return escaped
}
