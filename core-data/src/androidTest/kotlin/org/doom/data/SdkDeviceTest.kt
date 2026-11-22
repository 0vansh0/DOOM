package org.doom.data

import android.content.ContentValues
import android.net.Uri
import android.provider.MediaStore
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.doom.database.DoomDatabase
import org.doom.domain.ModError
import org.doom.domain.ModResult
import org.doom.model.ModStatus
import org.doom.model.CapabilityAvailability
import org.doom.model.AdapterStatus
import org.doom.model.CapabilityAdapter
import org.doom.engine.runtime.DefaultModRuntime
import org.doom.engine.runtime.ModEvent
import org.doom.engine.runtime.RuntimeDiagnostic
import org.doom.engine.CapabilityRegistry
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files
import java.util.UUID

class SdkDeviceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun probesActualDeviceApisWithoutGrantingCapabilities() {
        val scanner = DeviceCapabilityScanner(context)
        val snapshot = scanner.snapshot()
        android.util.Log.i("DoomSdkConformance", snapshot.toString())
        assertEquals("AVAILABLE", scanner.availability("clock.read"))
        assertEquals("AVAILABLE", scanner.availability("ui.preview"))
        assertEquals("UNSUPPORTED", scanner.availability("root.exec"))
        val reportedIds = (0 until snapshot.getJSONArray("capabilities").length())
            .map { snapshot.getJSONArray("capabilities").getJSONObject(it).getString("id") }.toSet()
        assertEquals(CapabilityRegistry.entries.map { it.id }.toSet(), reportedIds)
    }

    @Test fun typedDeviceScanSeparatesAccessHardwareAndAdapterStatus() {
        val adapter = object : CapabilityAdapter {
            override val adapterId = "test.adapter"
            override val displayName = "Test adapter"
            override fun status() = AdapterStatus(adapterId, displayName, "DISCONNECTED",
                CapabilityAvailability.UNAVAILABLE, setOf("future.test"), listOf("user connection"), "Not connected.")
        }
        val scanner = DeviceCapabilityScanner(context, listOf(adapter))
        val scan = scanner.scan(appForeground = false)
        assertNotNull(scan.androidApi)
        assertNotNull(scan.manufacturer)
        assertNotNull(scan.model)
        assertTrue(scan.capabilities.any { it.id == "battery.read" && it.executorAvailable })
        assertTrue(scan.capabilities.any { it.id == "overlay.render" && it.availability == scan.overlayAuthorization })
        assertEquals(CapabilityAvailability.UNKNOWN, scan.foregroundServiceEligibility)
        assertEquals(CapabilityAvailability.UNAVAILABLE, scan.adapterStatuses.single().availability)
        assertEquals("Not connected.", scan.adapterStatuses.single().failureReason)
    }

    @Test fun cliSdk2PackagesImportPersistConfigureReopenAndUninstall() = runBlocking {
        val name = "sdk2-${UUID.randomUUID()}.db"
        fun open() = Room.databaseBuilder(context, DoomDatabase::class.java, name).build()
        var db = open()
        var repository = RoomModRepository(context, db)
        val ids = mutableListOf<String>()
        try {
            for (asset in listOf("power-card", "focus-pulse", "low-battery-card")) {
                val resolver = context.contentResolver
                val uri = requireNotNull(resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    ContentValues().apply {
                        put(MediaStore.Downloads.DISPLAY_NAME, "${UUID.randomUUID()}.doommod")
                        put(MediaStore.Downloads.MIME_TYPE, "application/octet-stream")
                    }))
                val coordinator = ModImportCoordinator(context, repository)
                try {
                    context.assets.open("$asset.doommod").use { input ->
                        requireNotNull(resolver.openOutputStream(uri)).use { input.copyTo(it) }
                    }
                    val review = coordinator.review(uri.toString())
                    val id = review.manifest.id
                    ids += id
                    assertEquals(ModResult.Success, coordinator.install(review))
                    assertEquals(ModStatus.INSTALLED_DISABLED, db.mods().record(id)!!.mod.status)
                    assertTrue(db.mods().record(id)!!.grants.isEmpty())
                    assertEquals(ModError.PERMISSION_REQUIRED, (repository.requestEnable(id) as ModResult.Rejected).reason)
                    review.manifest.capabilities.forEach { repository.setGrant(id, true, it) }
                    val installed = repository.observeDetails(id).first()!!
                    if (asset == "low-battery-card") {
                        assertEquals(ModResult.Success, repository.configure(id,
                            installed.appearance.copy(options = mapOf("threshold" to 80))))
                        assertEquals(ModError.INVALID_SETTING, (repository.configure(id,
                            installed.appearance.copy(options = mapOf("other" to 80))) as ModResult.Rejected).reason)
                    }
                    val definition = repository.loadRuntimeDefinition(id)
                    assertEquals(id, definition.review.id)
                    if (asset == "focus-pulse" && DeviceCapabilityScanner(context).availability("haptics.vibrate") == "AVAILABLE") {
                        val ready = repository.observeDetails(id).first()!!
                        val events = mutableListOf<RuntimeDiagnostic>()
                        InstrumentationRegistry.getInstrumentation().runOnMainSync {
                            val runtime = DefaultModRuntime(AndroidEffects(context), android.os.SystemClock::elapsedRealtime,
                                { false }, { events.add(it) })
                            runtime.begin(false)
                            assertTrue(runtime.activate(ready, definition))
                            runtime.dispatch(ModEvent.MANUAL, id)
                            assertTrue(events.any { it.event == "haptics.pulse" && it.result == "SUBMITTED" })
                            runtime.stop()
                        }
                        android.util.Log.i("DoomSdkConformance", "Native haptic request submitted and cleaned. Physical sensation not asserted.")
                    }
                } finally {
                    coordinator.discard()
                    resolver.delete(uri, null, null)
                }
            }
            db.close()
            db = open()
            repository = RoomModRepository(context, db)
            assertEquals(3, repository.observeInstalled().first().size)
            val configured = repository.observeDetails("local.lowbatterycard").first()!!
            assertEquals(80, configured.appearance.options["threshold"])
            assertEquals(1, configured.optionDefinitions.size)
            assertTrue(configured.approvedCapabilities.containsAll(configured.capabilities))
            ids.forEach { id ->
                assertEquals(ModResult.Success, repository.uninstall(id))
                assertFalse(Files.exists(repository.packageStorage.target(id)))
                assertNull(db.mods().record(id))
            }
        } finally {
            ids.forEach { repository.uninstall(it) }
            db.close()
            context.deleteDatabase(name)
        }
    }
}
