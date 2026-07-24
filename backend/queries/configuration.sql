-- name: GetSystemState :one
SELECT * FROM system_state LIMIT 1;

-- name: CreateSystemState :one
INSERT INTO system_state (id, onboarding_completed, onboarding_completed_at, system_instance_id, telemetry_enabled, metadata)
VALUES ($1, $2, $3, $4, $5, $6)
RETURNING *;

-- name: UpdateSystemStateOnboarding :exec
UPDATE system_state
SET onboarding_completed = true, onboarding_completed_at = NOW(), updated_at = NOW()
WHERE id = $1;
