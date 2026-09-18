# RFC-025: Entity Editing, Soft-Delete Lifecycle & Secret-Safe Update Specification

## Status
Approved / Ready for Implementation

## Problem Statement
O InfraMap possui fluxos de criação (`POST`) para Sub-redes, Fontes de Descoberta e Dispositivos, mas o ciclo de vida de edição e remoção permanece incompleto e inconsistente entre os três módulos:
1. **Semântica de exclusão desigual**: `devices` já possui `deleted_at TIMESTAMPTZ`, mas `subnets` e `discovery_sources` não possuem a coluna — a exclusão de uma fonte de descoberta hoje é um `DELETE` físico (`DiscoveryRepository.DeleteSource`) e não existe nenhum endpoint de exclusão para sub-redes.
2. **Constraints únicas incondicionais**: `subnets.cidr` está definido como `CIDR NOT NULL UNIQUE`. Uma vez que uma sub-rede é removida (fisicamente hoje, via soft-delete amanhã), o mesmo bloco CIDR não pode ser recadastrado — a constraint não distingue registros ativos de excluídos.
3. **Edição incompleta**: Não existe `PUT /api/v1/subnets/{id}` nem `PUT /api/v1/discovery/sources/{id}`. A tela `EditDeviceScreen` já existe no frontend, mas não há `EditSubnetScreen` nem `EditDiscoverySourceScreen`, nem as rotas `Route.EditSubnet` / `Route.EditDiscoverySource`.
4. **Risco de exfiltração de segredo em edição**: `discovery_sources.config_encrypted` armazena o payload de configuração (incluindo segredos como tokens/senhas) cifrado. Sem uma regra explícita de preservação, qualquer formulário de edição que reenvie a configuração obrigaria o operador a redigitar segredos a cada alteração — ou, pior, um cliente malicioso poderia alterar o host/URL de destino mantendo o segredo antigo, redirecionando credenciais válidas para um endpoint arbitrário (SSRF via segredo herdado).
5. **Ausência de affordance de edição/exclusão nas listagens**: `SubnetsScreen`, `DiscoveryListScreen` e `DeviceListScreen` não possuem linhas clicáveis nem menu de ações (`⋮`) — a única forma de gerenciar um registro é via tela de detalhe, sem uma "Danger Zone" isolada para exclusão nem diálogo de confirmação com resumo de impacto.
6. **Ressurreição silenciosa em redescoberta**: Nenhuma rota atual trata o caso em que uma varredura automatizada encontra um dispositivo cujo registro já possui `deleted_at IS NOT NULL` — o comportamento não está definido, criando risco de ressurreição silenciosa ou supressão permanente do "zumbi".

Estes pontos foram consolidados nas decisões arquiteturais do [ADR-014](./adr/ADR-014-entity-editing-and-soft-delete-architecture.md) e no guideline **#215** de `CONTEXT.md`.

---

## Solution
Implementar um ciclo de vida de edição e exclusão uniforme para Sub-redes, Fontes de Descoberta e Dispositivos:
- **Soft-Delete Canônico**: Migração de banco adicionando `deleted_at` a `subnets` e `discovery_sources`, substituindo constraints únicas incondicionais por índices únicos parciais `WHERE deleted_at IS NULL`.
- **Ciclo de vida de dependentes**: Cascata lógica na camada de usecase para componentes filhos (coletores, execuções agendadas); bloqueio `409 Conflict` para credenciais compartilhadas em uso; preservação e desvinculação de fatos de rede observados (IPs, dispositivos).
- **Edição completa de Sub-rede**: `PUT /api/v1/subnets/{id}` com validação de contenção do gateway, detecção de conflito de CIDR e diálogo de confirmação de impacto no frontend.
- **Preservação de segredo & guarda anti-SSRF**: Segredo em branco na edição preserva o segredo cifrado existente; novo endpoint `POST /api/v1/discovery/sources/{id}/health` reutiliza o segredo já armazenado no servidor; alteração de host/URL/porta de destino exige reentrada obrigatória de credencial (`422 secret_required_on_target_change`).
- **UI/UX uniforme**: Linhas de tabela clicáveis (edição), menu de ações por linha (`⋮` com Editar/Excluir), "Danger Zone" ao final das telas de edição, e diálogo modal de confirmação com resumo de impacto.
- **Redescoberta segura**: Observações de varredura que correspondem a um registro com `deleted_at IS NOT NULL` são roteadas para a fila de Staging com a etiqueta **"Previamente Deletado"**, nunca ressuscitando o registro automaticamente.

---

## User Stories

1. Como administrador de rede, quero editar o CIDR, o gateway e a descrição de uma sub-rede já cadastrada, para corrigir um erro de configuração sem precisar excluir e recriar o registro.
2. Como administrador de rede, ao alterar o CIDR de uma sub-rede, quero ver um aviso de impacto informando quantos dispositivos/IPs deixarão de estar contidos no novo bloco, para decidir com segurança antes de confirmar.
3. Como administrador de rede, quero que o backend rejeite (`409 Conflict`) uma edição de CIDR que colida com outra sub-rede ativa, para evitar sobreposições silenciosas de escopo.
4. Como administrador de rede, quero excluir uma sub-rede e, em seguida, poder recadastrar o mesmo bloco CIDR mais tarde, para reorganizar o inventário sem ficar bloqueado por um registro "fantasma".
5. Como operador de descoberta, quero editar uma Fonte de Descoberta (nome, agendamento, configuração) sem ser forçado a redigitar a senha/token toda vez, para que o formulário de edição seja rápido e seguro.
6. Como operador de descoberta, quero que, ao alterar o host/URL de destino de uma fonte, o sistema exija que eu informe novamente a credencial, para impedir que um segredo antigo seja enviado a um destino diferente do original.
7. Como operador de descoberta, quero clicar em "Testar Conexão" na tela de edição sem que o segredo armazenado seja exposto de volta ao navegador, para validar a conectividade com segurança.
8. Como operador de descoberta, quero excluir uma Fonte de Descoberta e continuar vendo os dispositivos que ela já descobriu no inventário, apenas marcados como sem origem de descoberta ativa.
9. Como administrador de credenciais, quero que o sistema bloqueie (`409 Conflict`) a exclusão de uma credencial que ainda está em uso por uma fonte de descoberta ativa, para não quebrar coletores em produção silenciosamente.
10. Como operador, quero clicar em qualquer linha das listagens de Sub-redes, Fontes de Descoberta ou Dispositivos para ir direto à tela de edição, sem precisar localizar um botão específico.
11. Como operador, quero um menu de ações (`⋮`) em cada linha com opções explícitas "Editar" e "Excluir", para acessar ações destrutivas sem navegar até o detalhe.
12. Como operador, quero que a ação de exclusão fique isolada em uma "Danger Zone" ao final da tela de edição, para reduzir o risco de cliques acidentais.
13. Como operador, ao clicar em excluir, quero ver um diálogo de confirmação com o resumo exato do impacto (quantos coletores serão interrompidos, quantos dispositivos ficarão sem vínculo), para tomar uma decisão informada.
14. Como operador, quero que um dispositivo previamente excluído e detectado novamente por uma varredura apareça na fila de Staging com a etiqueta "Previamente Deletado", em vez de reaparecer automaticamente no inventário ativo.

---

## Implementation Decisions

### 1. Database Migrations (Soft-Delete & Partial Unique Indexes)
Nova migração `20260914000001_entity_editing_and_soft_delete.sql` (replicada em `backend/migrations/` e `backend/internal/platform/db/migrations/`, seguindo o padrão dual já existente no repositório):

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

- `sqlc` regenera `db.Subnet` / `db.DiscoverySource` com o campo `DeletedAt pgtype.Timestamptz` e as queries CRUD correspondentes em `internal/platform/db/inventory.sql.go` e `discovery.sql.go`.
- Todas as queries de listagem/lookup existentes (`ListSubnets`, `ListSources`, containment lookups do agendador) são atualizadas para incluir `WHERE deleted_at IS NULL`, espelhando o padrão já usado em `ListDevices`/`GetDeviceByID(includeDeleted bool)`.

### 2. Backend Domain & Usecases — Dependent Records Lifecycle
A cascata `ON DELETE CASCADE` do PostgreSQL não é acionada por um `UPDATE ... SET deleted_at = NOW()`; a semântica é implementada explicitamente na camada de usecase:

| Módulo | Ação ao excluir o pai | Implementação |
|---|---|---|
| `discovery` (`DefaultDiscoveryUseCase.SoftDeleteSource`) | Coletores (`discovery_source_collectors`) e execuções agendadas do source são desativados/soft-deletados (cascata lógica) | Substitui `discRepo.DeleteSource` (hard delete) por `discRepo.SoftDeleteSource`; dispara `discovery_source.deleted` no `eventbus` |
| `credentials` (`credentialsUseCase.DeleteCredential`) | Bloqueio se referenciada por fonte/coletor ativo | Nova checagem `repo.CountActiveReferences(ctx, credentialID)`; retorna `usecase.ErrCredentialInUse` mapeado para `409 Conflict` com a lista de fontes dependentes |
| `inventory` (`DefaultInventoryUseCase.SoftDeleteSubnet`) | IPs/dispositivos contidos NÃO são excluídos nem desvinculados de topologia — apenas o registro de sub-rede é marcado | Segue o padrão já existente de `SoftDeleteDevice` |
| `discovery` (`SoftDeleteSource`) | Dispositivos já descobertos permanecem no inventário; `device_discovery_records` referentes ao source permanecem para histórico, e o dispositivo não perde seu status operacional | Nenhuma mutação em `devices` é feita — apenas o `discovery_sources.deleted_at` é setado |

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

`ErrCredentialInUse` é mapeado no controller para `409 Conflict` com corpo `{"error":"CREDENTIAL_IN_USE","dependent_sources":[{"id":"...","name":"..."}]}`.

### 3. Subnet CIDR Editability & Containment Validation
`UpdateSubnetRequest` (novo, `backend/modules/inventory/dto/inventory_dto.go`):
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

Validações em `DefaultInventoryUseCase.UpdateSubnet`:
1. `netip.ParsePrefix(req.CIDR)` — formato e máscara válidos.
2. Se `GatewayIP` informado, `prefix.Contains(gatewayAddr)` deve ser verdadeiro; caso contrário, `ErrGatewayNotContained` → `422 Unprocessable Entity`.
3. `repo.FindOverlappingSubnet(ctx, prefix, excludeID)` — se outra sub-rede ativa colide/duplica o CIDR, `ErrSubnetConflict` → `409 Conflict`.
4. Antes de persistir, o usecase calcula `AffectedDeviceCount` (dispositivos ativos com IP fora do novo prefixo) e retorna esse valor no envelope de resposta para alimentar o diálogo de impacto do frontend.

### 4. Discovery Source Secret Preservation & SSRF Target Guard
`UpdateDiscoverySourceRequest` reaproveita o mesmo formato de `Config map[string]interface{}` de `CreateDiscoverySourceRequest`. A regra de preservação e a guarda de destino residem em `DefaultDiscoveryUseCase.UpdateSource`:

1. **Blank-secret preservation**: para cada chave marcada como sensível no schema do provider (ex.: `password`, `token`, `api_token`), se o valor recebido for string vazia ou ausente, o usecase copia o valor já armazenado do `config_encrypted` decifrado antes de recifrar — o segredo nunca precisa ser retransmitido pelo cliente.
2. **Target Change Guard**: o usecase compara os campos de endereçamento (`host`, `url`, `port`, `socket_path`) do payload recebido com os valores decifrados atuais. Se qualquer um divergir **e** o(s) campo(s) sensível(is) correspondente(s) estiverem em branco, a requisição é rejeitada:
   ```go
   if targetChanged && secretBlank {
       return nil, &ErrSecretRequiredOnTargetChange{Fields: changedTargetFields}
   }
   ```
   Mapeado pelo controller para `HTTP 422 Unprocessable Entity`:
   ```json
   {
     "error": "secret_required_on_target_change",
     "message": "Alterar o host/URL de destino exige reentrada da credencial.",
     "fields": ["api_url"]
   }
   ```
3. **Health check endpoint** — `POST /api/v1/discovery/sources/{id}/health`: não recebe nenhum campo de segredo no corpo (`{}` ou parâmetros de override não sensíveis apenas). O usecase resolve e decifra a configuração já persistida no servidor (idêntico ao `ProviderConfigResolver` do RFC-024) e executa o teste de conectividade do provider, devolvendo `{"status": "ok" | "error", "message": "..."}` sem jamais ecoar o segredo de volta.

### 5. REST API Endpoints & Contracts

| Método | Rota | Novo? | Descrição |
|---|---|---|---|
| `GET` | `/api/v1/subnets/{id}` | Sim | Detalhe de sub-rede para pré-preencher `EditSubnetScreen` |
| `PUT` | `/api/v1/subnets/{id}` | Sim | Atualiza CIDR/gateway/nome; `409`/`422` conforme §3 |
| `DELETE` | `/api/v1/subnets/{id}` | Sim | Soft-delete; corpo de resposta inclui resumo de impacto |
| `PUT` | `/api/v1/discovery/sources/{id}` | Sim | Atualiza fonte; preserva segredo em branco; `422 secret_required_on_target_change` |
| `POST` | `/api/v1/discovery/sources/{id}/health` | Sim | Testa conectividade reutilizando segredo do servidor |
| `DELETE` | `/api/v1/discovery/sources/{id}` | Alterado | Passa de hard delete para soft-delete + cascata lógica de coletores |
| `DELETE` | `/api/v1/credentials/{id}` | Alterado | Passa a retornar `409 Conflict` se referenciada por fonte ativa |
| `PUT` | `/api/v1/devices/{id}` | Existente | Sem alteração de contrato |
| `DELETE` | `/api/v1/devices/{id}` | Existente | Sem alteração de contrato (já soft-delete) |

Exemplo de resposta de exclusão com resumo de impacto (`DELETE /api/v1/subnets/{id}`):
```json
{
  "deleted_id": "b3f1...",
  "impact": {
    "affected_devices": 12,
    "unlinked_topology_edges": 0
  }
}
```

Exemplo de resposta de exclusão de fonte de descoberta (`DELETE /api/v1/discovery/sources/{id}`):
```json
{
  "deleted_id": "9ac2...",
  "impact": {
    "collectors_halted": 2,
    "devices_unlinked": 34
  }
}
```

`routes.go` de ambos os módulos são atualizados:
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
`Route.kt` ganha duas novas rotas, seguindo o padrão de `EditDevice` já existente:
```kotlin
@Serializable
@SerialName("edit_subnet")
data class EditSubnet(val id: String) : Route

@Serializable
@SerialName("edit_discovery_source")
data class EditDiscoverySource(val id: String) : Route
```
`Navigator.kt` e o grafo de navegação (`InfraMapApp.kt` / `MainScaffold.kt`) recebem os `composable` correspondentes, replicando o `NavigatorTest.kt` já existente para `EditDevice` com casos equivalentes para `EditSubnet(id)` e `EditDiscoverySource(id)`.

Novas telas e ViewModels (espelhando `EditDeviceScreen.kt` / `EditDeviceViewModel.kt`):
- `frontend/src/commonMain/kotlin/com/inframap/frontend/ui/subnets/EditSubnetScreen.kt`
- `frontend/src/commonMain/kotlin/com/inframap/frontend/ui/subnets/EditSubnetViewModel.kt`
- `frontend/src/commonMain/kotlin/com/inframap/frontend/ui/discovery/EditDiscoverySourceScreen.kt`
- `frontend/src/commonMain/kotlin/com/inframap/frontend/ui/discovery/EditDiscoverySourceViewModel.kt`

Novos usecases (`domain/usecase/subnet/` e `domain/usecase/discovery/`), espelhando `UpdateDeviceUseCase.kt` / `DeleteDeviceUseCase.kt`:
- `UpdateSubnetUseCase.kt`, `DeleteSubnetUseCase.kt`, `GetSubnetByIdUseCase.kt`
- `UpdateDiscoverySourceUseCase.kt`, `DeleteDiscoverySourceUseCase.kt`, `TestDiscoverySourceHealthUseCase.kt`

### 7. UI Screens & Components
Três componentes novos e compartilhados em `frontend/src/commonMain/kotlin/com/inframap/frontend/designsystem/`, reutilizados pelas três telas de edição (`EditSubnetScreen`, `EditDiscoverySourceScreen`, `EditDeviceScreen`):

- **`DangerZone.kt`**: Card estilizado (borda/tonalidade de erro do `UDS`/M3, `UDSTheme.udsColors.error*` equivalente local) posicionado ao final do formulário de edição, contendo título, descrição do impacto textual e o botão "Excluir [Entidade]".
- **`ImpactConfirmationDialog.kt`**: `AlertDialog` reutilizável parametrizado por `title`, `entityName`, e uma lista de `ImpactLine(label, count)` — renderiza o resumo (`impact.affected_devices`, `impact.collectors_halted`, etc.) devolvido pelo `DELETE`. Confirma somente após interação explícita (sem default de foco automático no botão destrutivo).
- **`RowOverflowMenu.kt`**: `IconButton` com ícone `⋮` (`Icons.Default.MoreVert`) abrindo um `DropdownMenu` com itens "Editar" e "Excluir", usado em `SubnetsScreen.kt`, `DiscoveryListScreen.kt` e `DeviceListScreen.kt`.

Ajustes nas listagens existentes:
- `SubnetsScreen.kt`, `DiscoveryListScreen.kt`, `DeviceListScreen.kt`: linhas passam a usar `m3ClickableCursor()` (guideline #213) navegando para a rota de edição correspondente; a célula final da linha passa a hospedar `RowOverflowMenu`.
- Clique na linha e clique no item "Editar" do menu convergem para a mesma navegação; o item "Excluir" abre `ImpactConfirmationDialog` sem navegar.

### 8. Localization Keys (PT/EN parity)
Novas chaves adicionadas em `frontend/src/commonMain/composeResources/values/strings.xml` (PT, autoritativo) e replicadas em `values-en/strings.xml`, seguindo o padrão de nomenclatura já usado por `edit_device_*`:

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
O par `values-en/strings.xml` recebe traduções equivalentes em inglês para cada chave acima, mantendo paridade 1:1 obrigatória (nenhuma chave PT sem correspondente EN e vice-versa) — invariante já verificado pelo pipeline de qualidade existente.

### 9. Rescanning Soft-Deleted Devices
Em `orchestrator.go` / `DefaultDiscoveryUseCase.persistDiscoveredDevice`, ao casar uma observação com um registro existente (`GetDeviceByID(ctx, id, includeDeleted=true)`), se `device.DeletedAt.Valid == true`:
- A observação **não** é aplicada diretamente ao dispositivo (nenhum `UPDATE devices SET deleted_at = NULL` implícito).
- É criado um registro de staging (`CreateStagingDevice`) com `metadata.previously_deleted = true` e `matched_device_id` apontando para o registro soft-deletado.
- O frontend (`StagingScreen`/fila de aprovação) exibe a etiqueta **"Previamente Deletado"** (`staging_tag_previously_deleted`) no card/linha correspondente.
- A aprovação manual do staging (`ApproveStagingDevice`) é o único caminho que restaura o dispositivo (`deleted_at = NULL`), preservando o controle explícito do operador.

---

## Testing Decisions
- **Backend Unit Tests**:
  - `inventory_usecase_test.go`: `UpdateSubnet` cobrindo containment do gateway, conflito de CIDR (`409`), e cálculo de `AffectedDeviceCount`; `SoftDeleteSubnet` verificando que dispositivos/IPs não são excluídos nem desvinculados.
  - `discovery_usecase_test.go`: `UpdateSource` cobrindo preservação de segredo em branco, guarda de destino (`422 secret_required_on_target_change`) com e sem segredo reenviado, e `SoftDeleteSource` verificando desativação lógica de coletores.
  - `credentials_usecase_test.go`: `DeleteCredential` retornando `409 Conflict` quando referenciada, e sucesso quando não referenciada.
  - `discovery_usecase_test.go` (rescan): observação casando com dispositivo `deleted_at IS NOT NULL` deve gerar staging com `previously_deleted = true` e **nunca** limpar `deleted_at` diretamente.
- **Backend Integration Tests** (`internal/platform/db` + Testcontainers, se aplicável ao repositório):
  - Migração aplicada e revertida (`goose up` / `goose down`) sem erro.
  - Recriação de um CIDR/nome após soft-delete (índice parcial permite duplicidade "lógica" apenas entre um registro ativo e um excluído).
  - Query de listagem (`ListSubnets`, `ListSources`) nunca retorna registros com `deleted_at IS NOT NULL`.
- **Frontend ViewModel Tests** (`commonTest`):
  - `EditSubnetViewModelTest.kt`: estado de erro em `409`/`422`, exibição do aviso de impacto antes da submissão.
  - `EditDiscoverySourceViewModelTest.kt`: campo de segredo permanece vazio ao carregar (nunca populado pela API), disparo do aviso de reentrada de credencial ao editar host/URL, chamada de `TestDiscoverySourceHealthUseCase` sem enviar segredo no payload.
  - `SubnetsViewModelTest.kt` / `DiscoveryListViewModelTest.kt` / `DeviceListViewModelTest.kt`: abertura do `ImpactConfirmationDialog` com os valores corretos de impacto antes de disparar `DELETE`.
- **Frontend Guard Tests** (`jvmTest`):
  - Extensão de `CursorGuardTest` (guideline #213) para cobrir `RowOverflowMenu` e as novas linhas clicáveis das três listagens.
  - Teste de paridade de localização (chave por chave entre `values/strings.xml` e `values-en/strings.xml`) estendido para as novas chaves da seção 8.
- **Coverage Gate**: Manter cobertura de testes $\ge 85\%$ e zero violações em `ktlintCheck`/`detekt` (frontend) e `golangci-lint` (backend).

---

## Out of Scope
- Restauração automática de sub-redes ou fontes de descoberta soft-deletadas via UI (apenas dispositivos possuem fluxo de aprovação/restauração via Staging).
- Exclusão em lote (bulk delete) de múltiplos registros simultaneamente.
- Versionamento/histórico de alterações (audit trail de diffs de campo a campo) além dos eventos já publicados no `eventbus`.
- Purga física (hard delete) definitiva de registros soft-deletados — nenhum job de retenção/expurgo é definido nesta RFC.

---

## Further Notes
Esta especificação consolida as decisões arquiteturais registradas no [ADR-014](./adr/ADR-014-entity-editing-and-soft-delete-architecture.md) e no guideline **#215** de `CONTEXT.md`, e estabelece a base para o fatiamento dos tickets de implementação de edição/soft-delete para Sub-redes, Fontes de Descoberta e reforço de consistência em Dispositivos.
