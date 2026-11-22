package org.doom.engine.runtime

/** Immutable, normalized data delivered with one event. Missing fields never satisfy a condition. */
data class ModEventSnapshot(
    val eventName: String,
    val batteryPercent: Int? = null,
    val charging: Boolean? = null,
    val metadata: Map<String, ConditionValue> = emptyMap()
)

sealed interface ConditionValue {
    data class Number(val value: Double) : ConditionValue
    data class Flag(val value: Boolean) : ConditionValue
    data class Text(val value: String) : ConditionValue
}

object V4ConditionEvaluator {
    const val MAX_DEPTH = 4
    const val MAX_LEAVES = 32
    const val MAX_NODES = 64

    fun evaluate(condition: V4Condition, event: ModEventSnapshot): Boolean {
        var nodes = 0
        var leaves = 0
        fun validate(node: V4Condition, depth: Int) {
            require(depth <= MAX_DEPTH) { "Condition nesting exceeds maximum depth $MAX_DEPTH." }
            require(++nodes <= MAX_NODES) { "Condition expression exceeds maximum node count $MAX_NODES." }
            if (node.op == V4ConditionOp.COMPARE) require(++leaves <= MAX_LEAVES) {
                "Condition expression exceeds maximum leaf count $MAX_LEAVES."
            } else {
                require(node.children.isNotEmpty()) { "Composed conditions must have at least one child." }
                node.children.forEach { validate(it, depth + 1) }
            }
        }
        validate(condition, 1)
        fun visit(node: V4Condition): Boolean {
            return when (node.op) {
                V4ConditionOp.ALL -> node.children.all(::visit)
                V4ConditionOp.ANY -> node.children.any(::visit)
                V4ConditionOp.COMPARE -> {
                    val actual = when (node.source) {
                        "battery.level" -> event.batteryPercent?.let { ConditionValue.Number(it.toDouble()) }
                        "battery.charging" -> event.charging?.let(ConditionValue::Flag)
                        "event.name" -> ConditionValue.Text(event.eventName)
                        else -> event.metadata[node.source]
                    } ?: return false
                    val expected = when (node.valueType) {
                        V4ValueType.NUMBER -> node.value?.toDoubleOrNull()?.let(ConditionValue::Number)
                        V4ValueType.BOOLEAN -> node.value?.toBooleanStrictOrNull()?.let(ConditionValue::Flag)
                        V4ValueType.STRING -> node.value?.let(ConditionValue::Text)
                        null -> null
                    } ?: return false
                    compare(actual, expected, requireNotNull(node.comparison))
                }
            }
        }
        return visit(condition)
    }

    private fun compare(actual: ConditionValue, expected: ConditionValue, op: V4Comparison): Boolean {
        if (actual::class != expected::class) return false
        return when {
            actual is ConditionValue.Number && expected is ConditionValue.Number -> when (op) {
                V4Comparison.BELOW, V4Comparison.LESS_THAN -> actual.value < expected.value
                V4Comparison.LESS_THAN_OR_EQUAL -> actual.value <= expected.value
                V4Comparison.ABOVE, V4Comparison.GREATER_THAN -> actual.value > expected.value
                V4Comparison.GREATER_THAN_OR_EQUAL -> actual.value >= expected.value
                V4Comparison.EQUALS -> actual.value == expected.value
                V4Comparison.NOT_EQUALS -> actual.value != expected.value
            }
            actual is ConditionValue.Flag && expected is ConditionValue.Flag -> when (op) {
                V4Comparison.EQUALS -> actual.value == expected.value
                V4Comparison.NOT_EQUALS -> actual.value != expected.value
                else -> false
            }
            actual is ConditionValue.Text && expected is ConditionValue.Text -> when (op) {
                V4Comparison.EQUALS -> actual.value == expected.value
                V4Comparison.NOT_EQUALS -> actual.value != expected.value
                else -> false
            }
            else -> false
        }
    }
}

/** Fires on false-to-true crossings; any false evaluation rearms the trigger. */
class V4ConditionEdgeTracker {
    private val matching = mutableMapOf<String, Boolean>()
    fun shouldFire(triggerId: String, matches: Boolean): Boolean {
        val fire = matches && matching[triggerId] != true
        matching[triggerId] = matches
        return fire
    }
    fun clear() = matching.clear()
}

/** Platform-independent normalization of ACTION_BATTERY_CHANGED extras for unit testing. */
data class BatteryReading(val percent: Int?, val charging: Boolean?) {
    companion object {
        const val STATUS_CHARGING = 2
        const val STATUS_DISCHARGING = 3
        const val STATUS_NOT_CHARGING = 4
        const val STATUS_FULL = 5
        fun fromExtras(level: Int?, scale: Int?, status: Int?, plugged: Int?): BatteryReading {
            val percent = if (level != null && scale != null && scale > 0 && level in 0..scale)
                (level.toLong() * 100 / scale).toInt() else null
            val charging = when (status) {
                STATUS_CHARGING -> true
                STATUS_DISCHARGING, STATUS_NOT_CHARGING -> false
                STATUS_FULL -> plugged?.let { it != 0 }
                else -> null
            }
            return BatteryReading(percent, charging)
        }
    }
}
