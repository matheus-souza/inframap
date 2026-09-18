# 14. Entity Editing, Soft-Delete Semantics, and Lifecycle Management

Date: 2026-09-14

## Context

Prior to this decision, InfraMap implemented creation flows for Subnets and Discovery Sources, but lacked:
1. End-to-end update capabilities (`PUT` endpoints, frontend edit routes, and pre-populated edit forms) for Subnets, Discovery Sources, and Devices.
2. Uniform soft-deletion semantics across infrastructure entities: while `devices` possessed a `deleted_at` column, `subnets` and `discovery_sources` lacked `deleted_at`, using hard deletion or lacking deletion endpoints entirely.
3. Partial unique index handling: `subnets.cidr` was constrained with `NOT NULL UNIQUE`, preventing re-registration of a previously deleted subnet CIDR.
4. Safeguards around sensitive secrets during discovery source updates, risking either credential leakage or unintended SSRF redirection of stored credentials to altered network endpoints.
5. Clear behavior for automated discovery sweeps encountering previously deleted devices, causing either silent resurrection or permanent zombie suppression.

## Decisions

### 1. Canonical ORM Timestamps & Soft-Delete Semantics
- All primary domain entities (`devices`, `subnets`, `discovery_sources`, and related persistent resources) must adhere to the standard ORM timestamp pattern:
  - `created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP`
  - `updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP`
  - `deleted_at TIMESTAMPTZ NULL`
- Soft-deletion sets `deleted_at = CURRENT_TIMESTAMP` and updates `updated_at`.
- Active records are strictly defined by `deleted_at IS NULL`. All standard domain queries (inventory lists, containment lookups, scheduler targets, topology queries) MUST filter `WHERE deleted_at IS NULL`.
- Unique constraints that must allow re-creation after soft deletion (such as `subnets.cidr` or `discovery_sources.name`) are implemented as PostgreSQL partial unique indexes:
  ```sql
  CREATE UNIQUE INDEX uq_subnets_cidr_active ON subnets (cidr) WHERE deleted_at IS NULL;
  CREATE UNIQUE INDEX uq_discovery_sources_name_active ON discovery_sources (name) WHERE deleted_at IS NULL;
  ```
- Soft-deleted entities remain in the database to preserve historical event provenance, audit logs, and discovery runs.

### 2. Dependent Record Semantics: Cascade, Block, and Unlink
Because PostgreSQL `ON DELETE CASCADE` does not automatically trigger upon an `UPDATE ... SET deleted_at = NOW()`, relationship lifecycle rules are enforced explicitly at the application usecase layer:
1. **Owned Child Components (Logical Cascade)**: Components owned entirely by the parent entity (e.g., `discovery_source_collectors`, scheduled recurring runs) are soft-deleted or deactivated when the parent is deleted.
2. **Shared External Resources (Strict 409 Block)**: Shared entities such as central `credentials` cannot be deleted while referenced by an active discovery source or collector; the API returns `HTTP 409 Conflict` with clear dependent entity references.
3. **Observed Network Facts (Preserve & Unlink)**: Deleting a parent topology or configuration entity does NOT destroy observed facts:
   - Deleting a Subnet does NOT delete IP addresses or devices located within that CIDR.
   - Deleting a Discovery Source does NOT delete discovered devices from inventory; the devices retain their operational status and historical provenance, while their active polling link is marked inactive.

### 3. Subnet CIDR Editability and Containment Integrity
- Subnet CIDR is fully editable via `PUT /api/v1/subnets/{id}`.
- Validations enforced by the backend:
  - Format and prefix mask validation (`netip.Prefix`).
  - Gateway IP containment: `gateway_ip` must fall within the new CIDR scope.
  - Conflict detection: The new CIDR must not overlap or duplicate an existing active subnet (`409 Conflict`).
- The frontend UI displays an impact warning dialog if resizing or changing the CIDR alters the set of IP addresses contained within its scope.

### 4. Discovery Source Secret Preservation & SSRF Target Guard
- Updating a discovery source (`PUT /api/v1/discovery/sources/{id}`):
  - Leaving secret fields (e.g. API tokens, passwords) blank in the update request signals the backend to preserve the existing server-side encrypted secret.
  - Test connection / health check endpoint (`POST /api/v1/discovery/sources/{id}/health`) executes live connectivity using the backend-decrypted secret without ever transmitting the secret back to the frontend.
- **Target Change Guard**: If the update request modifies the target host, URL, or port, the backend mandates re-entry of credentials (`HTTP 422 Unprocessable Entity: secret_required_on_target_change`). This fail-closed invariant prevents stored credentials from being exfiltrated or transmitted to an unintended target via SSRF.

### 5. UI/UX Interaction and Destruction Affordances
- **List Interaction**: Table and list rows are interactive (`m3ClickableCursor()`) and navigate to the respective Edit screen upon click.
- **Action Menu**: Each row features a secondary actions overflow menu (`⋮`) containing explicit "Editar" and "Excluir" actions.
- **Edit Screen Danger Zone**: Destructive deletion actions on the Edit screen are isolated in a styled "Danger Zone" card at the bottom of the form.
- **Impact Confirmation Dialog**: Deletion triggers a modal confirmation dialog outlining the exact impact (e.g., number of collectors halted, devices transitioning to unmanaged), requiring explicit operator confirmation before dispatching `DELETE`.

### 6. Automated Rediscovery of Soft-Deleted Devices
- When automated discovery sweeps (network scan, Proxmox, or Docker) detect a device that matches an existing record with `deleted_at IS NOT NULL`:
  - The device is NOT automatically restored or silently re-created.
  - The observation is routed to the **Staging Queue** with a prominent "Previamente Deletado" tag.
  - Operators retain full control to either explicitly restore the device (clearing `deleted_at`) or dismiss/suppress the staged observation.

## Consequences

### Positive
- Consistent data lifecycle across all infrastructure resources with complete audit trails.
- Safe credential management without secret exposure in client-side responses or risk of SSRF redirection.
- Operator transparency through impact summaries and explicit confirmation dialogs.
- Clean re-registration of subnets and sources via partial unique indexes.

### Negative / Trade-offs
- Soft-delete requires all SQL queries to include `WHERE deleted_at IS NULL` to prevent resurrection in list views.
- Application-level cascade logic must be carefully maintained and covered by unit/integration tests to ensure child components are consistently handled.
