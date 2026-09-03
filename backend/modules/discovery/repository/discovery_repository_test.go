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
	"github.com/jackc/pgx/v5/pgtype"
	"github.com/matheussouza/inframap/internal/platform/crypto"
	"github.com/matheussouza/inframap/internal/platform/db"
	"github.com/matheussouza/inframap/modules/discovery/dto"
	"github.com/matheussouza/inframap/modules/discovery/repository"
)

// mockDB implements db.DBTX and repository.TxBeginner for repository testing.
type mockDB struct {
	sources      map[uuid.UUID]db.DiscoverySource
	collectors   map[uuid.UUID][]db.DiscoverySourceCollector
	failExec     bool
	failTx       bool
	execFunc     func(ctx context.Context, sql string, args ...interface{}) (pgconn.CommandTag, error)
	queryRowFunc func(ctx context.Context, sql string, args ...interface{}) pgx.Row
}

func newMockDB() *mockDB {
	return &mockDB{
		sources:    make(map[uuid.UUID]db.DiscoverySource),
		collectors: make(map[uuid.UUID][]db.DiscoverySourceCollector),
	}
}

func (m *mockDB) Exec(ctx context.Context, sql string, args ...interface{}) (pgconn.CommandTag, error) {
	if m.execFunc != nil {
		return m.execFunc(ctx, sql, args...)
	}
	if m.failExec {
		return pgconn.CommandTag{}, errors.New("db exec failure")
	}
	return pgconn.NewCommandTag("DELETE 1"), nil
}

func (m *mockDB) Query(_ context.Context, _ string, _ ...interface{}) (pgx.Rows, error) {
	return nil, errors.New("query not directly supported on mockDB")
}

type mockRow struct {
	err      error
	scanFunc func(dest ...any) error
}

func (r *mockRow) Scan(dest ...any) error {
	if r.scanFunc != nil {
		return r.scanFunc(dest...)
	}
	if r.err != nil {
		return r.err
	}
	return errors.New("query row failed in mock")
}

func (m *mockDB) QueryRow(ctx context.Context, sql string, args ...interface{}) pgx.Row {
	if m.queryRowFunc != nil {
		return m.queryRowFunc(ctx, sql, args...)
	}
	return &mockRow{err: errors.New("db query failure on mockDB")}
}

func (m *mockDB) Begin(_ context.Context) (pgx.Tx, error) {
	if m.failTx {
		return nil, errors.New("tx begin failure")
	}
	return &mockTx{db: m}, nil
}

type mockTx struct {
	db         *mockDB
	committed  bool
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

func TestPgDiscoveryRepository_PurgeOldCollectorRuns(t *testing.T) {
	t.Run("Purges runs in chunks until 0 rows remain", func(t *testing.T) {
		mdb := newMockDB()
		callCount := 0
		mdb.execFunc = func(_ context.Context, _ string, _ ...interface{}) (pgconn.CommandTag, error) {
			callCount++
			if callCount == 1 {
				return pgconn.NewCommandTag("DELETE 500"), nil
			}
			if callCount == 2 {
				return pgconn.NewCommandTag("DELETE 250"), nil
			}
			return pgconn.NewCommandTag("DELETE 0"), nil
		}

		repo := repository.NewPgDiscoveryRepository(mdb, nil)
		deleted, err := repo.PurgeOldCollectorRuns(context.Background(), time.Now().Add(-7*24*time.Hour), 500)
		if err != nil {
			t.Fatalf("expected no error, got %v", err)
		}
		if deleted != 750 {
			t.Errorf("expected 750 deleted rows, got %d", deleted)
		}
		if callCount != 2 {
			t.Errorf("expected 2 exec calls, got %d", callCount)
		}
	})

	t.Run("Returns error when DB exec fails", func(t *testing.T) {
		mdb := newMockDB()
		mdb.failExec = true
		repo := repository.NewPgDiscoveryRepository(mdb, nil)
		_, err := repo.PurgeOldCollectorRuns(context.Background(), time.Now().Add(-7*24*time.Hour), 500)
		if err == nil {
			t.Error("expected error when DB exec fails")
		}
	})

	t.Run("Aborts immediately when context is canceled", func(t *testing.T) {
		mdb := newMockDB()
		repo := repository.NewPgDiscoveryRepository(mdb, nil)
		ctx, cancel := context.WithCancel(context.Background())
		cancel()

		_, err := repo.PurgeOldCollectorRuns(ctx, time.Now().Add(-7*24*time.Hour), 500)
		if !errors.Is(err, context.Canceled) {
			t.Errorf("expected context.Canceled error, got %v", err)
		}
	})

	t.Run("Normalizes non-positive batchSize to default 500", func(t *testing.T) {
		mdb := newMockDB()
		mdb.execFunc = func(_ context.Context, _ string, _ ...interface{}) (pgconn.CommandTag, error) {
			return pgconn.NewCommandTag("DELETE 0"), nil
		}
		repo := repository.NewPgDiscoveryRepository(mdb, nil)
		deleted, err := repo.PurgeOldCollectorRuns(context.Background(), time.Now().Add(-7*24*time.Hour), -10)
		if err != nil {
			t.Fatalf("expected no error, got %v", err)
		}
		if deleted != 0 {
			t.Errorf("expected 0 deleted, got %d", deleted)
		}
	})
}

func TestPgDiscoveryRepository_ListRunsBySourceIDPaged(t *testing.T) {
	t.Run("Fails when DB query fails", func(t *testing.T) {
		mdb := newMockDB()
		repo := repository.NewPgDiscoveryRepository(mdb, nil)
		_, _, err := repo.ListRunsBySourceIDPaged(context.Background(), uuid.New(), 20, 0)
		if err == nil {
			t.Error("expected error when DB query fails on mockDB")
		}
	})
}

func TestPgDiscoveryRepository_ResolveCollectorConfig(t *testing.T) {
	enc, err := crypto.NewAESGCMEncryptor("12345678901234567890123456789012")
	if err != nil {
		t.Fatalf("failed to create encryptor: %v", err)
	}

	sourceID := uuid.New()

	t.Run("Returns empty config and nil error when collector not found", func(t *testing.T) {
		mdb := newMockDB()
		mdb.queryRowFunc = func(_ context.Context, _ string, _ ...interface{}) pgx.Row {
			return &mockRow{err: pgx.ErrNoRows}
		}
		repo := repository.NewPgDiscoveryRepository(mdb, enc)

		cfg, err := repo.ResolveCollectorConfig(context.Background(), sourceID, "proxmox")
		if err != nil {
			t.Fatalf("expected nil error, got %v", err)
		}
		if len(cfg) != 0 {
			t.Errorf("expected empty config, got %v", cfg)
		}
	})

	t.Run("Returns empty config when ConfigEncrypted is invalid/empty", func(t *testing.T) {
		mdb := newMockDB()
		mdb.queryRowFunc = func(_ context.Context, _ string, _ ...interface{}) pgx.Row {
			return &mockRow{
				scanFunc: func(dest ...any) error {
					*(dest[0].(*uuid.UUID)) = uuid.New()
					*(dest[1].(*uuid.UUID)) = sourceID
					*(dest[2].(*string)) = "proxmox"
					*(dest[3].(*pgtype.Text)) = pgtype.Text{Valid: false}
					*(dest[4].(*bool)) = true
					*(dest[5].(*pgtype.Timestamptz)) = pgtype.Timestamptz{Time: time.Now(), Valid: true}
					return nil
				},
			}
		}
		repo := repository.NewPgDiscoveryRepository(mdb, enc)

		cfg, err := repo.ResolveCollectorConfig(context.Background(), sourceID, "proxmox")
		if err != nil {
			t.Fatalf("expected nil error, got %v", err)
		}
		if len(cfg) != 0 {
			t.Errorf("expected empty config, got %v", cfg)
		}
	})

	t.Run("Successfully decrypts and unmarshals collector config", func(t *testing.T) {
		rawConfig := map[string]interface{}{
			"api_url":  "https://192.168.1.100:8006",
			"token_id": "root@pam!token",
		}
		rawBytes, _ := json.Marshal(rawConfig)
		encryptedStr, encErr := enc.Encrypt(rawBytes)
		if encErr != nil {
			t.Fatalf("failed to encrypt config: %v", encErr)
		}

		mdb := newMockDB()
		mdb.queryRowFunc = func(_ context.Context, _ string, _ ...interface{}) pgx.Row {
			return &mockRow{
				scanFunc: func(dest ...any) error {
					*(dest[0].(*uuid.UUID)) = uuid.New()
					*(dest[1].(*uuid.UUID)) = sourceID
					*(dest[2].(*string)) = "proxmox"
					*(dest[3].(*pgtype.Text)) = pgtype.Text{String: encryptedStr, Valid: true}
					*(dest[4].(*bool)) = true
					*(dest[5].(*pgtype.Timestamptz)) = pgtype.Timestamptz{Time: time.Now(), Valid: true}
					return nil
				},
			}
		}
		repo := repository.NewPgDiscoveryRepository(mdb, enc)

		cfg, err := repo.ResolveCollectorConfig(context.Background(), sourceID, "proxmox")
		if err != nil {
			t.Fatalf("expected nil error, got %v", err)
		}
		if cfg["api_url"] != "https://192.168.1.100:8006" {
			t.Errorf("expected api_url to match, got %v", cfg["api_url"])
		}
		if cfg["token_id"] != "root@pam!token" {
			t.Errorf("expected token_id to match, got %v", cfg["token_id"])
		}
	})

	t.Run("Fails when encryptor is nil but config_encrypted is present", func(t *testing.T) {
		mdb := newMockDB()
		mdb.queryRowFunc = func(_ context.Context, _ string, _ ...interface{}) pgx.Row {
			return &mockRow{
				scanFunc: func(dest ...any) error {
					*(dest[0].(*uuid.UUID)) = uuid.New()
					*(dest[1].(*uuid.UUID)) = sourceID
					*(dest[2].(*string)) = "proxmox"
					*(dest[3].(*pgtype.Text)) = pgtype.Text{String: "encrypted-ciphertext", Valid: true}
					*(dest[4].(*bool)) = true
					*(dest[5].(*pgtype.Timestamptz)) = pgtype.Timestamptz{Time: time.Now(), Valid: true}
					return nil
				},
			}
		}
		repo := repository.NewPgDiscoveryRepository(mdb, nil)

		_, err := repo.ResolveCollectorConfig(context.Background(), sourceID, "proxmox")
		if err == nil {
			t.Error("expected error when encryptor is missing for encrypted collector config")
		}
	})

	t.Run("Fails when decryption fails", func(t *testing.T) {
		mdb := newMockDB()
		mdb.queryRowFunc = func(_ context.Context, _ string, _ ...interface{}) pgx.Row {
			return &mockRow{
				scanFunc: func(dest ...any) error {
					*(dest[0].(*uuid.UUID)) = uuid.New()
					*(dest[1].(*uuid.UUID)) = sourceID
					*(dest[2].(*string)) = "proxmox"
					*(dest[3].(*pgtype.Text)) = pgtype.Text{String: "invalid-base64-payload", Valid: true}
					*(dest[4].(*bool)) = true
					*(dest[5].(*pgtype.Timestamptz)) = pgtype.Timestamptz{Time: time.Now(), Valid: true}
					return nil
				},
			}
		}
		repo := repository.NewPgDiscoveryRepository(mdb, enc)

		_, err := repo.ResolveCollectorConfig(context.Background(), sourceID, "proxmox")
		if err == nil {
			t.Error("expected error when decrypt fails")
		}
	})

	t.Run("Fails when unmarshaling invalid JSON", func(t *testing.T) {
		encryptedStr, _ := enc.Encrypt([]byte("not a json payload"))
		mdb := newMockDB()
		mdb.queryRowFunc = func(_ context.Context, _ string, _ ...interface{}) pgx.Row {
			return &mockRow{
				scanFunc: func(dest ...any) error {
					*(dest[0].(*uuid.UUID)) = uuid.New()
					*(dest[1].(*uuid.UUID)) = sourceID
					*(dest[2].(*string)) = "proxmox"
					*(dest[3].(*pgtype.Text)) = pgtype.Text{String: encryptedStr, Valid: true}
					*(dest[4].(*bool)) = true
					*(dest[5].(*pgtype.Timestamptz)) = pgtype.Timestamptz{Time: time.Now(), Valid: true}
					return nil
				},
			}
		}
		repo := repository.NewPgDiscoveryRepository(mdb, enc)

		_, err := repo.ResolveCollectorConfig(context.Background(), sourceID, "proxmox")
		if err == nil {
			t.Error("expected error when JSON unmarshal fails")
		}
	})
}
