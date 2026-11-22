package org.doom.data

import java.net.URL
import java.net.UnknownHostException
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class MarketplaceFeedbackRepositoryTest {
    private fun report(category: String = "Bug") = FeedbackReport(
        category = category, title = "Overlay issue", description = "Ring did not appear",
        steps = "Connect charger", expected = "Ring appears", actual = "No ring",
        appVersion = "0.1.0", androidVersion = "16 (API 36)", modId = "sample.glow"
    )

    private fun sessions() = MarketplaceSessionManager { 1_000L }.also {
        it.establish("tester@example.org", "in-memory-secret", 120)
    }

    @Test fun submitsExactBackendFieldsWithConsentAndBearerToTrustedOrigin() = runBlocking {
        var requestUrl: URL? = null
        var requestBody: JSONObject? = null
        var bearer: String? = null
        val transport = object : AuthHttpTransport {
            override suspend fun post(url: URL, body: JSONObject) = error("Authenticated route required")
            override suspend fun postBearer(url: URL, body: JSONObject, token: String): AuthHttpResponse {
                requestUrl = url
                requestBody = body
                bearer = token
                return AuthHttpResponse(201, """{"data":{"id":"report-123","status":"open"}}""")
            }
        }
        val result = MarketplaceFeedbackRepository(URL("https://marketplace.example/"), sessions(), transport)
            .submit(report(), consent = true)

        assertEquals(FeedbackResult.Submitted("report-123", "open"), result)
        assertEquals("https", requestUrl?.protocol)
        assertEquals("marketplace.example", requestUrl?.host)
        assertEquals("/v1/feedback", requestUrl?.path)
        assertEquals("in-memory-secret", bearer)
        assertTrue(requestBody!!.getBoolean("consent"))
        val data = requestBody!!.getJSONObject("report")
        assertEquals(setOf("category", "title", "description", "steps", "expected", "actual", "appVersion", "androidVersion", "modId"),
            data.keys().asSequence().toSet())
        assertEquals("sample.glow", data.getString("modId"))
    }

    @Test fun missingConsentAndAuthenticationAreRejectedBeforeNetwork() = runBlocking {
        var called = false
        val transport = AuthHttpTransport { _, _ -> called = true; AuthHttpResponse(201, "{}") }
        val withSession = MarketplaceFeedbackRepository(URL("https://marketplace.example/"), sessions(), transport)
        assertEquals(FeedbackFailure.CONSENT_REQUIRED,
            (withSession.submit(report(), false) as FeedbackResult.Failure).reason)
        val withoutSession = MarketplaceFeedbackRepository(URL("https://marketplace.example/"),
            MarketplaceSessionManager { 1_000L }, transport)
        assertEquals(FeedbackFailure.SIGN_IN_REQUIRED,
            (withoutSession.submit(report(), true) as FeedbackResult.Failure).reason)
        assertFalse(called)
    }

    @Test fun invalidCategoryIsRejectedBeforeNetwork() = runBlocking {
        var called = false
        val transport = AuthHttpTransport { _, _ -> called = true; AuthHttpResponse(201, "{}") }
        val result = MarketplaceFeedbackRepository(URL("https://marketplace.example/"), sessions(), transport)
            .submit(report("Unknown"), true)
        assertEquals(FeedbackFailure.INVALID_REPORT, (result as FeedbackResult.Failure).reason)
        assertFalse(called)
    }

    @Test fun unauthorizedResponseClearsSessionAndRateLimitIsMapped() = runBlocking {
        val session = sessions()
        val unauthorized = MarketplaceFeedbackRepository(URL("https://marketplace.example/"), session,
            AuthHttpTransport { _, _ -> AuthHttpResponse(401, "{}") })
        assertEquals(FeedbackFailure.UNAUTHORIZED,
            (unauthorized.submit(report(), true) as FeedbackResult.Failure).reason)
        assertNull(session.token())

        val rateLimited = MarketplaceFeedbackRepository(URL("https://marketplace.example/"), sessions(),
            AuthHttpTransport { _, _ -> AuthHttpResponse(429, "{}") })
        assertEquals(FeedbackFailure.RATE_LIMITED,
            (rateLimited.submit(report(), true) as FeedbackResult.Failure).reason)
    }

    @Test fun offlineServerAndUnknownOutcomesAreDistinct() = runBlocking {
        fun repository(transport: AuthHttpTransport) = MarketplaceFeedbackRepository(
            URL("https://marketplace.example/"), sessions(), transport)
        assertEquals(FeedbackFailure.OFFLINE,
            (repository(AuthHttpTransport { _, _ -> throw UnknownHostException() }).submit(report(), true) as FeedbackResult.Failure).reason)
        assertEquals(FeedbackFailure.SERVER,
            (repository(AuthHttpTransport { _, _ -> AuthHttpResponse(503, "{}") }).submit(report(), true) as FeedbackResult.Failure).reason)
        assertEquals(FeedbackFailure.UNKNOWN_OUTCOME,
            (repository(AuthHttpTransport { _, _ -> throw java.io.IOException("read timed out") }).submit(report(), true) as FeedbackResult.Failure).reason)
    }

    @Test fun expiredTokenIsRejectedWithoutSendingRequest() = runBlocking {
        var now = 1_000L
        val session = MarketplaceSessionManager { now }
        session.establish("tester@example.org", "expired", 60)
        now += 61_000L
        var called = false
        val repository = MarketplaceFeedbackRepository(URL("https://marketplace.example/"), session,
            AuthHttpTransport { _, _ -> called = true; AuthHttpResponse(201, "{}") })
        assertEquals(FeedbackFailure.SIGN_IN_REQUIRED,
            (repository.submit(report(), true) as FeedbackResult.Failure).reason)
        assertFalse(called)
    }

    @Test fun insecureEndpointIsBlockedBeforeAuthorizationCanBeSent() = runBlocking {
        var called = false
        val repository = MarketplaceFeedbackRepository(URL("http://marketplace.example/"), sessions(),
            AuthHttpTransport { _, _ -> called = true; AuthHttpResponse(201, "{}") })
        assertEquals(FeedbackFailure.INSECURE_ENDPOINT,
            (repository.submit(report(), true) as FeedbackResult.Failure).reason)
        assertFalse(called)
    }
}
