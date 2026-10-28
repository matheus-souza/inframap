package usecase_test

import (
	"context"
	"errors"
	"fmt"
	"log/slog"
	"net/netip"
	"testing"
	"time"

	"github.com/google/uuid"
	"github.com/jackc/pgx/v5/pgtype"
	"github.com/matheussouza/inframap/internal/platform/db"
	"github.com/matheussouza/inframap/internal/platform/eventbus"
	"github.com/matheussouza/inframap/modules/discovery/dto"
	"github.com/matheussouza/inframap/modules/discovery/repository"
	"github.com/matheussouza/inframap/modules/discovery/usecase"
)

func newTargetsTestUseCase(t *testing.T) (*usecase.DefaultDiscoveryUseCase, *mockDiscRepo, *mockInvRepo) {
	t.Helper()
	discRepo := newMockDiscRepo()
	invRepo := newMockInvRepo()
	invRepo.subnets = make(map[uuid.UUID]*db.Subnet)
	bus := eventbus.NewInMemoryEventBus(1, 10)
	t.Cleanup(func() { _ = bus.Close() })
	return usecase.NewDefaultDiscoveryUseCase(discRepo, invRepo, bus, slog.Default()), discRepo, invRepo
}

func addSubnet(inv *mockInvRepo, cidr string, deleted bool) uuid.UUID {
	id := uuid.New()
	subnet := &db.Subnet{ID: id, Name: "lab-" + cidr, Cidr: netip.MustParsePrefix(cidr)}
	if deleted {
		subnet.DeletedAt = pgtype.Timestamptz{Time: time.Now(), Valid: true}
	}
	inv.subnets[id] = subnet
	return id
}

func targetsConfig(targets ...map[string]interface{}) map[string]interface{} {
	list := make([]interface{}, len(targets))
	for i, target := range targets {
		list[i] = target
	}
	return map[string]interface{}{"targets": list}
}

func freeTarget(cidr string) map[string]interface{} {
	return map[string]interface{}{"cidr": cidr}
}

func requireTargetCode(t *testing.T, err error, want string) {
	t.Helper()
	var targetErr *dto.TargetValidationError
	if !errors.As(err, &targetErr) {
		t.Fatalf("expected a target validation error %s, got %v", want, err)
	}
	if targetErr.Code != want {
		t.Fatalf("code = %s, want %s", targetErr.Code, want)
	}
	// The controller maps ErrInvalidInput to 400 first, so a target error must not wrap it.
	if errors.Is(err, usecase.ErrInvalidInput) {
		t.Fatalf("target validation errors must not wrap ErrInvalidInput: %v", err)
	}
}

// storedTargets reads back the targets the use case handed to the repository and asserts
// that the legacy key was dropped on the write.
func storedTargets(t *testing.T, repo *mockDiscRepo, id uuid.UUID) []dto.SourceTarget {
	t.Helper()
	config, ok := repo.sourceConfigs[id]
	if !ok {
		t.Fatalf("no source config written for %s", id)
	}
	if _, hasCIDR := config["cidr"]; hasCIDR {
		t.Errorf("expected the write to drop the legacy cidr key, got %v", config)
	}
	if _, hasTargets := config["targets"]; !hasTargets {
		t.Fatalf("expected the write to store targets, got %v", config)
	}
	targets, _, err := dto.ParseSourceTargets(config)
	if err != nil {
		t.Fatalf("stored targets do not parse: %v", err)
	}
	return targets
}

func TestCreateSource_Targets(t *testing.T) {
	ctx := context.Background()

	t.Run("legacy config.cidr is accepted as one target", func(t *testing.T) {
		uc, repo, _ := newTargetsTestUseCase(t)
		src, err := uc.CreateSource(ctx, &dto.CreateDiscoverySourceRequest{
			Name:   "Legacy sweep",
			Type:   "icmp_sweep",
			Config: map[string]interface{}{"cidr": "10.0.10.0/24"},
		})
		if err != nil {
			t.Fatalf("CreateSource failed: %v", err)
		}
		targets := storedTargets(t, repo, src.ID)
		if len(targets) != 1 || targets[0].CIDR != "10.0.10.0/24" {
			t.Fatalf("expected one stored target, got %+v", targets)
		}
		if len(src.Targets) != 1 || src.ConfigCIDR != "10.0.10.0/24" {
			t.Errorf("expected the response to carry the target, got %+v / %q", src.Targets, src.ConfigCIDR)
		}
	})

	t.Run("config.targets is stored in order", func(t *testing.T) {
		uc, repo, _ := newTargetsTestUseCase(t)
		src, err := uc.CreateSource(ctx, &dto.CreateDiscoverySourceRequest{
			Name:       "Multi-target sweep",
			Collectors: []dto.CollectorConfig{{Type: "icmp_sweep"}, {Type: "reverse_dns"}},
			Config:     targetsConfig(freeTarget("10.0.20.0/24"), freeTarget("192.0.2.0/24")),
		})
		if err != nil {
			t.Fatalf("CreateSource failed: %v", err)
		}
		targets := storedTargets(t, repo, src.ID)
		if len(targets) != 2 || targets[0].CIDR != "10.0.20.0/24" || targets[1].CIDR != "192.0.2.0/24" {
			t.Fatalf("unexpected stored targets: %+v", targets)
		}
	})

	t.Run("provider-only plan without targets is valid", func(t *testing.T) {
		uc, repo, _ := newTargetsTestUseCase(t)
		src, err := uc.CreateSource(ctx, &dto.CreateDiscoverySourceRequest{
			Name:       "Providers only",
			Collectors: []dto.CollectorConfig{{Type: "proxmox"}, {Type: "docker"}},
		})
		if err != nil {
			t.Fatalf("expected a provider-only plan to need no target, got %v", err)
		}
		if targets := storedTargets(t, repo, src.ID); len(targets) != 0 {
			t.Errorf("expected no stored targets, got %+v", targets)
		}
	})

	t.Run("a disabled sweep does not require targets", func(t *testing.T) {
		uc, _, _ := newTargetsTestUseCase(t)
		disabled := false
		_, err := uc.CreateSource(ctx, &dto.CreateDiscoverySourceRequest{
			Name:       "Paused sweep",
			Collectors: []dto.CollectorConfig{{Type: "icmp_sweep", Enabled: &disabled}, {Type: "proxmox"}},
		})
		if err != nil {
			t.Fatalf("expected the plan to run only providers and need no target, got %v", err)
		}
	})

	t.Run("linked target takes its CIDR from the subnet", func(t *testing.T) {
		uc, repo, inv := newTargetsTestUseCase(t)
		subnetID := addSubnet(inv, "10.0.30.0/24", false)
		src, err := uc.CreateSource(ctx, &dto.CreateDiscoverySourceRequest{
			Name: "Linked sweep",
			Type: "icmp_sweep",
			Config: targetsConfig(
				map[string]interface{}{"cidr": "10.0.99.0/24", "subnet_id": subnetID.String()},
				freeTarget("192.0.2.0/24"),
			),
		})
		if err != nil {
			t.Fatalf("CreateSource failed: %v", err)
		}
		targets := storedTargets(t, repo, src.ID)
		if len(targets) != 2 || targets[0].CIDR != "10.0.30.0/24" || targets[0].SubnetID == nil || *targets[0].SubnetID != subnetID {
			t.Fatalf("expected the linked target snapshot refreshed from the subnet, got %+v", targets)
		}
	})

	tooMany := make([]map[string]interface{}, dto.MaxSourceTargets+1)
	for i := range tooMany {
		tooMany[i] = freeTarget(fmt.Sprintf("10.0.%d.0/24", i))
	}

	rejections := []struct {
		name   string
		config func(inv *mockInvRepo) map[string]interface{}
		want   string
	}{
		{name: "sweep without targets", config: func(*mockInvRepo) map[string]interface{} { return nil }, want: dto.ErrCodeTargetsRequired},
		{name: "sweep with an empty list", config: func(*mockInvRepo) map[string]interface{} { return targetsConfig() }, want: dto.ErrCodeTargetsRequired},
		{name: "invalid cidr", config: func(*mockInvRepo) map[string]interface{} {
			return targetsConfig(freeTarget("10.0.0.0/24"), freeTarget("10.0.0.300/24"))
		}, want: dto.ErrCodeTargetInvalid},
		{name: "legacy cidr broader than /16", config: func(*mockInvRepo) map[string]interface{} {
			return map[string]interface{}{"cidr": "10.0.0.0/8"}
		}, want: dto.ErrCodeTargetInvalid},
		{name: "duplicate targets", config: func(*mockInvRepo) map[string]interface{} {
			return targetsConfig(freeTarget("10.0.10.0/24"), freeTarget("10.0.10.0/24"))
		}, want: dto.ErrCodeTargetsOverlap},
		{name: "partial overlap", config: func(*mockInvRepo) map[string]interface{} {
			return targetsConfig(freeTarget("10.0.0.0/23"), freeTarget("10.0.1.0/24"))
		}, want: dto.ErrCodeTargetsOverlap},
		{name: "linked target overlapping a free-form one", config: func(inv *mockInvRepo) map[string]interface{} {
			id := addSubnet(inv, "10.0.40.0/24", false)
			return targetsConfig(freeTarget("10.0.40.0/25"), map[string]interface{}{"cidr": "10.0.41.0/24", "subnet_id": id.String()})
		}, want: dto.ErrCodeTargetsOverlap},
		{name: "address sum over a /16", config: func(*mockInvRepo) map[string]interface{} {
			return targetsConfig(freeTarget("10.0.0.0/16"), freeTarget("10.1.0.0/24"))
		}, want: dto.ErrCodeTargetsTooLarge},
		{name: "more than 32 targets", config: func(*mockInvRepo) map[string]interface{} { return targetsConfig(tooMany...) }, want: dto.ErrCodeTargetsTooMany},
		{name: "unknown subnet", config: func(*mockInvRepo) map[string]interface{} {
			return targetsConfig(map[string]interface{}{"cidr": "10.0.50.0/24", "subnet_id": uuid.New().String()})
		}, want: dto.ErrCodeTargetSubnetNotFound},
		{name: "soft-deleted subnet", config: func(inv *mockInvRepo) map[string]interface{} {
			id := addSubnet(inv, "10.0.60.0/24", true)
			return targetsConfig(map[string]interface{}{"cidr": "10.0.60.0/24", "subnet_id": id.String()})
		}, want: dto.ErrCodeTargetSubnetNotFound},
	}

	for _, tc := range rejections {
		t.Run("rejects "+tc.name, func(t *testing.T) {
			uc, repo, inv := newTargetsTestUseCase(t)
			_, err := uc.CreateSource(ctx, &dto.CreateDiscoverySourceRequest{
				Name:   "Rejected sweep",
				Type:   "icmp_sweep",
				Config: tc.config(inv),
			})
			requireTargetCode(t, err, tc.want)
			if len(repo.sources) != 0 {
				t.Errorf("expected nothing persisted on a validation error, got %d sources", len(repo.sources))
			}
		})
	}
}

func TestUpdateSource_Targets(t *testing.T) {
	ctx := context.Background()

	// seedLegacySource stores a source as the repository reads a pre-targets payload.
	seedLegacySource := func(repo *mockDiscRepo, cidr string, collectorType string) uuid.UUID {
		id := uuid.New()
		resp := &dto.DiscoverySourceResponse{ID: id, Name: "Legacy", Type: collectorType, Enabled: true, LastStatus: "idle"}
		if cidr != "" {
			resp.Targets = []dto.SourceTargetResponse{{CIDR: cidr}}
			resp.ConfigCIDR = cidr
		}
		repo.sources[id] = resp
		return id
	}

	t.Run("a request without targets keeps the stored ones and migrates them", func(t *testing.T) {
		uc, repo, _ := newTargetsTestUseCase(t)
		id := seedLegacySource(repo, "10.0.10.0/24", "icmp_sweep")

		updated, err := uc.UpdateSource(ctx, id.String(), &dto.UpdateDiscoverySourceRequest{
			Name:       "Renamed",
			Type:       "icmp_sweep",
			Collectors: []dto.CollectorConfig{{Type: "icmp_sweep"}},
		})
		if err != nil {
			t.Fatalf("UpdateSource failed: %v", err)
		}
		targets := storedTargets(t, repo, id)
		if len(targets) != 1 || targets[0].CIDR != "10.0.10.0/24" {
			t.Fatalf("expected the stored target to be kept, got %+v", targets)
		}
		if updated.ConfigCIDR != "10.0.10.0/24" {
			t.Errorf("expected config_cidr to stay filled, got %q", updated.ConfigCIDR)
		}
	})

	t.Run("config.targets replaces the stored targets", func(t *testing.T) {
		uc, repo, _ := newTargetsTestUseCase(t)
		id := seedLegacySource(repo, "10.0.10.0/24", "icmp_sweep")

		_, err := uc.UpdateSource(ctx, id.String(), &dto.UpdateDiscoverySourceRequest{
			Name:       "Two blocks",
			Type:       "icmp_sweep",
			Collectors: []dto.CollectorConfig{{Type: "icmp_sweep"}},
			Config:     targetsConfig(freeTarget("10.0.10.0/24"), freeTarget("10.0.20.0/24")),
		})
		if err != nil {
			t.Fatalf("UpdateSource failed: %v", err)
		}
		if targets := storedTargets(t, repo, id); len(targets) != 2 || targets[1].CIDR != "10.0.20.0/24" {
			t.Fatalf("expected two stored targets, got %+v", targets)
		}
	})

	t.Run("legacy config.cidr on update is converted to one target", func(t *testing.T) {
		uc, repo, _ := newTargetsTestUseCase(t)
		id := seedLegacySource(repo, "10.0.10.0/24", "icmp_sweep")

		_, err := uc.UpdateSource(ctx, id.String(), &dto.UpdateDiscoverySourceRequest{
			Name:   "Moved",
			Type:   "icmp_sweep",
			Config: map[string]interface{}{"cidr": "192.0.2.0/24"},
		})
		if err != nil {
			t.Fatalf("UpdateSource failed: %v", err)
		}
		if targets := storedTargets(t, repo, id); len(targets) != 1 || targets[0].CIDR != "192.0.2.0/24" {
			t.Fatalf("expected the legacy cidr as the only target, got %+v", targets)
		}
	})

	t.Run("adding a sweep to a provider-only source requires targets", func(t *testing.T) {
		uc, repo, _ := newTargetsTestUseCase(t)
		id := seedLegacySource(repo, "", "proxmox")

		_, err := uc.UpdateSource(ctx, id.String(), &dto.UpdateDiscoverySourceRequest{
			Name:       "Now sweeping",
			Collectors: []dto.CollectorConfig{{Type: "proxmox"}, {Type: "icmp_sweep"}},
		})
		requireTargetCode(t, err, dto.ErrCodeTargetsRequired)
	})

	t.Run("provider-only update without targets stays valid", func(t *testing.T) {
		uc, repo, _ := newTargetsTestUseCase(t)
		id := seedLegacySource(repo, "", "proxmox")

		_, err := uc.UpdateSource(ctx, id.String(), &dto.UpdateDiscoverySourceRequest{
			Name:       "Providers",
			Collectors: []dto.CollectorConfig{{Type: "proxmox"}},
		})
		if err != nil {
			t.Fatalf("expected a provider-only update to need no target, got %v", err)
		}
	})

	t.Run("overlapping targets are rejected on update", func(t *testing.T) {
		uc, repo, _ := newTargetsTestUseCase(t)
		id := seedLegacySource(repo, "10.0.10.0/24", "icmp_sweep")

		_, err := uc.UpdateSource(ctx, id.String(), &dto.UpdateDiscoverySourceRequest{
			Name:   "Overlap",
			Type:   "icmp_sweep",
			Config: targetsConfig(freeTarget("10.0.0.0/23"), freeTarget("10.0.1.0/24")),
		})
		requireTargetCode(t, err, dto.ErrCodeTargetsOverlap)
		if _, written := repo.sourceConfigs[id]; written {
			t.Error("expected nothing written on a validation error")
		}
	})

	t.Run("an update of an unknown source reports not found", func(t *testing.T) {
		uc, _, _ := newTargetsTestUseCase(t)
		_, err := uc.UpdateSource(ctx, uuid.New().String(), &dto.UpdateDiscoverySourceRequest{
			Name: "Missing",
			Type: "icmp_sweep",
		})
		if !errors.Is(err, repository.ErrSourceNotFound) {
			t.Fatalf("expected ErrSourceNotFound, got %v", err)
		}
	})
}
