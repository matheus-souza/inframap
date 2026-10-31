-- +goose Up
-- RFC-027 §2: Subnet address map. Operator-owned view of which host addresses are
-- free, in use, reserved or in conflict within each Subnet's CIDR. No new tables:
-- the map is computed live from devices.ip_address and devices.ip_assignment.

-- +goose Down
-- No schema change to roll back.
