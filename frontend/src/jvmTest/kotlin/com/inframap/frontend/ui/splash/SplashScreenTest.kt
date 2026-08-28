package com.inframap.frontend.ui.splash

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import com.inframap.frontend.designsystem.InfraMapTheme
import com.inframap.frontend.generated.resources.Res
import com.inframap.frontend.generated.resources.splash_tagline
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.getString
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
            onNodeWithText(runBlocking { getString(Res.string.splash_tagline) }).assertIsDisplayed()
        }
}
