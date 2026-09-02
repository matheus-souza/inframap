-- name: CreateDevice :one
INSERT INTO devices (
    id, hostname, ip_address, mac_address, manufacturer, model, serial_number, device_type, status, metadata, provider_scope
) VALUES (
    $1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11
) RETURNING *;

-- name: GetDeviceByID :one
SELECT * FROM devices
WHERE id = $1 AND (deleted_at IS NULL OR $2::boolean = true);

-- name: ListDevices :many
SELECT * FROM devices
WHERE (deleted_at IS NULL OR $1::boolean = true)
  AND ($2::text = '' OR hostname ILIKE '%' || $2 || '%' || $2 || '%')
  AND ($3::text = '' OR device_type = $3)
ORDER BY created_at DESC
LIMIT $4 OFFSET $5;

-- name: CountDevices :one
SELECT COUNT(*) FROM devices
WHERE (deleted_at IS NULL OR $1::boolean = true)
  AND ($2::text = '' OR hostname ILIKE '%' || $2 || '%' || $2 || '%')
  AND ($3::text = '' OR device_type = $3);

-- name: UpdateDevice :one
-- Observing a device is what proves it is still there, so every update refreshes
-- last_seen_at and clears the absence streak that drives the lifecycle hysteresis.
UPDATE devices
SET hostname = $2,
    ip_address = $3,
    mac_address = $4,
    manufacturer = $5,
    model = $6,
    serial_number = $7,
    device_type = $8,
    status = $9,
    metadata = $10,
    provider_scope = COALESCE(NULLIF(sqlc.arg(provider_scope)::text, ''), provider_scope),
    last_seen_at = NOW(),
    absence_count = 0,
    updated_at = NOW()
WHERE id = $1
RETURNING *;

-- name: ListDevicesByProviderScope :many
-- Every workload an authoritative provider owns in one scope. Archived devices are left
-- out: they have already reached the end of the lifecycle and must not be counted again.
SELECT * FROM devices
WHERE provider_scope = $1
  AND deleted_at IS NULL
  AND status <> 'archived';

-- name: MarkDeviceAbsent :one
-- Advances one device along the absence hysteresis: the first miss in an authoritative,
-- complete run takes it offline, the second archives it. last_seen_at is deliberately left
-- untouched so it keeps pointing at the last time the device was actually observed.
UPDATE devices
SET absence_count = absence_count + 1,
    status = CASE WHEN absence_count + 1 >= sqlc.arg(archive_threshold)::int THEN 'archived' ELSE 'offline' END,
    updated_at = NOW()
WHERE id = $1
RETURNING *;

-- name: SoftDeleteDevice :exec
UPDATE devices
SET deleted_at = NOW(),
    status = 'deleted',
    updated_at = NOW()
WHERE id = $1;

-- name: CreateStagingDevice :one
INSERT INTO device_staging (
    id, hostname, ip_address, mac_address, manufacturer, model, device_type, discovery_source_id, raw_payload, status
) VALUES (
    $1, $2, $3, $4, $5, $6, $7, $8, $9, $10
) RETURNING *;

-- name: GetStagingDeviceByID :one
SELECT * FROM device_staging WHERE id = $1;

-- name: ListStagingDevices :many
SELECT * FROM device_staging
WHERE status = $1
ORDER BY created_at DESC
LIMIT $2 OFFSET $3;

-- name: CountStagingDevices :one
SELECT COUNT(*) FROM device_staging WHERE status = $1;

-- name: UpdateStagingDeviceStatus :exec
UPDATE device_staging
SET status = $2, updated_at = NOW()
WHERE id = $1;

-- name: CreateSubnet :one
INSERT INTO subnets (
    id, name, cidr, vlan_id, gateway_ip, description, discovery_enabled
) VALUES (
    $1, $2, $3, $4, $5, $6, $7
) RETURNING *;

-- name: ListSubnets :many
SELECT * FROM subnets ORDER BY name ASC;

-- name: GetSubnetByID :one
SELECT * FROM subnets WHERE id = $1;
