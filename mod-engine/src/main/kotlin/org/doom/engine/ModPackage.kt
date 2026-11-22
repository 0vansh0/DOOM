package org.doom.engine

import com.google.gson.Strictness
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import org.doom.model.GlowAppearance
import org.doom.model.ModManifest
import java.io.File
import java.io.InputStream
import java.nio.charset.CodingErrorAction
import java.util.zip.CRC32
import java.util.zip.ZipFile

object ModPackage {
    private const val archiveLimit = 1_048_576
    private const val manifestLimit = 16_384
    private const val assetLimit = 65_536
    private const val totalLimit = 262_144
    private val idPattern = Regex("[a-z][a-z0-9]*(\\.[a-z][a-z0-9]*)+")
    private val versionPattern = Regex("(0|[1-9][0-9]{0,5})(\\.(0|[1-9][0-9]{0,5})){2}")
    private val assetPattern = Regex("assets/[a-z0-9][a-z0-9_-]{0,39}\\.png")

    fun stage(input: InputStream, target: File): ModManifest {
        target.outputStream().use { output ->
            val buffer = ByteArray(8192)
            var total = 0
            while (true) {
                val read = input.read(buffer)
                if (read == -1) break
                total += read
                require(total <= archiveLimit) { "Package exceeds 1 MiB." }
                output.write(buffer, 0, read)
            }
        }
        return validate(target)
    }

    fun validate(file: File): ModManifest {
        require(file.length() in 1..archiveLimit) { "Invalid package size." }
        val manifests = mutableMapOf<String, ByteArray>()
        ZipFile(file).use { zip ->
            require(zip.size() in 2..10) { "Expected 2–10 package entries." }
            val names = mutableSetOf<String>()
            var total = 0
            zip.entries().asSequence().forEach { entry ->
                val name = entry.name
                val manifest = name == "doom.json" || name == "mod.json"
                require(manifest || assetPattern.matches(name)) { "Unsupported package entry." }
                require(names.add(name)) { "Duplicate package entry." }
                val limit = if (manifest) manifestLimit else assetLimit
                require(entry.size in 1..limit.toLong() && entry.compressedSize in 1..archiveLimit.toLong()) {
                    "Invalid entry size."
                }
                val bytes = zip.getInputStream(entry).use { input ->
                    val buffer = ByteArray(limit + 1)
                    var size = 0
                    while (size < buffer.size) {
                        val count = input.read(buffer, size, buffer.size - size)
                        if (count == -1) break
                        size += count
                    }
                    require(size <= limit && size.toLong() == entry.size) { "Invalid expanded entry size." }
                    buffer.copyOf(size)
                }
                total += bytes.size
                require(total <= totalLimit) { "Package expands beyond 256 KiB." }
                require(CRC32().apply { update(bytes) }.value == entry.crc) { "Invalid entry checksum." }
                if (manifest) manifests[name] = bytes
                else require(bytes.take(8) == pngSignature) { "Expected an approved PNG asset." }
            }
        }
        val envelope = parse(manifests["doom.json"] ?: error("Missing doom.json."))
        require(envelope.keys == setOf("schemaVersion") && envelope["schemaVersion"] == 1) {
            "Unsupported package schema."
        }
        val mod = parse(manifests["mod.json"] ?: error("Missing mod.json."))
        require(mod.keys == setOf("id", "title", "version", "description", "minAndroidApi",
            "capabilities", "trigger", "effect", "settings")) { "Unexpected mod fields." }
        val id = mod.string("id", 80)
        val version = mod.string("version", 20)
        require(idPattern.matches(id) && versionPattern.matches(version)) { "Invalid mod ID or version." }
        require(mod["capabilities"] == listOf("overlay") &&
            mod["trigger"] == "battery.charging" && mod["effect"] == "overlay.glow") {
            "Unsupported capability, trigger or effect."
        }
        val settings = mod["settings"] as? Map<*, *> ?: error("Missing appearance defaults.")
        require(settings.keys == setOf("color", "intensity")) { "Unknown appearance setting." }
        return ModManifest(id, mod.string("title", 60), version, mod.string("description", 300),
            mod.integer("minAndroidApi", 31..99), GlowAppearance(
                settings.string("color", 7), settings.integer("intensity", GlowAppearance.intensities)
            ))
    }

    private val pngSignature = listOf<Byte>(-119, 80, 78, 71, 13, 10, 26, 10)

    private fun parse(bytes: ByteArray): Map<String, Any> {
        val decoder = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
        val text = decoder.decode(java.nio.ByteBuffer.wrap(bytes)).toString()
        return JsonReader(text.reader()).use { reader ->
            reader.strictness = Strictness.STRICT
            val result = readObject(reader, 0)
            require(reader.peek() == JsonToken.END_DOCUMENT) { "Unexpected content after manifest." }
            result
        }
    }

    private fun readObject(reader: JsonReader, depth: Int): Map<String, Any> {
        require(depth <= 1) { "Unexpected nested manifest data." }
        val fields = mutableMapOf<String, Any>()
        reader.beginObject()
        while (reader.hasNext()) {
            val key = reader.nextName()
            require(key !in fields) { "Duplicate manifest field." }
            fields[key] = when (reader.peek()) {
                JsonToken.BEGIN_OBJECT -> readObject(reader, depth + 1)
                JsonToken.STRING -> reader.nextString()
                JsonToken.NUMBER -> reader.nextString().toIntOrNull() ?: error("Expected an integer.")
                JsonToken.BEGIN_ARRAY -> {
                    reader.beginArray()
                    val values = mutableListOf<String>()
                    while (reader.hasNext()) {
                        require(values.isEmpty() && reader.peek() == JsonToken.STRING) { "Invalid capability list." }
                        values.add(reader.nextString())
                    }
                    reader.endArray()
                    values
                }
                else -> error("Unsupported manifest value.")
            }
        }
        reader.endObject()
        return fields
    }

    private fun Map<*, *>.string(key: String, limit: Int): String =
        (get(key) as? String)?.takeIf {
            it.isNotBlank() && it.length <= limit && it.none(Char::isISOControl)
        } ?: error("Invalid $key.")

    private fun Map<*, *>.integer(key: String, range: IntRange): Int =
        (get(key) as? Int)?.takeIf { it in range } ?: error("Invalid $key.")
}
