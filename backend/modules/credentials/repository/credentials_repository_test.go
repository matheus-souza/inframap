package repository_test

import (
	"context"
	"testing"
	"time"

	"github.com/google/uuid"
	"github.com/jackc/pgx/v5/pgtype"
	"github.com/matheussouza/inframap/internal/platform/crypto"
	"github.com/matheussouza/inframap/internal/platform/db"
	"github.com/matheussouza/inframap/modules/credentials/repository"
)

type mockQueries struct {
	creds map[uuid.UUID]db.Credential
}

func newMockQueries() *mockQueries {
	return &mockQueries{creds: make(map[uuid.UUID]db.Credential)}
}

func (m *mockQueries) CreateCredential(_ context.Context, arg db.CreateCredentialParams) (db.Credential, error) {
	c := db.Credential{
		ID:            arg.ID,
		Name:          arg.Name,
		Type:          arg.Type,
		EncryptedData: arg.EncryptedData,
		Description:   arg.Description,
		CreatedAt:     arg.CreatedAt,
		UpdatedAt:     arg.UpdatedAt,
	}
	m.creds[arg.ID] = c
	return c, nil
}

func (m *mockQueries) GetCredentialByID(_ context.Context, id uuid.UUID) (db.Credential, error) {
	c, ok := m.creds[id]
	if !ok {
		return db.Credential{}, repository.ErrNotFound
	}
	return c, nil
}

func (m *mockQueries) ListCredentials(_ context.Context, _ db.ListCredentialsParams) ([]db.ListCredentialsRow, error) {
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
	return int64(len(m.creds)), nil
}

func (m *mockQueries) DeleteCredential(_ context.Context, id uuid.UUID) (int64, error) {
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
		repo, err := repository.NewPgxRepository(nil, enc)
		if err != repository.ErrNilQueries || repo != nil {
			t.Errorf("expected ErrNilQueries, got %v", err)
		}
	})

	t.Run("Missing Encryptor Rejection (Rule 8)", func(t *testing.T) {
		q := &db.Queries{}
		repo, err := repository.NewPgxRepository(q, nil)
		if err != repository.ErrMissingEncryptor || repo != nil {
			t.Errorf("expected ErrMissingEncryptor, got %v", err)
		}
	})
}

func TestPgxRepository_Methods(t *testing.T) {
	enc, _ := crypto.NewAESGCMEncryptor("12345678901234567890123456789012")
	mq := newMockQueries()

	// We test the contract directly
	ctx := context.Background()
	id := uuid.New()
	cred := &db.Credential{
		ID:          id,
		Name:        "Encrypted Test",
		Type:        "api_token",
		Description: pgtype.Text{String: "desc", Valid: true},
	}

	encryptedData, _ := enc.Encrypt([]byte("my-secret-123"))
	mq.CreateCredential(ctx, db.CreateCredentialParams{
		ID:            cred.ID,
		Name:          cred.Name,
		Type:          cred.Type,
		EncryptedData: encryptedData,
		Description:   cred.Description,
		CreatedAt:     pgtype.Timestamptz{Time: time.Now(), Valid: true},
		UpdatedAt:     pgtype.Timestamptz{Time: time.Now(), Valid: true},
	})

	// Get & Decrypt
	fetched, err := mq.GetCredentialByID(ctx, id)
	if err != nil {
		t.Fatalf("expected nil error on GetCredentialByID, got %v", err)
	}
	decrypted, err := enc.Decrypt(fetched.EncryptedData)
	if err != nil || string(decrypted) != "my-secret-123" {
		t.Errorf("expected decrypted my-secret-123, got %s (err: %v)", string(decrypted), err)
	}

	// List
	rows, err := mq.ListCredentials(ctx, db.ListCredentialsParams{Limit: 10, Offset: 0})
	if err != nil || len(rows) != 1 {
		t.Errorf("expected 1 list row, got %d (err: %v)", len(rows), err)
	}

	count, _ := mq.CountCredentials(ctx)
	if count != 1 {
		t.Errorf("expected count 1, got %d", count)
	}

	// Delete
	affected, err := mq.DeleteCredential(ctx, id)
	if err != nil || affected != 1 {
		t.Errorf("expected affected 1 on delete, got %d (err: %v)", affected, err)
	}

	// Second delete returns 0 affected
	affected2, _ := mq.DeleteCredential(ctx, id)
	if affected2 != 0 {
		t.Errorf("expected 0 affected on second delete, got %d", affected2)
	}
}
