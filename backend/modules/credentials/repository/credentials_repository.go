// Package repository provides PostgreSQL persistence for credentials with AES-256-GCM encryption.
package repository

import (
	"context"
	"errors"
	"fmt"
	"time"

	"github.com/google/uuid"
	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgtype"
	"github.com/matheussouza/inframap/internal/platform/crypto"
	"github.com/matheussouza/inframap/internal/platform/db"
)

var (
	// ErrMissingEncryptor indicates that an encryptor was not provided.
	ErrMissingEncryptor = errors.New("crypto encryptor is required for credential persistence")

	// ErrNotFound indicates the credential record was not found.
	ErrNotFound = errors.New("credential not found")

	// ErrNilQueries indicates db.Queries is nil.
	ErrNilQueries = errors.New("db queries instance is required")
)

// Repository defines persistence contract for credentials.
type Repository interface {
	Create(ctx context.Context, cred *db.Credential, secretPlaintext string) (*db.Credential, error)
	GetByID(ctx context.Context, id uuid.UUID) (*db.Credential, string, error)
	List(ctx context.Context, limit, offset int32) ([]db.Credential, int64, error)
	Delete(ctx context.Context, id uuid.UUID) error
}

type credentialQueries interface {
	CreateCredential(ctx context.Context, arg db.CreateCredentialParams) (db.Credential, error)
	GetCredentialByID(ctx context.Context, id uuid.UUID) (db.Credential, error)
	ListCredentials(ctx context.Context, arg db.ListCredentialsParams) ([]db.ListCredentialsRow, error)
	CountCredentials(ctx context.Context) (int64, error)
	DeleteCredential(ctx context.Context, id uuid.UUID) (int64, error)
}

type pgxRepository struct {
	queries   credentialQueries
	encryptor crypto.Encryptor
}

// NewPgxRepository constructs a new pgxRepository enforcing Rule 8 (Encryptor mandatory).
func NewPgxRepository(queries *db.Queries, encryptor crypto.Encryptor) (Repository, error) {
	if queries == nil {
		return nil, ErrNilQueries
	}
	if encryptor == nil {
		return nil, ErrMissingEncryptor
	}
	return &pgxRepository{
		queries:   queries,
		encryptor: encryptor,
	}, nil
}

// Create encrypts the secret and inserts a new credential into PostgreSQL.
func (r *pgxRepository) Create(ctx context.Context, cred *db.Credential, secretPlaintext string) (*db.Credential, error) {
	if secretPlaintext == "" {
		return nil, fmt.Errorf("cannot encrypt empty secret plaintext")
	}

	encrypted, err := r.encryptor.Encrypt([]byte(secretPlaintext))
	if err != nil {
		return nil, fmt.Errorf("failed to encrypt credential payload: %w", err)
	}

	now := time.Now().UTC()
	created, err := r.queries.CreateCredential(ctx, db.CreateCredentialParams{
		ID:            cred.ID,
		Name:          cred.Name,
		Type:          cred.Type,
		EncryptedData: encrypted,
		Description:   cred.Description,
		CreatedAt:     pgtype.Timestamptz{Time: now, Valid: true},
		UpdatedAt:     pgtype.Timestamptz{Time: now, Valid: true},
	})
	if err != nil {
		return nil, fmt.Errorf("failed to insert credential into database: %w", err)
	}

	return &created, nil
}

// GetByID retrieves a credential and decrypts its secret payload.
func (r *pgxRepository) GetByID(ctx context.Context, id uuid.UUID) (*db.Credential, string, error) {
	cred, err := r.queries.GetCredentialByID(ctx, id)
	if err != nil {
		if errors.Is(err, pgx.ErrNoRows) {
			return nil, "", ErrNotFound
		}
		return nil, "", fmt.Errorf("failed to get credential: %w", err)
	}

	decryptedBytes, err := r.encryptor.Decrypt(cred.EncryptedData)
	if err != nil {
		return nil, "", fmt.Errorf("failed to decrypt credential payload: %w", err)
	}

	return &cred, string(decryptedBytes), nil
}

// List returns a page of credentials (without plaintext secrets) and the total count.
func (r *pgxRepository) List(ctx context.Context, limit, offset int32) ([]db.Credential, int64, error) {
	if limit <= 0 {
		limit = 50
	}
	if offset < 0 {
		offset = 0
	}

	rows, err := r.queries.ListCredentials(ctx, db.ListCredentialsParams{
		Limit:  limit,
		Offset: offset,
	})
	if err != nil {
		return nil, 0, fmt.Errorf("failed to list credentials: %w", err)
	}

	total, err := r.queries.CountCredentials(ctx)
	if err != nil {
		return nil, 0, fmt.Errorf("failed to count credentials: %w", err)
	}

	// Map ListCredentialsRow to db.Credential
	credentials := make([]db.Credential, len(rows))
	for i, r := range rows {
		credentials[i] = db.Credential{
			ID:          r.ID,
			Name:        r.Name,
			Type:        r.Type,
			Description: r.Description,
			CreatedAt:   r.CreatedAt,
			UpdatedAt:   r.UpdatedAt,
		}
	}

	return credentials, total, nil
}

// Delete removes a credential and enforces Rule 11 (execrows check).
func (r *pgxRepository) Delete(ctx context.Context, id uuid.UUID) error {
	rowsAffected, err := r.queries.DeleteCredential(ctx, id)
	if err != nil {
		return fmt.Errorf("failed to delete credential: %w", err)
	}
	if rowsAffected == 0 {
		return ErrNotFound
	}
	return nil
}
