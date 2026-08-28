package com.inframap.frontend.ui.splash

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import com.inframap.frontend.designsystem.InfraMapTheme
import org.junit.jupiter.api.Test

@OptIn(ExperimentalTestApi::class)
class SplashScreenTest {
    @Test
    fun splashScreenRendersAppTitleAndTagline() =
        runComposeUiTest {
            setContent {
                InfraMapTheme {
                    SplashScreen()
                }
            }
            onNodeWithText("InfraMap").assertIsDisplayed()
            onNodeWithText("Infrastructure Discovery & Mapping").assertIsDisplayed()
        }
}
