package org.doom.model

data class GlowAppearance(val color: String, val intensity: Int, val ring: RingStyle? = null, val options: Map<String, Int> = emptyMap()) {
    companion object {
        val colorPattern = Regex("#[0-9a-fA-F]{6}")
        val intensities = 10..60
    }

    init {
        require(options.size <= 8 && options.all { (id, value) -> Regex("[a-z][a-z0-9-]{0,23}").matches(id) && value in 0..100 })
        require(colorPattern.matches(color)) { "Use a six-digit hex color." }
        require(intensity in intensities) { "Intensity must be 10–60." }
    }
}

data class ModManifest(
    val id: String,
    val title: String,
    val version: String,
    val description: String,
    val minAndroidApi: Int,
    val appearance: GlowAppearance
)

data class IntegerSetting(val id: String, val minimum: Int, val maximum: Int, val default: Int) {
    init {
        require(Regex("[a-z][a-z0-9-]{0,23}").matches(id))
        require(minimum in 0..100 && maximum in minimum..100 && default in minimum..maximum)
    }
}
