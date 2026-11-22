package org.doom.data

import org.doom.engine.ArchiveReader
import java.io.InputStream
import java.io.OutputStream
import java.nio.channels.Channels
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardOpenOption.*

internal class PackageStorage(private val root: Path) {
    private val discardOutput = object : OutputStream() {
        override fun write(value: Int) = Unit
        override fun write(bytes: ByteArray, offset: Int, length: Int) = Unit
    }
    private val idRule = Regex("[a-z][a-z0-9]*(\\.[a-z][a-z0-9]*)+")
    private val digestRule = Regex("[0-9a-f]{64}")
    private val stage get() = directory("import-stage")
    private val journals get() = directory("import-journal")

    fun packagePath(id: String) = "mods/${verifiedId(id)}.doommod"
    fun target(id: String): Path = directory("mods").resolve("${verifiedId(id)}.doommod")
    fun staged(sha: String, bundled: Boolean = false): Path {
        require(digestRule.matches(sha))
        return stage.resolve(if (bundled) "bundled-$sha.doommod" else "$sha.doommod")
    }

    fun stage(input: InputStream, bundled: Boolean = false, onChunk: () -> Unit = {}): String {
        val scratch = stage.resolve(if (bundled) "bundled.tmp" else "selection.tmp")
        try {
            val sha = create(scratch) { ArchiveReader.copyAndHash(input, it, onChunk) }
            val destination = staged(sha, bundled)
            check(!Files.exists(destination, NOFOLLOW_LINKS)) { "An unfinished review occupies staging." }
            Files.move(scratch, destination, ATOMIC_MOVE)
            return sha
        } finally {
            Files.deleteIfExists(scratch)
        }
    }

    fun open(path: Path): InputStream {
        check(Files.isRegularFile(path, NOFOLLOW_LINKS)) { "Package file is unavailable." }
        return Files.newInputStream(path, READ, NOFOLLOW_LINKS)
    }

    fun digest(path: Path): String = open(path).use { ArchiveReader.copyAndHash(it, discardOutput) }

    fun discard(sha: String, bundled: Boolean = false) { Files.deleteIfExists(staged(sha, bundled)) }

    fun publish(id: String, sha: String, bundled: Boolean = false) {
        val destination = target(id)
        check(!Files.exists(destination, NOFOLLOW_LINKS)) { "Package storage is occupied." }
        val source = staged(sha, bundled)
        check(digest(source) == sha) { "The reviewed package changed." }
        create(marker(id)) { it.write(sha.toByteArray(Charsets.US_ASCII)) }
        Files.move(source, destination, ATOMIC_MOVE)
    }

    /** Journaled replacement used only for explicit marketplace updates. */
    fun replaceForUpdate(id: String, oldSha: String, newSha: String) {
        require(digestRule.matches(oldSha) && digestRule.matches(newSha) && oldSha != newSha)
        val destination = target(id)
        val backup = previous(id)
        check(digest(destination) == oldSha) { "The installed package changed before update." }
        val source = staged(newSha)
        check(digest(source) == newSha) { "The reviewed update package changed." }
        check(!Files.exists(backup, NOFOLLOW_LINKS)) { "An unresolved package update backup already exists." }
        create(marker(id)) { it.write("UPDATE\n$oldSha\n$newSha".toByteArray(Charsets.US_ASCII)) }
        try {
            Files.move(destination, backup, ATOMIC_MOVE)
            Files.move(source, destination, ATOMIC_MOVE)
        } catch (failure: Exception) {
            if (Files.exists(backup, NOFOLLOW_LINKS) && !Files.exists(destination, NOFOLLOW_LINKS)) {
                runCatching { Files.move(backup, destination, ATOMIC_MOVE) }
            }
            throw failure
        }
    }

    /** null means an ordinary install journal; false preserves uncertain update evidence. */
    fun recoverUpdate(id: String, databaseSha: String?): Boolean? {
        val journal = marker(id)
        if (!Files.exists(journal, NOFOLLOW_LINKS)) return null
        val lines = runCatching { Files.newInputStream(journal, READ, NOFOLLOW_LINKS).bufferedReader(Charsets.US_ASCII).use { it.readText() }.split('\n') }.getOrNull() ?: return false
        if (lines.firstOrNull() != "UPDATE") return null
        val oldSha = lines.getOrNull(1)?.takeIf(digestRule::matches) ?: return false
        val newSha = lines.getOrNull(2)?.takeIf(digestRule::matches) ?: return false
        val destination = target(id)
        val backup = previous(id)
        val targetSha = runCatching { digest(destination) }.getOrNull()
        val backupSha = runCatching { digest(backup) }.getOrNull()
        return when (databaseSha) {
            oldSha -> when {
                targetSha == oldSha -> true
                backupSha == oldSha && (targetSha == null || targetSha == newSha) -> {
                    if (targetSha != null) Files.delete(destination)
                    Files.move(backup, destination, ATOMIC_MOVE)
                    true
                }
                else -> false
            }
            newSha -> targetSha == newSha
            else -> false
        }
    }

    fun clearJournal(id: String) { Files.deleteIfExists(previous(id)); Files.deleteIfExists(marker(id)) }

    fun pendingIds(): List<String> = Files.newDirectoryStream(journals, "*.pending").use { paths ->
        paths.map { verifiedId(it.fileName.toString().removeSuffix(".pending")) }
    }

    fun clearAbandonedStage() {
        Files.newDirectoryStream(stage).use { paths -> paths.forEach { Files.deleteIfExists(it) } }
    }

    private fun marker(id: String): Path = journals.resolve("${verifiedId(id)}.pending")
    private fun previous(id: String): Path = directory("mods").resolve(".${verifiedId(id)}.previous")

    private fun verifiedId(id: String): String {
        require(id.length <= 80 && idRule.matches(id)) { "Invalid package ID." }
        return id
    }

    private fun directory(name: String): Path = root.resolve(name).also {
        if (!Files.exists(it, NOFOLLOW_LINKS)) {
            try { Files.createDirectory(it) } catch (_: java.nio.file.FileAlreadyExistsException) {
                // Another read can initialize the same private directory.
            }
        }
        check(Files.isDirectory(it, NOFOLLOW_LINKS)) { "Package directory is unavailable." }
    }

    private fun <T> create(path: Path, write: (OutputStream) -> T): T =
        FileChannel.open(path, CREATE_NEW, WRITE, NOFOLLOW_LINKS).use { channel ->
            write(Channels.newOutputStream(channel)).also { channel.force(true) }
        }
}
