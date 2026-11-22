package org.doom.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test

class NavigationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun opensAllTopLevelDestinations() {
        compose.onNodeWithText("DOOM").assertIsDisplayed()
        compose.onNodeWithText("Browse local examples").performClick()
        compose.onNodeWithText("Local bundled examples").assertIsDisplayed()
        compose.onNode(hasText("Installed") and hasClickAction()).performClick()
        compose.onNode(hasText("Installed") and !hasClickAction()).assertIsDisplayed()
        compose.onNode(hasText("Settings") and hasClickAction()).performClick()
        compose.onNodeWithText("Theme").assertIsDisplayed()
        compose.onNode(hasText("Diagnostics") and hasClickAction()).performClick()
        compose.onNodeWithText("Device diagnostics").assertIsDisplayed()
        compose.onNodeWithText("Refresh scan").assertIsDisplayed()
    }

    @Test fun settingsOpensAccountAndBackReturnsToSettings() {
        compose.onNode(hasText("Settings") and hasClickAction()).performClick()
        compose.onNodeWithText("Theme").assertIsDisplayed()
        compose.onNodeWithText("Account").performClick()
        compose.onNodeWithText("DOOM account").assertIsDisplayed()
        compose.onNodeWithText("Sign In", ignoreCase = false).assertIsDisplayed()
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithText("Theme").assertIsDisplayed()
    }

    @Test fun feedbackRouteKeepsLocalExportAndShowsSignInGuidance() {
        compose.onNode(hasText("Settings") and hasClickAction()).performClick()
        compose.onNodeWithText("Beta feedback").performClick()
        compose.onNodeWithText("Save local report").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Sign in to DOOM").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Submission preview").performScrollTo().assertIsDisplayed()
    }
}
