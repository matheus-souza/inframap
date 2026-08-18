# ADR-007: UI Refinement v2 — Onboarding Unification, Collapsible NavRail & i18n

- **Status**: ACCEPTED
- **Date**: 2026-08-16
- **Authors**: Matheus Souza
- **Spec Reference**: `.scratch/ui_refinement_v2/spec.md`

---

## Context & Problem Statement

After the initial Excalidraw Dark UI Redesign (ADR-006), real-world usage revealed several gaps:

1. **Broken string resources**: A hand-written `Res.kt` with zero-length offsets shadows the Gradle-generated one, causing all error messages and localized strings to render as empty strings throughout the application.

2. **Fragmented onboarding**: Three independent mechanisms (Onboarding Screen, Product Tour, Setup Wizard) operate without coordination. The Tour and Wizard can appear simultaneously, confusing new users.

3. **Missing auto-configuration**: First-time users must manually navigate through subnet creation, discovery source configuration, and scan execution — a multi-step process that's opaque without prior knowledge.

4. **Icon-only NavRail**: The 56dp slim NavRail requires hover-to-discover what each section is, which is unintuitive for new users. Additionally, several Material Extended icons don't render correctly in Compose WASM.

5. **Mixed language UI**: Some strings are in Portuguese, others in English, many hardcoded directly in composables instead of using the Compose Resources i18n system.

---

## Decision Drivers

1. **First-time user experience**: The system must be self-explanatory and guide users through initial setup without requiring documentation.
2. **Zero-friction setup**: Network auto-detection should minimize manual configuration.
3. **Consistent visual language**: Icons must render reliably, and text must be in one consistent language with i18n support.
4. **Progressive disclosure**: Advanced configuration should be available but not required.

---

## Decisions

### AD-042: Onboarding Coordinator — Sequenced Tour + Wizard

**Context**: Tour and Wizard are independent overlays managed by separate ViewModels with separate LocalStorage keys. No mutual exclusion exists.

**Decision**: Introduce an `OnboardingCoordinator` singleton (Koin-managed) that sequences the two mechanisms:

1. Fresh install → Wizard first
2. Wizard completes → Tour starts
3. Mutual exclusion: only one visible at a time
4. Coordinator reads existing LocalStorage keys — no new persistence mechanism needed

**Rationale**: A coordinator is the minimal-invasive change. It doesn't merge the two features or require rewriting their ViewModels — it only adds a sequencing layer.

---

### AD-043: Auto Network Setup Coexists with Wizard

**Context**: The Setup Wizard provides a 3-step manual configuration flow (detect interfaces → choose scan type/frequency → run first scan). Users want faster setup.

**Decision**: Add an inline `AutoSetupBanner` on the Dashboard that offers one-click network configuration:
- Auto-detects interfaces via existing `GET /api/v1/network/interfaces`
- Creates subnets + discovery sources + triggers scan with sensible defaults
- Wizard remains available for users who want granular control (scan type, frequency, interface selection)

**Rationale**: The two paths serve different user profiles. Auto-setup is for users who want to get started immediately; Wizard is for users who want control. Removing the Wizard would lose the detailed configuration path.

---

### AD-044: Collapsible NavRail (Default Expanded)

**Context**: ADR-006 specified a slim 56dp icon-only NavRail. User feedback indicates this is unintuitive — new users can't identify sections without hovering. Reference dashboards show sidebars with visible text labels.

**Decision**: Make the NavRail collapsible between:
- **Expanded** (~200dp): Icon + text label — default for new users
- **Collapsed** (56dp): Icon-only with hover tooltips — current behavior

State persists in `localStorage("inframap_navrail_collapsed")`. Toggle button at the top of the sidebar. Animated transition (200ms).

**Supersedes**: ADR-006's "slim icon-only left navigation rail" guideline (CONTEXT.md #115).

---

### AD-045: Custom ImageVector Icons for WASM Reliability

**Context**: Material Icons Extended (`compose.materialIconsExtended`) icons render as blank squares in Compose WASM due to Skiko/Canvas2D rendering issues or tree-shaking.

**Decision**: Define the 6 critical NavRail icons as custom `ImageVector` constants with inline SVG path data in `designsystem/Icons.kt`. This eliminates dependency on Material Extended icon rendering for navigation-critical icons.

**Quality gate**: Playwright visual regression test captures the NavRail region; any broken icon causes pixel-diff failure in CI.

---

### AD-046: i18n with PT-BR Primary, EN Fallback

**Context**: The UI currently mixes Portuguese (empty states, tour steps) and English (dashboard KPIs, login form, metric labels). Many strings are hardcoded in composables.

**Decision**: Adopt Compose Resources i18n:
- `values/strings.xml`: PT-BR (primary locale)
- `values-en/strings.xml`: EN (fallback)
- All user-facing strings in composables must use `stringResource(Res.string.*)` — no hardcoded text
- The existing Gradle-generated `Res` (not the deleted hand-written one) is the only string accessor

---

## Positive Consequences

- First-time users get a coherent, sequenced onboarding experience with one-click setup option
- Icons render reliably across all browser/WASM environments with a CI quality gate
- NavRail is immediately understandable (expanded with labels) while remaining customizable
- i18n foundation enables future language additions without code changes

## Negative Consequences & Mitigation

- `OnboardingCoordinator` adds a new singleton — mitigated by keeping it stateless (reads LocalStorage, no own state)
- Custom `ImageVector` paths are verbose — mitigated by isolating in a single `Icons.kt` file
- Expanded NavRail takes more horizontal space — mitigated by making it collapsible with persistent state
