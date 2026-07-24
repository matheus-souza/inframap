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
