package org.doom.model

enum class ShizukuConnectionPhase {
    NOT_INSTALLED, NOT_RUNNING, PERMISSION_REQUIRED, CONNECTING,
    CONNECTED_ADB, CONNECTED_ROOT, DISCONNECTED, ERROR
}

enum class AdapterPrivilegeLevel { NONE, ADB, ROOT, OTHER, UNKNOWN }

data class ShizukuConnectionState(
    val phase: ShizukuConnectionPhase = ShizukuConnectionPhase.CONNECTING,
    val managerInstalled: Boolean = false,
    val binderAlive: Boolean = false,
    val authorized: Boolean = false,
    val privilegeLevel: AdapterPrivilegeLevel = AdapterPrivilegeLevel.NONE,
    val serviceApiVersion: Int? = null,
    val message: String? = null,
    val permissionDenied: Boolean = false
) {
    val displayLabel: String get() = when (phase) {
        ShizukuConnectionPhase.NOT_INSTALLED -> "Not installed"
        ShizukuConnectionPhase.NOT_RUNNING -> "Installed, not running"
        ShizukuConnectionPhase.PERMISSION_REQUIRED -> "Authorization required"
        ShizukuConnectionPhase.CONNECTING -> "Checking connection"
        ShizukuConnectionPhase.CONNECTED_ADB -> "Connected · ADB shell"
        ShizukuConnectionPhase.CONNECTED_ROOT -> "Connected · root"
        ShizukuConnectionPhase.DISCONNECTED -> "Disconnected"
        ShizukuConnectionPhase.ERROR -> "Connection error"
    }
}

object ShizukuStateMapper {
    fun map(
        managerInstalled: Boolean,
        binderAlive: Boolean,
        authorized: Boolean,
        uid: Int? = null,
        serviceApiVersion: Int? = null,
        disconnected: Boolean = false,
        permissionDenied: Boolean = false,
        error: String? = null
    ): ShizukuConnectionState {
        if (error != null) return ShizukuConnectionState(ShizukuConnectionPhase.ERROR, managerInstalled,
            binderAlive, authorized, AdapterPrivilegeLevel.UNKNOWN, serviceApiVersion, error)
        if (disconnected) return ShizukuConnectionState(ShizukuConnectionPhase.DISCONNECTED, managerInstalled,
            false, false, AdapterPrivilegeLevel.NONE, message = "Shizuku Binder disconnected.")
        if (!binderAlive) return ShizukuConnectionState(
            if (managerInstalled) ShizukuConnectionPhase.NOT_RUNNING else ShizukuConnectionPhase.NOT_INSTALLED,
            managerInstalled, false, false, AdapterPrivilegeLevel.NONE,
            message = if (managerInstalled) "Shizuku is installed but its service is not running." else "Shizuku or Sui is not detected."
        )
        if (!authorized) return ShizukuConnectionState(ShizukuConnectionPhase.PERMISSION_REQUIRED,
            managerInstalled, true, false, AdapterPrivilegeLevel.NONE, serviceApiVersion,
            if (permissionDenied) "Shizuku permission was denied. You can request it again." else "Authorize DOOM in Shizuku to continue.",
            permissionDenied)
        val privilege = when (uid) {
            0 -> AdapterPrivilegeLevel.ROOT
            2000 -> AdapterPrivilegeLevel.ADB
            null, -1 -> AdapterPrivilegeLevel.UNKNOWN
            else -> AdapterPrivilegeLevel.OTHER
        }
        val phase = when (privilege) {
            AdapterPrivilegeLevel.ROOT -> ShizukuConnectionPhase.CONNECTED_ROOT
            AdapterPrivilegeLevel.ADB -> ShizukuConnectionPhase.CONNECTED_ADB
            AdapterPrivilegeLevel.UNKNOWN, AdapterPrivilegeLevel.OTHER -> ShizukuConnectionPhase.ERROR
            AdapterPrivilegeLevel.NONE -> ShizukuConnectionPhase.ERROR
        }
        val message = when (privilege) {
            AdapterPrivilegeLevel.OTHER -> "Connected, but the Shizuku identity is neither ADB shell nor root."
            AdapterPrivilegeLevel.UNKNOWN, AdapterPrivilegeLevel.NONE -> "Could not determine the Shizuku identity."
            else -> null
        }
        return ShizukuConnectionState(phase, managerInstalled, true, true, privilege, serviceApiVersion, message)
    }
}

enum class PrivilegedOperationStatus { SUCCESS, UNAVAILABLE, UNAUTHORIZED, UNSUPPORTED, CANCELLED, ERROR }

data class PrivilegedOperationResult(
    val operationId: String,
    val status: PrivilegedOperationStatus,
    val message: String,
    val privilegeLevel: AdapterPrivilegeLevel? = null,
    val serviceApiVersion: Int? = null
)

object ShizukuOperationPolicy {
    const val DIAGNOSTIC_IDENTITY = "shizuku.diagnostic.identity"

    fun authorize(
        operationId: String,
        supported: Set<String>,
        binderAlive: Boolean,
        shizukuAuthorized: Boolean,
        userInitiated: Boolean,
        approvedOperationIds: Set<String>,
        privilegeLevel: AdapterPrivilegeLevel
    ): PrivilegedOperationStatus = when {
        operationId !in supported -> PrivilegedOperationStatus.UNSUPPORTED
        !binderAlive -> PrivilegedOperationStatus.UNAVAILABLE
        !shizukuAuthorized || operationId !in approvedOperationIds -> PrivilegedOperationStatus.UNAUTHORIZED
        !userInitiated -> PrivilegedOperationStatus.UNAUTHORIZED
        privilegeLevel !in setOf(AdapterPrivilegeLevel.ADB, AdapterPrivilegeLevel.ROOT) -> PrivilegedOperationStatus.UNSUPPORTED
        else -> PrivilegedOperationStatus.SUCCESS
    }
}
