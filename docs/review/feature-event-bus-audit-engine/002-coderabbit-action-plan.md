# Plano de Correções — CodeRabbit Review (PR #14)

| Data | 2026-07-24 |
|------|------------|
| Commit base da análise | `70b898d` (já contém fix parcial) |
| Status geral | 3 de 13 findings corrigidos; 10 pendentes |

---

## Legenda de status

| Símbolo | Significado |
|---------|-------------|
| DONE | Já corrigido no commit `70b898d` |
| TODO | Pendente — será implementado neste ciclo |
| BACKLOG | Adiado para iteração futura (escopo grande demais ou design a definir) |

---

## Findings de CÓDIGO

### 1. [DONE] Shutdown race — workers encerravam prematuramente
- **Arquivo**: `backend/internal/platform/eventbus/eventbus.go`
- **Severidade**: Major
- **O que era**: `Close()` chamava `cancel()` antes de `close(eventChan)`, causando perda de eventos em drenagem.
- **Fix aplicado**: `startWorkers` usa `for range eventChan`; `Close()` faz `close → Wait → cancel`.

### 2. [DONE] Backpressure sem log de aviso
- **Arquivo**: `backend/internal/platform/eventbus/eventbus.go:116`
- **Severidade**: Minor
- **Fix aplicado**: `log.Printf` adicionado antes de retornar `ErrBufferFull`.

### 3. [DONE] `json.Marshal` fallback silencioso no audit subscriber
- **Arquivo**: `backend/modules/audit/subscriber.go:35`
- **Severidade**: Minor
- **Fix aplicado**: `log.Printf` adicionado no path de fallback `"{}"`.

### 4. [TODO] `Publish` ignora o parâmetro `ctx`
- **Arquivo**: `backend/internal/platform/eventbus/eventbus.go:104`
- **Severidade**: Trivial
- **Ação**: Verificar `ctx.Err()` no início de `Publish`, retornando o erro do contexto quando cancelado/expirado. Manter comportamento atual para `ErrBusClosed` e envio não-bloqueante.

### 5. [TODO] `gcm.Open` não wrapa sentinel `ErrInvalidCiphertext`
- **Arquivo**: `backend/internal/platform/crypto/encryptor.go:84-85`
- **Severidade**: Trivial
- **Ação**: Trocar `fmt.Errorf("decryption failed: %w", err)` por `fmt.Errorf("%w: %v", ErrInvalidCiphertext, err)`. Garantir que `errors.Is(err, ErrInvalidCiphertext)` funcione para todos os cenários.

### 6. [TODO] `db.New(s.db)` reconstruído a cada evento
- **Arquivo**: `backend/modules/audit/subscriber.go:39`
- **Severidade**: Trivial (performance)
- **Ação**: Armazenar `*db.Queries` no struct `Subscriber`, inicializar em `NewSubscriber`, reutilizar em `HandleEvent`.

### 7. [TODO] `Payload()` sem documentação de responsabilidade do chamador
- **Arquivo**: `backend/internal/platform/eventbus/event.go:25`
- **Severidade**: Trivial
- **Ação**: Adicionar comentário em `NewBaseEvent` documentando que o payload é armazenado por referência e não deve ser mutado após publicação.

### 8. [BACKLOG] Retry/dead-letter para falhas de persistência de auditoria
- **Arquivo**: `backend/modules/audit/subscriber.go` + `eventbus.go`
- **Severidade**: Trivial (consideração futura)
- **Razão do adiamento**: Requer design de retry queue/dead-letter que é escopo de uma RFC própria. O comportamento atual (log do erro) é aceitável para MVP.

---

## Findings de TESTES

### 9. [TODO] Esperas ilimitadas (`wg.Wait()`) nos testes do eventbus
- **Arquivo**: `backend/internal/platform/eventbus/eventbus_test.go` — linhas 37, 63, 85
- **Severidade**: Minor
- **Ação**: Substituir `wg.Wait()` por canal de conclusão + `select` com `time.After(2 * time.Second)`. Cada timeout deve produzir falha diagnóstica (`t.Fatal`).

### 10. [TODO] Teste de overflow usa `time.Sleep(10ms)` não-determinístico
- **Arquivo**: `backend/internal/platform/eventbus/eventbus_test.go:107`
- **Severidade**: Minor
- **Ação**: No handler do teste, sinalizar entrada via canal (`handlerStarted`). Aguardar esse sinal antes de publicar o segundo evento. Assegurar que os dois primeiros `Publish` retornem `nil`.

---

## Findings de DOCUMENTAÇÃO (RFC-011)

### 11. [TODO] Headings Markdown pulam nível (`#` → `###`)
- **Arquivo**: `docs/RFC-011-event-bus-audit-crypto-engine.md` — linhas 22, 41, 77
- **Severidade**: Trivial
- **Ação**: Alterar `###` para `##` nas subseções de Requirements e Package Interface, corrigindo hierarquia MD001.

### 12. [TODO] Key Source não define falha obrigatória fora de dev
- **Arquivo**: `docs/RFC-011-event-bus-audit-crypto-engine.md:24`
- **Severidade**: Major (segurança)
- **Ação**: Documentar que `INFRAMAP_SECRET_KEY` ausente DEVE causar falha em qualquer ambiente que não seja `development`. Definir como o modo dev é detectado (ex: `INFRAMAP_ENV=development`). Proibir fallback efêmero em produção.

### 13. [TODO] Backpressure — documentar descarte e `ErrBufferFull`
- **Arquivo**: `docs/RFC-011-event-bus-audit-crypto-engine.md:46`
- **Severidade**: Major
- **Ação**: Explicitar na RFC que `Publish` descarta o evento no `default` do canal não-bloqueante, registra log de aviso e retorna `ErrBufferFull` ao chamador.

### 14. [TODO] Payload Semantics — definir estratégia de imutabilidade
- **Arquivo**: `docs/RFC-011-event-bus-audit-crypto-engine.md:44`
- **Severidade**: Major
- **Ação**: Documentar que `BaseEvent.Payload()` retorna referência sem cópia defensiva. Definir a estratégia: publishers devem passar structs por valor sem referências internas mutáveis. Listar a responsabilidade do chamador.

### 15. [TODO] `Close()` — documentar contrato de graceful shutdown
- **Arquivo**: `docs/RFC-011-event-bus-audit-crypto-engine.md:69`
- **Severidade**: Major
- **Ação**: Documentar que `Close()` rejeita novos `Publish`, drena eventos enfileirados, aguarda conclusão do worker pool e só então cancela o contexto interno.

---

## Ordem de implementação sugerida

| Fase | Items | Justificativa |
|------|-------|---------------|
| **1 — RFC fixes** | #11, #12, #13, #14, #15 | Documentação primeiro, alinha a spec antes do código |
| **2 — Code fixes** | #4, #5, #6, #7 | Pequenas correções de código alinhadas com a RFC atualizada |
| **3 — Test fixes** | #9, #10 | Testes mais robustos e determinísticos |
