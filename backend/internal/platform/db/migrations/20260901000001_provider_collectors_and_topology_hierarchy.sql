-- +goose NO TRANSACTION
-- +goose Up
ALTER TABLE devices
    ADD COLUMN IF NOT EXISTS parent_provider_ref TEXT,
    ADD COLUMN IF NOT EXISTS parent_device_id UUID REFERENCES devices(id) ON DELETE SET NULL;

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_devices_parent_device_id ON devices(parent_device_id);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_devices_parent_provider_ref_pending ON devices(parent_provider_ref) WHERE parent_device_id IS NULL AND deleted_at IS NULL;

CREATE UNIQUE INDEX CONCURRENTLY IF NOT EXISTS uq_devices_provider_ref ON devices((metadata->>'provider_ref')) WHERE deleted_at IS NULL AND metadata ? 'provider_ref';

ALTER TABLE topology_links DROP CONSTRAINT IF EXISTS uq_topology_links_source_target_type;
ALTER TABLE topology_links ADD CONSTRAINT uq_topology_links_source_target_type UNIQUE (source_device_id, target_device_id, link_type);

-- +goose Down
ALTER TABLE topology_links DROP CONSTRAINT IF EXISTS uq_topology_links_source_target_type;

DROP INDEX CONCURRENTLY IF EXISTS uq_devices_provider_ref;
DROP INDEX CONCURRENTLY IF EXISTS idx_devices_parent_provider_ref_pending;
DROP INDEX CONCURRENTLY IF EXISTS idx_devices_parent_device_id;

ALTER TABLE devices
    DROP COLUMN IF EXISTS parent_device_id,
    DROP COLUMN IF EXISTS parent_provider_ref;
