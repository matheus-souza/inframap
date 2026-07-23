# RFC-008 — API Specification & HTTP Communication Protocol

| Status | Accepted |
|----------|----------|
| Owner | InfraMap Team |
| Created | 2026 |
| Last Updated | 2026 |

---

# Overview

This document defines the official **RESTful API Specification**, HTTP response envelope standards, error handling formats, pagination policies, and **Real-time Event Streaming (Server-Sent Events)** protocol for InfraMap.

The API is built following the **API First** philosophy established in [project-foundation.md](./project-foundation.md). All backend business capabilities defined in [RFC-005](./RFC-005-architecture.md) are exposed as REST endpoints and SSE event streams, serving as the sole contract consumed by the Compose Multiplatform WebAssembly frontend and third-party integrations.

---

# Guiding Principles

1. **Strict HTTP Semantics**
   - Rely on native HTTP status codes (2xx, 4xx, 5xx) to communicate success or failure without redundant boolean flags in the payload body.

2. **Unified Envelope with Tracing (`request_id`)**
   - All responses include a `meta.request_id` (UUIDv7) to enable end-to-end correlation between frontend error dialogs and backend structured logs.

3. **Decoupled Real-time Event Architecture**
   - Real-time updates rely on **Server-Sent Events (SSE)**.
   - Business modules emit domain events to the **Internal Event Bus**, which forwards them to a dedicated **Realtime Gateway** without coupling core capabilities to SSE mechanics.

4. **Context-Aware Dual Pagination**
   - **Offset Pagination (`page`, `limit`)** for operational UI lists (`devices`, `subnets`, `users`).
   - **UUIDv7 Cursor Pagination (`starting_after`, `limit`)** for time-ordered streams (`audit_logs`, `discovery_records`, `activity_feed`).

---

# Response Envelope Specifications

## 1. Success Response Envelope

Success responses return HTTP status `200 OK`, `201 Created`, or `204 No Content`.

```json
{
  "data": {
    "id": "0198a123-4567-7890-abcd-ef1234567890",
    "hostname": "nas01.home.arpa",
    "ip_address": "192.168.1.10",
    "device_type": "storage",
    "status": "active"
  },
  "meta": {
    "request_id": "0198a999-8888-7777-6666-555544443333"
  }
}
```

### List Response with Offset Pagination

```json
{
  "data": [
    { "id": "0198a123...", "hostname": "pve01" },
    { "id": "0198a124...", "hostname": "pve02" }
  ],
  "meta": {
    "request_id": "0198a999...",
    "page": 1,
    "per_page": 50,
    "total_records": 120,
    "total_pages": 3
  }
}
```

### List Response with UUIDv7 Cursor Pagination

```json
{
  "data": [
    { "id": "0198a500...", "action": "device.create", "created_at": "2026-07-22T20:00:00Z" }
  ],
  "meta": {
    "request_id": "0198a999...",
    "has_more": true,
    "next_cursor": "0198a500...",
    "limit": 100
  }
}
```

---

## 2. Error Response Envelope

Error responses return appropriate 4xx or 5xx HTTP status codes. The `success` boolean is omitted in favor of explicit HTTP codes and an `error` block.

```json
{
  "error": {
    "code": "VALIDATION_FAILED",
    "message": "Invalid request payload attributes",
    "details": [
      {
        "field": "ip_address",
        "issue": "must be a valid IPv4 or IPv6 address"
      }
    ]
  },
  "meta": {
    "request_id": "0198a999-8888-7777-6666-555544443333"
  }
}
```

### Standard Error Code Mappings

| HTTP Status | Error Code | Description |
| :---: | :--- | :--- |
| **400** | `BAD_REQUEST` | Malformed JSON or syntax error |
| **400** | `VALIDATION_FAILED` | Field validation failed |
| **401** | `UNAUTHENTICATED` | Missing or invalid authentication token |
| **403** | `FORBIDDEN` | Insufficient RBAC permissions |
| **404** | `NOT_FOUND` | Resource does not exist |
| **409** | `CONFLICT` | Resource collision (e.g. duplicate username/MAC) |
| **422** | `UNPROCESSABLE_ENTITY` | Business rule violation |
| **429** | `RATE_LIMITED` | Request quota exceeded |
| **500** | `INTERNAL_ERROR` | Unexpected server error |

---

# REST API Route Registry

All endpoints are prefixed with `/api/v1`.

### 1. Onboarding & System Setup (`configuration`)
- `GET  /api/v1/setup/status` — Checks if onboarding is completed (`system_state`).
- `POST /api/v1/setup/onboard` — Executes first-time setup & admin user creation.

### 2. Authentication & Identity (`identity`)
- `POST /api/v1/auth/login` — Authenticates user credentials (returns session token / cookie).
- `POST /api/v1/auth/logout` — Terminates active session.
- `GET  /api/v1/auth/me` — Returns authenticated user profile and assigned RBAC permissions.

### 3. Inventory & Assets (`inventory`)
- `GET    /api/v1/devices` — List active devices (supports offset pagination, search & filters).
- `POST   /api/v1/devices` — Manually register a new device.
- `GET    /api/v1/devices/:id` — Retrieve device details by ID.
- `PUT    /api/v1/devices/:id` — Update device details (sets user lock on edited fields).
- `DELETE /api/v1/devices/:id` — Soft-delete device (`deleted_at`).

### 4. Staging Queue (`inventory`)
- `GET  /api/v1/devices/staging` — List newly discovered unverified devices.
- `POST /api/v1/devices/staging/:id/approve` — Approve staged device into active inventory.
- `POST /api/v1/devices/staging/:id/dismiss` — Dismiss/reject staged device.

### 5. Topology & Subnets (`topology`)
- `GET /api/v1/subnets` — List configured subnets and VLANs.
- `POST /api/v1/subnets` — Add new subnet scope.
- `GET /api/v1/topology/graph` — Retrieve complete node/edge graph data for visualization.
- `GET /api/v1/topology/links` — List topology connection links.

### 6. Discovery Engine (`discovery`)
- `GET  /api/v1/discovery/sources` — List configured scanners/providers.
- `POST /api/v1/discovery/sources` — Add new discovery source.
- `POST /api/v1/discovery/sources/:id/run` — Manually trigger scan for a source.

### 7. Audit & Activity Logs (`audit`)
- `GET /api/v1/audit/logs` — List audit events (Cursor pagination with UUIDv7).

---

# Real-time Event Streaming (Server-Sent Events)

InfraMap adopts **Server-Sent Events (SSE)** for streaming real-time notifications, scan progress, and topology updates to the browser.

### Architecture Decoupling

```text
  [ Capability / Use Case ]
             │
             ▼
  [ Internal Event Bus ] ──(Domain Events: DeviceCreated, DiscoveryFinished)
             │
             ▼
   [ Realtime Gateway ]
             │
             ▼
    [ SSE Channel ] ──(HTTP Streaming)──► [ Frontend / WebAssembly ]
```

### SSE Stream Endpoint
- `GET /api/v1/events/stream` — Establishes SSE stream connection.

### SSE Message Payload Format

Every SSE event follows the standard format: `event: <type>\ndata: <json>\n\n`.

#### 1. Scan Progress Event (`discovery.progress`)
```text
event: discovery.progress
data: {"source_id":"0198a...","source_name":"mDNS Scanner","scanned_items":45,"total_items":100,"progress_percent":45.0}
```

#### 2. Topology Change Event (`topology.updated`)
```text
event: topology.updated
data: {"action":"link_added","source_device_id":"0198a1...","target_device_id":"0198a2...","link_type":"physical_cable"}
```

#### 3. System Notification Event (`system.notification`)
```text
event: system.notification
data: {"level":"warning","message":"Integration Proxmox node-01 connection timed out"}
```

### SSE Reconnection & Event Recovery (`Last-Event-ID`)
- Every SSE message emitted includes an `id:` field containing a time-ordered UUIDv7 event identifier.
- If the SSE connection drops due to network fluctuation, the browser automatically sends a `Last-Event-ID: <uuidv7>` header upon reconnecting.
- The Realtime Gateway inspects `Last-Event-ID` and replays any missed events generated during the disconnection window.

---

# Authentication & Token Transport

InfraMap supports dual authentication transport mechanisms:

1. **Web Browser UI (Compose WASM)**: Uses an **`HttpOnly`, `SameSite=Lax`, `Secure` Cookie** named `inframap_session`.
   - Protects tokens from client-side XSS access.
   - Automatically attached by the browser on cross-origin/same-origin REST and SSE requests.

2. **Programmatic Clients & CLI/Scripts**: Uses standard HTTP header:
   - `Authorization: Bearer <session_token>`

### Token Format

Tokens are strictly **Opaque Stateful Tokens**. They are cryptographically secure random strings stored in the database (`user_sessions.token_hash`) for immediate revocation capabilities. 

To improve debuggability and identification, tokens must use a modern prefixed format:
- Example: `ims_4pE8x9TqW...` (where `ims_` stands for **I**nfra**M**ap **S**ession)
- Format: `prefix` + `_` + `crypto_random_string`

---

# Security, CORS & Rate Limiting

1. **CORS Policy**
   - Configurable allowed origins via environment variable `INFRAMAP_CORS_ALLOWED_ORIGINS`.
   - Production deployments restrict origins to the homelab reverse proxy domain.

2. **Security Headers**
   - `Content-Security-Policy`: Default strict policy.
   - `X-Frame-Options`: `DENY`
   - `X-Content-Type-Options`: `nosniff`
   - `Referrer-Policy`: `strict-origin-when-cross-origin`

3. **Rate Limiting Policy**
   - Standard API endpoints: **120 requests / minute** per IP.
   - Authentication endpoint (`/auth/login`): **10 requests / minute** per IP to prevent brute-force attacks.

