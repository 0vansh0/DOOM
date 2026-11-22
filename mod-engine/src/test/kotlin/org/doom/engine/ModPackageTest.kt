package org.doom.engine

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ModPackageTest {
    @get:Rule val temporary = TemporaryFolder()

    private val mod = """{"id":"independent.sample","title":"Sample","version":"1.0.0",
        "description":"Demo mod","minAndroidApi":31,"capabilities":["overlay"],
        "trigger":"battery.charging","effect":"overlay.glow",
        "settings":{"color":"#AABBCC","intensity":45}}"""

    @Test fun parsesAnIndependentMod() {
        val result = ModPackage.validate(archive())
        assertEquals("independent.sample", result.id)
        assertEquals(45, result.appearance.intensity)
    }

    @Test fun rejectsUnknownEffect() {
        rejected(archive(json = mod.replace("overlay.glow", "other.effect")))
    }

    @Test fun rejectsDuplicateJsonFields() {
        rejected(archive(json = mod.replace("\"intensity\":45", "\"intensity\":45,\"intensity\":50")))
    }

    @Test fun rejectsTraversalAndExecutableEntries() {
        rejected(archive(extra = "../escape.png" to byteArrayOf(1)))
        rejected(archive(extra = "assets/code.dex" to byteArrayOf(1)))
    }

    @Test fun rejectsOversizedDeflatedEntry() {
        rejected(archive(extra = "assets/huge.png" to ByteArray(1_000_000)))
    }

    @Test fun rejectsDuplicateZipEntries() {
        val file = archive(extra = "bad!.json" to byteArrayOf(1))
        // Use an equal-length replacement in both directory and local header.
        val bytes = file.readBytes()
        val from = "bad!.json".toByteArray()
        val to = "doom.json".toByteArray()
        for (offset in 0..bytes.size - from.size) {
            if (from.indices.all { bytes[offset + it] == from[it] }) to.copyInto(bytes, offset)
        }
        file.writeBytes(bytes)
        rejected(file)
    }

    private fun rejected(file: File) {
        assertThrows(Exception::class.java) { ModPackage.validate(file) }
    }

    private fun archive(json: String = mod, extra: Pair<String, ByteArray>? = null): File =
        temporary.newFile().also { file ->
            ZipOutputStream(file.outputStream()).use { zip ->
                val entries = listOf("doom.json" to """{"schemaVersion":1}""".toByteArray(),
                    "mod.json" to json.toByteArray()) + listOfNotNull(extra)
                for ((name, data) in entries) {
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(data)
                    zip.closeEntry()
                }
            }
        }
}
