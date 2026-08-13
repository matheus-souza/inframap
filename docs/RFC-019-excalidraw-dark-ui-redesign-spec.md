# RFC-019: Excalidraw-Inspired Dark Canvas UI/UX Redesign Specification

- **Feature Area**: Frontend UI/UX, Design System, Topology Canvas & Dashboard
- **Target Release**: v0.6.0
- **Status**: DRAFT / SPECIFIED
- **Authors**: Antigravity AI & Matheus Souza
- **ADR Reference**: ADR-006

---

## 1. Executive Summary

RFC-019 specifies the complete UI/UX overhaul of InfraMap, introducing an **Excalidraw-inspired Dark Canvas Theme**, a **Hybrid Slim Navigation Shell**, an interactive **Topology Canvas Mode**, and a **High-Density Inventory & Staging System**.

The design prioritizes visual excellence, tactile dark mode aesthetics (`#121214`), instant operational clarity, and keyboard-first shortcuts (`⌘K`). Development is structured into explicit, vertical delivery phases to guarantee incremental testability and zero regression on Kotlin WASM builds.

---

## 2. Design System & Theme Tokens

### 2.1 Color Palette (WCAG AA Compliant)

| Token Name | Hex Code | Role / Usage |
|---|---|---|
| `InfraMapCanvasBg` | `#121214` | Main topology canvas background |
| `InfraMapSurfaceBg` | `#18181b` | Cards, sidebars, floating toolbars |
| `InfraMapSurfaceElevated` | `#27272a` | Modals, inspector sheets, hover states |
| `InfraMapBorder` | `#27272a` | 1px subtle container borders |
| `InfraMapTextPrimary` | `#f4f4f5` | High-contrast body & header text |
| `InfraMapTextSecondary` | `#a1a1aa` | Labels, subtitles, metadata |
| `StatusOnline` | `#10b981` | Emerald Green — Active/Healthy devices & ICMP ping success |
| `StatusWarning` | `#f59e0b` | Amber — High latency, SNMP timeout, warning |
| `StatusOffline` | `#ef4444` | Ruby Red — Unreachable device / offline |
| `StatusStaging` | `#8b5cf6` | Electric Violet — Discovered unapproved device |

### 2.2 Typography Hierarchy

- **Primary UI Font**: `Inter`, `Roboto`, or system sans-serif.
- **Monospace Font**: `JetBrains Mono` for IP addresses (`192.168.1.1`), MAC addresses (`00:11:22:33:44:55`), ports, and CIDR blocks.

---

## 3. Architecture & Page Layout Modules

```text
┌─────────────────────────────────────────────────────────────────────────────┐
│ InfraMap Shell (Slim Rail Navigation)                                      │
├────┬────────────────────────────────────────────────────────────────────────┤
│ ⚡ │ TopBar: Breadcrumbs │ Universal Search (⌘K) │ Live SSE Status │ Profile  │
│    ├────────────────────────────────────────────────────────────────────────┤
│ 📊 │ MAIN CONTENT AREA                                                     │
│    │                                                                        │
│ 🗺️ │ Mode A: Dashboard / Overview (KPIs + Recent Table + SSE Feed)        │
│    │ Mode B: Topology Canvas (Excalidraw Dark Grid + Floating Toolbar)      │
│ 📋 │ Mode C: High-Density Inventory (Status Breakdown Bar + Multi-Actions)   │
│    │ Mode D: Staging Approval Queue                                         │
│ ⚙️ │                                                                        │
└────┴────────────────────────────────────────────────────────────────────────┘
```

### 3.1 Navigation Shell (`NavRail`)
- Slim left rail with icon-only default state (`56px` width).
- Tooltip labels on hover; click toggles route (`/dashboard`, `/topology`, `/inventory`, `/staging`, `/settings`).
- Active route highlighted with a subtle vertical pill indicator and violet/emerald glow.

### 3.2 Topology Canvas Mode (Excalidraw Dark Aesthetic)
- **Background**: `#121214` dark canvas with subtle `#27272a` dot matrix grid pattern (`20px` spacing).
- **Floating Toolbar**: Top-center pill container with rounded corners (`12px`) containing:
  - Select / Pointer
  - Pan / Hand
  - Zoom In / Zoom Out / Zoom Fit
  - Subnet Boundary Grouping toggle
  - Force-Directed Auto-Layout trigger
- **Slide-over Inspector Sheet**: Right-hand drawer (`360px`) opening on node selection, displaying:
  - Device Hostname, IP, MAC, Subnet, Vendor icon.
  - Active interfaces & protocol discovery metadata (ARP, ICMP, SNMP).
  - Quick action: "Trigger Re-scan" / "Edit Metadata".

### 3.3 Dashboard Overview
- **Phase 1 MVP**:
  1. **KPI Metric Cards Row**: 4 cards displaying Total Devices, Monitored Subnets, Active Discovery Engine status, and Pending Staging count.
  2. **Recent Devices & Activity Table**: High-density table of recent observations.
  3. **Live SSE Events Widget**: Real-time stream of discovery events.
- **Phase 2 Backlog**:
  - 24h Availability/Latency line chart + Device distribution donut chart.

### 3.4 High-Density Inventory & Staging Queue
- **Status Breakdown Bar**: Horizontal color-segmented bar above data grid (`Online 70%` 🟢 | `Alert 10%` 🟡 | `Staging 15%` 🟣 | `Offline 5%` 🔴).
- **Universal Search & Filters**: `⌘K` global search shortcut + inline filter pills (by CIDR, device type, discovery protocol).
- **Floating Action Bar**: Appears at bottom center when checkboxes are selected (`Approve 3 items`, `Re-scan`, `Delete`).

---

## 4. Phased Delivery Roadmap

| Phase | Milestone Name | Key Deliverables |
|---|---|---|
| **Phase 1** | **Excalidraw Dark Design System & Shell** | Color tokens, typography, `InfraMapTheme`, slim `NavRail`, `⌘K` modal placeholder |
| **Phase 2** | **Dashboard MVP & Live Activity** | KPI cards row, recent device table, SSE event stream widget |
| **Phase 3** | **High-Density Inventory & Staging** | Status distribution breakdown bar, data table, floating action bar |
| **Phase 4** | **Topology Canvas MVP (Auto-Layout)** | Dot matrix grid canvas, node rendering, pan/zoom, slide-over Inspector |
| **Phase 5** | **Canvas Backlog & Advanced Visuals** | Drag & drop persistence, freeform connections, latency charts |

---

## 5. Verification & Acceptance Criteria

- All color tokens achieve WCAG AA contrast ratio (>= 4.5:1).
- WASM production build passes cleanly (`./gradlew wasmJsBrowserDistribution`).
- E2E Playwright test suite verifies navigation, `⌘K` modal popover, data table selection, and canvas rendering.
