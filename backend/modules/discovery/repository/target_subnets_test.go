package repository_test

import (
	"context"
	"net/netip"
	"testing"

	"github.com/google/uuid"
	"github.com/matheussouza/inframap/internal/platform/crypto"
	"github.com/matheussouza/inframap/internal/platform/db"
	"github.com/matheussouza/inframap/modules/discovery/dto"
	"github.com/matheussouza/inframap/modules/discovery/repository"
	invrepo "github.com/matheussouza/inframap/modules/inventory/repository"
)

func createSourceWithTargets(t *testing.T, repo *repository.PgDiscoveryRepository, name string, targets []dto.SourceTarget) *dto.DiscoverySourceResponse {
	t.Helper()
	src, err := repo.CreateSource(context.Background(), &dto.CreateDiscoverySourceRequest{
		Name:   name,
		Type:   "icmp_sweep",
		Config: dto.WithSourceTargets(nil, targets),
	})
	if err != nil {
		t.Fatalf("failed to create source %q: %v", name, err)
	}
	return src
}

func findSource(t *testing.T, sources []*dto.DiscoverySourceResponse, id uuid.UUID) *dto.DiscoverySourceResponse {
	t.Helper()
	for _, src := range sources {
		if src.ID == id {
			return src
		}
	}
	t.Fatalf("source %s not listed", id)
	return nil
}

// TestPgDiscoveryRepository_TargetSubnets covers RFC-026 §6 and §7 against PostgreSQL: linked
// targets are named after their Subnet, the sources linked to a Subnet are found through the
// encrypted configs, and deleting the Subnet leaves the sources unchanged.
func TestPgDiscoveryRepository_TargetSubnets(t *testing.T) {
	pool := newMigratedPool(t)
	ctx := context.Background()
	enc, err := crypto.NewAESGCMEncryptor("12345678901234567890123456789012")
	if err != nil {
		t.Fatalf("failed to create encryptor: %v", err)
	}
	inventory := invrepo.NewPgInventoryRepository(pool)
	repo := repository.NewPgDiscoveryRepository(pool, enc).WithSubnetLookup(inventory)

	newSubnet := func(name, cidr string) uuid.UUID {
		subnet, createErr := inventory.CreateSubnet(ctx, db.CreateSubnetParams{
			ID:   uuid.New(),
			Name: name,
			Cidr: netip.MustParsePrefix(cidr),
		})
		if createErr != nil {
			t.Fatalf("failed to create subnet %s: %v", cidr, createErr)
		}
		return subnet.ID
	}
	labID := newSubnet("Lab", "10.0.10.0/24")
	officeID := newSubnet("Office", "10.0.20.0/24")

	linked := createSourceWithTargets(t, repo, "Lab and free sweep", []dto.SourceTarget{
		{CIDR: "10.0.10.0/24", SubnetID: &labID},
		{CIDR: "192.0.2.0/24"},
	})
	office := createSourceWithTargets(t, repo, "Office sweep", []dto.SourceTarget{{CIDR: "10.0.20.0/24", SubnetID: &officeID}})
	createSourceWithTargets(t, repo, "Free sweep", []dto.SourceTarget{{CIDR: "198.51.100.0/24"}})
	deletedSource := createSourceWithTargets(t, repo, "Deleted lab sweep", []dto.SourceTarget{{CIDR: "10.0.10.0/24", SubnetID: &labID}})
	if _, err := repo.SoftDeleteSource(ctx, deletedSource.ID); err != nil {
		t.Fatalf("failed to soft-delete source: %v", err)
	}

	t.Run("create response names the linked subnet", func(t *testing.T) {
		target := linked.Targets[0]
		if target.SubnetName == nil || *target.SubnetName != "Lab" || target.SubnetDeleted {
			t.Fatalf("expected the linked target named Lab, got %+v", target)
		}
		if freeTarget := linked.Targets[1]; freeTarget.SubnetName != nil || freeTarget.SubnetDeleted {
			t.Errorf("expected a free-form target without subnet details, got %+v", freeTarget)
		}
	})

	t.Run("list names the subnet of every source", func(t *testing.T) {
		sources, err := repo.ListSources(ctx)
		if err != nil {
			t.Fatalf("ListSources failed: %v", err)
		}
		if target := findSource(t, sources, office.ID).Targets[0]; target.SubnetName == nil || *target.SubnetName != "Office" {
			t.Errorf("expected the office target named Office, got %+v", target)
		}
		if target := findSource(t, sources, linked.ID).Targets[0]; target.SubnetName == nil || *target.SubnetName != "Lab" {
			t.Errorf("expected the lab target named Lab, got %+v", target)
		}
	})

	t.Run("only active sources linked to the subnet are found", func(t *testing.T) {
		// A source whose config cannot be decrypted is skipped instead of failing the lookup.
		if _, err := pool.Exec(ctx,
			`INSERT INTO discovery_sources (id, name, type, config_encrypted) VALUES ($1, 'Unreadable sweep', 'icmp_sweep', 'not-ciphertext')`,
			uuid.New()); err != nil {
			t.Fatalf("failed to insert unreadable source: %v", err)
		}

		refs, err := repo.ListSourcesLinkedToSubnet(ctx, labID)
		if err != nil {
			t.Fatalf("ListSourcesLinkedToSubnet failed: %v", err)
		}
		if len(refs) != 1 || refs[0].ID != linked.ID || refs[0].Name != "Lab and free sweep" {
			t.Fatalf("expected only %s, got %+v", linked.ID, refs)
		}

		none, err := repo.ListSourcesLinkedToSubnet(ctx, uuid.New())
		if err != nil {
			t.Fatalf("ListSourcesLinkedToSubnet failed: %v", err)
		}
		if none == nil || len(none) != 0 {
			t.Errorf("expected an empty list for an unlinked subnet, got %#v", none)
		}
	})

	t.Run("deleting the subnet keeps the target and flags it", func(t *testing.T) {
		if err := inventory.SoftDeleteSubnet(ctx, labID); err != nil {
			t.Fatalf("failed to soft-delete subnet: %v", err)
		}

		src, err := repo.GetSourceByID(ctx, linked.ID)
		if err != nil {
			t.Fatalf("GetSourceByID failed: %v", err)
		}
		if len(src.Targets) != 2 {
			t.Fatalf("expected the source to keep both targets, got %+v", src.Targets)
		}
		target := src.Targets[0]
		if target.SubnetID == nil || *target.SubnetID != labID || target.CIDR != "10.0.10.0/24" {
			t.Errorf("expected the target to stay linked to the deleted subnet, got %+v", target)
		}
		if !target.SubnetDeleted || target.SubnetName == nil || *target.SubnetName != "Lab" {
			t.Errorf("expected the target flagged deleted and still named Lab, got %+v", target)
		}

		refs, err := repo.ListSourcesLinkedToSubnet(ctx, labID)
		if err != nil {
			t.Fatalf("ListSourcesLinkedToSubnet failed: %v", err)
		}
		if len(refs) != 1 || refs[0].ID != linked.ID {
			t.Errorf("expected the source to stay linked after the subnet was deleted, got %+v", refs)
		}
	})
}
