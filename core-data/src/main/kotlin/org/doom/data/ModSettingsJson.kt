package org.doom.data

import org.doom.model.*
import org.json.JSONArray
import org.json.JSONObject

internal fun encodeSettingDefinitions(definitions: Map<String, ModSettingDefinition>): String = JSONObject().apply {
    definitions.forEach { (id, definition) ->
        put(id, JSONObject().apply {
            put("label", definition.label)
            put("type", definition.type.name.lowercase())
            put("default", definition.defaultValue.toJsonValue())
            definition.description?.let { put("description", it) }
            definition.minimum?.let { put("min", it) }
            definition.maximum?.let { put("max", it) }
            definition.step?.let { put("step", it) }
            definition.unit?.let { put("unit", it) }
            if (definition.choices.isNotEmpty()) put("values", JSONArray(definition.choices))
            definition.maxLength?.let { put("maxLength", it) }
        })
    }
}.toString()

internal fun decodeSettingDefinitions(json: String?): Map<String, ModSettingDefinition> {
    if (json.isNullOrBlank()) return emptyMap()
    val root = JSONObject(json)
    val definitions = buildMap {
        root.keys().forEach { id ->
            val item = root.getJSONObject(id)
            val type = ModSettingType.valueOf(item.getString("type").uppercase())
            val unit = item.optString("unit").takeIf { item.has("unit") }
            val choices = item.optJSONArray("values")?.let { array -> List(array.length()) { array.getString(it) } }.orEmpty()
            val default = readSettingValue(type, item.get("default"), unit)
            put(id, ModSettingDefinition(id, item.optString("label", id), type, default,
                item.optString("description").takeIf { item.has("description") },
                item.optDouble("min").takeIf { item.has("min") }, item.optDouble("max").takeIf { item.has("max") },
                item.optDouble("step").takeIf { item.has("step") }, unit, choices,
                item.optInt("maxLength").takeIf { item.has("maxLength") }))
        }
    }
    ModSettingValidator.validateDefinitions(definitions)
    return definitions
}

internal fun decodeSettingValues(definitions: Map<String, ModSettingDefinition>, json: String?): Map<String, ModSettingValue> {
    if (definitions.isEmpty()) return emptyMap()
    val raw = runCatching { json?.let(::JSONObject)?.optJSONObject("values") }.getOrNull()
    val values = definitions.mapValues { (id, definition) ->
        val item = raw?.opt(id) ?: return@mapValues definition.defaultValue
        runCatching { readSettingValue(definition.type, item, definition.unit) }
            .getOrElse { definition.defaultValue }
    }
    return runCatching { ModSettingValidator.validateValues(definitions, values) }
        .getOrElse { definitions.mapValues { it.value.defaultValue } }
}

internal fun encodeSettingValues(values: Map<String, ModSettingValue>): JSONObject = JSONObject().apply {
    values.forEach { (id, value) -> put(id, value.toJsonValue()) }
}

private fun ModSettingValue.toJsonValue(): Any = when (this) {
    is ModSettingValue.BooleanValue -> value
    is ModSettingValue.IntegerValue -> value
    is ModSettingValue.NumberValue -> value
    is ModSettingValue.ColorValue -> value
    is ModSettingValue.EnumValue -> value
    is ModSettingValue.TextValue -> value
    is ModSettingValue.DurationValue -> value
}

private fun readSettingValue(type: ModSettingType, raw: Any, unit: String?): ModSettingValue = when (type) {
    ModSettingType.BOOLEAN -> ModSettingValue.BooleanValue(raw as Boolean)
    ModSettingType.INTEGER -> ModSettingValue.IntegerValue((raw as Number).toLong().also { require(raw.toDouble() == it.toDouble()) })
    ModSettingType.NUMBER -> ModSettingValue.NumberValue((raw as Number).toDouble())
    ModSettingType.DURATION -> ModSettingValue.DurationValue((raw as Number).toDouble(), requireNotNull(unit))
    ModSettingType.COLOR -> ModSettingValue.ColorValue(raw as String)
    ModSettingType.ENUM -> ModSettingValue.EnumValue(raw as String)
    ModSettingType.TEXT -> ModSettingValue.TextValue(raw as String)
}
