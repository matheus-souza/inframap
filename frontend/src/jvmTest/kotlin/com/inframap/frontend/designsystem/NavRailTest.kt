package com.inframap.frontend.designsystem

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.SpaceDashboard
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class NavRailTest {
    private val sampleItems =
        listOf(
            NavRailItem("Dashboard", Icons.Filled.SpaceDashboard, "dashboard"),
            NavRailItem("Devices", Icons.Filled.Dns, "devices"),
        )

    @Test
    fun navRailRendersItemsByContentDescription() =
        runComposeUiTest {
            setContent {
                InfraMapTheme {
                    InfraMapNavRail(
                        items = sampleItems,
                        selectedRoute = "dashboard",
                        onItemSelected = {},
                    )
                }
            }
            onNodeWithContentDescription("Dashboard").assertIsDisplayed()
            onNodeWithContentDescription("Devices").assertIsDisplayed()
        }

    @Test
    fun navRailItemSelectionTriggersCallback() =
        runComposeUiTest {
            var selected: String? = null
            setContent {
                InfraMapTheme {
                    InfraMapNavRail(
                        items = sampleItems,
                        selectedRoute = "dashboard",
                        onItemSelected = { selected = it },
                    )
                }
            }
            onNodeWithContentDescription("Devices").performClick()
            assertEquals("devices", selected)
        }
}
