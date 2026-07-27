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
	"github.com/matheussouza/inframap/modules/credentials/dto"
	"github.com/matheussouza/inframap/modules/credentials/repository"
	"github.com/matheussouza/inframap/modules/credentials/usecase"
)

type mockRepo struct {
	items       map[uuid.UUID]db.Credential
	secrets     map[uuid.UUID]string
	shouldError bool
}

func newMockRepo() *mockRepo {
	return &mockRepo{
		items:   make(map[uuid.UUID]db.Credential),
		secrets: make(map[uuid.UUID]string),
	}
}

func (m *mockRepo) Create(_ context.Context, cred *db.Credential, secret string) (*db.Credential, error) {
	if m.shouldError {
		return nil, errors.New("db error")
	}
	now := time.Now().UTC()
	cred.CreatedAt = pgtype.Timestamptz{Time: now, Valid: true}
	cred.UpdatedAt = pgtype.Timestamptz{Time: now, Valid: true}
	m.items[cred.ID] = *cred
	m.secrets[cred.ID] = secret
	return cred, nil
}

func (m *mockRepo) GetByID(_ context.Context, id uuid.UUID) (*db.Credential, string, error) {
	if m.shouldError {
		return nil, "", errors.New("db error")
	}
	c, ok := m.items[id]
	if !ok {
		return nil, "", repository.ErrNotFound
	}
	return &c, m.secrets[id], nil
}

func (m *mockRepo) List(_ context.Context, _, _ int32) ([]db.Credential, int64, error) {
	if m.shouldError {
		return nil, 0, errors.New("db error")
	}
	res := make([]db.Credential, 0, len(m.items))
	for _, v := range m.items {
		res = append(res, v)
	}
	return res, int64(len(m.items)), nil
}

func (m *mockRepo) Delete(_ context.Context, id uuid.UUID) error {
	if m.shouldError {
		return errors.New("db error")
	}
	if _, ok := m.items[id]; !ok {
		return repository.ErrNotFound
	}
	delete(m.items, id)
	delete(m.secrets, id)
	return nil
}

func TestCredentialsUseCase_Unit(t *testing.T) {
	repo := newMockRepo()
	bus := eventbus.NewInMemoryEventBus(1, 10)
	defer func() {
		_ = bus.Close()
	}()

	uc, err := usecase.NewCredentialsUseCase(repo, bus)
	if err != nil {
		t.Fatalf("expected nil error on NewCredentialsUseCase, got %v", err)
	}

	ctx := context.Background()

	t.Run("CreateCredential Success & Validation Failure & DB Error", func(t *testing.T) {
		// Validation error
		_, err := uc.CreateCredential(ctx, dto.CreateCredentialRequest{Name: ""})
		if err == nil {
			t.Error("expected validation error on empty name")
		}

		res, err := uc.CreateCredential(ctx, dto.CreateCredentialRequest{
			Name:        "SSH Homelab Key",
			Type:        "ssh_key",
			SecretData:  "-----BEGIN RSA PRIVATE KEY-----",
			Description: "Main SSH Key",
		})
		if err != nil {
			t.Fatalf("expected nil error, got %v", err)
		}
		if res.Name != "SSH Homelab Key" || res.Type != "ssh_key" {
			t.Errorf("unexpected credential response: %+v", res)
		}

		// Retrieve by ID
		fetched, err := uc.GetCredentialByID(ctx, res.ID)
		if err != nil {
			t.Fatalf("expected nil error on GetCredentialByID, got %v", err)
		}
		if fetched.SecretData != "-----BEGIN RSA PRIVATE KEY-----" {
			t.Errorf("expected decrypted secret data, got %s", fetched.SecretData)
		}

		// DB Error
		repo.shouldError = true
		_, err = uc.CreateCredential(ctx, dto.CreateCredentialRequest{
			Name:       "Fail",
			Type:       "api_token",
			SecretData: "secret",
		})
		if err == nil {
			t.Error("expected DB error on CreateCredential")
		}
		repo.shouldError = false
	})

	t.Run("GetCredentialByID Invalid UUID & DB Error", func(t *testing.T) {
		_, err := uc.GetCredentialByID(ctx, "invalid-uuid")
		if err != usecase.ErrInvalidCredentialID {
			t.Errorf("expected ErrInvalidCredentialID, got %v", err)
		}

		repo.shouldError = true
		validID := uuid.New().String()
		_, err = uc.GetCredentialByID(ctx, validID)
		if err == nil {
			t.Error("expected DB error on GetCredentialByID")
		}
		repo.shouldError = false
	})

	t.Run("ListCredentials Success & DB Error", func(t *testing.T) {
		list, total, err := uc.ListCredentials(ctx, -1, 200)
		if err != nil {
			t.Fatalf("expected nil error on ListCredentials, got %v", err)
		}
		if total < 1 || len(list) < 1 {
			t.Errorf("expected at least 1 item, got count %d", total)
		}
		if list[0].SecretData != "" {
			t.Errorf("expected secret_data to be masked in list response, got %s", list[0].SecretData)
		}

		repo.shouldError = true
		_, _, err = uc.ListCredentials(ctx, 1, 10)
		if err == nil {
			t.Error("expected DB error on ListCredentials")
		}
		repo.shouldError = false
	})

	t.Run("DeleteCredential Success, Invalid UUID & DB Error", func(t *testing.T) {
		// Invalid UUID
		if err := uc.DeleteCredential(ctx, "bad-id"); err != usecase.ErrInvalidCredentialID {
			t.Errorf("expected ErrInvalidCredentialID, got %v", err)
		}

		created, _ := uc.CreateCredential(ctx, dto.CreateCredentialRequest{
			Name:       "To Delete",
			Type:       "api_token",
			SecretData: "token-secret",
		})

		repo.shouldError = true
		if err := uc.DeleteCredential(ctx, created.ID); err == nil {
			t.Error("expected DB error on DeleteCredential")
		}
		repo.shouldError = false

		if err := uc.DeleteCredential(ctx, created.ID); err != nil {
			t.Errorf("expected nil error on DeleteCredential, got %v", err)
		}

		if err := uc.DeleteCredential(ctx, created.ID); err != repository.ErrNotFound {
			t.Errorf("expected ErrNotFound on second delete, got %v", err)
		}
	})

	t.Run("Nil Repository Constructor Error", func(t *testing.T) {
		_, err := usecase.NewCredentialsUseCase(nil, bus)
		if err != usecase.ErrNilRepository {
			t.Errorf("expected ErrNilRepository, got %v", err)
		}
	})
}
