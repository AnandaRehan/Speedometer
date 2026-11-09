package com.ehan.speedometer

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ehan.speedometer.ui.theme.SpeedometerTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// SDK 35 runs on JDK 17+; SDK 36 would require JDK 21.
@Config(sdk = [35], qualifiers = "w360dp-h640dp-xxhdpi")
class MainScreenScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun mainScreen() {
        composeRule.setContent {
            SpeedometerTheme {
                MainScreen()
            }
        }
        // Saved to app/src/test/screenshots/<package>.<class>.<method>.png
        composeRule.onRoot().captureRoboImage()
    }
}
