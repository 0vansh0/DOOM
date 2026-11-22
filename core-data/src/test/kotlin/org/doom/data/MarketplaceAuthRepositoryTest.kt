package org.doom.data

import java.net.URL
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MarketplaceAuthRepositoryTest {
    @Test fun lateSuccessfulResponseCannotRestoreSessionAfterLogout() = runBlocking {
        val sessions = MarketplaceSessionManager { 10_000L }
        val requestStarted = CompletableDeferred<Unit>()
        val response = CompletableDeferred<AuthHttpResponse>()
        val repository = MarketplaceAuthRepository(
            URL("https://marketplace.example"),
            sessions,
            object : AuthHttpTransport {
                override suspend fun post(url: URL, body: JSONObject): AuthHttpResponse {
                    assertEquals("/v1/auth/login", url.path)
                    assertEquals("tester@example.org", body.getString("email"))
                    requestStarted.complete(Unit)
                    return response.await()
                }
            }
        )

        val login = async { repository.login("tester@example.org", "not-logged") }
        requestStarted.await()
        repository.logout()
        response.complete(
            AuthHttpResponse(
                200,
                """{"data":{"tokenType":"Bearer","expiresIn":900,"accessToken":"late-token","user":{"email":"tester@example.org"}}}"""
            )
        )

        val result = login.await()
        assertTrue(result is AuthResult.Failure && result.reason == AuthFailure.SESSION_INVALIDATED)
        assertNull(sessions.session.value)
        assertNull(sessions.token())
    }
}
