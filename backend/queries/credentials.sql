-- name: CreateCredential :one
INSERT INTO credentials (
    id,
    name,
    type,
    encrypted_data,
    description,
    created_at,
    updated_at
) VALUES (
    $1, $2, $3, $4, $5, $6, $7
)
RETURNING *;

-- name: GetCredentialByID :one
SELECT id, name, type, encrypted_data, description, created_at, updated_at
FROM credentials
WHERE id = $1;

-- name: GetCredentialByIDForUpdate :one
SELECT id, name, type, encrypted_data, description, created_at, updated_at
FROM credentials
WHERE id = $1
FOR UPDATE;

-- name: ListCredentials :many
SELECT id, name, type, description, created_at, updated_at
FROM credentials
ORDER BY created_at DESC, id DESC
LIMIT $1 OFFSET $2;

-- name: CountCredentials :one
SELECT COUNT(*) FROM credentials;

-- name: DeleteCredential :execrows
DELETE FROM credentials
WHERE id = $1;

-- name: ListActiveDiscoverySourcesWithConfig :many
SELECT id, name, config_encrypted
FROM discovery_sources
WHERE deleted_at IS NULL AND config_encrypted IS NOT NULL;

-- name: ListActiveDiscoveryCollectorsWithConfig :many
SELECT dsc.source_id, ds.name AS source_name, dsc.config_encrypted
FROM discovery_source_collectors dsc
JOIN discovery_sources ds ON ds.id = dsc.source_id
WHERE ds.deleted_at IS NULL AND dsc.config_encrypted IS NOT NULL;

