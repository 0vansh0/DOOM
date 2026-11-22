package org.doom.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.doom.data.AuthResult
import org.doom.data.MarketplaceAuthRepository
import org.doom.data.MarketplaceSession
import org.doom.data.MarketplaceSessionManager

data class AccountUiState(
    val session: MarketplaceSession? = null,
    val loading: Boolean = false,
    val registering: Boolean = false,
    val error: String? = null,
    val expired: Boolean = false
)

@HiltViewModel
class AccountViewModel @Inject constructor(
    private val auth: MarketplaceAuthRepository,
    sessions: MarketplaceSessionManager
) : ViewModel() {
    private val _state = MutableStateFlow(AccountUiState())
    val state = _state.asStateFlow()
    private var requestInFlight = false

    init {
        viewModelScope.launch {
            sessions.session.collectLatest { session ->
                val expired = _state.value.session != null && session == null
                _state.update { it.copy(session = session, expired = expired || it.expired) }
                if (session != null) {
                    delay((session.expiresAtMillis - System.currentTimeMillis()).coerceAtLeast(0L))
                    if (sessions.session.value == session) {
                        sessions.token()
                        _state.update { it.copy(expired = true) }
                    }
                }
            }
        }
    }

    fun signIn(email: String, password: String) = authenticate(register = false, email, password, null)
    fun register(email: String, password: String, displayName: String) =
        authenticate(register = true, email, password, displayName)

    private fun authenticate(register: Boolean, email: String, password: String, displayName: String?) {
        if (requestInFlight) return
        requestInFlight = true
        _state.update { it.copy(loading = true, registering = register, error = null) }
        viewModelScope.launch {
            try {
                val result = if (register) auth.register(email, password, displayName.orEmpty()) else auth.login(email, password)
                when (result) {
                    is AuthResult.Success -> _state.update { it.copy(loading = false, error = null, expired = false) }
                    is AuthResult.Failure -> _state.update {
                        it.copy(loading = false, error = result.message)
                    }
                }
            } finally {
                requestInFlight = false
                _state.update { it.copy(loading = false, registering = false) }
            }
        }
    }

    fun logout() {
        auth.logout()
        _state.update { AccountUiState() }
    }

    fun dismissError() = _state.update { it.copy(error = null, expired = false) }
}
