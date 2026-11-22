package org.doom.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.doom.core.designsystem.DoomTheme
import org.doom.data.MarketplaceSession
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals

class AccountScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun loginValidatesFieldsAndCallsAuthentication() {
        var credentials: Pair<String, String>? = null
        compose.setContent {
            DoomTheme {
                AccountScreen(AccountUiState(), { email, password -> credentials = email to password },
                    { _, _, _ -> error("Registration should not be called") }, {}, {})
            }
        }

        compose.onNodeWithText("Email").performTextInput("invalid")
        compose.onNodeWithText("Sign In", ignoreCase = false).assertIsNotEnabled()
        compose.onNodeWithText("Email").performTextClearance()
        compose.onNodeWithText("Email").performTextInput("dev@example.org")
        compose.onNodeWithText("Password").performTextInput("test-password")
        compose.onNodeWithText("Sign In", ignoreCase = false).performClick()
        assertEquals("dev@example.org" to "test-password", credentials)
    }

    @Test fun accountShowsAuthenticationError() {
        compose.setContent {
            DoomTheme {
                AccountScreen(AccountUiState(error = "Authentication failed"), { _, _ -> }, { _, _, _ -> },
                    {}, {})
            }
        }
        compose.onNodeWithText("Authentication failed").assertIsDisplayed()
    }

    @Test fun signedInAccountCanLogOut() {
        var logoutCalled = false
        compose.setContent {
            DoomTheme {
                AccountScreen(AccountUiState(session = MarketplaceSession("dev@example.org", Long.MAX_VALUE)),
                    { _, _ -> }, { _, _, _ -> }, { logoutCalled = true }, {})
            }
        }
        compose.onNodeWithText("Signed in as dev@example.org").assertIsDisplayed()
        compose.onNodeWithText("Log out").performClick()
        assertEquals(true, logoutCalled)
    }

    @Test fun registrationModeAndExpiredSessionAreVisible() {
        compose.setContent {
            DoomTheme {
                AccountScreen(AccountUiState(expired = true), { _, _ -> }, { _, _, _ -> }, {}, {})
            }
        }
        compose.onNode(hasText("Register") and hasClickAction()).performClick()
        compose.onNodeWithText("Display name").assertIsDisplayed()
        compose.onNodeWithText("Your session expired. Sign in again.").assertIsDisplayed()
    }
}
