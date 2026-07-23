# RFC-004 — Security Policy

| Status | Accepted |
|----------|----------|
| Owner | InfraMap Team |
| Created | 2026 |
| Last Updated | 2026 |

---

# Overview

This document defines the security principles, architectural guidelines and mandatory security requirements adopted by InfraMap.

Security is considered a first-class concern of the project and is incorporated throughout the entire Software Development Life Cycle (Secure SDLC), rather than being treated as a post-development activity.

The objective of this document is to establish a consistent security baseline that ensures the confidentiality, integrity, availability and auditability of the platform while maintaining simplicity for self-hosted and homelab environments.

---

# Security Principles

InfraMap adopts the following security principles:

- Security by Design
- Secure by Default
- Defense in Depth
- Least Privilege
- Secure SDLC
- Fail Secure
- Principle of Explicit Trust
- Automation First

Every new feature must be designed considering security requirements from its conception.

---

# Security Priorities

InfraMap prioritizes security objectives in the following order:

1. Availability
2. Integrity
3. Ease of Use
4. Auditability
5. Privacy
6. Confidentiality

The system is intended for infrastructure management where availability and operational reliability are essential.

---

# Trust Model

InfraMap adopts a partial Zero Trust philosophy.

Devices present in the managed infrastructure may provide information used by the discovery engine.

However, discovered information is never considered the source of truth.

The InfraMap database always represents the authoritative inventory.

All discovered information should be validated, normalized and reconciled before being persisted.

---

# Secure Software Development Lifecycle

Security is integrated throughout every development phase.

The project adopts Secure SDLC practices including:

- Threat-aware architecture decisions
- Static analysis
- Dependency validation
- Secret detection
- Security-focused code review
- Continuous vulnerability scanning
- Automated security validation in CI

Security validation is mandatory before every merge.

---

# Authentication

The first stable version adopts local authentication based on username and password.

The authentication architecture shall remain modular, allowing future support for additional providers without architectural changes.

Future supported providers include:

- OAuth2
- OpenID Connect (OIDC)
- LDAP
- Authentik
- Authelia
- Keycloak
- Reverse Proxy Authentication
- Additional identity providers

Authentication providers should be pluggable.

---

# Multi-Factor Authentication

Multi-Factor Authentication is optional.

When authentication is delegated to an external Identity Provider, InfraMap should rely on the provider's MFA implementation whenever available.

Local authentication may support MFA in future versions.

---

# Session Management

InfraMap supports traditional session-based authentication.

The authentication architecture should also allow future evolution toward token-based authentication, including:

- Access Tokens
- Refresh Tokens

Current implementations should not prevent this future evolution.

---

# Authorization

InfraMap adopts Role-Based Access Control (RBAC).

Authorization should be structured around:

- Roles
- Permissions

Even though the initial version is simple, authorization should be implemented using a scalable permission model.

---

# Initial Roles

The initial authorization model should support:

- Administrator
- Operator
- Viewer
- Read Only

Additional roles may be introduced without changing the authorization architecture.

---

# Password Security

Passwords must never be stored in plain text.

The project should adopt the strongest password hashing algorithm broadly recommended by the security community.

Current recommendation:

- Argon2id

Password policies may evolve over time.

---

# Secret Management

Sensitive information must never be hardcoded.

Secrets may be provided through:

- Environment Variables
- Docker Secrets
- Secret Management Systems
- Encrypted Configuration Files

The architecture should support multiple secret providers.

---

# Encryption at Rest

Sensitive credentials stored by InfraMap should be encrypted before persistence.

Examples include:

- API Tokens
- Integration Credentials
- Authentication Secrets
- Encryption Keys

The encryption key must remain external to the database.

---

# Secret Rotation

Automatic secret rotation is not required in the initial release.

The architecture should support future implementation without requiring structural changes.

---

# Communication Security

HTTPS is the preferred communication protocol.

HTTP may be allowed only for:

- Local development
- Explicitly configured environments

Production deployments should always prioritize HTTPS.

---

# Reverse Proxy Support

InfraMap is designed to operate behind reverse proxies.

Supported reverse proxies include:

- Caddy
- Nginx Proxy Manager
- Traefik
- HAProxy
- Nginx

The application should correctly handle forwarded headers and trusted proxy configuration.

Certificate management remains the responsibility of the reverse proxy.

---

# TLS Policy

Production environments should adopt modern TLS configurations.

Recommended policy:

- TLS 1.3 preferred
- TLS 1.2 supported for compatibility
- TLS 1.0 prohibited
- TLS 1.1 prohibited

Weak ciphers should not be enabled.

---

# Internal Certificates

InfraMap should support environments using:

- Let's Encrypt
- Internal Certificate Authorities
- Self-signed Certificates
- Enterprise PKI

The project should not require public certificates.

---

# API Security

The platform should implement the following protections by default:

- Rate Limiting
- Request Size Limits
- Request Timeouts
- Configurable CORS
- Content Security Policy (CSP)
- Security Headers
- HTTP Strict Transport Security (HSTS)
- CSRF Protection
- XSS Protection
- Clickjacking Protection

Security defaults should follow current OWASP recommendations whenever applicable.

---

# Abuse Protection

The application should include protection against:

- Brute Force
- Enumeration
- Flooding
- Replay Attacks

Detection and mitigation strategies may evolve over time.

---

# Database Security

Database communication should use encrypted connections whenever technically possible.

The application must never require administrative database credentials during normal operation.

A dedicated application user with minimum required privileges should be used.

---

# Initial Administrator

During the first startup, InfraMap should execute an onboarding process.

The onboarding is responsible for creating the initial administrator account.

Once completed, onboarding must be permanently disabled until explicitly reset.

---

# Audit Policy

InfraMap considers auditability a core feature.

The following events must generate audit records:

- Login
- Logout
- Failed Login
- Password Change
- User Modification
- User Removal
- Configuration Changes
- Version Updates
- Internal Errors
- Authentication Failures
- Authorization Failures
- Attack Detection

Future versions may support dedicated audit storage.

---

# Sensitive Information

Sensitive information must never appear in logs.

Examples include:

- Passwords
- Tokens
- Authorization Headers
- Cookies
- API Keys
- Secrets

Sensitive values should always be masked.

---

# Container Security

Official container images should adopt modern security practices.

Recommended requirements include:

- Non-root execution
- Health Checks
- Multi-stage builds
- Minimal base images
- Rootless containers whenever practical
- Read-only file systems whenever practical

Security should not unnecessarily compromise maintainability.

---

# Docker Security

Official Docker Compose configurations should avoid:

- Privileged containers
- Host PID namespace
- Host IPC namespace
- Host networking

Exceptions should require explicit justification.

---

# Dependency Policy

InfraMap should continuously adopt the latest stable versions of its dependencies.

Abandoned dependencies should be replaced whenever practical.

Dependency health is considered part of project security.

---

# Vulnerability Management

InfraMap adopts the following policy:

| Severity | Merge |
|----------|-------|
| Critical | Blocked |
| High | Blocked |
| Medium | Blocked |
| Low | Warning |

Security validation is mandatory before merge.

---

# Backup Policy

InfraMap should support:

- Automated Backups
- Database Restore
- Configuration Export
- Database Export

Backup functionality should operate without external services whenever possible.

---

# Privacy

Telemetry should always be optional.

Crash reporting should always be optional.

Users must retain full control over data sharing.

---

# Managed Asset Information

InfraMap may collect infrastructure metadata including:

- Hostname
- IP Address
- MAC Address
- Manufacturer
- Operating System
- Running Services

The collection of each data category should be configurable.

The platform should also support custom inventory attributes such as:

- Asset ID
- Internal Inventory Number
- Custom Labels
- Custom Metadata

---

# Updates

Application updates remain under user control.

InfraMap should never update itself automatically.

Users explicitly initiate updates through their preferred deployment workflow.

---

# Security Benchmarks

Security decisions should align with recognized industry standards whenever practical.

Primary references include:

- OWASP ASVS
- OWASP Top 10
- CIS Benchmarks
- Docker Bench
- NIST Secure Software Development Framework

These references provide guidance rather than mandatory implementation requirements.

---

# Homelab Requirements

InfraMap is designed primarily for self-hosted environments.

The platform should:

- Operate completely offline
- Require minimal external dependencies
- Function without Internet access
- Support internal DNS
- Support `.home.arpa`
- Support `.lan`
- Support `.local`
- Support reverse proxies
- Support self-hosted certificate infrastructures

Future versions should allow delegated authentication while preserving local authentication as a fallback whenever appropriate.

---

# Future Security Evolution

The project is intentionally designed for incremental security evolution.

Advanced capabilities such as:

- Image Signing
- SBOM
- Supply Chain Security
- SLSA Provenance
- Automated Secret Rotation
- Delegated Authentication
- Advanced MFA

may be incorporated in future releases without requiring significant architectural redesign.

---

# Final Principle

Security is not a feature.

It is a continuous engineering discipline integrated into every aspect of InfraMap.

Every architectural decision should balance security, usability, maintainability and operational simplicity while preserving the project's primary objective of being a secure, lightweight and self-hosted infrastructure management platform.