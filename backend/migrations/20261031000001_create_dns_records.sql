-- +goose Up
-- RFC-027 §3: DNS records. Operator-owned source of truth for local home/lab names.
-- A and CNAME records, linked to a device explicitly (device_id) or implicitly by
-- address (an A record follows whichever live device holds its address).
CREATE TABLE dns_records (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(253) NOT NULL,
    record_type VARCHAR(8) NOT NULL,
    address INET NULL,
    target VARCHAR(253) NULL,
    device_id UUID NULL REFERENCES devices(id) ON DELETE SET NULL,
    notes TEXT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- name is stored lower-cased, without the trailing dot.
CREATE INDEX idx_dns_records_name ON dns_records(name);
CREATE INDEX idx_dns_records_device_id ON dns_records(device_id);

-- A record requires an address; CNAME requires a target.
-- Enforced at the DTO layer; repeated here as a guard for direct SQL writes.
ALTER TABLE dns_records ADD CONSTRAINT chk_dns_records_record_type
    CHECK (record_type IN ('A', 'CNAME'));

-- Unique on (name, record_type, address, target). A CNAME cannot share its name
-- with any other record (RFC 1034 §3.6.2).
CREATE UNIQUE INDEX idx_dns_records_unique_name_type_address_target
    ON dns_records(name, record_type, address, target);

-- +goose Down
DROP INDEX IF EXISTS idx_dns_records_unique_name_type_address_target;
DROP INDEX IF EXISTS idx_dns_records_device_id;
DROP INDEX IF EXISTS idx_dns_records_name;
ALTER TABLE dns_records DROP CONSTRAINT IF EXISTS chk_dns_records_record_type;
DROP TABLE IF EXISTS dns_records;
