-- name: CreateDevice :one
INSERT INTO devices (
    id, hostname, ip_address, mac_address, manufacturer, model, serial_number, device_type, status, metadata
) VALUES (
    $1, $2, $3, $4, $5, $6, $7, $8, $9, $10
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
