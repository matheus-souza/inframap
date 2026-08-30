package com.inframap.frontend.designsystem

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.inframap.frontend.designsystem.InfraMapIcons
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class NavRailTest {
    private val sampleItems =
        listOf(
            NavRailItem("Dashboard", InfraMapIcons.Dashboard, "dashboard"),
            NavRailItem("Devices", InfraMapIcons.Dns, "devices"),
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

    @Test
    fun navRailSlimModeRendersItemsByContentDescription() =
        runComposeUiTest {
            var selected: String? = null
            setContent {
                InfraMapTheme {
                    InfraMapNavRail(
                        items = sampleItems,
                        selectedRoute = "dashboard",
                        onItemSelected = { selected = it },
                        isExpanded = false,
                    )
                }
            }
            onNodeWithContentDescription("Dashboard").assertIsDisplayed()
            onNodeWithContentDescription("Devices").assertIsDisplayed()
            onNodeWithContentDescription("Devices").performClick()
            assertEquals("devices", selected)
        }

    @Test
    fun navRailToggleButtonTriggersCallback() =
        runComposeUiTest {
            var toggled = false
            setContent {
                InfraMapTheme {
                    InfraMapNavRail(
                        items = sampleItems,
                        selectedRoute = "dashboard",
                        onItemSelected = {},
                        isExpanded = true,
                        onToggleExpanded = { toggled = true },
                    )
                }
            }
            onNodeWithContentDescription("Collapse menu").performClick()
            assertEquals(true, toggled)
        }
}
