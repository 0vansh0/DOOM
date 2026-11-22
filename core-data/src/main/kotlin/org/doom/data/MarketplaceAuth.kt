package org.doom.data

import java.io.IOException
import java.net.ConnectException
import java.net.UnknownHostException
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.doom.core.data.BuildConfig
import org.json.JSONObject

data class MarketplaceSession(val email: String, val expiresAtMillis: Long)

@Singleton
class MarketplaceSessionManager @Inject constructor() {
    private var clock: () -> Long = System::currentTimeMillis
    private val _session = MutableStateFlow<MarketplaceSession?>(null)
    val session = _session.asStateFlow()
    private var accessToken: String? = null
    private var authAttemptGeneration: Long = 0L

    @Synchronized
    fun token(): String? {
        if ((_session.value?.expiresAtMillis ?: 0L) <= clock()) clear()
        return accessToken
    }

    @Synchronized
    fun establish(email: String, token: String, expiresInSeconds: Long) {
        require(email.isNotBlank() && token.isNotBlank() && expiresInSeconds in 60..3600)
        authAttemptGeneration += 1
        accessToken = token
        _session.value = MarketplaceSession(email, clock() + expiresInSeconds * 1000L)
    }

    @Synchronized
    internal fun beginAuthenticationAttempt(): Long {
        authAttemptGeneration += 1
        return authAttemptGeneration
    }

    @Synchronized
    internal fun establishIfCurrentAttempt(
        generation: Long,
        email: String,
        token: String,
        expiresInSeconds: Long
    ): Boolean {
        require(email.isNotBlank() && token.isNotBlank() && expiresInSeconds in 60..3600)
        if (generation != authAttemptGeneration) return false
        accessToken = token
        _session.value = MarketplaceSession(email, clock() + expiresInSeconds * 1000L)
        return true
    }

    @Synchronized
    fun clear() {
        authAttemptGeneration += 1
        accessToken = null
        _session.value = null
    }

    internal constructor(clock: () -> Long) : this() {
        this.clock = clock
    }
}

enum class AuthFailure { INVALID_INPUT, INVALID_CREDENTIALS, EMAIL_EXISTS, RATE_LIMITED, SERVER, NETWORK, INSECURE_ENDPOINT, SESSION_INVALIDATED }

sealed interface AuthResult {
    data class Success(val email: String, val expiresAtMillis: Long) : AuthResult
    data class Failure(val reason: AuthFailure, val message: String) : AuthResult
}

data class AuthHttpResponse(val status: Int, val body: String)

fun interface AuthHttpTransport {
    suspend fun post(url: URL, body: JSONObject): AuthHttpResponse

    suspend fun postBearer(url: URL, body: JSONObject, token: String): AuthHttpResponse = post(url, body)
}

@Singleton
class MarketplaceAuthRepository @Inject constructor(private val sessions: MarketplaceSessionManager) {
    private val baseUrl: URL = URL(BuildConfig.MARKETPLACE_BASE_URL)
    private val transport: AuthHttpTransport = UrlAuthHttpTransport()

    internal constructor(baseUrl: URL, sessions: MarketplaceSessionManager, transport: AuthHttpTransport) : this(sessions) {
        this.testBaseUrl = baseUrl
        this.testTransport = transport
    }

    private var testBaseUrl: URL? = null
    private var testTransport: AuthHttpTransport? = null
    private fun base() = testBaseUrl ?: baseUrl
    private fun http() = testTransport ?: transport

    suspend fun login(email: String, password: String): AuthResult = authenticate(email, password, null)

    suspend fun register(email: String, password: String, displayName: String): AuthResult =
        authenticate(email, password, displayName)

    fun logout() = sessions.clear()

    private suspend fun authenticate(emailInput: String, password: String, displayName: String?): AuthResult {
        val attemptGeneration = sessions.beginAuthenticationAttempt()
        val email = emailInput.trim().lowercase(Locale.ROOT)
        val registering = displayName != null
        if (!email.matches(Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) || password.isEmpty() ||
            password.length > 128 || (registering && (password.length < 12 || displayName!!.trim().length !in 2..80))) {
            return AuthResult.Failure(AuthFailure.INVALID_INPUT, "Check the email, password and account name.")
        }
        if (base().protocol != "https") return AuthResult.Failure(AuthFailure.INSECURE_ENDPOINT, "Authentication requires HTTPS.")
        val body = JSONObject().put("email", email).put("password", password)
        if (registering) body.put("displayName", displayName!!.trim())
        return try {
            val route = if (registering) "/v1/auth/register" else "/v1/auth/login"
            val target = URL(base(), route)
            require(target.protocol == "https" && target.host.equals(base().host, true) && target.port == base().port)
            val response = http().post(target, body)
            if (response.status !in 200..299) {
                val failure = when (response.status) {
                    400 -> AuthFailure.INVALID_INPUT
                    401 -> AuthFailure.INVALID_CREDENTIALS
                    409 -> AuthFailure.EMAIL_EXISTS
                    429 -> AuthFailure.RATE_LIMITED
                    else -> AuthFailure.SERVER
                }
                AuthResult.Failure(failure, errorMessage(response.body, "Authentication failed (HTTP ${response.status})."))
            } else {
                val data = JSONObject(response.body).getJSONObject("data")
                require(data.getString("tokenType").equals("Bearer", ignoreCase = true))
                val expiresIn = data.getLong("expiresIn")
                val user = data.getJSONObject("user")
                val actualEmail = user.getString("email")
                if (sessions.establishIfCurrentAttempt(attemptGeneration, actualEmail, data.getString("accessToken"), expiresIn)) {
                    AuthResult.Success(actualEmail, requireNotNull(sessions.session.value).expiresAtMillis)
                } else {
                    AuthResult.Failure(AuthFailure.SESSION_INVALIDATED, "Sign-in was cancelled. Please try again.")
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            AuthResult.Failure(AuthFailure.NETWORK, "Could not reach the marketplace. Check the connection and try again.")
        }
    }

    private fun errorMessage(body: String, fallback: String): String = runCatching {
        JSONObject(body).getJSONObject("error").getString("message").take(240)
    }.getOrDefault(fallback)
}

internal class UrlAuthHttpTransport : AuthHttpTransport {
    override suspend fun post(url: URL, body: JSONObject): AuthHttpResponse = send(url, body, null)

    override suspend fun postBearer(url: URL, body: JSONObject, token: String): AuthHttpResponse =
        send(url, body, token)

    private suspend fun send(url: URL, body: JSONObject, token: String?): AuthHttpResponse = withContext(Dispatchers.IO) {
        require(url.protocol == "https")
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.connectTimeout = 8_000
        connection.readTimeout = 15_000
        connection.instanceFollowRedirects = false
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/json")
        connection.setRequestProperty("Accept", "application/json")
        if (token != null) connection.setRequestProperty("Authorization", "Bearer $token")
        val job = currentCoroutineContext()[kotlinx.coroutines.Job]
        val cancellation = job?.invokeOnCompletion { connection.disconnect() }
        try {
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            currentCoroutineContext().ensureActive()
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.use { input -> input.readBytes().also { require(it.size <= 64 * 1024) }.toString(Charsets.UTF_8) }.orEmpty()
            AuthHttpResponse(status, response)
        } finally {
            cancellation?.dispose()
            connection.disconnect()
        }
    }
}
