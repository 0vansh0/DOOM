package org.doom.model

enum class CapabilityAvailability { AVAILABLE, DENIED, UNAVAILABLE, UNSUPPORTED, UNKNOWN }
enum class CapabilityRisk { LOW, MEDIUM, HIGH }
enum class CapabilityParameterType { STRING, INTEGER, NUMBER, BOOLEAN, ENUM }

data class CapabilityParameterDefinition(
    val type: CapabilityParameterType,
    val required: Boolean = false,
    val minimum: Double? = null,
    val maximum: Double? = null,
    val maxLength: Int? = null,
    val values: Set<String> = emptySet()
)

data class CapabilityDefinition(
    val id: String,
    val contractVersion: Int,
    val displayName: String,
    val description: String,
    val minimumAndroidApi: Int,
    val parameterSchema: Map<String, CapabilityParameterDefinition>,
    val requiredAndroidPermissions: List<String>,
    val requiredDoomApproval: Boolean,
    val requiredHardwareFeatures: List<String>,
    val requiredAdapter: String?,
    val minimumPrivilegeLevel: String,
    val trustedExecutorId: String?,
    val resourceLimits: Map<String, Long>,
    val risk: CapabilityRisk,
    val auditRequired: Boolean,
    val implemented: Boolean,
    val requiredCapabilities: Set<String> = emptySet(),
    val unavailableReason: String? = null
)

data class CapabilityAdapterDefinition(
    val id: String,
    val displayName: String,
    val privilegeLevels: Set<String>,
    val operationIds: Set<String>,
    val capabilityIds: Set<String>,
    val authorization: String,
    val implemented: Boolean,
    val risk: CapabilityRisk
)

object CapabilityStatusPolicy {
    fun api(currentApi: Int?, minimumApi: Int): CapabilityAvailability = when {
        currentApi == null || minimumApi < 1 -> CapabilityAvailability.UNKNOWN
        currentApi < minimumApi -> CapabilityAvailability.UNSUPPORTED
        else -> CapabilityAvailability.AVAILABLE
    }

    fun permission(currentApi: Int, introducedApi: Int, granted: Boolean): CapabilityAvailability = when {
        currentApi < introducedApi -> CapabilityAvailability.AVAILABLE
        granted -> CapabilityAvailability.AVAILABLE
        else -> CapabilityAvailability.DENIED
    }

    fun hardware(present: Boolean?): CapabilityAvailability = when (present) {
        true -> CapabilityAvailability.AVAILABLE
        false -> CapabilityAvailability.UNAVAILABLE
        null -> CapabilityAvailability.UNKNOWN
    }
}

data class CapabilityStatus(
    val id: String,
    val displayName: String,
    val description: String,
    val availability: CapabilityAvailability,
    val requiredAndroidAccess: String? = null,
    val requiredApi: Int? = null,
    val reason: String? = null,
    val executorAvailable: Boolean = false,
    val adapterId: String? = null,
    val privilegeTier: String = "STANDARD",
    val requiresDoomApproval: Boolean = true,
    val requiredDoomCapabilities: Set<String> = emptySet(),
    val risk: CapabilityRisk = CapabilityRisk.LOW,
    val requiredAndroidPermissions: List<String> = emptyList(),
    val requiredHardwareFeatures: List<String> = emptyList(),
    val minimumPrivilegeLevel: String = "STANDARD",
    val resourceLimits: Map<String, Long> = emptyMap(),
    val auditRequired: Boolean = true,
    val unavailableReason: String? = null
)

data class DisplayMetadata(
    val widthPixels: Int,
    val heightPixels: Int,
    val densityDpi: Int,
    val rotation: Int
)

data class DeviceCapabilitySnapshot(
    val scannedAtMillis: Long,
    val androidApi: Int?,
    val androidRelease: String?,
    val manufacturer: String?,
    val model: String?,
    val appForeground: Boolean,
    val capabilities: List<CapabilityStatus>,
    val overlayAuthorization: CapabilityAvailability,
    val notificationAuthorization: CapabilityAvailability,
    val vibrationHardware: CapabilityAvailability,
    val foregroundServiceEligibility: CapabilityAvailability,
    val adapterStatuses: List<AdapterStatus> = emptyList(),
    val scanError: String? = null,
    val displayMetadata: DisplayMetadata? = null
)

data class AdapterStatus(
    val adapterId: String,
    val displayName: String,
    val connectionStatus: String,
    val availability: CapabilityAvailability,
    val supportedCapabilities: Set<String>,
    val permissionRequirements: List<String> = emptyList(),
    val failureReason: String? = null,
    val privilegeLevel: String = "UNKNOWN",
    val supportedOperations: Set<String> = emptySet(),
    val capabilityRequirements: Map<String, Set<String>> = emptyMap()
)

data class ModCapabilityCompatibility(
    val capabilityId: String,
    val availability: CapabilityAvailability,
    val doomApproved: Boolean,
    val reason: String,
    val risk: CapabilityRisk = CapabilityRisk.LOW,
    val requiredAndroidPermissions: List<String> = emptyList(),
    val requiredAdapter: String? = null
)

data class ModCompatibilityReport(
    val compatible: Boolean,
    val authorized: Boolean,
    val definitionSupported: Boolean,
    val manifestSupported: Boolean,
    val androidCompatible: Boolean,
    val capabilities: List<ModCapabilityCompatibility>,
    val warnings: List<String>
)

/** Future adapters may report status, but this interface deliberately exposes no privileged operation. */
interface CapabilityAdapter {
    val adapterId: String
    val displayName: String
    fun status(): AdapterStatus
}

object ModCompatibilityEvaluator {
    fun evaluate(
        requirements: Collection<String>,
        approved: Set<String>,
        minAndroidApi: Int,
        currentAndroidApi: Int?,
        definitionSupported: Boolean,
        manifestSupported: Boolean,
        capabilities: Map<String, CapabilityStatus>
    ): ModCompatibilityReport {
        val androidCompatible = currentAndroidApi != null && currentAndroidApi >= minAndroidApi
        val rows = requirements.distinct().map { id ->
            val status = capabilities[id]
            val availability = when {
                status == null -> CapabilityAvailability.UNSUPPORTED
                !status.executorAvailable -> CapabilityAvailability.UNSUPPORTED
                else -> status.availability
            }
            val requiredGrants = status?.requiredDoomCapabilities?.ifEmpty { setOf(id) } ?: setOf(id)
            val doomApproved = status?.requiresDoomApproval != true || requiredGrants.all { it in approved }
            val reason = when {
                status == null -> "Unknown capability; no trusted DOOM executor is registered."
                !status.executorAvailable -> status.unavailableReason ?: "No trusted DOOM executor is available for this capability."
                status.requiredApi != null && currentAndroidApi != null && currentAndroidApi < status.requiredApi -> "Requires Android API ${status.requiredApi}."
                availability == CapabilityAvailability.DENIED -> status.reason ?: "Android access is denied."
                availability == CapabilityAvailability.UNAVAILABLE -> status.reason ?: "Required device service or hardware is unavailable."
                availability == CapabilityAvailability.UNSUPPORTED -> status.reason ?: "This capability is unsupported by this DOOM build."
                availability == CapabilityAvailability.UNKNOWN -> status.reason ?: "Availability could not be determined."
                !doomApproved -> "Approve this capability for this mod in DOOM."
                else -> "Available and approved."
            }
            ModCapabilityCompatibility(id, availability, doomApproved, reason,
                status?.risk ?: CapabilityRisk.HIGH,
                status?.requiredAndroidPermissions.orEmpty(), status?.adapterId)
        }
        val compatible = definitionSupported && manifestSupported && androidCompatible && rows.all {
            it.availability == CapabilityAvailability.AVAILABLE
        }
        val authorized = rows.all { it.availability == CapabilityAvailability.AVAILABLE && it.doomApproved }
        val warnings = buildList {
            if (!definitionSupported) add("The mod definition version is not supported.")
            if (!manifestSupported) add("The mod manifest version is not supported.")
            if (!androidCompatible) add("This mod requires Android API $minAndroidApi.")
        }
        return ModCompatibilityReport(compatible, authorized, definitionSupported, manifestSupported, androidCompatible, rows, warnings)
    }
}
