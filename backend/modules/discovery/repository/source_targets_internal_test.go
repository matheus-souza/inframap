package repository

import (
	"testing"

	"github.com/google/uuid"
	"github.com/jackc/pgx/v5/pgtype"
	"github.com/matheussouza/inframap/internal/platform/crypto"
	"github.com/matheussouza/inframap/internal/platform/db"
)

// sourceRowWithConfig builds a discovery source row whose encrypted config holds payload.
func sourceRowWithConfig(t *testing.T, enc crypto.Encryptor, payload string) *db.DiscoverySource {
	t.Helper()
	row := &db.DiscoverySource{ID: uuid.New(), Name: "Lab sweep", Type: "icmp_sweep", LastStatus: "idle"}
	if payload == "" {
		return row
	}
	encrypted, err := enc.Encrypt([]byte(payload))
	if err != nil {
		t.Fatalf("failed to encrypt config: %v", err)
	}
	row.ConfigEncrypted = pgtype.Text{String: encrypted, Valid: true}
	return row
}

func TestMapSourceToDTO_Targets(t *testing.T) {
	enc, err := crypto.NewAESGCMEncryptor("12345678901234567890123456789012")
	if err != nil {
		t.Fatalf("failed to create encryptor: %v", err)
	}
	repo := &PgDiscoveryRepository{encryptor: enc}
	subnetID := uuid.New()

	t.Run("legacy cidr payload reads as one target", func(t *testing.T) {
		resp, err := repo.mapSourceToDTO(sourceRowWithConfig(t, enc, `{"cidr":"10.0.10.0/24"}`))
		if err != nil {
			t.Fatalf("mapSourceToDTO failed: %v", err)
		}
		if len(resp.Targets) != 1 || resp.Targets[0].CIDR != "10.0.10.0/24" || resp.Targets[0].SubnetID != nil {
			t.Fatalf("expected one free-form target, got %+v", resp.Targets)
		}
		if resp.ConfigCIDR != "10.0.10.0/24" {
			t.Errorf("expected config_cidr 10.0.10.0/24, got %q", resp.ConfigCIDR)
		}
	})

	t.Run("targets payload reads as is", func(t *testing.T) {
		payload := `{"targets":[{"cidr":"10.0.10.0/24","subnet_id":"` + subnetID.String() + `"},{"cidr":"192.0.2.0/24"}]}`
		resp, err := repo.mapSourceToDTO(sourceRowWithConfig(t, enc, payload))
		if err != nil {
			t.Fatalf("mapSourceToDTO failed: %v", err)
		}
		if len(resp.Targets) != 2 {
			t.Fatalf("expected 2 targets, got %+v", resp.Targets)
		}
		if resp.Targets[0].SubnetID == nil || *resp.Targets[0].SubnetID != subnetID {
			t.Errorf("expected the first target linked to %s, got %+v", subnetID, resp.Targets[0])
		}
		if resp.Targets[1].CIDR != "192.0.2.0/24" || resp.Targets[1].SubnetID != nil {
			t.Errorf("unexpected second target: %+v", resp.Targets[1])
		}
		if resp.ConfigCIDR != "10.0.10.0/24" {
			t.Errorf("expected config_cidr to mirror the first target, got %q", resp.ConfigCIDR)
		}
	})

	t.Run("an explicit empty list ignores a stale cidr", func(t *testing.T) {
		resp, err := repo.mapSourceToDTO(sourceRowWithConfig(t, enc, `{"targets":[],"cidr":"10.0.10.0/24"}`))
		if err != nil {
			t.Fatalf("mapSourceToDTO failed: %v", err)
		}
		if len(resp.Targets) != 0 || resp.ConfigCIDR != "" {
			t.Errorf("expected no targets, got %+v / %q", resp.Targets, resp.ConfigCIDR)
		}
	})

	t.Run("a source without config has an empty target list", func(t *testing.T) {
		resp, err := repo.mapSourceToDTO(sourceRowWithConfig(t, enc, ""))
		if err != nil {
			t.Fatalf("mapSourceToDTO failed: %v", err)
		}
		if resp.Targets == nil || len(resp.Targets) != 0 || resp.ConfigCIDR != "" {
			t.Errorf("expected an empty, non-nil target list, got %#v / %q", resp.Targets, resp.ConfigCIDR)
		}
	})
}
