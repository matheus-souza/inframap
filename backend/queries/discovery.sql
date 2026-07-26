-- name: CreateDiscoverySource :one
INSERT INTO discovery_sources (
    id, name, type, enabled, schedule_cron, config_encrypted, last_status
) VALUES (
    $1, $2, $3, $4, $5, $6, $7
) RETURNING *;

-- name: GetDiscoverySourceByID :one
SELECT * FROM discovery_sources WHERE id = $1;

-- name: ListDiscoverySources :many
SELECT * FROM discovery_sources ORDER BY created_at DESC;

-- name: UpdateDiscoverySourceStatus :one
UPDATE discovery_sources
SET last_status = $2,
    last_run_at = NOW(),
    updated_at = NOW()
WHERE id = $1
RETURNING *;

-- name: UpsertDeviceDiscoveryRecord :one
INSERT INTO device_discovery_records (
    id, device_id, discovery_source_id, matched_by, raw_payload, last_scanned_at
) VALUES (
    $1, $2, $3, $4, $5, NOW()
)
ON CONFLICT (device_id, discovery_source_id) DO UPDATE
SET matched_by = EXCLUDED.matched_by,
    raw_payload = EXCLUDED.raw_payload,
    last_scanned_at = NOW()
RETURNING *;

-- name: ListDiscoveryRecordsByDevice :many
SELECT * FROM device_discovery_records
WHERE device_id = $1
ORDER BY last_scanned_at DESC;

-- name: ListDiscoveryRecordsBySource :many
SELECT * FROM device_discovery_records
WHERE discovery_source_id = $1
ORDER BY last_scanned_at DESC
LIMIT $2 OFFSET $3;

-- name: DeleteDiscoverySource :exec
DELETE FROM discovery_sources WHERE id = $1;
