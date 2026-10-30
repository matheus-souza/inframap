package com.inframap.frontend.ui.subnets

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.inframap.frontend.data.dto.SubnetAddressDeviceDto
import com.inframap.frontend.data.dto.SubnetAddressDto
import com.inframap.frontend.data.dto.SubnetAddressMapResponse
import com.inframap.frontend.designsystem.InfraMapTheme
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@OptIn(ExperimentalTestApi::class)
class SubnetAddressMapScreenTest {
    private val map =
        SubnetAddressMapResponse(
            subnetId = "sub1",
            cidr = "192.0.2.0/30",
            total = 2,
            free = 1,
            reserved = 1,
            addresses =
                listOf(
                    SubnetAddressDto(
                        address = "192.0.2.1",
                        state = "reserved",
                        isGateway = true,
                        devices =
                            listOf(
                                SubnetAddressDeviceDto(id = "d1", hostname = "router", deviceType = "router", ipAssignment = "static"),
                            ),
                    ),
                    SubnetAddressDto(address = "192.0.2.2", state = "free"),
                ),
        )

    @Test
    fun showsCountsAndTheHolderOfTheSelectedAddress() =
        runComposeUiTest {
            var selected = ""
            var opened = ""
            setContent {
                InfraMapTheme {
                    SubnetAddressMapScreen(
                        state = SubnetAddressMapUiState(subnetId = "sub1", isLoading = false, map = map, selectedAddress = "192.0.2.1"),
                        actions =
                            SubnetAddressMapActions(
                                onAddressSelected = { selected = it },
                                onDeviceClicked = { opened = it },
                                onEditSubnetClicked = {},
                                onBackClicked = {},
                                onRetryClicked = {},
                            ),
                    )
                }
            }

            onNodeWithText("Reserved: 1").assertIsDisplayed()
            onNodeWithText("Free: 1").assertIsDisplayed()
            onNodeWithText("Subnet gateway").assertIsDisplayed()

            onNodeWithTag("address_192.0.2.2").performClick()
            assertEquals("192.0.2.2", selected)

            onNodeWithText("router · Router · Static").performClick()
            assertEquals("d1", opened)
        }

    @Test
    fun showsTheErrorWithARetry() =
        runComposeUiTest {
            var retried = false
            setContent {
                InfraMapTheme {
                    SubnetAddressMapScreen(
                        state =
                            SubnetAddressMapUiState(
                                subnetId = "sub1",
                                isLoading = false,
                                errorMessage =
                                    com.inframap.frontend.ui.util.UiText
                                        .DynamicString("too large"),
                            ),
                        actions =
                            SubnetAddressMapActions(
                                onAddressSelected = {},
                                onDeviceClicked = {},
                                onEditSubnetClicked = {},
                                onBackClicked = {},
                                onRetryClicked = { retried = true },
                            ),
                    )
                }
            }

            onNodeWithText("too large").assertIsDisplayed()
            onNodeWithText("Try Again").performClick()
            assertEquals(true, retried)
        }
}
