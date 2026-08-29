-- +goose Up
-- +goose StatementBegin
CREATE TABLE IF NOT EXISTS discovery_source_collectors (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    source_id UUID NOT NULL REFERENCES discovery_sources(id) ON DELETE CASCADE,
    collector_type VARCHAR(64) NOT NULL CHECK (collector_type IN ('icmp_sweep', 'arp_sweep', 'mdns', 'reverse_dns', 'snmp', 'proxmox', 'docker', 'unifi')),
    config_encrypted TEXT,
    enabled BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_source_collector UNIQUE (source_id, collector_type)
);

CREATE INDEX IF NOT EXISTS idx_discovery_source_collectors_source_id ON discovery_source_collectors(source_id);

CREATE TABLE IF NOT EXISTS discovery_collector_runs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    source_id UUID NOT NULL REFERENCES discovery_sources(id) ON DELETE CASCADE,
    collector_type VARCHAR(64) NOT NULL CHECK (collector_type IN ('icmp_sweep', 'arp_sweep', 'mdns', 'reverse_dns', 'snmp', 'proxmox', 'docker', 'unifi')),
    status VARCHAR(32) NOT NULL CHECK (status IN ('success', 'error', 'timeout', 'skipped')),
    devices_found INT NOT NULL DEFAULT 0,
    duration_ms INT NOT NULL DEFAULT 0,
    error_message TEXT,
    started_at TIMESTAMPTZ NOT NULL,
    finished_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_discovery_collector_runs_source_id ON discovery_collector_runs(source_id);

ALTER TABLE discovery_sources
    DROP CONSTRAINT IF EXISTS discovery_sources_last_status_check;

ALTER TABLE discovery_sources
    ADD CONSTRAINT discovery_sources_last_status_check
    CHECK (last_status IN ('idle', 'running', 'error', 'cancelled', 'partial')) NOT VALID;

ALTER TABLE discovery_sources
    VALIDATE CONSTRAINT discovery_sources_last_status_check;

-- Data migration: seed source collectors with identical type as defined in ADR-011
INSERT INTO discovery_source_collectors (id, source_id, collector_type, config_encrypted, enabled, created_at)
SELECT gen_random_uuid(), ds.id, ds.type, ds.config_encrypted, ds.enabled, ds.created_at
FROM discovery_sources ds
WHERE ds.type IS NOT NULL AND ds.type != ''
ON CONFLICT (source_id, collector_type) DO NOTHING;
-- +goose StatementEnd

-- +goose Down
-- +goose StatementBegin
DROP TABLE IF EXISTS discovery_collector_runs;
DROP TABLE IF EXISTS discovery_source_collectors;

ALTER TABLE discovery_sources
    DROP CONSTRAINT IF EXISTS discovery_sources_last_status_check;

ALTER TABLE discovery_sources
    ADD CONSTRAINT discovery_sources_last_status_check
    CHECK (last_status IN ('idle', 'running', 'error', 'cancelled')) NOT VALID;

ALTER TABLE discovery_sources
    VALIDATE CONSTRAINT discovery_sources_last_status_check;
-- +goose StatementEnd
