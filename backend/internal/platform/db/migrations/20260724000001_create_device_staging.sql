-- +goose Up
-- =============================================================================
-- Module 2 Extension: Device Staging Queue — RFC-014 Compliant
-- =============================================================================

CREATE TABLE device_staging (
    id UUID PRIMARY KEY,
    hostname VARCHAR(255) NOT NULL,
    ip_address INET,
    mac_address MACADDR,
    manufacturer VARCHAR(128),
    model VARCHAR(128),
    device_type VARCHAR(64) NOT NULL DEFAULT 'unknown',
    discovery_source_id UUID REFERENCES discovery_sources(id) ON DELETE SET NULL,
    raw_payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    status VARCHAR(32) NOT NULL DEFAULT 'pending',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_device_staging_status ON device_staging(status);
CREATE INDEX idx_device_staging_ip ON device_staging(ip_address);
CREATE INDEX idx_device_staging_mac ON device_staging(mac_address);

CREATE TRIGGER update_device_staging_updated_at
    BEFORE UPDATE ON device_staging
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- +goose Down
DROP TRIGGER IF EXISTS update_device_staging_updated_at ON device_staging;
DROP TABLE IF EXISTS device_staging;
