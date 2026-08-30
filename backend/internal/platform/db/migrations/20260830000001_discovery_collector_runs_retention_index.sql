-- +goose Up
-- +goose StatementBegin
CREATE INDEX IF NOT EXISTS idx_discovery_collector_runs_finished_at
    ON discovery_collector_runs (finished_at ASC, id ASC);
-- +goose StatementEnd

-- +goose Down
-- +goose StatementBegin
DROP INDEX IF EXISTS idx_discovery_collector_runs_finished_at;
-- +goose StatementEnd
