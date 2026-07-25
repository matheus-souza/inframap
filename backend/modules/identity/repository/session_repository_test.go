package repository_test

import (
	"strings"
	"testing"

	"github.com/matheussouza/inframap/modules/identity/repository"
)

func TestSessionRepository_Unit(t *testing.T) {
	repo := repository.NewPgSessionRepository(nil)

	t.Run("GenerateToken returns prefixed 68-char token", func(t *testing.T) {
		token, err := repo.GenerateToken()
		if err != nil {
			t.Fatalf("unexpected error generating token: %v", err)
		}

		if !strings.HasPrefix(token, repository.SessionPrefix) {
			t.Errorf("expected token prefix %s, got %s", repository.SessionPrefix, token)
		}

		// ims_ (4 chars) + 64 hex chars = 68 chars
		if len(token) != 68 {
			t.Errorf("expected token length 68, got %d", len(token))
		}
	})

	t.Run("HashToken produces deterministic SHA-256 string", func(t *testing.T) {
		token := "ims_1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef"
		hash1 := repo.HashToken(token)
		hash2 := repo.HashToken(token)

		if hash1 == "" {
			t.Error("expected non-empty hash string")
		}
		if hash1 != hash2 {
			t.Errorf("hash mismatch: %s != %s", hash1, hash2)
		}
		if len(hash1) != 64 {
			t.Errorf("expected hash length 64, got %d", len(hash1))
		}
	})

	t.Run("HashToken produces different hashes for different tokens", func(t *testing.T) {
		tokenA := "ims_token_A"
		tokenB := "ims_token_B"

		hashA := repo.HashToken(tokenA)
		hashB := repo.HashToken(tokenB)

		if hashA == hashB {
			t.Error("expected different hashes for different tokens")
		}
	})
}
