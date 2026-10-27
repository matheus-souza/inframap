# RFC-026: Multi-Target Discovery Sources Specification

## Status
Proposed

## Problem Statement
Uma Fonte de Descoberta é hoje um plano de coletores (ADR-011) apontado para **um único** bloco CIDR. Uma rede com várias sub-redes que devem ser varridas da mesma forma exige uma fonte por sub-rede:

1. **Configuração duplicada**: cada fonte repete nome, agendamento, coletores e credenciais. Mudar o agendamento ou trocar uma credencial SNMP significa editar N fontes e mantê-las sincronizadas à mão.
2. **Setup automático multiplica fontes**: `AutoSetupCoordinator` cria uma fonte `ICMP Sweep — <cidr>` por interface detectada; um host com três interfaces começa a vida com três fontes quase idênticas.
3. **Contrato de alvo único em toda a pilha**: `config.cidr` (string) no JSON cifrado, `DiscoverySourceResponse.ConfigCIDR`, `collectors.DiscoveryTarget{CIDR}` e o campo único de CIDR em `CreateDiscoverySourceScreen` assumem um alvo por fonte.
4. **Sem vínculo com Sub-redes cadastradas**: o CIDR de uma fonte é texto copiado no momento da criação. Editar o CIDR de uma Sub-rede (RFC-025) não se reflete na fonte, e excluir uma Sub-rede não informa quais fontes ainda a varrem.
5. **Histórico sem alvo**: `discovery_collector_runs` registra uma linha por coletor e não sabe qual bloco foi varrido; com múltiplos alvos não seria possível saber qual sub-rede falhou.

## Solution
Uma Fonte de Descoberta passa a ter uma **lista ordenada de alvos** (`targets`). Cada alvo é um CIDR, opcionalmente vinculado a uma Sub-rede cadastrada. Uma execução varre todos os alvos com o mesmo plano de coletores, agendamento e credenciais.

- A lista é **opcional** quando o plano só contém providers (`collectors.RequiresCIDR`, introduzido na correção de fontes provider-only) e **obrigatória com ao menos um alvo** quando há qualquer varredura de rede.
- Coletores são classificados por **escopo de alvo**: varreduras por bloco rodam uma vez por alvo; coletores de enlace local rodam uma vez filtrando pela união dos alvos; providers rodam uma vez, sem alvo.
- Fontes existentes continuam funcionando sem migração de dados: `config.cidr` é lido como uma lista de um alvo.
- Fontes separadas continuam sendo o modelo correto quando cada bloco precisa de agendamento, credencial ou coletores diferentes.

## User Stories
1. Como operador, quero que uma única fonte varra `10.0.10.0/24`, `10.0.20.0/24` e `10.0.30.0/24` com o mesmo plano, para não manter três fontes idênticas.
2. Como operador, quero escolher alvos entre as Sub-redes já cadastradas, para que uma alteração de CIDR na Sub-rede seja seguida pela fonte.
3. Como operador, quero também informar um CIDR avulso, para varrer um bloco que não cadastrei como Sub-rede.
4. Como operador, quero ver no histórico de execuções qual alvo falhou, para diagnosticar uma sub-rede inacessível sem perder o resultado das demais.
5. Como operador, ao excluir uma Sub-rede, quero ver quais fontes a varrem, para entender o impacto antes de confirmar.
6. Como operador, quero que o setup automático crie **uma** fonte para todas as interfaces detectadas.
7. Como operador de uma fonte com credencial (SNMP), quero ser obrigado a reinformar a credencial ao **adicionar** um alvo, para que ela não seja enviada a hosts que nunca autorizei.

## Implementation Decisions

### 1. Modelo de configuração
`targets` vive no JSON cifrado de configuração da fonte, no mesmo lugar do `cidr` atual:

```json
{
  "targets": [
    { "cidr": "10.0.10.0/24", "subnet_id": "6f1c…" },
    { "cidr": "192.168.50.0/24" }
  ],
  "credential_id": "…"
}
```

- **Leitura compatível**: se `targets` estiver ausente e `cidr` presente, a fonte é lida como `[{ "cidr": <cidr> }]`. Nenhuma migração de dados é necessária.
- **Escrita**: todo `POST`/`PUT` grava `targets` e remove `cidr` do payload (migração preguiçosa no próximo salvamento).
- **Alvo vinculado** (`subnet_id` presente): o CIDR efetivo é resolvido a partir da Sub-rede **no momento da execução**; o `cidr` gravado é apenas um snapshot para exibição. Uma Sub-rede soft-deletada faz o alvo ser ignorado na execução (seção 4).
- **Alvo avulso** (`subnet_id` ausente): o `cidr` gravado é o alvo.

### 2. Validação (backend, `CreateSource` e `UpdateSource`)
| Regra | Erro |
|---|---|
| Plano com varredura e `targets` vazio | `422 error_targets_required` |
| CIDR inválido ou maior que `/16` (`ParseTargetPrefix`) | `422 error_target_invalid` |
| Dois alvos iguais ou sobrepostos na mesma fonte | `422 error_targets_overlap` |
| Soma de hosts de todos os alvos acima de 65 536 (o tamanho de um `/16`, limite atual por alvo) | `422 error_targets_too_large` |
| `subnet_id` inexistente ou soft-deletado | `422 error_target_subnet_not_found` |
| Mais de 32 alvos | `422 error_targets_too_many` |

Alvos sobrepostos são rejeitados, e não deduplicados, para que a contagem de hosts e o histórico por alvo permaneçam inequívocos. Sobreposição **entre fontes diferentes** continua permitida, como hoje.

### 3. Escopo de alvo dos coletores e orquestração
Cada tipo de coletor declara um escopo no registro (`collectors`), ao lado de `RequiresCIDR`:

| Escopo | Coletores | Execução |
|---|---|---|
| `per_target` | `icmp_sweep`, `reverse_dns`, `snmp` | Uma chamada `Collect` por alvo |
| `union` | `mdns`, `arp_sweep` | Uma chamada; consulta única (multicast / tabela ARP local) filtrando respostas pela união dos alvos |
| `none` | `proxmox`, `docker`, `unifi` | Uma chamada, sem alvo |

- `DiscoveryTarget` ganha `Prefixes []netip.Prefix` para o escopo `union`; `CIDR` continua sendo o alvo do escopo `per_target`.
- Os alvos são processados **em sequência**; dentro de um alvo, os coletores continuam em paralelo limitados pelo worker pool atual. Isso mantém a carga de rede de uma fonte multi-alvo igual à de uma fonte de alvo único em cada instante.
- O cancelamento do contexto interrompe o alvo corrente e pula os restantes (registrados como `skipped`).
- O lock por fonte do agendador (AD-036) não muda: uma execução cobre todos os alvos, e um agendamento que dispare durante uma execução longa continua sendo pulado.

### 4. Histórico de execuções e status agregado
- Migração: `ALTER TABLE discovery_collector_runs ADD COLUMN target_cidr CIDR NULL`.
- Escopo `per_target`: uma linha por (coletor, alvo) com `target_cidr` preenchido. Escopos `union` e `none`: uma linha por coletor com `target_cidr` nulo.
- Alvo vinculado a Sub-rede soft-deletada: linha `skipped` com `error_message = "target subnet deleted"`, sem varredura.
- Status da fonte agregado sobre todas as linhas, com a mesma regra atual: todas `success` → `idle`; mistura → `partial`; nenhuma `success` → `error`.

### 5. Guarda de credencial (extensão da RFC-025 §4)
Para fontes com segredo configurado, **adicionar** um alvo ou **alterar** o CIDR de um alvo existente é tratado como mudança de destino (`dto.IsTargetKey`): sem o segredo reenviado, o `PUT` é rejeitado com `422 secret_required_on_target_change`. **Remover** alvos não exige o segredo, pois não expõe a credencial a hosts novos.

### 6. Sub-redes vinculadas
- `GET /api/v1/subnets/{id}/deletion-impact` passa a incluir `discovery_sources: [{ "id", "name" }]` com as fontes ativas que têm um alvo vinculado à Sub-rede.
- Excluir a Sub-rede **não** altera as fontes (semântica *preserve & unlink* do ADR-014): o alvo permanece na lista, é ignorado nas execuções (seção 4) e é sinalizado na tela de edição da fonte para remoção manual.

### 7. Contrato da API (atualiza RFC-008)
- **Request** (`POST`/`PUT /api/v1/discovery/sources`): `config.targets` como na seção 1. `config.cidr` continua aceito e é convertido em um alvo.
- **Response** (`DiscoverySourceResponse`): novo campo `targets: [{ "cidr", "subnet_id", "subnet_name", "subnet_deleted" }]`. `config_cidr` permanece preenchido com o CIDR do primeiro alvo, marcado como **deprecated**, e é removido na versão menor seguinte.
- **Runs** (`GET /api/v1/discovery/sources/{id}/runs`): cada item ganha `target_cidr`.
- `POST /api/v1/discovery/scan` (varredura avulsa com `cidr` no corpo) não muda.

### 8. Frontend
- **Criar/Editar Fonte**: o campo único de CIDR vira um editor de alvos:
  - seleção múltipla das Sub-redes cadastradas (chips);
  - entrada de CIDR avulso com validação local (mesmas regras da seção 2);
  - contador de hosts somados.

  O editor fica oculto quando o plano só tem providers (`requiresCidr`), e o rascunho persistido (`CreateDiscoverySourceDraft`) passa a guardar a lista.
- **Listagem de Fontes**: a coluna CIDR mostra o primeiro alvo e `+N`, com a lista completa em tooltip.
- **Diagnóstico de execução**: as linhas do histórico exibem o alvo (`target_cidr`).
- **Setup automático**: cria uma única fonte "Varredura da rede local" com um alvo por interface detectada. Instalações que já passaram pelo setup não são alteradas.

### 9. Localization Keys (PT/EN parity)
`discovery_targets_label`, `discovery_targets_add_subnet`, `discovery_targets_add_cidr`, `discovery_targets_host_count`, `discovery_targets_more` (`+%1$d`), `discovery_target_subnet_deleted`, `error_targets_required`, `error_target_invalid`, `error_targets_overlap`, `error_targets_too_large`, `error_targets_too_many`, `error_target_subnet_not_found`, `delete_subnet_impact_sources`, `auto_setup_local_network_source_name`.

## Testing Decisions
- **Backend Unit Tests**:
  - Leitura compatível: payload com `cidr` legado vira um alvo; payload com `targets` é lido como está; gravação remove `cidr`.
  - Matriz de validação da seção 2, incluindo sobreposição parcial (`10.0.0.0/23` × `10.0.1.0/24`) e o limite de hosts somados.
  - Orquestração: coletor `per_target` chamado N vezes com o CIDR de cada alvo; `union` chamado uma vez com todos os prefixos; `none` chamado uma vez sem alvo.
  - Status agregado: um alvo com erro e os demais com sucesso resulta em `partial`, com `target_cidr` correto em cada linha.
  - Alvo vinculado a Sub-rede soft-deletada é registrado como `skipped` e não chama nenhum coletor.
  - Guarda de credencial: adicionar alvo sem segredo → `422`; remover alvo sem segredo → `200`.
- **Backend Integration Tests**: migração `target_cidr` aplicada e revertida; `deletion-impact` de Sub-rede lista as fontes vinculadas.
- **Frontend ViewModel Tests**: editor de alvos (adicionar/remover Sub-rede e CIDR avulso, erros de sobreposição e limite), rascunho com lista de alvos sobrevivendo ao F5, editor oculto em plano só de providers.
- **Coverage Gate**: cobertura $\ge 85\%$, zero violações em `golangci-lint`, `ktlintCheck` e `detekt`.

## Out of Scope
- Agendamento, credencial ou conjunto de coletores **por alvo** (use fontes separadas).
- Execução de alvos em paralelo.
- Alvos IPv6.
- Migração automática que funda fontes existentes de alvo único em uma fonte multi-alvo.

## Further Notes
As decisões estruturais estão registradas no [ADR-015](./adr/ADR-015-multi-target-discovery-sources.md). Esta RFC depende da correção que permitiu executar fontes compostas apenas por providers (`collectors.RequiresCIDR`, guideline **#218** de `CONTEXT.md`), sobre a qual a obrigatoriedade de alvos da seção 2 é construída.
