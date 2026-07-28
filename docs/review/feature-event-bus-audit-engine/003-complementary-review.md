# Review Complementar — Arquivos não cobertos pelo CodeRabbit (PR #14)

| Data | 2026-07-25 |
|------|------------|
| Revisor | Claude (review manual) |
| Escopo | Todos os arquivos do diff que o CodeRabbit não cobriu com comentários inline |
| Branch | `feature/event-bus-audit-engine` (analisado a partir de `feature/coverage-policy-rfc015`) |

---

## Legenda de severidade

| Símbolo | Significado |
|---------|-------------|
| MAJOR | Bug, vulnerabilidade ou problema arquitetural que precisa de correção |
| MINOR | Melhoria importante mas sem risco imediato |
| TRIVIAL | Nitpick ou melhoria cosmética |

---

## API Server (`backend/cmd/api/`)

### F1. [MINOR] Shutdown não fecha EventBus antes do DB pool
- **Arquivo**: `backend/internal/bootstrap/app.go:142-149`
- **Problema**: `Close()` fecha o EventBus e depois o DB pool, o que está correto em ordem. Porém, `main.go:62` chama `server.Shutdown()` mas o `defer app.Close()` na linha 34 executa depois — se o `server.Shutdown` demorar mais que o timeout de 5s, o `log.Printf` executa mas o servidor é abandonado sem garantia de que o EventBus drenou os eventos pendentes antes do pool ser fechado.
- **Ação**: O EventBus deveria ser fechado **após** o `server.Shutdown` mas **antes** de fechar o DB pool. Considerar fechar explicitamente em sequência no bloco de shutdown de `main.go` em vez de depender do `defer`.

### F2. [MINOR] `main.go` usa `log.Fatalf` dentro de goroutine
- **Arquivo**: `backend/cmd/api/main.go:53`
- **Problema**: `log.Fatalf` chama `os.Exit(1)`, o que mata o processo imediatamente sem executar defers (incluindo `app.Close()`). Se `ListenAndServe` falhar por porta ocupada, o DB pool e EventBus ficam abertos.
- **Ação**: Trocar por envio de erro via canal para a goroutine principal, permitindo shutdown graceful.

### F3. [TRIVIAL] Test de health usa `DatabaseURL` inválido mas espera status 200
- **Arquivo**: `backend/cmd/api/main_test.go:16`
- **Problema**: O teste passa `postgres://invalid:invalid@localhost:5432/invalid` como URL. Isso funciona porque `bootstrap.New` faz apenas `log.Warn` no ping falho e continua. O teste valida o happy path do health endpoint mas não verifica se o DB está degraded — o endpoint retorna `"ok"` mesmo com o banco inacessível.
- **Ação**: Considerar retornar `"degraded"` no health quando o ping do DB falha, ou ao menos documentar que o health check não verifica conectividade real.

---

## Crypto Engine (`backend/internal/platform/crypto/`)

### F4. [MINOR] `gcm.Open` erro não wrapa sentinel `ErrInvalidCiphertext`
- **Arquivo**: `backend/internal/platform/crypto/encryptor.go:94`
- **Problema**: Já identificado pelo CodeRabbit (item #5 do plano anterior). `errors.Is(err, ErrInvalidCiphertext)` não funciona para falhas de autenticação GCM.
- **Status**: Já no plano — mantido aqui para rastreabilidade.

### F5. [MINOR] Testes crypto não verificam com `errors.Is` os sentinels
- **Arquivo**: `backend/internal/platform/crypto/encryptor_test.go`
- **Problema**: `TestAESGCMEncryptor_InvalidCiphertextFormat` e `TestAESGCMEncryptor_WrongKeyDecryption` apenas verificam `err == nil` mas não usam `errors.Is(err, ErrInvalidCiphertext)` ou `errors.Is(err, ErrInvalidKeyLength)`. Quando o F4 for corrigido, esses testes precisam validar o sentinel correto.
- **Ação**: Adicionar asserções com `errors.Is` em todos os testes de erro do crypto.

### F6. [TRIVIAL] Falta teste para plaintext vazio
- **Arquivo**: `backend/internal/platform/crypto/encryptor_test.go`
- **Problema**: Não há teste de `Encrypt([]byte{})` / `Encrypt(nil)`. AES-GCM suporta plaintext vazio, mas vale documentar o comportamento.
- **Ação**: Adicionar caso de teste para plaintext vazio.

### F7. [TRIVIAL] Falta teste para ciphertext com prefixo `v1:` mas base64 válido com dados corrompidos
- **Arquivo**: `backend/internal/platform/crypto/encryptor_test.go`
- **Problema**: O teste `InvalidCiphertextFormat` usa input sem prefixo `v1:`. Não há teste para `v1:<base64_válido_mas_adulterado>` — que é o cenário de tampering real.
- **Ação**: Adicionar teste que encripta, modifica um byte do ciphertext, e verifica que Decrypt falha.

---

## Audit Subscriber Tests (`backend/modules/audit/`)

### F8. [MINOR] Mock `QueryRow` retorna `mockRow` que sempre faz `Scan` com sucesso
- **Arquivo**: `backend/modules/audit/subscriber_test.go:35-36`
- **Problema**: `mockRow.Scan()` sempre retorna `nil`, então o teste nunca valida o cenário de falha de persistência. O `HandleEvent` retorna `fmt.Errorf("failed to insert audit log: %w", err)` mas esse path nunca é exercitado.
- **Ação**: Adicionar teste com mock que retorna erro no `Scan` e verificar que `HandleEvent` propaga o erro.

### F9. [MINOR] Teste usa `wg.Wait()` sem timeout
- **Arquivo**: `backend/modules/audit/subscriber_test.go:72`
- **Problema**: Mesmo padrão identificado pelo CodeRabbit nos testes do eventbus. Se o handler nunca executar, o teste trava indefinidamente.
- **Ação**: Substituir por `select` com `time.After`.

---

## Migration SQL (`backend/migrations/`)

### ~~F10.~~ [DESCARTADO] `models.go` gera struct `DeviceStaging`
- **Motivo do descarte**: A tabela `device_staging` existe na migration `20260724000001_create_device_staging.sql` (RFC-014), adicionada em outra feature branch. Falso positivo.

### F11. [TRIVIAL] Migration `Down` não remove extensão `uuid-ossp`
- **Arquivo**: `backend/migrations/20260722000001_initial_schema.sql:326`
- **Problema**: O `Down` dropa tabelas e a function, mas não faz `DROP EXTENSION IF EXISTS "uuid-ossp"`. Isso é intencional (extensões são compartilhadas no DB), mas vale documentar a decisão.
- **Ação**: Nenhuma ação necessária — comportamento correto. Apenas registrado.

### F12. [TRIVIAL] `audit_logs.resource_id` aceita NULL sem FK
- **Arquivo**: `backend/migrations/20260722000001_initial_schema.sql:300`
- **Problema**: `resource_id UUID` não tem `REFERENCES` — é um UUID solto sem integridade referencial. Isso é intencional para logs de auditoria polimórficos (o recurso pode ser de qualquer tabela), mas combinado com `resource_type VARCHAR(64)` cria um pattern "polymorphic association" que não tem garantia de integridade.
- **Ação**: Aceitável para audit logs. Registrado como decisão de design.

---

## CI/CD Workflows (`.github/workflows/`)

### F13. [MAJOR] Actions não pinadas por SHA — vulnerável a tag hijacking
- **Arquivos**: Todos os workflows
- **Problema**: Actions usam tags mutáveis (`@v4`, `@v5`, `@v6`, `@v2`, `@v3`) em vez de SHA pinado. Um atacante que comprometa o repositório de uma action pode injetar código malicioso alterando a tag.
  - `actions/checkout@v4`
  - `actions/setup-go@v5`
  - `actions/setup-node@v4`
  - `golangci/golangci-lint-action@v6`
  - `gitleaks/gitleaks-action@v2`
  - `codecov/codecov-action@v5`
  - `marocchino/sticky-pull-request-comment@v2`
  - `github/codeql-action/init@v3`
  - `github/codeql-action/analyze@v3`
- **Ação**: Pinar por SHA completo com comentário de tag. Ex: `actions/checkout@b4ffde65f46336ab88eb53be808477a3936bae11 # v4.1.1`. O Dependabot já está configurado para atualizar `github-actions`, o que mantém os SHAs atualizados.

### F14. [MINOR] Semgrep usa imagem `semgrep/semgrep` sem tag de versão
- **Arquivo**: `.github/workflows/semgrep.yml:14`
- **Problema**: `image: semgrep/semgrep` usa `:latest` implícito. Uma atualização breaking do Semgrep pode quebrar o CI sem aviso.
- **Ação**: Pinar uma versão específica (ex: `semgrep/semgrep:1.67.0`).

### F15. [MINOR] CI instala ferramentas via `go install` sem versão pinada em alguns casos
- **Arquivo**: `.github/workflows/ci.yml:67`
- **Problema**: `go install github.com/pressly/goose/v3/cmd/goose@latest` usa `@latest`. Se uma versão nova do goose mudar o formato de migrations, o CI quebra. O `govulncheck` na linha 112 também usa `@latest`.
- **Ação**: Pinar versões (alinhar com `.mise.toml`: goose `3.24.1`).

### F16. [MINOR] `docker-compose.dev.yml` expõe PostgreSQL na porta 5432 sem bind em localhost
- **Arquivo**: `docker-compose.dev.yml:11`
- **Problema**: `ports: "5432:5432"` faz bind em `0.0.0.0`, expondo o PostgreSQL na rede local. Em redes públicas (cafés, coworking), qualquer pessoa na mesma rede pode acessar o banco com as credenciais hardcoded.
- **Ação**: Trocar para `"127.0.0.1:5432:5432"`.

### F17. [TRIVIAL] `release.yml` instala plugins semantic-release globalmente
- **Arquivo**: `.github/workflows/release.yml:29`
- **Problema**: `npm install -g` sem `--prefer-offline` e sem lockfile. Builds não são reprodutíveis — versões de plugins podem variar entre runs.
- **Ação**: Considerar usar um `package.json` com lockfile, ou pinar versões nos `npm install`.

---

## Makefile

### F18. [TRIVIAL] `make verify` depende de `generate` mas `generate` não é prerequisito de `test`
- **Arquivo**: `Makefile:62`
- **Problema**: `verify: generate lint test build` roda generate antes dos testes. Mas `make test` isolado não roda `generate` — se alguém alterar um `.sql` e rodar `make test` direto, os testes podem usar código sqlc desatualizado.
- **Ação**: Considerar adicionar `generate` como dependência de `test`, ou documentar que `make verify` é o caminho recomendado.

---

## Resumo de Findings

| Severidade | Qtd | IDs |
|------------|-----|-----|
| MAJOR | 1 | F13 |
| MINOR | 8 | F1, F2, F4, F5, F8, F9, F14, F15, F16 |
| TRIVIAL | 6 | F3, F6, F7, F11, F12, F17, F18 |
| DESCARTADO | 1 | F10 |
| **Total válidos** | **15** | |

---

## Ordem de implementação sugerida

| Fase | Items | Justificativa |
|------|-------|---------------|
| **1 — Segurança** | F13, F16 | Actions sem SHA e PostgreSQL exposto são riscos reais |
| **2 — Correções de código** | F1, F2, F4, F5 | Shutdown ordering e error handling |
| **3 — Testes** | F8, F9, F6, F7 | Cobertura de cenários de falha e edge cases |
| **4 — CI hardening** | F14, F15, F17 | Reprodutibilidade e pinning de versões |
| **5 — Cosmético** | F3, F11, F12, F18 | Melhorias opcionais |
