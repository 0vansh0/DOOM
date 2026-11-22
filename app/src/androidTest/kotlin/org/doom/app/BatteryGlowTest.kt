package org.doom.app

import android.provider.Settings
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assume.assumeFalse
import org.junit.Rule
import org.junit.Test

class BatteryGlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun installsPreviewsAndUninstallsWithOverlayDenied() {
        assumeFalse(Settings.canDrawOverlays(compose.activity))
        compose.onNodeWithText("Browse local examples").performClick()
        awaitText("Install Battery Glow")
        compose.onNodeWithText("Install Battery Glow").performClick()
        compose.onNode(hasText("Installed") and hasClickAction()).performClick()
        awaitText("Battery Glow")
        compose.onNode(hasText("Battery Glow") and hasClickAction()).performClick()
        compose.onNodeWithContentDescription("Enable mod").performScrollTo().assertIsOff()
            .performSemanticsAction(SemanticsActions.OnClick)
        compose.waitUntil(8_000) {
            compose.onAllNodesWithText("PERMISSION_REQUIRED", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Open Android overlay settings").assertExists()
        compose.onNodeWithContentDescription("Enable mod").assertIsOn()
        compose.onNodeWithContentDescription("In-app preview").performScrollTo().assertIsOn()
        val uninstall = hasText("Uninstall mod") and isEnabled()
        compose.waitUntil(8_000) { compose.onAllNodes(uninstall).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(uninstall).performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
        awaitText("Uninstall")
        compose.onNodeWithText("Uninstall").performClick()
        awaitText("This mod is unavailable.")
    }

    private fun awaitText(text: String) {
        compose.waitUntil(8_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }
}
