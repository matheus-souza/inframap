# ADR-010: Material Design 3 Design System & Motion Architecture (AD-054 to AD-058)

> **Status**: Accepted  
> **Date**: 2026-08-27  
> **Related RFC**: RFC-022 (Material 3 Visual & Motion Design Spec)  

---

## Context

Following the stabilization of WebAssembly runtime mechanics and network resource loading (PRs #125–#134), user feedback identified that the visual presentation of InfraMap suffered from overly harsh, near-pitch-black contrast (17.1:1 contrast ratio), static grey loading blocks, and abrupt transitions without spatial hierarchy.

To provide an ergonomic, soothing, and delightful operator experience for extended homelab and NOC infrastructure monitoring, InfraMap adopts the **Google Material Design 3 (M3) Design System & Motion Architecture** (per [m3.material.io/styles](https://m3.material.io/styles) and [m3.material.io/styles/motion](https://m3.material.io/styles/motion/overview/how-it-works)).

---

## Decision AD-054: Material Design 3 Soft Dark Slate Tonal Palette & WCAG AAA Ergonomics

### Context
Hard pitch-black canvas backgrounds (`#121214`) paired with pure white text (`#f4f4f5`) produce optical halation, pupil fatigue, and harsh visual contrast over long sessions.

### Decision
Evolve `InfraMapColorScheme` to adopt the **M3 Tonal Surface Container System** derived from calibrated HCT tonal luminance:

1. **Surface Container Hierarchy**:
   - `surfaceContainerLowest` (`#0E0E12`, Tone 4): Topology canvas base & deep wells.
   - `surfaceContainerLow` (`#18181D`, Tone 10): `NavRail` background & sub-panels.
   - `surfaceContainer` (`#1E1D24`, Tone 12): Standard KPI cards, inventory tables, and data grids.
   - `surfaceContainerHigh` (`#28272F`, Tone 17): Hovered cards, floating toolbars, and quick-action strips.
   - `surfaceContainerHighest` (`#33323C`, Tone 22): Modals, dialogs, drawers, and command palette.

2. **Tonal Accent Roles (Tone 80 / Tone 20)**:
   - Primary: `InfraMapPrimary` (`#D0BCFF`) / `onPrimary` (`#381E72`) / `primaryContainer` (`#4F378B`).
   - Secondary (Online / Healthy): `InfraMapSecondary` (`#6EE7B7`) / `onSecondary` (`#003826`).
   - Tertiary (Warning / Latency): `InfraMapTertiary` (`#FCD34D`) / `onTertiary` (`#452B00`).
   - Error (Offline / Critical): `InfraMapError` (`#FFB4AB`) / `onError` (`#690005`).

3. **Text & Border Contrast**:
   - Primary Text (`onSurface`): `#E4E1E6` (Tone 90) on Tone 12 provides **13.1:1** contrast (WCAG AAA compliant without glare).
   - Secondary Text (`onSurfaceVariant`): `#C8C5D0` (Tone 80) provides **9.8:1** contrast.
   - Borders (`outlineVariant`): `#48454E` with subtle opacity replacing harsh 1px black borders.

---

## Decision AD-055: Material 3 Motion System & Navigation Transitions

### Context
Instantaneous route swaps create visual disorientation and obscure screen hierarchy.

### Decision
Implement the **Material 3 Motion System** across all navigation boundaries:

1. **NavRail Peer Transitions (Fade Through)**:
   - Switching between top-level tabs (`Dashboard` $\leftrightarrow$ `Devices` $\leftrightarrow$ `Subnets` $\leftrightarrow$ `Discovery` $\leftrightarrow$ `Topology`) utilizes **M3 Fade Through**:
   - Outgoing content scales down slightly (`100%` $\rightarrow$ `92%`) while fading out (`DurationTokens.Short3` = 150ms).
   - Incoming content scales up (`92%` $\rightarrow$ `100%`) while fading in (`DurationTokens.Medium3` = 350ms) using `CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)` (`EmphasizedDecelerate`).

2. **Hierarchical Master-Detail Transitions (Shared Axis X)**:
   - Drill-down transitions (e.g. `Devices` $\rightarrow$ `DeviceDetail`) use **M3 Shared Axis X**:
   - Forward: Outgoing slides left by 30% with fade; incoming slides from right (+30%) with fade.
   - Backward: Reverse direction smoothly.

3. **Splash to Dashboard Transition (Shared Axis Z)**:
   - Root auth transition expands and scales smoothly into view (`0.85f` $\rightarrow$ `1.0f`) with crossfade.

---

## Decision AD-056: Physics-Based Interactive Micro-Interactions

### Context
Buttons and clickable cards lack physical presence and responsive tactile feedback on desktop/canvas web environments.

### Decision
Introduce `Modifier.m3InteractiveScale()` leveraging Compose spring physics:
- **Press Scale**: Compresses to `0.96f` on press with `spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMedium)`.
- **Hover Lift**: Scales smoothly to `1.015f` with container color shift to `surfaceContainerHigh`.
- **Focus Indicator**: Accessible soft outline ring (`outlineVariant`).

---

## Decision AD-057: 45° Linear Gradient Shimmer Wave for Loading States

### Context
Static, opaque grey rectangular blocks create visual heaviness and do not convey asynchronous network activity.

### Decision
Replace static skeletons with **M3 Shimmer Wave**:
- 45° linear gradient sweeping continuously over 1200ms (`LinearEasing`) from `surfaceContainer` (`#1E1D24`) through `surfaceContainerHigh` (`#2A2932`) and back.
- When asynchronous data resolves, the skeleton smoothly crossfades into real content without layout jank.

---

## Decision AD-058: Material 3 Emphasized Container Scale for Modals and Command Palette

### Context
Modals and command palettes popping abruptly onto the screen disrupt operator focus.

### Decision
All overlay containers (`CommandPaletteModal`, `InfraMapConfirmDialog`, `SetupWizardOverlay`) use **M3 Emphasized Container Scale**:
- **Enter**: `scaleIn(0.88f -> 1.0f) + fadeIn()` over 350ms (`EmphasizedDecelerate`).
- **Exit**: `scaleOut(1.0f -> 0.88f) + fadeOut()` over 200ms (`EmphasizedAccelerate`).
- **Scrim**: Soft 50% black backdrop overlay with synchronized fade.
