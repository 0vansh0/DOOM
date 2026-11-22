package org.doom.engine

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder.LITTLE_ENDIAN
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import com.google.gson.JsonParser
import org.doom.engine.runtime.WorkflowExecutionMode

class ArchiveReaderTest {
    private val example = resource("Battery-Glow.doommod")
    private val manifest = resource("doom.json").decodeToString()
    private val definition = resource("mod.json").decodeToString()
    private val icon = resource("assets/glow.png")

    @Test fun readsCliPackageAndInspectsPng() {
        val review = ArchiveReader.validate(example.inputStream()) { _, w, h ->
            assertEquals(32, w)
            assertEquals(32, h)
        }
        assertEquals("community.batteryglow", review.id)
        assertEquals("2.0.0", review.version)
        assertEquals(10..60, review.intensityRange)
    }

    @Test fun importsDefinitionV4ThroughArchiveReader() {
        val fixtures = JsonParser.parseString(resource("v4.json").decodeToString()).asJsonArray
        val fixture = fixtures.first { it.asJsonObject["name"].asString == "v4-valid-composed-multi-action" }.asJsonObject
        val archive = zip(listOf("doom.json" to fixture["manifest"].toString().toByteArray(),
            "mod.json" to fixture["definition"].toString().toByteArray()))
        val parsed = ArchiveReader.read(archive.inputStream())
        assertEquals("local.powercard", parsed.review.id)
        assertEquals(1, parsed.v4?.triggers?.size)
    }

    @Test fun importsAllCliPackagedCommunityExamples() {
        val examples = listOf(
            "releases/battery-glow.doommod",
            "releases/battery-guardian.doommod",
            "releases/edge-aura.doommod",
            "releases/workflow-showcase.doommod",
            "releases/dynamic-settings-showcase.doommod",
            "releases/capability-showcase.doommod"
        )
        for (name in examples) {
            val review = ArchiveReader.validate(resource(name).inputStream())
            assertTrue("$name should have a valid package identity", review.id.startsWith("community."))
        }
    }

    @Test fun importsVisualBuilderSdkBridgeExport() {
        val parsed = ArchiveReader.read(resource("fixtures/visual-builder-export-13b5.doommod").inputStream())
        assertEquals("local.milestonebuildertest", parsed.review.id)
        val definition = requireNotNull(parsed.v4)
        assertEquals(1, definition.triggers.size)
        val trigger = definition.triggers.single()
        assertEquals("battery.charging.started", trigger.event)
        assertEquals(listOf("ring", "message"), trigger.actions.map { it.id })
        assertEquals(WorkflowExecutionMode.SEQUENTIAL, trigger.workflow?.executionMode)
        assertTrue(definition.settings.containsKey("color"))
    }

    @Test fun importsEveryGeneratedVisualBuilderTemplatePackage() {
        val examples = listOf("battery-glow", "low-battery-alert", "charging-companion", "custom-floating-card")
        for (name in examples) {
            val parsed = ArchiveReader.read(resource("builder-$name.doommod").inputStream())
            assertTrue("$name should be a Definition 4 package", parsed.v4?.triggers?.isNotEmpty() == true)
        }
    }

    @Test fun rejectsMissingDocumentsAndMalformedJson() {
        reject(zip(listOf("mod.json" to definition.toByteArray(), "assets/glow.png" to icon)))
        reject(pkg(m = "{broken"))
        reject(pkg(m = manifest.replace("\"schemaVersion\": 2", "\"schemaVersion\": 2, \"schemaVersion\": 2")))
        reject(pkg(m = manifest.replace("\"schemaVersion\": 2", "\"schemaVersion\": 2.0")))
    }

    @Test fun rejectsUnknownVersionsAndRegistryNames() {
        reject(pkg(m = manifest.replace("\"schemaVersion\": 2", "\"schemaVersion\": 3")))
        reject(pkg(d = definition.replace("\"definitionVersion\": 1", "\"definitionVersion\": 3")))
        reject(pkg(m = manifest.replace("battery.read", "network.read")))
        reject(pkg(m = manifest.replace("battery.read", "clock.read")))
        reject(pkg(d = definition.replace("battery.charging.started", "battery.level.changed")))
        reject(pkg(d = definition.replace("overlay.show", "overlay.hide")))
        reject(pkg(d = definition.replace("battery-ring", "unknown-template")))
        reject(pkg(d = definition.replace("\"effect\": \"charging-ring\"", "\"effect\": \"wrong-id\"")))
    }

    @Test fun rejectsInvalidSettingsAndMissingIcon() {
        reject(pkg(d = definition.replace("\"default\": 45", "\"default\": \"45\"")))
        reject(pkg(d = definition.replace("\"min\": 10", "\"min\": 55")))
        reject(pkg(d = definition.replace("\"max\": 60", "\"max\": 65")))
        reject(pkg(m = manifest.replace("glow.png", "missing.png")))
    }

    @Test fun rejectsTraversalAmbiguitiesUnknownEntriesAndDuplicateNames() {
        for (name in listOf("../doom.json", "/doom.json", "assets\\glow.png", "assets/../glow.png",
            "assets/Glow.png", "launcher.dex")) {
            reject(pkg(extra = name to byteArrayOf(1)))
        }
        val duplicate = pkg(extra = "copy.json" to byteArrayOf(1))
        replaceName(duplicate, "copy.json", "doom.json")
        reject(duplicate)
    }

    @Test fun rejectsSymlinkAndInconsistentLocalName() {
        val archive = pkg()
        val zip = ByteBuffer.wrap(archive).order(LITTLE_ENDIAN)
        val central = zip.getInt(archive.size - 6)
        zip.putShort(central + 40, 0xa000.toShort())
        reject(archive)
        val changed = pkg()
        changed[30] = 'x'.code.toByte()
        reject(changed)
    }

    @Test fun rejectsOversizedArchiveAndAnExpansionLargerThanItsHeader() {
        reject(ByteArray(ArchiveReader.compressedLimit + 1))
        val inflated = ByteArray(600_000)
        val compressed = java.util.zip.Deflater(6, true).let { deflater ->
            try {
                deflater.setInput(inflated)
                deflater.finish()
                val bytes = ByteArray(inflated.size)
                val size = deflater.deflate(bytes)
                assertTrue(deflater.finished())
                bytes.copyOf(size)
            } finally { deflater.end() }
        }
        val falseSize = 40
        val archive = pkg(d = "x".repeat(falseSize))
        val zip = ByteBuffer.wrap(archive).order(LITTLE_ENDIAN)
        val previousCentral = zip.getInt(archive.size - 6)
        // Make a compact synthetic test with a bounded declared size and a larger actual stream.
        val docLocal = 30 + "doom.json".length + manifest.toByteArray().size
        val docData = docLocal + 30 + "mod.json".length
        val shift = compressed.size - falseSize
        val forged = archive.copyOfRange(0, docData) + compressed + archive.copyOfRange(docData + falseSize, archive.size)
        val next = ByteBuffer.wrap(forged).order(LITTLE_ENDIAN)
        val newCentral = previousCentral + shift
        val second = newCentral + 46 + "doom.json".length
        val third = second + 46 + "mod.json".length
        next.putShort(docLocal + 8, 8)
        next.putInt(docLocal + 18, compressed.size)
        next.putShort(second + 10, 8)
        next.putInt(second + 20, compressed.size)
        next.putInt(third + 42, next.getInt(third + 42) + shift)
        next.putInt(forged.size - 6, newCentral)
        reject(forged)
    }

    @Test fun rejectsBadImageAndPropagatesOutOfSpace() {
        reject(zip(listOf("doom.json" to manifest.toByteArray(), "mod.json" to definition.toByteArray(),
            "assets/glow.png" to byteArrayOf(1, 2, 3))))
        val broken = object : OutputStream() {
            override fun write(value: Int) { throw IOException("No space left on device") }
        }
        val failure = assertThrows(IOException::class.java) { ArchiveReader.copyAndHash(example.inputStream(), broken) }
        assertEquals("No space left on device", failure.message)
    }

    private fun resource(path: String) = checkNotNull(javaClass.getResourceAsStream("/$path")).use { it.readBytes() }

    private fun reject(bytes: ByteArray) {
        assertThrows(Exception::class.java) { ArchiveReader.validate(bytes.inputStream()) }
    }

    private fun pkg(m: String = manifest, d: String = definition, extra: Pair<String, ByteArray>? = null) =
        zip(listOf("doom.json" to m.toByteArray(), "mod.json" to d.toByteArray(), "assets/glow.png" to icon)
            + listOfNotNull(extra))

    private fun zip(entries: List<Pair<String, ByteArray>>): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            for ((name, data) in entries) {
                val entry = ZipEntry(name).apply {
                    method = ZipEntry.STORED
                    size = data.size.toLong()
                    crc = crc(data)
                }
                zip.putNextEntry(entry)
                zip.write(data)
                zip.closeEntry()
            }
        }
        return output.toByteArray()
    }

    private fun replaceName(bytes: ByteArray, before: String, after: String) {
        val source = before.toByteArray()
        val replacement = after.toByteArray()
        for (i in 0..bytes.size - source.size) {
            if (source.indices.all { bytes[i + it] == source[it] }) replacement.copyInto(bytes, i)
        }
    }
}
