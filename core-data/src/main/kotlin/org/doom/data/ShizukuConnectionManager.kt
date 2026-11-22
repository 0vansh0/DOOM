package org.doom.data

import android.content.Context
import android.content.pm.PackageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.doom.model.ShizukuConnectionPhase
import rikka.shizuku.Shizuku
import rikka.sui.Sui
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import org.doom.model.*

/** Process-scoped listener owner for the official Shizuku API. No permission is requested during refresh. */
@Singleton
class ShizukuConnectionManager @Inject constructor(@ApplicationContext private val context: Context) {
    private val started = AtomicBoolean(false)
    private val generationCounter = AtomicLong(0)
    private val mutableState = MutableStateFlow(ShizukuConnectionState())
    private val mutableGeneration = MutableStateFlow(0L)
    private val deniedRequest = AtomicBoolean(false)
    val state = mutableState.asStateFlow()
    val generation = mutableGeneration.asStateFlow()
    val currentGeneration: Long get() = mutableGeneration.value

    private val binderReceived = Shizuku.OnBinderReceivedListener { refresh() }
    private val binderDead = Shizuku.OnBinderDeadListener { onBinderDead() }
    private val permissionResult = Shizuku.OnRequestPermissionResultListener { requestCode, result ->
        if (requestCode == PERMISSION_REQUEST_CODE) {
            val denied = result != PackageManager.PERMISSION_GRANTED
            deniedRequest.set(denied)
            val refreshed = refresh()
            if (denied && refreshed.phase == ShizukuConnectionPhase.PERMISSION_REQUIRED) {
                mutableState.value = refreshed.copy(permissionDenied = true,
                    message = "Shizuku permission was denied. You can request it again.")
            }
        }
    }

    fun start() {
        if (!started.compareAndSet(false, true)) return
        Shizuku.addBinderReceivedListener(binderReceived)
        Shizuku.addBinderDeadListener(binderDead)
        Shizuku.addRequestPermissionResultListener(permissionResult)
        refresh()
    }

    fun stop() {
        if (!started.compareAndSet(true, false)) return
        Shizuku.removeBinderReceivedListener(binderReceived)
        Shizuku.removeBinderDeadListener(binderDead)
        Shizuku.removeRequestPermissionResultListener(permissionResult)
        invalidateSession()
        mutableState.value = ShizukuStateMapper.map(managerInstalled(), false, false, disconnected = true)
    }

    /** Re-probes the current Binder; no cached Binder is retained by DOOM. */
    fun refresh(): ShizukuConnectionState {
        val installed = managerInstalled()
        val binderAlive = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
        if (!binderAlive) {
            if (mutableState.value.binderAlive) invalidateSession()
            return ShizukuStateMapper.map(installed, false, false).also { mutableState.value = it }
        }
        try {
            if (Shizuku.isPreV11()) {
                return ShizukuStateMapper.map(installed, true, false,
                    error = "Shizuku server API versions before 11 are unsupported.").also { mutableState.value = it }
            }
            val authorized = Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
            if (authorized) deniedRequest.set(false)
            val uid = if (authorized) Shizuku.getUid() else null
            val version = runCatching { Shizuku.getVersion() }.getOrNull()
            return ShizukuStateMapper.map(installed, true, authorized, uid, version,
                permissionDenied = deniedRequest.get()).also { mutableState.value = it }
        } catch (_: SecurityException) {
            return ShizukuStateMapper.map(installed, true, false, permissionDenied = deniedRequest.get()).also { mutableState.value = it }
        } catch (_: IllegalStateException) {
            if (mutableState.value.binderAlive) invalidateSession()
            return ShizukuStateMapper.map(installed, false, false).also { mutableState.value = it }
        } catch (_: RuntimeException) {
            return ShizukuStateMapper.map(installed, false, false, error = "Could not read Shizuku service state.")
                .also { mutableState.value = it }
        }
    }

    /** Must only be called from an explicit user action. */
    fun requestAuthorization(): ShizukuConnectionState {
        val current = refresh()
        if (!current.binderAlive) return current.copy(message = "Start Shizuku before requesting DOOM authorization.")
        if (current.authorized) return current
        deniedRequest.set(false)
        return try {
            Shizuku.requestPermission(PERMISSION_REQUEST_CODE)
            current.copy(message = "Review and approve DOOM in the Shizuku permission dialog.")
                .also { mutableState.value = it }
        } catch (_: RuntimeException) {
            ShizukuStateMapper.map(current.managerInstalled, current.binderAlive, false,
                error = "Shizuku could not start its permission request.").also { mutableState.value = it }
        }
    }

    private fun managerInstalled(): Boolean = try {
        @Suppress("DEPRECATION")
        context.packageManager.getPackageInfo(SHIZUKU_PACKAGE, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        // The Shizuku provider automatically initializes Sui when present; it need not be a visible package.
        runCatching { Sui.isSui() }.getOrDefault(false)
    } catch (_: RuntimeException) {
        false
    }

    private fun onBinderDead() {
        invalidateSession()
        mutableState.value = ShizukuStateMapper.map(managerInstalled(), false, false, disconnected = true)
    }

    private fun invalidateSession() {
        mutableGeneration.value = generationCounter.incrementAndGet()
    }

    companion object {
        const val PERMISSION_REQUEST_CODE = 0xD00
        const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
    }
}
