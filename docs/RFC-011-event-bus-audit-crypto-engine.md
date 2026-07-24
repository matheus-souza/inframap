# RFC-011 — In-Memory Event Bus, Audit Logger & Secret Encryption Engine

| Status | Accepted |
|----------|----------|
| Owner | InfraMap Team |
| Created | 2026-07-24 |
| Last Updated | 2026-07-24 |

---

# Overview

This document specifies the technical design, package contracts, and implementation requirements for:
1. **In-Memory Event Bus (`backend/internal/platform/eventbus`)**: Asynchronous, channel-based domain event publisher/subscriber engine.
2. **Secret Encryption Package (`backend/internal/platform/crypto`)**: AES-256-GCM authenticated encryption for credentials and integration secrets.
3. **Audit Log Subscriber Module (`backend/modules/audit`)**: Asynchronous subscriber that persists published domain events into the PostgreSQL `audit_logs` table (RFC-006).

---

# 1. Secret Encryption Engine (`internal/platform/crypto`)

### Requirements
- **Algorithm**: AES-256-GCM (Galois/Counter Mode) with 12-byte random nonce per payload.
- **Key Source**: 32-byte master key derived from environment variable `INFRAMAP_SECRET_KEY` (or generated fallback for dev).
- **Output Format**: Base64 encoded payload: `v1:<base64(nonce + ciphertext + auth_tag)>`.

### Package Interface
```go
package crypto

type Encryptor interface {
    Encrypt(plaintext []byte) (string, error)
    Decrypt(ciphertext string) ([]byte, error)
}
```

---

# 2. In-Memory Event Bus (`internal/platform/eventbus`)

### Requirements
- **Channel Buffer**: 1000-event buffered channel.
- **Worker Pool**: Configurable worker pool (default: 5 goroutines) for parallel subscriber handling.
- **Payload Semantics**: Pass typed Go structs by value (no shared pointers, defensive copies of slices/maps).
- **Fault Isolation**: Panic recovery (`recover()`) per subscriber handler execution to prevent worker crashes.
- **Backpressure Protection**: Non-blocking channel send; logs backpressure warning if buffer overflows.

### Core Contracts
```go
package eventbus

import (
    "context"
    "time"
)

type DomainEvent interface {
    EventID() string       // UUIDv7
    EventType() string     // e.g. "device.created", "system.configured"
    OccurredAt() time.Time
    Payload() any
}

type EventHandler func(ctx context.Context, event DomainEvent) error

type EventBus interface {
    Publish(ctx context.Context, event DomainEvent) error
    Subscribe(eventType string, handler EventHandler) error
    Close() error
}
```

---

# 3. Audit Log Subscriber Module (`modules/audit`)

### Requirements
- **Subscriber**: Listens to all wildcard domain events (`*`) or specific event types.
- **Persistence**: Writes an audit log record into `audit_logs` table using `sqlc` generated queries.
- **Record Mapping**:
  - `id`: `event.EventID()` (UUIDv7)
  - `event_type`: `event.EventType()`
  - `payload`: JSONB serialized `event.Payload()`
  - `created_at`: `event.OccurredAt()`

---

# Implementation Seams & Verification Criteria

1. **Seam 1 (`crypto` test)**: Unit tests for `Encrypt()` and `Decrypt()`, verifying invalid ciphertext detection and wrong key rejection.
2. **Seam 2 (`eventbus` test)**: Concurrency tests for `Publish()`, `Subscribe()`, worker pool dispatch, backpressure, and panic recovery.
3. **Seam 3 (`audit` test)**: Integration test asserting published `DomainEvent` is asynchronously stored in PostgreSQL `audit_logs`.
