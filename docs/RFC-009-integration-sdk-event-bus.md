# RFC-009 — Integration Provider SDK & Internal Event Bus

| Status | Accepted |
|----------|----------|
| Owner | InfraMap Team |
| Created | 2026 |
| Last Updated | 2026 |

---

# Overview

This document defines the official specification for the **Integration Provider SDK** and the **Internal Event Bus** of InfraMap.

To maintain the architectural decoupled boundaries specified in [RFC-005](./RFC-005-architecture.md), all external infrastructure integrations (e.g., Proxmox VE, Docker Engine, UniFi Controller, Mikrotik RouterOS, Home Assistant, SNMP) must be implemented as modular, independent providers adhering to a standardized Go SDK contract. Furthermore, all inter-module notifications and asynchronous side-effects are decoupled via an **In-Memory Go Event Bus**.

---

# Guiding Principles

1. **Pluggable Integration Architecture**
   - New discovery integrations can be added by implementing a single Go `Provider` interface without modifying core capabilities (`inventory`, `topology`, `identity`).

2. **Zero External Infrastructure Brokers**
   - The Event Bus relies strictly on **Go Channels & Worker Pools** in-memory, avoiding mandatory external message broker dependencies (e.g., Redis, RabbitMQ) to preserve homelab simplicity.

3. **AES-256-GCM Secret Encryption**
   - Integration credentials and API keys stored in `discovery_sources.config_encrypted` are encrypted using **AES-256-GCM** (Galois/Counter Mode) with hardware acceleration (`AES-NI` in Go).

4. **Fault Isolation & Resilience**
   - Each provider scan executes inside an isolated goroutine protected by a `panic` recovery handler (`recover()`). A failing integration can **never** crash the main application process.

---

# Integration Provider SDK

All discovery integrations must implement the `Provider` interface located in `backend/internal/platform/sdk/provider.go`.

```go
package sdk

import (
    "context"
    "time"
)

// ProviderMetadata defines human-readable details for UI rendering
type ProviderMetadata struct {
    ID          string   `json:"id"`          // e.g., "proxmox", "docker", "unifi"
    Name        string   `json:"name"`        // e.g., "Proxmox VE"
    Description string   `json:"description"` // e.g., "Discovers VMs and LXC containers via Proxmox REST API"
    Version     string   `json:"version"`     // e.g., "1.0.0"
    Icon        string   `json:"icon"`        // Icon identifier or SVG path
    Category    string   `json:"category"`    // hypervisor, container, network, iot
}

// ConfigField defines a dynamic configuration parameter required by the integration
type ConfigField struct {
    Key         string `json:"key"`         // e.g., "api_url", "token_id"
    Label       string `json:"label"`       // e.g., "Proxmox API URL"
    Type        string `json:"type"`        // text, password, number, boolean
    Required    bool   `json:"required"`
    Default     any    `json:"default,omitempty"`
    Description string `json:"description,omitempty"`
}

// ConfigSchema returns JSON Schema parameters for UI form generation
type ConfigSchema struct {
    Fields []ConfigField `json:"fields"`
}

// ProviderConfig represents decrypted runtime parameters for an execution
type ProviderConfig map[string]any

// Provider is the mandatory contract for all InfraMap integrations
type Provider interface {
    // ID returns the unique provider identifier (e.g., "proxmox")
    ID() string

    // Metadata returns information for UI representation
    Metadata() ProviderMetadata

    // ConfigSchema defines input parameters needed by the provider
    ConfigSchema() ConfigSchema

    // HealthCheck validates connectivity to the target service
    HealthCheck(ctx context.Context, config ProviderConfig) error

    // Discover executes scanning and returns normalized device DTOs
    Discover(ctx context.Context, config ProviderConfig) ([]NormalizedDevice, error)
}
```

---

# Secret Encryption (AES-256-GCM)

All sensitive integration configurations (passwords, tokens, private keys) are encrypted before persistence in PostgreSQL.

### Key Management & Encryption Flow
1. **Master Secret Key:** Derived from environment variable `INFRAMAP_SECRET_KEY` (32-byte key).
2. **Encryption Standard:** **AES-256-GCM** using a cryptographically secure 12-byte random Initialization Vector (IV/Nonce) per payload.
3. **Storage Format:** Stored as base64-encoded strings: `base64(nonce + ciphertext + auth_tag)`.

```sql
-- PostgreSQL table storing encrypted credentials
UPDATE discovery_sources 
SET config_encrypted = 'v1:a1b2c3...nonce...ciphertext' 
WHERE id = '...';
```

---

# Internal Event Bus Architecture

InfraMap adopts an **In-Memory Event Bus** to decouple capabilities without introducing external message queues.

```text
 [ Use Case / Module ]
          │
          ▼
   [ EventBus.Publish() ]
          │
          ▼
 [ Go Channel (Buffer: 1000) ]
          │
          ▼
  [ Worker Pool (N Workers) ] ──(Parallel Async Dispatch)
          │
    ┌─────┴───────────────┬──────────────────┐
    ▼                     ▼                  ▼
[ Topology Module ]  [ Audit Module ]  [ Realtime Gateway (SSE) ]
```

### Event Contracts

```go
package eventbus

import (
    "context"
    "time"
)

// DomainEvent represents an immutable domain state change
type DomainEvent interface {
    EventID() string      // UUIDv7
    EventType() string    // e.g., "device.created", "discovery.finished"
    OccurredAt() time.Time
    Payload() any         // Typed Go struct passed by value (defensive copy of slices/maps)
}

// EventHandler processes subscribed events
type EventHandler func(ctx context.Context, event DomainEvent) error

// EventBus handles event publishing and subscription
type EventBus interface {
    Publish(ctx context.Context, event DomainEvent) error
    Subscribe(eventType string, handler EventHandler) error
}
```

### Payload Semantics & Immutability

The internal Event Bus passes typed Go structs in memory. To prevent unintended shared-memory mutations between decoupled modules:
- Events must be published **by value**, avoiding the sharing of pointers.
- Mutable fields within payloads (such as slices or maps) must be defensively copied before publication.
- **No internal serialization** (JSON, Protobuf) is used for the Event Bus. Serialization is strictly the responsibility of external transport layers (e.g., SSE Gateway) when events leave the application boundary.

### Standard Event Types

| Event Type | Emitted By | Description |
| :--- | :--- | :--- |
| `device.created` | `inventory` | A new device has passed reconciliation and entered active inventory |
| `device.updated` | `inventory` | Existing device properties or status updated |
| `device.deleted` | `inventory` | Device has been soft-deleted |
| `discovery.started` | `discovery` | A discovery scan cycle has initiated |
| `discovery.progress`| `discovery` | Intermediate scan progress update |
| `discovery.finished`| `discovery` | Discovery scan cycle completed successfully |
| `topology.updated` | `topology` | Topology graph edges updated |
| `integration.failed`| `integrations`| Integration provider encountered an error |

---

# Resilience, Circuit Breaker & Panic Isolation

### 1. Isolated Execution, Timeout & Panic Recovery
Every provider discovery task executes within a dedicated goroutine wrapped with explicit context timeouts and a panic recovery function:

```go
// Enforce a hard execution timeout per scan (default: 30 seconds)
scanCtx, cancel := context.WithTimeout(ctx, 30*time.Second)
defer cancel()

go func() {
    defer func() {
        if r := recover(); r != nil {
            logger.Error("panic recovered in integration provider", "provider", provider.ID(), "panic", r)
            eventBus.Publish(ctx, NewIntegrationFailedEvent(provider.ID(), r))
        }
    }()
    
    // Execute provider discovery with hard timeout
    devices, err := provider.Discover(scanCtx, config)
    ...
}()
```

### 2. Event Bus Backpressure & Overflow Protection
To prevent slow event subscribers from blocking Use Case execution loops:
- **Non-Blocking Dispatch:** Event publishing uses non-blocking channel sends.
- **Buffer Overflow Safeguard:** If the 1000-event channel buffer is full, the event bus logs a backpressure warning (`event bus buffer full`) and drops/spills lower-priority telemetry events rather than freezing business logic.

### 3. Retry Policy (Exponential Backoff)
When a provider encounters a transient network error during `HealthCheck` or `Discover`:
- **Attempt 1:** Immediate retry after **1 second**.
- **Attempt 2:** Retry after **2 seconds**.
- **Attempt 3:** Retry after **4 seconds**.
- If all 3 attempts fail, the execution logs an error, emits `integration.failed`, and marks source status as `failed`.

### 4. Circuit Breaker Pattern
To avoid hammering an offline server (e.g., a powered-down Proxmox node):
- **Closed State:** Normal operation.
- **Open State:** Triggered after **5 consecutive failed scan cycles**. Future automated scans are skipped for **15 minutes**.
- **Half-Open State:** After 15 minutes, a single `HealthCheck()` attempt is permitted. If successful, the breaker resets to `Closed`; if failed, it re-enters `Open` for another 15 minutes.

