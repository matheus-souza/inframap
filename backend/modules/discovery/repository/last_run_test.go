package repository_test

import (
	"context"
	"fmt"
	"net/netip"
	"testing"
	"time"

	"github.com/google/uuid"
	"github.com/jackc/pgx/v5/pgtype"
	"github.com/jackc/pgx/v5/pgxpool"
	"github.com/matheussouza/inframap/internal/platform/db"
	"github.com/matheussouza/inframap/modules/discovery/dto"
	"github.com/matheussouza/inframap/modules/discovery/repository"
)

// insertRunRows records one run of a source: every row shares startedAt, as TriggerRun writes them.
func insertRunRows(t *testing.T, repo *repository.PgDiscoveryRepository, sourceID uuid.UUID, startedAt time.Time, rows []db.CreateCollectorRunParams) {
	t.Helper()
	for i := range rows {
		row := rows[i]
		row.SourceID = sourceID
		row.StartedAt = pgtype.Timestamptz{Time: startedAt, Valid: true}
		row.FinishedAt = pgtype.Timestamptz{Time: startedAt.Add(time.Duration(i+1) * time.Second), Valid: true}
		if err := repo.CreateCollectorRun(context.Background(), &row); err != nil {
			t.Fatalf("CreateCollectorRun failed: %v", err)
		}
	}
}

func insertSource(t *testing.T, pool *pgxpool.Pool, name string, createdAt time.Time) uuid.UUID {
	t.Helper()
	id := uuid.New()
	if _, err := pool.Exec(context.Background(),
		`INSERT INTO discovery_sources (id, name, type, created_at) VALUES ($1, $2, 'icmp_sweep', $3)`, id, name, createdAt); err != nil {
		t.Fatalf("failed to insert source: %v", err)
	}
	return id
}

// multiTargetRun lays out a run the way the engine records it (RFC-026 §4): one row per target
// for each per_target collector, and one row without a target for the union and none scopes.
// The first target's ICMP sweep fails, so the run is partial.
func multiTargetRun(targets int) []db.CreateCollectorRunParams {
	var rows []db.CreateCollectorRunParams
	for i := 0; i < targets; i++ {
		prefix := netip.MustParsePrefix(fmt.Sprintf("10.20.%d.0/24", i))
		for _, collectorType := range []string{"icmp_sweep", "reverse_dns"} {
			row := db.CreateCollectorRunParams{CollectorType: collectorType, Status: "success", DevicesFound: 1, TargetCidr: &prefix}
			if i == 0 && collectorType == "icmp_sweep" {
				row.Status = "error"
				row.DevicesFound = 0
				row.ErrorMessage = pgtype.Text{String: "network unreachable", Valid: true}
			}
			rows = append(rows, row)
		}
	}
	return append(rows,
		db.CreateCollectorRunParams{CollectorType: "arp_sweep", Status: "success", DevicesFound: 2},
		db.CreateCollectorRunParams{CollectorType: "proxmox", Status: "success", DevicesFound: 3},
	)
}

func lastRunTargets(summary *dto.CollectorRunSummary) map[string]bool {
	keys := make(map[string]bool, len(summary.Collectors))
	for _, c := range summary.Collectors {
		target := "-"
		if c.TargetCIDR != nil {
			target = *c.TargetCIDR
		}
		keys[c.CollectorType+"@"+target] = true
	}
	return keys
}

func TestPgDiscoveryRepository_LastRun(t *testing.T) {
	pool := newMigratedPool(t)
	ctx := context.Background()
	repo := repository.NewPgDiscoveryRepository(pool, nil)

	now := time.Now().UTC().Truncate(time.Millisecond)
	multiID := insertSource(t, pool, "Multi-target sweep", now.Add(-3*time.Minute))
	failedID := insertSource(t, pool, "Failed sweep", now.Add(-2*time.Minute))
	neverRunID := insertSource(t, pool, "Never run", now.Add(-time.Minute))

	// An earlier run started two seconds before the latest one: it must not be merged into it.
	insertRunRows(t, repo, multiID, now.Add(-2*time.Second), []db.CreateCollectorRunParams{
		{CollectorType: "docker", Status: "error", ErrorMessage: pgtype.Text{String: "daemon unreachable", Valid: true}},
	})
	const targets = 5 // 5 targets x 2 per_target collectors + 2 others = 12 rows, above any page of 10.
	insertRunRows(t, repo, multiID, now, multiTargetRun(targets))
	insertRunRows(t, repo, failedID, now.Add(-time.Hour), []db.CreateCollectorRunParams{
		{CollectorType: "icmp_sweep", Status: "skipped", ErrorMessage: pgtype.Text{String: "target subnet deleted", Valid: true}},
	})

	assertMultiTargetSummary := func(t *testing.T, summary *dto.CollectorRunSummary) {
		t.Helper()
		if summary == nil {
			t.Fatal("expected a last run summary")
		}
		if len(summary.Collectors) != 2*targets+2 {
			t.Fatalf("expected the whole run of %d rows, got %d: %v", 2*targets+2, len(summary.Collectors), lastRunTargets(summary))
		}
		keys := lastRunTargets(summary)
		for i := 0; i < targets; i++ {
			for _, collectorType := range []string{"icmp_sweep", "reverse_dns"} {
				if key := fmt.Sprintf("%s@10.20.%d.0/24", collectorType, i); !keys[key] {
					t.Errorf("expected row %s in the summary, got %v", key, keys)
				}
			}
		}
		if keys["docker@-"] {
			t.Error("expected the earlier run to be left out of the summary")
		}
		if summary.Status != "partial" {
			t.Errorf("expected status partial, got %q", summary.Status)
		}
		if want := (2*targets - 1) + 2 + 3; summary.DevicesFound != want {
			t.Errorf("expected %d devices, got %d", want, summary.DevicesFound)
		}
		if !summary.StartedAt.Equal(now) {
			t.Errorf("expected the run to start at %s, got %s", now, summary.StartedAt)
		}
		if want := now.Add(time.Duration(2*targets+2) * time.Second); !summary.FinishedAt.Equal(want) {
			t.Errorf("expected the run to finish at %s, got %s", want, summary.FinishedAt)
		}
	}

	t.Run("list fills the latest run of every source", func(t *testing.T) {
		sources, err := repo.ListSources(ctx)
		if err != nil {
			t.Fatalf("ListSources failed: %v", err)
		}
		byID := make(map[uuid.UUID]*dto.DiscoverySourceResponse, len(sources))
		for _, src := range sources {
			byID[src.ID] = src
		}
		if len(byID) != 3 {
			t.Fatalf("expected 3 sources, got %d", len(byID))
		}

		assertMultiTargetSummary(t, byID[multiID].LastRun)

		failed := byID[failedID].LastRun
		if failed == nil || len(failed.Collectors) != 1 || failed.Status != "error" {
			t.Fatalf("expected a one-row error summary, got %+v", failed)
		}
		if failed.Collectors[0].ErrorMessage != "target subnet deleted" {
			t.Errorf("expected the skipped row's message, got %q", failed.Collectors[0].ErrorMessage)
		}

		if byID[neverRunID].LastRun != nil {
			t.Errorf("expected no summary for a source that never ran, got %+v", byID[neverRunID].LastRun)
		}
	})

	t.Run("get by id summarizes the whole latest run", func(t *testing.T) {
		src, err := repo.GetSourceByID(ctx, multiID)
		if err != nil {
			t.Fatalf("GetSourceByID failed: %v", err)
		}
		assertMultiTargetSummary(t, src.LastRun)
	})

	t.Run("status update returns the whole latest run", func(t *testing.T) {
		src, err := repo.UpdateSourceStatus(ctx, multiID, "partial")
		if err != nil {
			t.Fatalf("UpdateSourceStatus failed: %v", err)
		}
		assertMultiTargetSummary(t, src.LastRun)
	})
}
