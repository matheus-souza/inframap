package db

import (
	"strings"
	"testing"
)

func TestEmbeddedMigrations_ContainsEntityEditingAndSoftDelete(t *testing.T) {
	entries, err := migrations.ReadDir("migrations")
	if err != nil {
		t.Fatalf("failed to read embedded migrations dir: %v", err)
	}

	var found bool
	for _, entry := range entries {
		if entry.Name() == "20260914000001_entity_editing_and_soft_delete.sql" {
			found = true
			break
		}
	}

	if !found {
		t.Fatal("expected 20260914000001_entity_editing_and_soft_delete.sql to be present in embedded migrations")
	}

	content, err := migrations.ReadFile("migrations/20260914000001_entity_editing_and_soft_delete.sql")
	if err != nil {
		t.Fatalf("failed to read migration file: %v", err)
	}

	sqlStr := string(content)

	// Check Up and Down headers
	if !strings.Contains(sqlStr, "-- +goose Up") {
		t.Error("missing '-- +goose Up' directive")
	}
	if !strings.Contains(sqlStr, "-- +goose Down") {
		t.Error("missing '-- +goose Down' directive")
	}

	// Check required DDL statements
	expectedDDL := []string{
		"ALTER TABLE subnets",
		"ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ",
		"ALTER TABLE discovery_sources",
		"CREATE UNIQUE INDEX IF NOT EXISTS uq_subnets_cidr_active",
		"ON subnets (cidr) WHERE deleted_at IS NULL",
		"CREATE UNIQUE INDEX IF NOT EXISTS uq_discovery_sources_name_active",
		"ON discovery_sources (name) WHERE deleted_at IS NULL",
	}

	for _, ddl := range expectedDDL {
		if !strings.Contains(sqlStr, ddl) {
			t.Errorf("migration missing expected DDL snippet: %q", ddl)
		}
	}
}
