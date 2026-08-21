# RFC-020: UI Refinement v3 — Excalidraw Slate Dark & Auto-Setup UX Spec

> **Status**: Approved  
> **Authors**: InfraMap Core Team  
> **Date**: 2026-08-21  
> **Target Branch**: `develop`  
> **Target Framework**: Kotlin Multiplatform / Compose WASM (`wasmJs`)  

---

## 1. Context & Problem Statement

InfraMap successfully completed its foundation redesign (RFC-019) and critical bug fixes (v2 / PRs #112–#116). However, the overall user experience still requires targeted refinement to reach the fluid, tactile feel of modern dark canvas tools (like Excalidraw):

1. **First-Time Network Registration Friction**: While interfaces can be probed, users lack an intuitive, inline onboarding card on the Dashboard that shows detected subnets and offers one-click scan triggers with live visual progress.
2. **Design Tokens & Surface Harmony**: The dark palette needs alignment with the Excalidraw Dark Slate palette (`#121214` canvas, `#18181b` surface containers, `#27272a` subtle borders, and `#8b5cf6` violet accents).
3. **Command Palette (`Ctrl+K`) Ergonomics**: The search modal must group items into clear categories ("Ações Rápidas", "Dispositivos", "Sub-redes", "Navegação") with keyboard-first navigation and clear status badges.
4. **Empty State & Loading Standardization**: Empty states must follow a uniform component with clear primary/secondary CTAs, and loading states must utilize smooth shimmer skeletons rather than static or jarring spinners.

---

## 2. Design Decisions (ADR-008 Summary)

| Decision | Choice | Rationale |
|---|---|---|
| **AD-047: Auto-Setup Registration Flow** | **Dashboard Inline Hero Card (1-Click Scan)** | Non-modal, zero-friction discovery with inline live progress bar and direct fallback to manual configuration. |
| **AD-048: Visual Aesthetic & Tokens** | **Excalidraw Dark Slate Modern** | Smooth `#121214` canvas, `#18181b` containers, subtle `#27272a` borders, vibrant soft functional badges, and `JetBrains Mono` for network data. |
| **AD-049: Command Palette Architecture** | **Hybrid Action + Categorized Search** | Unified index supporting instant fuzzy search across system actions, inventory devices, subnets, and routes. |
| **AD-050: Feedback & Loading System** | **Shimmer Skeletons + Floating Toasts** | Non-blocking tactile feedback with smooth animated skeletons and dismissable bottom toasts for async operations. |

---

## 3. Detailed Specifications by Module

### Module 1: Design Tokens & Feedback Components (`frontend/designsystem`)
- **Color Scheme Tokens**:
  - `canvasBackground`: `#121214`
  - `surfaceContainer`: `#18181b`
  - `surfaceContainerHigh`: `#222226`
  - `outlineSubtle`: `#27272a`
  - `accentPrimary`: `#8b5cf6` (Slate Violet)
  - `statusOnline`: `#10b981` (Emerald)
  - `statusWarning`: `#f59e0b` (Amber)
  - `statusAlert`: `#ef4444` (Ruby Red)
  - `statusStaging`: `#a78bfa` (Soft Purple)
- **Shimmer Component (`InfraMapShimmer`)**:
  - Linear gradient brush animating from `#18181b` → `#27272a` → `#18181b` (1200ms cycle).
  - Reusable container modifier `Modifier.shimmerPlaceholder(shape = RoundedCornerShape(8.dp))`.
- **Toast System (`InfraMapToastManager`)**:
  - StateFlow-based event queue rendering non-blocking toasts in the bottom-right corner.

### Module 2: Dashboard Auto-Setup Hero Card (`frontend/ui/dashboard`)
- **Condition**: Visible when `totalSubnets == 0` or network interfaces are discovered without active discovery sources.
- **Card Elements**:
  1. Header: *"Redes Locais Detectadas"* with radar icon.
  2. Interface Pill List: Chips displaying detected CIDRs (e.g., `eth0: 192.168.1.0/24`).
  3. Primary CTA: *"Configurar e Iniciar Varredura"* (triggers auto-creation of subnets + sources + scan).
  4. Secondary CTA: *"Configuração Manual"* (opens Wizard or redirects to Subnets page).
  5. Live Progress: Inline animated progress bar indicating scan progress with device counter.

### Module 3: Categorized Command Palette (`frontend/ui/command`)
- **Modal Specifications**:
  - Max width 640dp, top-aligned (margin 80dp).
  - Clean semi-transparent scrim (`Color.Black.copy(alpha = 0.65f)`) with `indication = null` and accessible `onClickLabel`.
  - Search Input with auto-focus and clear button.
- **Section Headers**:
  - ⚡ *Ações Rápidas*: "Nova Varredura", "Cadastrar Sub-rede", "Exportar Inventário", "Alternar Barra Lateral".
  - 🖥️ *Dispositivos*: Matching hosts with status dot, IP, and hostname.
  - 🌐 *Sub-redes*: Matching CIDRs with device count badge.
  - 🧭 *Navegação*: Direct jumps to Dashboard, Staging, Topologia, Fontes, Configurações.
- **Keybindings**: `↑` / `↓` to navigate, `Enter` to execute, `Esc` to close.

### Module 4: Standardized Empty States & Monospace Typography
- **Component (`InfraMapEmptyState`)**:
  - Centered layout with 48dp custom `ImageVector` icon.
  - Title in `titleMedium` and description in `bodyMedium` (muted).
  - Primary CTA button (`InfraMapButton`) + Optional secondary link (`InfraMapOutlinedButton`).
- **Typography Rule**:
  - All IP addresses, CIDR masks, MAC addresses, and port numbers MUST be styled with `FontFamily.Monospace` (JetBrains Mono) for tabular legibility.

---

## 4. Delivery Slices & Tickets Roadmap

| Ticket | Scope | Target Package | Dependencies |
|---|---|---|---|
| **T37** | Excalidraw Dark Tokens, Shimmer Skeletons & Toast System | `frontend/designsystem` | — |
| **T38** | Dashboard Auto-Setup Hero Card with Live Scan Progress | `frontend/ui/dashboard` | T37 |
| **T39** | Universal Categorized Command Palette (`Ctrl+K`) | `frontend/ui/command` | T37 |
| **T40** | Uniform Empty States & Monospace Network Data Polish | `frontend/ui/*` | T37, T38 |

---

## 5. Verification & Quality Gates

- **Unit & UI Tests**: `./gradlew jvmTest koverVerify` (maintaining 85%+ coverage).
- **Playwright Visual Suite**: Screenshot comparisons for Dashboard Hero Card, Command Palette categories, and Empty States.
- **Accessibility**: Screen-reader labels (`onClickLabel`, `contentDescription`) verified on all interactive elements.
