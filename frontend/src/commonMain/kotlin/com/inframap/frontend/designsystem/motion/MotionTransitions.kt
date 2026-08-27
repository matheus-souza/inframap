@file:Suppress("detekt:MaxLineLength")

package com.inframap.frontend.designsystem.motion

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import com.inframap.frontend.navigation.Route

/**
 * Material Design 3 Navigation & Container Motion Transitions for InfraMap.
 *
 * Provides standardized ContentTransform and Enter/Exit transitions:
 * - Fade Through: Peer tab switches and top-level NavRail navigation.
 * - Shared Axis X: Lateral hierarchical master-detail and step navigation.
 * - Shared Axis Z: Depth drill-in navigation (Splash -> Login / Dashboard).
 * - Dialog & Modal Scale: Emphasized container scale entrance and exit with backdrop scrim.
 */
object MotionTransitions {
    /**
     * Material Design 3 Fade Through transition.
     *
     * Used for top-level navigation destination switches (e.g. NavRail peer destinations).
     * The incoming content scales in from 92% to 100% while fading in,
     * while the outgoing content fades out.
     */
    fun fadeThrough(durationMillis: Int = MotionTokens.DurationTokens.Medium2): ContentTransform {
        val enter =
            fadeIn(
                animationSpec = MotionTokens.Specs.emphasizedDecelerate(durationMillis),
            ) +
                scaleIn(
                    initialScale = 0.92f,
                    animationSpec = MotionTokens.Specs.emphasizedDecelerate(durationMillis),
                )
        val exitDuration = (durationMillis * 0.35f).toInt().coerceAtLeast(MotionTokens.DurationTokens.Short1)
        val exit =
            fadeOut(
                animationSpec = MotionTokens.Specs.emphasizedAccelerate(exitDuration),
            )
        return enter togetherWith exit
    }

    /**
     * Material Design 3 Shared Axis X transition.
     *
     * Used for lateral hierarchical navigation such as master-detail flows or step-by-step forms.
     *
     * @param forward True if navigating deeper/forward (target enters from right, initial exits to left);
     *                false if navigating back (target enters from left, initial exits to right).
     * @param slideDistance Offset distance in pixels for the horizontal slide transition.
     * @param durationMillis Duration of the transition in milliseconds.
     */
    fun sharedAxisX(
        forward: Boolean = true,
        slideDistance: Int = 96,
        durationMillis: Int = MotionTokens.DurationTokens.Medium2,
    ): ContentTransform {
        val enterOffset = if (forward) slideDistance else -slideDistance
        val exitOffset = if (forward) -slideDistance else slideDistance

        val enter =
            slideInHorizontally(
                initialOffsetX = { enterOffset },
                animationSpec = MotionTokens.Specs.emphasizedDecelerate(durationMillis),
            ) +
                fadeIn(
                    animationSpec = MotionTokens.Specs.emphasizedDecelerate(durationMillis),
                )
        val exit =
            slideOutHorizontally(
                targetOffsetX = { exitOffset },
                animationSpec = MotionTokens.Specs.emphasizedAccelerate(durationMillis),
            ) +
                fadeOut(
                    animationSpec = MotionTokens.Specs.emphasizedAccelerate(durationMillis),
                )
        return enter togetherWith exit
    }

    /**
     * Material Design 3 Shared Axis Z transition.
     *
     * Used for depth transitions in the UI hierarchy (e.g. Splash -> Login / Dashboard, drill-in).
     *
     * @param forward True if navigating deeper into the application (target scales up from [initialScale] to 1.0,
     *                initial scales up from 1.0 to [targetScale]);
     *                false if navigating up/backwards (target scales down from [targetScale] to 1.0,
     *                initial scales down from 1.0 to [initialScale]).
     * @param initialScale Scale factor for the smaller depth state (default 0.8f).
     * @param targetScale Scale factor for the larger depth state (default 1.1f).
     * @param durationMillis Duration of the transition in milliseconds.
     */
    fun sharedAxisZ(
        forward: Boolean = true,
        initialScale: Float = 0.8f,
        targetScale: Float = 1.1f,
        durationMillis: Int = MotionTokens.DurationTokens.Medium4,
    ): ContentTransform {
        val enterScale = if (forward) initialScale else targetScale
        val exitScale = if (forward) targetScale else initialScale

        val enter =
            scaleIn(
                initialScale = enterScale,
                animationSpec = MotionTokens.Specs.emphasizedDecelerate(durationMillis),
            ) +
                fadeIn(
                    animationSpec = MotionTokens.Specs.emphasizedDecelerate(durationMillis),
                )
        val exit =
            scaleOut(
                targetScale = exitScale,
                animationSpec = MotionTokens.Specs.emphasizedAccelerate(durationMillis),
            ) +
                fadeOut(
                    animationSpec = MotionTokens.Specs.emphasizedAccelerate(durationMillis),
                )
        return enter togetherWith exit
    }

    /**
     * Material Design 3 Emphasized Container Scale entrance for Modals and Dialogs.
     */
    fun dialogEnter(durationMillis: Int = MotionTokens.DurationTokens.Medium3): EnterTransition =
        scaleIn(
            initialScale = 0.88f,
            animationSpec = MotionTokens.Specs.emphasizedDecelerate(durationMillis),
        ) +
            fadeIn(
                animationSpec = MotionTokens.Specs.emphasizedDecelerate(durationMillis),
            )

    /**
     * Material Design 3 Emphasized Container Scale exit for Modals and Dialogs.
     */
    fun dialogExit(durationMillis: Int = MotionTokens.DurationTokens.Short4): ExitTransition =
        scaleOut(
            targetScale = 0.88f,
            animationSpec = MotionTokens.Specs.emphasizedAccelerate(durationMillis),
        ) +
            fadeOut(
                animationSpec = MotionTokens.Specs.emphasizedAccelerate(durationMillis),
            )

    /**
     * Material Design 3 Scrim backdrop fade entrance.
     */
    fun dialogScrimEnter(durationMillis: Int = MotionTokens.DurationTokens.Medium1): EnterTransition =
        fadeIn(
            animationSpec = MotionTokens.Specs.linear(durationMillis),
        )

    /**
     * Material Design 3 Scrim backdrop fade exit.
     */
    fun dialogScrimExit(durationMillis: Int = MotionTokens.DurationTokens.Short4): ExitTransition =
        fadeOut(
            animationSpec = MotionTokens.Specs.linear(durationMillis),
        )

    /**
     * Resolves the navigation transition for scaffold routes.
     */
    fun scaffoldTransition(
        initialRoute: Route,
        targetRoute: Route,
    ): ContentTransform = resolveScaffoldTransition(initialRoute, targetRoute)

    /**
     * Resolves the navigation transition for top-level application routes.
     */
    fun appTransition(
        initialRoute: Route,
        targetRoute: Route,
    ): ContentTransform = resolveAppTransition(initialRoute, targetRoute)
}

private fun resolveScaffoldTransition(
    initialRoute: Route,
    targetRoute: Route,
): ContentTransform {
    val isDeviceInitial = isDeviceRoute(initialRoute)
    val isDeviceTarget = isDeviceRoute(targetRoute)
    val isSubnetInitial = isSubnetRoute(initialRoute)
    val isSubnetTarget = isSubnetRoute(targetRoute)
    val isDiscoveryInitial = isDiscoveryRoute(initialRoute)
    val isDiscoveryTarget = isDiscoveryRoute(targetRoute)

    return when {
        isDeviceInitial && isDeviceTarget -> {
            val initialLevel = deviceHierarchyLevel(initialRoute)
            val targetLevel = deviceHierarchyLevel(targetRoute)
            if (initialLevel != targetLevel) {
                MotionTransitions.sharedAxisX(forward = targetLevel > initialLevel)
            } else {
                MotionTransitions.fadeThrough()
            }
        }
        isSubnetInitial && isSubnetTarget -> {
            MotionTransitions.sharedAxisX(forward = targetRoute is Route.CreateSubnet)
        }
        isDiscoveryInitial && isDiscoveryTarget -> {
            MotionTransitions.sharedAxisX(forward = targetRoute is Route.CreateDiscoverySource)
        }
        else -> MotionTransitions.fadeThrough()
    }
}

private fun resolveAppTransition(
    initialRoute: Route,
    targetRoute: Route,
): ContentTransform {
    val isScaffoldInitial = isMainScaffoldRoute(initialRoute)
    val isScaffoldTarget = isMainScaffoldRoute(targetRoute)

    return if (isScaffoldInitial && isScaffoldTarget) {
        EnterTransition.None togetherWith ExitTransition.None
    } else {
        val initialDepth = appRouteDepth(initialRoute)
        val targetDepth = appRouteDepth(targetRoute)
        MotionTransitions.sharedAxisZ(forward = targetDepth >= initialDepth)
    }
}

private fun isDeviceRoute(route: Route): Boolean =
    route is Route.Devices ||
        route is Route.DeviceDetail ||
        route is Route.CreateDevice ||
        route is Route.EditDevice

private fun deviceHierarchyLevel(route: Route): Int =
    when (route) {
        Route.Devices -> 0
        is Route.DeviceDetail, Route.CreateDevice -> 1
        is Route.EditDevice -> 2
        else -> 0
    }

private fun isSubnetRoute(route: Route): Boolean {
    val isSubnets = route is Route.Subnets
    val isCreate = route is Route.CreateSubnet
    return isSubnets || isCreate
}

private fun isDiscoveryRoute(route: Route): Boolean {
    val isSources = route is Route.DiscoverySources
    val isCreate = route is Route.CreateDiscoverySource
    return isSources || isCreate
}

private fun isMainScaffoldRoute(route: Route): Boolean {
    val isExcluded = route is Route.Splash || route is Route.Login || route is Route.Onboarding
    return !isExcluded
}

private fun appRouteDepth(route: Route): Int =
    when (route) {
        Route.Splash -> 0
        Route.Login, Route.Onboarding -> 1
        else -> 2
    }
