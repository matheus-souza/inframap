# RFC-023 — UI/UX Refinement, Progressive Dashboard & Session Lifecycle Specification

| Status | Accepted |
|---|---|
| Created | 2026-08-31 |

---

## Problem Statement
Continuous use of the application and visual testing of InfraMap surfaced five friction points in the user experience and the session lifecycle:
1. **NavRail**: In the expanded state, labels such as "Fontes de Descoberta" (Discovery Sources) wrapped onto a second line when selected, because of the offset introduced by the active indicator and the narrow 200dp width. Opening and closing were not animated, and item height in slim mode (56dp) was larger than in expanded mode (44dp).
2. **Unwanted Focus Ring**: Interacting with the Compose WASM canvas made the browser draw a blue outline around the entire viewport.
3. **Text Selection**: Users could not select and copy technical text (IP addresses, CIDR blocks, MAC addresses, identifiers).
4. **Dashboard**: KPI cards were not interactive or clickable, and the welcome banner suggested registering subnets even when the database already had subnets configured.
5. **Expired Session (401)**: When the token or session expired, the application kept the user on the current screen with stale data and an error banner, instead of redirecting them immediately to the authentication screen (`/login`).

---

## Solution
Implement a coordinated set of UI/UX, transition and authentication lifecycle refinements:
- **NavRail**: 220dp expanded width, smooth animation (250ms), a standard 44dp height for every item, a selection indicator that does not shift the icon horizontally, and safe truncation with an ellipsis.
- **Canvas Focus Ring & Selection**: Remove canvas outlines through CSS and wrap the main content in a `SelectionContainer`.
- **Interactive & Progressive Dashboard**: Direct navigation from the metric cards and a progressive state machine for the Dashboard suggestions.
- **Expired Session (401)**: A global interceptor in `ApiClient` with an immediate redirect to `Route.Login`.

---

## User Stories

1. As a network operator, I want side menu (NavRail) items to keep their text on a single line when selected, so menus stay clean and free of visual breaks.
2. As a user, I want a smooth animation when expanding and collapsing the side menu, so the interface feels polished and modern.
3. As a user, I want side menu items to have the same vertical spacing whether the menu is open or closed.
4. As a user, I want to click anywhere in the application without a blue focus outline wrapping the whole browser window.
5. As an infrastructure administrator, I want to select and copy on-screen text (such as IPs, MACs, hostnames and statuses) with the mouse and keyboard shortcuts (Ctrl+C / Cmd+C).
6. As an operator, I want to click the "Active Devices" card on the Dashboard and be taken straight to the device inventory screen.
7. As an operator, I want to click the "Staged Devices" card on the Dashboard and be taken straight to the staging queue.
8. As an operator, I want to click the "Discovery Sources" card on the Dashboard and be taken to the discovery source list.
9. As a new user with subnets registered but no discovery sources, I want the Dashboard to suggest configuring a discovery source instead of asking me to register a subnet again.
10. As a new user with discovery sources registered but no scanned devices, I want the Dashboard to suggest triggering the first scan.
11. As a user with a populated inventory, I want the Dashboard welcome banner to disappear automatically so operational metrics take full focus.
12. As a user whose session expired or whose token was revoked, I want to be redirected immediately to the Login screen when trying to load data, so I know I need to authenticate again and I am not left looking at outdated information.

---

## Implementation Decisions

### 1. NavRail Component (`NavRail.kt`)
- `railWidth` managed by `animateDpAsState(if (isExpanded) 220.dp else 56.dp, tween(250, easing = FastOutSlowInEasing))`.
- `ExpandedNavRailItem` and `SlimNavRailItem` standardized to a fixed `44.dp` height.
- `ExpandedNavRailItemContent` renders the active indicator (a 3dp `Box`) at a fixed position without changing the icon's inner padding (12dp).
- The navigation item `Text` is configured with `maxLines = 1`, `softWrap = false` and `overflow = TextOverflow.Ellipsis`.

### 2. Global Canvas Focus & Text Selection
- In `frontend/src/wasmJsMain/resources/index.html` (and `backend/cmd/api/static/index.html`):
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
- In `MainScaffold.kt`: wrap `ScaffoldMainContent` in a `SelectionContainer`.

### 3. Progressive Dashboard (`DashboardScreen.kt` & `DashboardViewModel.kt`)
- Add an `onClick: (() -> Unit)? = null` parameter to the metric cards (`MetricCard`), applying `Modifier.clickable(indication = null)` when `onClick != null`.
- Structure the welcome state machine:
  - Stage 1 (`totalSubnets == 0`): banner suggesting subnet registration / auto-setup.
  - Stage 2 (`totalSubnets > 0 && totalDiscoverySources == 0`): banner suggesting registering a Discovery Source.
  - Stage 3 (`totalDiscoverySources > 0 && totalActiveDevices == 0 && totalStagedDevices == 0`): banner suggesting running the first scan.
  - Stage 4 (`totalActiveDevices > 0 || totalStagedDevices > 0`): welcome banner omitted (highest precedence: any active or staged device suppresses the banner).

### 4. Global 401 Session Interceptor (`ApiClient.kt` & `InfraMapApp.kt`)
- `ApiClient` exposes a property/callback `var onSessionExpired: (() -> Unit)? = null`.
- When `safeCall` receives `httpStatus == 401` or the `"UNAUTHORIZED"` code, it invokes `onSessionExpired?.invoke()`.
- The `InfraMapApp` entry point registers the listener on `ApiClient`, which clears the token/session state and calls `navigator.navigateTo(Route.Login)`.

---

## Testing Decisions
- **Unit Tests (`jvmTest`)**:
  - `NavRailTest.kt`: verify rendering at 220dp and 56dp, a 44dp height in both modes, and non-wrapping text.
  - `DashboardViewModelTest.kt`: validate the progressive onboarding stage computation from subnets, sources and devices.
  - `ApiClientTest.kt`: validate that `onSessionExpired` fires on 401 responses.
- **Coverage Gate**:
  - Keep test coverage $\ge 85\%$ and zero `ktlintCheck` and `detekt` violations.
- **E2E & Visual Testing**:
  - Playwright visual regression in `tests/e2e-browser/` and `frontend/e2e/tests/navigation.spec.ts`.

---

## Out of Scope
- A full redesign of the backend RBAC permission system.
- Support for multiple light visual themes (focus remains 100% on the Dracula/M3 Dark Theme).
- Free resizing of the side menu by dragging with the cursor.

---

## Further Notes
This specification consolidates the architectural decisions recorded in [ADR-012](./adr/ADR-012-ui-ux-and-session-lifecycle.md) and lays the groundwork for slicing the implementation tickets.
