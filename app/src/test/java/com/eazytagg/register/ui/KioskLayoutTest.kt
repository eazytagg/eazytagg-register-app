package com.eazytagg.register.ui

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w1280dp-h1280dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class KioskLayoutTest {
    @get:Rule val compose = createComposeRule()

    private fun render(width: Int, height: Int, largeText: Boolean = false) {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, if (largeText) 1.5f else 1f)) {
                PDynamicsTheme(DefaultAccent, false) {
                    AdaptiveKioskLayout(true, Modifier.requiredSize(width.dp, height.dp).testTag("app-window"),
                        brand = { compact ->
                            Box(Modifier.fillMaxSize().background(DefaultAccent).testTag("event-panel")) {
                                Text("Your event", Modifier.align(Alignment.TopStart).padding(12.dp), color = Color.White)
                                if (!compact) Box(Modifier.align(Alignment.BottomCenter)) { PoweredByLogo(onDark = true) }
                            }
                        },
                        body = {
                            RegistrationFormLayout(rememberScrollState(), "", false, "Submit", {}, {}) {
                                Text("Your details")
                                repeat(20) { i ->
                                    TextField("", {}, label = { Text("Field ${i + 1}") }, modifier = Modifier.fillMaxWidth().testTag("field-${i + 1}"))
                                }
                            }
                        }, footer = { PoweredByLogo() })
                }
            }
        }
    }

    private fun actionsAndBrandStayVisible() {
        compose.onNodeWithText("Submit").assertIsDisplayed()
        compose.onNodeWithText("Cancel").assertIsDisplayed()
        compose.onNodeWithContentDescription("Popular Dynamics").assertIsDisplayed()
        compose.onNodeWithTag("field-20").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Submit").assertIsDisplayed()
        compose.onNodeWithText("Cancel").assertIsDisplayed()
        val image = compose.onNodeWithContentDescription("Popular Dynamics").fetchSemanticsNode().boundsInRoot
        assertTrue("The full wordmark should be wider than the icon", image.width > image.height * 2f)
    }

    private fun snapshot(name: String) {
        val folder = File("build/reports/ui-screenshots").apply { mkdirs() }
        File(folder, "$name.png").outputStream().use { out ->
            compose.onNodeWithTag("app-window").captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, out)
        }
    }

    @Test fun phonePortraitLeavesRoomForScrollableFormAndButtons() {
        render(360, 740); actionsAndBrandStayVisible()
        val header = compose.onNodeWithTag("event-panel").fetchSemanticsNode().boundsInRoot
        assertTrue("Portrait header must not fill the screen", header.height <= 150f)

    }
    @Test fun portraitTabletUsesAHeaderRatherThanASidebar() {
        render(800, 1100); actionsAndBrandStayVisible()
        val header = compose.onNodeWithTag("event-panel").fetchSemanticsNode().boundsInRoot
        assertTrue(header.width > 700f && header.height < 160f)
    }
    @Test fun landscapeTabletKeepsTheSidePanelAndBothActions() {
        render(1080, 640); actionsAndBrandStayVisible()
        val panel = compose.onNodeWithTag("event-panel").fetchSemanticsNode().boundsInRoot
        assertTrue(panel.height > panel.width)

    }
    @Test fun narrowPhoneKeepsBothActionsReadable() {
        render(320, 620); actionsAndBrandStayVisible()
    }
    @Test fun keyboardSizedWindowStillLeavesFormAndActionsAccessible() {
        render(360, 380); actionsAndBrandStayVisible()
    }
    @Test fun largerSystemTextDoesNotHideTheActions() {
        render(360, 740, largeText = true); actionsAndBrandStayVisible()
    }
    @Test fun checkInActionsAreCentredAndAccessibleInPortrait() {
        compose.setContent { PDynamicsTheme(DefaultAccent, false) {
            Box(Modifier.requiredSize(360.dp, 740.dp)) { CheckInActions("Check in", "Scan or enter your PIN", "Scan my QR code", "Enter my PIN", "Back", true, {}, {}, {}) }
        } }
        compose.onNodeWithText("Scan my QR code").assertIsDisplayed()
        compose.onNodeWithText("Enter my PIN").assertIsDisplayed()
        compose.onNodeWithText("Back").assertIsDisplayed()
        val scan = compose.onNodeWithText("Scan my QR code").fetchSemanticsNode().boundsInRoot
        val back = compose.onNodeWithText("Back").fetchSemanticsNode().boundsInRoot
        assertTrue(scan.top > 180f && back.bottom < 620f)
    }
    @Test fun checkInActionsRemainAccessibleWithKeyboardSizedHeight() {
        compose.setContent { PDynamicsTheme(DefaultAccent, true) {
            Box(Modifier.requiredSize(360.dp, 300.dp)) { CheckInActions("Check in", "Scan or enter your PIN", "Scan my QR code", "Enter my PIN", "Back", true, {}, {}, {}) }
        } }
        compose.onNodeWithText("Back").performScrollTo().assertIsDisplayed()
    }

}
