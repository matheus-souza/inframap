-- +goose NO TRANSACTION
-- +goose Up

-- Authoritative providers report a closed set of workloads per scope. Recording the scope a
-- device belongs to lets a complete run tell "this workload is gone" apart from "this
-- workload was never in this run's scope", which is what keeps a Docker sweep from
-- archiving Proxmox VMs.
ALTER TABLE devices
    ADD COLUMN IF NOT EXISTS provider_scope TEXT,
    ADD COLUMN IF NOT EXISTS absence_count SMALLINT NOT NULL DEFAULT 0;

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_devices_provider_scope_active
    ON devices(provider_scope)
    WHERE provider_scope IS NOT NULL AND deleted_at IS NULL;

-- +goose Down
DROP INDEX CONCURRENTLY IF EXISTS idx_devices_provider_scope_active;

ALTER TABLE devices
    DROP COLUMN IF EXISTS absence_count,
    DROP COLUMN IF EXISTS provider_scope;
