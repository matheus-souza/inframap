# RFC-013 — Identity, Authentication & RBAC Engine Specification

| Status | Accepted |
|--------|----------|
| Owner | InfraMap Team |
| Created | 2026 |
| Last Updated | 2026 |

---

# Problem Statement

InfraMap requires a secure, stateful authentication and role-based access control (RBAC) engine to protect operational endpoints, manage user sessions, and enforce security policies across browser WASM clients and programmatic API consumers.

---

# User Stories

1. **As a User**, I want to submit my username and password to `POST /api/v1/auth/login` so that I can establish an authenticated session.
2. **As a Web Application (WASM)**, I want authentication credentials stored in a secure `HttpOnly`, `SameSite=Lax`, `Secure` cookie named `inframap_session` to protect against XSS token theft.
3. **As a Programmatic Client / CLI**, I want to pass my session token via `Authorization: Bearer ims_...` header to access protected endpoints.
4. **As an Authenticated User**, I want to query `GET /api/v1/auth/me` to inspect my active profile and assigned RBAC permissions.
5. **As an Authenticated User**, I want to submit `POST /api/v1/auth/logout` to immediately revoke my active session.
6. **As a Security Administrator**, I want brute-force attempts rate-limited and accounts temporarily locked (15 minutes after 5 failed attempts in 5 minutes) with full audit logging (`user.login_success`, `user.login_failed`, `user.account_locked`).

---

# Implementation Decisions

### 1. Token Format & Transport
- **Format**: Stateful Opaque Token with `ims_` prefix (`ims_<64_hex_chars>`).
- **Storage**: HMAC-SHA256 hashed token (`token_hash`) stored in `user_sessions` table.
- **Dual Transport**:
  - Web UI: `HttpOnly`, `SameSite=Lax`, `Path=/`, `Max-Age=604800` cookie (`inframap_session`).
  - API / CLI: `Authorization: Bearer ims_...` header.

### 2. Session Lifecycle & Sliding Expiration
- **Inactivity Timeout**: 30 minutes of inactivity triggers expiration.
- **Sliding Renewal**: Active requests extend `expires_at` by 30 minutes up to the hard maximum limit.
- **Hard Maximum Expiration**: 7 days (604,800 seconds) absolute maximum lifetime from session creation.

### 3. Password Verification & Brute-Force Defense
- **Password Check**: Verify against bcrypt `password_hash` stored in `users`.
- **Lockout Policy**: 5 failed login attempts for a username within 5 minutes triggers a 15-minute account lock.
- **Progressive Delay**: 100ms artificial delay penalty added on failed login responses to hinder timing attacks.

### 4. Middleware & Context Injection
- **`AuthMiddleware`**: Inspects `inframap_session` cookie first, falls back to `Authorization: Bearer` header.
- **Bypass Routes**: `/api/v1/health`, `/api/v1/setup/status`, `/api/v1/setup/onboard`, `/api/v1/auth/login`.
- **Context Injection**: Injects authenticated `User` and `Permissions` list into Go `context.Context` via `httputil.UserContextKey`.

---

# Identified Test Seams

1. **Token Repository Seam (`SessionRepository`)**: Unit test session creation, token hashing, lookup by hash, sliding renewal, and explicit revocation.
2. **Password Verification Seam**: Unit test bcrypt verification and progressive delay penalty.
3. **Identity UseCase Seam (`IdentityUseCase`)**: Unit test login, logout, profile resolution, lockout counters, and domain event publishing (`user.login_success`, `user.login_failed`, `user.account_locked`).
4. **HTTP Controller Seam (`IdentityController`)**: Unit test `POST /auth/login`, `POST /auth/logout`, `GET /auth/me` status codes and cookie headers.
5. **Auth Middleware Seam (`AuthMiddleware`)**: Unit test token extraction from cookie and Bearer header, 401 UNAUTHENTICATED handling, and public route bypassing.
6. **E2E Integration Seam**: End-to-end HTTP tests in `backend/tests/e2e/identity_e2e_test.go`.

---

# Out of Scope

- OAuth2 / OIDC SSO integration (deferred to future RFC).
- Multi-factor authentication (MFA / TOTP).
