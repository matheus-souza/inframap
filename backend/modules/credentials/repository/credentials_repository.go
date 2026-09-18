// Package repository provides PostgreSQL persistence for credentials with AES-256-GCM encryption.
package repository

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"strings"
	"time"

	"github.com/google/uuid"
	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgtype"
	"github.com/matheussouza/inframap/internal/platform/crypto"
	"github.com/matheussouza/inframap/internal/platform/db"
	"github.com/matheussouza/inframap/modules/credentials/dto"
)

var (
	// ErrMissingEncryptor indicates that an encryptor was not provided.
	ErrMissingEncryptor = errors.New("crypto encryptor is required for credential persistence")

	// ErrNotFound indicates the credential record was not found.
	ErrNotFound = errors.New("credential not found")

	// ErrNilQueries indicates db.Queries is nil.
	ErrNilQueries = errors.New("db queries instance is required")
)

// ErrCredentialInUse indicates the credential cannot be deleted because active discovery sources reference it.
type ErrCredentialInUse struct {
	DependentSources []dto.DependentSource
}

func (e *ErrCredentialInUse) Error() string {
	return "credential is in use by active discovery sources"
}

// TxBeginner abstracts the transaction begin operation from pgxpool.Pool.
type TxBeginner interface {
	Begin(ctx context.Context) (pgx.Tx, error)
}

// Repository defines persistence contract for credentials.
type Repository interface {
	Create(ctx context.Context, cred *db.Credential, secretPlaintext string) (*db.Credential, error)
	GetByID(ctx context.Context, id uuid.UUID) (*db.Credential, string, error)
	List(ctx context.Context, limit, offset int32) ([]db.Credential, int64, error)
	Delete(ctx context.Context, id uuid.UUID) error
	CountActiveReferences(ctx context.Context, id uuid.UUID) (int, []dto.DependentSource, error)
}

type activeReferenceQueries interface {
	ListActiveDiscoverySourcesWithConfig(ctx context.Context) ([]db.ListActiveDiscoverySourcesWithConfigRow, error)
	ListActiveDiscoveryCollectorsWithConfig(ctx context.Context) ([]db.ListActiveDiscoveryCollectorsWithConfigRow, error)
}

type credentialQueries interface {
	activeReferenceQueries
	CreateCredential(ctx context.Context, arg db.CreateCredentialParams) (db.Credential, error)
	GetCredentialByID(ctx context.Context, id uuid.UUID) (db.Credential, error)
	GetCredentialByIDForUpdate(ctx context.Context, id uuid.UUID) (db.Credential, error)
	ListCredentials(ctx context.Context, arg db.ListCredentialsParams) ([]db.ListCredentialsRow, error)
	CountCredentials(ctx context.Context) (int64, error)
	DeleteCredential(ctx context.Context, id uuid.UUID) (int64, error)
}

// PgxRepository implements Repository using PostgreSQL with field-level encryption.
type PgxRepository struct {
	database  db.DBTX
	queries   credentialQueries
	dbQueries *db.Queries
	encryptor crypto.Encryptor
}

// NewPgxRepository constructs a new PgxRepository enforcing Rule 8 (Encryptor mandatory).
func NewPgxRepository(queries *db.Queries, encryptor crypto.Encryptor) (*PgxRepository, error) {
	if queries == nil {
		return nil, ErrNilQueries
	}
	if encryptor == nil {
		return nil, ErrMissingEncryptor
	}
	return &PgxRepository{
		queries:   queries,
		dbQueries: queries,
		encryptor: encryptor,
	}, nil
}

// WithDatabase sets the database connection for transaction management.
func (r *PgxRepository) WithDatabase(database db.DBTX) *PgxRepository {
	r.database = database
	if database != nil {
		r.dbQueries = db.New(database)
	}
	return r
}



// Create encrypts the secret and inserts a new credential into PostgreSQL.
func (r *PgxRepository) Create(ctx context.Context, cred *db.Credential, secretPlaintext string) (*db.Credential, error) {
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
func (r *PgxRepository) GetByID(ctx context.Context, id uuid.UUID) (*db.Credential, string, error) {
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
func (r *PgxRepository) List(ctx context.Context, limit, offset int32) ([]db.Credential, int64, error) {
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

// CountActiveReferences inspects active discovery sources and collectors for references to the credential.
func (r *PgxRepository) CountActiveReferences(ctx context.Context, id uuid.UUID) (int, []dto.DependentSource, error) {
	return r.countActiveReferences(ctx, r.queries, id)
}

func (r *PgxRepository) countActiveReferences(ctx context.Context, q activeReferenceQueries, id uuid.UUID) (int, []dto.DependentSource, error) {
	credIDStr := id.String()
	seen := make(map[string]bool)
	sources := []dto.DependentSource{}

	srcRows, err := q.ListActiveDiscoverySourcesWithConfig(ctx)
	if err != nil {
		return 0, nil, fmt.Errorf("failed to list active discovery sources: %w", err)
	}

	for _, row := range srcRows {
		if !row.ConfigEncrypted.Valid || row.ConfigEncrypted.String == "" {
			continue
		}
		decrypted, err := r.encryptor.Decrypt(row.ConfigEncrypted.String)
		if err != nil {
			continue
		}
		var cfg map[string]interface{}
		if err := json.Unmarshal(decrypted, &cfg); err != nil {
			continue
		}
		if rawID, ok := cfg["credential_id"]; ok && strings.TrimSpace(fmt.Sprint(rawID)) == credIDStr {
			sID := row.ID.String()
			if !seen[sID] {
				seen[sID] = true
				sources = append(sources, dto.DependentSource{
					ID:   sID,
					Name: row.Name,
				})
			}
		}
	}

	colRows, err := q.ListActiveDiscoveryCollectorsWithConfig(ctx)
	if err != nil {
		return 0, nil, fmt.Errorf("failed to list active discovery collectors: %w", err)
	}

	for _, col := range colRows {
		if !col.ConfigEncrypted.Valid || col.ConfigEncrypted.String == "" {
			continue
		}
		decrypted, err := r.encryptor.Decrypt(col.ConfigEncrypted.String)
		if err != nil {
			continue
		}
		var cfg map[string]interface{}
		if err := json.Unmarshal(decrypted, &cfg); err != nil {
			continue
		}
		if rawID, ok := cfg["credential_id"]; ok && strings.TrimSpace(fmt.Sprint(rawID)) == credIDStr {
			sID := col.SourceID.String()
			if !seen[sID] {
				seen[sID] = true
				sources = append(sources, dto.DependentSource{
					ID:   sID,
					Name: col.SourceName,
				})
			}
		}
	}

	return len(sources), sources, nil
}

// Delete removes a credential and enforces Rule 11 (execrows check) and TOCTOU protection.
func (r *PgxRepository) Delete(ctx context.Context, id uuid.UUID) error {
	if r.database != nil && r.dbQueries != nil {
		if beginner, ok := r.database.(TxBeginner); ok {
			tx, err := beginner.Begin(ctx)
			if err != nil {
				return fmt.Errorf("failed to begin transaction: %w", err)
			}
			defer func() { _ = tx.Rollback(ctx) }()

			qtx := r.dbQueries.WithTx(tx)

			// Lock credential row for update
			_, err = qtx.GetCredentialByIDForUpdate(ctx, id)
			if err != nil {
				if errors.Is(err, pgx.ErrNoRows) {
					return ErrNotFound
				}
				return fmt.Errorf("failed to lock credential: %w", err)
			}

			// Atomic reference check within transaction
			count, sources, err := r.countActiveReferences(ctx, qtx, id)
			if err != nil {
				return fmt.Errorf("failed to check active references: %w", err)
			}
			if count > 0 {
				return &ErrCredentialInUse{DependentSources: sources}
			}

			rowsAffected, err := qtx.DeleteCredential(ctx, id)
			if err != nil {
				return fmt.Errorf("failed to delete credential: %w", err)
			}
			if rowsAffected == 0 {
				return ErrNotFound
			}

			if err := tx.Commit(ctx); err != nil {
				return fmt.Errorf("failed to commit delete transaction: %w", err)
			}
			return nil
		}
	}

	// Fallback when database TxBeginner is not configured (e.g. basic mock query unit tests)
	rowsAffected, err := r.queries.DeleteCredential(ctx, id)
	if err != nil {
		return fmt.Errorf("failed to delete credential: %w", err)
	}
	if rowsAffected == 0 {
		return ErrNotFound
	}
	return nil
}

