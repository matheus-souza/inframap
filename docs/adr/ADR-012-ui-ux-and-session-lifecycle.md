# ADR-012 — UI/UX Refinement, Progressive Dashboard & Session Lifecycle

| Status | Accepted |
|---|---|
| Created | 2026-08-29 |

---

## Context
Continuous use of the application and manual test sessions identified five visual and lifecycle behaviors that called for standardization and architectural refinement:
1. **NavRail (Side Menu)**: Items with longer pt-BR labels (e.g. "Fontes de Descoberta") wrapped onto a second line when selected, because of the extra 12dp padding introduced by the active indicator and the narrow 200dp width. In addition, the width toggle had no animated transition, and item height in the collapsed state (56dp) did not match the expanded state (44dp).
2. **HTML5 Canvas Focus Outline**: In modern browsers, clicking the canvas where Compose WASM renders made a default blue focus outline wrap the entire viewport.
3. **Text Selection**: In Compose Multiplatform, text drawn on the Canvas has no native selection enabled by default, preventing users from copying technical data (IPs, CIDRs, MACs, statuses).
4. **Dashboard KPIs & Suggestions**: Metric cards were static (no direct click navigation), and the onboarding card showed a redundant suggestion to register subnets even when the user already had subnets configured.
5. **Expired Session (401)**: 401 Unauthorized responses were handled as local screen errors, showing error banners over stale data instead of forcing immediate re-authentication.

## Decision

### 1. NavRail Layout & Motion
- The expanded NavRail width is fixed at `220.dp`, animated through `animateDpAsState(targetValue = railWidth, animationSpec = tween(250, easing = FastOutSlowInEasing))`.
- Item height in both states (Slim and Expanded) is standardized at `44.dp`, removing vertical spacing inconsistencies.
- The active selection indicator (a 3dp `Box`) is rendered as an overlay with zero margin over the left edge, without pushing the icon 12dp to the right.
- Every navigation label uses `maxLines = 1`, `softWrap = false` and `overflow = TextOverflow.Ellipsis`.

### 2. Canvas Outline & Selection Container
- In the CSS (`index.html`), the focus outline is removed from the `canvas` element and `#inframap-app` (`outline: none !important; box-shadow: none !important;`).
- In Compose, the main content area in `MainScaffold` is wrapped in a `SelectionContainer`, allowing text and tables to be selected and copied. Click and action components use `DisableSelection` where needed.

### 3. Progressive Dashboard Onboarding & Card Navigation
- KPI cards become clickable with a hover affordance, navigating directly to their routes:
  - Active Devices $\to$ `Route.Devices`
  - Staged Devices $\to$ `Route.Staging`
  - Discovery Sources $\to$ `Route.DiscoverySources`
- The onboarding card follows a progressive state machine:
  - `totalSubnets == 0`: suggests creating a subnet / Auto Setup.
  - `totalSubnets > 0 && totalDiscoverySources == 0`: suggests creating a Discovery Source.
  - `totalDiscoverySources > 0 && totalActiveDevices == 0 && totalStagedDevices == 0`: suggests triggering the first scan.
  - `totalActiveDevices > 0 || totalStagedDevices > 0`: card omitted (highest precedence: the presence of devices suppresses the banner).

### 4. Global 401 Session Redirection
- `ApiClient` emits a global session-expired signal (`onSessionExpired`) whenever an HTTP response has status `401 Unauthorized` or carries the `"UNAUTHORIZED"` error code.
- The global listener (wired to the `Navigator`) immediately runs `navigator.navigateTo(Route.Login)`, clearing data, tokens and session state, and preventing partial alerts over stale data.

## Consequences
- **Positive**: consistent UX, smooth navigation without visual breaks, higher technical productivity by allowing addresses and IDs to be copied, and stronger security with immediate logout when a token expires.
- **Neutral**: unit tests for `ApiClient`, `DashboardViewModel` and `NavRailTest` must cover the new navigation flows and listeners.
