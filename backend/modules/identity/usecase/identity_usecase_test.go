package usecase_test

import (
	"context"
	"errors"
	"testing"
	"time"

	"github.com/google/uuid"
	"github.com/matheussouza/inframap/modules/identity/repository"
	"github.com/matheussouza/inframap/modules/identity/usecase"
)

type mockSessionRepo struct {
	sessions    map[string]*repository.SessionData
	permissions map[uuid.UUID][]string
	tokenErr    error
	sessionErr  error
	permErr     error
}

func newMockSessionRepo() *mockSessionRepo {
	return &mockSessionRepo{
		sessions:    make(map[string]*repository.SessionData),
		permissions: make(map[uuid.UUID][]string),
	}
}

func (m *mockSessionRepo) GenerateToken() (string, error) {
	if m.tokenErr != nil {
		return "", m.tokenErr
	}
	return "ims_mock_token_1234567890abcdef", nil
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

func TestIdentityUseCase_Unit(t *testing.T) {
	repo := newMockSessionRepo()
	uc := usecase.NewDefaultIdentityUseCase(nil, repo, nil, nil)

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
