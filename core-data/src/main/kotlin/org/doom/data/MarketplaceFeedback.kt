package org.doom.data

import java.io.IOException
import java.net.ConnectException
import java.net.URL
import java.net.UnknownHostException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import org.doom.core.data.BuildConfig
import org.json.JSONObject

data class FeedbackReport(
    val category: String,
    val title: String,
    val description: String,
    val steps: String,
    val expected: String,
    val actual: String,
    val appVersion: String,
    val androidVersion: String,
    val modId: String
) {
    fun toJson(): JSONObject = JSONObject()
        .put("category", category)
        .put("title", title.trim())
        .put("description", description.trim())
        .put("steps", steps.trim())
        .put("expected", expected.trim())
        .put("actual", actual.trim())
        .put("appVersion", appVersion.trim())
        .put("androidVersion", androidVersion.trim())
        .put("modId", modId.trim())
}

enum class FeedbackFailure {
    INVALID_REPORT, CONSENT_REQUIRED, SIGN_IN_REQUIRED, UNAUTHORIZED, FORBIDDEN,
    RATE_LIMITED, OFFLINE, SERVER, UNKNOWN_OUTCOME, INSECURE_ENDPOINT
}

sealed interface FeedbackResult {
    data class Submitted(val reportId: String, val status: String) : FeedbackResult
    data class Failure(val reason: FeedbackFailure, val message: String) : FeedbackResult
}

fun interface FeedbackSubmitter {
    suspend fun submit(report: FeedbackReport, consent: Boolean): FeedbackResult
}

@Singleton
class MarketplaceFeedbackRepository @Inject constructor(
    private val sessions: MarketplaceSessionManager
) : FeedbackSubmitter {
    private val defaultBaseUrl = URL(BuildConfig.MARKETPLACE_BASE_URL)
    private val defaultTransport: AuthHttpTransport = UrlAuthHttpTransport()
    private var testBaseUrl: URL? = null
    private var testTransport: AuthHttpTransport? = null

    internal constructor(baseUrl: URL, sessions: MarketplaceSessionManager, transport: AuthHttpTransport) : this(sessions) {
        testBaseUrl = baseUrl
        testTransport = transport
    }

    override suspend fun submit(report: FeedbackReport, consent: Boolean): FeedbackResult {
        if (!consent) return FeedbackResult.Failure(FeedbackFailure.CONSENT_REQUIRED,
            "Confirm that you want to send this report to DOOM.")
        val token = sessions.token()
            ?: return FeedbackResult.Failure(FeedbackFailure.SIGN_IN_REQUIRED, "Sign in to submit feedback.")
        val invalid = validate(report)
        if (invalid != null) return FeedbackResult.Failure(FeedbackFailure.INVALID_REPORT, invalid)
        val base = testBaseUrl ?: defaultBaseUrl
        if (base.protocol != "https") return FeedbackResult.Failure(FeedbackFailure.INSECURE_ENDPOINT,
            "Feedback submission requires the configured HTTPS service.")
        val target = URL(base, "v1/feedback")
        if (target.protocol != "https" || !target.host.equals(base.host, ignoreCase = true) || target.port != base.port)
            return FeedbackResult.Failure(FeedbackFailure.INSECURE_ENDPOINT, "The feedback destination is not trusted.")
        return try {
            val response = (testTransport ?: defaultTransport).postBearer(
                target, JSONObject().put("consent", true).put("report", report.toJson()), token
            )
            when (response.status) {
                201 -> {
                    val data = JSONObject(response.body).getJSONObject("data")
                    val id = data.getString("id")
                    val status = data.getString("status")
                    if (id.isBlank() || status.isBlank()) FeedbackResult.Failure(
                        FeedbackFailure.UNKNOWN_OUTCOME, "The server response did not confirm a report identifier.")
                    else FeedbackResult.Submitted(id, status)
                }
                400, 422 -> FeedbackResult.Failure(FeedbackFailure.INVALID_REPORT,
                    response.errorMessage("The server rejected the report fields."))
                401 -> {
                    sessions.clear()
                    FeedbackResult.Failure(FeedbackFailure.UNAUTHORIZED, "Your session expired. Sign in again.")
                }
                403 -> FeedbackResult.Failure(FeedbackFailure.FORBIDDEN,
                    "This account is not authorized to submit feedback.")
                429 -> FeedbackResult.Failure(FeedbackFailure.RATE_LIMITED,
                    "Too many reports were submitted. Please wait and try again.")
                in 500..599 -> FeedbackResult.Failure(FeedbackFailure.SERVER,
                    "The feedback service is temporarily unavailable.")
                else -> FeedbackResult.Failure(FeedbackFailure.SERVER,
                    "Feedback submission failed (HTTP ${response.status}).")
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: UnknownHostException) {
            FeedbackResult.Failure(FeedbackFailure.OFFLINE, "The feedback service could not be reached. Save a local copy instead.")
        } catch (e: ConnectException) {
            FeedbackResult.Failure(FeedbackFailure.OFFLINE, "The feedback service could not be reached. Save a local copy instead.")
        } catch (e: IOException) {
            FeedbackResult.Failure(FeedbackFailure.UNKNOWN_OUTCOME,
                "The connection ended before DOOM could confirm whether the report was received. Do not resubmit automatically; check before trying again.")
        } catch (_: Exception) {
            FeedbackResult.Failure(FeedbackFailure.UNKNOWN_OUTCOME,
                "DOOM could not confirm the submission result. Do not resubmit automatically.")
        }
    }

    private fun validate(report: FeedbackReport): String? {
        val categories = setOf("Bug", "Suggestion", "Mod compatibility", "Marketplace", "Unexpected behavior")
        if (report.category !in categories) return "Choose a supported feedback category."
        if (report.title.trim().length !in 1..120) return "Enter a title of at most 120 characters."
        if (report.description.trim().length !in 1..2000) return "Enter a description of at most 2000 characters."
        if (report.steps.length > 2000 || report.expected.length > 2000 || report.actual.length > 2000)
            return "Feedback details must be at most 2000 characters each."
        if (report.appVersion.trim().length !in 1..120 || report.androidVersion.trim().length !in 1..120)
            return "App and Android versions are required."
        if (report.modId.length > 100) return "Mod reference must be at most 100 characters."
        return null
    }

    private fun AuthHttpResponse.errorMessage(fallback: String): String = runCatching {
        JSONObject(body).getJSONObject("error").getString("message").take(240)
    }.getOrDefault(fallback)
}
