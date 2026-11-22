package org.doom.domain

import org.doom.model.ModStatus
import org.doom.model.ModStatus.*

sealed interface ModResult {
    data object Success : ModResult
    data class Rejected(val reason: ModError, val message: String) : ModResult
}

enum class ModError { NOT_FOUND, INVALID_TRANSITION, PERMISSION_REQUIRED, INCOMPATIBLE, SAFE_MODE, STORAGE, DUPLICATE_ID, INVALID_SETTING, QUARANTINED }

object ModLifecycle {
    fun allows(from: ModStatus, to: ModStatus): Boolean = when (to) {
        INSTALLED_DISABLED -> from != UNINSTALLING
        ENABLE_REQUESTED -> from in setOf(INSTALLED_DISABLED, READY_TO_ACTIVATE, PERMISSION_REQUIRED, INCOMPATIBLE, ERROR)
        CHECKING_REQUIREMENTS -> from == ENABLE_REQUESTED
        READY_TO_ACTIVATE, PERMISSION_REQUIRED, INCOMPATIBLE -> from == CHECKING_REQUIREMENTS
        ERROR -> true
        UNINSTALLING -> from != UNINSTALLING
    }
}
