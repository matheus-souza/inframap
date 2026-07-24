# RFC-012 — System Configuration & Onboarding Specification

| Status | Accepted |
|--------|----------|
| Owner | InfraMap Team |
| Created | 2026 |
| Last Updated | 2026 |

---

# Problem Statement

InfraMap requires a deterministic, single-shot system initialization mechanism to transition a freshly deployed instance into an operational state. Without a formal configuration capability, system setup cannot create the initial administrative account, seed default RBAC roles, or establish system instance telemetry parameters in a secure and reproducible manner.

---

# User Stories

1. **As a System Administrator**, I want to check `GET /api/v1/setup/status` so that I can determine if the InfraMap instance requires initial onboarding.
2. **As a System Administrator**, I want to submit `POST /api/v1/setup/onboard` with my initial admin credentials, full name, and telemetry preferences so that the system initializes itself in a single operation.
3. **As a Developer**, I want onboarding to seed standard RBAC roles (`admin`, `operator`, `viewer`) and prevent subsequent re-onboarding attempts with `409 CONFLICT`.

---

# Implementation Decisions

### 1. Architectural Alignment
- Follows Modular Monolith capability layout (`modules/configuration/`) per RFC-005.
- Controller → UseCase → Repository → Database architecture.
- Emits `system.onboarded` domain event to `internal/platform/eventbus`.

### 2. Password Strength Policy
- Minimum length: **12 characters**.
- Strength evaluation: `zxcvbn` score $\ge 3$.
- Passphrases allowed (no mandatory symbol/uppercase complexity rules to encourage long, memorable passphrases).
- Password hashing: **bcrypt** (cost 12).

### 3. Data Model & Seeding Rules
- **`system_state`**: Singleton record. If missing at bootstrap, auto-seeded with `onboarding_completed = false` and generated `system_instance_id`.
- **`system_state.metadata`**: Strictly restricted to technical installation parameters (`installed_version`, `installed_at`). Business/device metadata belongs to `devices.metadata`.
- **Standard Roles Seeded**:
  - `admin`: Full system access
  - `operator`: Read/write access to inventory and discovery
  - `viewer`: Read-only access to topology and inventory

---

# Testing Decisions & Identified Seams

1. **Platform Seams (`httputil`)**: Verify HTTP response envelopes (`data`, `meta.request_id`, `error.code`) and security headers.
2. **Repository Seam (`SetupRepository`)**: Test SQL transactions for atomic user creation, role seeding, and state updates using mock DBTX.
3. **UseCase Seam (`SetupUseCase`)**: Test password strength validation (`zxcvbn`), one-shot enforcement (subsequent calls return `ErrAlreadyOnboarded`), and event publishing.
4. **HTTP Controller Seam (`SetupController`)**: Test `GET /setup/status` and `POST /setup/onboard` status codes (200, 201, 400, 409).
5. **Integration Seam**: End-to-end HTTP request triggering full bootstrap, database insertion, and audit log event capture.

---

# Out of Scope

- Session authentication management (`POST /auth/login` belongs to `identity` module in RFC-008).
- User profile updates or password resets.
