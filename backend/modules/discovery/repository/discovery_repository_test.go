package repository_test

import (
	"context"
	"encoding/json"
	"errors"
	"testing"
	"time"

	"github.com/google/uuid"
	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgconn"
	"github.com/matheussouza/inframap/internal/platform/crypto"
	"github.com/matheussouza/inframap/internal/platform/db"
	"github.com/matheussouza/inframap/modules/discovery/dto"
	"github.com/matheussouza/inframap/modules/discovery/repository"
)

// mockDB implements db.DBTX and repository.TxBeginner for repository testing.
type mockDB struct {
	sources    map[uuid.UUID]db.DiscoverySource
	collectors map[uuid.UUID][]db.DiscoverySourceCollector
	failExec   bool
	failTx     bool
}

func newMockDB() *mockDB {
	return &mockDB{
		sources:    make(map[uuid.UUID]db.DiscoverySource),
		collectors: make(map[uuid.UUID][]db.DiscoverySourceCollector),
	}
}

func (m *mockDB) Exec(_ context.Context, _ string, _ ...interface{}) (pgconn.CommandTag, error) {
	if m.failExec {
		return pgconn.CommandTag{}, errors.New("db exec failure")
	}
	return pgconn.NewCommandTag("DELETE 1"), nil
}

func (m *mockDB) Query(_ context.Context, _ string, _ ...interface{}) (pgx.Rows, error) {
	return nil, errors.New("query not directly supported on mockDB")
}

type mockRow struct {
	err error
}

func (r *mockRow) Scan(_ ...any) error {
	if r.err != nil {
		return r.err
	}
	return errors.New("query row failed in mock")
}

func (m *mockDB) QueryRow(_ context.Context, _ string, _ ...interface{}) pgx.Row {
	return &mockRow{err: errors.New("db query failure on mockDB")}
}

func (m *mockDB) Begin(_ context.Context) (pgx.Tx, error) {
	if m.failTx {
		return nil, errors.New("tx begin failure")
	}
	return &mockTx{db: m}, nil
}

type mockTx struct {
	db        *mockDB
	committed bool
	rolledBack bool
}

func (t *mockTx) Begin(_ context.Context) (pgx.Tx, error) {
	return t, nil
}

func (t *mockTx) Commit(_ context.Context) error {
	t.committed = true
	return nil
}

func (t *mockTx) Rollback(_ context.Context) error {
	t.rolledBack = true
	return nil
}

func (t *mockTx) CopyFrom(_ context.Context, _ pgx.Identifier, _ []string, _ pgx.CopyFromSource) (int64, error) {
	return 0, nil
}

func (t *mockTx) SendBatch(_ context.Context, _ *pgx.Batch) pgx.BatchResults {
	return nil
}

func (t *mockTx) LargeObjects() pgx.LargeObjects {
	return pgx.LargeObjects{}
}

func (t *mockTx) Prepare(_ context.Context, _ string, _ string) (*pgconn.StatementDescription, error) {
	return nil, nil
}

func (t *mockTx) Exec(ctx context.Context, sql string, args ...interface{}) (pgconn.CommandTag, error) {
	return t.db.Exec(ctx, sql, args...)
}

func (t *mockTx) Query(ctx context.Context, sql string, args ...interface{}) (pgx.Rows, error) {
	return t.db.Query(ctx, sql, args...)
}

func (t *mockTx) QueryRow(ctx context.Context, sql string, args ...interface{}) pgx.Row {
	return t.db.QueryRow(ctx, sql, args...)
}

func (t *mockTx) Conn() *pgx.Conn {
	return nil
}

func TestPgDiscoveryRepository_CreateSource_EncryptionCheck(t *testing.T) {
	enc, err := crypto.NewAESGCMEncryptor("12345678901234567890123456789012")
	if err != nil {
		t.Fatalf("failed to create encryptor: %v", err)
	}

	t.Run("CreateSource fails when config present but encryptor is nil", func(t *testing.T) {
		repo := repository.NewPgDiscoveryRepository(newMockDB(), nil)
		req := &dto.CreateDiscoverySourceRequest{
			Name: "Test No Encryptor",
			Type: "icmp_sweep",
			Config: map[string]interface{}{
				"cidr": "10.0.0.0/24",
			},
		}
		req.Normalize()
		_, err := repo.CreateSource(context.Background(), req)
		if err == nil {
			t.Error("expected error when encryptor is missing for encrypted config")
		}
	})

	t.Run("CreateSource fails when tx begin fails", func(t *testing.T) {
		mdb := newMockDB()
		mdb.failTx = true
		repo := repository.NewPgDiscoveryRepository(mdb, enc)
		req := &dto.CreateDiscoverySourceRequest{
			Name: "Test Tx Fail",
			Type: "icmp_sweep",
		}
		req.Normalize()
		_, err := repo.CreateSource(context.Background(), req)
		if err == nil {
			t.Error("expected error when transaction begin fails")
		}
	})
}

func TestDiscoveryRepository_DTO_EmptySlices(t *testing.T) {
	resp := &dto.DiscoverySourceResponse{
		ID:         uuid.New(),
		Name:       "Empty Collectors Plan",
		Type:       "icmp_sweep",
		Enabled:    true,
		Collectors: make([]dto.CollectorResponse, 0),
		LastStatus: "idle",
		CreatedAt:  time.Now(),
		UpdatedAt:  time.Now(),
	}

	if resp.Collectors == nil {
		t.Fatal("expected non-nil collectors slice")
	}

	data, err := json.Marshal(resp)
	if err != nil {
		t.Fatalf("failed to marshal DTO: %v", err)
	}

	var raw map[string]interface{}
	if err := json.Unmarshal(data, &raw); err != nil {
		t.Fatalf("failed to unmarshal JSON: %v", err)
	}

	cols, ok := raw["collectors"].([]interface{})
	if !ok {
		t.Fatalf("expected collectors to be json array, got %T", raw["collectors"])
	}
	if len(cols) != 0 {
		t.Errorf("expected 0 collectors, got %d", len(cols))
	}
}

func TestPgDiscoveryRepository_CollectorRuns(t *testing.T) {
	mdb := newMockDB()
	repo := repository.NewPgDiscoveryRepository(mdb, nil)

	t.Run("CreateCollectorRun returns error for nil params", func(t *testing.T) {
		err := repo.CreateCollectorRun(context.Background(), nil)
		if err == nil {
			t.Error("expected error when CreateCollectorRun receives nil params")
		}
	})

	t.Run("CreateCollectorRun fails when DB query fails", func(t *testing.T) {
		mdb.failExec = true
		err := repo.CreateCollectorRun(context.Background(), &db.CreateCollectorRunParams{
			SourceID:      uuid.New(),
			CollectorType: "icmp_sweep",
			Status:        "success",
		})
		if err == nil {
			t.Error("expected error when DB exec fails")
		}
		mdb.failExec = false
	})

	t.Run("ListRunsBySourceID fails when DB query fails", func(t *testing.T) {
		_, err := repo.ListRunsBySourceID(context.Background(), uuid.New(), 0)
		if err == nil {
			t.Error("expected error when DB query fails on mockDB")
		}
	})
}
