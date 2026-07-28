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
