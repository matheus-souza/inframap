# InfraMap — Domain Context & Ubiquitous Language

> **Shared Domain Vocabulary**: This document establishes the ubiquitous language used across all code, tests, and documentation within the InfraMap codebase.

---

## Core Domain Terms

| Term | Definition | Code Representation |
|---|---|---|
| **InfraMap** | Open-source network infrastructure discovery, mapping, and observability platform. | Repository root (`github.com/matheussouza/inframap`) |
| **Node** | Physical, virtual, or cloud infrastructure entity (e.g., Server, Switch, Router, VM, Container). | `Table: nodes`, `struct Node` |
| **Interface** | Physical or logical network interface attached to a Node (e.g., eth0, vlan10). | `Table: interfaces`, `struct Interface` |
| **Link** | Point-to-point connection between two interfaces on separate nodes. | `Table: links`, `struct Link` |
| **Topology** | Graph representation of Nodes, Interfaces, and Links forming the network map. | Domain package: `internal/domain/topology` |
| **Discovery Engine** | Subsystem responsible for scanning IP ranges, executing plugins, and ingesting assets. | Domain package: `internal/domain/discovery` |
| **Discovery Source** | Configuration targeting a range, subnet, or provider API for automated discovery. | `Table: discovery_sources` |
| **Discovery Record** | Timestamped raw observation output produced by a discovery scan. | `Table: device_discovery_records` |
| **Credential** | Encrypted authentication secret used by discovery collectors (SNMP, SSH, API token). | `Table: credentials` |
| **System State** | Singleton configuration entity tracking installation status, telemetry, and technical metadata. | `Table: system_state`, `struct SystemState` |
| **Onboarding** | Single-shot system initialization process creating instance ID, system roles, and initial admin user. | `modules/configuration/usecase` |
| **System Role** | Built-in RBAC role (`admin`, `operator`, `viewer`) seeded during system onboarding. | `Table: roles`, `struct Role` |
| **Session Token** | Cryptographically secure stateful token prefixed with `ims_` used for authentication. | `Table: user_sessions`, `struct UserSession` |
| **Sliding Session** | Session model with 30-minute inactivity sliding renewal managed by `SessionRepository` and 7-day hard absolute expiration limit. | `modules/identity/repository` |
| **Auth Context** | Request context carrying authenticated `userID` and assigned RBAC `permissions` list. | `internal/platform/httputil` |
| **Device Inventory** | System of record for all physical, virtual, and network devices. | `modules/inventory`, `Table: devices` |
| **Device Staging** | Holding queue for newly discovered devices awaiting manual verification before promotion to active inventory. | `modules/inventory`, `Table: device_staging` |
| **User-Locked Fields** | Device properties modified manually by an operator, protected from automated scanner overwrites. | `metadata->'user_locked_fields'` |
| **Field Confidence Scores** | Per-attribute confidence scores (`metadata->'field_confidence_scores'`) governing reconciliation precedence. | `modules/discovery/engine` |

---

## Architectural Decision Records (ADRs) & Specifications

- **RFC-001**: System Vision & Technical Architecture Specification
- **RFC-006**: Core Domain Models & PostgreSQL Schema Definition
- **RFC-008**: Discovery Engine & Collector Plugin Architecture
- **RFC-010**: Repository Scaffolding & Developer Environment
- **RFC-011**: Event Bus, Audit Logger & Crypto Engine
- **RFC-012**: System Configuration & Onboarding Specification
- **RFC-013**: Identity, Authentication & RBAC Engine Specification
- **RFC-014**: Infrastructure Inventory Engine Specification
- **RFC-015**: Code Coverage & Quality Gate Policy (Patch Coverage >= 85%)
- **RFC-016**: Automated Discovery & Reconciliation Engine Specification

All active architecture decisions and technical specifications live in `docs/` and `docs/adr/`.

---

## Mandatory Engineering Guidelines (Learned from Code Reviews)

Guidelines below are continuously updated from internal code reviews, CodeRabbit findings, and peer audits.

1. **Opaque Error Responses in Controllers**: HTTP handlers MUST return generic, opaque messages to clients (e.g. `"Failed to process request"`) and log root cause details internally (`log.Printf` / `slog.Error`).
2. **Clean Concurrency & Graceful Shutdown**: Background workers MUST take `context.Context` for clean termination. Async test assertions using `wg.Wait()` MUST enforce timeouts via `select` with `time.After(2 * time.Second)`.
3. **Pure Validation & Explicit Normalization**: DTOs MUST separate mutating `Normalize()` (trimming, default values) from pure `Validate()` methods. Input range checks (e.g., `0..100`, valid IP/CIDR/MAC) MUST be executed before domain logic.
4. **Sanitized Logging & Sentinel Errors**: Dynamic user data in log strings MUST be sanitized (`sanitizeLogInput`). Error wrapping MUST preserve sentinels (`fmt.Errorf("%w: %v", SentinelErr, err)`) for `errors.Is` compatibility.
5. **No Error Swallowing & Mandatory Logging**: All error returns and parse failures MUST be either returned to caller or logged via `slog.Warn/Error`.
6. **Paginated Database Queries**: Any database listing operation used for in-memory matching/processing MUST use pagination loops to handle datasets > 1000 items.
7. **Resilient JSON Type Assertions**: Dynamic JSON metadata unmarshaled into `map[string]interface{}` MUST handle numbers (`float64`) and strings via `fmt.Sprintf("%v", val)`.
8. **Strict Encryption Enforcement**: Operations requiring secret storage MUST error when encryptors are missing, with NO silent fallback to plaintext.
9. **Module-Local Sentinel Errors**: Modules MUST NOT import sentinel errors (`ErrInvalidUUID`, `ErrInvalidInput`, etc.) from sibling module `usecase` packages. Shared error types belong in `internal/platform/` or are re-declared locally within each module that needs them.
10. **Bootstrap Validation Before Resource Allocation**: Configuration validations (encryptor keys, credentials, feature flags) MUST be performed BEFORE allocating resources that require explicit cleanup (database pools, event bus workers). This prevents resource leaks on validation failures.
11. **SQL Delete Rows Affected Check (`:execrows`)**: SQL delete queries targeting single entities MUST use `:execrows` in `queries/*.sql` and inspect `rowsAffected`. If `rowsAffected == 0`, the repository MUST return `ErrNotFound` (allowing HTTP 404 response).
12. **RFC Spec & DTO Alignment**: RFC endpoint specifications MUST match 1:1 with the actual JSON structure returned by DTO structs (preventing discrepancies between documentation and code).
13. **Strict TLS Protocol Configuration (`MinVersion`)**: Any `tls.Config` constructed for HTTP clients or TLS listeners MUST explicitly specify `MinVersion: tls.VersionTLS12` (or `tls.VersionTLS13`) to prevent TLS fallback security warnings.
14. **URL Scheme & Host Validation for External Requests**: Any user-provided URL string used to construct `http.Client` requests MUST be parsed via `url.Parse` and validated to enforce explicit allowed schemes (`http://` or `https://`) and a non-empty host before request execution, preventing SSRF / uncontrolled data in network request vulnerabilities.
15. **Ordering Subscription & Replay Query in Event Streams**: Streaming HTTP handlers (such as SSE) MUST register client subscription (`Subscribe()`) BEFORE executing past event replay queries (`GetEventsAfter(lastEventID)`). This eliminates race windows where live events emitted during the replay query phase could be dropped.
16. **Non-Blocking Event Drop Visibility & Safe UUID Construction**: Event message builders MUST handle UUID generation without calling `uuid.Must` (avoiding panic in background EventBus workers). Channel broadcast drops in event gateways MUST log a warning (`slog.Warn`) with event metadata.
17. **Deterministic Async Test Synchronization**: Unit tests waiting for background goroutines or channel subscriptions MUST use deterministic state polling (e.g. `waitForSubscriber(gw, count, timeout)`) instead of arbitrary fixed `time.Sleep` durations to prevent test flakiness under CI load.
