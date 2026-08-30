# ADR-012: UI/UX Refinement, Progressive Dashboard & Session Lifecycle

Date: 2026-08-29

## Status
Accepted

## Context
O uso contínuo da aplicação e sessões de testes manuais identificaram cinco comportamentos visuais e de ciclo de vida que demandavam padronização e refinamento arquitetural:
1. **NavRail (Menu Lateral)**: Itens com rótulos mais longos em pt-BR (ex.: "Fontes de Descoberta") sofriam quebra de linha indesejada quando selecionados, devido ao recuo adicional de 12dp introduzido pelo indicador ativo e largura restrita de 200dp. Além disso, a alternância de largura não possuía transição animada e a altura dos itens no estado fechado (56dp) destoava do estado aberto (44dp).
2. **Focus Outline do HTML5 Canvas**: Em navegadores modernos, ao clicar no canvas onde o Compose WASM é renderizado, uma borda azul de foco padrão envolvia o viewport inteiro.
3. **Seleção de Texto**: No Compose Multiplatform, elementos de texto sobre Canvas são desenhados sem seleção nativa habilitada por padrão, impedindo o usuário de copiar dados técnicos (IPs, CIDRs, MACs, status).
4. **Dashboard KPI & Sugestões**: Cards de métricas eram estáticos (sem navegação direta por clique) e o card de onboarding exibia sugestão redundante de cadastrar sub-redes mesmo quando o usuário já possuía sub-redes configuradas.
5. **Sessão Expirada (401)**: Respostas 401 Unauthorized eram tratadas como erros locais de tela, exibindo banners de erro sobrepostos em dados obsoletos em vez de forçar a re-autenticação imediata.

## Decision

### 1. NavRail Layout & Motion
- A largura do NavRail expandido é fixada em `220.dp` com animação via `animateDpAsState(targetValue = railWidth, animationSpec = tween(250, easing = FastOutSlowInEasing))`.
- A altura dos itens em ambos os estados (Slim e Expanded) é padronizada em `44.dp`, eliminando inconsistências de espaçamento vertical.
- O indicador de seleção ativa (`Box` de 3dp) é renderizado como overlay ou margem zero sobre a borda esquerda, sem empurrar o ícone 12dp para a direita.
- Todos os rótulos de navegação utilizam `maxLines = 1`, `softWrap = false` e `overflow = TextOverflow.Ellipsis`.

### 2. Canvas Outline & Selection Container
- No CSS (`index.html`), remove-se o focus outline do elemento `canvas` e `#inframap-app` (`outline: none !important; box-shadow: none !important;`).
- No Compose, envolve-se a área principal de conteúdo no `MainScaffold` em `SelectionContainer`, permitindo seleção e cópia de textos e tabelas. Componentes de clique e ação utilizam `DisableSelection` quando necessário.

### 3. Progressive Dashboard Onboarding & Card Navigation
- Os cards de KPI tornam-se clicáveis com affordance de hover, navegando diretamente para suas respectivas rotas:
  - Dispositivos Ativos $\to$ `Route.Devices`
  - Dispositivos em Staging $\to$ `Route.Staging`
  - Fontes de Descoberta $\to$ `Route.DiscoverySources`
- O card de onboarding adota um modelo de máquina de estados progressiva:
  - `totalSubnets == 0`: Sugere criação de sub-rede / Auto Setup.
  - `totalSubnets > 0 && totalDiscoverySources == 0`: Sugere criação de Fonte de Descoberta.
  - `totalDiscoverySources > 0 && totalActiveDevices == 0 && totalStagedDevices == 0`: Sugere disparo da primeira varredura.
  - `totalActiveDevices > 0 || totalStagedDevices > 0`: Card omitido, dando foco total às métricas.

### 4. Global 401 Session Redirection
- O `ApiClient` emite um sinal global de expiração de sessão (`onSessionExpired`) quando qualquer resposta HTTP for status `401 Unauthorized` ou possuir código de erro `"UNAUTHORIZED"`.
- O listener global (conectado ao `Navigator`) executa imediatamente `navigator.navigateTo(Route.Login)`, limpando dados de sessão e prevenindo alertas parciais sobrepostos em dados obsoletos.

## Consequences
- **Positivas**: UX consistente, navegação suave e sem quebras visuais, maior produtividade técnica permitindo cópia de endereços e IDs, segurança reforçada com logout imediato em caso de token expirado.
- **Neutras**: Testes unitários do `ApiClient`, `DashboardViewModel` e `NavRailTest` devem cobrir os novos fluxos de navegação e listeners.
