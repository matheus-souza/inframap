// Package repository provides persistence operations for sessions, users, and RBAC permissions.
package repository

import (
	"context"
	"crypto/rand"
	"crypto/sha256"
	"encoding/hex"
	"errors"
	"fmt"
	"net/netip"
	"time"

	"github.com/google/uuid"
	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgtype"
	"github.com/jackc/pgx/v5/pgxpool"
	"github.com/matheussouza/inframap/internal/platform/db"
)

// SessionPrefix is the required prefix for InfraMap stateful session tokens.
const SessionPrefix = "ims_"

// SlidingInactivityDuration is the 30-minute sliding inactivity timeout.
const SlidingInactivityDuration = 30 * time.Minute

// AbsoluteMaxDuration is the 7-day hard maximum session lifetime.
const AbsoluteMaxDuration = 7 * 24 * time.Hour

var (
	// ErrSessionNotFound indicates that the session token does not exist in the database.
	ErrSessionNotFound = errors.New("session not found")

	// ErrSessionExpired indicates that the session has passed its expiration limit.
	ErrSessionExpired = errors.New("session has expired")
)

// ErrUserNotFound indicates that no user matched the lookup criteria.
var ErrUserNotFound = errors.New("user not found")

// SessionData holds session details along with joined user info.
type SessionData struct {
	SessionID string
	UserID    uuid.UUID
	Username  string
	Email     string
	FullName  string
	IsActive  bool
	ExpiresAt time.Time
	CreatedAt time.Time
}

// UserCredentials holds the fields needed for authentication.
type UserCredentials struct {
	ID           uuid.UUID
	Username     string
	Email        string
	PasswordHash string
	FullName     string
	IsActive     bool
}

// SessionRepository defines the contract for stateful session persistence.
type SessionRepository interface {
	GenerateToken() (string, error)
	HashToken(token string) string
	CreateSession(ctx context.Context, userID uuid.UUID, token, userAgent, ipAddress string) (*SessionData, error)
	GetSessionByToken(ctx context.Context, token string) (*SessionData, error)
	RevokeSession(ctx context.Context, token string) error
	GetUserPermissions(ctx context.Context, userID uuid.UUID) ([]string, error)
	LookupUserByUsername(ctx context.Context, username string) (*UserCredentials, error)
}

// PgSessionRepository implements SessionRepository backed by PostgreSQL via pgxpool.
type PgSessionRepository struct {
	pool *pgxpool.Pool
}

// NewPgSessionRepository creates a new PgSessionRepository instance.
func NewPgSessionRepository(pool *pgxpool.Pool) *PgSessionRepository {
	return &PgSessionRepository{pool: pool}
}

// GenerateToken generates a cryptographically secure token prefixed with ims_.
func (r *PgSessionRepository) GenerateToken() (string, error) {
	bytes := make([]byte, 32)
	if _, err := rand.Read(bytes); err != nil {
		return "", fmt.Errorf("failed to generate random bytes for token: %w", err)
	}
	return SessionPrefix + hex.EncodeToString(bytes), nil
}

// HashToken computes the SHA-256 hash of a session token for secure database storage.
func (r *PgSessionRepository) HashToken(token string) string {
	hash := sha256.Sum256([]byte(token))
	return hex.EncodeToString(hash[:])
}

// CreateSession persists a new user session with a 30-minute sliding expiration.
func (r *PgSessionRepository) CreateSession(ctx context.Context, userID uuid.UUID, token, userAgent, ipAddress string) (*SessionData, error) {
	queries := db.New(r.pool)

	sessionID, err := uuid.NewV7()
	if err != nil {
		return nil, err
	}

	tokenHash := r.HashToken(token)
	expiresAt := time.Now().Add(SlidingInactivityDuration)

	var ipAddr *netip.Addr
	if ipAddress != "" {
		if addr, parseErr := netip.ParseAddr(ipAddress); parseErr == nil {
			ipAddr = &addr
		}
	}

	var uAgent pgtype.Text
	if userAgent != "" {
		uAgent = pgtype.Text{String: userAgent, Valid: true}
	}

	sessionRow, err := queries.CreateSession(ctx, db.CreateSessionParams{
		ID:        sessionID,
		UserID:    userID,
		TokenHash: tokenHash,
		UserAgent: uAgent,
		IpAddress: ipAddr,
		ExpiresAt: pgtype.Timestamptz{Time: expiresAt, Valid: true},
	})
	if err != nil {
		return nil, fmt.Errorf("failed to create session: %w", err)
	}

	return &SessionData{
		SessionID: sessionRow.ID.String(),
		UserID:    sessionRow.UserID,
		ExpiresAt: sessionRow.ExpiresAt.Time,
		CreatedAt: sessionRow.CreatedAt.Time,
	}, nil
}

// GetSessionByToken retrieves and validates a session by its token, extending sliding expiry if active.
func (r *PgSessionRepository) GetSessionByToken(ctx context.Context, token string) (*SessionData, error) {
	queries := db.New(r.pool)
	tokenHash := r.HashToken(token)

	row, err := queries.GetSessionByHash(ctx, tokenHash)
	if err != nil {
		if errors.Is(err, pgx.ErrNoRows) {
			return nil, ErrSessionNotFound
		}
		return nil, fmt.Errorf("failed to lookup session: %w", err)
	}

	now := time.Now()

	// Check sliding 30-minute expiration
	if now.After(row.ExpiresAt.Time) {
		_ = queries.DeleteSession(ctx, tokenHash)
		return nil, ErrSessionExpired
	}

	// Check 7-day hard absolute expiration
	if now.Sub(row.CreatedAt.Time) > AbsoluteMaxDuration {
		_ = queries.DeleteSession(ctx, tokenHash)
		return nil, ErrSessionExpired
	}

	// Extend sliding expiration by 30 minutes
	newExpiry := now.Add(SlidingInactivityDuration)
	if updateErr := queries.UpdateSessionExpiry(ctx, db.UpdateSessionExpiryParams{
		ID:        row.ID,
		ExpiresAt: pgtype.Timestamptz{Time: newExpiry, Valid: true},
	}); updateErr != nil {
		return nil, fmt.Errorf("failed to update session expiry: %w", updateErr)
	}

	return &SessionData{
		SessionID: row.ID.String(),
		UserID:    row.UserID,
		Username:  row.Username,
		Email:     row.Email,
		FullName:  row.FullName,
		IsActive:  row.IsActive,
		ExpiresAt: newExpiry,
		CreatedAt: row.CreatedAt.Time,
	}, nil
}

// RevokeSession deletes a session by token hash.
func (r *PgSessionRepository) RevokeSession(ctx context.Context, token string) error {
	queries := db.New(r.pool)
	tokenHash := r.HashToken(token)
	return queries.DeleteSession(ctx, tokenHash)
}

// LookupUserByUsername finds a user by username or email (case-insensitive).
func (r *PgSessionRepository) LookupUserByUsername(ctx context.Context, username string) (*UserCredentials, error) {
	row := r.pool.QueryRow(ctx,
		"SELECT id, username, email, password_hash, full_name, is_active FROM users WHERE LOWER(username) = $1 OR LOWER(email) = $1 LIMIT 1",
		username,
	)

	var creds UserCredentials
	err := row.Scan(&creds.ID, &creds.Username, &creds.Email, &creds.PasswordHash, &creds.FullName, &creds.IsActive)
	if err != nil {
		if errors.Is(err, pgx.ErrNoRows) {
			return nil, ErrUserNotFound
		}
		return nil, fmt.Errorf("failed to lookup user: %w", err)
	}

	return &creds, nil
}

// GetUserPermissions retrieves assigned RBAC permissions for a user.
func (r *PgSessionRepository) GetUserPermissions(ctx context.Context, userID uuid.UUID) ([]string, error) {
	queries := db.New(r.pool)
	perms, err := queries.GetUserPermissions(ctx, userID)
	if err != nil {
		return nil, fmt.Errorf("failed to fetch user permissions: %w", err)
	}
	return perms, nil
}
