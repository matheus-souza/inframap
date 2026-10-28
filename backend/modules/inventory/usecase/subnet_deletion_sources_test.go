package usecase_test

import (
	"context"
	"encoding/json"
	"errors"
	"strings"
	"testing"

	"github.com/google/uuid"
	"github.com/matheussouza/inframap/modules/inventory/dto"
	"github.com/matheussouza/inframap/modules/inventory/usecase"
)

// fakeSubnetSourceFinder reports linkedSources for every subnet in bySubnet.
type fakeSubnetSourceFinder struct {
	bySubnet map[uuid.UUID][]dto.SubnetDiscoverySource
	err      error
	calls    []uuid.UUID
}

func (f *fakeSubnetSourceFinder) ListSourcesLinkedToSubnet(_ context.Context, subnetID uuid.UUID) ([]dto.SubnetDiscoverySource, error) {
	f.calls = append(f.calls, subnetID)
	if f.err != nil {
		return nil, f.err
	}
	return f.bySubnet[subnetID], nil
}

func createTestSubnet(t *testing.T, uc *usecase.DefaultInventoryUseCase, name, cidr string) uuid.UUID {
	t.Helper()
	created, err := uc.CreateSubnet(context.Background(), dto.CreateSubnetRequest{Name: name, CIDR: cidr})
	if err != nil {
		t.Fatalf("failed to create subnet: %v", err)
	}
	return uuid.MustParse(created.ID)
}

func TestGetSubnetDeletionImpact_DiscoverySources(t *testing.T) {
	ctx := context.Background()

	t.Run("lists the sources linked to the subnet", func(t *testing.T) {
		uc := usecase.NewDefaultInventoryUseCase(newMockInventoryRepository(), nil, nil)
		subnetID := createTestSubnet(t, uc, "Lab", "10.0.10.0/24")
		otherID := createTestSubnet(t, uc, "Office", "10.0.20.0/24")
		linked := []dto.SubnetDiscoverySource{{ID: uuid.NewString(), Name: "Lab sweep"}}
		finder := &fakeSubnetSourceFinder{bySubnet: map[uuid.UUID][]dto.SubnetDiscoverySource{
			subnetID: linked,
			otherID:  {{ID: uuid.NewString(), Name: "Office sweep"}},
		}}
		uc.WithSubnetSourceFinder(finder)

		impact, err := uc.GetSubnetDeletionImpact(ctx, subnetID.String())
		if err != nil {
			t.Fatalf("GetSubnetDeletionImpact failed: %v", err)
		}
		if len(finder.calls) != 1 || finder.calls[0] != subnetID {
			t.Fatalf("expected one lookup for %s, got %v", subnetID, finder.calls)
		}
		got := impact.Impact.DiscoverySources
		if len(got) != 1 || got[0] != linked[0] {
			t.Fatalf("expected only the source linked to the subnet, got %+v", got)
		}

		deleted, err := uc.SoftDeleteSubnet(ctx, subnetID.String())
		if err != nil {
			t.Fatalf("SoftDeleteSubnet failed: %v", err)
		}
		if len(deleted.Impact.DiscoverySources) != 1 {
			t.Errorf("expected the delete response to report the linked source, got %+v", deleted.Impact.DiscoverySources)
		}
	})

	t.Run("an empty array when no source is linked", func(t *testing.T) {
		uc := usecase.NewDefaultInventoryUseCase(newMockInventoryRepository(), nil, nil)
		subnetID := createTestSubnet(t, uc, "Lab", "10.0.10.0/24")
		uc.WithSubnetSourceFinder(&fakeSubnetSourceFinder{})

		impact, err := uc.GetSubnetDeletionImpact(ctx, subnetID.String())
		if err != nil {
			t.Fatalf("GetSubnetDeletionImpact failed: %v", err)
		}
		data, err := json.Marshal(impact)
		if err != nil {
			t.Fatalf("marshal failed: %v", err)
		}
		if !strings.Contains(string(data), `"discovery_sources":[]`) {
			t.Errorf("expected discovery_sources to be an empty array, got %s", data)
		}
	})

	t.Run("an empty array without a finder", func(t *testing.T) {
		uc := usecase.NewDefaultInventoryUseCase(newMockInventoryRepository(), nil, nil)
		subnetID := createTestSubnet(t, uc, "Lab", "10.0.10.0/24")

		impact, err := uc.GetSubnetDeletionImpact(ctx, subnetID.String())
		if err != nil {
			t.Fatalf("GetSubnetDeletionImpact failed: %v", err)
		}
		if impact.Impact.DiscoverySources == nil || len(impact.Impact.DiscoverySources) != 0 {
			t.Errorf("expected an empty, non-nil list, got %#v", impact.Impact.DiscoverySources)
		}
	})

	t.Run("a finder error fails the impact and keeps the subnet", func(t *testing.T) {
		uc := usecase.NewDefaultInventoryUseCase(newMockInventoryRepository(), nil, nil)
		subnetID := createTestSubnet(t, uc, "Lab", "10.0.10.0/24")
		finderErr := errors.New("sources unavailable")
		uc.WithSubnetSourceFinder(&fakeSubnetSourceFinder{err: finderErr})

		if _, err := uc.GetSubnetDeletionImpact(ctx, subnetID.String()); !errors.Is(err, finderErr) {
			t.Fatalf("expected the finder error, got %v", err)
		}
		if _, err := uc.SoftDeleteSubnet(ctx, subnetID.String()); !errors.Is(err, finderErr) {
			t.Fatalf("expected the delete to fail with the finder error, got %v", err)
		}
		if _, err := uc.GetSubnetByID(ctx, subnetID.String()); err != nil {
			t.Errorf("expected the subnet to survive a failed delete, got %v", err)
		}
	})
}
