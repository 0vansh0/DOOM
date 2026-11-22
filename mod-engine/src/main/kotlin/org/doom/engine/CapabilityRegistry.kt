package org.doom.engine

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.doom.model.CapabilityDefinition
import org.doom.model.CapabilityParameterDefinition
import org.doom.model.CapabilityParameterType
import org.doom.model.CapabilityRisk
import org.doom.model.CapabilityAdapterDefinition
import org.doom.model.PrivilegedOperationDefinition
import org.doom.model.AdapterPrivilegeLevel
import org.doom.model.RestorePolicy

/** Versioned trusted catalog. Imported packages may reference entries but cannot add or implement them. */
object CapabilityRegistry {
    const val VERSION = 2
    private val catalog: JsonObject = checkNotNull(javaClass.getResourceAsStream("/v2/registry.json"))
        .bufferedReader().use { JsonParser.parseReader(it).asJsonObject }
        .also { require(it["registryVersion"].asInt == VERSION) { "Unsupported capability registry version." } }

    val entries: List<CapabilityDefinition> = catalog.getAsJsonArray("capabilities").map { element ->
        val item = element.asJsonObject
        val parameters = item.getAsJsonObject("parameterSchema")?.entrySet()?.associate { (name, raw) ->
            val parameter = raw.asJsonObject
            val type = CapabilityParameterType.valueOf(parameter["type"].asString.uppercase())
            name to CapabilityParameterDefinition(type,
                parameter["required"]?.asBoolean ?: false,
                parameter["minimum"]?.asDouble,
                parameter["maximum"]?.asDouble,
                parameter["maxLength"]?.asInt,
                parameter.getAsJsonArray("values")?.map { it.asString }?.toSet().orEmpty())
        }.orEmpty()
        CapabilityDefinition(
            id = item["id"].asString,
            contractVersion = item["contractVersion"].asInt,
            displayName = item["displayName"].asString,
            description = item["description"].asString,
            minimumAndroidApi = item["minAndroidApi"].asInt,
            parameterSchema = parameters,
            requiredAndroidPermissions = item.getAsJsonArray("requiredAndroidPermissions").map { it.asString },
            requiredDoomApproval = item["doomApproval"].asBoolean,
            requiredHardwareFeatures = item.getAsJsonArray("requiredHardwareFeatures").map { it.asString },
            requiredAdapter = item["requiredAdapter"]?.takeUnless { it.isJsonNull }?.asString ?: item["adapterId"]?.takeUnless { it.isJsonNull }?.asString,
            minimumPrivilegeLevel = item["minimumPrivilegeLevel"].asString,
            trustedExecutorId = item["trustedExecutorId"]?.takeUnless { it.isJsonNull }?.asString,
            resourceLimits = item.getAsJsonObject("resourceLimits").entrySet().associate { it.key to it.value.asLong },
            risk = CapabilityRisk.valueOf(item["riskClassification"].asString),
            auditRequired = item["auditRequired"].asBoolean,
            implemented = item["implemented"].asBoolean,
            requiredCapabilities = item.getAsJsonArray("requiredCapabilities")?.map { it.asString }?.toSet().orEmpty(),
            unavailableReason = item["unavailableReason"]?.takeUnless { it.isJsonNull }?.asString
        )
    }.also { definitions ->
        require(definitions.map { it.id }.distinct().size == definitions.size) { "Duplicate capability IDs in trusted registry." }
        require(definitions.all { it.contractVersion > 0 && it.minimumAndroidApi >= 1 }) { "Invalid capability contract metadata." }
    }

    val byId: Map<String, CapabilityDefinition> = entries.associateBy { it.id }
    val adapters: List<CapabilityAdapterDefinition> = catalog.getAsJsonArray("adapters").map { element ->
        val item = element.asJsonObject
        CapabilityAdapterDefinition(item["id"].asString, item["displayName"].asString,
            item.getAsJsonArray("privilegeLevels").map { it.asString }.toSet(),
            item.getAsJsonArray("operations").map { it.asJsonObject["id"].asString }.toSet(),
            item.getAsJsonArray("capabilityIds").map { it.asString }.toSet(), item["authorization"].asString,
            item["implemented"].asBoolean, CapabilityRisk.valueOf(item["riskClassification"].asString))
    }
    val operationDefinitions: Map<String, PrivilegedOperationDefinition> = catalog.getAsJsonArray("privilegedOperations").map { element ->
        val item = element.asJsonObject
        val parameters = item.getAsJsonObject("parameterSchema")?.entrySet()?.associate { (name, raw) ->
            val parameter = raw.asJsonObject
            val type = CapabilityParameterType.valueOf(parameter["type"].asString.uppercase())
            name to CapabilityParameterDefinition(type, parameter["required"]?.asBoolean ?: false,
                parameter["minimum"]?.asDouble, parameter["maximum"]?.asDouble,
                parameter["maxLength"]?.asInt, parameter.getAsJsonArray("values")?.map { it.asString }?.toSet().orEmpty())
        }.orEmpty()
        PrivilegedOperationDefinition(
            id = item["id"].asString, version = item["version"].asInt,
            requiredCapability = item["requiredCapability"].asString,
            minimumAndroidApi = item["minimumAndroidApi"].asInt,
            minimumPrivilege = AdapterPrivilegeLevel.valueOf(item["minimumPrivilegeLevel"].asString),
            risk = CapabilityRisk.valueOf(item["riskClassification"].asString),
            confirmationRequired = item["confirmationRequired"].asBoolean,
            modAccessible = item["modAccessible"].asBoolean, targetId = item["targetId"].asString,
            requiredAdapter = item["requiredAdapter"]?.takeUnless { it.isJsonNull }?.asString,
            requiredAndroidPermissions = item.getAsJsonArray("requiredAndroidPermissions").map { it.asString },
            parameterSchema = parameters,
            preconditions = item.getAsJsonArray("preconditions").map { it.asString },
            postconditions = item.getAsJsonArray("postconditions").map { it.asString },
            callsPerMinute = item["callsPerMinute"].asInt, timeoutMillis = item["timeoutMillis"].asLong,
            auditRequired = item["auditRequired"].asBoolean,
            minimumValue = parameters.values.firstOrNull()?.minimum?.toLong(),
            maximumValue = parameters.values.firstOrNull()?.maximum?.toLong(),
            restorePolicy = RestorePolicy.valueOf(item["recoveryPolicy"].asString)
        )
    }.also { definitions ->
        require(definitions.map { it.id }.distinct().size == definitions.size) { "Duplicate privileged operation IDs." }
        require(definitions.all { it.version > 0 && it.minimumAndroidApi >= 1 && it.callsPerMinute > 0 && it.timeoutMillis in 1..30_000 }) {
            "Invalid privileged operation metadata."
        }
        require(definitions.none { it.modAccessible }) { "Privileged operations cannot be mod-accessible in this registry version." }
        require(definitions.all { it.requiredCapability in byId }) { "Privileged operation references an unknown capability." }
        require(definitions.all { it.requiredCapability in byId }) { "Privileged operation references an unknown capability." }
    }.associateBy { it.id }
    val actionCapabilities: Map<String, String> = catalog.getAsJsonObject("actions").entrySet().associate { it.key to it.value.asString }
    val eventCapabilities: Map<String, String?> = catalog.getAsJsonObject("events").entrySet().associate { it.key to it.value.takeUnless { v -> v.isJsonNull }?.asString }
    val conditionCapabilities: Map<String, String?> = catalog.getAsJsonObject("conditions").entrySet().associate { it.key to it.value.takeUnless { v -> v.isJsonNull }?.asString }
}
