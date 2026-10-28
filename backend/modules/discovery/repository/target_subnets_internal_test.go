package repository

import (
	"context"
	"errors"
	"testing"
	"time"

	"github.com/google/uuid"
	"github.com/jackc/pgx/v5/pgtype"
	"github.com/matheussouza/inframap/internal/platform/db"
	"github.com/matheussouza/inframap/modules/discovery/dto"
)

// countingSubnetLookup serves subnets from memory and records every call.
type countingSubnetLookup struct {
	subnets map[uuid.UUID]db.Subnet
	err     error
	calls   [][]uuid.UUID
}

func (l *countingSubnetLookup) ListSubnetsByIDs(_ context.Context, ids []uuid.UUID) ([]db.Subnet, error) {
	l.calls = append(l.calls, ids)
	if l.err != nil {
		return nil, l.err
	}
	out := make([]db.Subnet, 0, len(ids))
	for _, id := range ids {
		if subnet, ok := l.subnets[id]; ok {
			out = append(out, subnet)
		}
	}
	return out, nil
}

func linkedTarget(cidr string, id uuid.UUID) dto.SourceTargetResponse {
	return dto.SourceTargetResponse{CIDR: cidr, SubnetID: &id}
}

func TestAttachTargetSubnets(t *testing.T) {
	ctx := context.Background()
	activeID, deletedID, missingID := uuid.New(), uuid.New(), uuid.New()
	newLookup := func() *countingSubnetLookup {
		return &countingSubnetLookup{subnets: map[uuid.UUID]db.Subnet{
			activeID:  {ID: activeID, Name: "Lab"},
			deletedID: {ID: deletedID, Name: "Retired", DeletedAt: pgtype.Timestamptz{Time: time.Now(), Valid: true}},
		}}
	}
	newSources := func() []*dto.DiscoverySourceResponse {
		return []*dto.DiscoverySourceResponse{
			{ID: uuid.New(), Targets: []dto.SourceTargetResponse{
				linkedTarget("10.0.10.0/24", activeID),
				{CIDR: "192.0.2.0/24"},
			}},
			{ID: uuid.New(), Targets: []dto.SourceTargetResponse{
				linkedTarget("10.0.10.0/24", activeID),
				linkedTarget("10.0.20.0/24", deletedID),
				linkedTarget("10.0.30.0/24", missingID),
			}},
			{ID: uuid.New(), Targets: []dto.SourceTargetResponse{}},
		}
	}

	t.Run("one lookup fills every linked target", func(t *testing.T) {
		lookup := newLookup()
		repo := &PgDiscoveryRepository{subnets: lookup}
		sources := newSources()

		repo.attachTargetSubnets(ctx, sources...)

		if len(lookup.calls) != 1 {
			t.Fatalf("expected a single subnet lookup, got %d", len(lookup.calls))
		}
		if len(lookup.calls[0]) != 3 {
			t.Errorf("expected the 3 distinct subnet ids in one lookup, got %v", lookup.calls[0])
		}

		active := sources[0].Targets[0]
		if active.SubnetName == nil || *active.SubnetName != "Lab" || active.SubnetDeleted {
			t.Errorf("expected the linked target to carry its subnet name, got %+v", active)
		}
		free := sources[0].Targets[1]
		if free.SubnetName != nil || free.SubnetDeleted {
			t.Errorf("expected a free-form target to carry no subnet details, got %+v", free)
		}
		if again := sources[1].Targets[0]; again.SubnetName == nil || *again.SubnetName != "Lab" {
			t.Errorf("expected a subnet shared by two sources to be named on both, got %+v", again)
		}
		deleted := sources[1].Targets[1]
		if deleted.SubnetName == nil || *deleted.SubnetName != "Retired" || !deleted.SubnetDeleted {
			t.Errorf("expected a soft-deleted subnet to keep its name and be flagged, got %+v", deleted)
		}
		missing := sources[1].Targets[2]
		if missing.SubnetName != nil || !missing.SubnetDeleted {
			t.Errorf("expected a missing subnet to be flagged deleted with no name, got %+v", missing)
		}
	})

	t.Run("no lookup without linked targets", func(t *testing.T) {
		lookup := newLookup()
		repo := &PgDiscoveryRepository{subnets: lookup}
		repo.attachTargetSubnets(ctx, &dto.DiscoverySourceResponse{Targets: []dto.SourceTargetResponse{{CIDR: "192.0.2.0/24"}}}, nil)
		if len(lookup.calls) != 0 {
			t.Errorf("expected no lookup, got %d", len(lookup.calls))
		}
	})

	t.Run("a failed lookup leaves the details empty", func(t *testing.T) {
		lookup := newLookup()
		lookup.err = errors.New("database unavailable")
		repo := &PgDiscoveryRepository{subnets: lookup}
		sources := newSources()

		repo.attachTargetSubnets(ctx, sources...)

		for _, target := range sources[1].Targets {
			if target.SubnetName != nil || target.SubnetDeleted {
				t.Errorf("expected no subnet details after a failed lookup, got %+v", target)
			}
		}
	})

	t.Run("no lookup configured", func(t *testing.T) {
		repo := &PgDiscoveryRepository{}
		sources := newSources()
		repo.attachTargetSubnets(ctx, sources...)
		if target := sources[0].Targets[0]; target.SubnetName != nil || target.SubnetDeleted {
			t.Errorf("expected no subnet details without a lookup, got %+v", target)
		}
	})
}
