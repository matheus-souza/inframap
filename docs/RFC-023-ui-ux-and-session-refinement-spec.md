# RFC-023: UI/UX Refinement, Progressive Dashboard & Session Lifecycle Specification

## Status
Approved / Ready for Implementation

## Problem Statement
Durante o uso contínuo da aplicação e testes visuais do InfraMap, foram identificados cinco pontos de fricção na experiência do usuário e de ciclo de vida de sessão:
1. **NavRail**: No estado expandido, rótulos como "Fontes de Descoberta" sofriam quebra de linha ao serem selecionados devido ao deslocamento causado pelo indicador ativo e largura restrita de 200dp. A transição de abertura/fechamento não era animada e a altura dos itens no modo slim (56dp) era maior que no modo expandido (44dp).
2. **Focus Ring Indesejado**: Ao interagir com o canvas Compose WASM, o navegador exibia uma borda azul ao redor do viewport inteiro.
3. **Seleção de Texto**: Usuários não conseguiam selecionar e copiar informações textuais técnicas (endereços IP, blocos CIDR, endereços MAC, identificadores).
4. **Dashboard**: Cards de KPI não eram interativos/clicáveis e o banner de boas-vindas sugeria cadastrar sub-redes mesmo quando o banco de dados já possuía sub-redes configuradas.
5. **Sessão Expirada (401)**: Quando o token/sessão expirava, a aplicação mantinha o usuário na tela atual com dados defasados e um banner de erro, em vez de redirecioná-lo imediatamente para a tela de autenticação (`/login`).

---

## Solution
Implementar uma suíte coordenada de refinamentos de UI/UX, transições e ciclo de vida de autenticação:
- **NavRail**: Largura expandida de 220dp, animação fluida (250ms), altura padronizada de 44dp para todos os itens, indicador de seleção sem deslocamento horizontal do ícone e truncamento seguro com elipse.
- **Canvas Focus Ring & Seleção**: Remoção via CSS de outlines no canvas e encapsulamento do conteúdo principal em `SelectionContainer`.
- **Dashboard Interativo & Progressivo**: Navegação direta nos cards de métricas e máquina de estados progressiva para sugestões na Dashboard.
- **Sessão Expirada (401)**: Interceptador global no `ApiClient` com redirecionamento imediato para `Route.Login`.

---

## User Stories

1. Como operador de rede, quero que os itens do menu lateral (NavRail) mantenham seu texto em uma única linha ao serem selecionados, para que a leitura dos menus permaneça limpa e sem quebras visuais.
2. Como usuário do sistema, quero ver uma animação suave ao expandir e recolher o menu lateral, para que a interface pareça polida e moderna.
3. Como usuário do sistema, quero que os itens do menu lateral tenham o mesmo espaçamento vertical tanto quando o menu está aberto quanto quando está fechado.
4. Como usuário do sistema, quero clicar em qualquer parte da aplicação sem que uma borda azul de foco envolva toda a janela do navegador.
5. Como administrador de infraestrutura, quero selecionar e copiar textos da tela (como IPs, MACs, nomes de host e status) usando o mouse e atalhos de teclado (Ctrl+C / Cmd+C).
6. Como operador, quero clicar no card de "Dispositivos Ativos" na Dashboard e ser direcionado diretamente para a tela de inventário de dispositivos.
7. Como operador, quero clicar no card de "Dispositivos em Staging" na Dashboard e ser direcionado diretamente para a fila de staging.
8. Como operador, quero clicar no card de "Fontes de Descoberta" na Dashboard e ser direcionado para a listagem de fontes de descoberta.
9. Como novo usuário com sub-redes já cadastradas mas sem fontes de descoberta, quero que a Dashboard sugira a configuração de uma fonte de descoberta em vez de me pedir para cadastrar uma sub-rede novamente.
10. Como novo usuário com fontes de descoberta cadastradas mas sem dispositivos escaneados, quero que a Dashboard sugira o disparo da primeira varredura.
11. Como usuário com inventário populado, quero que o banner de boas-vindas na Dashboard desapareça automaticamente para dar destaque total às métricas operacionais.
12. Como usuário cuja sessão expirou ou token foi revogado, quero ser redirecionado imediatamente para a tela de Login ao tentar carregar dados, para que eu saiba que preciso autenticar novamente e não fique olhando para informações desatualizadas.

---

## Implementation Decisions

### 1. NavRail Component (`NavRail.kt`)
- `railWidth` gerenciado por `animateDpAsState(if (isExpanded) 220.dp else 56.dp, tween(250, easing = FastOutSlowInEasing))`.
- `ExpandedNavRailItem` e `SlimNavRailItem` padronizados para altura fixa de `44.dp`.
- `ExpandedNavRailItemContent` renderiza o indicador ativo (`Box` de 3dp) de forma fixa sem alterar o recuo interno do ícone (12dp).
- `Text` do item de navegação configurado com `maxLines = 1`, `softWrap = false` e `overflow = TextOverflow.Ellipsis`.

### 2. Global Canvas Focus & Text Selection
- No arquivo `frontend/src/wasmJsMain/resources/index.html` (e `backend/cmd/api/static/index.html`):
  ```css
  canvas, canvas:focus, canvas:focus-visible {
      outline: none !important;
      box-shadow: none !important;
      -webkit-tap-highlight-color: transparent;
  }
  #inframap-app, #inframap-app:focus {
      outline: none !important;
  }
  ```
- No arquivo `MainScaffold.kt`: Envolver `ScaffoldMainContent` com `SelectionContainer`.

### 3. Progressive Dashboard (`DashboardScreen.kt` & `DashboardViewModel.kt`)
- Adicionar parâmetro `onClick: (() -> Unit)? = null` aos cards de métricas (`MetricCard`), aplicando `Modifier.clickable(indication = null)` quando `onClick != null`.
- Estruturar a máquina de estados de boas-vindas:
  - Estágio 1 (`totalSubnets == 0`): Banner sugerindo cadastro de sub-rede / auto-setup.
  - Estágio 2 (`totalSubnets > 0 && totalDiscoverySources == 0`): Banner sugerindo cadastrar Fonte de Descoberta.
  - Estágio 3 (`totalDiscoverySources > 0 && totalActiveDevices == 0 && totalStagedDevices == 0`): Banner sugerindo executar primeira varredura.
  - Estágio 4 (`totalActiveDevices > 0 || totalStagedDevices > 0`): Omitir banner de boas-vindas (precedência máxima: qualquer dispositivo ativo ou em staging suprime o banner).

### 4. Global 401 Session Interceptor (`ApiClient.kt` & `InfraMapApp.kt`)
- O `ApiClient` expõe uma propriedade/callback `var onSessionExpired: (() -> Unit)? = null`.
- Quando `safeCall` recebe `httpStatus == 401` ou código `"UNAUTHORIZED"`, invoca `onSessionExpired?.invoke()`.
- O ponto de entrada `InfraMapApp` registra o listener no `ApiClient` que limpa o token/estado de sessão e aciona `navigator.navigateTo(Route.Login)`.

---

## Testing Decisions
- **Unit Tests (`jvmTest`)**:
  - `NavRailTest.kt`: Verificar renderização em 220dp e 56dp, altura de 44dp em ambos os modos e formato de texto sem quebra.
  - `DashboardViewModelTest.kt`: Validar cálculo de estágios de onboarding progressivo com base em sub-redes, fontes e dispositivos.
  - `ApiClientTest.kt`: Validar disparo do callback `onSessionExpired` em respostas 401.
- **Coverage Gate**:
  - Manter cobertura de testes $\ge 85\%$ e zero violações em `ktlintCheck` e `detekt`.
- **E2E & Visual Testing**:
  - Playwright visual regression em `tests/e2e-browser/` e `frontend/e2e/tests/navigation.spec.ts`.

---

## Out of Scope
- Redesenho completo do sistema de permissões RBAC no backend.
- Suporte a múltiplos temas visuais claros (foco permanece 100% no Dark Theme Dracula/M3).
- Redimensionamento livre do menu lateral por arraste com o cursor.

---

## Further Notes
Esta especificação consolida as decisões arquiteturais registradas no [ADR-012](./adr/ADR-012-ui-ux-and-session-lifecycle.md) e estabelece a base para o fatiamento dos tickets em `.scratch/ui-ux-auth-refinement/issues/`.
