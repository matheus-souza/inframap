package usecase_test

import (
	"context"
	"errors"
	"log/slog"
	"os"
	"testing"
	"time"

	"github.com/google/uuid"
	"github.com/matheussouza/inframap/modules/identity/repository"
	"github.com/matheussouza/inframap/modules/identity/usecase"
)

type mockSessionRepository struct {
	tokens       map[string]*repository.SessionData
	userPerms    map[uuid.UUID][]string
	generateErr  error
	createErr    error
	getErr       error
	revokeErr    error
	permsErr     error
}

func newMockSessionRepository() *mockSessionRepository {
	return &mockSessionRepository{
		tokens:    make(map[string]*repository.SessionData),
		userPerms: make(map[uuid.UUID][]string),
	}
}

func (m *mockSessionRepository) GenerateToken() (string, error) {
	if m.generateErr != nil {
		return "", m.generateErr
	}
	return "ims_mocktoken1234567890123456789012345678901234567890", nil
}

func (m *mockSessionRepository) HashToken(token string) string {
	return "hash_" + token
}

func (m *mockSessionRepository) CreateSession(_ context.Context, userID uuid.UUID, token, _, _ string) (*repository.SessionData, error) {
	if m.createErr != nil {
		return nil, m.createErr
	}
	data := &repository.SessionData{
		SessionID: uuid.New().String(),
		UserID:    userID,
		ExpiresAt: time.Now().Add(30 * time.Minute),
		CreatedAt: time.Now(),
	}
	m.tokens[token] = data
	return data, nil
}

func (m *mockSessionRepository) GetSessionByToken(_ context.Context, token string) (*repository.SessionData, error) {
	if m.getErr != nil {
		return nil, m.getErr
	}
	data, exists := m.tokens[token]
	if !exists {
		return nil, repository.ErrSessionNotFound
	}
	return data, nil
}

func (m *mockSessionRepository) RevokeSession(_ context.Context, token string) error {
	if m.revokeErr != nil {
		return m.revokeErr
	}
	delete(m.tokens, token)
	return nil
}

func (m *mockSessionRepository) GetUserPermissions(_ context.Context, userID uuid.UUID) ([]string, error) {
	if m.permsErr != nil {
		return nil, m.permsErr
	}
	return m.userPerms[userID], nil
}

func TestIdentityUseCase_Unit(t *testing.T) {
	logger := slog.New(slog.NewJSONHandler(os.Stdout, nil))
	mockRepo := newMockSessionRepository()
	uc := usecase.NewDefaultIdentityUseCase(nil, mockRepo, nil, logger)

	t.Run("Logout with empty token returns nil", func(t *testing.T) {
		err := uc.Logout(context.Background(), "")
		if err != nil {
			t.Errorf("expected nil error on empty token logout, got %v", err)
		}
	})

	t.Run("Logout revokes active session", func(t *testing.T) {
		token := "ims_testtoken"
		mockRepo.tokens[token] = &repository.SessionData{
			SessionID: "sess-1",
			UserID:    uuid.New(),
		}

		err := uc.Logout(context.Background(), token)
		if err != nil {
			t.Errorf("unexpected error on logout: %v", err)
		}

		if _, exists := mockRepo.tokens[token]; exists {
			t.Error("expected token to be revoked from repository")
		}
	})

	t.Run("GetMe with non-existent token returns error", func(t *testing.T) {
		_, err := uc.GetMe(context.Background(), "ims_nonexistent")
		if err == nil {
			t.Error("expected error for non-existent token, got nil")
		}
	})

	t.Run("GetMe with valid token returns profile", func(t *testing.T) {
		userID := uuid.New()
		token := "ims_validtoken"
		mockRepo.tokens[token] = &repository.SessionData{
			SessionID: "sess-2",
			UserID:    userID,
			Username:  "john",
			Email:     "john@example.com",
			FullName:  "John Doe",
			IsActive:  true,
		}
		mockRepo.userPerms[userID] = []string{"read:system", "write:system"}

		resp, err := uc.GetMe(context.Background(), token)
		if err != nil {
			t.Fatalf("unexpected error on GetMe: %v", err)
		}

		if resp.Username != "john" {
			t.Errorf("expected username john, got %s", resp.Username)
		}

		if len(resp.Permissions) != 2 {
			t.Errorf("expected 2 permissions, got %d", len(resp.Permissions))
		}
	})

	t.Run("GetMe handles permission error", func(t *testing.T) {
		userID := uuid.New()
		token := "ims_permerrtoken"
		mockRepo.tokens[token] = &repository.SessionData{
			SessionID: "sess-3",
			UserID:    userID,
			Username:  "jane",
		}
		mockRepo.permsErr = errors.New("db error")

		_, err := uc.GetMe(context.Background(), token)
		if err == nil {
			t.Error("expected error when GetUserPermissions fails, got nil")
		}
		mockRepo.permsErr = nil
	})
}
