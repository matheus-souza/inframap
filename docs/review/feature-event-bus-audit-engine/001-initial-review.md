# Review — Branch `feature/event-bus-audit-engine`

| Data | 2026-07-24 |
|------|------------|
| RFC de referência | RFC-011, RFC-009, RFC-005, RFC-006, RFC-010 |
| Commits revisados | `3f5fd78` (RFC-011 doc) → `197ab82` (implementação) |
| Escopo | Event Bus in-memory, Crypto Engine AES-256-GCM, Audit Log Subscriber |

---

## Resumo da branch

Esta branch implementa três componentes de plataforma definidos na RFC-011:

1. **`internal/platform/crypto`** — Encriptação AES-256-GCM para segredos de integração
2. **`internal/platform/eventbus`** — Event Bus in-memory com channel buffered e worker pool
3. **`modules/audit`** — Subscriber que persiste domain events na tabela `audit_logs`

Além disso, entrega a migration inicial com todo o schema RFC-006, o API server com health check, configuração sqlc, docker-compose dev, e Makefile atualizado.

---

## 1. Crypto Engine (`internal/platform/crypto`)

### Conformidade com RFC-011 / RFC-009

| Requisito | Status | Observação |
|-----------|--------|------------|
| AES-256-GCM | OK | Usa `crypto/aes` + `cipher.NewGCM` |
| Nonce de 12 bytes (random) | OK | `gcm.NonceSize()` retorna 12; preenchido via `crypto/rand` |
| Chave de 32 bytes via env | PARCIAL | Validação de 32 bytes existe, mas `main.go` não lê `INFRAMAP_SECRET_KEY` |
| Formato `v1:<base64(nonce+ciphertext+tag)>` | OK | Prefixo `v1:` + base64 do `Seal(nonce, nonce, plaintext, nil)` |
| Interface `Encryptor` | OK | Interface definida no mesmo pacote |

### Pontos a corrigir

- **[CRYPTO-01] Decrypt não valida prefixo `v1:`**: `strings.TrimPrefix` remove silenciosamente o prefixo, mas se o ciphertext não tiver o prefixo (ex: `v2:...` ou string arbitrária), ele tenta decodificar mesmo assim. Deveria retornar erro se o prefixo esperado estiver ausente, preparando para versionamento futuro do formato.

- **[CRYPTO-02] `NewAESGCMEncryptor` aceita `string` mas a RFC fala em env var**: Não existe bootstrap que leia `INFRAMAP_SECRET_KEY` e passe para o construtor. Isso vai ficar pendente para o bootstrap module, mas vale registrar como pendência.

- **[CRYPTO-03] Nenhum teste de empty plaintext**: Os testes cobrem encrypt/decrypt, chave inválida, ciphertext inválido e chave errada — bom coverage. Faltam edge cases: plaintext vazio (`[]byte{}`), ciphertext com prefixo `v1:` mas base64 válido com payload curto demais.

### Pontos positivos

- Implementação limpa, sem dependências externas além da stdlib.
- Separação clara entre interface e implementação concreta.
- Testes cobrem os 4 cenários críticos definidos na RFC-011 Seam 1.

---

## 2. Event Bus (`internal/platform/eventbus`)

### Conformidade com RFC-009 / RFC-011

| Requisito | Status | Observação |
|-----------|--------|------------|
| Channel buffer 1000 | OK | Default em `NewInMemoryEventBus` |
| Worker pool configurável (default 5) | PARCIAL | Configurável sim, mas o default no construtor é `1` (mínimo), não `5` |
| Payload por valor (sem ponteiros compartilhados) | OK | `BaseEvent` usa fields unexported, passado por valor |
| Panic recovery por handler | OK | `defer recover()` no dispatch |
| Non-blocking publish (backpressure) | OK | `select` com `default` retornando `ErrBufferFull` |
| Wildcard subscriber (`*`) | OK | `dispatch()` concatena handlers específicos + wildcard |
| Interface `EventBus` com `Close()` | OK | RFC-009 não tinha `Close()`, RFC-011 adiciona |

### Pontos a corrigir

- **[EVENTBUS-01] Default de workers inconsistente com RFC-011**: A RFC-011 especifica "default: 5 goroutines", mas `NewInMemoryEventBus` com `workers < 1` atribui `1`. O construtor deveria ter um default de `5` quando o chamador passa `0` ou valor negativo (ou ter uma constante `DefaultWorkers = 5`).

- **[EVENTBUS-02] Race condition no `Close()`**: A sequência `b.cancel()` seguida de `close(b.eventChan)` pode causar race: um worker que acabou de ler do channel pode estar executando `dispatch()` enquanto o context foi cancelado. Na prática funciona porque `b.wg.Wait()` espera, mas há um cenário teórico: se `cancel()` é chamado e um worker sai pelo `case <-b.ctx.Done()`, ao mesmo tempo outro worker tenta ler do channel já fechado. O `ok` check no `case event, ok := <-b.eventChan` mitiga isso, mas a ordem mais segura seria primeiro fechar o channel (para que workers saiam pelo `!ok`) e depois cancelar o context.

- **[EVENTBUS-03] Erro do handler é silenciosamente ignorado**: `_ = h(b.ctx, event)` descarta o erro retornado pelo handler. A RFC-011 não especifica o que fazer com erros de handler, mas pelo menos um log de warning seria consistente com a filosofia de observabilidade da RFC-005. Isso é relevante especialmente para o audit subscriber, onde uma falha de persist deveria ser logada.

- **[EVENTBUS-04] `Subscribe()` após `Publish()` em cenário de startup**: Não há garantia de ordenação — se `Publish()` for chamado antes de `Subscribe()`, o evento é despachado sem subscribers e perdido silenciosamente. Isso é by-design para um event bus in-memory, mas vale documentar como limitação conhecida.

- **[EVENTBUS-05] Context do `Publish()` é ignorado**: O método recebe `ctx context.Context` mas não o usa — o dispatch usa `b.ctx` (o context interno do bus). Isso significa que deadlines/cancellation do caller não são respeitados. A RFC-009 não menciona isso explicitamente, mas a assinatura sugere que o context deveria ser propagado.

### Pontos positivos

- Defensive copy dos handlers (`append([]EventHandler(nil), ...)`) antes do dispatch — evita race na iteração.
- Separação limpa entre `Subscribe()` (write lock) e `Publish()` (read lock).
- Testes cobrem publish/subscribe, panic recovery, wildcard, e backpressure — os 4 cenários da RFC-011 Seam 2.

---

## 3. Audit Subscriber (`modules/audit`)

### Conformidade com RFC-011

| Requisito | Status | Observação |
|-----------|--------|------------|
| Wildcard subscriber (`*`) | OK | `bus.Subscribe("*", s.HandleEvent)` |
| Persist em `audit_logs` via sqlc | OK | Usa `db.New(s.db).CreateAuditLog()` |
| Mapping: `id` = `EventID()` (UUIDv7) | OK | Parseia UUID do event |
| Mapping: `event_type` = `EventType()` | DIVERGENTE | Campo mapeado para `Action`, não `EventType` (ver abaixo) |
| Mapping: `payload` = JSONB `Payload()` | OK | `json.Marshal(event.Payload())` → campo `Changes` |
| Mapping: `created_at` = `OccurredAt()` | OK | Usa `pgtype.Timestamptz` |

### Pontos a corrigir

- **[AUDIT-01] Mapping de campos não segue a RFC-006**: A tabela `audit_logs` tem campos semânticos (`actor_name`, `action`, `resource_type`, `resource_id`, `changes`). O subscriber mapeia:
  - `Action` ← `event.EventType()` (OK semanticamente)
  - `ResourceType` ← hardcoded `"system_event"` (deveria ser extraído do payload ou do event type, ex: `device` para `device.created`)
  - `ActorName` ← hardcoded `"system"` (OK para eventos de sistema, mas não escalável para eventos de usuário)
  - `Changes` ← payload inteiro (RFC-006 especifica `{ "before": {...}, "after": {...} }`, não o payload raw)

  Isso não é um bug agora, mas vai precisar de refactor quando eventos reais de CRUD forem implementados. Vale registrar como debt técnico.

- **[AUDIT-02] Fallback silencioso em `uuid.Parse` e `json.Marshal`**: Se o EventID não for um UUID válido, gera um `uuid.New()` (v4 em vez de v7). Se o payload falha ao serializar, usa `{}`. Ambos os fallbacks silenciam erros que poderiam indicar bugs no publisher. Pelo menos um log de warning nesses caminhos seria adequado.

- **[AUDIT-03] Teste usa mock que não valida a query SQL**: O `mockDBTX` retorna sucesso incondicional. O teste verifica que o subscriber não crasha, mas não valida que os parâmetros corretos foram passados ao banco. Como a RFC-011 Seam 3 pede "integration test asserting published DomainEvent is asynchronously stored in PostgreSQL audit_logs", o teste atual é mais um smoke test do que um integration test. Faltaria:
  - Um teste com banco real (testcontainers ou banco dev local), ou
  - Um mock mais sofisticado que capture e valide os argumentos passados.

- **[AUDIT-04] Teste depende de `time.Sleep(50ms)` para sincronização**: Isso é frágil — em CI com carga ou em máquinas lentas pode falhar intermitentemente. Melhor usar um callback/channel para sinalizar que o handler executou.

### Pontos positivos

- Implementação compacta e focada — o subscriber faz exatamente uma coisa.
- Registro via `bus.Subscribe("*", ...)` segue o padrão wildcard da RFC.
- Instanciação de `db.Queries` por chamada (`db.New(s.db)`) é thread-safe.

---

## 4. Migration (`20260722000001_initial_schema.sql`)

### Conformidade com RFC-006

| Requisito | Status | Observação |
|-----------|--------|------------|
| 15 tabelas conforme RFC-006 | OK | Todas as 15 tabelas presentes |
| UUIDs como PK | OK | Todos usam `UUID PRIMARY KEY` |
| `created_at` / `updated_at` timestamps | OK | Presentes em todas as tabelas aplicáveis |
| Soft delete (`deleted_at`) em `devices` | OK | Campo presente |
| Tipos nativos PostgreSQL (`inet`, `cidr`, `macaddr`) | OK | |
| Goose Up / Down annotations | OK | |
| Trigger `update_updated_at_column()` | OK | Aplicado em todas as tabelas com `updated_at` |
| Indexes conforme RFC-006 | PARCIAL | Ver abaixo |

### Pontos a corrigir

- **[MIGRATION-01] Indexes ausentes em relação à RFC-006**: A RFC-006 define indexes para `users(username)`, `users(email)`, `roles(name)`, `user_sessions(token_hash)`, e `discovery_records(discovery_source_id)`. A migration não inclui esses indexes. Como `username` e `email` já têm constraint `UNIQUE` (que cria index implícito no PostgreSQL), os dois primeiros são aceitáveis. Mas `roles(name)` (também UNIQUE), `user_sessions(token_hash)` (também UNIQUE), e `discovery_records(discovery_source_id)` merecem verificação — o index explícito de `discovery_records(discovery_source_id)` está de fato ausente na migration.

- **[MIGRATION-02] Migration monolítica com todas as 15 tabelas**: Embora funcione para o schema inicial, a RFC-006 e RFC-010 sugerem migrations incrementais. Uma única migration com 326 linhas dificulta rollbacks parciais. Se precisar fazer rollback, derruba todas as 15 tabelas de uma vez. Para uma initial migration isso é aceitável, mas vale considerar se futuras adições seguirão o padrão incremental.

- **[MIGRATION-03] `uuid-ossp` extension vs `pgcrypto`**: A migration habilita `uuid-ossp`, mas o código Go gera UUIDs application-side via `google/uuid`. A extension pode não ser necessária. Se for mantida, considerar que em PostgreSQL 17+ `gen_random_uuid()` já está built-in sem extensions.

---

## 5. API Server (`cmd/api/main.go`)

### Pontos a corrigir

- **[API-01] Versão hardcoded `v1.0.0-rc.1`**: O health endpoint retorna versão fixa. A RFC-010 e o `.releaserc.json` indicam que o projeto usa semantic-release. A versão deveria ser injetada via `ldflags` no build (`-ldflags "-X main.version=$VERSION"`).

- **[API-02] Nenhum bootstrap de componentes RFC-011**: O `main.go` não instancia o EventBus, Crypto Engine, nem o Audit Subscriber. São componentes implementados mas não conectados ao lifecycle da aplicação. Isso é esperado nesta fase (scaffold), mas deveria estar documentado como próximo passo.

- **[API-03] Sem graceful shutdown**: O server não trata `SIGTERM`/`SIGINT` para shutdown gracioso. Importante para Docker (que envia SIGTERM no stop) e para garantir que o EventBus faça `Close()` antes de encerrar.

### Pontos positivos

- Timeouts configurados no `http.Server` (read/write/idle) — boa prática de segurança.
- Porta configurável via env var com default `8055` (conforme ADR-004).
- Testes cobrem handler, porta default e porta custom.

---

## 6. sqlc Configuration & Generated Code

### Pontos a corrigir

- **[SQLC-01] Apenas 2 queries definidas**: `health.sql` e `audit.sql`. Para as 15 tabelas do schema, não há queries de CRUD para nenhuma entidade core. Isso é esperado nesta fase, mas o `audit.sql` poderia já incluir uma query de listagem/paginação de audit logs (útil para a API de audit futura).

- **[SQLC-02] Override de `inet` mapeando para `netip.Addr` sem ponteiro**: Campos nullable `inet` (como `ip_address` em `devices`) são mapeados para `*netip.Addr` (ponteiro), o que é correto. Mas campos non-nullable `inet` (como `address` em `ip_addresses`) são mapeados para `netip.Addr` (valor) — OK. O override global `inet → netip.Addr` funciona porque o sqlc detecta nullability. Sem issues aqui.

---

## 7. Infraestrutura (Makefile, Docker Compose, CI)

### Conformidade com RFC-010

| Requisito | Status | Observação |
|-----------|--------|------------|
| `make dev` funcional | OK | Sobe Postgres + roda backend |
| `make verify` = generate + lint + test + build | OK | Targets encadeados corretamente |
| `make test` com `-race` | OK | Flag de race detector presente |
| PostgreSQL 17+ | OK | `postgres:17-alpine` no compose |
| `mise` como toolchain manager | OK | Makefile detecta e usa mise |

### Pontos a corrigir

- **[INFRA-01] `test-coverage` só cobre `./cmd/...`**: O target `test-coverage` no Makefile usa `./cmd/...` em vez de `./...`. Isso significa que os testes de `internal/platform/crypto`, `internal/platform/eventbus` e `modules/audit` ficam fora do coverage report. Deveria ser `./...` para consistência com o target `test`.

- **[INFRA-02] `docker-compose` deprecated**: Os comandos no Makefile usam `docker-compose` (com hífen) em vez de `docker compose` (subcomando do Docker CLI moderno). Em Docker 25+, `docker-compose` é um legacy plugin.

---

## 8. BaseEvent (`eventbus/event.go`)

### Pontos a corrigir

- **[EVENT-01] `uuid.Must(uuid.NewV7())` panics se UUIDv7 falhar**: `uuid.NewV7()` pode retornar erro se a source de entropia falhar. `uuid.Must` converteria isso em panic. Embora extremamente improvável, num contexto de alta carga ou entropy starvation (container sem `/dev/urandom`), isso crasharia o caller. Considerar usar `uuid.New()` (v4) como fallback, ou aceitar o panic como comportamento desejado.

- **[EVENT-02] `time.Now().UTC()` vs clock injetável**: Para testabilidade, o `NewBaseEvent` deveria aceitar opcionalmente um timestamp ou um clock interface. Isso facilitaria testes determinísticos sem depender do relógio do sistema. Menor prioridade.

---

## Resumo dos achados

### Bloqueadores (devem ser corrigidos antes do merge)

Nenhum bloqueador hard encontrado. A implementação está funcional e alinhada com as RFCs.

### Alta prioridade

| ID | Componente | Descrição |
|----|------------|-----------|
| EVENTBUS-01 | eventbus | Default de workers deveria ser 5, não 1 |
| EVENTBUS-03 | eventbus | Erros de handler silenciosamente ignorados |
| AUDIT-03 | audit | Teste não valida persistência real (não atende Seam 3 da RFC-011) |
| AUDIT-04 | audit | Sincronização por `time.Sleep` é frágil |
| INFRA-01 | Makefile | Coverage report exclui packages de platform e modules |

### Média prioridade

| ID | Componente | Descrição |
|----|------------|-----------|
| CRYPTO-01 | crypto | `Decrypt` não valida presença do prefixo `v1:` |
| EVENTBUS-02 | eventbus | Ordem de close channel/cancel context poderia ser mais segura |
| EVENTBUS-05 | eventbus | Context do Publish é ignorado no dispatch |
| AUDIT-01 | audit | Mapping de campos diverge do formato `before/after` da RFC-006 |
| AUDIT-02 | audit | Fallbacks silenciosos em parse de UUID e serialização |
| API-01 | main.go | Versão hardcoded, deveria usar ldflags |
| API-03 | main.go | Sem graceful shutdown (relevante para Docker/EventBus) |
| MIGRATION-01 | migration | Index de `discovery_records(discovery_source_id)` ausente |

### Baixa prioridade / Debt técnico

| ID | Componente | Descrição |
|----|------------|-----------|
| CRYPTO-02 | crypto | Bootstrap de `INFRAMAP_SECRET_KEY` pendente |
| CRYPTO-03 | crypto | Testes de edge case (empty plaintext) ausentes |
| EVENTBUS-04 | eventbus | Documentar limitação de eventos pré-subscribe |
| EVENT-01 | event | `uuid.Must` pode panic em entropy starvation |
| EVENT-02 | event | Clock não injetável dificulta testes determinísticos |
| API-02 | main.go | Componentes RFC-011 implementados mas não bootstrapped |
| SQLC-01 | sqlc | Queries CRUD pendentes para entidades core |
| MIGRATION-02 | migration | Migration monolítica (aceitável para initial) |
| MIGRATION-03 | migration | Extension `uuid-ossp` possivelmente desnecessária |
| INFRA-02 | Makefile | `docker-compose` (hífen) é legacy |

---

## Conclusão

A branch entrega com qualidade os três componentes definidos na RFC-011. A implementação está limpa, idiomática em Go, e com boa cobertura de testes para o escopo proposto. Os pontos levantados são majoritariamente melhorias incrementais e alinhamentos mais rigorosos com as RFCs — nenhum é um bloqueador para merge, mas os itens de alta prioridade (especialmente o default de workers, logging de erros do handler, e o coverage do Makefile) deveriam ser endereçados antes ou logo após o merge.
