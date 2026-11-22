package org.doom.data

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.doom.core.data.BuildConfig
import org.doom.database.DoomDatabase
import org.doom.database.MarketplaceCatalogEntity
import org.doom.domain.MarketplaceRepository
import org.doom.model.*
import org.json.JSONArray
import org.json.JSONObject

private const val PACKAGE_LIMIT = 10 * 1024 * 1024

@Singleton
class MarketplaceHttpRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: DoomDatabase,
) : MarketplaceRepository {
    private val base = URL(BuildConfig.MARKETPLACE_BASE_URL)
    private val cache = database.marketplaceCatalog()

    init {
        require(base.protocol == "https" || BuildConfig.DEBUG) { "Marketplace connections must use HTTPS." }
        require(base.userInfo == null && base.query == null && base.ref == null)
    }

    override suspend fun browse(search: String?, category: String?, cursor: String?, limit: Int, sort: String): MarketplacePage = withContext(Dispatchers.IO) {
        require(limit in 1..50 && sort in setOf("recent", "name"))
        val query = Uri.Builder().appendQueryParameter("limit", limit.toString()).appendQueryParameter("sort", sort)
            .apply { search?.takeIf(String::isNotBlank)?.let { appendQueryParameter("search", it.take(80)) }; category?.let { appendQueryParameter("category", it.take(40)) }; cursor?.let { appendQueryParameter("cursor", it) } }.build().encodedQuery
        try {
            val response = requestJson("v1/catalog/mods?$query")
            val rows = response.getJSONArray("data").toListings()
            val timestamp = System.currentTimeMillis()
            database.withTransaction { cache.upsert(rows.map { it.toEntity(timestamp) }); cache.trim() }
            val categories = cache.recent(100).map { it.category }.distinct().sorted()
            MarketplacePage(rows, response.optJSONObject("pagination")?.optString("nextCursor")?.takeUnless { it == "null" || it.isBlank() }, categories, timestamp, false)
        } catch (failure: Exception) {
            if (failure is kotlinx.coroutines.CancellationException) throw failure
            val cached = cache.recent(100).mapNotNull { it.toModel() }
                .filter { (search.isNullOrBlank() || it.name.contains(search, true) || it.summary.contains(search, true)) && (category == null || it.category == category) }
                .take(limit)
            MarketplacePage(cached, null, cache.recent(100).map { it.category }.distinct().sorted(),
                cache.recent(100).maxOfOrNull { it.refreshedAt } ?: 0L, true, "Showing cached catalog data. It may be outdated; installation requires a live release check.")
        }
    }

    override suspend fun details(modId: String): MarketplaceModDetails = withContext(Dispatchers.IO) {
        require(modId.matches(Regex("[a-z][a-z0-9]*(?:[._-][a-z0-9]+)*")))
        try {
            val data = requestJson("v1/catalog/mods/${Uri.encode(modId)}").getJSONObject("data")
            val mod = data.getJSONObject("mod")
            val versions = data.getJSONArray("versions").toListings()
            val stamp = System.currentTimeMillis()
            database.withTransaction { cache.upsert(versions.map { it.toEntity(stamp) }); cache.trim() }
            MarketplaceModDetails(modId, mod.getString("name"), mod.optString("summary"), mod.optString("category"),
                mod.optString("developer").takeUnless { it == "null" || it.isBlank() }, versions.map { it.latest }, false)
        } catch (failure: Exception) {
            if (failure is kotlinx.coroutines.CancellationException) throw failure
            val saved = cache.find(modId)?.toModel() ?: throw failure
            MarketplaceModDetails(modId, saved.name, saved.summary, saved.category, saved.developer, listOf(saved.latest), true)
        }
    }

    override suspend fun download(modId: String, version: String, expectedSha256: String): MarketplaceDownloadedPackage = withContext(Dispatchers.IO) {
        require(base.protocol == "https") { "Marketplace installation requires authenticated HTTPS metadata. Configure HTTPS before downloading packages." }
        require(expectedSha256.matches(Regex("[a-f0-9]{64}"))) { "Marketplace checksum is invalid." }
        val query = requestJson("v1/catalog/mods/${Uri.encode(modId)}/versions/${Uri.encode(version)}/download").getJSONObject("data")
        require(query.getString("modId") == modId && query.getString("version") == version && query.getString("sha256") == expectedSha256) {
            "Published package identity changed. Refresh the listing before installing."
        }
        val path = query.getString("downloadUrl")
        require(path.startsWith("/v1/catalog/mods/") && path.endsWith("/package") && !path.contains("\\") && !path.contains("..")) { "Marketplace returned an unsafe download destination." }
        val target = URL(base, path)
        require(target.protocol == base.protocol && target.host.equals(base.host, true) && target.port == base.port) { "Marketplace package URL is outside the trusted API origin." }
        val file = File(context.cacheDir, "marketplace-${UUID.randomUUID()}.doommod")
        try {
            require(file.createNewFile()) { "Could not create a private package download file." }
            val connection = open(target)
            val job = currentCoroutineContext()[kotlinx.coroutines.Job]
            val cancelHook = job?.invokeOnCompletion { connection.disconnect() }
            try {
                val status = connection.responseCode
                require(status == HttpURLConnection.HTTP_OK) { "Package download failed with HTTP $status." }
                require(connection.contentLengthLong in 0..PACKAGE_LIMIT.toLong()) { "Package exceeds the 10 MB download limit." }
                require(connection.contentType?.substringBefore(';') in setOf("application/vnd.doom.mod", "application/octet-stream")) { "Marketplace returned an unexpected package type." }
                require(connection.getHeaderField("x-doom-sha256") == expectedSha256) { "Marketplace artifact checksum metadata did not match." }
                val digest = MessageDigest.getInstance("SHA-256")
                var count = 0L
                connection.inputStream.use { input -> file.outputStream().use { output ->
                    val buffer = ByteArray(16 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = input.read(buffer); if (read < 0) break
                        count += read
                        require(count <= PACKAGE_LIMIT) { "Package exceeds the 10 MB download limit." }
                        digest.update(buffer, 0, read); output.write(buffer, 0, read)
                    }
                } }
                require(count > 0 && (connection.contentLengthLong < 0 || count == connection.contentLengthLong)) { "Package download was incomplete." }
                val actual = digest.digest().joinToString("") { "%02x".format(it) }
                require(actual == expectedSha256) { "Downloaded package checksum did not match the published release." }
                MarketplaceDownloadedPackage(file, modId, version, actual)
            } finally { cancelHook?.dispose(); connection.disconnect() }
        } catch (failure: Exception) {
            file.delete()
            if (failure is kotlinx.coroutines.CancellationException) throw failure
            throw IOException(failure.message ?: "Marketplace package download failed.", failure)
        }
    }

    private suspend fun requestJson(path: String): JSONObject {
        val connection = open(URL(base, path))
        val hook = currentCoroutineContext()[kotlinx.coroutines.Job]?.invokeOnCompletion { connection.disconnect() }
        try {
            val status = connection.responseCode
            if (status !in 200..299) throw IOException("Marketplace request failed with HTTP $status.")
            val bytes = connection.inputStream.use { input -> input.readBytes().also { require(it.size <= 2 * 1024 * 1024) { "Marketplace response is too large." } } }
            return JSONObject(bytes.toString(Charsets.UTF_8)).let { root -> root.optJSONObject("data")?.let { JSONObject().put("data", it).put("pagination", root.optJSONObject("pagination")) } ?: root }
        } finally { hook?.dispose(); connection.disconnect() }
    }

    private fun open(url: URL): HttpURLConnection {
        require(url.protocol == "https" || (BuildConfig.DEBUG && url.protocol == "http")) { "Marketplace requires HTTPS." }
        require(url.host.equals(base.host, true) && url.port == base.port) { "Marketplace request is outside the configured API origin." }
        return (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"; connectTimeout = 8_000; readTimeout = 20_000
            instanceFollowRedirects = false; setRequestProperty("Accept", "application/json, application/vnd.doom.mod")
            setRequestProperty("User-Agent", "DOOM-Android")
        }
    }

    private fun JSONArray.toListings(): List<MarketplaceMod> = List(length()) { index ->
        val o = getJSONObject(index); val id = o.getString("id")
        val caps = o.getJSONArray("capabilities").strings()
        val compatibility = o.optJSONObject("compatibility")?.let { obj -> obj.keys().asSequence().associateWith { obj.get(it).toString() } }.orEmpty()
        val version = MarketplaceVersion(id, o.getString("version"), o.getInt("manifestSchema"), o.getInt("definitionVersion"),
            o.getInt("minAndroidApi"), caps, compatibility, o.getString("sha256"), o.optString("publishedAt").takeUnless { it == "null" }, o.getString("versionId"))
        MarketplaceMod(id, o.getString("name"), o.optString("summary"), o.optString("category", "other"),
            o.optString("developer").takeUnless { it == "null" || it.isBlank() }, version)
    }

    private fun JSONArray.strings() = List(length()) { getString(it) }
    private fun MarketplaceMod.toEntity(time: Long) = MarketplaceCatalogEntity(id, name, summary, category, developer, latest.version,
        latest.manifestSchema, latest.definitionVersion, latest.minAndroidApi, JSONArray(latest.capabilities).toString(),
        JSONObject(latest.compatibility).toString(), latest.sha256, latest.publishedAt, latest.versionId, time)
    private fun MarketplaceCatalogEntity.toModel(): MarketplaceMod? = runCatching {
        MarketplaceMod(modId, name, summary, category, developer, MarketplaceVersion(modId, version, manifestSchema,
            definitionVersion, minAndroidApi, JSONArray(capabilitiesJson).strings(), JSONObject(compatibilityJson).let { obj -> obj.keys().asSequence().associateWith { obj.get(it).toString() } },
            sha256, publishedAt, versionId))
    }.getOrNull()
}
