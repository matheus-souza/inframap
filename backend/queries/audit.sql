-- name: CreateAuditLog :one
INSERT INTO audit_logs (
    id,
    actor_id,
    actor_name,
    action,
    resource_type,
    resource_id,
    changes,
    ip_address,
    created_at
) VALUES (
    $1, $2, $3, $4, $5, $6, $7, $8, $9
)
RETURNING *;
