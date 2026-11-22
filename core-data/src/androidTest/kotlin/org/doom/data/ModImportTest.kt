package org.doom.data

import android.content.ContentValues
import android.net.Uri
import android.provider.MediaStore
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.doom.database.DoomDatabase
import org.doom.database.InstalledModEntity
import org.doom.database.ModSettingsEntity
import org.doom.database.CapabilityGrantEntity
import org.doom.domain.ModError
import org.doom.domain.ModResult
import org.doom.model.ModStatus
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files
import java.util.UUID
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import java.util.zip.CRC32
import java.security.MessageDigest
import org.json.JSONObject
import org.doom.engine.ArchiveReader

class ModImportTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val id = "community.batteryglow"
    private val archive = "Battery-Glow.doommod"

    @Test fun updateJournalRestoresOldOrKeepsCommittedNewPackage() {
        val root = Files.createTempDirectory(context.cacheDir.toPath(), "package-update-")
        val storage = PackageStorage(root)
        try {
            val oldSha = storage.stage(ByteArrayInputStream("old package".toByteArray()))
            storage.publish("community.updatefixture", oldSha)
            storage.clearJournal("community.updatefixture")
            val newSha = storage.stage(ByteArrayInputStream("new package".toByteArray()))
            storage.replaceForUpdate("community.updatefixture", oldSha, newSha)
            assertEquals(newSha, storage.digest(storage.target("community.updatefixture")))
            assertEquals(true, storage.recoverUpdate("community.updatefixture", oldSha))
            assertEquals(oldSha, storage.digest(storage.target("community.updatefixture")))
            storage.clearJournal("community.updatefixture")

            val stagedAgain = storage.stage(ByteArrayInputStream("new package".toByteArray()))
            storage.replaceForUpdate("community.updatefixture", oldSha, stagedAgain)
            assertEquals(true, storage.recoverUpdate("community.updatefixture", stagedAgain))
            assertEquals(stagedAgain, storage.digest(storage.target("community.updatefixture")))
            storage.clearJournal("community.updatefixture")
        } finally {
            storage.clearAbandonedStage()
            Files.deleteIfExists(storage.target("community.updatefixture"))
            storage.clearJournal("community.updatefixture")
            Files.deleteIfExists(root.resolve("mods"))
            Files.deleteIfExists(root.resolve("import-stage"))
            Files.deleteIfExists(root.resolve("import-journal"))
            Files.deleteIfExists(root)
        }
    }

    @Test fun semanticUpdateComparisonHandlesCoreAndPrereleaseOrdering() {
        assertTrue(isStrictlyNewerVersion("1.10.0", "1.9.9"))
        assertTrue(isStrictlyNewerVersion("2.0.0", "2.0.0-rc.1"))
        assertFalse(isStrictlyNewerVersion("1.0.0", "1.0.0"))
        assertFalse(isStrictlyNewerVersion("1.0.0-beta.2", "1.0.0-beta.10"))
        assertFalse(isStrictlyNewerVersion("1.0.0", "1.1.0"))
    }

    @Test fun verifiedUpdatePreservesSettingsAndGrantsAndStaysDisabled() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, DoomDatabase::class.java).build()
        val repository = RoomModRepository(context, db)
        val coordinator = ModImportCoordinator(context, repository)
        val oldFile = File(context.cacheDir, "update-old-${UUID.randomUUID()}.doommod")
        val newFile = File(context.cacheDir, "update-new-${UUID.randomUUID()}.doommod")
        fun checksum(file: File): String = FileInputStream(file).use { input ->
            ArchiveReader.copyAndHash(input, object : java.io.OutputStream() {
                override fun write(value: Int) = Unit
                override fun write(bytes: ByteArray, offset: Int, length: Int) = Unit
            })
        }
        try {
            context.assets.open(archive).use { input -> FileOutputStream(oldFile).use { output -> input.copyTo(output) } }
            ZipInputStream(FileInputStream(oldFile)).use { source ->
                ZipOutputStream(FileOutputStream(newFile)).use { destination ->
                    while (true) {
                        val entry = source.nextEntry ?: break
                        val bytes = if (entry.name == "doom.json") {
                            JSONObject(source.readBytes().toString(Charsets.UTF_8)).put("version", "99.0.1").toString().toByteArray()
                        } else source.readBytes()
                        val crc = CRC32().apply { update(bytes) }
                        val outputEntry = ZipEntry(entry.name).apply {
                            method = ZipEntry.STORED
                            size = bytes.size.toLong()
                            compressedSize = bytes.size.toLong()
                            this.crc = crc.value
                        }
                        destination.putNextEntry(outputEntry)
                        destination.write(bytes)
                        destination.closeEntry()
                    }
                }
            }
            val originalVersion = FileInputStream(oldFile).use { ArchiveReader.validate(it).version }
            val originalSha = checksum(oldFile)
            val first = coordinator.reviewDownloaded(oldFile, id, originalVersion, originalSha)
            assertEquals(ModResult.Success, coordinator.install(first))
            db.mods().putSettings(ModSettingsEntity(id, "{\"values\":{\"keep\":true}}", 123L))
            db.mods().putGrant(CapabilityGrantEntity(id, "ui.preview", true, 456L))

            val updated = coordinator.reviewDownloaded(newFile, id, "99.0.1", checksum(newFile))
            assertEquals(ModResult.Success, coordinator.update(updated))
            val stored = db.mods().record(id)!!
            assertEquals("99.0.1", stored.mod.version)
            assertEquals(ModStatus.INSTALLED_DISABLED, stored.mod.status)
            assertFalse(stored.mod.desiredEnabled)
            assertEquals("{\"values\":{\"keep\":true}}", stored.settings!!.settingsJson)
            assertEquals(123L, stored.settings!!.updatedAt)
            assertEquals("ui.preview", stored.grants.single().capabilityId)
            assertTrue(stored.grants.single().approved)
            assertEquals(checksum(repository.packageStorage.target(id).toFile()), stored.mod.sha256)
            assertTrue(repository.packageStorage.pendingIds().isEmpty())
        } finally {
            coordinator.discard()
            repository.uninstall(id)
            oldFile.delete(); newFile.delete()
            db.close()
        }
    }

    @Test fun cliArchiveReviewInstallAndUninstallUseRealContentUri() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, DoomDatabase::class.java).build()
        val repository = RoomModRepository(context, db)
        val coordinator = ModImportCoordinator(context, repository)
        val uri = publishDocument()
        try {
            val review = coordinator.review(uri.toString())
            assertEquals(id, review.manifest.id)
            assertTrue(review.compatible)
            assertNull(db.mods().record(id))
            coordinator.discard()
            assertFalse(Files.exists(repository.packageStorage.staged(review.sha256)))
            assertNull(db.mods().record(id))

            val approved = coordinator.review(uri.toString())
            assertEquals(ModResult.Success, coordinator.install(approved))
            val installed = db.mods().record(id)!!
            assertEquals(ModStatus.INSTALLED_DISABLED, installed.mod.status)
            assertEquals(approved.sha256, installed.mod.sha256)
            assertEquals("mods/$id.doommod", installed.mod.packagePath)
            assertFalse(installed.mod.desiredEnabled)
            assertTrue(installed.grants.isEmpty())
            val path = repository.packageStorage.target(id)
            assertEquals(approved.sha256, repository.packageStorage.digest(path))
            assertTrue(repository.packageStorage.pendingIds().isEmpty())

            val duplicate = coordinator.review(uri.toString())
            assertEquals(ModError.DUPLICATE_ID, (coordinator.install(duplicate) as ModResult.Rejected).reason)
            assertEquals(approved.sha256, repository.packageStorage.digest(path))
            assertEquals(ModResult.Success, repository.uninstall(id))
            assertFalse(Files.exists(path))
            assertNull(db.mods().record(id))
        } finally {
            coordinator.discard()
            repository.uninstall(id)
            context.contentResolver.delete(uri, null, null)
            db.close()
        }
    }

    @Test fun startupRollsBackUnpublishedFileAndKeepsCommittedFile() = runBlocking {
        val name = "package-journal-${UUID.randomUUID()}.db"
        fun open() = Room.databaseBuilder(context, DoomDatabase::class.java, name).build()
        val db = open()
        val repository = RoomModRepository(context, db)
        val storage = repository.packageStorage
        try {
            repository.prepareImport()
            val uncommitted = "test.uncommitted"
            val committed = "test.committed"
            val first = context.assets.open(archive).use { storage.stage(it) }
            storage.publish(uncommitted, first)
            val second = context.assets.open(archive).use { storage.stage(it) }
            storage.publish(committed, second)
            db.mods().insert(InstalledModEntity(committed, "Committed", "1.0.0", storage.packagePath(committed),
                sha256 = second))
            db.close()

            val reopened = open()
            try {
                val restarted = RoomModRepository(context, reopened)
                assertEquals(committed, restarted.observeInstalled().first().single().id)
                assertFalse(Files.exists(storage.target(uncommitted)))
                assertEquals(second, storage.digest(storage.target(committed)))
                assertTrue(storage.pendingIds().isEmpty())
                assertEquals(ModResult.Success, restarted.uninstall(committed))
            } finally {
                reopened.close()
            }
        } finally {
            db.close()
            for (id in listOf("test.uncommitted", "test.committed")) {
                Files.deleteIfExists(storage.target(id))
                storage.clearJournal(id)
            }
            context.deleteDatabase(name)
        }
    }

    @Test fun cancellationDuringCopyRemovesTemporaryFile() {
        val root = Files.createTempDirectory(context.cacheDir.toPath(), "cancel-import-")
        val storage = PackageStorage(root)
        try {
            assertThrows(CancellationException::class.java) {
                context.assets.open(archive).use { storage.stage(it) { throw CancellationException() } }
            }
            Files.newDirectoryStream(root.resolve("import-stage")).use { assertFalse(it.iterator().hasNext()) }
        } finally {
            Files.deleteIfExists(root.resolve("import-stage"))
            Files.deleteIfExists(root)
        }
    }

    @Test fun loadsRuntimeBindingsFromTheVerifiedPrivateArchive() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, DoomDatabase::class.java).build()
        val repository = RoomModRepository(context, db)
        val coordinator = ModImportCoordinator(context, repository)
        val uri = publishDocument("Runtime-Glow.doommod")
        val runtimeId = "community.runtimeglow"
        try {
            val reviewed = coordinator.review(uri.toString())
            assertEquals(ModResult.Success, coordinator.install(reviewed))
            val definition = repository.loadRuntimeDefinition(runtimeId)
            assertEquals(setOf("battery.charging.started", "battery.charging.stopped", "mod.manual.trigger"),
                definition.subscriptions.keys.map { it.wireName }.toSet())
            Files.write(repository.packageStorage.target(runtimeId), byteArrayOf(1, 2, 3))
            try {
                repository.loadRuntimeDefinition(runtimeId)
                fail("Expected digest mismatch")
            } catch (error: IllegalArgumentException) {
                assertEquals("Installed package digest changed.", error.message)
            }
        } finally {
            coordinator.discard()
            repository.uninstall(runtimeId)
            context.contentResolver.delete(uri, null, null)
            db.close()
        }
    }

    private fun publishDocument(asset: String = archive): Uri {
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, "${UUID.randomUUID()}.doommod")
            put(MediaStore.Downloads.MIME_TYPE, "application/octet-stream")
        }
        val resolver = context.contentResolver
        val uri = requireNotNull(resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values))
        context.assets.open(asset).use { source ->
            requireNotNull(resolver.openOutputStream(uri)).use { source.copyTo(it) }
        }
        return uri
    }
}
