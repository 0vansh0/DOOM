package org.doom.engine.runtime

import org.junit.Assert.*
import org.junit.Test

class V4ConditionEvaluatorTest {
    private val event = ModEventSnapshot("battery.level.changed", 20, false)
    private fun number(op: V4Comparison, target: String = "20") = V4Condition(V4ConditionOp.COMPARE,
        "battery.level", op, V4ValueType.NUMBER, target)

    @Test fun numericComparisonOperatorsRespectBoundaries() {
        assertTrue(V4ConditionEvaluator.evaluate(number(V4Comparison.LESS_THAN, "21"), event))
        assertTrue(V4ConditionEvaluator.evaluate(number(V4Comparison.LESS_THAN_OR_EQUAL), event))
        assertFalse(V4ConditionEvaluator.evaluate(number(V4Comparison.LESS_THAN), event))
        assertTrue(V4ConditionEvaluator.evaluate(number(V4Comparison.GREATER_THAN, "19"), event))
        assertTrue(V4ConditionEvaluator.evaluate(number(V4Comparison.GREATER_THAN_OR_EQUAL), event))
        assertTrue(V4ConditionEvaluator.evaluate(number(V4Comparison.EQUALS), event))
        assertFalse(V4ConditionEvaluator.evaluate(number(V4Comparison.NOT_EQUALS), event))
    }

    @Test fun booleanStringAndNestedAllAnyAreTyped() {
        val charging = V4Condition(V4ConditionOp.COMPARE, "battery.charging", V4Comparison.EQUALS, V4ValueType.BOOLEAN, "false")
        val eventName = V4Condition(V4ConditionOp.COMPARE, "event.name", V4Comparison.NOT_EQUALS, V4ValueType.STRING, "mod.manual.trigger")
        val nested = V4Condition(V4ConditionOp.ALL, children = listOf(number(V4Comparison.LESS_THAN_OR_EQUAL),
            V4Condition(V4ConditionOp.ANY, children = listOf(charging, eventName))))
        assertTrue(V4ConditionEvaluator.evaluate(nested, event))
        assertFalse(V4ConditionEvaluator.evaluate(charging.copy(valueType = V4ValueType.NUMBER), event))
    }

    @Test fun missingMetadataDoesNotMatchAndLimitsRejectComplexTrees() {
        assertFalse(V4ConditionEvaluator.evaluate(number(V4Comparison.LESS_THAN), ModEventSnapshot("battery.level.changed")))
        var deep = number(V4Comparison.LESS_THAN)
        repeat(4) { deep = V4Condition(V4ConditionOp.ALL, children = listOf(deep)) }
        assertTrue(runCatching { V4ConditionEvaluator.evaluate(deep, event) }.isFailure)
        val tooMany = V4Condition(V4ConditionOp.ALL, children = List(64) { number(V4Comparison.LESS_THAN) })
        assertTrue(runCatching { V4ConditionEvaluator.evaluate(tooMany, event) }.isFailure)
    }

    @Test fun thresholdEdgeRearmsOnlyAfterConditionBecomesFalse() {
        val tracker = V4ConditionEdgeTracker()
        assertFalse(tracker.shouldFire("low", false)) // 21%
        assertTrue(tracker.shouldFire("low", true)) // 19%, crossing below 20
        assertFalse(tracker.shouldFire("low", true)) // 18%, still low
        assertFalse(tracker.shouldFire("low", false)) // 20%, rearm
        assertTrue(tracker.shouldFire("low", true)) // 19% again
    }

    @Test fun batteryExtrasNormalizeSafely() {
        assertEquals(BatteryReading(50, true), BatteryReading.fromExtras(1, 2, BatteryReading.STATUS_CHARGING, 1))
        assertEquals(BatteryReading(null, true), BatteryReading.fromExtras(1, 0, BatteryReading.STATUS_CHARGING, 1))
        assertEquals(BatteryReading(null, null), BatteryReading.fromExtras(null, null, null, null))
        assertEquals(BatteryReading(100, false), BatteryReading.fromExtras(4, 4, 3, 0))
        assertEquals(BatteryReading(100, false), BatteryReading.fromExtras(100, 100, BatteryReading.STATUS_FULL, 0))
        assertEquals(BatteryReading(100, true), BatteryReading.fromExtras(100, 100, BatteryReading.STATUS_FULL, 1))
    }
}
