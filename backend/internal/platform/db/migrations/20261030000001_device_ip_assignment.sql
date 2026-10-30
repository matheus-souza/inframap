-- +goose Up
-- RFC-027 §1: how the device's address is assigned. Operator intent, never written by
-- discovery (ADR-016); existing devices start as 'unknown'.
ALTER TABLE devices ADD COLUMN ip_assignment VARCHAR(16) NOT NULL DEFAULT 'unknown';
ALTER TABLE devices ADD CONSTRAINT chk_devices_ip_assignment
    CHECK (ip_assignment IN ('unknown', 'dynamic', 'reserved', 'static'));

-- +goose Down
ALTER TABLE devices DROP CONSTRAINT IF EXISTS chk_devices_ip_assignment;
ALTER TABLE devices DROP COLUMN IF EXISTS ip_assignment;
