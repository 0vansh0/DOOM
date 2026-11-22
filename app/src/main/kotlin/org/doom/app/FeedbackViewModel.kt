package org.doom.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.doom.data.FeedbackFailure
import org.doom.data.FeedbackReport
import org.doom.data.FeedbackResult
import org.doom.data.FeedbackSubmitter
import org.doom.data.MarketplaceSession
import org.doom.data.MarketplaceSessionManager

enum class FeedbackUiStatus {
    EDITING, VALIDATION_FAILURE, SIGN_IN_REQUIRED, CONSENT_REQUIRED, SUBMITTING,
    SUBMITTED, UNAUTHORIZED, RATE_LIMITED, OFFLINE, SERVER_ERROR, UNKNOWN_OUTCOME
}

data class FeedbackUiState(
    val status: FeedbackUiStatus = FeedbackUiStatus.EDITING,
    val session: MarketplaceSession? = null,
    val reportId: String? = null,
    val message: String? = null
) {
    val submitting: Boolean get() = status == FeedbackUiStatus.SUBMITTING
}

@HiltViewModel
class FeedbackViewModel @Inject constructor(
    private val repository: FeedbackSubmitter,
    sessions: MarketplaceSessionManager
) : ViewModel() {
    private val _state = MutableStateFlow(FeedbackUiState())
    val state = _state.asStateFlow()
    private var submissionInFlight = false

    init {
        viewModelScope.launch {
            sessions.session.collect { session ->
                _state.update { old ->
                    val next = when {
                        session != null -> old.status
                        old.status == FeedbackUiStatus.SUBMITTED -> old.status
                        old.status == FeedbackUiStatus.UNAUTHORIZED -> old.status
                        else -> FeedbackUiStatus.SIGN_IN_REQUIRED
                    }
                    old.copy(session = session, status = next)
                }
            }
        }
    }

    fun submit(report: FeedbackReport, consent: Boolean) {
        if (submissionInFlight) return
        if (!consent) {
            _state.update { it.copy(status = FeedbackUiStatus.CONSENT_REQUIRED,
                message = "Confirm that you want to send this report to DOOM.") }
            return
        }
        if (_state.value.session == null) {
            _state.update { it.copy(status = FeedbackUiStatus.SIGN_IN_REQUIRED,
                message = "Sign in to submit feedback. You can still save a local report.") }
            return
        }
        submissionInFlight = true
        _state.update { it.copy(status = FeedbackUiStatus.SUBMITTING, reportId = null, message = null) }
        viewModelScope.launch {
            try {
                when (val result = repository.submit(report, consent)) {
                    is FeedbackResult.Submitted -> _state.update {
                        it.copy(status = FeedbackUiStatus.SUBMITTED, reportId = result.reportId,
                            message = "DOOM confirmed receipt of your report (${result.status}).")
                    }
                    is FeedbackResult.Failure -> _state.update {
                        it.copy(status = result.toUiStatus(), message = result.message)
                    }
                }
            } finally {
                submissionInFlight = false
            }
        }
    }

    fun clearMessage() = _state.update { it.copy(message = null) }

    private fun FeedbackResult.Failure.toUiStatus(): FeedbackUiStatus = when (reason) {
        FeedbackFailure.INVALID_REPORT -> FeedbackUiStatus.VALIDATION_FAILURE
        FeedbackFailure.CONSENT_REQUIRED -> FeedbackUiStatus.CONSENT_REQUIRED
        FeedbackFailure.SIGN_IN_REQUIRED -> FeedbackUiStatus.SIGN_IN_REQUIRED
        FeedbackFailure.UNAUTHORIZED -> FeedbackUiStatus.UNAUTHORIZED
        FeedbackFailure.FORBIDDEN -> FeedbackUiStatus.UNAUTHORIZED
        FeedbackFailure.RATE_LIMITED -> FeedbackUiStatus.RATE_LIMITED
        FeedbackFailure.OFFLINE -> FeedbackUiStatus.OFFLINE
        FeedbackFailure.SERVER -> FeedbackUiStatus.SERVER_ERROR
        FeedbackFailure.UNKNOWN_OUTCOME -> FeedbackUiStatus.UNKNOWN_OUTCOME
        FeedbackFailure.INSECURE_ENDPOINT -> FeedbackUiStatus.SERVER_ERROR
    }
}
