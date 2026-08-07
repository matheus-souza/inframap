package bootstrap_test

import (
	"os"
	"testing"

	"github.com/matheussouza/inframap/internal/bootstrap"
)

func TestNewConfigFromEnv_IndividualPGBars(t *testing.T) {
	os.Unsetenv("DATABASE_URL")
	os.Setenv("PGHOST", "db.internal")
	os.Setenv("PGUSER", "admin")
	os.Setenv("PGPASSWORD", "p@ss%word#123")
	os.Setenv("PGDATABASE", "custom_db")
	os.Setenv("PGPORT", "5433")
	os.Setenv("PGSSLMODE", "require")

	defer func() {
		os.Unsetenv("PGHOST")
		os.Unsetenv("PGUSER")
		os.Unsetenv("PGPASSWORD")
		os.Unsetenv("PGDATABASE")
		os.Unsetenv("PGPORT")
		os.Unsetenv("PGSSLMODE")
	}()

	cfg := bootstrap.NewConfigFromEnv()

	expected := "postgres://admin:p%40ss%25word%23123@db.internal:5433/custom_db?sslmode=require"
	if cfg.DatabaseURL != expected {
		t.Errorf("expected DatabaseURL %q, got %q", expected, cfg.DatabaseURL)
	}
}
