package db

import (
	"context"
	"database/sql"
	"fmt"
	"io/fs"
	"net/url"
	"os"
	"strings"
	"testing"
	"time"

	"github.com/pressly/goose/v3"
)

const (
	targetCIDRMigration        = "20261027000001_discovery_collector_runs_target_cidr.sql"
	targetCIDRMigrationVersion = int64(20261027000001)
	targetCIDRPreviousVersion  = int64(20260914000001)
)

func TestEmbeddedMigrations_ContainsCollectorRunsTargetCIDR(t *testing.T) {
	content, err := migrations.ReadFile("migrations/" + targetCIDRMigration)
	if err != nil {
		t.Fatalf("expected %s to be embedded: %v", targetCIDRMigration, err)
	}

	sqlStr := string(content)
	for _, snippet := range []string{
		"-- +goose Up",
		"ALTER TABLE discovery_collector_runs ADD COLUMN target_cidr CIDR NULL",
		"-- +goose Down",
		"DROP COLUMN IF EXISTS target_cidr",
	} {
		if !strings.Contains(sqlStr, snippet) {
			t.Errorf("migration missing expected snippet: %q", snippet)
		}
	}
}

// TestMigration_CollectorRunsTargetCIDR_UpDownUp applies the target_cidr migration, rolls it back
// to the previous version and applies it again (RFC-026 §4) against a throwaway database.
func TestMigration_CollectorRunsTargetCIDR_UpDownUp(t *testing.T) {
	conn := openScratchDatabase(t)
	ctx := context.Background()

	fsys, err := fs.Sub(migrations, "migrations")
	if err != nil {
		t.Fatalf("failed to open embedded migrations: %v", err)
	}
	provider, err := goose.NewProvider(goose.DialectPostgres, conn, fsys)
	if err != nil {
		t.Fatalf("failed to create goose provider: %v", err)
	}

	if _, err := provider.Up(ctx); err != nil {
		t.Fatalf("goose up failed: %v", err)
	}
	assertTargetCIDRColumn(t, conn, true)

	if _, err := provider.DownTo(ctx, targetCIDRPreviousVersion); err != nil {
		t.Fatalf("goose down-to %d failed: %v", targetCIDRPreviousVersion, err)
	}
	assertTargetCIDRColumn(t, conn, false)

	if _, err := provider.Up(ctx); err != nil {
		t.Fatalf("goose re-up failed: %v", err)
	}
	assertTargetCIDRColumn(t, conn, true)

	version, err := provider.GetDBVersion(ctx)
	if err != nil {
		t.Fatalf("failed to read db version: %v", err)
	}
	if version < targetCIDRMigrationVersion {
		t.Errorf("expected db version >= %d, got %d", targetCIDRMigrationVersion, version)
	}

	// The existing status CHECK already allows 'skipped', and target_cidr accepts NULL and a CIDR.
	sourceID := "00000000-0000-0000-0000-0000000000a1"
	if _, err := conn.ExecContext(ctx,
		`INSERT INTO discovery_sources (id, name, type) VALUES ($1, 'migration-test', 'icmp_sweep')`, sourceID); err != nil {
		t.Fatalf("failed to insert source: %v", err)
	}
	if _, err := conn.ExecContext(ctx,
		`INSERT INTO discovery_collector_runs (source_id, collector_type, status, started_at, target_cidr)
		 VALUES ($1, 'icmp_sweep', 'skipped', now(), '192.0.2.0/24'), ($1, 'proxmox', 'success', now(), NULL)`,
		sourceID); err != nil {
		t.Fatalf("failed to insert collector runs: %v", err)
	}
}

func assertTargetCIDRColumn(t *testing.T, conn *sql.DB, wantPresent bool) {
	t.Helper()
	var dataType, nullable string
	err := conn.QueryRow(
		`SELECT data_type, is_nullable FROM information_schema.columns
		 WHERE table_name = 'discovery_collector_runs' AND column_name = 'target_cidr'`,
	).Scan(&dataType, &nullable)
	switch {
	case !wantPresent && err == sql.ErrNoRows:
		return
	case !wantPresent && err == nil:
		t.Fatal("expected target_cidr column to be dropped")
	case err != nil:
		t.Fatalf("failed to inspect target_cidr column: %v", err)
	}
	if dataType != "cidr" || nullable != "YES" {
		t.Errorf("expected nullable cidr column, got type=%s nullable=%s", dataType, nullable)
	}
}

// openScratchDatabase creates a throwaway database next to DATABASE_URL so that rolling migrations
// back cannot disturb tests in other packages sharing the main database.
func openScratchDatabase(t *testing.T) *sql.DB {
	t.Helper()
	baseURL := os.Getenv("DATABASE_URL")
	if baseURL == "" {
		t.Skip("skipping migration test: DATABASE_URL not set")
	}

	admin, err := sql.Open("pgx", baseURL)
	if err != nil {
		t.Skipf("skipping migration test: %v", err)
	}
	t.Cleanup(func() { _ = admin.Close() })
	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()
	if err := admin.PingContext(ctx); err != nil {
		t.Skipf("skipping migration test: PostgreSQL not reachable: %v", err)
	}

	name := fmt.Sprintf("inframap_migtest_%d", time.Now().UnixNano())
	if _, err := admin.Exec("CREATE DATABASE " + name); err != nil {
		t.Fatalf("failed to create scratch database: %v", err)
	}

	parsed, err := url.Parse(baseURL)
	if err != nil {
		t.Fatalf("failed to parse DATABASE_URL: %v", err)
	}
	parsed.Path = "/" + name
	conn, err := sql.Open("pgx", parsed.String())
	if err != nil {
		t.Fatalf("failed to open scratch database: %v", err)
	}
	t.Cleanup(func() {
		_ = conn.Close()
		_, _ = admin.Exec("DROP DATABASE IF EXISTS " + name + " WITH (FORCE)")
	})
	return conn
}
