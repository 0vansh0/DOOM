package org.doom.model

enum class RingPosition { BOTTOM_START, BOTTOM_CENTER, BOTTOM_END }

data class RingStyle(
    val thicknessDp: Int = 4,
    val diameterDp: Int = 48,
    val durationSeconds: Int = 20,
    val position: RingPosition = RingPosition.BOTTOM_END
) {
    init {
        require(thicknessDp in thicknessRange && diameterDp in diameterRange && durationSeconds in durationRange)
    }

    companion object {
        val thicknessRange = 2..10
        val diameterRange = 32..64
        val durationRange = 1..20
    }
}
