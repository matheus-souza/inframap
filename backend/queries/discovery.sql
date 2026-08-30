-- name: CreateDiscoverySource :one
INSERT INTO discovery_sources (
    id, name, type, enabled, schedule_cron, config_encrypted, last_status
) VALUES (
    $1, $2, $3, $4, $5, $6, $7
) RETURNING *;

-- name: GetDiscoverySourceByID :one
SELECT * FROM discovery_sources WHERE id = $1;

-- name: ListDiscoverySources :many
SELECT * FROM discovery_sources ORDER BY created_at DESC, id DESC;

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
ORDER BY last_scanned_at DESC, id DESC;

-- name: ListDiscoveryRecordsBySource :many
SELECT * FROM device_discovery_records
WHERE discovery_source_id = $1
ORDER BY last_scanned_at DESC, id DESC
LIMIT $2 OFFSET $3;

-- name: DeleteDiscoverySource :execrows
DELETE FROM discovery_sources WHERE id = $1;

-- name: CreateDiscoverySourceCollector :one
INSERT INTO discovery_source_collectors (
    id, source_id, collector_type, config_encrypted, enabled, created_at
) VALUES (
    $1, $2, $3, $4, $5, $6
) RETURNING *;

-- name: ListCollectorsBySourceID :many
SELECT * FROM discovery_source_collectors
WHERE source_id = $1
ORDER BY created_at ASC, id ASC;

-- name: ListAllDiscoverySourceCollectors :many
SELECT * FROM discovery_source_collectors
ORDER BY created_at ASC, id ASC;

-- name: DeleteCollectorsBySourceID :execrows
DELETE FROM discovery_source_collectors WHERE source_id = $1;

-- name: CreateCollectorRun :one
INSERT INTO discovery_collector_runs (
    id, source_id, collector_type, status, devices_found, duration_ms, error_message, started_at, finished_at
) VALUES (
    $1, $2, $3, $4, $5, $6, $7, $8, $9
) RETURNING *;

-- name: ListCollectorRunsBySource :many
SELECT * FROM discovery_collector_runs
WHERE source_id = $1
ORDER BY started_at DESC, id DESC
LIMIT $2 OFFSET $3;

-- name: ListCollectorRunsBySourcePaged :many
SELECT id, source_id, collector_type, status, devices_found, duration_ms, error_message, started_at, finished_at
FROM discovery_collector_runs
WHERE source_id = $1
ORDER BY started_at DESC, id DESC
LIMIT $2 OFFSET $3;

-- name: PurgeOldCollectorRunsChunk :execrows
DELETE FROM discovery_collector_runs
WHERE id IN (
    SELECT sub.id FROM discovery_collector_runs sub
    WHERE sub.finished_at < $1
    ORDER BY sub.finished_at ASC, sub.id ASC
    LIMIT $2
);

