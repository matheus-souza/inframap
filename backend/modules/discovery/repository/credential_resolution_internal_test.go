package repository

import (
	"context"
	"errors"
	"testing"

	"github.com/google/uuid"
	"github.com/matheussouza/inframap/internal/platform/db"
	"github.com/matheussouza/inframap/internal/platform/sdk"
)

type stubCredentialResolver struct {
	secret string
	err    error
	calls  []uuid.UUID
}

func (s *stubCredentialResolver) GetByID(_ context.Context, id uuid.UUID) (*db.Credential, string, error) {
	s.calls = append(s.calls, id)
	if s.err != nil {
		return nil, "", s.err
	}
	return &db.Credential{ID: id}, s.secret, nil
}

func TestResolveCredentialReference(t *testing.T) {
	credentialID := uuid.New()

	t.Run("merges the credential secrets into the config", func(t *testing.T) {
		resolver := &stubCredentialResolver{secret: `{"token_id":"root@pam!ref","token_secret":"s3cr3t"}`}
		repo := &PgDiscoveryRepository{credentials: resolver}

		config := sdk.ProviderConfig{
			"api_url":           "https://pve.local:8006",
			CredentialConfigKey: credentialID.String(),
		}

		resolved, err := repo.resolveCredentialReference(context.Background(), config, "proxmox")
		if err != nil {
			t.Fatalf("unexpected error: %v", err)
		}
		if resolved["token_id"] != "root@pam!ref" || resolved["token_secret"] != "s3cr3t" {
			t.Errorf("credential secrets did not reach the config: %v", resolved)
		}
		if resolved["api_url"] != "https://pve.local:8006" {
			t.Error("the collector's own settings must survive the merge")
		}
		// The reference is not a provider setting and must not reach the provider, where it
		// would be an unknown key at best and a logged secret identifier at worst.
		if _, present := resolved[CredentialConfigKey]; present {
			t.Error("the credential reference must be stripped from the resolved config")
		}
		if len(resolver.calls) != 1 || resolver.calls[0] != credentialID {
			t.Errorf("expected exactly one lookup of %s, got %v", credentialID, resolver.calls)
		}
	})

	t.Run("values written on the collector win over the credential", func(t *testing.T) {
		// An operator can point several plans at one shared credential and still override a
		// single setting for one of them.
		resolver := &stubCredentialResolver{secret: `{"api_url":"https://shared:8006","token_id":"shared"}`}
		repo := &PgDiscoveryRepository{credentials: resolver}

		resolved, err := repo.resolveCredentialReference(
			context.Background(),
			sdk.ProviderConfig{"api_url": "https://override:8006", CredentialConfigKey: credentialID.String()},
			"proxmox",
		)
		if err != nil {
			t.Fatalf("unexpected error: %v", err)
		}
		if resolved["api_url"] != "https://override:8006" {
			t.Errorf("api_url = %v, want the collector's own value", resolved["api_url"])
		}
		if resolved["token_id"] != "shared" {
			t.Errorf("token_id = %v, want the credential's value", resolved["token_id"])
		}
	})

	t.Run("a config without a reference is returned untouched", func(t *testing.T) {
		repo := &PgDiscoveryRepository{}

		resolved, err := repo.resolveCredentialReference(
			context.Background(),
			sdk.ProviderConfig{"api_url": "https://pve.local:8006"},
			"proxmox",
		)
		if err != nil {
			t.Fatalf("unexpected error: %v", err)
		}
		if resolved["api_url"] != "https://pve.local:8006" {
			t.Error("expected the config to survive unchanged")
		}
	})

	t.Run("an unresolvable reference fails loudly", func(t *testing.T) {
		// Continuing would attempt the connection with no credentials and report an
		// authentication failure, sending the operator to look in the wrong place.
		cases := map[string]*PgDiscoveryRepository{
			"no resolver configured": {},
			"lookup failed":          {credentials: &stubCredentialResolver{err: errors.New("not found")}},
			"credential is not a settings object": {
				credentials: &stubCredentialResolver{secret: "not-json"},
			},
			// A literal "null" unmarshals into a nil map without error, so it would otherwise
			// merge nothing and let the run proceed with no credentials at all.
			"credential payload is null": {
				credentials: &stubCredentialResolver{secret: "null"},
			},
		}

		for name, repo := range cases {
			t.Run(name, func(t *testing.T) {
				_, err := repo.resolveCredentialReference(
					context.Background(),
					sdk.ProviderConfig{CredentialConfigKey: credentialID.String()},
					"proxmox",
				)
				if err == nil {
					t.Error("expected an error rather than a silent fallback")
				}
			})
		}
	})

	t.Run("a malformed reference is rejected", func(t *testing.T) {
		repo := &PgDiscoveryRepository{credentials: &stubCredentialResolver{secret: "{}"}}

		if _, err := repo.resolveCredentialReference(
			context.Background(),
			sdk.ProviderConfig{CredentialConfigKey: "not-a-uuid"},
			"proxmox",
		); err == nil {
			t.Error("expected a malformed credential id to be rejected")
		}
	})

	t.Run("a blank reference is treated as absent", func(t *testing.T) {
		// The UI sends an empty string when the operator picks no credential.
		repo := &PgDiscoveryRepository{}

		resolved, err := repo.resolveCredentialReference(
			context.Background(),
			sdk.ProviderConfig{CredentialConfigKey: "  ", "api_url": "https://pve.local:8006"},
			"proxmox",
		)
		if err != nil {
			t.Fatalf("unexpected error: %v", err)
		}
		if _, present := resolved[CredentialConfigKey]; present {
			t.Error("a blank reference must be dropped")
		}
	})
}
