-- name: CreateUser :one
INSERT INTO users (id, username, email, password_hash, full_name, is_active)
VALUES ($1, $2, $3, $4, $5, $6)
RETURNING *;

-- name: CreateRole :one
INSERT INTO roles (id, name, description, is_system)
VALUES ($1, $2, $3, $4)
RETURNING *;

-- name: GetRoleByName :one
SELECT * FROM roles WHERE name = $1;

-- name: AssignUserRole :exec
INSERT INTO user_roles (user_id, role_id)
VALUES ($1, $2);

-- name: CreateSession :one
INSERT INTO user_sessions (id, user_id, token_hash, user_agent, ip_address, expires_at)
VALUES ($1, $2, $3, $4, $5, $6)
RETURNING *;

-- name: GetSessionByHash :one
SELECT s.id, s.user_id, s.token_hash, s.user_agent, s.ip_address, s.expires_at, s.created_at,
       u.username, u.email, u.full_name, u.is_active
FROM user_sessions s
JOIN users u ON s.user_id = u.id
WHERE s.token_hash = $1;

-- name: UpdateSessionExpiry :exec
UPDATE user_sessions
SET expires_at = $2
WHERE id = $1;

-- name: DeleteSession :exec
DELETE FROM user_sessions
WHERE token_hash = $1;

-- name: DeleteUserSessions :exec
DELETE FROM user_sessions
WHERE user_id = $1;

-- name: GetUserPermissions :many
SELECT DISTINCT p.name
FROM user_roles ur
JOIN role_permissions rp ON ur.role_id = rp.role_id
JOIN permissions p ON rp.permission_id = p.id
WHERE ur.user_id = $1;
