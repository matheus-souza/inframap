-- +goose NO TRANSACTION
-- +goose Up
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_discovery_collector_runs_finished_at
    ON discovery_collector_runs (finished_at ASC, id ASC);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_discovery_collector_runs_source_started
    ON discovery_collector_runs (source_id, started_at DESC, id DESC);

-- +goose Down
DROP INDEX CONCURRENTLY IF EXISTS idx_discovery_collector_runs_source_started;

DROP INDEX CONCURRENTLY IF EXISTS idx_discovery_collector_runs_finished_at;

