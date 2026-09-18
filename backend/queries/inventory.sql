-- name: CreateDevice :one
INSERT INTO devices (
    id, hostname, ip_address, mac_address, manufacturer, model, serial_number, device_type, status, metadata, provider_scope, parent_provider_ref
) VALUES (
    $1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, $12
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

-- name: GetDeviceByProviderRef :one
-- Resolves a workload by its canonical identity, backed by the partial unique index
-- uq_devices_provider_ref.
SELECT * FROM devices
WHERE metadata->>'provider_ref' = sqlc.arg(provider_ref)::text AND deleted_at IS NULL;

-- name: ListDevicesPendingParentResolution :many
-- Children that declared a parent the engine has not resolved to a device yet, which
-- happens whenever a workload is discovered before its host. Backed by
-- idx_devices_parent_provider_ref_pending.
SELECT * FROM devices
WHERE parent_provider_ref = $1
  AND parent_device_id IS NULL
  AND deleted_at IS NULL;

-- name: SetDeviceParent :one
-- Anchors a workload to its host. Kept apart from UpdateDevice so field reconciliation can
-- never clobber the containment hierarchy, and vice versa.
UPDATE devices
SET parent_device_id = $2,
    parent_provider_ref = COALESCE(NULLIF(sqlc.arg(parent_provider_ref)::text, ''), parent_provider_ref),
    updated_at = NOW()
WHERE id = $1
RETURNING *;

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

-- name: RestoreDevice :one
UPDATE devices
SET deleted_at = NULL,
    status = 'active',
    hostname = CASE WHEN sqlc.arg(hostname)::text <> '' THEN sqlc.arg(hostname)::text ELSE hostname END,
    ip_address = COALESCE(sqlc.narg(ip_address), ip_address),
    mac_address = COALESCE(sqlc.narg(mac_address), mac_address),
    manufacturer = CASE WHEN sqlc.arg(manufacturer)::text <> '' THEN sqlc.arg(manufacturer)::text ELSE manufacturer END,
    model = CASE WHEN sqlc.arg(model)::text <> '' THEN sqlc.arg(model)::text ELSE model END,
    device_type = CASE WHEN sqlc.arg(device_type)::text <> '' THEN sqlc.arg(device_type)::text ELSE device_type END,
    last_seen_at = NOW(),
    absence_count = 0,
    updated_at = NOW()
WHERE id = $1
RETURNING *;

-- name: CreateStagingDevice :one
INSERT INTO device_staging (
    id, hostname, ip_address, mac_address, manufacturer, model, device_type, discovery_source_id, raw_payload, status
) VALUES (
    $1, $2, $3, $4, $5, $6, $7, $8, $9, $10
) RETURNING *;

-- name: GetStagingDeviceByID :one
SELECT * FROM device_staging WHERE id = $1;

-- name: FindPendingStagingDevice :one
SELECT * FROM device_staging
WHERE status IN ('pending', 'discovered')
  AND (
    (sqlc.narg('ip_address')::inet IS NOT NULL AND ip_address = sqlc.narg('ip_address'))
    OR (sqlc.narg('mac_address')::macaddr IS NOT NULL AND mac_address = sqlc.narg('mac_address'))
    OR (sqlc.narg('matched_device_id')::text IS NOT NULL AND (raw_payload->>'matched_device_id' = sqlc.narg('matched_device_id')::text OR raw_payload->'metadata'->>'matched_device_id' = sqlc.narg('matched_device_id')::text))
  )
LIMIT 1;

-- name: UpdateStagingDevice :one
UPDATE device_staging
SET hostname = CASE WHEN sqlc.arg(hostname)::text <> '' THEN sqlc.arg(hostname)::text ELSE hostname END,
    ip_address = COALESCE(sqlc.narg(ip_address), ip_address),
    mac_address = COALESCE(sqlc.narg(mac_address), mac_address),
    device_type = CASE WHEN sqlc.arg(device_type)::text <> '' THEN sqlc.arg(device_type)::text ELSE device_type END,
    raw_payload = sqlc.arg(raw_payload),
    updated_at = NOW()
WHERE id = $1
RETURNING *;

-- name: ListStagingDevices :many
SELECT * FROM device_staging
WHERE status = $1 OR ($1 = 'pending' AND status = 'discovered')
ORDER BY created_at DESC
LIMIT $2 OFFSET $3;

-- name: CountStagingDevices :one
SELECT COUNT(*) FROM device_staging WHERE status = $1 OR ($1 = 'pending' AND status = 'discovered');

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
SELECT * FROM subnets WHERE deleted_at IS NULL ORDER BY name ASC;

-- name: GetSubnetByID :one
SELECT * FROM subnets WHERE id = $1 AND deleted_at IS NULL;

-- name: UpdateSubnet :one
UPDATE subnets
SET name = $2,
    cidr = $3,
    vlan_id = $4,
    gateway_ip = $5,
    description = $6,
    discovery_enabled = $7,
    updated_at = NOW()
WHERE id = $1 AND deleted_at IS NULL
RETURNING *;

-- name: SoftDeleteSubnet :exec
UPDATE subnets
SET deleted_at = NOW(),
    updated_at = NOW()
WHERE id = $1 AND deleted_at IS NULL;

-- name: GetInactiveDiscoverySourceDeviceIDs :many
SELECT DISTINCT ddr.device_id
FROM device_discovery_records ddr
JOIN discovery_sources ds ON ds.id = ddr.discovery_source_id
WHERE ddr.device_id = ANY($1::uuid[])
  AND ds.deleted_at IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM device_discovery_records ddr2
      JOIN discovery_sources ds2 ON ds2.id = ddr2.discovery_source_id
      WHERE ddr2.device_id = ddr.device_id AND ds2.deleted_at IS NULL
  );

