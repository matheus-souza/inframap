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
    metadata
FROM devices
WHERE status != 'deleted'
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
WHERE source_device_id IN (SELECT id FROM devices WHERE status != 'deleted')
  AND target_device_id IN (SELECT id FROM devices WHERE status != 'deleted')
ORDER BY id
LIMIT sqlc.arg('limit') OFFSET sqlc.arg('offset');
