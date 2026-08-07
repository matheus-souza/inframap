package bootstrap_test

import (
	"testing"

	"github.com/matheussouza/inframap/internal/bootstrap"
)

func TestNewConfigFromEnv_IndividualPGBars(t *testing.T) {
	t.Setenv("DATABASE_URL", "")
	t.Setenv("PGHOST", "db.internal")
	t.Setenv("PGUSER", "admin")
	t.Setenv("PGPASSWORD", "p@ss%word#123")
	t.Setenv("PGDATABASE", "custom_db")
	t.Setenv("PGPORT", "5433")
	t.Setenv("PGSSLMODE", "require")

	cfg := bootstrap.NewConfigFromEnv()

	expected := "postgres://admin:p%40ss%25word%23123@db.internal:5433/custom_db?sslmode=require"
	if cfg.DatabaseURL != expected {
		t.Errorf("expected DatabaseURL %q, got %q", expected, cfg.DatabaseURL)
	}
}
