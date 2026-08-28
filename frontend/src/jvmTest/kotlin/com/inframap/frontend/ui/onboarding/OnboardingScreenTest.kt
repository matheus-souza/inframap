package com.inframap.frontend.ui.onboarding

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.inframap.frontend.designsystem.InfraMapTheme
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@OptIn(ExperimentalTestApi::class)
class OnboardingScreenTest {
    @Test
    fun onboardingScreenRendersElements() =
        runComposeUiTest {
            setContent {
                InfraMapTheme {
                    OnboardingScreen(
                        state = OnboardingUiState(),
                        onUsernameChanged = {},
                        onEmailChanged = {},
                        onFullNameChanged = {},
                        onPasswordChanged = {},
                        onConfirmPasswordChanged = {},
                        onSetupClick = {},
                    )
                }
            }
            onNodeWithText("InfraMap").assertIsDisplayed()
            onNodeWithText("Create your admin account").assertIsDisplayed()
            onNodeWithText("Full Name").assertIsDisplayed()
            onNodeWithText("Email").assertIsDisplayed()
            onNodeWithText("Username").assertIsDisplayed()
            onNodeWithText("Password").assertIsDisplayed()
            onNodeWithText("Must be at least 12 characters").assertIsDisplayed()
            onNodeWithText("Confirm Password").assertIsDisplayed()
            onNodeWithText("Complete Setup").assertIsDisplayed()
        }

    @Test
    fun onboardingScreenShowsLoadingState() =
        runComposeUiTest {
            setContent {
                InfraMapTheme {
                    OnboardingScreen(
                        state = OnboardingUiState(isLoading = true),
                        onUsernameChanged = {},
                        onEmailChanged = {},
                        onFullNameChanged = {},
                        onPasswordChanged = {},
                        onConfirmPasswordChanged = {},
                        onSetupClick = {},
                    )
                }
            }
            onNodeWithText("Setting up...").assertIsDisplayed()
        }

    @Test
    fun setupButtonClickTriggersCallback() =
        runComposeUiTest {
            var clicked = false
            setContent {
                InfraMapTheme {
                    OnboardingScreen(
                        state = OnboardingUiState(username = "admin", fullName = "Admin User"),
                        onUsernameChanged = {},
                        onEmailChanged = {},
                        onFullNameChanged = {},
                        onPasswordChanged = {},
                        onConfirmPasswordChanged = {},
                        onSetupClick = { clicked = true },
                    )
                }
            }
            onNodeWithText("Complete Setup").performClick()
            assertEquals(true, clicked)
        }
}
