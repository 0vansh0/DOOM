package org.doom.data

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Process
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.doom.domain.RuntimeRecoveryPolicy
import javax.inject.Inject

internal class RuntimeRecovery @Inject constructor(@ApplicationContext private val context: Context) {
    private val data = context.getSharedPreferences("runtime_recovery", Context.MODE_PRIVATE)
    // Host crash-loop detection remains separate from the shorter per-mod execution fault window.
    private val policy = RuntimeRecoveryPolicy(windowMillis = 300_000)

    suspend fun begin(): Boolean = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val pid = data.getInt("pending_pid", 0)
        val timestamp = data.getLong("pending_started", 0)
        val exit = if (pid != 0 && pid != Process.myPid()) {
            runCatching {
                context.getSystemService(ActivityManager::class.java)
                    .getHistoricalProcessExitReasons(context.packageName, pid, 1).firstOrNull()
            }.getOrNull()
        } else null
        val confirmed = exit?.reason in setOf(ApplicationExitInfo.REASON_CRASH,
            ApplicationExitInfo.REASON_CRASH_NATIVE, ApplicationExitInfo.REASON_ANR) &&
            (exit?.timestamp ?: 0) >= timestamp
        val recent = recent(now, if (confirmed) exit?.timestamp else null)
        val edit = data.edit().putString("host_failures", recent.joinToString(","))
        if (recent.size >= policy.threshold) edit.remove("pending_pid").remove("pending_started")
        else edit.putInt("pending_pid", Process.myPid()).putLong("pending_started", now)
        check(edit.commit())
        recent.size >= policy.threshold
    }

    suspend fun ready() = withContext(Dispatchers.IO) {
        check(data.edit().remove("pending_pid").remove("pending_started").commit())
    }

    suspend fun failed(): Boolean = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val recent = recent(now, now)
        check(data.edit().putString("host_failures", recent.joinToString(","))
            .remove("pending_pid").remove("pending_started").commit())
        recent.size >= policy.threshold
    }

    private fun recent(now: Long, extra: Long?) = policy.recent(
        data.getString("host_failures", "").orEmpty().split(',').mapNotNull(String::toLongOrNull) +
            listOfNotNull(extra), now).takeLast(policy.threshold)
}
