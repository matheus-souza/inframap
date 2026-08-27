package com.inframap.frontend.designsystem.motion

import androidx.compose.animation.AnimatedContent
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import com.inframap.frontend.navigation.Route
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

@OptIn(ExperimentalTestApi::class)
class MotionTransitionsTest {
    @Test
    fun fadeThroughTransitionProducesValidTransform() {
        val transition = MotionTransitions.fadeThrough()
        assertNotNull(transition)
    }

    @Test
    fun sharedAxisXTransitionProducesValidTransform() {
        val forwardTransition = MotionTransitions.sharedAxisX(forward = true)
        val backwardTransition = MotionTransitions.sharedAxisX(forward = false)
        assertNotNull(forwardTransition)
        assertNotNull(backwardTransition)
    }

    @Test
    fun sharedAxisZTransitionProducesValidTransform() {
        val forwardTransition = MotionTransitions.sharedAxisZ(forward = true)
        val backwardTransition = MotionTransitions.sharedAxisZ(forward = false)
        assertNotNull(forwardTransition)
        assertNotNull(backwardTransition)
    }

    @Test
    fun scaffoldTransitionResolvesPeerAndMasterDetailTransforms() {
        // Peer transitions
        val peerTransition = MotionTransitions.scaffoldTransition(Route.Dashboard, Route.Devices)
        val stagingTransition = MotionTransitions.scaffoldTransition(Route.Staging, Route.Topology)
        assertNotNull(peerTransition)
        assertNotNull(stagingTransition)

        // Master-detail forward transitions
        val deviceDetailForward =
            MotionTransitions.scaffoldTransition(Route.Devices, Route.DeviceDetail("dev-1"))
        val subnetCreateForward =
            MotionTransitions.scaffoldTransition(Route.Subnets, Route.CreateSubnet())
        val discoveryCreateForward =
            MotionTransitions.scaffoldTransition(Route.DiscoverySources, Route.CreateDiscoverySource)
        assertNotNull(deviceDetailForward)
        assertNotNull(subnetCreateForward)
        assertNotNull(discoveryCreateForward)

        // Master-detail backward transitions
        val deviceDetailBackward =
            MotionTransitions.scaffoldTransition(Route.DeviceDetail("dev-1"), Route.Devices)
        val subnetCreateBackward =
            MotionTransitions.scaffoldTransition(Route.CreateSubnet(), Route.Subnets)
        val discoveryCreateBackward =
            MotionTransitions.scaffoldTransition(Route.CreateDiscoverySource, Route.DiscoverySources)
        assertNotNull(deviceDetailBackward)
        assertNotNull(subnetCreateBackward)
        assertNotNull(discoveryCreateBackward)
    }

    @Test
    fun appTransitionResolvesRootRouteTransforms() {
        val splashToLogin = MotionTransitions.appTransition(Route.Splash, Route.Login)
        val splashToDashboard = MotionTransitions.appTransition(Route.Splash, Route.Dashboard)
        val loginToDashboard = MotionTransitions.appTransition(Route.Login, Route.Dashboard)
        val intraScaffold = MotionTransitions.appTransition(Route.Dashboard, Route.Devices)

        assertNotNull(splashToLogin)
        assertNotNull(splashToDashboard)
        assertNotNull(loginToDashboard)
        assertNotNull(intraScaffold)
    }

    @Test
    fun fadeThroughAnimatesBetweenScreens() =
        runComposeUiTest {
            var screen by mutableStateOf("TabA")
            setContent {
                AnimatedContent(
                    targetState = screen,
                    transitionSpec = { MotionTransitions.fadeThrough() },
                ) { target ->
                    Text("Screen: $target")
                }
            }

            onNodeWithText("Screen: TabA").assertIsDisplayed()
            screen = "TabB"
            waitForIdle()
            onNodeWithText("Screen: TabB").assertIsDisplayed()
        }

    @Test
    fun sharedAxisXAnimatesForwardAndBackward() =
        runComposeUiTest {
            var isForward by mutableStateOf(true)
            var currentScreen by mutableStateOf("Master")

            setContent {
                AnimatedContent(
                    targetState = currentScreen,
                    transitionSpec = { MotionTransitions.sharedAxisX(forward = isForward) },
                ) { target ->
                    Text("Screen: $target")
                }
            }

            onNodeWithText("Screen: Master").assertIsDisplayed()

            // Navigate forward
            isForward = true
            currentScreen = "Detail"
            waitForIdle()
            onNodeWithText("Screen: Detail").assertIsDisplayed()

            // Navigate backward
            isForward = false
            currentScreen = "Master"
            waitForIdle()
            onNodeWithText("Screen: Master").assertIsDisplayed()
        }

    @Test
    fun sharedAxisZAnimatesDepthDrillIn() =
        runComposeUiTest {
            var isForward by mutableStateOf(true)
            var currentScreen by mutableStateOf("Splash")

            setContent {
                AnimatedContent(
                    targetState = currentScreen,
                    transitionSpec = { MotionTransitions.sharedAxisZ(forward = isForward) },
                ) { target ->
                    Text("Screen: $target")
                }
            }

            onNodeWithText("Screen: Splash").assertIsDisplayed()

            isForward = true
            currentScreen = "Dashboard"
            waitForIdle()
            onNodeWithText("Screen: Dashboard").assertIsDisplayed()
        }
}
