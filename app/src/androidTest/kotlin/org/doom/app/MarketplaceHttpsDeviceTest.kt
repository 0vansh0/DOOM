package org.doom.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.doom.core.data.BuildConfig as DataBuildConfig
import org.doom.data.AuthResult
import org.doom.data.FeedbackFailure
import org.doom.data.FeedbackReport
import org.doom.data.FeedbackResult
import org.doom.data.MarketplaceAuthRepository
import org.doom.data.MarketplaceFeedbackRepository
import org.doom.data.MarketplaceSessionManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MarketplaceHttpsDeviceTest {
    @Test
    fun registersLogsInAndSubmitsConsentedFeedbackThroughTrustedHttps() = runBlocking {
        assertTrue("Debug API origin must use HTTPS", DataBuildConfig.MARKETPLACE_BASE_URL.startsWith("https://"))
        assertTrue("Physical-device tunnel must target localhost", DataBuildConfig.MARKETPLACE_BASE_URL.startsWith("https://localhost:"))

        val sessions = MarketplaceSessionManager()
        val auth = MarketplaceAuthRepository(sessions)
        val suffix = UUID.randomUUID().toString()
        val email = "doom-device-$suffix@example.test"
        val password = "Doom-${UUID.randomUUID()}-${UUID.randomUUID()}"

        val registration = auth.register(email, password, "DOOM device integration")
        assertTrue("Registration should succeed against the isolated HTTPS API: $registration", registration is AuthResult.Success)
        assertTrue((registration as AuthResult.Success).expiresAtMillis > System.currentTimeMillis())

        auth.logout()
        assertEquals(null, sessions.token())
        val invalid = auth.login(email, "incorrect-${UUID.randomUUID()}")
        assertTrue("Invalid credentials should not establish a session", invalid is AuthResult.Failure)
        assertEquals(null, sessions.token())

        val login = auth.login(email, password)
        assertTrue("Login should succeed", login is AuthResult.Success)
        assertNotNull("Access token must remain in the in-memory session", sessions.token())
        val signedInAt = System.currentTimeMillis()
        assertTrue(requireNotNull(sessions.session.value).expiresAtMillis in (signedInAt + 850_000)..(signedInAt + 950_000))

        val report = FeedbackReport(
            category = "Marketplace",
            title = "13B5A Android HTTPS integration report",
            description = "Synthetic device integration test; no device identifiers are included.",
            steps = "Run the Android HTTPS integration test.",
            expected = "The isolated feedback endpoint accepts a consented report.",
            actual = "Checking server-confirmed feedback submission.",
            appVersion = "0.1.0-beta.1",
            androidVersion = "API 36",
            modId = "",
        )
        val feedback = MarketplaceFeedbackRepository(sessions)
        assertTrue(feedback.submit(report, consent = false) is FeedbackResult.Failure)
        val submitted = feedback.submit(report, consent = true)
        assertTrue("Feedback submission must be server-confirmed", submitted is FeedbackResult.Submitted)
        assertTrue((submitted as FeedbackResult.Submitted).reportId.isNotBlank())

        sessions.establish(email, "invalid-test-token", 900)
        val unauthorized = feedback.submit(report.copy(title = "Unauthorized token test"), consent = true)
        assertEquals(FeedbackFailure.UNAUTHORIZED, (unauthorized as FeedbackResult.Failure).reason)
        assertEquals("HTTP 401 must clear the in-memory session", null, sessions.token())
        auth.logout()
    }
}
