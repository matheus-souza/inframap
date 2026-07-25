// Package usecase implements authentication, session handling, and RBAC business logic.
package usecase

import (
	"context"
	"errors"
	"fmt"
	"log/slog"
	"net"
	"strings"
	"sync"
	"time"

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

// dummyBcryptHash is used for timing-attack mitigation when a user is not found.
const dummyBcryptHash = "$2a$12$13f3jK8x1fdgZti0gBQuPuf/fKyokAsdsb3YVnwf4/fxqpSCUJFhK"

// IdentityUseCase defines application logic for authentication and profile inspection.
type IdentityUseCase interface {
	Login(ctx context.Context, req dto.LoginRequest, userAgent, ipAddress string) (*dto.LoginResponse, error)
	Logout(ctx context.Context, token string) error
	GetMe(ctx context.Context, token string) (*dto.UserMeResponse, error)
}

type failedAttemptTracker struct {
	count       int
	firstFail   time.Time
	lockedUntil time.Time
}

// DefaultIdentityUseCase implements IdentityUseCase.
type DefaultIdentityUseCase struct {
	sessionRepo repository.SessionRepository
	eventBus    eventbus.EventBus
	logger      *slog.Logger

	mu       sync.Mutex
	lockouts map[string]*failedAttemptTracker
}

const lockoutCleanupInterval = 10 * time.Minute
const lockoutWindowDuration = 5 * time.Minute

// NewDefaultIdentityUseCase creates a new DefaultIdentityUseCase.
// The provided context controls the lifetime of the background lockout cleanup goroutine.
func NewDefaultIdentityUseCase(ctx context.Context, sessionRepo repository.SessionRepository, bus eventbus.EventBus, logger *slog.Logger) *DefaultIdentityUseCase {
	uc := &DefaultIdentityUseCase{
		sessionRepo: sessionRepo,
		eventBus:    bus,
		logger:      logger,
		lockouts:    make(map[string]*failedAttemptTracker),
	}
	go uc.cleanupLockouts(ctx)
	return uc
}

func (uc *DefaultIdentityUseCase) cleanupLockouts(ctx context.Context) {
	ticker := time.NewTicker(lockoutCleanupInterval)
	defer ticker.Stop()

	for {
		select {
		case <-ctx.Done():
			return
		case <-ticker.C:
			uc.mu.Lock()
			now := time.Now()
			for key, tracker := range uc.lockouts {
				expired := now.After(tracker.lockedUntil) && now.Sub(tracker.firstFail) > lockoutWindowDuration
				if expired {
					delete(uc.lockouts, key)
				}
			}
			uc.mu.Unlock()
		}
	}
}

// Login authenticates credentials, applies brute-force defense, creates session, and emits audit events.
func (uc *DefaultIdentityUseCase) Login(ctx context.Context, req dto.LoginRequest, userAgent, ipAddress string) (*dto.LoginResponse, error) {
	normalizedUsername := strings.ToLower(strings.TrimSpace(req.Username))
	cleanIP := ipAddress
	if host, _, err := net.SplitHostPort(ipAddress); err == nil {
		cleanIP = host
	}

	usernameKey := fmt.Sprintf("%s:%s", normalizedUsername, cleanIP)

	uc.mu.Lock()
	tracker, exists := uc.lockouts[usernameKey]
	if exists && time.Now().Before(tracker.lockedUntil) {
		uc.mu.Unlock()
		return nil, ErrAccountLocked
	}
	uc.mu.Unlock()

	user, err := uc.sessionRepo.LookupUserByUsername(ctx, normalizedUsername)
	if err != nil {
		_ = bcrypt.CompareHashAndPassword([]byte(dummyBcryptHash), []byte(req.Password))
		uc.recordFailedAttempt(ctx, usernameKey, normalizedUsername, cleanIP, "user not found")
		time.Sleep(100 * time.Millisecond)
		return nil, ErrInvalidCredentials
	}

	if bcryptErr := bcrypt.CompareHashAndPassword([]byte(user.PasswordHash), []byte(req.Password)); bcryptErr != nil {
		uc.recordFailedAttempt(ctx, usernameKey, normalizedUsername, cleanIP, "password mismatch")
		time.Sleep(100 * time.Millisecond)
		return nil, ErrInvalidCredentials
	}

	if !user.IsActive {
		return nil, ErrUserInactive
	}

	uc.mu.Lock()
	delete(uc.lockouts, usernameKey)
	uc.mu.Unlock()

	token, err := uc.sessionRepo.GenerateToken()
	if err != nil {
		return nil, fmt.Errorf("failed to generate token: %w", err)
	}

	session, err := uc.sessionRepo.CreateSession(ctx, user.ID, token, userAgent, cleanIP)
	if err != nil {
		return nil, err
	}

	perms, permErr := uc.sessionRepo.GetUserPermissions(ctx, user.ID)
	if permErr != nil {
		return nil, fmt.Errorf("failed to retrieve user permissions: %w", permErr)
	}

	if uc.eventBus != nil {
		evt := eventbus.NewBaseEvent("user.login_success", map[string]string{
			"user_id":    user.ID.String(),
			"username":   user.Username,
			"ip_address": cleanIP,
			"user_agent": userAgent,
		})
		_ = uc.eventBus.Publish(ctx, evt)
	}

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
	if !exists || now.Sub(tracker.firstFail) > lockoutWindowDuration {
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

	perms, permErr := uc.sessionRepo.GetUserPermissions(ctx, session.UserID)
	if permErr != nil {
		return nil, fmt.Errorf("failed to retrieve user permissions: %w", permErr)
	}

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
	return strings.Map(func(r rune) rune {
		if r < 0x20 || r == 0x7F {
			return -1
		}
		return r
	}, input)
}
