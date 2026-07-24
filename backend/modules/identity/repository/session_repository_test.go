package repository_test

import (
	"testing"

	"github.com/matheussouza/inframap/modules/identity/repository"
)

func TestSessionRepository_GenerateAndHashToken(t *testing.T) {
	repo := repository.NewPgSessionRepository(nil)

	token, err := repo.GenerateToken()
	if err != nil {
		t.Fatalf("unexpected error generating token: %v", err)
	}

	if len(token) < 10 {
		t.Errorf("token too short: %s", token)
	}

	hash := repo.HashToken(token)
	if len(hash) != 64 {
		t.Errorf("expected 64 char hex SHA-256 hash, got length %d", len(hash))
	}
}
