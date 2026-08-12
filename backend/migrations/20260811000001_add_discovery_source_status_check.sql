-- +goose Up
ALTER TABLE discovery_sources
    ADD CONSTRAINT discovery_sources_last_status_check
    CHECK (last_status IN ('idle', 'running', 'error', 'cancelled'));

-- +goose Down
ALTER TABLE discovery_sources
    DROP CONSTRAINT IF EXISTS discovery_sources_last_status_check;
