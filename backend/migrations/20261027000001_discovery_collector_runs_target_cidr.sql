-- +goose Up
-- RFC-026 §4: per_target collectors record one run row per target; union and
-- none scopes keep a NULL target_cidr.
ALTER TABLE discovery_collector_runs ADD COLUMN target_cidr CIDR NULL;

-- +goose Down
ALTER TABLE discovery_collector_runs DROP COLUMN IF EXISTS target_cidr;
