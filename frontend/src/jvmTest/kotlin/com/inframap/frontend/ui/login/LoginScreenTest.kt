package com.inframap.frontend.ui.login

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.inframap.frontend.designsystem.InfraMapTheme
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@OptIn(ExperimentalTestApi::class)
class LoginScreenTest {
    @Test
    fun loginScreenRendersElements() =
        runComposeUiTest {
            setContent {
                InfraMapTheme {
                    LoginScreen(
                        state = LoginUiState(),
                        onUsernameChanged = {},
                        onPasswordChanged = {},
                        onLoginClick = {},
                    )
                }
            }
            onNodeWithText("InfraMap").assertIsDisplayed()
            onNodeWithText("Sign in to your instance").assertIsDisplayed()
            onNodeWithText("Username").assertIsDisplayed()
            onNodeWithText("Password").assertIsDisplayed()
            onNodeWithText("Sign In").assertIsDisplayed()
        }

    @Test
    fun loginScreenShowsLoadingState() =
        runComposeUiTest {
            setContent {
                InfraMapTheme {
                    LoginScreen(
                        state = LoginUiState(isLoading = true),
                        onUsernameChanged = {},
                        onPasswordChanged = {},
                        onLoginClick = {},
                    )
                }
            }
            onNodeWithText("Signing in...").assertIsDisplayed()
        }

    @Test
    fun loginButtonClickTriggersCallback() =
        runComposeUiTest {
            var clicked = false
            setContent {
                InfraMapTheme {
                    LoginScreen(
                        state = LoginUiState(username = "admin", password = "password"),
                        onUsernameChanged = {},
                        onPasswordChanged = {},
                        onLoginClick = { clicked = true },
                    )
                }
            }
            onNodeWithText("Sign In").performClick()
            assertEquals(true, clicked)
        }
}
