package repository

import (
	"context"
	"errors"
	"testing"
	"time"

	"github.com/google/uuid"
	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgtype"
	"github.com/matheussouza/inframap/internal/platform/crypto"
	"github.com/matheussouza/inframap/internal/platform/db"
)

type mockQueries struct {
	creds     map[uuid.UUID]db.Credential
	failNext  bool
	failCount bool
}

func newMockQueries() *mockQueries {
	return &mockQueries{creds: make(map[uuid.UUID]db.Credential)}
}

func (m *mockQueries) CreateCredential(_ context.Context, arg db.CreateCredentialParams) (db.Credential, error) {
	if m.failNext {
		m.failNext = false
		return db.Credential{}, errors.New("db error")
	}
	c := db.Credential(arg)
	m.creds[arg.ID] = c
	return c, nil
}

func (m *mockQueries) GetCredentialByID(_ context.Context, id uuid.UUID) (db.Credential, error) {
	if m.failNext {
		m.failNext = false
		return db.Credential{}, errors.New("connection refused")
	}
	c, ok := m.creds[id]
	if !ok {
		return db.Credential{}, pgx.ErrNoRows
	}
	return c, nil
}

func (m *mockQueries) ListCredentials(_ context.Context, _ db.ListCredentialsParams) ([]db.ListCredentialsRow, error) {
	if m.failNext {
		m.failNext = false
		return nil, errors.New("db error")
	}
	rows := make([]db.ListCredentialsRow, 0, len(m.creds))
	for _, v := range m.creds {
		rows = append(rows, db.ListCredentialsRow{
			ID:          v.ID,
			Name:        v.Name,
			Type:        v.Type,
			Description: v.Description,
			CreatedAt:   v.CreatedAt,
			UpdatedAt:   v.UpdatedAt,
		})
	}
	return rows, nil
}

func (m *mockQueries) CountCredentials(_ context.Context) (int64, error) {
	if m.failCount {
		m.failCount = false
		return 0, errors.New("count error")
	}
	return int64(len(m.creds)), nil
}

func (m *mockQueries) DeleteCredential(_ context.Context, id uuid.UUID) (int64, error) {
	if m.failNext {
		m.failNext = false
		return 0, errors.New("db error")
	}
	if _, ok := m.creds[id]; !ok {
		return 0, nil
	}
	delete(m.creds, id)
	return 1, nil
}

func TestNewPgxRepository_Rule8_EncryptorMandatory(t *testing.T) {
	enc, err := crypto.NewAESGCMEncryptor("12345678901234567890123456789012")
	if err != nil {
		t.Fatalf("failed to create test encryptor: %v", err)
	}

	t.Run("Missing Queries Rejection", func(t *testing.T) {
		repo, err := NewPgxRepository(nil, enc)
		if !errors.Is(err, ErrNilQueries) || repo != nil {
			t.Errorf("expected ErrNilQueries, got %v", err)
		}
	})

	t.Run("Missing Encryptor Rejection (Rule 8)", func(t *testing.T) {
		q := &db.Queries{}
		repo, err := NewPgxRepository(q, nil)
		if !errors.Is(err, ErrMissingEncryptor) || repo != nil {
			t.Errorf("expected ErrMissingEncryptor, got %v", err)
		}
	})
}

func newTestRepo(t *testing.T) (*pgxRepository, *mockQueries) {
	t.Helper()
	enc, err := crypto.NewAESGCMEncryptor("12345678901234567890123456789012")
	if err != nil {
		t.Fatalf("failed to create encryptor: %v", err)
	}
	mq := newMockQueries()
	repo := &pgxRepository{queries: mq, encryptor: enc}
	return repo, mq
}

func TestPgxRepository_CreateAndGetByID(t *testing.T) {
	repo, _ := newTestRepo(t)
	ctx := context.Background()

	id := uuid.New()
	cred := &db.Credential{
		ID:          id,
		Name:        "Test Cred",
		Type:        "api_token",
		Description: pgtype.Text{String: "desc", Valid: true},
	}

	created, err := repo.Create(ctx, cred, "my-secret-123")
	if err != nil {
		t.Fatalf("Create failed: %v", err)
	}
	if created.ID != id {
		t.Errorf("expected ID %s, got %s", id, created.ID)
	}
	if created.EncryptedData == "my-secret-123" {
		t.Error("expected encrypted data, got plaintext")
	}

	fetched, secret, err := repo.GetByID(ctx, id)
	if err != nil {
		t.Fatalf("GetByID failed: %v", err)
	}
	if secret != "my-secret-123" {
		t.Errorf("expected decrypted secret 'my-secret-123', got '%s'", secret)
	}
	if fetched.Name != "Test Cred" {
		t.Errorf("expected name 'Test Cred', got '%s'", fetched.Name)
	}
}

func TestPgxRepository_Create_EmptySecret(t *testing.T) {
	repo, _ := newTestRepo(t)
	ctx := context.Background()
	cred := &db.Credential{ID: uuid.New(), Name: "x", Type: "api_token"}

	_, err := repo.Create(ctx, cred, "")
	if err == nil {
		t.Error("expected error for empty secret")
	}
}

func TestPgxRepository_Create_DBError(t *testing.T) {
	repo, mq := newTestRepo(t)
	ctx := context.Background()
	cred := &db.Credential{ID: uuid.New(), Name: "x", Type: "api_token"}

	mq.failNext = true
	_, err := repo.Create(ctx, cred, "secret")
	if err == nil {
		t.Error("expected error on DB failure")
	}
}

func TestPgxRepository_GetByID_NotFound(t *testing.T) {
	repo, _ := newTestRepo(t)
	ctx := context.Background()

	_, _, err := repo.GetByID(ctx, uuid.New())
	if !errors.Is(err, ErrNotFound) {
		t.Errorf("expected ErrNotFound, got %v", err)
	}
}

func TestPgxRepository_GetByID_DBError(t *testing.T) {
	repo, mq := newTestRepo(t)
	ctx := context.Background()

	mq.failNext = true
	_, _, err := repo.GetByID(ctx, uuid.New())
	if err == nil {
		t.Fatal("expected error on DB failure")
	}
	if errors.Is(err, ErrNotFound) {
		t.Error("DB errors must NOT be mapped to ErrNotFound")
	}
}

func TestPgxRepository_List(t *testing.T) {
	repo, _ := newTestRepo(t)
	ctx := context.Background()

	cred := &db.Credential{ID: uuid.New(), Name: "Listed", Type: "ssh_key",
		Description: pgtype.Text{String: "d", Valid: true}}
	_, _ = repo.Create(ctx, cred, "ssh-secret")

	items, total, err := repo.List(ctx, 10, 0)
	if err != nil {
		t.Fatalf("List failed: %v", err)
	}
	if total != 1 || len(items) != 1 {
		t.Errorf("expected 1 item and total 1, got %d items, total %d", len(items), total)
	}
	if items[0].EncryptedData != "" {
		t.Error("List should not include encrypted_data")
	}
}

func TestPgxRepository_List_DefaultLimits(t *testing.T) {
	repo, _ := newTestRepo(t)
	ctx := context.Background()

	items, _, err := repo.List(ctx, -1, -5)
	if err != nil {
		t.Fatalf("List with negative params failed: %v", err)
	}
	if len(items) != 0 {
		t.Errorf("expected 0 items, got %d", len(items))
	}
}

func TestPgxRepository_List_DBError(t *testing.T) {
	repo, mq := newTestRepo(t)
	ctx := context.Background()

	mq.failNext = true
	_, _, err := repo.List(ctx, 10, 0)
	if err == nil {
		t.Error("expected error on list DB failure")
	}
}

func TestPgxRepository_List_CountError(t *testing.T) {
	repo, mq := newTestRepo(t)
	ctx := context.Background()

	mq.failCount = true
	_, _, err := repo.List(ctx, 10, 0)
	if err == nil {
		t.Error("expected error on count DB failure")
	}
}

func TestPgxRepository_Delete(t *testing.T) {
	repo, _ := newTestRepo(t)
	ctx := context.Background()

	id := uuid.New()
	cred := &db.Credential{ID: id, Name: "ToDelete", Type: "custom_secret"}
	_, _ = repo.Create(ctx, cred, "doomed-secret")

	err := repo.Delete(ctx, id)
	if err != nil {
		t.Fatalf("Delete failed: %v", err)
	}

	err = repo.Delete(ctx, id)
	if !errors.Is(err, ErrNotFound) {
		t.Errorf("expected ErrNotFound on second delete, got %v", err)
	}
}

func TestPgxRepository_Delete_DBError(t *testing.T) {
	repo, mq := newTestRepo(t)
	ctx := context.Background()

	mq.failNext = true
	err := repo.Delete(ctx, uuid.New())
	if err == nil {
		t.Error("expected error on delete DB failure")
	}
}

func TestPgxRepository_GetByID_DecryptionFailure(t *testing.T) {
	enc1, _ := crypto.NewAESGCMEncryptor("12345678901234567890123456789012")
	enc2, _ := crypto.NewAESGCMEncryptor("abcdefghijklmnopqrstuvwxyz123456")
	mq := newMockQueries()

	repoWrite := &pgxRepository{queries: mq, encryptor: enc1}
	repoRead := &pgxRepository{queries: mq, encryptor: enc2}
	ctx := context.Background()

	id := uuid.New()
	cred := &db.Credential{ID: id, Name: "WrongKey", Type: "api_token",
		CreatedAt: pgtype.Timestamptz{Time: time.Now(), Valid: true},
		UpdatedAt: pgtype.Timestamptz{Time: time.Now(), Valid: true},
	}
	_, _ = repoWrite.Create(ctx, cred, "secret-data")

	_, _, err := repoRead.GetByID(ctx, id)
	if err == nil {
		t.Error("expected decryption failure with wrong key")
	}
}
