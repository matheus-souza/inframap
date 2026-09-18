package repository

import (
	"context"
	"encoding/json"
	"errors"
	"strings"
	"testing"
	"time"

	"github.com/google/uuid"
	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgconn"
	"github.com/jackc/pgx/v5/pgtype"
	"github.com/matheussouza/inframap/internal/platform/crypto"
	"github.com/matheussouza/inframap/internal/platform/db"
)



type mockQueries struct {
	creds          map[uuid.UUID]db.Credential
	sources        []db.ListActiveDiscoverySourcesWithConfigRow
	collectors     []db.ListActiveDiscoveryCollectorsWithConfigRow
	failNext       bool
	failCount      bool
	failSources    bool
	failCollectors bool
}

func newMockQueries() *mockQueries {
	return &mockQueries{
		creds:      make(map[uuid.UUID]db.Credential),
		sources:    []db.ListActiveDiscoverySourcesWithConfigRow{},
		collectors: []db.ListActiveDiscoveryCollectorsWithConfigRow{},
	}
}

func (m *mockQueries) CreateCredential(_ context.Context, arg db.CreateCredentialParams) (db.Credential, error) {
	if m.failNext {
		m.failNext = false
		return db.Credential{}, errors.New("db error")
	}
	c := db.Credential(arg)
	m.creds[arg.ID] = c
	return c, nil
}

func (m *mockQueries) GetCredentialByID(_ context.Context, id uuid.UUID) (db.Credential, error) {
	if m.failNext {
		m.failNext = false
		return db.Credential{}, errors.New("connection refused")
	}
	c, ok := m.creds[id]
	if !ok {
		return db.Credential{}, pgx.ErrNoRows
	}
	return c, nil
}

func (m *mockQueries) GetCredentialByIDForUpdate(ctx context.Context, id uuid.UUID) (db.Credential, error) {
	return m.GetCredentialByID(ctx, id)
}

func (m *mockQueries) ListCredentials(_ context.Context, _ db.ListCredentialsParams) ([]db.ListCredentialsRow, error) {
	if m.failNext {
		m.failNext = false
		return nil, errors.New("db error")
	}
	rows := make([]db.ListCredentialsRow, 0, len(m.creds))
	for _, v := range m.creds {
		rows = append(rows, db.ListCredentialsRow{
			ID:          v.ID,
			Name:        v.Name,
			Type:        v.Type,
			Description: v.Description,
			CreatedAt:   v.CreatedAt,
			UpdatedAt:   v.UpdatedAt,
		})
	}
	return rows, nil
}

func (m *mockQueries) CountCredentials(_ context.Context) (int64, error) {
	if m.failCount {
		m.failCount = false
		return 0, errors.New("count error")
	}
	return int64(len(m.creds)), nil
}

func (m *mockQueries) DeleteCredential(_ context.Context, id uuid.UUID) (int64, error) {
	if m.failNext {
		m.failNext = false
		return 0, errors.New("db error")
	}
	if _, ok := m.creds[id]; !ok {
		return 0, nil
	}
	delete(m.creds, id)
	return 1, nil
}

func (m *mockQueries) ListActiveDiscoverySourcesWithConfig(_ context.Context) ([]db.ListActiveDiscoverySourcesWithConfigRow, error) {
	if m.failSources {
		m.failSources = false
		return nil, errors.New("db error listing sources")
	}
	return m.sources, nil
}

func (m *mockQueries) ListActiveDiscoveryCollectorsWithConfig(_ context.Context) ([]db.ListActiveDiscoveryCollectorsWithConfigRow, error) {
	if m.failCollectors {
		m.failCollectors = false
		return nil, errors.New("db error listing collectors")
	}
	return m.collectors, nil
}


func TestNewPgxRepository_Rule8_EncryptorMandatory(t *testing.T) {
	enc, err := crypto.NewAESGCMEncryptor("12345678901234567890123456789012")
	if err != nil {
		t.Fatalf("failed to create test encryptor: %v", err)
	}

	t.Run("Missing Queries Rejection", func(t *testing.T) {
		repo, err := NewPgxRepository(nil, enc)
		if !errors.Is(err, ErrNilQueries) || repo != nil {
			t.Errorf("expected ErrNilQueries, got %v", err)
		}
	})

	t.Run("Missing Encryptor Rejection (Rule 8)", func(t *testing.T) {
		q := &db.Queries{}
		repo, err := NewPgxRepository(q, nil)
		if !errors.Is(err, ErrMissingEncryptor) || repo != nil {
			t.Errorf("expected ErrMissingEncryptor, got %v", err)
		}
	})
}

func newTestRepo(t *testing.T) (*PgxRepository, *mockQueries) {
	t.Helper()
	enc, err := crypto.NewAESGCMEncryptor("12345678901234567890123456789012")
	if err != nil {
		t.Fatalf("failed to create encryptor: %v", err)
	}
	mq := newMockQueries()
	repo := &PgxRepository{queries: mq, encryptor: enc}
	return repo, mq
}

func TestPgxRepository_CreateAndGetByID(t *testing.T) {
	repo, _ := newTestRepo(t)
	ctx := context.Background()

	id := uuid.New()
	cred := &db.Credential{
		ID:          id,
		Name:        "Test Cred",
		Type:        "api_token",
		Description: pgtype.Text{String: "desc", Valid: true},
	}

	created, err := repo.Create(ctx, cred, "my-secret-123")
	if err != nil {
		t.Fatalf("Create failed: %v", err)
	}
	if created.ID != id {
		t.Errorf("expected ID %s, got %s", id, created.ID)
	}
	if created.EncryptedData == "my-secret-123" {
		t.Error("expected encrypted data, got plaintext")
	}

	fetched, secret, err := repo.GetByID(ctx, id)
	if err != nil {
		t.Fatalf("GetByID failed: %v", err)
	}
	if secret != "my-secret-123" {
		t.Errorf("expected decrypted secret 'my-secret-123', got '%s'", secret)
	}
	if fetched.Name != "Test Cred" {
		t.Errorf("expected name 'Test Cred', got '%s'", fetched.Name)
	}
}

func TestPgxRepository_Create_EmptySecret(t *testing.T) {
	repo, _ := newTestRepo(t)
	ctx := context.Background()
	cred := &db.Credential{ID: uuid.New(), Name: "x", Type: "api_token"}

	_, err := repo.Create(ctx, cred, "")
	if err == nil {
		t.Error("expected error for empty secret")
	}
}

func TestPgxRepository_Create_DBError(t *testing.T) {
	repo, mq := newTestRepo(t)
	ctx := context.Background()
	cred := &db.Credential{ID: uuid.New(), Name: "x", Type: "api_token"}

	mq.failNext = true
	_, err := repo.Create(ctx, cred, "secret")
	if err == nil {
		t.Error("expected error on DB failure")
	}
}

func TestPgxRepository_GetByID_NotFound(t *testing.T) {
	repo, _ := newTestRepo(t)
	ctx := context.Background()

	_, _, err := repo.GetByID(ctx, uuid.New())
	if !errors.Is(err, ErrNotFound) {
		t.Errorf("expected ErrNotFound, got %v", err)
	}
}

func TestPgxRepository_GetByID_DBError(t *testing.T) {
	repo, mq := newTestRepo(t)
	ctx := context.Background()

	mq.failNext = true
	_, _, err := repo.GetByID(ctx, uuid.New())
	if err == nil {
		t.Fatal("expected error on DB failure")
	}
	if errors.Is(err, ErrNotFound) {
		t.Error("DB errors must NOT be mapped to ErrNotFound")
	}
}

func TestPgxRepository_List(t *testing.T) {
	repo, _ := newTestRepo(t)
	ctx := context.Background()

	cred := &db.Credential{ID: uuid.New(), Name: "Listed", Type: "ssh_key",
		Description: pgtype.Text{String: "d", Valid: true}}
	_, _ = repo.Create(ctx, cred, "ssh-secret")

	items, total, err := repo.List(ctx, 10, 0)
	if err != nil {
		t.Fatalf("List failed: %v", err)
	}
	if total != 1 || len(items) != 1 {
		t.Errorf("expected 1 item and total 1, got %d items, total %d", len(items), total)
	}
	if items[0].EncryptedData != "" {
		t.Error("List should not include encrypted_data")
	}
}

func TestPgxRepository_List_DefaultLimits(t *testing.T) {
	repo, _ := newTestRepo(t)
	ctx := context.Background()

	items, _, err := repo.List(ctx, -1, -5)
	if err != nil {
		t.Fatalf("List with negative params failed: %v", err)
	}
	if len(items) != 0 {
		t.Errorf("expected 0 items, got %d", len(items))
	}
}

func TestPgxRepository_List_DBError(t *testing.T) {
	repo, mq := newTestRepo(t)
	ctx := context.Background()

	mq.failNext = true
	_, _, err := repo.List(ctx, 10, 0)
	if err == nil {
		t.Error("expected error on list DB failure")
	}
}

func TestPgxRepository_List_CountError(t *testing.T) {
	repo, mq := newTestRepo(t)
	ctx := context.Background()

	mq.failCount = true
	_, _, err := repo.List(ctx, 10, 0)
	if err == nil {
		t.Error("expected error on count DB failure")
	}
}

func TestPgxRepository_Delete(t *testing.T) {
	repo, _ := newTestRepo(t)
	ctx := context.Background()

	id := uuid.New()
	cred := &db.Credential{ID: id, Name: "ToDelete", Type: "custom_secret"}
	_, _ = repo.Create(ctx, cred, "doomed-secret")

	err := repo.Delete(ctx, id)
	if err != nil {
		t.Fatalf("Delete failed: %v", err)
	}

	err = repo.Delete(ctx, id)
	if !errors.Is(err, ErrNotFound) {
		t.Errorf("expected ErrNotFound on second delete, got %v", err)
	}
}

func TestPgxRepository_Delete_DBError(t *testing.T) {
	repo, mq := newTestRepo(t)
	ctx := context.Background()

	mq.failNext = true
	err := repo.Delete(ctx, uuid.New())
	if err == nil {
		t.Error("expected error on delete DB failure")
	}
}

func TestPgxRepository_GetByID_DecryptionFailure(t *testing.T) {
	enc1, _ := crypto.NewAESGCMEncryptor("12345678901234567890123456789012")
	enc2, _ := crypto.NewAESGCMEncryptor("abcdefghijklmnopqrstuvwxyz123456")
	mq := newMockQueries()

	repoWrite := &PgxRepository{queries: mq, encryptor: enc1}
	repoRead := &PgxRepository{queries: mq, encryptor: enc2}
	ctx := context.Background()

	id := uuid.New()
	cred := &db.Credential{ID: id, Name: "WrongKey", Type: "api_token",
		CreatedAt: pgtype.Timestamptz{Time: time.Now(), Valid: true},
		UpdatedAt: pgtype.Timestamptz{Time: time.Now(), Valid: true},
	}
	_, _ = repoWrite.Create(ctx, cred, "secret-data")

	_, _, err := repoRead.GetByID(ctx, id)
	if err == nil {
		t.Error("expected decryption failure with wrong key")
	}
}

func TestPgxRepository_CountActiveReferences(t *testing.T) {
	repo, mq := newTestRepo(t)
	ctx := context.Background()

	credID := uuid.New()
	otherCredID := uuid.New()

	// 1. Source referencing credID
	srcCfgBytes, _ := json.Marshal(map[string]interface{}{
		"credential_id": credID.String(),
	})
	encSrcCfg, _ := repo.encryptor.Encrypt(srcCfgBytes)
	srcID := uuid.New()
	mq.sources = append(mq.sources, db.ListActiveDiscoverySourcesWithConfigRow{
		ID:              srcID,
		Name:            "Proxmox Production",
		ConfigEncrypted: pgtype.Text{String: encSrcCfg, Valid: true},
	})

	// 2. Collector 1 under source 2 referencing credID
	col1CfgBytes, _ := json.Marshal(map[string]interface{}{
		"credential_id": credID.String(),
	})
	encCol1Cfg, _ := repo.encryptor.Encrypt(col1CfgBytes)
	source2ID := uuid.New()
	mq.collectors = append(mq.collectors, db.ListActiveDiscoveryCollectorsWithConfigRow{
		SourceID:        source2ID,
		SourceName:      "Docker Swarm",
		ConfigEncrypted: pgtype.Text{String: encCol1Cfg, Valid: true},
	})

	// 3. Collector 2 under same source 2 also referencing credID (must be deduplicated)
	col2CfgBytes, _ := json.Marshal(map[string]interface{}{
		"credential_id": credID.String(),
	})
	encCol2Cfg, _ := repo.encryptor.Encrypt(col2CfgBytes)
	mq.collectors = append(mq.collectors, db.ListActiveDiscoveryCollectorsWithConfigRow{
		SourceID:        source2ID,
		SourceName:      "Docker Swarm",
		ConfigEncrypted: pgtype.Text{String: encCol2Cfg, Valid: true},
	})

	// 4. Source referencing a different credential
	otherCfgBytes, _ := json.Marshal(map[string]interface{}{
		"credential_id": otherCredID.String(),
	})
	encOtherCfg, _ := repo.encryptor.Encrypt(otherCfgBytes)
	mq.sources = append(mq.sources, db.ListActiveDiscoverySourcesWithConfigRow{
		ID:              uuid.New(),
		Name:            "Other Source",
		ConfigEncrypted: pgtype.Text{String: encOtherCfg, Valid: true},
	})

	// Count references for credID: should be 2 (Proxmox Production + Docker Swarm deduplicated)
	count, sources, err := repo.CountActiveReferences(ctx, credID)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if count != 2 || len(sources) != 2 {
		t.Fatalf("expected 2 active references, got %d (sources: %+v)", count, sources)
	}
	if (sources[0].Name != "Proxmox Production" && sources[1].Name != "Proxmox Production") ||
		(sources[0].Name != "Docker Swarm" && sources[1].Name != "Docker Swarm") {
		t.Errorf("expected Proxmox Production and Docker Swarm in sources: %+v", sources)
	}

	// Count references for unrelated UUID: should be 0
	unrelatedID := uuid.New()
	countZero, sourcesZero, err := repo.CountActiveReferences(ctx, unrelatedID)
	if err != nil {
		t.Fatalf("unexpected error for unused cred: %v", err)
	}
	if countZero != 0 || len(sourcesZero) != 0 {
		t.Errorf("expected 0 references, got %d", countZero)
	}

	// Error on sources query
	mq.failSources = true
	_, _, err = repo.CountActiveReferences(ctx, credID)
	if err == nil {
		t.Error("expected error when listing sources fails")
	}

	// Error on collectors query
	mq.failCollectors = true
	_, _, err = repo.CountActiveReferences(ctx, credID)
	if err == nil {
		t.Error("expected error when listing collectors fails")
	}
}

type mockRow struct {
	scanFunc func(dest ...any) error
}

func (r *mockRow) Scan(dest ...any) error {
	if r.scanFunc != nil {
		return r.scanFunc(dest...)
	}
	return nil
}

type mockRows struct {
	pgx.Rows
	closed bool
}

func (r *mockRows) Close() {
	r.closed = true
}

func (r *mockRows) Next() bool {
	return false
}

func (r *mockRows) Err() error {
	return nil
}

type mockDBTX struct {
	committed  bool
	rolledBack bool
	deleted    bool
	failLock   bool
	queryFunc  func(ctx context.Context, sql string, args ...interface{}) (pgx.Rows, error)
}

func (m *mockDBTX) Begin(_ context.Context) (pgx.Tx, error) {
	return &mockTxWrapper{mock: m}, nil
}

func (m *mockDBTX) Exec(_ context.Context, _ string, _ ...interface{}) (pgconn.CommandTag, error) {
	m.deleted = true
	return pgconn.NewCommandTag("DELETE 1"), nil
}

func (m *mockDBTX) Query(ctx context.Context, sql string, args ...interface{}) (pgx.Rows, error) {
	if m.queryFunc != nil {
		return m.queryFunc(ctx, sql, args...)
	}
	return &mockRows{}, nil
}

func (m *mockDBTX) QueryRow(_ context.Context, _ string, _ ...interface{}) pgx.Row {
	if m.failLock {
		return &mockRow{scanFunc: func(_ ...any) error { return pgx.ErrNoRows }}
	}
	return &mockRow{}
}

type mockRowsWithData struct {
	pgx.Rows
	data   [][]any
	index  int
	closed bool
}

func (r *mockRowsWithData) Close() {
	r.closed = true
}

func (r *mockRowsWithData) Next() bool {
	r.index++
	return r.index <= len(r.data)
}

func (r *mockRowsWithData) Scan(dest ...any) error {
	row := r.data[r.index-1]
	for i, val := range row {
		if i < len(dest) {
			switch d := dest[i].(type) {
			case *uuid.UUID:
				*d = val.(uuid.UUID)
			case *string:
				*d = val.(string)
			case *pgtype.Text:
				*d = val.(pgtype.Text)
			}
		}
	}
	return nil
}

func (r *mockRowsWithData) Err() error {
	return nil
}

type mockTxWrapper struct {
	pgx.Tx
	mock *mockDBTX
}

func (t *mockTxWrapper) Commit(_ context.Context) error {
	t.mock.committed = true
	return nil
}

func (t *mockTxWrapper) Rollback(_ context.Context) error {
	t.mock.rolledBack = true
	return nil
}

func (t *mockTxWrapper) Exec(ctx context.Context, sql string, args ...interface{}) (pgconn.CommandTag, error) {
	return t.mock.Exec(ctx, sql, args...)
}

func (t *mockTxWrapper) Query(ctx context.Context, sql string, args ...interface{}) (pgx.Rows, error) {
	return t.mock.Query(ctx, sql, args...)
}

func (t *mockTxWrapper) QueryRow(ctx context.Context, sql string, args ...interface{}) pgx.Row {
	return t.mock.QueryRow(ctx, sql, args...)
}

func TestPgxRepository_Delete_WithDatabaseTx(t *testing.T) {
	repo, _ := newTestRepo(t)
	ctx := context.Background()

	mockDB := &mockDBTX{}
	repo.WithDatabase(mockDB)

	id := uuid.New()

	// 1. Success delete in transaction
	err := repo.Delete(ctx, id)
	if err != nil {
		t.Fatalf("unexpected error on transactional delete: %v", err)
	}
	if !mockDB.committed {
		t.Error("expected transaction to be committed")
	}
	if !mockDB.deleted {
		t.Error("expected Exec to be called for deletion")
	}

	// 2. Lock not found -> ErrNotFound
	mockDB.failLock = true
	err = repo.Delete(ctx, id)
	if !errors.Is(err, ErrNotFound) {
		t.Errorf("expected ErrNotFound on missing row lock, got %v", err)
	}
	if !mockDB.rolledBack {
		t.Error("expected rollback on lock failure")
	}

	// 3. Blocked by active reference in transaction -> returns ErrCredentialInUse, rolledBack = true, deleted = false
	mockDB3 := &mockDBTX{}
	repo.WithDatabase(mockDB3)

	srcCfgBytes, _ := json.Marshal(map[string]interface{}{
		"credential_id": id.String(),
	})
	encCfg, _ := repo.encryptor.Encrypt(srcCfgBytes)

	mockDB3.queryFunc = func(_ context.Context, sql string, _ ...interface{}) (pgx.Rows, error) {
		if strings.Contains(sql, "FROM discovery_sources\n") {
			return &mockRowsWithData{
				data: [][]any{
					{uuid.New(), "Source In Tx", pgtype.Text{String: encCfg, Valid: true}},
				},
			}, nil
		}
		return &mockRows{}, nil
	}


	err = repo.Delete(ctx, id)
	if err == nil {
		t.Fatal("expected error on active reference during transactional delete")
	}
	var inUseErr *ErrCredentialInUse
	if !errors.As(err, &inUseErr) {
		t.Fatalf("expected *ErrCredentialInUse, got %T (%v)", err, err)
	}
	if len(inUseErr.DependentSources) != 1 || inUseErr.DependentSources[0].Name != "Source In Tx" {
		t.Errorf("unexpected dependent sources: %+v", inUseErr.DependentSources)
	}
	if !mockDB3.rolledBack {
		t.Error("expected rollback when active references exist")
	}
	if mockDB3.committed {
		t.Error("did not expect commit when active references exist")
	}
	if mockDB3.deleted {
		t.Error("did not expect delete when active references exist")
	}
}


