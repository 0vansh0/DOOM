package org.doom.engine

import org.doom.model.PackageReview
import org.doom.engine.runtime.ValidatedPackage
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.zip.CRC32
import java.util.zip.Inflater

object ArchiveReader {
    const val compressedLimit = 10 * 1024 * 1024
    private const val expandedLimit = 25 * 1024 * 1024
    private val assetPath = Regex("assets/[a-z0-9][a-z0-9_-]{0,39}\\.png")

    fun copyAndHash(input: InputStream, output: OutputStream, onChunk: () -> Unit = {}): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(8192)
        var total = 0
        while (true) {
            onChunk()
            val count = input.read(buffer)
            if (count == -1) break
            require(count > 0) { "Package stream stopped unexpectedly." }
            total += count
            require(total <= compressedLimit) { "Package exceeds 10 MiB." }
            digest.update(buffer, 0, count)
            output.write(buffer, 0, count)
        }
        require(total > 0) { "The selected document is empty." }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun validate(input: InputStream, imageBounds: (ByteArray, Int, Int) -> Unit = { _, _, _ -> }): PackageReview {
        return read(input, imageBounds = imageBounds).review
    }

    fun read(input: InputStream, expectedSha256: String? = null,
        imageBounds: (ByteArray, Int, Int) -> Unit = { _, _, _ -> },
        cached: (String) -> ValidatedPackage? = { null }): ValidatedPackage {
        val archive = ByteArrayOutputStream().use {
            val digest = copyAndHash(input, it)
            require(expectedSha256 == null || expectedSha256 == digest) { "Installed package digest changed." }
            cached(digest)?.let { hit -> return hit }
            it.toByteArray()
        }
        require(archive.size in 22..compressedLimit) { "Invalid ZIP size." }
        val entries = directory(archive)
        val manifests = mutableMapOf<String, ByteArray>()
        for (entry in entries) {
            val contents = if (entry.method == 0) {
                require(entry.compressed == entry.expanded) { "Invalid stored entry size." }
                archive.copyOfRange(entry.data, entry.data + entry.compressed)
            } else inflateExact(archive, entry.data, entry.compressed, entry.expanded, raw = true)
            require(crc(contents) == entry.checksum) { "Entry checksum does not match." }
            if (entry.name.startsWith("assets/")) {
                val (width, height) = PngBounds.validate(contents)
                imageBounds(contents, width, height)
            } else manifests[entry.name] = contents
        }
        return PackageFormat.validate(
            manifests["doom.json"] ?: error("Missing doom.json."),
            manifests["mod.json"] ?: error("Missing mod.json."),
            entries.map { it.name }.toSet()
        )
    }

    private data class Entry(val name: String, val method: Int, val compressed: Int, val expanded: Int,
        val checksum: Long, val offset: Int, val data: Int)

    private fun directory(bytes: ByteArray): List<Entry> {
        val zip = ZipBytes(bytes)
        val end = bytes.size - 22
        require(zip.u32(end) == 0x06054b50L && zip.u16(end + 20) == 0) { "Expected a canonical ZIP." }
        val count = zip.u16(end + 10)
        require(count in 2..50 && zip.u16(end + 8) == count && zip.u32(end + 4) == 0L) { "Invalid ZIP entry count or disk." }
        val start = zip.bounded32(end + 16, end)
        require(zip.u32(end + 12) == (end - start).toLong()) { "Invalid ZIP directory size." }
        val names = hashSetOf<String>()
        var position = start
        var total = 0
        val entries = List(count) {
            require(position <= end - 46 && zip.u32(position) == 0x02014b50L) { "Invalid central header." }
            val flags = zip.u16(position + 8)
            val method = zip.u16(position + 10)
            require(flags in setOf(0, 2048) && method in setOf(0, 8)) { "Unsupported ZIP compression or flags." }
            val length = zip.u16(position + 28)
            require(length in 1..51 && position + 46 + length <= end && zip.u32(position + 30) == 0L &&
                zip.u16(position + 34) == 0) { "ZIP extensions are not supported." }
            val name = String(bytes, position + 46, length, Charsets.ISO_8859_1)
            val limit = when (name) {
                "doom.json" -> 64 * 1024
                "mod.json" -> 256 * 1024
                else -> {
                    require(assetPath.matches(name)) { "Unsupported archive entry path." }
                    5 * 1024 * 1024
                }
            }
            require(names.add(name)) { "Duplicate archive entry." }
            val mode = zip.u16(position + 40) and 0xf000
            require(mode in setOf(0, 0x8000) && (zip.u16(position + 38) and 0x18) == 0) {
                "Only regular ZIP files are allowed."
            }
            val compressed = zip.bounded32(position + 20, compressedLimit)
            val expanded = zip.bounded32(position + 24, limit)
            total += expanded
            require(compressed > 0 && expanded > 0 && total <= expandedLimit) { "Archive exceeds expansion limits." }
            val local = zip.bounded32(position + 42, start)
            val data = local + 30 + length
            require(data <= start && compressed <= start - data && zip.u32(local) == 0x04034b50L &&
                zip.u16(local + 6) == flags && zip.u16(local + 8) == method &&
                zip.u16(local + 26) == length && zip.u16(local + 28) == 0 &&
                zip.u32(local + 14) == zip.u32(position + 16) &&
                zip.u32(local + 18) == compressed.toLong() && zip.u32(local + 22) == expanded.toLong() &&
                String(bytes, local + 30, length, Charsets.ISO_8859_1) == name) { "ZIP headers disagree." }
            Entry(name, method, compressed, expanded, zip.u32(position + 16), local, data)
                .also { position += 46 + length }
        }
        require(position == end) { "Unexpected ZIP directory data." }
        var next = 0
        entries.sortedBy { it.offset }.forEach {
            require(it.offset == next) { "ZIP data overlaps or contains an unlisted entry." }
            next = it.data + it.compressed
        }
        require(next == start) { "Unexpected bytes before ZIP directory." }
        return entries
    }

    private class ZipBytes(private val bytes: ByteArray) {
        fun u16(i: Int) = (bytes[i].toInt() and 255) or ((bytes[i + 1].toInt() and 255) shl 8)
        fun u32(i: Int) = u16(i).toLong() or (u16(i + 2).toLong() shl 16)
        fun bounded32(i: Int, limit: Int): Int = u32(i).also {
            require(it <= limit.toLong()) { "ZIP field exceeds its limit." }
        }.toInt()
    }
}

internal fun crc(bytes: ByteArray, offset: Int = 0, size: Int = bytes.size) =
    CRC32().apply { update(bytes, offset, size) }.value

internal fun inflateExact(bytes: ByteArray, offset: Int, length: Int, size: Int, raw: Boolean): ByteArray {
    val inflater = Inflater(raw)
    try {
        inflater.setInput(bytes, offset, length)
        val result = ByteArray(size + 1)
        var written = 0
        while (!inflater.finished() && written < result.size) {
            val count = inflater.inflate(result, written, result.size - written)
            require(count > 0 || inflater.finished()) { "Truncated or invalid compressed data." }
            written += count
        }
        require(inflater.finished() && inflater.remaining == 0 && written == size) { "Invalid expanded size." }
        return result.copyOf(size)
    } finally {
        inflater.end()
    }
}
