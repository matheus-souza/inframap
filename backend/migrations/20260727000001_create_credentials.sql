-- +goose Up
-- SQL migration to create the credentials table for encrypted secret storage.

CREATE TABLE credentials (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    type VARCHAR(50) NOT NULL,
    encrypted_data TEXT NOT NULL,
    description TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_credentials_type ON credentials(type);
CREATE INDEX idx_credentials_created_at ON credentials(created_at DESC);

-- +goose Down
DROP TABLE IF EXISTS credentials;
