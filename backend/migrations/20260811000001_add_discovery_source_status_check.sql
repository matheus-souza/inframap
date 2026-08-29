-- +goose Up
-- +goose StatementBegin
UPDATE discovery_sources
SET last_status = 'idle'
WHERE last_status NOT IN ('idle', 'running', 'error', 'cancelled');
-- +goose StatementEnd

ALTER TABLE discovery_sources
    ADD CONSTRAINT discovery_sources_last_status_check
    CHECK (last_status IN ('idle', 'running', 'error', 'cancelled')) NOT VALID;

ALTER TABLE discovery_sources
    VALIDATE CONSTRAINT discovery_sources_last_status_check;

-- +goose Down
ALTER TABLE discovery_sources
    DROP CONSTRAINT IF EXISTS discovery_sources_last_status_check;
