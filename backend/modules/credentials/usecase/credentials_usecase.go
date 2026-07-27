// Package usecase implements business logic for the Credentials module.
package usecase

import (
	"context"
	"errors"
	"log/slog"

	"github.com/google/uuid"
	"github.com/jackc/pgx/v5/pgtype"
	"github.com/matheussouza/inframap/internal/platform/db"
	"github.com/matheussouza/inframap/internal/platform/eventbus"
	"github.com/matheussouza/inframap/modules/credentials/dto"
	"github.com/matheussouza/inframap/modules/credentials/repository"
)

var (
	// ErrInvalidCredentialID indicates the UUID is invalid or empty.
	ErrInvalidCredentialID = errors.New("invalid credential id: must be a valid UUIDv7")

	// ErrNilRepository indicates the repository dependency is missing.
	ErrNilRepository = errors.New("credentials repository is required")
)

// UseCase defines business logic contract for managing credentials.
type UseCase interface {
	CreateCredential(ctx context.Context, req dto.CreateCredentialRequest) (*dto.CredentialResponse, error)
	GetCredentialByID(ctx context.Context, idStr string) (*dto.CredentialResponse, error)
	ListCredentials(ctx context.Context, page, perPage int32) ([]dto.CredentialResponse, int64, error)
	DeleteCredential(ctx context.Context, idStr string) error
}

type credentialsUseCase struct {
	repo repository.Repository
	bus  eventbus.EventBus
}

// NewCredentialsUseCase constructs a new UseCase instance.
func NewCredentialsUseCase(repo repository.Repository, bus eventbus.EventBus) (UseCase, error) {
	if repo == nil {
		return nil, ErrNilRepository
	}
	return &credentialsUseCase{
		repo: repo,
		bus:  bus,
	}, nil
}

// CreateCredential validates, constructs UUIDv7, persists encrypted secret, and publishes domain event.
func (uc *credentialsUseCase) CreateCredential(ctx context.Context, req dto.CreateCredentialRequest) (*dto.CredentialResponse, error) {
	req.Normalize()
	if err := req.Validate(); err != nil {
		return nil, err
	}

	id, err := uuid.NewV7()
	if err != nil {
		slog.Warn("UUIDv7 generation failed, falling back to UUIDv4", "error", err)
		id = uuid.New()
	}

	var descText pgtype.Text
	if req.Description != "" {
		descText = pgtype.Text{String: req.Description, Valid: true}
	}

	credInput := &db.Credential{
		ID:          id,
		Name:        req.Name,
		Type:        req.Type,
		Description: descText,
	}

	created, err := uc.repo.Create(ctx, credInput, req.SecretData)
	if err != nil {
		return nil, err
	}

	res := uc.toResponse(created, "")

	if uc.bus != nil {
		if err := uc.bus.Publish(ctx, eventbus.NewBaseEvent("credential.created", map[string]interface{}{
			"credential_id": created.ID.String(),
			"name":          created.Name,
			"type":          created.Type,
		})); err != nil {
			slog.Warn("failed to publish credential.created event", "credential_id", created.ID, "error", err)
		}
	}

	return res, nil
}

// GetCredentialByID parses UUID, retrieves credential metadata and decrypted secret payload.
func (uc *credentialsUseCase) GetCredentialByID(ctx context.Context, idStr string) (*dto.CredentialResponse, error) {
	id, err := uuid.Parse(idStr)
	if err != nil || id == uuid.Nil {
		return nil, ErrInvalidCredentialID
	}

	cred, plaintextSecret, err := uc.repo.GetByID(ctx, id)
	if err != nil {
		return nil, err
	}

	return uc.toResponse(cred, plaintextSecret), nil
}

// ListCredentials returns offset-paginated credentials with secrets masked.
func (uc *credentialsUseCase) ListCredentials(ctx context.Context, page, perPage int32) ([]dto.CredentialResponse, int64, error) {
	if page < 1 {
		page = 1
	}
	if perPage < 1 || perPage > 100 {
		perPage = 50
	}

	offset := (page - 1) * perPage

	creds, total, err := uc.repo.List(ctx, perPage, offset)
	if err != nil {
		return nil, 0, err
	}

	responses := make([]dto.CredentialResponse, len(creds))
	for i, c := range creds {
		responses[i] = *uc.toResponse(&c, "")
	}

	return responses, total, nil
}

// DeleteCredential parses UUID, removes record, and publishes domain event.
func (uc *credentialsUseCase) DeleteCredential(ctx context.Context, idStr string) error {
	id, err := uuid.Parse(idStr)
	if err != nil || id == uuid.Nil {
		return ErrInvalidCredentialID
	}

	if err := uc.repo.Delete(ctx, id); err != nil {
		return err
	}

	if uc.bus != nil {
		if err := uc.bus.Publish(ctx, eventbus.NewBaseEvent("credential.deleted", map[string]interface{}{
			"credential_id": id.String(),
		})); err != nil {
			slog.Warn("failed to publish credential.deleted event", "credential_id", id, "error", err)
		}
	}

	return nil
}

func (uc *credentialsUseCase) toResponse(cred *db.Credential, secret string) *dto.CredentialResponse {
	var desc string
	if cred.Description.Valid {
		desc = cred.Description.String
	}

	return &dto.CredentialResponse{
		ID:          cred.ID.String(),
		Name:        cred.Name,
		Type:        cred.Type,
		Description: desc,
		SecretData:  secret,
		CreatedAt:   cred.CreatedAt.Time,
		UpdatedAt:   cred.UpdatedAt.Time,
	}
}
