# ADR-006: Excalidraw-Inspired Dark Canvas UI/UX Redesign System

- **Status**: ACCEPTED
- **Date**: 2026-08-12
- **Authors**: Antigravity AI & Matheus Souza
- **RFC Reference**: RFC-019 (UI Redesign Specification)

---

## Context & Problem Statement

InfraMap requires a modern, visually stunning, high-efficiency user interface tailored for homelab operators, network engineers, and system administrators. The current Compose HTML / Kotlin WASM frontend provides functional views, but lacks a cohesive dark-mode aesthetic, interactive canvas controls, and high-density inventory management views.

Operators need:
1. An immersive, dark-canvas topology map with smooth pan/zoom, auto-layout, and device inspection.
2. A high-density dashboard presenting instant health KPIs, SSE live activity, and inventory summaries.
3. High-density inventory tables with color-coded status breakdown bars, universal `⌘K` search, and floating batch action bars.

---

## Decision Drivers

1. **Visual Excellence & Modern Aesthetics**: Excalidraw Slate dark mode (`#121214` canvas, `#18181b` surface containers, `#27272a` borders) with functional neon accents.
2. **Incremental Phased Delivery**: Prevent monolithic UI changes by splitting canvas topology and analytics into Phase 1 (MVP auto-layout & core tables) and Phase 2 (Advanced manual drag-and-drop, custom vector drawing, interactive latency charts).
3. **High Density & Fast Operations**: High-density data tables, `⌘K` global command palette, and floating action bars for batch device approval and re-scans.
4. **WCAG AA Compliance**: High contrast color tokens (`#10b981` green, `#f59e0b` amber, `#ef4444` red, `#8b5cf6` violet) over dark surfaces.

---

## Considered Options

- **Option A (Chosen)**: **Excalidraw Slate Dark Aesthetic + Hybrid Slim Navigation + Phased Delivery**
  - Slim icon-only left navigation rail with expandable labels.
  - Dedicated Canvas Mode for Topology with dot matrix grid, floating toolbar, and slide-over Inspector Sheet.
  - Phased feature rollout ensuring rapid delivery and testability.

- **Option B**: Traditional wide sidebar with static topology tree and light mode defaults.
- **Option C**: Pure black OLED monochrome UI without functional network status colors.

---

## Decision Outcome

**Chosen Option: Option A**.

### Key Architectural Choices

1. **Design Tokens & Theme System**:
   - `InfraMapCanvasBg`: `#121214` (Deep Charcoal Black)
   - `InfraMapSurfaceBg`: `#18181b` (Container Surface)
   - `InfraMapBorder`: `#27272a` (Subtle 1px border)
   - `StatusOnline`: `#10b981` (Emerald Green)
   - `StatusWarning`: `#f59e0b` (Amber)
   - `StatusOffline`: `#ef4444` (Ruby Red)
   - `StatusStaging`: `#8b5cf6` (Electric Violet)
   - Typography: Inter/Roboto (UI) + JetBrains Mono (IPs, MACs, CIDRs).

2. **Canvas Topology Mode**:
   - Skiko/Compose HTML Canvas rendering with dot matrix grid.
   - Auto-layout (Force-Directed / Tree) with zoom-to-fit and pan controls.
   - Slide-over Inspector Sheet for device details and quick actions.

3. **High-Density Inventory & Staging**:
   - Status distribution breakdown bar at top of data grid.
   - Universal search (`⌘K` command palette integration).
   - Floating bottom action bar on multi-selection.

---

## Positive Consequences

- Delivers a state-of-the-art, visually impressive UI that wows users on first glance.
- Phased approach guarantees stable, testable delivery without breaking existing WASM builds.
- Keyboard-first shortcuts (`⌘K`) accelerate homelab management operations.

## Negative Consequences & Mitigation

- Skiko/Canvas rendering requires performance optimization for > 200 nodes.
  - *Mitigation*: Node clustering and viewport culling for large CIDR blocks.
