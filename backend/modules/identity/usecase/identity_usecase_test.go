package usecase_test

import (
	"context"
	"errors"
	"log/slog"
	"testing"
	"time"

	"github.com/google/uuid"
	"github.com/matheussouza/inframap/internal/platform/eventbus"
	"github.com/matheussouza/inframap/modules/identity/dto"
	"github.com/matheussouza/inframap/modules/identity/repository"
	"github.com/matheussouza/inframap/modules/identity/usecase"
	"golang.org/x/crypto/bcrypt"
)

type mockEventBus struct {
	events []eventbus.DomainEvent
}

func (m *mockEventBus) Publish(_ context.Context, event eventbus.DomainEvent) error {
	m.events = append(m.events, event)
	return nil
}

func (m *mockEventBus) Subscribe(_ string, _ eventbus.EventHandler) error { return nil }
func (m *mockEventBus) Close() error                                      { return nil }

type mockSessionRepo struct {
	sessions    map[string]*repository.SessionData
	permissions map[uuid.UUID][]string
	users       map[string]*repository.UserCredentials
	tokenErr    error
	sessionErr  error
	permErr     error
	userErr     error
}

func newMockSessionRepo() *mockSessionRepo {
	return &mockSessionRepo{
		sessions:    make(map[string]*repository.SessionData),
		permissions: make(map[uuid.UUID][]string),
		users:       make(map[string]*repository.UserCredentials),
	}
}

func (m *mockSessionRepo) GenerateToken() (string, error) {
	if m.tokenErr != nil {
		return "", m.tokenErr
	}
	return "ims_mock_token_1234567890abcdef1234567890abcdef", nil
}

func (m *mockSessionRepo) HashToken(token string) string {
	return token
}

func (m *mockSessionRepo) CreateSession(_ context.Context, userID uuid.UUID, token, _, _ string) (*repository.SessionData, error) {
	if m.sessionErr != nil {
		return nil, m.sessionErr
	}
	sess := &repository.SessionData{
		SessionID: uuid.New().String(),
		UserID:    userID,
		ExpiresAt: time.Now().Add(30 * time.Minute),
		Username:  "mockuser",
		Email:     "mock@example.com",
		FullName:  "Mock User",
		IsActive:  true,
	}
	m.sessions[token] = sess
	return sess, nil
}

func (m *mockSessionRepo) GetSessionByToken(_ context.Context, token string) (*repository.SessionData, error) {
	if m.sessionErr != nil {
		return nil, m.sessionErr
	}
	sess, exists := m.sessions[token]
	if !exists {
		return nil, repository.ErrSessionNotFound
	}
	return sess, nil
}

func (m *mockSessionRepo) RevokeSession(_ context.Context, token string) error {
	if m.sessionErr != nil {
		return m.sessionErr
	}
	delete(m.sessions, token)
	return nil
}

func (m *mockSessionRepo) GetUserPermissions(_ context.Context, userID uuid.UUID) ([]string, error) {
	if m.permErr != nil {
		return nil, m.permErr
	}
	return m.permissions[userID], nil
}

func (m *mockSessionRepo) LookupUserByUsername(_ context.Context, username string) (*repository.UserCredentials, error) {
	if m.userErr != nil {
		return nil, m.userErr
	}
	creds, exists := m.users[username]
	if !exists {
		return nil, repository.ErrUserNotFound
	}
	return creds, nil
}

func seedTestUser(repo *mockSessionRepo, username, password string) uuid.UUID {
	userID := uuid.New()
	hash, _ := bcrypt.GenerateFromPassword([]byte(password), bcrypt.MinCost)
	repo.users[username] = &repository.UserCredentials{
		ID:           userID,
		Username:     username,
		Email:        username + "@example.com",
		PasswordHash: string(hash),
		FullName:     "Test User",
		IsActive:     true,
	}
	repo.permissions[userID] = []string{"admin:access"}
	return userID
}

func TestIdentityUseCase_Unit(t *testing.T) {
	repo := newMockSessionRepo()
	uc := usecase.NewDefaultIdentityUseCase(context.Background(), repo, nil, nil)

	t.Run("Logout with empty token returns nil", func(t *testing.T) {
		err := uc.Logout(context.Background(), "")
		if err != nil {
			t.Fatalf("expected nil error, got %v", err)
		}
	})

	t.Run("Logout revokes active session", func(t *testing.T) {
		token := "ims_test_token"
		repo.sessions[token] = &repository.SessionData{
			SessionID: uuid.New().String(),
			UserID:    uuid.New(),
		}

		err := uc.Logout(context.Background(), token)
		if err != nil {
			t.Fatalf("expected nil error on logout, got %v", err)
		}
		if _, exists := repo.sessions[token]; exists {
			t.Error("session was not revoked")
		}
	})

	t.Run("GetMe with non-existent token returns error", func(t *testing.T) {
		_, err := uc.GetMe(context.Background(), "invalid_token")
		if !errors.Is(err, repository.ErrSessionNotFound) {
			t.Errorf("expected ErrSessionNotFound, got %v", err)
		}
	})

	t.Run("GetMe with valid token returns profile", func(t *testing.T) {
		userID := uuid.New()
		token := "ims_valid_token"
		repo.sessions[token] = &repository.SessionData{
			SessionID: uuid.New().String(),
			UserID:   userID,
			Username: "admin",
			Email:    "admin@example.com",
			FullName: "Admin User",
			IsActive: true,
		}
		repo.permissions[userID] = []string{"admin:access", "inventory:read"}

		resp, err := uc.GetMe(context.Background(), token)
		if err != nil {
			t.Fatalf("unexpected error on GetMe: %v", err)
		}

		if resp.Username != "admin" {
			t.Errorf("expected username admin, got %s", resp.Username)
		}
		if len(resp.Permissions) != 2 {
			t.Errorf("expected 2 permissions, got %d", len(resp.Permissions))
		}
	})

	t.Run("GetMe handles permission error", func(t *testing.T) {
		userID := uuid.New()
		token := "ims_perm_error_token"
		repo.sessions[token] = &repository.SessionData{
			SessionID: uuid.New().String(),
			UserID:    userID,
		}
		repo.permErr = errors.New("db permission query failed")
		defer func() { repo.permErr = nil }()

		_, err := uc.GetMe(context.Background(), token)
		if err == nil {
			t.Error("expected error when permission lookup fails")
		}
	})
}

func TestCleanupLockouts_ContextCancel(_ *testing.T) {
	repo := newMockSessionRepo()
	ctx, cancel := context.WithCancel(context.Background())
	_ = usecase.NewDefaultIdentityUseCase(ctx, repo, nil, nil)
	cancel()
	time.Sleep(50 * time.Millisecond)
}

func TestIdentityUseCase_Login(t *testing.T) {
	t.Run("Login with valid credentials", func(t *testing.T) {
		repo := newMockSessionRepo()
		uc := usecase.NewDefaultIdentityUseCase(context.Background(), repo, nil, nil)
		seedTestUser(repo, "admin", "correct-horse-battery-staple")

		resp, err := uc.Login(context.Background(), dto.LoginRequest{
			Username: "admin",
			Password: "correct-horse-battery-staple",
		}, "test-agent", "127.0.0.1")

		if err != nil {
			t.Fatalf("unexpected error on login: %v", err)
		}
		if resp.Username != "admin" {
			t.Errorf("expected username admin, got %s", resp.Username)
		}
		if resp.Token == "" {
			t.Error("expected non-empty token")
		}
		if len(resp.Permissions) != 1 {
			t.Errorf("expected 1 permission, got %d", len(resp.Permissions))
		}
	})

	t.Run("Login with wrong password returns ErrInvalidCredentials", func(t *testing.T) {
		repo := newMockSessionRepo()
		uc := usecase.NewDefaultIdentityUseCase(context.Background(), repo, nil, nil)
		seedTestUser(repo, "admin", "correct-horse-battery-staple")

		_, err := uc.Login(context.Background(), dto.LoginRequest{
			Username: "admin",
			Password: "wrong-password",
		}, "test-agent", "127.0.0.1")

		if !errors.Is(err, usecase.ErrInvalidCredentials) {
			t.Errorf("expected ErrInvalidCredentials, got %v", err)
		}
	})

	t.Run("Login with unknown user returns ErrInvalidCredentials", func(t *testing.T) {
		repo := newMockSessionRepo()
		uc := usecase.NewDefaultIdentityUseCase(context.Background(), repo, nil, nil)

		_, err := uc.Login(context.Background(), dto.LoginRequest{
			Username: "nonexistent",
			Password: "some-password",
		}, "test-agent", "127.0.0.1")

		if !errors.Is(err, usecase.ErrInvalidCredentials) {
			t.Errorf("expected ErrInvalidCredentials, got %v", err)
		}
	})

	t.Run("Login with inactive user and correct password returns ErrUserInactive", func(t *testing.T) {
		repo := newMockSessionRepo()
		uc := usecase.NewDefaultIdentityUseCase(context.Background(), repo, nil, nil)
		seedTestUser(repo, "disabled", "correct-horse-battery-staple")
		repo.users["disabled"].IsActive = false

		_, err := uc.Login(context.Background(), dto.LoginRequest{
			Username: "disabled",
			Password: "correct-horse-battery-staple",
		}, "test-agent", "127.0.0.1")

		if !errors.Is(err, usecase.ErrUserInactive) {
			t.Errorf("expected ErrUserInactive, got %v", err)
		}
	})

	t.Run("Login with inactive user and wrong password returns ErrInvalidCredentials", func(t *testing.T) {
		repo := newMockSessionRepo()
		uc := usecase.NewDefaultIdentityUseCase(context.Background(), repo, nil, nil)
		seedTestUser(repo, "disabled2", "correct-horse-battery-staple")
		repo.users["disabled2"].IsActive = false

		_, err := uc.Login(context.Background(), dto.LoginRequest{
			Username: "disabled2",
			Password: "wrong-password",
		}, "test-agent", "127.0.0.1")

		if !errors.Is(err, usecase.ErrInvalidCredentials) {
			t.Errorf("expected ErrInvalidCredentials for wrong password on inactive user, got %v", err)
		}
	})

	t.Run("Login success emits event", func(t *testing.T) {
		repo := newMockSessionRepo()
		bus := &mockEventBus{}
		logger := slog.Default()
		uc := usecase.NewDefaultIdentityUseCase(context.Background(), repo, bus, logger)
		seedTestUser(repo, "evtuser", "correct-horse-battery-staple")

		_, err := uc.Login(context.Background(), dto.LoginRequest{
			Username: "evtuser",
			Password: "correct-horse-battery-staple",
		}, "test-agent", "127.0.0.1")

		if err != nil {
			t.Fatalf("unexpected error: %v", err)
		}
		found := false
		for _, evt := range bus.events {
			if evt.EventType() == "user.login_success" {
				found = true
			}
		}
		if !found {
			t.Error("expected user.login_success event to be published")
		}
	})

	t.Run("Login failure emits login_failed event", func(t *testing.T) {
		repo := newMockSessionRepo()
		bus := &mockEventBus{}
		uc := usecase.NewDefaultIdentityUseCase(context.Background(), repo, bus, nil)
		seedTestUser(repo, "evtuser2", "correct-horse-battery-staple")

		_, _ = uc.Login(context.Background(), dto.LoginRequest{
			Username: "evtuser2",
			Password: "wrong",
		}, "test-agent", "127.0.0.1")

		found := false
		for _, evt := range bus.events {
			if evt.EventType() == "user.login_failed" {
				found = true
			}
		}
		if !found {
			t.Error("expected user.login_failed event to be published")
		}
	})

	t.Run("Login lockout emits account_locked event and logs warning", func(t *testing.T) {
		repo := newMockSessionRepo()
		bus := &mockEventBus{}
		logger := slog.Default()
		uc := usecase.NewDefaultIdentityUseCase(context.Background(), repo, bus, logger)
		seedTestUser(repo, "lockevt", "correct-horse-battery-staple")

		for i := 0; i < 5; i++ {
			_, _ = uc.Login(context.Background(), dto.LoginRequest{
				Username: "lockevt",
				Password: "wrong",
			}, "test-agent", "10.0.0.1")
		}

		found := false
		for _, evt := range bus.events {
			if evt.EventType() == "user.account_locked" {
				found = true
			}
		}
		if !found {
			t.Error("expected user.account_locked event to be published")
		}
	})

	t.Run("Login lockout after 5 failed attempts", func(t *testing.T) {
		repo := newMockSessionRepo()
		uc := usecase.NewDefaultIdentityUseCase(context.Background(), repo, nil, nil)
		seedTestUser(repo, "target", "correct-horse-battery-staple")

		for i := 0; i < 5; i++ {
			_, _ = uc.Login(context.Background(), dto.LoginRequest{
				Username: "target",
				Password: "wrong",
			}, "test-agent", "10.0.0.1")
		}

		_, err := uc.Login(context.Background(), dto.LoginRequest{
			Username: "target",
			Password: "correct-horse-battery-staple",
		}, "test-agent", "10.0.0.1")

		if !errors.Is(err, usecase.ErrAccountLocked) {
			t.Errorf("expected ErrAccountLocked after 5 failures, got %v", err)
		}
	})
}
