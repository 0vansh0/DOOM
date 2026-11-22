package org.doom.data

import android.os.SystemClock
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RuntimeMetrics @Inject constructor() {
    private val values = mutableMapOf<String, ArrayDeque<Long>>()

    fun started() = SystemClock.elapsedRealtimeNanos()

    @Synchronized
    fun record(name: String, start: Long) {
        val samples = values.getOrPut(name) { ArrayDeque() }
        if (samples.size == 128) samples.removeFirst()
        samples.addLast((SystemClock.elapsedRealtimeNanos() - start) / 1_000)
    }

    @Synchronized
    internal fun summary() = JSONObject().apply {
        for ((name, samples) in values) {
            val ordered = samples.sorted()
            fun percentile(percent: Int) = ordered[((percent * ordered.size + 99) / 100 - 1).coerceAtLeast(0)]
            put(name, JSONObject().put("samples", samples.size).put("p50Us", percentile(50)).put("p95Us", percentile(95)))
        }
    }
}
