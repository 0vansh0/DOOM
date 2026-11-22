package org.doom.engine

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

private val pngSignature = byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10)

internal object PngBounds {
    fun validate(bytes: ByteArray): Pair<Int, Int> {
        require(bytes.size >= 45 && bytes.copyOfRange(0, 8).contentEquals(pngSignature)) { "Expected a PNG asset." }
        val data = ByteBuffer.wrap(bytes)
        data.position(8)
        var width = 0
        var height = 0
        var stride = 0
        var ended = false
        val pixels = ByteArrayOutputStream()
        while (data.remaining() >= 12 && !ended) {
            val length = data.int
            require(length in 0..(data.remaining() - 8)) { "Invalid PNG chunk size." }
            val typeOffset = data.position()
            val type = String(bytes, typeOffset, 4, Charsets.US_ASCII)
            val offset = typeOffset + 4
            data.position(offset + length)
            require(crc(bytes, typeOffset, length + 4) == (data.int.toLong() and 0xffffffffL)) { "Invalid PNG checksum." }
            when (type) {
                "IHDR" -> {
                    require(typeOffset == 12 && length == 13) { "Invalid PNG header." }
                    width = data.getInt(offset)
                    height = data.getInt(offset + 4)
                    val color = bytes[offset + 9].toInt()
                    require(width in 1..2048 && height in 1..2048 && width * height <= 1_048_576 &&
                        bytes[offset + 8].toInt() == 8 && color in setOf(2, 6) &&
                        (offset + 10..offset + 12).all { bytes[it].toInt() == 0 }) { "Unsupported PNG format or dimensions." }
                    stride = 1 + width * if (color == 2) 3 else 4
                }
                "IDAT" -> {
                    require(stride > 0 && length > 0) { "Invalid PNG image data." }
                    pixels.write(bytes, offset, length)
                }
                "IEND" -> {
                    require(length == 0 && pixels.size() > 0 && !data.hasRemaining()) { "Invalid PNG end." }
                    ended = true
                }
                else -> error("Unsupported PNG chunk.")
            }
        }
        require(ended) { "Incomplete PNG asset." }
        val compressed = pixels.toByteArray()
        val decoded = inflateExact(compressed, 0, compressed.size, stride * height, raw = false)
        require((0 until height).all { decoded[it * stride].toInt() in 0..4 }) { "Invalid PNG filter." }
        return width to height
    }
}
