-- name: CreateTopologyLink :one
INSERT INTO topology_links (
    id,
    source_device_id,
    target_device_id,
    source_interface_id,
    target_interface_id,
    link_type,
    confidence_score,
    discovered_by,
    metadata,
    created_at,
    updated_at
) VALUES (
    $1, $2, $3, $4, $5, $6, $7, $8, $9, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
)
RETURNING *;

-- name: GetTopologyLinkByID :one
SELECT * FROM topology_links
WHERE id = $1 LIMIT 1;

-- name: ListTopologyLinks :many
SELECT * FROM topology_links
WHERE 
    (sqlc.narg('link_type')::varchar IS NULL OR link_type = sqlc.narg('link_type'))
    AND (sqlc.narg('source_device_id')::uuid IS NULL OR source_device_id = sqlc.narg('source_device_id'))
    AND (sqlc.narg('target_device_id')::uuid IS NULL OR target_device_id = sqlc.narg('target_device_id'))
ORDER BY created_at DESC
LIMIT sqlc.arg('limit') OFFSET sqlc.arg('offset');

-- name: DeleteTopologyLink :execrows
DELETE FROM topology_links
WHERE id = $1;

-- name: ListActiveDevicesForGraph :many
SELECT 
    id,
    hostname,
    ip_address,
    mac_address,
    device_type,
    status,
    metadata,
    parent_device_id
FROM devices
WHERE deleted_at IS NULL AND status != 'deleted'
ORDER BY id
LIMIT sqlc.arg('limit') OFFSET sqlc.arg('offset');

-- name: ListActiveTopologyLinksForGraph :many
SELECT 
    id,
    source_device_id,
    target_device_id,
    source_interface_id,
    target_interface_id,
    link_type,
    confidence_score,
    discovered_by,
    metadata
FROM topology_links
WHERE source_device_id IN (SELECT id FROM devices WHERE deleted_at IS NULL AND status != 'deleted')
  AND target_device_id IN (SELECT id FROM devices WHERE deleted_at IS NULL AND status != 'deleted')
ORDER BY id
LIMIT sqlc.arg('limit') OFFSET sqlc.arg('offset');

-- name: DeleteContainmentLinksForChild :execrows
-- Removes the containment edges anchoring a workload to a host. Used when a workload
-- migrates: the old edge must disappear in the same transaction that creates the new one,
-- or the graph would briefly show the workload living on two hosts at once.
DELETE FROM topology_links
WHERE target_device_id = $1
  AND link_type = $2
  AND source_device_id <> $3;

-- name: UpsertContainmentLink :one
-- Idempotent containment edge. Repeated discovery runs re-report the same parentage, and
-- uq_topology_links_source_target_type turns the retry into a no-op update.
INSERT INTO topology_links (
    id, source_device_id, target_device_id, link_type, confidence_score, discovered_by, metadata
) VALUES (
    $1, $2, $3, $4, $5, $6, $7
)
ON CONFLICT (source_device_id, target_device_id, link_type)
DO UPDATE SET metadata = EXCLUDED.metadata,
              confidence_score = EXCLUDED.confidence_score,
              discovered_by = EXCLUDED.discovered_by,
              updated_at = CURRENT_TIMESTAMP
RETURNING *;
