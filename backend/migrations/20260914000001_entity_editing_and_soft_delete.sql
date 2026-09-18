-- +goose Up
-- 1. Soft-delete columns on subnets and discovery_sources
ALTER TABLE subnets
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ;

ALTER TABLE discovery_sources
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_subnets_deleted_at ON subnets(deleted_at);
CREATE INDEX IF NOT EXISTS idx_discovery_sources_deleted_at ON discovery_sources(deleted_at);

-- 2. Partial unique indexes
ALTER TABLE subnets DROP CONSTRAINT IF EXISTS subnets_cidr_key;

CREATE UNIQUE INDEX IF NOT EXISTS uq_subnets_cidr_active
    ON subnets (cidr) WHERE deleted_at IS NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_discovery_sources_name_active
    ON discovery_sources (name) WHERE deleted_at IS NULL;

-- +goose Down
DROP INDEX IF EXISTS uq_discovery_sources_name_active;
DROP INDEX IF EXISTS uq_subnets_cidr_active;
ALTER TABLE subnets ADD CONSTRAINT subnets_cidr_key UNIQUE (cidr);
DROP INDEX IF EXISTS idx_discovery_sources_deleted_at;
DROP INDEX IF EXISTS idx_subnets_deleted_at;
ALTER TABLE discovery_sources DROP COLUMN IF EXISTS deleted_at;
ALTER TABLE subnets DROP COLUMN IF EXISTS deleted_at;
