package org.doom.engine

import com.google.gson.*
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import org.doom.engine.runtime.*
import org.doom.model.EffectTemplate
import org.doom.model.GlowAppearance
import org.doom.model.PackageReview
import org.doom.model.IntegerSetting
import org.doom.model.ModSettingDefinition
import org.doom.model.ModSettingType
import org.doom.model.ModSettingValue
import org.doom.model.ModSettingValidator
import org.doom.model.RingStyle
import org.doom.model.RingPosition
import java.nio.ByteBuffer
import java.math.BigDecimal
import java.nio.charset.CodingErrorAction

object PackageFormat {
    private val manifests = mapOf(2 to schema("doom"), 3 to schema("doom-sdk2"))
    private val definitions = mapOf(1 to schema("mod"), 2 to schema("mod-v2"), 3 to schema("mod-sdk2"), 4 to schema("mod-v4"))

    fun validate(manifestBytes: ByteArray, definitionBytes: ByteArray, files: Set<String>): ValidatedPackage {
        val manifest = parse(manifestBytes).asJsonObject
        val definition = parse(definitionBytes).asJsonObject
        val manifestVersion = manifest["schemaVersion"]?.asInt
        checkSchema(manifest, requireNotNull(manifests[manifestVersion]) { "Unsupported manifest version." }, "doom.json")
        val version = definition["definitionVersion"]?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asInt
        require(if (manifestVersion == 3) version in 3..4 else version in 1..2) { "Manifest schema $manifestVersion does not support definition version $version." }
        val definitionSchema = requireNotNull(definitions[version]) { "Unsupported definition version." }
        checkSchema(definition, definitionSchema, "mod.json")
        val caps = manifest.getAsJsonArray("capabilities").map { it.asString }
        if (version == 4) return validateV4(manifest, definition, caps, files)
        val options = definition.getAsJsonObject("settings").getAsJsonArray("options")?.map {
            val item = it.asJsonObject
            IntegerSetting(item["id"].asString, item["min"].asInt, item["max"].asInt, item["default"].asInt)
        } ?: emptyList()
        require(options.map { it.id }.toSet().size == options.size) { "Duplicate setting IDs." }
        val subscriptions = subscriptions(definition, caps, options)
        val icon = manifest["icon"].asString
        require(icon.isEmpty() || icon in files) { "The declared icon is missing." }
        val settings = definition.getAsJsonObject("settings")
        val intensity = settings.getAsJsonObject("intensity")
        val range = intensity["min"].asInt..intensity["max"].asInt
        require(intensity["default"].asInt in range) { "Intensity default is outside its declared range." }
        val ring = settings.getAsJsonObject("ring")?.let {
            RingStyle(it["thicknessDp"].asInt, it["diameterDp"].asInt, it["durationSeconds"].asInt,
                RingPosition.valueOf(it["position"].asString))
        }
        require(ring == null || subscriptions.values.any { it.action.template == EffectTemplate.BATTERY_RING }) {
            "Ring settings require the battery-ring template."
        }
        val review = PackageReview(
            manifest["id"].asString, manifest["name"].asString, manifest["version"].asString,
            manifest["description"].asString, manifest["author"].asString, manifest["license"].asString,
            manifest["minAndroidApi"].asInt, caps,
            GlowAppearance(settings.getAsJsonObject("color")["default"].asString, intensity["default"].asInt, ring, options.associate { it.id to it.default }),
            range, options
        )
        return ValidatedPackage(review, subscriptions)
    }

    private fun validateV4(manifest: JsonObject, definition: JsonObject, caps: List<String>, files: Set<String>): ValidatedPackage {
        require(manifest["icon"].asString.let { it.isEmpty() || it in files }) { "The declared icon is missing." }
        var count = 0
        var leaves = 0
        val settings = parseV4Settings(definition.getAsJsonObject("settings"))
        fun parseCondition(node: JsonObject, depth: Int): V4Condition {
            require(depth <= 4) { "Condition nesting exceeds maximum depth 4." }
            require(++count <= V4ConditionEvaluator.MAX_NODES) { "Condition node count exceeds maximum ${V4ConditionEvaluator.MAX_NODES}." }
            return when (node["op"].asString) {
                "all", "any" -> V4Condition(V4ConditionOp.valueOf(node["op"].asString.uppercase()),
                    children = node.getAsJsonArray("conditions").map { parseCondition(it.asJsonObject, depth + 1) })
                "compare" -> {
                    val source = node["source"].asString
                    require(++leaves <= V4ConditionEvaluator.MAX_LEAVES) { "Condition leaf count exceeds maximum ${V4ConditionEvaluator.MAX_LEAVES}." }
                    val comparison = when (node["comparison"].asString) {
                        "below" -> V4Comparison.BELOW
                        "above" -> V4Comparison.ABOVE
                        "equals" -> V4Comparison.EQUALS
                        "lessThan" -> V4Comparison.LESS_THAN
                        "lessThanOrEqual" -> V4Comparison.LESS_THAN_OR_EQUAL
                        "greaterThan" -> V4Comparison.GREATER_THAN
                        "greaterThanOrEqual" -> V4Comparison.GREATER_THAN_OR_EQUAL
                        "notEquals" -> V4Comparison.NOT_EQUALS
                        else -> error("Unknown condition comparison.")
                    }
                    val value = node["value"]
                    when (source) {
                        "battery.level" -> {
                            require("battery.read" in caps) { "battery.read capability is required by condition." }
                            require(value.isJsonPrimitive && value.asJsonPrimitive.isNumber) { "Battery level requires a numeric comparison value." }
                            require(value.isJsonPrimitive && value.asJsonPrimitive.isNumber && value.asDouble in 0.0..100.0 &&
                                value.asDouble % 1.0 == 0.0) { "Battery level must be an integer in 0..100." }
                            V4Condition(V4ConditionOp.COMPARE, source, comparison, V4ValueType.NUMBER, value.asNumber.toString())
                        }
                        "battery.charging" -> {
                            require("battery.read" in caps) { "battery.read capability is required by condition." }
                            require(comparison in setOf(V4Comparison.EQUALS, V4Comparison.NOT_EQUALS) && value.isJsonPrimitive && value.asJsonPrimitive.isBoolean) { "Charging supports boolean equality only." }
                            V4Condition(V4ConditionOp.COMPARE, source, comparison, V4ValueType.BOOLEAN, value.asBoolean.toString())
                        }
                        "event.name" -> {
                            require(comparison in setOf(V4Comparison.EQUALS, V4Comparison.NOT_EQUALS) && value.isJsonPrimitive && value.asJsonPrimitive.isString) { "Event metadata supports string equality only." }
                            V4Condition(V4ConditionOp.COMPARE, source, comparison, V4ValueType.STRING, value.asString)
                        }
                        else -> {
                            require(source.startsWith("setting.")) { "Unknown condition source: $source." }
                            val id = source.removePrefix("setting.")
                            val setting = settings[id] ?: error("Unknown setting reference: $id.")
                            val parsed = parseSettingConditionValue(setting, value, comparison)
                            V4Condition(V4ConditionOp.COMPARE, source, comparison, parsed.first, parsed.second)
                        }
                    }
                }
                else -> error("Unknown condition operator: ${node["op"].asString}.")
            }
        }
        val workflowIds = mutableSetOf<String>()
        val triggers = definition.getAsJsonArray("triggers").map { element ->
            val trigger = element.asJsonObject
            val event = trigger["event"].asString
            require(event in setOf("battery.charging.started", "battery.charging.stopped", "battery.level.changed", "mod.manual.trigger")) { "Unsupported trigger: $event." }
            require(event == "mod.manual.trigger" || "battery.read" in caps) { "Trigger $event requires battery.read." }
            val workflow = trigger["workflow"]?.asJsonObject?.let { item ->
                require(workflowIds.add(item["id"].asString)) { "Workflow IDs must be unique." }
                val mode = when (item["executionMode"].asString) {
                    "sequential" -> WorkflowExecutionMode.SEQUENTIAL
                    "parallel" -> WorkflowExecutionMode.PARALLEL
                    else -> error("Unsupported workflow execution mode.")
                }
                val failurePolicy = when (item["failurePolicy"].asString) {
                    "stopOnFailure" -> WorkflowFailurePolicy.STOP_ON_FAILURE
                    "continueOnFailure" -> WorkflowFailurePolicy.CONTINUE_ON_FAILURE
                    else -> error("Unsupported workflow failure policy.")
                }
                V4WorkflowConfig(item["id"].asString, mode, failurePolicy, item["timeoutMs"].asLong,
                    item["maxConcurrency"]?.asInt ?: WorkflowCoordinator.MAX_PARALLEL_ACTIONS)
            }
            val actions = trigger.getAsJsonArray("actions").mapIndexed { actionIndex, actionElement ->
                val action = actionElement.asJsonObject
                val effect = action["effect"].asString
                val capability = CapabilityRegistry.actionCapabilities[effect]
                    ?: error("Unknown action: $effect.")
                require(capability in caps) { "Action $effect requires capability $capability." }
                CapabilityRegistry.entries.firstOrNull { it.id == capability }?.requiredCapabilities?.forEach {
                    require(it in caps) { "Action $effect also requires capability $it." }
                }
                val template = action["template"]?.asString
                require(if (effect in setOf("overlay.dismiss", "haptics.pulse", "device.display.read")) template == null else template != null) { "Action $effect requires valid template parameters." }
                val parameterObject = action.getAsJsonObject("parameters") ?: JsonObject()
                if (effect == "device.display.read") require(template == null && parameterObject.size() == 0) {
                    "Display metadata action accepts no parameters or template."
                }
                parameterObject.entrySet().forEach { (key, value) ->
                    require(key.matches(Regex("[a-z][a-zA-Z0-9]{0,31}")) && value.toString().length <= 128) { "Invalid action parameter: $key." }
                    if (value.isJsonObject) {
                        val settingId = value.asJsonObject["\$setting"].asString
                        require(settingId in settings) { "Unknown setting reference: $settingId." }
                    }
                }
                val actionId = action["id"]?.asString ?: "${trigger["id"].asString}-action-${actionIndex + 1}"
                if (workflow != null) require(action.has("id")) { "Workflow actions must declare stable IDs." }
                V4Action(effect, template, parameterObject.entrySet().associate { it.key to it.value.toString() }, actionId,
                    action["independent"]?.asBoolean ?: false)
            }
            require(actions.map { it.id }.toSet().size == actions.size) { "Action IDs must be unique within a trigger." }
            if (workflow != null && (workflow.executionMode == WorkflowExecutionMode.PARALLEL ||
                    workflow.failurePolicy == WorkflowFailurePolicy.CONTINUE_ON_FAILURE)) {
                require(actions.drop(1).all { it.independent }) { "Parallel/continue workflows require later actions to be marked independent." }
            }
            V4Trigger(trigger["id"].asString, event, parseCondition(trigger.getAsJsonObject("condition"), 1), actions, workflow)
        }
        require(triggers.map { it.id }.toSet().size == triggers.size) { "Trigger IDs must be unique." }
        val review = PackageReview(manifest["id"].asString, manifest["name"].asString, manifest["version"].asString,
            manifest["description"].asString, manifest["author"].asString, manifest["license"].asString,
            manifest["minAndroidApi"].asInt, caps, GlowAppearance("#B48CFF", 45), GlowAppearance.intensities)
        return ValidatedPackage(review.copy(settingDefinitions = settings), emptyMap(), V4Definition(triggers, settings))
    }

    private fun parseV4Settings(settings: JsonObject): Map<String, ModSettingDefinition> {
        val definitions = settings.entrySet().associate { (id, raw) ->
            val item = raw.asJsonObject
            val typeName = item["type"].asString
            val type = when (typeName) {
                "boolean" -> ModSettingType.BOOLEAN
                "integer" -> ModSettingType.INTEGER
                "number" -> ModSettingType.NUMBER
                "duration" -> ModSettingType.DURATION
                "color" -> ModSettingType.COLOR
                "enum" -> ModSettingType.ENUM
                "text" -> ModSettingType.TEXT
                else -> error("Unsupported setting type: ${item["type"].asString}.")
            }
            val allowed = setOf("type", "default", "label", "description") + when (type) {
                ModSettingType.INTEGER, ModSettingType.NUMBER -> setOf("min", "max", "step")
                ModSettingType.DURATION -> setOf("min", "max", "step", "unit")
                ModSettingType.ENUM -> setOf("values")
                ModSettingType.TEXT -> setOf("maxLength")
                else -> emptySet()
            }
            require(item.keySet().all { it in allowed }) { "Unsupported field for $typeName setting $id." }
            val default = item["default"]
            fun number(element: JsonElement): Double {
                require(element.isJsonPrimitive && element.asJsonPrimitive.isNumber)
                return element.asDouble.also { require(it.isFinite()) }
            }
            fun integer(element: JsonElement): Long {
                val value = number(element)
                require(element.asBigDecimal.scale() <= 0 && value >= Long.MIN_VALUE.toDouble() && value <= Long.MAX_VALUE.toDouble())
                return value.toLong()
            }
            val minimum = item["min"]?.let(::number)
            val maximum = item["max"]?.let(::number)
            val unit = item["unit"]?.asString
            val step = item["step"]?.let(::number) ?: when (type) {
                ModSettingType.INTEGER -> 1.0
                ModSettingType.NUMBER, ModSettingType.DURATION -> (((maximum ?: 1.0) - (minimum ?: 0.0)) / 100.0).coerceAtLeast(0.000001)
                else -> null
            }
            val value = when (type) {
                ModSettingType.BOOLEAN -> {
                    require(default.isJsonPrimitive && default.asJsonPrimitive.isBoolean)
                    ModSettingValue.BooleanValue(default.asBoolean)
                }
                ModSettingType.INTEGER -> ModSettingValue.IntegerValue(integer(default))
                ModSettingType.NUMBER -> ModSettingValue.NumberValue(number(default))
                ModSettingType.DURATION -> ModSettingValue.DurationValue(number(default), unit ?: "")
                ModSettingType.COLOR -> ModSettingValue.ColorValue(default.asString)
                ModSettingType.ENUM -> ModSettingValue.EnumValue(default.asString)
                ModSettingType.TEXT -> ModSettingValue.TextValue(default.asString)
            }
            id to ModSettingDefinition(id, item["label"]?.asString ?: id.toSettingLabel(), type, value,
                item["description"]?.asString, minimum, maximum, step, unit,
                item.getAsJsonArray("values")?.map { it.asString }.orEmpty(), item["maxLength"]?.asInt)
        }
        ModSettingValidator.validateDefinitions(definitions)
        return definitions
    }

    private fun parseSettingConditionValue(
        setting: ModSettingDefinition,
        raw: JsonElement,
        comparison: V4Comparison
    ): Pair<V4ValueType, String> = when (setting.defaultValue) {
        is ModSettingValue.BooleanValue -> {
            require(comparison in setOf(V4Comparison.EQUALS, V4Comparison.NOT_EQUALS) && raw.isJsonPrimitive && raw.asJsonPrimitive.isBoolean)
            V4ValueType.BOOLEAN to raw.asBoolean.toString()
        }
        is ModSettingValue.IntegerValue, is ModSettingValue.NumberValue, is ModSettingValue.DurationValue -> {
            require(raw.isJsonPrimitive && raw.asJsonPrimitive.isNumber)
            V4ValueType.NUMBER to raw.asNumber.toString()
        }
        is ModSettingValue.EnumValue, is ModSettingValue.TextValue, is ModSettingValue.ColorValue -> {
            require(comparison in setOf(V4Comparison.EQUALS, V4Comparison.NOT_EQUALS) && raw.isJsonPrimitive && raw.asJsonPrimitive.isString)
            V4ValueType.STRING to raw.asString
        }
    }

    private fun String.toSettingLabel(): String =
        replace(Regex("([a-z0-9])([A-Z])"), "$1 $2").replace('-', ' ').replaceFirstChar { it.uppercase() }

    private fun subscriptions(definition: JsonObject, caps: List<String>, options: List<IntegerSetting>): Map<ModEvent, Subscription> {
        val effects = definition.getAsJsonArray("effects").map { it.asJsonObject }
        val actions = effects.associate { effect ->
            val action = ModEffect.entries.single { it.wireName == effect["effect"].asString }
            val template = effect["template"]?.asString?.let { name -> EffectTemplate.entries.single { it.wireName == name } }
            require(action.capability in caps) { "Effect references an undeclared capability." }
            require(if (action in setOf(ModEffect.DISMISS_OVERLAY, ModEffect.HAPTIC_PULSE)) template == null && !effect.has("params")
                else template != null && effect.has("params")) { "Invalid effect parameters." }
            effect["id"].asString to ModAction(action, template)
        }
        require(effects.size == actions.size) { "Effect IDs must be unique." }
        val usedEffects = mutableSetOf<String>()
        val triggers = definition.getAsJsonArray("triggers").map { it.asJsonObject }
        val subscriptions = triggers.associate { trigger ->
            val event = ModEvent.entries.single { it.wireName == trigger["event"].asString }
            val condition = trigger["condition"]?.asString?.let { name -> Condition.entries.single { it.wireName == name } }
                ?: Condition.ALWAYS
            require(listOfNotNull(event.capability, condition.capability).all { it in caps }) {
                "Trigger references an undeclared capability."
            }
            val id = trigger["effect"].asString
            val action = requireNotNull(actions[id]) { "Trigger refers to an unknown effect." }
            usedEffects += id
            val setting = trigger["setting"]?.asString
            require(if (condition == Condition.BATTERY_BELOW_SETTING) options.any { it.id == setting }
                else setting == null) { "Invalid condition setting reference." }
            event to Subscription(condition, action, setting)
        }
        require(subscriptions.size == triggers.size && usedEffects == actions.keys) {
            "Events must be unique and all effects must be referenced."
        }
        return subscriptions
    }

    private fun schema(name: String): JsonObject = checkNotNull(javaClass.getResourceAsStream("/v2/$name.schema.json"))
        .bufferedReader(Charsets.UTF_8).use { JsonParser.parseReader(it).asJsonObject }

    private fun parse(bytes: ByteArray): JsonElement {
        val text = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes)).toString()
        require(!text.startsWith('\uFEFF')) { "JSON must not include a byte order mark." }
        return JsonReader(text.reader()).use { reader ->
            reader.strictness = Strictness.STRICT
            val value = read(reader, 0)
            require(reader.peek() == JsonToken.END_DOCUMENT) { "Trailing JSON data." }
            value
        }
    }

    private fun read(reader: JsonReader, depth: Int): JsonElement {
        require(depth <= 12) { "JSON is nested too deeply." }
        return when (reader.peek()) {
            JsonToken.BEGIN_OBJECT -> JsonObject().apply {
                reader.beginObject()
                while (reader.hasNext()) {
                    val name = reader.nextName()
                    require(!has(name)) { "Duplicate JSON property." }
                    add(name, read(reader, depth + 1))
                }
                reader.endObject()
            }
            JsonToken.BEGIN_ARRAY -> JsonArray().apply {
                reader.beginArray()
                while (reader.hasNext()) add(read(reader, depth + 1))
                reader.endArray()
            }
            JsonToken.STRING -> JsonPrimitive(reader.nextString().also {
                require(Charsets.UTF_8.newEncoder().canEncode(it)) { "Invalid Unicode in JSON." }
            })
            JsonToken.NUMBER -> JsonPrimitive(BigDecimal(reader.nextString()))
            JsonToken.BOOLEAN -> JsonPrimitive(reader.nextBoolean())
            else -> error("Unsupported JSON value.")
        }
    }

    // The trusted schemas use this deliberately small set of JSON Schema keywords.
    private fun checkSchema(value: JsonElement, rule: JsonObject, path: String) {
        check(rule.keySet().all { it in keywords }) { "Unsupported validator rule." }
        fun test(condition: Boolean) { require(condition) { "Invalid package field: $path." } }
        if (rule.has("oneOf") || rule.has("\$ref")) {
            rule["oneOf"]?.let { choices -> test(choices.asJsonArray.count { choice ->
                runCatching { checkSchema(value, choice.asJsonObject, path); true }.getOrDefault(false)
            } == 1) }
            rule["\$ref"]?.let { ref ->
                val root = JsonParser.parseReader(javaClass.getResourceAsStream("/v2/mod-v4.schema.json")!!.bufferedReader())
                val target = ref.asString.removePrefix("#/").split('/').fold(root) { current, part -> current.asJsonObject[part] }
                checkSchema(value, target.asJsonObject, path)
            }
            return
        }
        rule["const"]?.let { expected ->
            test(if (value.isJsonPrimitive && expected.isJsonPrimitive && value.asJsonPrimitive.isNumber && expected.asJsonPrimitive.isNumber)
                value.asString == expected.asString else value == expected)
        }
        rule.getAsJsonArray("enum")?.let { test(value in it) }
        when (rule["type"]?.asString) {
            "object" -> {
                test(value.isJsonObject)
                val fields = value.asJsonObject
                val rules = rule.getAsJsonObject("properties")
                test(rule.getAsJsonArray("required")?.all { fields.has(it.asString) } ?: true)
                test(fields.size() <= (rule["maxProperties"]?.asInt ?: Int.MAX_VALUE))
                test(if (rule["additionalProperties"]?.isJsonPrimitive == true && rule["additionalProperties"].asJsonPrimitive.isBoolean)
                    !rule["additionalProperties"].asBoolean && fields.keySet().all { rules?.has(it) == true }
                    else rule["additionalProperties"]?.isJsonObject == true)
                rule["additionalProperties"]?.takeIf { it.isJsonObject }?.asJsonObject?.let { extra ->
                    fields.entrySet().filter { rules?.has(it.key) != true }.forEach { (key, child) -> checkSchema(child, extra, "$path.$key") }
                }
                rules?.entrySet()?.forEach { (key, nested) ->
                    fields[key]?.let { checkSchema(it, nested.asJsonObject, "$path.$key") }
                }
            }
            "array" -> {
                test(value.isJsonArray)
                val items = value.asJsonArray
                test(items.size() in (rule["minItems"]?.asInt ?: 0)..(rule["maxItems"]?.asInt ?: Int.MAX_VALUE))
                if (rule["uniqueItems"]?.asBoolean == true) test(items.toSet().size == items.size())
                items.forEachIndexed { index, item -> checkSchema(item, rule["items"].asJsonObject, "$path[$index]") }
            }
            "string" -> {
                test(value.isJsonPrimitive && value.asJsonPrimitive.isString)
                val text = value.asString
                test(text.codePointCount(0, text.length) in
                    (rule["minLength"]?.asInt ?: 0)..(rule["maxLength"]?.asInt ?: Int.MAX_VALUE))
                rule["pattern"]?.let { test(Regex(it.asString).containsMatchIn(text)) }
            }
            "integer" -> {
                test(value.isJsonPrimitive && value.asJsonPrimitive.isNumber)
                val number = value.asBigDecimal
                test(number.scale() <= 0)
                test(number >= BigDecimal.valueOf(rule["minimum"]?.asDouble ?: Int.MIN_VALUE.toDouble()) &&
                    number <= BigDecimal.valueOf(rule["maximum"]?.asDouble ?: Int.MAX_VALUE.toDouble()))
            }
            "number" -> {
                test(value.isJsonPrimitive && value.asJsonPrimitive.isNumber)
                val number = value.asDouble
                test(number.isFinite())
                test(number >= (rule["minimum"]?.asDouble ?: -Double.MAX_VALUE) &&
                    number <= (rule["maximum"]?.asDouble ?: Double.MAX_VALUE))
            }
            "boolean" -> test(value.isJsonPrimitive && value.asJsonPrimitive.isBoolean)
            null -> Unit
            else -> error("Unsupported schema type.")
        }
    }

    private val keywords = setOf("\$schema", "type", "additionalProperties", "properties", "required",
        "const", "enum", "minItems", "maxItems", "items", "uniqueItems", "minLength", "maxLength",
        "pattern", "minimum", "maximum", "\$ref", "oneOf", "maxProperties", "\$defs")
}
