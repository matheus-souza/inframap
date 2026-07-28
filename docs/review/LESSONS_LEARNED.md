# InfraMap — Continuous Engineering & Review Knowledge Base

> **Purpose**: This document catalogues lessons learned from external code reviews (CodeRabbit, Peer Reviews, Audit Reports). Every developer and AI agent MUST consult this knowledge base before specifying, designing, or implementing new modules.

---

## 🛑 Anti-Patterns & Mandatory Guidelines Catalog

### 1. Error Propagation & Logging
- ❌ **Anti-Pattern**: Swallowing errors silently (e.g., `if fetchErr == nil { ... }` without an `else` branch, or discarding `netip.ParseAddr` errors without logging).
- ✅ **Required Pattern**: Always log errors with contextual fields via `slog.Logger` or return wrapped errors with sentinel preservation (`fmt.Errorf("failed to process: %w", err)`).
- 🔒 **Controller Privacy Rule**: Return opaque error responses to HTTP clients (`httputil.WriteError`), but log detailed tracebacks internally.

### 2. Database & Data Access
- ❌ **Anti-Pattern**: Using static query limits (e.g. `limit = 1000`) without a pagination loop when fetching active inventory for matching or processing.
- ✅ **Required Pattern**: Implement pagination loops (`for { offset += limit }`) when fetching collections that could exceed page boundaries.
- ❌ **Anti-Pattern**: Plaintext fallbacks when encryption dependencies are missing (e.g., saving unencrypted secrets when `encryptor == nil`).
- ✅ **Required Pattern**: Enforce strict erroring (`return nil, fmt.Errorf("encryptor is required")`) whenever encryption or security requirements are unfulfilled.

### 3. Type Assertions & JSON Unmarshaling
- ❌ **Anti-Pattern**: Expecting `.(string)` on unmarshaled JSON numbers inside `map[string]interface{}` (e.g., `meta["proxmox"]["vm_id"].(string)` fails when JSON contains `100`).
- ✅ **Required Pattern**: Accept interface values and convert using `fmt.Sprintf("%v", val)` or `strings.TrimSpace` for string/number flexibility.

### 4. State Reconciliations & Metadata Merging
- ❌ **Anti-Pattern**: Overwriting nested JSONB namespaces (e.g. `meta[sourceType] = incoming.RawPayload`), wiping out existing provider keys during partial scans.
- ✅ **Required Pattern**: Perform deep key-by-key merging on nested metadata maps and use `reflect.DeepEqual` before marking records as changed.
- ❌ **Anti-Pattern**: Single device-level confidence score blocking independent attribute updates.
- ✅ **Required Pattern**: Track confidence scores per field independently in `metadata.field_confidence_scores`.

### 5. Cross-Module Boundary Isolation
- **Anti-Pattern**: Importing sentinel errors (`ErrInvalidUUID`, `ErrInvalidInput`) from a sibling domain module's `usecase` package (e.g., `discovery` importing from `inventory/usecase`). This creates hidden coupling that breaks when the source module refactors.
- **Required Pattern**: Define module-local sentinel errors in each module's `usecase` package. If errors are truly shared across 3+ modules, promote them to `internal/platform/errors`.

### 6. Resource Leak Prevention in Bootstrap
- ❌ **Anti-Pattern**: Performing validations (e.g., encryptor key validation) AFTER creating expensive resources (database pool, event bus). If validation fails, the already-allocated resources are never cleaned up.
- ✅ **Required Pattern**: Validate all configuration and create lightweight dependencies BEFORE allocating resources that require explicit cleanup. Order bootstrap steps: config validation → resource allocation → module wiring.

### 7. Documentation & Implementation Alignment
- ❌ **Anti-Pattern**: Documenting future state machine transitions (e.g., 24h/30d offline sweeps) as already delivered in a feature RFC.
- ✅ **Required Pattern**: Clearly distinguish what is delivered in the current feature PR vs. what is delegated to future background lifecycle workers (Phase 4.2).

### 8. Repository Error Discrimination
- ❌ **Anti-Pattern**: Mapping ALL database errors to `ErrNotFound` (e.g., `if err != nil { return nil, ErrLinkNotFound }`). Connection timeouts, context cancellations, and constraint violations are silently converted to "not found", hiding real failures.
- ✅ **Required Pattern**: Check for `pgx.ErrNoRows` specifically to return `ErrNotFound`. All other errors must be wrapped and returned or logged.

### 9. HTTP Client Timeout for External Integrations
- ❌ **Anti-Pattern**: Creating `http.Client{}` without a `Timeout` field for provider API calls. If the target accepts TCP but never responds, the goroutine hangs indefinitely.
- ✅ **Required Pattern**: Always set `Timeout: 30 * time.Second` (or appropriate ceiling) on `http.Client` instances used for external API calls.

### 10. Silent Error Swallowing in Provider Fetch Loops
- ❌ **Anti-Pattern**: Cascading `if err == nil { ... }` checks that silently discard errors in optional fetch paths (e.g., Docker `/info`, Proxmox QEMU VMs per node). Operators see incomplete results with no indication of why data is missing.
- ✅ **Required Pattern**: Log each error via `slog.Warn` with contextual fields (provider, node, endpoint) before continuing the loop.

### 11. Internal Error Details Leaked to API Clients
- ❌ **Anti-Pattern**: Passing `err.Error()` directly as the HTTP response message (e.g., `httputil.WriteError(..., err.Error(), nil)` or `Message: err.Error()`). This leaks internal details such as hostnames, IPs, connection strings, and stack traces.
- ✅ **Required Pattern**: Return a static, opaque message to the client (e.g., `"Invalid input"`, `"Health check failed"`). Log the full error server-side via `slog.Warn/Error`.

### 12. SSE/Streaming Write Error Handling
- ❌ **Anti-Pattern**: Ignoring `fmt.Fprint` return values when writing to SSE connections (e.g., `_, _ = fmt.Fprint(w, ": connected\n\n")`). A disconnected client silently fails writes, but the subscriber goroutine stays registered in the event bus, filling its channel buffer and potentially blocking publishers.
- ✅ **Required Pattern**: Check every write to an SSE `http.ResponseWriter`. On error, log with `slog.Error` and return immediately — `defer unsub()` handles cleanup. Apply consistently to initial ack, replay, heartbeat, and live event writes.

### 13. Hardcoded Cryptographic Fallback Keys
- ❌ **Anti-Pattern**: Using a hardcoded, publicly known key string (e.g., `"12345678901234567890123456789012"`) as a fallback when `INFRAMAP_MASTER_KEY` is not set. This makes all encrypted secrets (SNMP, SSH keys, API tokens) decryptable by anyone who reads the source code.
- ✅ **Required Pattern**: Generate an ephemeral random key via `crypto/rand` for dev mode. Log a prominent warning that encrypted data won't survive restarts. Never use a static key that appears in source code or test fixtures.

### 14. Request Body Size Limits for Secret Endpoints
- ❌ **Anti-Pattern**: Decoding JSON request bodies without a size limit on endpoints that accept secret data. An arbitrarily large payload exhausts memory during JSON decoding and encryption.
- ✅ **Required Pattern**: Apply `http.MaxBytesReader(w, r.Body, limit)` before `json.NewDecoder().Decode()`. Check `errors.As(err, &http.MaxBytesError{})` and return HTTP 413.

### 15. Database CHECK Constraints for Domain Enums
- ❌ **Anti-Pattern**: Relying solely on application-layer DTO validation to enforce allowed values for enum-like columns (e.g., credential types). Direct SQL writes, migrations, or admin tools can bypass validation.
- ✅ **Required Pattern**: Add `CHECK (col IN (...))` in the migration for columns with a closed set of valid values.

### 16. Deterministic Pagination Ordering
- ❌ **Anti-Pattern**: Using `ORDER BY created_at DESC` without a tiebreaker in paginated queries. Rows created at the same instant can shift between pages.
- ✅ **Required Pattern**: Always include a unique column as tiebreaker: `ORDER BY created_at DESC, id DESC`.

### 17. Test Fixtures Triggering Secret Scanners
- ❌ **Anti-Pattern**: Using realistic PEM headers (`-----BEGIN RSA PRIVATE KEY-----`) or AWS key patterns in test fixtures. Gitleaks and similar scanners flag these, blocking CI pipelines.
- ✅ **Required Pattern**: Use clearly fake dummy strings (e.g., `"fake-ssh-key-fixture-not-real"`) that don't match scanner rules.

### 18. Gradle Version Compatibility with Kotlin Gradle Plugin
- ❌ **Anti-Pattern**: Using the latest Gradle wrapper version without checking Kotlin Gradle Plugin (KGP) compatibility matrix. KGP versions have explicit upper bounds on supported Gradle versions (e.g., KGP 2.1.21 supports up to Gradle 8.12.1).
- ✅ **Required Pattern**: Before setting `distributionUrl` in `gradle-wrapper.properties`, verify the Gradle version falls within the KGP-supported range at https://kotlinlang.org/docs/gradle-configure-project.html#apply-the-plugin.

### 19. WASM Readiness Signaling (Canvas-Based UIs)
- ❌ **Anti-Pattern**: Using canvas dimension checks (`canvas.width > 0`) or arbitrary timeouts to detect when a Compose/WASM application has finished loading. Canvas elements have default positive dimensions before any content is rendered, making this check unreliable.
- ✅ **Required Pattern**: Use explicit readiness callbacks from the application layer. In Kotlin/WASM, use `@JsFun` interop to call a JavaScript function (e.g., `window.infraMapReady()`) from a `LaunchedEffect(Unit)` block. The JavaScript side listens for this callback to transition from splash to app, with a timeout that shows an error state and retry button (not silent hiding).

### 20. Unit Tests Must Assert Production Code, Not Literals
- ❌ **Anti-Pattern**: Writing unit tests that compare hardcoded values against identical hardcoded values (e.g., `assertEquals(0xFFbd93f9u.toLong(), 0xFFbd93f9u.toLong())`). These tests pass even if the production code changes, providing false confidence.
- ✅ **Required Pattern**: Make production constants `internal` and test against the actual production values (e.g., `assertEquals(Color(0xFFbd93f9), InfraMapColorScheme.primary)`). Tests should break when production code changes unexpectedly.

### 21. CodeQL and Non-JVM Kotlin Targets
- ❌ **Anti-Pattern**: Enabling CodeQL `java-kotlin` language scanning on repositories containing only Kotlin/WASM or Kotlin/JS source sets. CodeQL's java-kotlin extractor only supports JVM-targeted Kotlin and fails with "could not process any code" on WASM/JS targets.
- ✅ **Required Pattern**: In CodeQL workflows, filter source detection to exclude non-JVM source sets (`wasmJs*`, `js*`) when checking for java-kotlin code. Only enable the scan when JVM-targeted Kotlin files exist (e.g., `src/main/kotlin/`, `src/jvmMain/`).

### 22. URL Path Sanitization with `filepath.Clean` (Not `path.Clean`)
- ❌ **Anti-Pattern**: Using `path.Clean(r.URL.Path)` to sanitize URL paths before serving files. Semgrep flags this as `filepath-clean-misuse` because `path.Clean` is not designed to prevent path traversal attacks — it normalizes path separators but doesn't guarantee safety.
- ✅ **Required Pattern**: Use `filepath.Clean("/" + strings.Trim(r.URL.Path, "/"))` followed by `filepath.ToSlash(cleaned)` to normalize back to forward slashes for `fs.FS` compatibility. This satisfies Semgrep's path traversal rule while maintaining cross-platform correctness with embedded filesystems.

### 23. External Coverage Tools: Consistent Evaluation Before Gating
- ❌ **Anti-Pattern**: Relying on external coverage tools (e.g., Codecov) as merge gates without ensuring they evaluate with complete data. When multiple CI jobs upload coverage reports (backend + frontend), the tool may post a premature SUCCESS after the first upload, then correct to FAILURE after receiving all data — but auto-merge already triggered on the premature result.
- ✅ **Required Pattern**: Configure `after_n_builds: N` (where N = number of CI jobs uploading coverage) and `wait_for_ci: true` in the coverage tool configuration. This ensures evaluation only happens after all data is available. When adding a new coverage upload job, always increment `after_n_builds`.
- 📖 **Incident**: PRs #40 and #41 merged to develop with `codecov/project` FAILURE because Codecov posted a CheckRun (SUCCESS) after 1/2 uploads, and the commit Status (FAILURE) arrived after auto-merge had already executed.

### 24. All Quality Gate Jobs Must Be Required Status Checks
- ❌ **Anti-Pattern**: Adding a new CI quality gate job (e.g., `Verify Frontend Quality Gates`) without adding it to the branch protection required status checks list. The job runs and fails, but the merge proceeds because GitHub only blocks on checks explicitly listed as required.
- ✅ **Required Pattern**: When creating a new CI job that validates code quality, immediately add its exact name to the branch protection required status checks via GitHub API or Settings UI. Verify with `gh api repos/{owner}/{repo}/branches/{branch}/protection/required_status_checks`.
- 📖 **Incident**: PR #39 merged to develop with `Verify Frontend Quality Gates` FAILURE because it was not in the required checks list.

### 25. GITHUB_TOKEN Does Not Trigger Subsequent Workflows
- ❌ **Anti-Pattern**: Using `GITHUB_TOKEN` (or `github.token`) in workflows that merge PRs (auto-merge) and expecting the resulting push to trigger CI on the target branch. GitHub suppresses workflow runs for events caused by `GITHUB_TOKEN` to prevent infinite loops.
- ✅ **Required Pattern**: Accept that auto-merge pushes won't trigger CI on the target branch when using `GITHUB_TOKEN`. Add `workflow_dispatch` to CI workflows so baseline runs can be triggered manually. For projects requiring automatic baseline updates, use a GitHub App token or PAT instead of `GITHUB_TOKEN` in auto-merge workflows.
- 📖 **Impact**: Coverage tools using `target: auto` compare against the last commit with a report. When CI doesn't run on target branch pushes, the baseline becomes stale, causing false positive/negative coverage comparisons.

---

## 📋 Pre-Implementation Checklist (Run Before Writing Code)

Before writing code for any ticket or RFC, verify:
- [ ] Are DTO `Normalize()` (mutating) and `Validate()` (pure) separated?
- [ ] Are all error branches handled with either an explicit return or `slog.Warn/Error` logging?
- [ ] Are all database fetches paginated if total records can exceed 1000?
- [ ] Are JSON map assertions resilient to numbers vs strings?
- [ ] Are metadata merges deep-merged and guarded by `reflect.DeepEqual`?
- [ ] Is encryption strictly enforced without silent fallback to plaintext or hardcoded keys?
- [ ] Are sentinel errors defined locally (not imported from sibling modules)?
- [ ] Are bootstrap validations performed BEFORE resource allocation?
- [ ] Are unit test suites targeting >= 85% Patch Coverage?
- [ ] Do repository `Get*ByID` methods check `pgx.ErrNoRows` specifically (not blanket `ErrNotFound`)?
- [ ] Do all `http.Client` instances for external APIs have an explicit `Timeout`?
- [ ] Are provider fetch loops logging errors before continuing (not silently swallowing)?
- [ ] Are controller error responses using static messages (not `err.Error()`)?
- [ ] Do SSE/streaming handlers check every write error and terminate on failure?
- [ ] Do secret-accepting endpoints apply `http.MaxBytesReader` before decoding?
- [ ] Do enum-like DB columns have `CHECK` constraints in the migration?
- [ ] Do paginated queries include a unique tiebreaker column in `ORDER BY`?
- [ ] Are test fixtures free of patterns that trigger secret scanners (PEM headers, AWS keys)?
- [ ] Is the Gradle wrapper version within the Kotlin Gradle Plugin's supported range?
- [ ] Do WASM/Canvas-based UIs use explicit readiness callbacks (not dimension checks or silent timeouts)?
- [ ] Do unit tests assert against actual production values (not hardcoded literal-vs-literal)?
- [ ] Is CodeQL java-kotlin scanning excluded for non-JVM Kotlin targets (WASM, JS)?
- [ ] Is URL path sanitization using `filepath.Clean` (not `path.Clean`) with `filepath.ToSlash` for `fs.FS`?
- [ ] Is the external coverage tool configured with `after_n_builds` matching the number of coverage upload jobs?
- [ ] Are all CI quality gate jobs listed as required status checks in branch protection?
- [ ] Does the CI workflow have `workflow_dispatch` for manual baseline refresh?
