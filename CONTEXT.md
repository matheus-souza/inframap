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
- **RFC-017**: Network Topology & Mapping Engine Specification

All active architecture decisions and technical specifications live in `docs/` and `docs/adr/`.

---

## Mandatory Engineering Guidelines (Learned from Code Reviews)

Guidelines below are continuously updated from internal code reviews, CodeRabbit findings, and peer audits.

1. **Opaque Error Responses in Controllers**: HTTP handlers MUST return generic, opaque messages to clients (e.g. `"Failed to process request"`) and log root cause details internally (`log.Printf` / `slog.Error`).
2. **Clean Concurrency & Graceful Shutdown**: Background workers MUST take `context.Context` for clean termination. Async event bus tests MUST use a buffered `chan string` + `select`/`time.After(5s)` for synchronization — never unbounded `sync.WaitGroup.Wait()` or `time.Sleep`.
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
18. **Explicit HTTP Client Timeout for External Requests**: Any `http.Client` created for external API calls (provider integrations, webhooks, health checks) MUST set an explicit `Timeout` field (e.g. `30 * time.Second`). Omitting it causes the client to hang indefinitely if the target accepts the TCP connection but never responds.
19. **Error Logging in Non-Critical Provider Fetch Paths**: Provider integration code that fetches optional or per-item data (e.g., Docker `/info`, Proxmox QEMU VMs per node) MUST log errors via `slog.Warn` before continuing. Silent error swallowing hides operational failures that produce incomplete discovery results.
20. **SSE/Streaming Write Error Handling**: Every write to an SSE `http.ResponseWriter` (`fmt.Fprint`) MUST check the returned error. On failure, log via `slog.Error` and return immediately — `defer unsub()` handles subscriber cleanup. Ignoring write errors keeps dead subscribers registered in the EventBus, filling channel buffers and potentially blocking publishers.
21. **No Hardcoded Cryptographic Keys**: Bootstrap or factory code MUST NEVER use hardcoded, publicly known keys as fallback when environment variables are missing. For development mode, generate an ephemeral random key via `crypto/rand` and log a prominent warning that encrypted data won't survive restarts. Hardcoded keys in source code are equivalent to plaintext storage.
22. **Request Body Size Limits for Secret-Bearing Endpoints**: Controllers that accept secret payloads (credentials, API tokens, SSH keys) MUST apply `http.MaxBytesReader` before decoding. Return HTTP 413 with `errors.As(err, &http.MaxBytesError{})` when the limit is exceeded. This prevents memory exhaustion during JSON decoding and encryption of arbitrarily large payloads.
23. **Database CHECK Constraints for Domain Enums**: When a column represents a closed set of values validated at the DTO layer (e.g., credential types, device statuses), the migration MUST also enforce the constraint via SQL `CHECK (col IN (...))`. This prevents invalid data from bypassing the application layer via direct SQL writes, migrations, or admin tools.
24. **Deterministic Pagination Ordering**: `ORDER BY` clauses in paginated SQL queries MUST include a unique column (typically `id`) as a tiebreaker after the primary sort column. Without it, rows with identical sort values can shift between pages, causing duplicates or omissions.
25. **Gradle/KGP Version Compatibility**: When configuring Kotlin Multiplatform projects, the Gradle wrapper version MUST fall within the Kotlin Gradle Plugin's supported range (e.g., KGP 2.1.21 supports up to Gradle 8.12.1). Using unsupported versions causes build failures or undefined behavior.
26. **Explicit Readiness Signaling for WASM Applications**: Compose/WASM entry points MUST signal readiness to the host page via an explicit callback (`@JsFun` interop calling a JavaScript function). The splash/loading screen MUST transition to an error state with a retry button on timeout, not silently hide.
27. **Unit Tests Must Assert Production Values**: Unit tests MUST compare expected values against actual production constants/functions (e.g., `InfraMapColorScheme.primary`), not hardcoded literal-vs-literal comparisons. Production values referenced in tests MUST be `internal` visibility to enable test access.
28. **CodeQL Language Matrix for Non-JVM Kotlin**: CodeQL workflows with `java-kotlin` in the language matrix MUST exclude Kotlin/WASM and Kotlin/JS source sets from source detection, as the CodeQL extractor only supports JVM-targeted Kotlin.
29. **URL Path Sanitization for File Serving**: Code serving files from `fs.FS` based on URL paths MUST use `filepath.Clean` (not `path.Clean`) for sanitization, followed by `filepath.ToSlash` to maintain forward-slash compatibility with `fs.FS`. Semgrep flags `path.Clean` on user input as `filepath-clean-misuse`.
30. **External Coverage Tool Consistency**: When using external coverage tools (Codecov) as merge gates with multiple CI jobs uploading coverage, configure `after_n_builds: N` (N = number of upload jobs) and `wait_for_ci: true` to prevent premature evaluation. The tool may post a SUCCESS CheckRun after partial data, then correct to FAILURE — but auto-merge already triggered.
31. **Required Status Checks Completeness**: Every CI quality gate job MUST be added to the branch protection required status checks list. A job that runs and fails but is not required will NOT block the merge. Verify with `gh api repos/{owner}/{repo}/branches/{branch}/protection/required_status_checks`.
32. **GITHUB_TOKEN Workflow Trigger Limitation**: `GITHUB_TOKEN` pushes (including auto-merge) do NOT trigger subsequent workflow runs on the target branch. CI workflows MUST include `workflow_dispatch` for manual baseline refresh. Coverage tools using `target: auto` will have stale baselines without this.
33. **Pagination/Normalization Tests Must Assert Normalized Values**: Tests exercising pagination defaults (zero/negative page, out-of-range perPage) MUST assert the actual normalized values passed to the repository mock — not just `err == nil`. Capture limit/offset/status via mock fields and verify against expected defaults.
34. **Concurrent Async API Batching in ViewModels**: When fetching multiple independent API endpoints for a screen/dashboard, ViewModels MUST execute requests concurrently using `coroutineScope` + `async { ... }` instead of sequential `await`s to minimize total network latency. Always cancel any in-flight fetch job before launching a new fetch to prevent stale async responses from overwriting updated state.
35. **Lifecycle Disposal & Cleanup for ViewModels in Compose**: When a Composable instantiates or remembers a ViewModel with background coroutines/jobs (such as auto-refresh loops or SSE event listeners), use `DisposableEffect(viewModel)` with `onDispose { viewModel.clear() }` to guarantee that background jobs are cancelled when the route unmounts or dependencies change.
36. **Codecov Exclusions for Composable UI Screens**: When creating or modifying Compose UI screen files (`*Screen.kt` / `ui/app/**`), ensure they are covered by the `frontend/src/commonMain/kotlin/**/ui/**/*Screen.kt` pattern in `codecov.yml` to mirror Kover exclusions in `build.gradle.kts` and prevent Codecov patch coverage merge blocks on UI layout composables.
37. **Test Scheduler Determinism with Recurring Delay Loops**: In unit tests exercising ViewModels or classes with recurring delay loops (such as periodic auto-refresh), use `runCurrent()` (or explicit time steps with `advanceTimeBy()`) before calling disposal methods (`clear()`, `stopAutoRefresh()`) instead of `advanceUntilIdle()`. Calling `advanceUntilIdle()` while a recurring delay loop is active causes the test scheduler to execute infinite virtual time iterations or throw `UncompletedCoroutinesError`.
38. **SSE Disconnection Handling & Reconnection Loop**: Front-end SSE clients and ViewModels MUST explicitly handle `SSEEvent.Disconnected`. Upon receiving a disconnect event or connection error, close the existing native connection resource (`disconnect()`) and execute reconnection logic with backoff delay (e.g. 5s) inside a controlled loop (`while (isActive)`), preserving `last_event_id` to avoid duplicating event listeners or losing event streams.
39. **Local Pre-Push Quality Gate Enforcement**: The repository configures `.githooks/pre-push` via `make setup-hooks` (`git config core.hooksPath .githooks`). The hook MUST run `make verify` (executing backend Go lint/test + frontend Kotlin `./gradlew jvmTest ktlintCheck detekt koverVerify`). This enforces 85%+ coverage thresholds locally on every `git push`, blocking pushes that would break Codecov or CI gates.
40. **Explicit Reconnection Verification in SSE/Event Stream Unit Tests**: Unit tests exercising event stream handlers (such as `SSEClient` or `WebSocketClient`) MUST track total connection attempts (`connectCount`) in test doubles (`FakeSSEClient`) and explicitly assert that reconnect attempts occur after disconnected events and backoff delays (`assertEquals(2, fakeSse.connectCount)`).
41. **Test Scheduler Execution Guard for Recurring Infinite Loops**: Unit tests exercising ViewModels or background services with infinite recurring loops (`while(isActive) { delay(...) }`) MUST NOT invoke `advanceUntilIdle()` while the loop is active. Use `runCurrent()` to advance scheduled tasks deterministically before stopping background jobs (`stopAutoRefresh()`), avoiding infinite test scheduler execution.
42. **Unified Single-Job Cancellation for Event-Driven Reloads**: When ViewModels reload state in response to asynchronous push events (such as SSE or WebSocket notifications), the reload operation MUST delegate to a single-job runner (`triggerFetchMetrics()`) that cancels any in-flight fetch job (`fetchMetricsJob?.cancel()`) prior to starting a new request. This prevents race conditions where an older, delayed network response overwrites newer data.
43. **Mutation Job Tracking & Reentrancy Guards in ViewModels**: User-triggered MVI mutation actions in ViewModels (such as `createDevice()`, `updateDevice()`, `deleteDevice()`) MUST track their coroutine in a dedicated `Job` variable (e.g., `submitJob`, `deleteJob`), enforce reentrancy guards by checking execution state flags (`isSubmitting` / `isDeleting`), and explicitly cancel all tracked jobs inside the `clear()` method invoked by Compose `onDispose`.
