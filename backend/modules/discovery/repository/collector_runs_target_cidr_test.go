package repository_test

import (
	"context"
	"fmt"
	"net/netip"
	"net/url"
	"os"
	"testing"
	"time"

	"github.com/google/uuid"
	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgtype"
	"github.com/jackc/pgx/v5/pgxpool"
	"github.com/matheussouza/inframap/internal/platform/db"
	"github.com/matheussouza/inframap/modules/discovery/repository"
)

// newMigratedPool creates a throwaway, fully migrated database next to DATABASE_URL.
func newMigratedPool(t *testing.T) *pgxpool.Pool {
	t.Helper()
	baseURL := os.Getenv("DATABASE_URL")
	if baseURL == "" {
		t.Skip("skipping repository integration test: DATABASE_URL not set")
	}
	ctx, cancel := context.WithTimeout(context.Background(), 30*time.Second)
	defer cancel()

	admin, err := pgx.Connect(ctx, baseURL)
	if err != nil {
		t.Skipf("skipping repository integration test: PostgreSQL not reachable: %v", err)
	}
	name := fmt.Sprintf("inframap_repotest_%d", time.Now().UnixNano())
	if _, err := admin.Exec(ctx, "CREATE DATABASE "+name); err != nil {
		_ = admin.Close(ctx)
		t.Fatalf("failed to create scratch database: %v", err)
	}

	parsed, err := url.Parse(baseURL)
	if err != nil {
		t.Fatalf("failed to parse DATABASE_URL: %v", err)
	}
	parsed.Path = "/" + name
	scratchURL := parsed.String()

	pool, err := pgxpool.New(ctx, scratchURL)
	if err != nil {
		t.Fatalf("failed to open scratch database: %v", err)
	}
	t.Cleanup(func() {
		pool.Close()
		dropCtx, dropCancel := context.WithTimeout(context.Background(), 10*time.Second)
		defer dropCancel()
		_, _ = admin.Exec(dropCtx, "DROP DATABASE IF EXISTS "+name+" WITH (FORCE)")
		_ = admin.Close(dropCtx)
	})

	if err := db.RunMigrations(ctx, scratchURL); err != nil {
		t.Fatalf("failed to migrate scratch database: %v", err)
	}
	return pool
}

func TestPgDiscoveryRepository_CollectorRunsTargetCIDR(t *testing.T) {
	pool := newMigratedPool(t)
	ctx := context.Background()
	repo := repository.NewPgDiscoveryRepository(pool, nil)

	sourceID := uuid.New()
	if _, err := pool.Exec(ctx,
		`INSERT INTO discovery_sources (id, name, type) VALUES ($1, 'Target CIDR source', 'icmp_sweep')`, sourceID); err != nil {
		t.Fatalf("failed to insert source: %v", err)
	}

	startedAt := time.Now().UTC().Truncate(time.Millisecond)
	target := netip.MustParsePrefix("192.0.2.0/24")
	withTargetID := uuid.New()
	withoutTargetID := uuid.New()

	runs := []*db.CreateCollectorRunParams{
		{
			ID:            withTargetID,
			SourceID:      sourceID,
			CollectorType: "icmp_sweep",
			Status:        "success",
			DevicesFound:  3,
			StartedAt:     pgtype.Timestamptz{Time: startedAt, Valid: true},
			FinishedAt:    pgtype.Timestamptz{Time: startedAt.Add(time.Second), Valid: true},
			TargetCidr:    &target,
		},
		{
			ID:            withoutTargetID,
			SourceID:      sourceID,
			CollectorType: "proxmox",
			Status:        "success",
			StartedAt:     pgtype.Timestamptz{Time: startedAt.Add(-time.Second), Valid: true},
			FinishedAt:    pgtype.Timestamptz{Time: startedAt.Add(time.Second), Valid: true},
		},
	}
	for _, run := range runs {
		if err := repo.CreateCollectorRun(ctx, run); err != nil {
			t.Fatalf("CreateCollectorRun failed: %v", err)
		}
	}

	t.Run("unset target_cidr is stored as NULL", func(t *testing.T) {
		var isNull bool
		if err := pool.QueryRow(ctx,
			`SELECT target_cidr IS NULL FROM discovery_collector_runs WHERE id = $1`, withoutTargetID).Scan(&isNull); err != nil {
			t.Fatalf("failed to read run: %v", err)
		}
		if !isNull {
			t.Error("expected target_cidr to be NULL when unset")
		}
	})

	t.Run("paged runs listing exposes target_cidr", func(t *testing.T) {
		items, total, err := repo.ListRunsBySourceIDPaged(ctx, sourceID, 10, 0)
		if err != nil {
			t.Fatalf("ListRunsBySourceIDPaged failed: %v", err)
		}
		if total != 2 || len(items) != 2 {
			t.Fatalf("expected 2 runs, got total=%d len=%d", total, len(items))
		}
		byID := map[uuid.UUID]*string{}
		for _, item := range items {
			byID[item.ID] = item.TargetCIDR
		}
		if got := byID[withTargetID]; got == nil || *got != "192.0.2.0/24" {
			t.Errorf("expected target_cidr 192.0.2.0/24, got %v", got)
		}
		if got := byID[withoutTargetID]; got != nil {
			t.Errorf("expected nil target_cidr, got %q", *got)
		}
	})

	t.Run("unpaged runs listing exposes target_cidr", func(t *testing.T) {
		items, err := repo.ListRunsBySourceID(ctx, sourceID, 10)
		if err != nil {
			t.Fatalf("ListRunsBySourceID failed: %v", err)
		}
		if len(items) != 2 || items[0].ID != withTargetID {
			t.Fatalf("expected the targeted run first, got %d items", len(items))
		}
		if items[0].TargetCIDR == nil || *items[0].TargetCIDR != "192.0.2.0/24" {
			t.Errorf("expected target_cidr 192.0.2.0/24, got %v", items[0].TargetCIDR)
		}
		if items[1].TargetCIDR != nil {
			t.Errorf("expected nil target_cidr, got %q", *items[1].TargetCIDR)
		}
	})

	t.Run("last run summary carries target_cidr per collector", func(t *testing.T) {
		src, err := repo.GetSourceByID(ctx, sourceID)
		if err != nil {
			t.Fatalf("GetSourceByID failed: %v", err)
		}
		if src.LastRun == nil || len(src.LastRun.Collectors) != 2 {
			t.Fatalf("expected a last run summary with 2 collectors, got %+v", src.LastRun)
		}
		for _, c := range src.LastRun.Collectors {
			switch c.CollectorType {
			case "icmp_sweep":
				if c.TargetCIDR == nil || *c.TargetCIDR != "192.0.2.0/24" {
					t.Errorf("expected icmp_sweep target_cidr 192.0.2.0/24, got %v", c.TargetCIDR)
				}
			case "proxmox":
				if c.TargetCIDR != nil {
					t.Errorf("expected nil proxmox target_cidr, got %q", *c.TargetCIDR)
				}
			}
		}
	})
}
