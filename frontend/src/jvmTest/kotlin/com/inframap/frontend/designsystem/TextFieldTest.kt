package com.inframap.frontend.designsystem

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import org.junit.jupiter.api.Test

@OptIn(ExperimentalTestApi::class)
class TextFieldTest {
    @Test
    fun textFieldRendersLabel() =
        runComposeUiTest {
            setContent {
                InfraMapTheme {
                    InfraMapTextField(
                        value = "",
                        onValueChange = {},
                        label = "Username",
                    )
                }
            }
            onNodeWithText("Username").assertIsDisplayed()
        }

    @Test
    fun textFieldRendersValue() =
        runComposeUiTest {
            setContent {
                InfraMapTheme {
                    InfraMapTextField(
                        value = "admin",
                        onValueChange = {},
                        label = "Username",
                    )
                }
            }
            onNodeWithText("admin").assertIsDisplayed()
        }

    @Test
    fun textFieldRendersErrorMessage() =
        runComposeUiTest {
            setContent {
                InfraMapTheme {
                    InfraMapTextField(
                        value = "",
                        onValueChange = {},
                        label = "Email",
                        error = "Email is required",
                    )
                }
            }
            onNodeWithText("Email is required").assertIsDisplayed()
        }

    @Test
    fun textFieldWithoutErrorDoesNotShowErrorText() =
        runComposeUiTest {
            setContent {
                InfraMapTheme {
                    InfraMapTextField(
                        value = "",
                        onValueChange = {},
                        label = "Email",
                        error = null,
                    )
                }
            }
            onNodeWithText("Email is required").assertDoesNotExist()
        }
}
