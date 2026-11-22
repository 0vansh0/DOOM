package org.doom.engine

import org.doom.model.*
import org.junit.Assert.*
import org.junit.Test

class CapabilityCompatibilityTest {
    @Test fun shizukuStateMappingCoversInstallRunPermissionBinderAndPrivilegeLevels() {
        assertEquals(ShizukuConnectionPhase.NOT_INSTALLED,
            ShizukuStateMapper.map(false, false, false).phase)
        assertEquals(ShizukuConnectionPhase.NOT_RUNNING,
            ShizukuStateMapper.map(true, false, false).phase)
        val needsPermission = ShizukuStateMapper.map(true, true, false)
        assertEquals(ShizukuConnectionPhase.PERMISSION_REQUIRED, needsPermission.phase)
        assertEquals("Authorization required", needsPermission.displayLabel)
        assertEquals(ShizukuConnectionPhase.PERMISSION_REQUIRED,
            ShizukuStateMapper.map(true, true, false, permissionDenied = true).phase)
        assertEquals(ShizukuConnectionPhase.DISCONNECTED,
            ShizukuStateMapper.map(true, false, false, disconnected = true).phase)
        assertEquals(ShizukuConnectionPhase.CONNECTED_ADB,
            ShizukuStateMapper.map(true, true, true, uid = 2000).phase)
        assertEquals(ShizukuConnectionPhase.CONNECTED_ROOT,
            ShizukuStateMapper.map(true, true, true, uid = 0).phase)
        assertEquals(ShizukuConnectionPhase.ERROR,
            ShizukuStateMapper.map(true, true, true, uid = 10000).phase)
        assertEquals(ShizukuConnectionPhase.CONNECTED_ADB,
            ShizukuStateMapper.map(true, true, true, uid = 2000, serviceApiVersion = 12).phase)
    }

    @Test fun privilegedOperationGateRejectsUnsupportedUnauthorizedAndStaleCalls() {
        val operation = ShizukuOperationPolicy.DIAGNOSTIC_IDENTITY
        val registered = setOf(operation)
        assertEquals(PrivilegedOperationStatus.UNSUPPORTED,
            ShizukuOperationPolicy.authorize("shell.exec", registered, true, true, true,
                setOf("shell.exec"), AdapterPrivilegeLevel.ROOT))
        assertEquals(PrivilegedOperationStatus.UNAVAILABLE,
            ShizukuOperationPolicy.authorize(operation, registered, false, true, true,
                setOf(operation), AdapterPrivilegeLevel.ROOT))
        assertEquals(PrivilegedOperationStatus.UNAUTHORIZED,
            ShizukuOperationPolicy.authorize(operation, registered, true, false, true,
                setOf(operation), AdapterPrivilegeLevel.ROOT))
        assertEquals(PrivilegedOperationStatus.UNAUTHORIZED,
            ShizukuOperationPolicy.authorize(operation, registered, true, true, false,
                setOf(operation), AdapterPrivilegeLevel.ROOT))
        assertEquals(PrivilegedOperationStatus.UNAUTHORIZED,
            ShizukuOperationPolicy.authorize(operation, registered, true, true, true,
                emptySet(), AdapterPrivilegeLevel.ROOT))
        assertEquals(PrivilegedOperationStatus.UNSUPPORTED,
            ShizukuOperationPolicy.authorize(operation, registered, true, true, true,
                setOf(operation), AdapterPrivilegeLevel.OTHER))
        assertEquals(PrivilegedOperationStatus.SUCCESS,
            ShizukuOperationPolicy.authorize(operation, registered, true, true, true,
                setOf(operation), AdapterPrivilegeLevel.ADB))
    }

    @Test fun capabilityProbePolicyModelsPermissionHardwareAndUnknownStatesDeterministically() {
        assertEquals(CapabilityAvailability.AVAILABLE, CapabilityStatusPolicy.api(31, 31))
        assertEquals(CapabilityAvailability.UNSUPPORTED, CapabilityStatusPolicy.api(30, 31))
        assertEquals(CapabilityAvailability.UNKNOWN, CapabilityStatusPolicy.api(null, 31))
        assertEquals(CapabilityAvailability.AVAILABLE, CapabilityStatusPolicy.permission(31, 33, false))
        assertEquals(CapabilityAvailability.DENIED, CapabilityStatusPolicy.permission(33, 33, false))
        assertEquals(CapabilityAvailability.AVAILABLE, CapabilityStatusPolicy.permission(33, 33, true))
        assertEquals(CapabilityAvailability.UNAVAILABLE, CapabilityStatusPolicy.hardware(false))
        assertEquals(CapabilityAvailability.UNKNOWN, CapabilityStatusPolicy.hardware(null))
    }

    private fun cap(id: String, state: CapabilityAvailability = CapabilityAvailability.AVAILABLE,
                    executor: Boolean = true, access: String? = null) = CapabilityStatus(
        id, id, "test capability", state, access, 31,
        if (state == CapabilityAvailability.DENIED) "Android access was denied." else null, executor
    )

    @Test fun separatesCompatibilityFromAndroidAndDoomAuthorization() {
        val denied = ModCompatibilityEvaluator.evaluate(listOf("overlay.render"), emptySet(), 31, 35, true, true,
            mapOf("overlay.render" to cap("overlay.render", CapabilityAvailability.DENIED, access = "SYSTEM_ALERT_WINDOW")))
        assertFalse(denied.compatible)
        assertFalse(denied.authorized)
        assertEquals(CapabilityAvailability.DENIED, denied.capabilities.single().availability)
        assertTrue(denied.capabilities.single().reason.contains("Android access"))

        val approvalMissing = ModCompatibilityEvaluator.evaluate(listOf("battery.read"), emptySet(), 31, 35, true, true,
            mapOf("battery.read" to cap("battery.read")))
        assertTrue(approvalMissing.compatible)
        assertFalse(approvalMissing.authorized)
        assertTrue(approvalMissing.capabilities.single().reason.contains("Approve"))
    }

    @Test fun reportsUnsupportedHardwareUnknownCapabilitiesAndMissingExecutor() {
        val report = ModCompatibilityEvaluator.evaluate(
            listOf("haptics.vibrate", "future.unknown", "future.adapter"), setOf("haptics.vibrate"), 31, 35, true, true,
            mapOf("haptics.vibrate" to cap("haptics.vibrate", CapabilityAvailability.UNAVAILABLE),
                "future.adapter" to cap("future.adapter", CapabilityAvailability.AVAILABLE, executor = false))
        )
        assertEquals(CapabilityAvailability.UNAVAILABLE, report.capabilities[0].availability)
        assertEquals(CapabilityAvailability.UNSUPPORTED, report.capabilities[1].availability)
        assertEquals(CapabilityAvailability.UNSUPPORTED, report.capabilities[2].availability)
        assertFalse(report.compatible)
    }

    @Test fun reportsApiAndFormatCompatibilitySeparately() {
        val report = ModCompatibilityEvaluator.evaluate(emptyList(), emptySet(), 35, 34, false, true, emptyMap())
        assertFalse(report.compatible)
        assertFalse(report.definitionSupported)
        assertTrue(report.manifestSupported)
        assertFalse(report.androidCompatible)
        assertTrue(report.warnings.size >= 2)
    }

    @Test fun standardCapabilitiesRemainIndependentOfOptionalShizukuAdapter() {
        val report = ModCompatibilityEvaluator.evaluate(
            requirements = listOf("battery.read", "overlay.render"),
            approved = setOf("battery.read", "overlay.render"),
            minAndroidApi = 31, currentAndroidApi = 35,
            definitionSupported = true, manifestSupported = true,
            capabilities = mapOf(
                "battery.read" to cap("battery.read"),
                "overlay.render" to cap("overlay.render")
            )
        )
        assertTrue(report.compatible)
        assertTrue(report.authorized)
    }
}
