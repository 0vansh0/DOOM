package org.doom.data

import androidx.room.Room
import android.content.ContextWrapper
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import org.doom.database.*
import org.doom.domain.*
import org.doom.engine.runtime.RuntimeDiagnostic
import org.doom.engine.ArchiveReader
import org.doom.model.*
import org.doom.model.ModStatus.*
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class ModRepositoryTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val sample = "community.batteryglow"

    @Test fun declarativeSettingsAreValidatedIsolatedAndPersistAcrossDatabaseReopen() = runBlocking {
        val id = "community.chargingworkflow"
        val name = "settings-${UUID.randomUUID()}.db"
        val privateRoot = java.io.File(context.filesDir, "settings-test-${UUID.randomUUID()}")
        check(privateRoot.mkdirs())
        val isolatedContext = object : ContextWrapper(context) { override fun getFilesDir() = privateRoot }
        try {
            val first = Room.databaseBuilder(context, DoomDatabase::class.java, name).build()
            try {
                val repository = RoomModRepository(isolatedContext, first, TestPreferences())
                val bytes = context.assets.open("charging-workflow.doommod").use { it.readBytes() }
                val review = ArchiveReader.validate(bytes.inputStream())
                val sha = repository.packageStorage.stage(bytes.inputStream())
                repository.packageStorage.publish(id, sha)
                first.mods().insert(InstalledModEntity(id, review.name, review.version,
                    repository.packageStorage.packagePath(id), minAndroidApi = review.minAndroidApi, sha256 = sha))
                // observeDetails initializes the repository and backfills typed definitions from this verified V4 file.
                assertTrue(repository.observeDetails(id).first()!!.settingDefinitions.isNotEmpty())
                val values = mapOf(
                    "enabled" to ModSettingValue.BooleanValue(false),
                    "accentColor" to ModSettingValue.ColorValue("#33CCAA"),
                    "intensity" to ModSettingValue.IntegerValue(55),
                    "ringThickness" to ModSettingValue.IntegerValue(9),
                    "durationMs" to ModSettingValue.DurationValue(5000.0, "ms"),
                    "position" to ModSettingValue.EnumValue("bottom-center"),
                    "message" to ModSettingValue.TextValue("Plugged in"),
                    "scale" to ModSettingValue.NumberValue(1.2)
                )
                assertEquals(ModResult.Success, repository.configureSettings(id, values))
                assertEquals(values, repository.observeDetails(id).first()!!.settingValues)
                val invalid = values + ("accentColor" to ModSettingValue.ColorValue("purple"))
                assertEquals(ModError.INVALID_SETTING, (repository.configureSettings(id, invalid) as ModResult.Rejected).reason)
                // Simulate a pre-6D installed row whose old Room version had no definition metadata.
                first.mods().update(first.mods().record(id)!!.mod.copy(settingDefinitions = null))
            } finally { first.close() }
            val reopened = Room.databaseBuilder(context, DoomDatabase::class.java, name)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7).build()
            try {
                val repository = RoomModRepository(isolatedContext, reopened, TestPreferences())
                val mod = repository.observeDetails(id).first()!!
                assertTrue("V4 setting definitions should be recovered from the verified private package", mod.settingDefinitions.isNotEmpty())
                assertEquals(ModSettingValue.BooleanValue(false), mod.settingValues["enabled"])
                assertEquals(ModSettingValue.ColorValue("#33CCAA"), mod.settingValues["accentColor"])
                assertEquals(ModSettingValue.DurationValue(5000.0, "ms"), mod.settingValues["durationMs"])
                assertEquals(ModSettingValue.TextValue("Plugged in"), mod.settingValues["message"])
                reopened.mods().putSettings(ModSettingsEntity(id,
                    """{"values":{"accentColor":"purple","durationMs":999999}}""", System.currentTimeMillis()))
                val recovered = repository.observeDetails(id).first()!!
                assertEquals(ModSettingValue.ColorValue("#A78BFA"), recovered.settingValues["accentColor"])
                assertEquals(ModSettingValue.DurationValue(3000.0, "ms"), recovered.settingValues["durationMs"])
            } finally { reopened.close() }
        } finally {
            context.deleteDatabase(name)
            privateRoot.deleteRecursively()
        }
    }

    @Test fun idempotentRegistrationAndReactiveCrud() = runBlocking {
        withDatabase { db ->
            val repository = RoomModRepository(context, db, TestPreferences())
            assertTrue(repository.observeInstalled().first().isEmpty())
            val installed = async(start = CoroutineStart.UNDISPATCHED) {
                repository.observeInstalled().first { it.isNotEmpty() }.single()
            }
            repository.installExample(sample)
            repository.installExample(sample)
            assertEquals(INSTALLED_DISABLED, installed.await().status)
            assertEquals(1, db.mods().rows().size)

            val appearance = GlowAppearance("#102030", 30, RingStyle())
            val updated = async(start = CoroutineStart.UNDISPATCHED) {
                repository.observeDetails(sample).first { it?.appearance == appearance }
            }
            assertEquals(ModResult.Success, repository.configure(sample, appearance))
            assertEquals(appearance, updated.await()?.appearance)
            assertEquals(ModResult.Success, repository.setGrant(sample, true, "overlay.render"))
            assertTrue(db.mods().record(sample)!!.grants.first { it.capabilityId == "overlay.render" }.approved)
            repository.recordFailure(sample, "Test failure")
            assertEquals(ModResult.Success, repository.uninstall(sample))
            assertTrue(repository.observeInstalled().first().isEmpty())
            assertNull(db.mods().record(sample))
            assertTrue(db.mods().logs(sample).isEmpty())

            // If settings or grants survived the FK cascade, registering again would expose them.
            repository.installExample(sample)
            val reinstalled = db.mods().record(sample)!!
            assertNull(reinstalled.settings)
            assertTrue(reinstalled.grants.isEmpty())
        }
    }

    @Test fun permissionAndSafeModeAreIndependentOfDesiredState() = runBlocking {
        withDatabase { db ->
            val prefs = TestPreferences()
            var authorized = false
            val repository = RoomModRepository(context, db, prefs, overlayAuthorized = { authorized })
            repository.installExample(sample)
            assertEquals(ModError.PERMISSION_REQUIRED, (repository.requestEnable(sample) as ModResult.Rejected).reason)
            assertEquals(PERMISSION_REQUIRED, db.mods().record(sample)!!.mod.status)
            assertTrue(db.mods().record(sample)!!.mod.desiredEnabled)
            repository.setGrant(sample, true, "overlay.render")
            repository.setGrant(sample, true, "battery.read")
            repository.setGrant(sample, true, "ui.preview")
            assertEquals(PERMISSION_REQUIRED, db.mods().record(sample)!!.mod.status)
            authorized = true
            repository.reconcileRequirements()
            assertEquals(READY_TO_ACTIVATE, db.mods().record(sample)!!.mod.status)
            repository.setSafeMode(true)
            assertEquals(INSTALLED_DISABLED, db.mods().record(sample)!!.mod.status)
            assertTrue(db.mods().record(sample)!!.mod.desiredEnabled)
            assertEquals(ModError.SAFE_MODE, (repository.requestEnable(sample) as ModResult.Rejected).reason)
            repository.setSafeMode(false)
            assertEquals(READY_TO_ACTIVATE, db.mods().record(sample)!!.mod.status)
            repository.setGrant(sample, false, "overlay.render")
            assertEquals(PERMISSION_REQUIRED, db.mods().record(sample)!!.mod.status)
            assertFalse(db.mods().record(sample)!!.grants.first { it.capabilityId == "overlay.render" }.approved)
            repository.disableAll()
            assertFalse(db.mods().record(sample)!!.mod.desiredEnabled)
        }
    }

    @Test fun reopenedDatabaseReconcilesInterruptedOperationsAndKeepsSettings() = runBlocking {
        val name = "lifecycle-${UUID.randomUUID()}.db"
        val appearance = GlowAppearance("#AABBCC", 44, RingStyle())
        val prefs = TestPreferences()
        val oldId = "legacy.pending"
        val relative = "mods/$oldId.doommod"
        val file = java.io.File(context.filesDir, relative)
        file.parentFile?.mkdirs()
        try {
            file.writeText("legacy private package")
            val original = Room.databaseBuilder(context, DoomDatabase::class.java, name).build()
            try {
                val repository = RoomModRepository(context, original, prefs)
                repository.installExample(sample)
                repository.configure(sample, appearance)
                val mod = original.mods().record(sample)!!.mod
                original.mods().update(mod.copy(desiredEnabled = true, status = CHECKING_REQUIREMENTS))
                original.mods().insert(InstalledModEntity(oldId, "Legacy", "1.0.0", relative, status = UNINSTALLING))
            } finally {
                original.close()
            }
            val reopened = Room.databaseBuilder(context, DoomDatabase::class.java, name).build()
            try {
                val repository = RoomModRepository(context, reopened, prefs)
                val mod = repository.observeInstalled().first().single()
                assertEquals(PERMISSION_REQUIRED, mod.status)
                assertTrue(mod.desiredEnabled)
                assertEquals(appearance, mod.appearance)
                assertFalse(file.exists())
            } finally {
                reopened.close()
            }
        } finally {
            file.delete()
            java.io.File(context.filesDir, "mods/$sample.doommod").delete()
            context.deleteDatabase(name)
        }
    }

    @Test fun concurrentRequestsAndFailureLoggingRemainConsistent() = runBlocking {
        withDatabase { db ->
            val repository = RoomModRepository(context, db, TestPreferences(), overlayAuthorized = { true })
            repository.installExample(sample)
            repository.setGrant(sample, true, "overlay.render")
            repository.setGrant(sample, true, "battery.read")
            repository.setGrant(sample, true, "ui.preview")
            coroutineScope {
                repeat(30) { i ->
                    launch(Dispatchers.Default) {
                        val result = if (i % 2 == 0) repository.requestEnable(sample) else repository.disable(sample)
                        assertEquals(ModResult.Success, result)
                    }
                }
            }
            val mod = db.mods().record(sample)!!.mod
            assertEquals(if (mod.desiredEnabled) READY_TO_ACTIVATE else INSTALLED_DISABLED, mod.status)
            assertEquals(ModResult.Success, repository.recordFailure(sample, "Requirement probe failed"))
            assertEquals(ERROR, db.mods().record(sample)!!.mod.status)
            assertEquals("Requirement probe failed", db.mods().record(sample)!!.mod.lastError)
            assertEquals("ERROR", db.mods().logs(sample).single().result)
            assertEquals(ModError.NOT_FOUND, (repository.disable("missing.id") as ModResult.Rejected).reason)
        }
    }

    @Test fun incompatibleDeviceCommitsExplicitStatus() = runBlocking {
        withDatabase { db ->
            val repository = RoomModRepository(context, db, TestPreferences(), androidApi = 30)
            db.mods().insert(InstalledModEntity(sample, "Sample", "1.0.0", "mods/$sample.doommod"))
            assertEquals(ModError.INCOMPATIBLE, (repository.requestEnable(sample) as ModResult.Rejected).reason)
            assertEquals(INCOMPATIBLE, db.mods().record(sample)!!.mod.status)
        }
    }

    @Test fun globalPreferencesCanBeReadBackFromDataStore() = runBlocking {
        val prefs = DataStoreDoomPreferences(context)
        val before = prefs.values.first()
        try {
            prefs.setSafeMode(true)
            prefs.setTheme(ThemePreference.SYSTEM)
            prefs.setDiagnosticLogging(true)
            val reloaded = DataStoreDoomPreferences(context).values.first()
            assertTrue(reloaded.safeMode)
            assertEquals(ThemePreference.SYSTEM, reloaded.theme)
            assertTrue(reloaded.diagnosticLogging)
        } finally {
            prefs.setSafeMode(before.safeMode)
            prefs.setTheme(before.theme)
            prefs.setDiagnosticLogging(before.diagnosticLogging)
        }
    }

    @Test fun verifiedDefaultsResetAndRepeatedFailuresQuarantineOnlyOneMod() = runBlocking {
        withDatabase { db ->
            val repository = RoomModRepository(context, db, TestPreferences(), overlayAuthorized = { true })
            repository.installExample(sample)
            val defaults = repository.observeDetails(sample).first()!!.appearance
            assertEquals(7, defaults.ring?.thicknessDp)
            val updated = defaults.copy(ring = defaults.ring!!.copy(thicknessDp = 9, durationSeconds = 8))
            assertEquals(ModResult.Success, repository.configure(sample, updated))
            assertEquals(updated, repository.observeDetails(sample).first()!!.appearance)
            assertEquals(ModResult.Success, repository.resetSettings(sample))
            assertEquals(defaults, repository.observeDetails(sample).first()!!.appearance)
            db.mods().insert(InstalledModEntity("local.healthy", "Healthy", "1.0.0", "mods/local.healthy.doommod"))
            listOf("overlay.render", "battery.read", "ui.preview").forEach { repository.setGrant(sample, true, it) }
            repeat(3) {
                assertEquals(ModResult.Success, repository.requestEnable(sample))
                repository.recordRuntimeDiagnostic(RuntimeDiagnostic(sample, "runtime_failed", "ERROR", "Test failure"))
            }
            assertFalse(db.mods().record(sample)!!.mod.desiredEnabled)
            assertEquals(ModError.QUARANTINED, (repository.requestEnable(sample) as ModResult.Rejected).reason)
            assertEquals(INSTALLED_DISABLED, db.mods().record("local.healthy")!!.mod.status)
        }
    }

    private suspend fun withDatabase(check: suspend (DoomDatabase) -> Unit) {
        val db = Room.inMemoryDatabaseBuilder(context, DoomDatabase::class.java).build()
        try {
            withTimeout(20_000) { check(db) }
        } finally {
            java.io.File(context.filesDir, "mods/$sample.doommod").delete()
            db.close()
        }
    }

    private class TestPreferences : DoomPreferences {
        override val values = MutableStateFlow(GlobalPreferences())
        override suspend fun setSafeMode(enabled: Boolean) { values.value = values.value.copy(safeMode = enabled) }
        override suspend fun setTheme(theme: ThemePreference) { values.value = values.value.copy(theme = theme) }
        override suspend fun setDiagnosticLogging(enabled: Boolean) { values.value = values.value.copy(diagnosticLogging = enabled) }
        override suspend fun setPreviewOnOpen(enabled: Boolean) { values.value = values.value.copy(previewOnOpen = enabled) }
    }
}
