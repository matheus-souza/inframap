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
ORDER BY created_at DESC;

-- name: DeleteTopologyLink :exec
DELETE FROM topology_links
WHERE id = $1;

-- name: ListAllActiveNodesAndLinks :many
SELECT 
    d.id AS device_id,
    d.hostname,
    d.ip_address,
    d.mac_address,
    d.device_type,
    d.status,
    d.metadata AS device_metadata,
    tl.id AS link_id,
    tl.source_device_id,
    tl.target_device_id,
    tl.source_interface_id,
    tl.target_interface_id,
    tl.link_type,
    tl.confidence_score,
    tl.discovered_by,
    tl.metadata AS link_metadata
FROM devices d
LEFT JOIN topology_links tl ON (d.id = tl.source_device_id OR d.id = tl.target_device_id)
WHERE d.status != 'deleted';
