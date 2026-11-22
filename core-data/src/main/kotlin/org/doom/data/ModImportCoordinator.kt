package org.doom.data

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.doom.domain.ModResult
import org.doom.model.PackageReview
import org.doom.engine.ArchiveReader
import java.io.File
import java.io.FileInputStream
import javax.inject.Inject
import javax.inject.Singleton

class ImportCandidate internal constructor(
    val manifest: PackageReview,
    val sha256: String,
    val compatible: Boolean
)

@Singleton
class ModImportCoordinator @Inject internal constructor(
    @ApplicationContext private val context: Context,
    private val repository: RoomModRepository
) {
    private val lock = Mutex()
    private var pending: ImportCandidate? = null

    suspend fun review(uri: String): ImportCandidate = withContext(Dispatchers.IO) {
        lock.withLock {
            repository.prepareImport()
            discardPending()
            val document = Uri.parse(uri)
            require(document.scheme == "content") { "Choose a document with Android's file picker." }
            val storage = repository.packageStorage
            val scope = this
            val sha = requireNotNull(context.contentResolver.openInputStream(document)) { "Could not open document." }
                .use { storage.stage(it) { scope.coroutineContext.ensureActive() } }
            try {
                val manifest = storage.open(storage.staged(sha)).use { ArchiveReader.validate(it, ::androidPngBounds) }
                coroutineContext.ensureActive()
                ImportCandidate(manifest, sha, Build.VERSION.SDK_INT >= manifest.minAndroidApi).also { pending = it }
            } finally {
                if (pending?.sha256 != sha) storage.discard(sha)
            }
        }
    }

    /** Marketplace bytes pass through the identical app-private staging and archive validation path. */
    suspend fun reviewDownloaded(file: File, expectedModId: String, expectedVersion: String, expectedSha256: String): ImportCandidate = withContext(Dispatchers.IO) {
        lock.withLock {
            repository.prepareImport()
            discardPending()
            val storage = repository.packageStorage
            var sha: String? = null
            try {
                val stagedSha = FileInputStream(file).use { storage.stage(it) }
                sha = stagedSha
                require(stagedSha == expectedSha256) { "Downloaded package checksum changed before validation." }
                val manifest = storage.open(storage.staged(stagedSha)).use { ArchiveReader.validate(it, ::androidPngBounds) }
                require(manifest.id == expectedModId && manifest.version == expectedVersion) { "Package identity does not match the published version." }
                ImportCandidate(manifest, stagedSha, Build.VERSION.SDK_INT >= manifest.minAndroidApi).also { pending = it }
            } finally {
                file.delete()
                sha?.takeIf { pending?.sha256 != it }?.let(storage::discard)
            }
        }
    }

    suspend fun install(candidate: ImportCandidate): ModResult = withContext(Dispatchers.IO) {
        lock.withLock {
            require(pending === candidate) { "Choose the document again to start a new review." }
            try {
                repository.installReviewed(candidate.manifest, candidate.sha256)
            } finally {
                discardPending()
            }
        }
    }

    suspend fun update(candidate: ImportCandidate): ModResult = withContext(Dispatchers.IO) {
        lock.withLock {
            require(pending === candidate) { "Choose the published package again to start a new update review." }
            try { repository.updateReviewed(candidate.manifest, candidate.sha256) }
            finally { discardPending() }
        }
    }

    suspend fun discard() = withContext(Dispatchers.IO) { lock.withLock { discardPending() } }

    private fun discardPending() {
        pending?.let { repository.packageStorage.discard(it.sha256) }
        pending = null
    }
}

internal fun androidPngBounds(bytes: ByteArray, width: Int, height: Int) {
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    require(options.outMimeType == "image/png" && options.outWidth == width && options.outHeight == height) {
        "Android could not verify PNG bounds."
    }
}
