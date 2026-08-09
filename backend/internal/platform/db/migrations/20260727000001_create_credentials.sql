-- +goose Up
-- SQL migration to create the credentials table for encrypted secret storage.

CREATE TABLE credentials (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    type VARCHAR(50) NOT NULL CHECK (type IN ('snmp_v2c', 'snmp_v3', 'ssh_key', 'api_token', 'custom_secret')),
    encrypted_data TEXT NOT NULL,
    description TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_credentials_type ON credentials(type);
CREATE INDEX idx_credentials_created_at ON credentials(created_at DESC);

CREATE TRIGGER update_credentials_updated_at
    BEFORE UPDATE ON credentials
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- +goose Down
DROP TRIGGER IF EXISTS update_credentials_updated_at ON credentials;
DROP TABLE IF EXISTS credentials;
