# RFC-022: Material Design 3 Design System & Motion Architecture Spec

> **Status**: Accepted  
> **Date**: 2026-08-27  
> **Related ADR**: ADR-010  
> **Supervises**: Tickets T44–T48  

---

## Problem Statement

Homelab operators and network engineers spend extended periods monitoring infrastructure topologies, inspecting discovered devices, and troubleshooting network state. The current UI suffers from:
1. **Excessive Visual Contrast & Halation**: Near-pitch-black canvas (`#121214`) paired with pure white text (`#f4f4f5`) creates an aggressive 17.1:1 contrast ratio, leading to eye strain and optical fatigue.
2. **Abrupt, Jerky Navigation Transitions**: Switching between top-level navigation tabs (`Dashboard`, `Devices`, `Topology`, `Subnets`) instantaneously swaps DOM subtrees without spatial cues or easing curves.
3. **Flat, Static Interactive States**: Buttons and cards lack physical spring responsiveness on hover and click.
4. **Static Grey Loading Blocks**: Asynchronous data queries render heavy, static grey boxes that fail to indicate background network activity.
5. **Abrupt Overlay Entrances**: Modals, confirmation dialogs, and the Command Palette (`⌘K`) snap onto the screen without entrance scaling or deceleration curves.

---

## Solution

Evolve InfraMap's design system to the **Google Material Design 3 (M3)** specification:
1. **M3 Soft Dark Slate Tonal System**: Replace hard borders and pitch-black backgrounds with a 5-tier Surface Container hierarchy (`surfaceContainerLowest` `#0E0E12`, `surfaceContainerLow` `#18181D`, `surfaceContainer` `#1E1D24`, `surfaceContainerHigh` `#28272F`, `surfaceContainerHighest` `#33323C`) and an ergonomic 13.1:1 text contrast (`onSurface` `#E4E1E6` on Tone 12) meeting WCAG AAA guidelines.
2. **Material 3 Motion Architecture**:
   - **M3 Fade Through** for peer NavRail tab switching (`scaleIn(0.92f -> 1.0f) + fadeIn()` over 350ms with `EmphasizedDecelerate`).
   - **M3 Shared Axis X** for hierarchical master-detail navigation (`Devices` $\leftrightarrow$ `DeviceDetail`).
   - **M3 Shared Axis Z** for authentication state entry (`Splash` $\rightarrow$ `Dashboard`).
3. **Physics-Based Spring Micro-Interactions**: Implement `Modifier.m3InteractiveScale()` compressing controls to `0.96f` on press with spring physics (`dampingRatio = 0.7f`) and subtle `1.015f` hover elevation.
4. **Continuous 45° Linear Gradient Shimmer Wave**: Replace static grey boxes with a 1200ms linear gradient sweep (`#1E1D24` $\rightarrow$ `#2A2932` $\rightarrow$ `#1E1D24`) and smooth crossfade upon data arrival.
5. **M3 Emphasized Container Scale for Modals**: Overlay containers and the Command Palette scale in from `0.88f` to `1.0f` with `EmphasizedDecelerate` over 350ms and exit cleanly in 200ms over a 50% backdrop scrim.

---

## User Stories

1. As an operator, I want the dark theme background and cards to have soft, harmonious tonal contrast, so that I can monitor my infrastructure for hours without visual fatigue or text halation.
2. As an operator, I want tab switching in the navigation rail to glide seamlessly with M3 Fade Through, so that screen transitions feel fluid and grounded.
3. As an operator, I want drilling down into a device detail screen to slide laterally with M3 Shared Axis X, so that I maintain clear spatial context of where I am in the hierarchy.
4. As an operator, I want buttons and clickable cards to physically react with spring compression when clicked, so that I get immediate tactile feedback on my actions.
5. As an operator, I want hover states on cards and buttons to gently elevate with surface tonal shifts, so that actionable elements stand out effortlessly under my cursor.
6. As an operator, I want loading states to display an elegant 45-degree diagonal shimmer sweep, so that I know network activity is actively in progress.
7. As an operator, I want the transition from loading skeleton to live dashboard cards to crossfade smoothly, so that the screen does not abruptly flicker or jump.
8. As an operator, I want the Command Palette (`⌘K`) to scale in gracefully from the center with a soft backdrop scrim, so that quick navigation feels focused and polished.
9. As an operator, I want confirmation and deletion dialogs to enter with M3 container scale, so that destructive confirmations feel deliberate and prominent.
10. As a developer, I want all M3 motion tokens, easing curves, and duration specs centralized in a reusable Kotlin module, so that all future screens automatically adhere to the design system.

---

## Implementation Decisions

### 1. Design System Tokens & Surface Container Hierarchy
- Centralize M3 color tokens in `frontend/src/commonMain/kotlin/com/inframap/frontend/designsystem/Color.kt`.
- Provide full 8-role Surface Container scale (`surfaceDim`, `surface`, `surfaceBright`, `surfaceContainerLowest`, `surfaceContainerLow`, `surfaceContainer`, `surfaceContainerHigh`, `surfaceContainerHighest`).
- Provide balanced Tone 80 / Tone 20 primary (`#D0BCFF` / `#381E72`), secondary (`#6EE7B7` / `#003826`), tertiary (`#FCD34D` / `#452B00`), and error (`#FFB4AB` / `#690005`) palettes.
- Maintain backward-compatible aliases for existing component references (`InfraMapCanvasBg`, `InfraMapSurfaceBg`, `StatusOnline`, etc.).
- Update `Theme.kt` with `InfraMapShapes` (5-tier corner radii: `4dp`, `8dp`, `12dp`, `16dp`, `28dp`).

### 2. Motion Tokens & Easing Curves (`MotionTokens.kt`)
- `EasingTokens`: `Emphasized` (`cubic-bezier(0.2, 0.0, 0.0, 1.0)`), `EmphasizedDecelerate` (`(0.05, 0.7, 0.1, 1.0)`), `EmphasizedAccelerate` (`(0.3, 0.0, 0.8, 0.15)`), and `Standard`.
- `DurationTokens`: Short (50–200ms), Medium (250–400ms), Long (450–600ms), ExtraLong (700–1200ms).
- `SpringTokens`: Spatial spring (`dampingRatio = 0.8f`, `stiffness = Low`), Button press spring (`dampingRatio = 0.7f`, `stiffness = Medium`).

### 3. Transition Helpers & Screen Navigators (`MotionTransitions.kt`)
- `MotionTransitions.fadeThrough()`: Scale 0.92 $\rightarrow$ 1.0 + fade in/out.
- `MotionTransitions.sharedAxisX(forward)`: Lateral 30% slide + fade in/out.
- `MotionTransitions.sharedAxisZ(forward)`: Depth 0.85 $\rightarrow$ 1.0 scale + fade in/out.
- `MotionTransitions.dialogEnter() / dialogExit()`: Modal container scale (0.88 $\rightarrow$ 1.0) and scrim.

### 4. Interactive Components & Shimmer Wave Modifiers
- `Modifier.m3InteractiveScale(interactionSource, pressScale = 0.96f, hoverScale = 1.015f)`.
- `Modifier.m3Shimmer(shape, baseColor, highlightColor, durationMillis = 1200)`.
- `InfraMapButton`, `InfraMapOutlinedButton`, and `InfraMapCard` integrate `m3InteractiveScale`.
- `LoadingSkeleton.kt` and `Shimmer.kt` upgraded with diagonal 45° linear gradient sweeps.
- `CommandPaletteModal.kt` and dialogs upgraded with animated scale & scrim transitions.

---

## Testing Decisions

1. **Unit & State Tests (JVM)**:
   - Verify `MotionTokens` easing calculations and duration constants.
   - Verify `m3Shimmer` modifier instantiation and color gradient definitions.
   - Verify `InfraMapColorScheme` tonal token mappings and contrast ratios.
   - Verify `CardDefaults` and `ButtonDefaults` color role assignments.
2. **Browser E2E Tests (Playwright)**:
   - Assert `#inframap-app` renders with updated surface container colors.
   - Assert `CommandPalette` (`Meta+k` / `Control+k`) opens with M3 container transform overlay and closes on `Escape`.
   - Assert navigation between `Dashboard`, `Subnets`, and `Topology` completes smoothly with zero console exceptions.

---

## Out of Scope

- Light mode theme switcher toggle (reserved for future iteration; current scope focuses on M3 Soft Dark Slate).
- Complex 3D topology canvas particle transitions (topology WebGL/Skiko canvas maintains existing force-directed graph rendering).

---

## Further Notes

- Adheres to Guideline #140 in `CONTEXT.md`.
- All Compose Multiplatform motion and spring specifications execute seamlessly across Kotlin WASM (browser) and JVM desktop targets.
