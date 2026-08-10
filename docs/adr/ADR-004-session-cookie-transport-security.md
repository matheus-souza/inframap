# ADR-004: Session Cookie Secure Flag Derived from Request Transport

| ID | Date | Context / Decision | Rationale | Impact | Status |
| --- | --- | --- | --- | --- | --- |
| AD-027 | 2026-08-10 | Derive `Secure` flag from `r.TLS` and `X-Forwarded-Proto` instead of hardcoding | InfraMap is a self-hosted tool deployed on homelab HTTP, production HTTPS, and reverse-proxied setups. A hardcoded `Secure: true` causes browsers to silently reject cookies over plain HTTP, breaking authentication entirely. | Session cookie `Secure` flag adapts to actual transport, supporting all deployment topologies | Active |
| AD-028 | 2026-08-10 | Trust `X-Forwarded-Proto` without explicit trusted-proxy allowlist | Standard reverse proxies (Caddy, Nginx, Traefik, AWS ALB) overwrite `X-Forwarded-Proto` by default, stripping client-injected values. A client forging `X-Forwarded-Proto: https` on plain HTTP causes self-denial (browser rejects the Secure cookie), not a security vulnerability. Adding a trusted-proxy allowlist is over-engineering for a self-hosted tool with no multi-tenant threat model. | Simpler configuration; consistent with Go stdlib `httputil.ReverseProxy`, Chi, Gin, and Echo behavior | Active |

## Context

InfraMap runs in three deployment topologies:

1. **Plain HTTP** (homelab, development): `r.TLS == nil`, no proxy headers → `Secure: false`
2. **Direct HTTPS**: `r.TLS != nil` → `Secure: true` (short-circuit, header ignored)
3. **Reverse proxy with TLS termination**: `r.TLS == nil`, proxy sets `X-Forwarded-Proto: https` → `Secure: true`

The original implementation hardcoded `Secure: true`, which broke topology #1 completely — browsers silently reject `Secure` cookies on non-HTTPS origins.

## Threat Analysis for Header Trust

**Scenario: Client forges `X-Forwarded-Proto: https` on plain HTTP**
- Result: Cookie gets `Secure: true` → browser refuses to store it → authentication fails
- Impact: Self-denial-of-service. The attacker can only break their own session.
- Severity: None (not exploitable against other users)

**Scenario: Client forges `X-Forwarded-Proto: http` behind HTTPS proxy**
- Properly configured proxies overwrite this header → forge has no effect
- If proxy is misconfigured and doesn't overwrite: `r.TLS` is `nil` (proxy terminates TLS), cookie gets `Secure: false`
- Impact: Cookie sent on HTTP between proxy and backend (internal network, same host in homelab)
- Severity: Operator misconfiguration, not application vulnerability

**Scenario: Direct HTTPS with any `X-Forwarded-Proto` value**
- `r.TLS != nil` → returns `true` immediately (Go `||` short-circuit)
- Impact: None — header is never evaluated

## Alternatives Considered

| Alternative | Rejected Because |
| --- | --- |
| Hardcode `Secure: true` | Breaks plain HTTP deployments (homelab, dev). Root cause of the bug this ADR addresses |
| Trusted-proxy IP allowlist | Over-engineering for self-hosted single-tenant tool. Would require environment configuration that most operators won't set correctly |
| Environment variable `FORCE_HTTPS=true` | Adds deployment complexity. The transport already carries the information needed |
| `Forwarded` header (RFC 7239) | Low adoption by mainstream proxies. `X-Forwarded-Proto` is the de facto standard |

## Implementation

```go
func isSecureRequest(r *http.Request) bool {
    return r.TLS != nil || r.Header.Get("X-Forwarded-Proto") == "https"
}
```

Applied to both login and logout cookie emission sites with `// nosemgrep` suppression for Semgrep's `cookie-missing-secure` rule (static analysis cannot evaluate runtime function results).

## Test Coverage

| Scenario | Login | Logout |
| --- | --- | --- |
| Plain HTTP (no proxy) | `Secure: false` | `Secure: false` |
| HTTPS via `X-Forwarded-Proto` header | `Secure: true` | `Secure: true` |
| Direct TLS (`r.TLS != nil`) | `Secure: true` | `Secure: true` |
