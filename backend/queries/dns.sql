-- name: CreateDNSRecord :one
INSERT INTO dns_records (
    id, name, record_type, address, target, device_id, notes
) VALUES (
    $1, $2, $3, $4, $5, $6, $7
) RETURNING *;

-- name: GetDNSRecordByID :one
SELECT * FROM dns_records
WHERE id = $1;

-- name: ListDNSRecords :many
SELECT * FROM dns_records
ORDER BY name ASC, record_type ASC
LIMIT $1 OFFSET $2;

-- name: CountDNSRecords :one
SELECT COUNT(*) FROM dns_records;

-- name: UpdateDNSRecord :one
UPDATE dns_records
SET name = $2,
    record_type = $3,
    address = $4,
    target = $5,
    device_id = $6,
    notes = $7,
    updated_at = NOW()
WHERE id = $1
RETURNING *;

-- name: DeleteDNSRecord :exec
DELETE FROM dns_records
WHERE id = $1;

-- name: ListDNSRecordsByDevice :many
SELECT * FROM dns_records
WHERE device_id = $1
ORDER BY name ASC, record_type ASC;
