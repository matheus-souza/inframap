# ADR-008: UI Refinement v3 Architectural Decisions

> **Status**: Accepted  
> **Date**: 2026-08-21  
> **Related RFC**: RFC-020  
> **Supervises**: T37–T40 Implementation Cycle  

---

## Context

Following the completion of the UI Refinement v2 stabilization phase (PRs #112–#116), a comprehensive grilling and design interview was conducted to define the next architectural and experiential iteration of InfraMap. The focus centers on delivering a tactile, dark-slate aesthetic inspired by modern canvas applications (Excalidraw) and providing an effortless, auto-guided network discovery journey.

---

## Decision AD-047: Dashboard Inline Hero Card for Network Auto-Setup

### Context
First-time users need immediate network visibility and one-click scanning without being forced through intrusive modal wizards that block dashboard exploration.

### Decision
Implement an inline **Hero Auto-Setup Card** directly in `DashboardScreen`.
1. When `totalSubnets == 0`, `DashboardViewModel` queries `/api/v1/network/interfaces`.
2. A non-blocking banner renders discovered CIDR blocks with an immediate **"Configurar e Iniciar Varredura"** CTA.
3. Upon trigger, the coordinator orchestrates subnet creation, discovery source provisioning, and initiates the initial scan with live inline progress reporting.
4. A secondary CTA provides direct access to manual configuration for advanced operators.

---

## Decision AD-048: Excalidraw Dark Slate Design Tokens & Monospace Typography

### Context
Visual consistency across network graphs, data tables, and modals requires unified dark theme tokens with subtle contrast, soft functional indicators, and clear distinction for technical network values (IPs, MACs, CIDRs).

### Decision
Adopt the **Excalidraw Dark Slate** palette across `InfraMapColorScheme`:
- Base Canvas: `#121214`
- Elevated Containers: `#18181b` / `#222226`
- Subtle Outlines: `#27272a`
- Primary Brand Accent: `#8b5cf6` (Slate Violet)
- Functional Status Colors: `#10b981` (Online), `#f59e0b` (Warning), `#ef4444` (Alert), `#a78bfa` (Staging)
- Technical Data Typography: Mandatory `FontFamily.Monospace` (JetBrains Mono) for all IP, CIDR, MAC, and Port representations.

---

## Decision AD-049: Categorized Hybrid Command Palette (`Ctrl+K`)

### Context
A universal command palette must balance global action execution (system shortcuts) with deep asset querying (devices, subnets) without visual confusion.

### Decision
Structure `CommandPaletteModal` with a **Categorized Hybrid Layout**:
- Sections: *Ações Rápidas* (Lightning), *Dispositivos* (Host), *Sub-redes* (Network), and *Navegação* (Compass).
- Each item displays contextual status badges (e.g., online status, device counts).
- Backdrop clickable explicitly uses `interactionSource`, `indication = null`, and `onClickLabel` per Guidelines #123 & #124.

---

## Decision AD-050: Standardized Shimmer Skeletons & Floating Toasts

### Context
Loading spinners cause layout shifts, and alert banners consume persistent vertical space for transient notifications.

### Decision
Standardize feedback mechanics:
1. **Shimmer Skeletons**: Replace full-screen loading spinners with `InfraMapShimmer` containers mirroring the shape of KPI cards, tables, and lists during async queries.
2. **Floating Toasts**: Asynchronous background notifications (scan started, device approved, error toasts) render via a non-blocking toast stack in the viewport bottom-right corner.
