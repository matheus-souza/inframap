# RFC-025 — Entity Editing, Soft-Delete Lifecycle & Secret-Safe Update Specification

| Status | Accepted |
|---|---|
| Created | 2026-09-18 |

---

## Problem Statement
InfraMap has creation flows (`POST`) for Subnets, Discovery Sources and Devices, but the edit and removal lifecycle remains incomplete and inconsistent across the three modules:
1. **Uneven deletion semantics**: `devices` already has `deleted_at TIMESTAMPTZ`, but `subnets` and `discovery_sources` lack the column — deleting a discovery source is a physical `DELETE` today (`DiscoveryRepository.DeleteSource`), and there is no deletion endpoint for subnets at all.
2. **Unconditional unique constraints**: `subnets.cidr` is defined as `CIDR NOT NULL UNIQUE`. Once a subnet is removed (physically today, by soft-delete tomorrow), the same CIDR block cannot be registered again — the constraint does not tell active records from deleted ones.
3. **Incomplete editing**: there is no `PUT /api/v1/subnets/{id}` nor `PUT /api/v1/discovery/sources/{id}`. The `EditDeviceScreen` already exists in the frontend, but there is no `EditSubnetScreen` nor `EditDiscoverySourceScreen`, nor the `Route.EditSubnet` / `Route.EditDiscoverySource` routes.
4. **Secret exfiltration risk on edit**: `discovery_sources.config_encrypted` stores the configuration payload (including secrets such as tokens and passwords) encrypted. Without an explicit preservation rule, any edit form that re-sends the configuration would force the operator to retype secrets on every change — or, worse, a malicious client could change the destination host/URL while keeping the old secret, redirecting valid credentials to an arbitrary endpoint (SSRF through an inherited secret).
5. **No edit/delete affordance in the lists**: `SubnetsScreen`, `DiscoveryListScreen` and `DeviceListScreen` have neither clickable rows nor an action menu (`⋮`) — the only way to manage a record is through the detail screen, with no isolated "Danger Zone" for deletion and no confirmation dialog summarizing the impact.
6. **Silent resurrection on rediscovery**: no current path handles an automated scan finding a device whose record already has `deleted_at IS NOT NULL` — the behavior is undefined, risking silent resurrection or permanent suppression of the "zombie".

These points were consolidated in the architectural decisions of [ADR-014](./adr/ADR-014-entity-editing-and-soft-delete-architecture.md) and in guideline **#215** of `CONTEXT.md`.

---

## Solution
Implement a uniform edit and delete lifecycle for Subnets, Discovery Sources and Devices:
- **Canonical Soft-Delete**: a database migration adding `deleted_at` to `subnets` and `discovery_sources`, replacing unconditional unique constraints with partial unique indexes `WHERE deleted_at IS NULL`.
- **Dependent record lifecycle**: logical cascade in the use case layer for child components (collectors, scheduled runs); a `409 Conflict` block for shared credentials in use; preservation and unlinking of observed network facts (IPs, devices).
- **Full Subnet editing**: `PUT /api/v1/subnets/{id}` with gateway containment validation, CIDR conflict detection and an impact confirmation dialog in the frontend.
- **Secret preservation & anti-SSRF guard**: a blank secret on edit preserves the existing encrypted secret; a new `POST /api/v1/discovery/sources/{id}/health` endpoint reuses the secret already stored on the server; changing the destination host/URL/port requires re-entering the credential (`422 secret_required_on_target_change`).
- **Uniform UI/UX**: clickable table rows (edit), a per-row action menu (`⋮` with Edit/Delete), a "Danger Zone" at the end of edit screens, and a modal confirmation dialog summarizing the impact.
- **Safe rediscovery**: scan observations matching a record with `deleted_at IS NOT NULL` are routed to the Staging queue with the **"Previamente Deletado"** (Previously Deleted) tag, never resurrecting the record automatically.

---

## User Stories

1. As a network administrator, I want to edit the CIDR, gateway and description of a registered subnet, to fix a configuration mistake without deleting and recreating the record.
2. As a network administrator, when changing a subnet's CIDR, I want an impact warning telling me how many devices/IPs will no longer be contained in the new block, so I can decide safely before confirming.
3. As a network administrator, I want the backend to reject (`409 Conflict`) a CIDR edit that collides with another active subnet, to avoid silent scope overlaps.
4. As a network administrator, I want to delete a subnet and later register the same CIDR block again, to reorganize the inventory without being blocked by a "ghost" record.
5. As a discovery operator, I want to edit a Discovery Source (name, schedule, configuration) without being forced to retype the password/token every time, so the edit form is quick and safe.
6. As a discovery operator, when I change a source's destination host/URL, I want the system to require the credential again, so an old secret is never sent to a destination other than the original.
7. As a discovery operator, I want to click "Test Connection" on the edit screen without the stored secret being exposed back to the browser, to validate connectivity safely.
8. As a discovery operator, I want to delete a Discovery Source and keep seeing the devices it already discovered in the inventory, only marked as having no active discovery origin.
9. As a credentials administrator, I want the system to block (`409 Conflict`) deleting a credential still used by an active discovery source, so production collectors do not break silently.
10. As an operator, I want to click any row of the Subnets, Discovery Sources or Devices lists to go straight to the edit screen, without hunting for a specific button.
11. As an operator, I want an action menu (`⋮`) on each row with explicit "Edit" and "Delete" options, to reach destructive actions without navigating to the detail screen.
12. As an operator, I want the delete action isolated in a "Danger Zone" at the end of the edit screen, to reduce the risk of accidental clicks.
13. As an operator, when clicking delete, I want a confirmation dialog with the exact impact summary (how many collectors will be halted, how many devices will be unlinked), to make an informed decision.
14. As an operator, I want a previously deleted device detected again by a scan to show up in the Staging queue tagged "Previamente Deletado", instead of reappearing automatically in the active inventory.

---

## Implementation Decisions

### 1. Database Migrations (Soft-Delete & Partial Unique Indexes)
A new `20260914000001_entity_editing_and_soft_delete.sql` migration (replicated in `backend/migrations/` and `backend/internal/platform/db/migrations/`, following the dual pattern already in the repository):

```sql
-- +goose Up

-- 1. Canonical soft-delete timestamp on subnets and discovery_sources
ALTER TABLE subnets
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ;

ALTER TABLE discovery_sources
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_subnets_deleted_at ON subnets(deleted_at);
CREATE INDEX IF NOT EXISTS idx_discovery_sources_deleted_at ON discovery_sources(deleted_at);

-- 2. Replace unconditional unique constraints with partial unique indexes
ALTER TABLE subnets DROP CONSTRAINT IF EXISTS subnets_cidr_key;

CREATE UNIQUE INDEX IF NOT EXISTS uq_subnets_cidr_active
    ON subnets (cidr) WHERE deleted_at IS NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_discovery_sources_name_active
    ON discovery_sources (name) WHERE deleted_at IS NULL;

-- +goose Down
DROP INDEX IF EXISTS uq_discovery_sources_name_active;
DROP INDEX IF EXISTS uq_subnets_cidr_active;
ALTER TABLE subnets ADD CONSTRAINT subnets_cidr_key UNIQUE (cidr);
DROP INDEX IF EXISTS idx_discovery_sources_deleted_at;
DROP INDEX IF EXISTS idx_subnets_deleted_at;
ALTER TABLE discovery_sources DROP COLUMN IF EXISTS deleted_at;
ALTER TABLE subnets DROP COLUMN IF EXISTS deleted_at;
```

- `sqlc` regenerates `db.Subnet` / `db.DiscoverySource` with the `DeletedAt pgtype.Timestamptz` field and the corresponding CRUD queries in `internal/platform/db/inventory.sql.go` and `discovery.sql.go`.
- Every existing list/lookup query (`ListSubnets`, `ListSources`, the scheduler's containment lookups) is updated to include `WHERE deleted_at IS NULL`, mirroring the pattern already used by `ListDevices`/`GetDeviceByID(includeDeleted bool)`.

### 2. Backend Domain & Usecases — Dependent Records Lifecycle
PostgreSQL's `ON DELETE CASCADE` is not triggered by an `UPDATE ... SET deleted_at = NOW()`; the semantics are implemented explicitly in the use case layer:

| Module | Action when the parent is deleted | Implementation |
|---|---|---|
| `discovery` (`DefaultDiscoveryUseCase.SoftDeleteSource`) | The source's collectors (`discovery_source_collectors`) and scheduled runs are deactivated/soft-deleted (logical cascade) | Replaces `discRepo.DeleteSource` (hard delete) with `discRepo.SoftDeleteSource`; publishes `discovery_source.deleted` on the `eventbus` |
| `credentials` (`credentialsUseCase.DeleteCredential`) | Blocked if referenced by an active source/collector | New `repo.CountActiveReferences(ctx, credentialID)` check; returns `usecase.ErrCredentialInUse`, mapped to `409 Conflict` with the list of dependent sources |
| `inventory` (`DefaultInventoryUseCase.SoftDeleteSubnet`) | Contained IPs/devices are NOT deleted nor unlinked from topology — only the subnet record is marked | Follows the existing `SoftDeleteDevice` pattern |
| `discovery` (`SoftDeleteSource`) | Already discovered devices stay in the inventory; the source's `device_discovery_records` remain for history, and the device keeps its operational status | No mutation is made to `devices` — only `discovery_sources.deleted_at` is set |

```go
// backend/modules/discovery/usecase/discovery_usecase.go
func (u *DefaultDiscoveryUseCase) SoftDeleteSource(ctx context.Context, idStr string) error {
    id, err := uuid.Parse(idStr)
    if err != nil {
        return ErrInvalidUUID
    }
    if err := u.discRepo.DeactivateCollectors(ctx, id); err != nil { // logical cascade
        return err
    }
    if err := u.discRepo.SoftDeleteSource(ctx, id); err != nil {
        return err
    }
    _ = u.eventBus.Publish(ctx, eventbus.NewBaseEvent("discovery_source.deleted", map[string]interface{}{
        "source_id": id.String(),
    }))
    return nil
}
```

```go
// backend/modules/credentials/usecase/credentials_usecase.go
func (uc *credentialsUseCase) DeleteCredential(ctx context.Context, idStr string) error {
    id, err := uuid.Parse(idStr)
    if err != nil {
        return ErrInvalidUUID
    }
    inUse, sources, err := uc.repo.CountActiveReferences(ctx, id)
    if err != nil {
        return err
    }
    if inUse > 0 {
        return &ErrCredentialInUse{DependentSources: sources}
    }
    return uc.repo.DeleteCredential(ctx, id)
}
```

`ErrCredentialInUse` is mapped by the controller to `409 Conflict` with the body `{"error":"CREDENTIAL_IN_USE","dependent_sources":[{"id":"...","name":"..."}]}`.

### 3. Subnet CIDR Editability & Containment Validation
`UpdateSubnetRequest` (new, `backend/modules/inventory/dto/inventory_dto.go`):
```go
type UpdateSubnetRequest struct {
    Name              string  `json:"name"`
    CIDR              string  `json:"cidr"`
    VLANID            *int32  `json:"vlan_id,omitempty"`
    GatewayIP         *string `json:"gateway_ip,omitempty"`
    Description       *string `json:"description,omitempty"`
    DiscoveryEnabled  *bool   `json:"discovery_enabled,omitempty"`
}
```

Validations in `DefaultInventoryUseCase.UpdateSubnet`:
1. `netip.ParsePrefix(req.CIDR)` — valid format and mask.
2. If `GatewayIP` is provided, `prefix.Contains(gatewayAddr)` must be true; otherwise `ErrGatewayNotContained` → `422 Unprocessable Entity`.
3. `repo.FindOverlappingSubnet(ctx, prefix, excludeID)` — if another active subnet collides with or duplicates the CIDR, `ErrSubnetConflict` → `409 Conflict`.
4. Before persisting, the use case computes `AffectedDeviceCount` (active devices whose IP falls outside the new prefix) and returns it in the response envelope to feed the frontend impact dialog.

### 4. Discovery Source Secret Preservation & SSRF Target Guard
`UpdateDiscoverySourceRequest` reuses the `Config map[string]interface{}` shape of `CreateDiscoverySourceRequest`. The preservation rule and the target guard live in `DefaultDiscoveryUseCase.UpdateSource`:

1. **Blank-secret preservation**: for every key marked as sensitive in the provider schema (e.g. `password`, `token`, `api_token`), if the received value is an empty string or missing, the use case copies the stored value from the decrypted `config_encrypted` before re-encrypting — the client never has to send the secret again.
2. **Target Change Guard**: the use case compares the addressing fields (`host`, `url`, `port`, `socket_path`) of the received payload with the current decrypted values. If any of them differs **and** the corresponding sensitive field(s) are blank, the request is rejected:
   ```go
   if targetChanged && secretBlank {
       return nil, &ErrSecretRequiredOnTargetChange{Fields: changedTargetFields}
   }
   ```
   Mapped by the controller to `HTTP 422 Unprocessable Entity`:
   ```json
   {
     "error": "secret_required_on_target_change",
     "message": "Changing the destination host/URL requires re-entering the credential.",
     "fields": ["api_url"]
   }
   ```
3. **Health check endpoint** — `POST /api/v1/discovery/sources/{id}/health`: receives no secret field in the body (`{}` or non-sensitive override parameters only). The use case resolves and decrypts the configuration already persisted on the server (identical to RFC-024's `ProviderConfigResolver`) and runs the provider connectivity test, returning `{"status": "ok" | "error", "message": "..."}` without ever echoing the secret back.

### 5. REST API Endpoints & Contracts

| Method | Route | New? | Description |
|---|---|---|---|
| `GET` | `/api/v1/subnets/{id}` | Yes | Subnet detail to pre-fill `EditSubnetScreen` |
| `PUT` | `/api/v1/subnets/{id}` | Yes | Updates CIDR/gateway/name; `409`/`422` per §3 |
| `DELETE` | `/api/v1/subnets/{id}` | Yes | Soft-delete; the response body includes an impact summary |
| `PUT` | `/api/v1/discovery/sources/{id}` | Yes | Updates the source; preserves a blank secret; `422 secret_required_on_target_change` |
| `POST` | `/api/v1/discovery/sources/{id}/health` | Yes | Tests connectivity reusing the server-side secret |
| `DELETE` | `/api/v1/discovery/sources/{id}` | Changed | Moves from hard delete to soft-delete + logical collector cascade |
| `DELETE` | `/api/v1/credentials/{id}` | Changed | Now returns `409 Conflict` if referenced by an active source |
| `PUT` | `/api/v1/devices/{id}` | Existing | No contract change |
| `DELETE` | `/api/v1/devices/{id}` | Existing | No contract change (already soft-delete) |

Example deletion response with an impact summary (`DELETE /api/v1/subnets/{id}`):
```json
{
  "deleted_id": "b3f1...",
  "impact": {
    "affected_devices": 12,
    "unlinked_topology_edges": 0
  }
}
```

Example discovery source deletion response (`DELETE /api/v1/discovery/sources/{id}`):
```json
{
  "deleted_id": "9ac2...",
  "impact": {
    "collectors_halted": 2,
    "devices_unlinked": 34
  }
}
```

Both modules' `routes.go` are updated:
```go
// backend/modules/inventory/routes.go
mux.HandleFunc("GET /api/v1/subnets/{id}", ctrl.GetSubnetByID)
mux.HandleFunc("PUT /api/v1/subnets/{id}", ctrl.UpdateSubnet)
mux.HandleFunc("DELETE /api/v1/subnets/{id}", ctrl.DeleteSubnet)
```
```go
// backend/modules/discovery/routes.go
mux.HandleFunc("PUT /api/v1/discovery/sources/{id}", ctrl.UpdateSource)
mux.HandleFunc("POST /api/v1/discovery/sources/{id}/health", ctrl.HealthCheckSource)
```

### 6. Frontend Navigation & Routes
`Route.kt` gains two new routes, following the existing `EditDevice` pattern:
```kotlin
@Serializable
@SerialName("edit_subnet")
data class EditSubnet(val id: String) : Route

@Serializable
@SerialName("edit_discovery_source")
data class EditDiscoverySource(val id: String) : Route
```
`Navigator.kt` and the navigation graph (`InfraMapApp.kt` / `MainScaffold.kt`) get the corresponding `composable` entries, replicating the existing `NavigatorTest.kt` cases for `EditDevice` with equivalent cases for `EditSubnet(id)` and `EditDiscoverySource(id)`.

New screens and ViewModels (mirroring `EditDeviceScreen.kt` / `EditDeviceViewModel.kt`):
- `frontend/src/commonMain/kotlin/com/inframap/frontend/ui/subnets/EditSubnetScreen.kt`
- `frontend/src/commonMain/kotlin/com/inframap/frontend/ui/subnets/EditSubnetViewModel.kt`
- `frontend/src/commonMain/kotlin/com/inframap/frontend/ui/discovery/EditDiscoverySourceScreen.kt`
- `frontend/src/commonMain/kotlin/com/inframap/frontend/ui/discovery/EditDiscoverySourceViewModel.kt`

New use cases (`domain/usecase/subnet/` and `domain/usecase/discovery/`), mirroring `UpdateDeviceUseCase.kt` / `DeleteDeviceUseCase.kt`:
- `UpdateSubnetUseCase.kt`, `DeleteSubnetUseCase.kt`, `GetSubnetByIdUseCase.kt`
- `UpdateDiscoverySourceUseCase.kt`, `DeleteDiscoverySourceUseCase.kt`, `TestDiscoverySourceHealthUseCase.kt`

### 7. UI Screens & Components
Three new shared components in `frontend/src/commonMain/kotlin/com/inframap/frontend/designsystem/`, reused by the three edit screens (`EditSubnetScreen`, `EditDiscoverySourceScreen`, `EditDeviceScreen`):

- **`DangerZone.kt`**: a styled card (error border/tone from `UDS`/M3, the local `UDSTheme.udsColors.error*` equivalent) placed at the end of the edit form, holding a title, a textual impact description and the "Delete [Entity]" button.
- **`ImpactConfirmationDialog.kt`**: a reusable `AlertDialog` parameterized by `title`, `entityName` and a list of `ImpactLine(label, count)` — it renders the summary (`impact.affected_devices`, `impact.collectors_halted`, etc.) returned by the `DELETE`. It confirms only after explicit interaction (no automatic default focus on the destructive button).
- **`RowOverflowMenu.kt`**: an `IconButton` with the `⋮` icon (`Icons.Default.MoreVert`) opening a `DropdownMenu` with "Edit" and "Delete" items, used in `SubnetsScreen.kt`, `DiscoveryListScreen.kt` and `DeviceListScreen.kt`.

Changes to the existing lists:
- `SubnetsScreen.kt`, `DiscoveryListScreen.kt`, `DeviceListScreen.kt`: rows use `m3ClickableCursor()` (guideline #213) to navigate to the matching edit route; the last cell of the row hosts `RowOverflowMenu`.
- Clicking the row and clicking the menu's "Edit" item converge on the same navigation; the "Delete" item opens `ImpactConfirmationDialog` without navigating.

### 8. Localization Keys (PT/EN parity)
New keys added to `frontend/src/commonMain/composeResources/values/strings.xml` (PT, authoritative) and replicated in `values-en/strings.xml`, following the naming pattern already used by `edit_device_*`. The block below shows the authoritative PT values:

```xml
<!-- Edit Subnet -->
<string name="edit_subnet_header_title">Editar Sub-rede</string>
<string name="edit_subnet_header_subtitle">Atualize o bloco CIDR e os metadados da sub-rede</string>
<string name="edit_subnet_submit_button">Salvar Alterações</string>
<string name="edit_subnet_impact_warning">Alterar o CIDR pode afetar %1$d dispositivo(s) atualmente contidos neste bloco.</string>

<!-- Edit Discovery Source -->
<string name="edit_discovery_source_header_title">Editar Fonte de Descoberta</string>
<string name="edit_discovery_source_secret_hint">Deixe em branco para manter a credencial atual</string>
<string name="edit_discovery_source_target_changed_warning">O host/URL de destino foi alterado. Informe novamente a credencial para confirmar.</string>
<string name="edit_discovery_source_test_connection">Testar Conexão</string>

<!-- Danger Zone -->
<string name="danger_zone_title">Zona de Risco</string>
<string name="danger_zone_delete_button">Excluir %1$s</string>
<string name="danger_zone_subnet_description">Esta sub-rede será removida da listagem ativa. Dispositivos e IPs observados não serão excluídos.</string>
<string name="danger_zone_discovery_source_description">Esta fonte será removida da listagem ativa. Os dispositivos já descobertos permanecerão no inventário.</string>

<!-- Impact Confirmation Dialog -->
<string name="impact_dialog_title">Confirmar Exclusão</string>
<string name="impact_dialog_affected_devices">%1$d dispositivo(s) afetado(s)</string>
<string name="impact_dialog_collectors_halted">%1$d coletor(es) será(ão) interrompido(s)</string>
<string name="impact_dialog_confirm_button">Confirmar Exclusão</string>
<string name="impact_dialog_cancel_button">Cancelar</string>

<!-- Row Overflow Menu -->
<string name="row_overflow_menu_content_description">Mais ações</string>
<string name="row_overflow_menu_edit">Editar</string>
<string name="row_overflow_menu_delete">Excluir</string>

<!-- Staging: Previously Deleted -->
<string name="staging_tag_previously_deleted">Previamente Deletado</string>

<!-- Errors -->
<string name="error_credential_in_use">Esta credencial está em uso por %1$d fonte(s) de descoberta ativa(s).</string>
<string name="error_subnet_conflict">Este bloco CIDR conflita com uma sub-rede já cadastrada.</string>
<string name="error_gateway_not_contained">O IP do gateway não pertence ao novo bloco CIDR.</string>
<string name="error_secret_required_on_target_change">O destino foi alterado. Informe a credencial novamente para continuar.</string>
```
The `values-en/strings.xml` counterpart receives equivalent English translations for every key above, keeping the mandatory 1:1 parity (no PT key without an EN counterpart and vice versa) — an invariant already checked by the existing quality pipeline.

### 9. Rescanning Soft-Deleted Devices
In `orchestrator.go` / `DefaultDiscoveryUseCase.persistDiscoveredDevice`, when an observation matches an existing record (`GetDeviceByID(ctx, id, includeDeleted=true)`) and `device.DeletedAt.Valid == true`:
- The observation is **not** applied directly to the device (no implicit `UPDATE devices SET deleted_at = NULL`).
- A staging record is created (`CreateStagingDevice`) with `metadata.previously_deleted = true` and `matched_device_id` pointing to the soft-deleted record.
- The frontend (`StagingScreen`/approval queue) shows the **"Previamente Deletado"** tag (`staging_tag_previously_deleted`) on the matching card/row.
- Manual staging approval (`ApproveStagingDevice`) is the only path that restores the device (`deleted_at = NULL`), preserving the operator's explicit control.

---

## Testing Decisions
- **Backend Unit Tests**:
  - `inventory_usecase_test.go`: `UpdateSubnet` covering gateway containment, CIDR conflict (`409`) and the `AffectedDeviceCount` computation; `SoftDeleteSubnet` verifying that devices/IPs are neither deleted nor unlinked.
  - `discovery_usecase_test.go`: `UpdateSource` covering blank-secret preservation, the target guard (`422 secret_required_on_target_change`) with and without a re-sent secret, and `SoftDeleteSource` verifying the logical deactivation of collectors.
  - `credentials_usecase_test.go`: `DeleteCredential` returning `409 Conflict` when referenced, and succeeding when not referenced.
  - `discovery_usecase_test.go` (rescan): an observation matching a `deleted_at IS NOT NULL` device must produce a staging record with `previously_deleted = true` and **never** clear `deleted_at` directly.
- **Backend Integration Tests** (`internal/platform/db` + Testcontainers, where applicable to the repository):
  - Migration applied and rolled back (`goose up` / `goose down`) without errors.
  - Recreating a CIDR/name after soft-delete (the partial index allows a "logical" duplicate only between one active and one deleted record).
  - List queries (`ListSubnets`, `ListSources`) never return records with `deleted_at IS NOT NULL`.
- **Frontend ViewModel Tests** (`commonTest`):
  - `EditSubnetViewModelTest.kt`: error state on `409`/`422`, impact warning shown before submission.
  - `EditDiscoverySourceViewModelTest.kt`: the secret field stays empty on load (never populated by the API), the credential re-entry warning fires when editing host/URL, and `TestDiscoverySourceHealthUseCase` is called without the secret in the payload.
  - `SubnetsViewModelTest.kt` / `DiscoveryListViewModelTest.kt` / `DeviceListViewModelTest.kt`: `ImpactConfirmationDialog` opens with the correct impact values before the `DELETE` is sent.
- **Frontend Guard Tests** (`jvmTest`):
  - Extend `CursorGuardTest` (guideline #213) to cover `RowOverflowMenu` and the new clickable rows of the three lists.
  - Extend the localization parity test (key by key between `values/strings.xml` and `values-en/strings.xml`) to the new keys of section 8.
- **Coverage Gate**: keep test coverage $\ge 85\%$ and zero `ktlintCheck`/`detekt` (frontend) and `golangci-lint` (backend) violations.

---

## Out of Scope
- Automatic restoration of soft-deleted subnets or discovery sources through the UI (only devices have an approval/restore flow, through Staging).
- Bulk deletion of multiple records at once.
- Change versioning/history (a field-by-field diff audit trail) beyond the events already published on the `eventbus`.
- Definitive physical purge (hard delete) of soft-deleted records — this RFC defines no retention/purge job.

---

## Further Notes
This specification consolidates the architectural decisions recorded in [ADR-014](./adr/ADR-014-entity-editing-and-soft-delete-architecture.md) and in guideline **#215** of `CONTEXT.md`, and lays the groundwork for slicing the implementation tickets for editing/soft-delete of Subnets, Discovery Sources and the consistency reinforcement on Devices.
