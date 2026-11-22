package org.doom.data

import android.content.Context
import android.os.Build
import android.provider.Settings
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import androidx.room.withTransaction
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.doom.database.*
import org.doom.domain.*
import org.doom.model.*
import org.doom.model.ModStatus.*
import org.doom.engine.ArchiveReader
import org.doom.engine.runtime.RuntimeDiagnostic
import org.doom.engine.runtime.ValidatedPackage
import org.json.JSONArray
import org.json.JSONObject
import java.nio.file.Files
import java.io.File
import java.math.BigInteger

internal fun isStrictlyNewerVersion(candidate: String, installed: String): Boolean {
    fun parse(value: String): Pair<List<BigInteger>, List<String>?>? {
        val pattern = Regex("^(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)(?:-([0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*))?(?:\\+[0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*)?$")
        val match = pattern.matchEntire(value) ?: return null
        val prerelease = match.groupValues[4].takeIf(String::isNotEmpty)?.split('.')
        if (prerelease.orEmpty().any { it.length > 1 && it.all(Char::isDigit) && it.startsWith('0') }) return null
        return listOf(match.groupValues[1], match.groupValues[2], match.groupValues[3]).map(::BigInteger) to prerelease
    }
    val next = parse(candidate) ?: return false
    val old = parse(installed) ?: return false
    for (index in 0..2) if (next.first[index] != old.first[index]) return next.first[index] > old.first[index]
    val a = next.second; val b = old.second
    if (a == null || b == null) return a == null && b != null
    for (index in 0 until minOf(a.size, b.size)) {
        val left = a[index]; val right = b[index]
        if (left == right) continue
        val leftNum = left.all(Char::isDigit); val rightNum = right.all(Char::isDigit)
        if (leftNum && rightNum) return BigInteger(left) > BigInteger(right)
        if (leftNum != rightNum) return leftNum
        return left > right
    }
    return a.size > b.size
}

internal class RoomModRepository(
    private val context: Context,
    private val database: DoomDatabase,
    private val preferences: DoomPreferences = DataStoreDoomPreferences(context),
    private val androidApi: Int = Build.VERSION.SDK_INT,
    private val overlayAuthorized: () -> Boolean = { Settings.canDrawOverlays(context) }
) : ModRepository {
    internal val packageStorage = PackageStorage(context.filesDir.toPath())
    private val dao = database.mods()
    private val lock = Mutex()
    private val exampleLock = Mutex()
    private var initialized = false
    private val recoveryPolicy = RuntimeRecoveryPolicy()
    private val verifiedDefinitions = object : LinkedHashMap<String, ValidatedPackage>(16, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ValidatedPackage>?) = size > 16
    }

    override fun observeInstalled(): Flow<List<InstalledMod>> = flow {
        initialize()
        emitAll(dao.observeRecords().map { records -> records.map { it.toModel() } })
    }

    override fun observeDetails(id: String): Flow<InstalledMod?> = flow {
        initialize()
        emitAll(dao.observeRecord(id).map { it?.toModel() })
    }

    override suspend fun bundledExamples() = withContext(Dispatchers.IO) {
        val review = context.assets.open(sampleAsset).use { ArchiveReader.validate(it, ::androidPngBounds) }
        listOf(ModManifest(review.id, review.name, review.version, review.description, review.minAndroidApi, review.appearance))
    }

    override suspend fun installExample(id: String) {
        require(id == sampleId) { "Bundled example is unavailable." }
        return withContext(Dispatchers.IO) {
            exampleLock.withLock {
                prepareImport()
                if (dao.record(id) != null) return@withLock
                val sha = context.assets.open(sampleAsset).use { packageStorage.stage(it, bundled = true) }
                try {
                    val reviewed = packageStorage.open(packageStorage.staged(sha, bundled = true)).use {
                        ArchiveReader.validate(it, ::androidPngBounds)
                    }
                    check(reviewed.id == id)
                    when (val result = installReviewed(reviewed, sha, bundled = true)) {
                        ModResult.Success -> Unit
                        is ModResult.Rejected -> {
                            if (result.reason != ModError.DUPLICATE_ID) error(result.message)
                        }
                    }
                } finally {
                    packageStorage.discard(sha, bundled = true)
                }
            }
        }
    }

    internal suspend fun prepareImport() = initialize()

    internal suspend fun loadRuntimeDefinition(id: String) = withContext(Dispatchers.IO) {
        val row = requireNotNull(dao.record(id)).mod
        val digest = requireNotNull(row.sha256) { "Import a public-format archive before running this mod." }
        require(row.packagePath == packageStorage.packagePath(id))
        val definition = packageStorage.open(packageStorage.target(id)).use {
            ArchiveReader.read(it, digest, ::androidPngBounds) { hash ->
                synchronized(verifiedDefinitions) { verifiedDefinitions[hash] }
            }.also { value -> synchronized(verifiedDefinitions) { verifiedDefinitions[digest] = value } }
        }
        require(definition.review.id == row.id && definition.review.version == row.version &&
            definition.review.minAndroidApi == row.minAndroidApi && androidApi >= row.minAndroidApi)
        definition
    }

    internal suspend fun recordRuntimeDiagnostic(event: RuntimeDiagnostic) = serialized { prefs ->
        val row = dao.record(event.modId)?.mod
        val now = event.timestampMillis
        val durableWorkflowMarker = event.triggerOrOperationId in setOf("workflow.started", "workflow.terminal")
        if (prefs.diagnosticLogging || event.severity != DiagnosticSeverity.INFO || durableWorkflowMarker) {
            database.withTransaction {
                dao.insertDiagnostic(ExecutionDiagnosticEntity(
                    timestamp = now, modId = event.modId,
                    runtimeSessionId = event.runtimeSessionId?.take(80), workflowId = event.workflowId?.take(120),
                    triggerOrOperationId = event.triggerOrOperationId?.take(120), severity = event.severity,
                    result = event.diagnosticResult, failureCategory = event.failureCategory,
                    message = sanitizeDiagnosticMessage(event.message),
                    durationMillis = event.durationMillis?.coerceIn(0, 30_000), resourceOwner = event.resourceOwner?.take(120)
                ))
                dao.trimDiagnostics()
                if (row != null) {
                    dao.log(ModExecutionLogEntity(modId = row.id, event = event.event.take(120), result = event.result.take(32),
                        message = sanitizeDiagnosticMessage(event.message).takeIf(String::isNotEmpty), timestamp = now))
                    dao.trimLog()
                }
            }
        }
        if (row != null && row.desiredEnabled && recoveryPolicy.qualifies(event.failureCategory)) {
            database.withTransaction {
                val current = dao.record(row.id)?.mod ?: return@withTransaction
                val failures = dao.qualifyingFaults(row.id, now - recoveryPolicy.windowMillis)
                if (recoveryPolicy.quarantined(failures, now)) {
                    dao.update(current.copy(status = ERROR,
                        lastError = "Suspended after repeated execution failures. Review diagnostics before retrying.",
                        desiredEnabled = false))
                } else if (current.status == READY_TO_ACTIVATE) {
                    transition(current, ERROR, prefs, desired = true,
                        error = "Execution failed. Review diagnostics before retrying.")
                }
            }
        }
        ModResult.Success
    }

    override fun observeExecutionDiagnostics(): Flow<List<ExecutionDiagnostic>> = flow {
        initialize()
        emitAll(dao.observeDiagnostics().map { rows -> rows.map { it.toModel() } })
    }

    override suspend fun clearExecutionDiagnostics() = withContext(Dispatchers.IO) {
        initialize()
        database.withTransaction {
            dao.deleteDiagnostics()
            dao.deleteLogs()
        }
    }

    internal suspend fun installReviewed(review: PackageReview, sha: String, bundled: Boolean = false): ModResult = serialized { prefs ->
        if (androidApi < review.minAndroidApi) {
            return@serialized ModResult.Rejected(ModError.INCOMPATIBLE, "Requires Android API ${review.minAndroidApi}.")
        }
        if (dao.record(review.id) != null) {
            return@serialized ModResult.Rejected(ModError.DUPLICATE_ID,
                "This mod ID is already installed. Uninstall it before installing another version.")
        }
        withContext(NonCancellable + Dispatchers.IO) {
            // Resolve a prior failed publication before allowing another attempt for the same ID.
            if (review.id in packageStorage.pendingIds()) recoverPackage(review.id, prefs)
            try {
                packageStorage.publish(review.id, sha, bundled)
                dao.insert(InstalledModEntity(
                    id = review.id, name = review.name, version = review.version,
                    packagePath = packageStorage.packagePath(review.id), description = review.description,
                    minAndroidApi = review.minAndroidApi, defaultColor = review.appearance.color,
                    defaultIntensity = review.appearance.intensity, sha256 = sha,
                    declaredCapabilities = JSONArray(review.capabilities).toString(),
                    author = review.author, license = review.license,
                    minimumIntensity = review.intensityRange.first, maximumIntensity = review.intensityRange.last,
                    ringDefaults = review.appearance.ring?.toJson()?.toString(),
                    optionDefinitions = JSONArray().apply { review.optionDefinitions.forEach {
                        put(JSONObject().put("id", it.id).put("min", it.minimum).put("max", it.maximum).put("default", it.default))
                    } }.toString(),
                    settingDefinitions = review.settingDefinitions.takeIf { it.isNotEmpty() }?.let(::encodeSettingDefinitions)
                ))
            } catch (failure: Exception) {
                if (review.id in packageStorage.pendingIds()) recoverPackage(review.id, prefs)
                throw failure
            }
            // After Room commits, a pending marker is safe to leave for the next startup.
            runCatching { packageStorage.clearJournal(review.id) }
            ModResult.Success
        }
    }

    internal suspend fun updateReviewed(review: PackageReview, sha: String): ModResult = serialized { prefs ->
        if (androidApi < review.minAndroidApi) return@serialized ModResult.Rejected(ModError.INCOMPATIBLE, "Requires Android API ${review.minAndroidApi}.")
        val before = dao.record(review.id)?.mod ?: return@serialized ModResult.Rejected(ModError.NOT_FOUND, "This mod is not installed.")
        if (before.status != INSTALLED_DISABLED || before.desiredEnabled) {
            return@serialized ModResult.Rejected(ModError.INVALID_TRANSITION, "Disable this mod before updating it.")
        }
        if (!isStrictlyNewerVersion(review.version, before.version)) {
            return@serialized ModResult.Rejected(ModError.INCOMPATIBLE, "Only a newer semantic version can be installed as an update.")
        }
        val oldSha = before.sha256 ?: return@serialized ModResult.Rejected(ModError.STORAGE, "The installed package cannot be verified for a safe update.")
        withContext(NonCancellable + Dispatchers.IO) {
            if (review.id in packageStorage.pendingIds()) recoverPackage(review.id, prefs)
            try {
                packageStorage.replaceForUpdate(review.id, oldSha, sha)
                database.withTransaction {
                    val current = dao.record(review.id)?.mod ?: error("Installed mod disappeared during update.")
                    check(current.sha256 == oldSha && current.status == INSTALLED_DISABLED && !current.desiredEnabled) {
                        "Mod state changed while its update was being committed."
                    }
                    dao.update(current.copy(
                        name = review.name, version = review.version, description = review.description,
                        minAndroidApi = review.minAndroidApi, defaultColor = review.appearance.color,
                        defaultIntensity = review.appearance.intensity, sha256 = sha,
                        declaredCapabilities = JSONArray(review.capabilities).toString(), author = review.author,
                        license = review.license, minimumIntensity = review.intensityRange.first,
                        maximumIntensity = review.intensityRange.last, ringDefaults = review.appearance.ring?.toJson()?.toString(),
                        optionDefinitions = JSONArray().apply { review.optionDefinitions.forEach {
                            put(JSONObject().put("id", it.id).put("min", it.minimum).put("max", it.maximum).put("default", it.default))
                        } }.toString(),
                        settingDefinitions = review.settingDefinitions.takeIf { it.isNotEmpty() }?.let(::encodeSettingDefinitions),
                        status = INSTALLED_DISABLED, desiredEnabled = false, lastError = null
                    ))
                }
                synchronized(verifiedDefinitions) { verifiedDefinitions.remove(oldSha) }
                packageStorage.clearJournal(review.id)
                ModResult.Success
            } catch (failure: Exception) {
                if (review.id in packageStorage.pendingIds()) recoverPackage(review.id, prefs)
                throw failure
            }
        }
    }

    private suspend fun recoverPackage(id: String, prefs: GlobalPreferences) {
        val record = dao.record(id)?.mod
        val updateRecovery = packageStorage.recoverUpdate(id, record?.sha256)
        if (updateRecovery != null) {
            if (updateRecovery) packageStorage.clearJournal(id)
            else if (record != null) transition(record, ERROR, prefs, desired = false,
                error = "Package update recovery is uncertain. The previous package and recovery journal were preserved.")
            return
        }
        if (record == null) Files.deleteIfExists(packageStorage.target(id))
        else if (record.sha256 != null && record.packagePath == packageStorage.packagePath(id) &&
            runCatching { packageStorage.digest(packageStorage.target(id)) }.getOrNull() != record.sha256) {
            transition(record, ERROR, prefs, desired = false, error = "Installed package is missing or damaged.")
        }
        packageStorage.clearJournal(id)
    }

    override suspend fun requestEnable(id: String): ModResult = withMod(id) { record, prefs ->
        if (!ModLifecycle.allows(record.mod.status, ENABLE_REQUESTED)) return@withMod invalid()
        val now = System.currentTimeMillis()
        if (recoveryPolicy.quarantined(dao.qualifyingFaults(id, now - recoveryPolicy.windowMillis), now)) {
            return@withMod ModResult.Rejected(ModError.QUARANTINED, "The mod is temporarily quarantined after repeated failures.")
        }
        transition(record.mod, ENABLE_REQUESTED, prefs, desired = true)
        evaluate(dao.record(id)!!, prefs)
    }

    override suspend fun disable(id: String): ModResult = withMod(id) { record, prefs ->
        if (!ModLifecycle.allows(record.mod.status, INSTALLED_DISABLED)) return@withMod invalid()
        transition(record.mod, INSTALLED_DISABLED, prefs, desired = false)
        ModResult.Success
    }

    override suspend fun configure(id: String, appearance: GlowAppearance): ModResult = withMod(id) { record, _ ->
        val options = record.mod.options()
        if (appearance.options.keys != options.map { it.id }.toSet() ||
            options.any { appearance.options[it.id] !in it.minimum..it.maximum }) {
            return@withMod ModResult.Rejected(ModError.INVALID_SETTING, "Invalid developer setting value.")
        }
        if (record.mod.status == UNINSTALLING) return@withMod invalid()
        if (appearance.intensity !in record.mod.minimumIntensity..record.mod.maximumIntensity) {
            return@withMod ModResult.Rejected(ModError.INVALID_SETTING, "Intensity is outside this mod's declared range.")
        }
        if ((appearance.ring == null) != (record.mod.ringDefaults == null)) {
            return@withMod ModResult.Rejected(ModError.INVALID_SETTING, "Ring settings are unavailable for this package.")
        }
        val json = record.settings?.settingsJson?.let { runCatching { JSONObject(it) }.getOrNull() } ?: JSONObject()
        json.put("options", JSONObject(appearance.options)).put("color", appearance.color).put("intensity", appearance.intensity)
        appearance.ring?.let { json.put("ring", it.toJson()) }
        dao.putSettings(ModSettingsEntity(id, json.toString(), System.currentTimeMillis()))
        ModResult.Success
    }

    override suspend fun configureSettings(id: String, values: Map<String, ModSettingValue>): ModResult = withMod(id) { record, _ ->
        if (record.mod.status == UNINSTALLING) return@withMod invalid()
        val definitions = runCatching { decodeSettingDefinitions(record.mod.settingDefinitions) }.getOrElse {
            return@withMod ModResult.Rejected(ModError.INVALID_SETTING, "This package has invalid settings metadata.")
        }
        val resolved = runCatching { ModSettingValidator.validateValues(definitions, values) }.getOrElse {
            return@withMod ModResult.Rejected(ModError.INVALID_SETTING, it.message ?: "Invalid mod setting value.")
        }
        val json = record.settings?.settingsJson?.let { runCatching { JSONObject(it) }.getOrNull() } ?: JSONObject()
        json.put("values", encodeSettingValues(resolved))
        database.withTransaction { dao.putSettings(ModSettingsEntity(id, json.toString(), System.currentTimeMillis())) }
        ModResult.Success
    }

    override suspend fun resetSettings(id: String) = withMod(id) { record, _ ->
        if (record.mod.status == UNINSTALLING) return@withMod invalid()
        dao.resetSettings(id)
        ModResult.Success
    }

    internal suspend fun diagnostics(): JSONArray {
        initialize()
        return database.withTransaction {
            JSONArray().apply {
                dao.rows().forEach { row ->
                    put(JSONObject().put("id", row.id).put("version", row.version).put("status", row.status.name)
                        .put("events", JSONArray().apply {
                            dao.logs(row.id).forEach { event ->
                                put(JSONObject().put("timestamp", event.timestamp).put("event", sanitizeDiagnosticMessage(event.event))
                                    .put("result", sanitizeDiagnosticMessage(event.result)).put("message", sanitizeDiagnosticMessage(event.message)))
                            }
                        }))
                }
            }
        }
    }

    internal suspend fun diagnosticExport(): JSONArray {
        initialize()
        return JSONArray().apply {
            dao.recentDiagnostics().asReversed().forEach { row ->
                put(JSONObject().put("timestamp", row.timestamp).put("modId", row.modId)
                    .put("runtimeSessionId", row.runtimeSessionId ?: JSONObject.NULL)
                    .put("workflowId", row.workflowId ?: JSONObject.NULL)
                    .put("operation", row.triggerOrOperationId ?: JSONObject.NULL)
                    .put("severity", row.severity.name).put("result", row.result.name)
                    .put("failureCategory", row.failureCategory.name).put("message", row.message)
                    .put("durationMillis", row.durationMillis ?: JSONObject.NULL)
                    .put("resourceOwner", row.resourceOwner ?: JSONObject.NULL))
            }
        }
    }

    override suspend fun setGrant(id: String, granted: Boolean, capabilityId: String): ModResult = withMod(id) { record, prefs ->
        if (record.mod.status == UNINSTALLING) return@withMod invalid()
        if (capabilityId !in record.mod.capabilities) return@withMod invalid()
        database.withTransaction {
            dao.putGrant(CapabilityGrantEntity(id, capabilityId, granted, System.currentTimeMillis()))
            if (record.mod.desiredEnabled && record.mod.status != ERROR) reconcile(dao.record(id)!!, prefs)
        }
        ModResult.Success
    }

    override suspend fun recordFailure(id: String, message: String): ModResult = withMod(id) { record, prefs ->
        if (record.mod.status == UNINSTALLING) return@withMod invalid()
        transition(record.mod, ERROR, prefs, error = message.take(500))
        ModResult.Success
    }

    override suspend fun uninstall(id: String): ModResult = withMod(id) { record, prefs ->
        if (!ModLifecycle.allows(record.mod.status, UNINSTALLING)) return@withMod invalid()
        withContext(NonCancellable) {
            val removed = transition(record.mod, UNINSTALLING, prefs, desired = false)
            finishUninstall(removed, prefs)
        }
    }

    override suspend fun disableAll(): ModResult = serialized { prefs ->
        database.withTransaction {
            dao.rows().filter { it.status != UNINSTALLING }.forEach {
                transition(it, INSTALLED_DISABLED, prefs, desired = false)
            }
        }
        ModResult.Success
    }

    override suspend fun setSafeMode(enabled: Boolean): ModResult = serialized {
        // DataStore is authoritative. Startup repairs the Room projection after interruption.
        preferences.setSafeMode(enabled)
        reconcileRows(preferences.values.first())
        ModResult.Success
    }

    override suspend fun reconcileRequirements(): ModResult = serialized { prefs ->
        reconcileRows(prefs)
        ModResult.Success
    }

    private suspend fun initialize() = lock.withLock { ensureInitialized() }

    private suspend fun ensureInitialized() {
        if (initialized) return
        val prefs = preferences.values.first()
        withContext(Dispatchers.IO) {
            packageStorage.pendingIds().forEach { recoverPackage(it, prefs) }
            packageStorage.clearAbandonedStage()
            backfillSettingDefinitions()
        }
        reconcileRows(prefs)
        reconcileInterruptedWorkflows()
        initialized = true
    }

    private suspend fun reconcileInterruptedWorkflows() = database.withTransaction {
        val interrupted = dao.interruptedWorkflowStarts()
        interrupted.forEach { start ->
            dao.insertDiagnostic(ExecutionDiagnosticEntity(
                timestamp = System.currentTimeMillis(), modId = start.modId,
                runtimeSessionId = start.runtimeSessionId, workflowId = start.workflowId,
                triggerOrOperationId = "workflow.interrupted", severity = DiagnosticSeverity.WARNING,
                result = DiagnosticResult.UNKNOWN, failureCategory = DiagnosticFailureCategory.INTERRUPTED,
                message = "Process ended before this workflow completed; DOOM did not replay its actions.",
                durationMillis = null, resourceOwner = null
            ))
        }
        if (interrupted.isNotEmpty()) dao.trimDiagnostics()
    }

    /** Older V4 installs predate the nullable Room metadata column; recover schemas from their hash-verified package bytes. */
    private suspend fun backfillSettingDefinitions() {
        for (row in dao.rows().filter { it.settingDefinitions == null && it.sha256 != null }) {
            try {
                require(row.packagePath == packageStorage.packagePath(row.id))
                val parsed = packageStorage.open(packageStorage.target(row.id)).use {
                    ArchiveReader.read(it, requireNotNull(row.sha256), ::androidPngBounds)
                }
                require(parsed.review.id == row.id && parsed.review.version == row.version)
                val definitions = parsed.review.settingDefinitions
                if (definitions.isNotEmpty()) dao.update(row.copy(settingDefinitions = encodeSettingDefinitions(definitions)))
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (_: Exception) {
                // A missing or damaged legacy package remains visible with its original row and settings.
            }
        }
    }

    private suspend fun reconcileRows(prefs: GlobalPreferences) {
        dao.rows().forEach { mod ->
            if (mod.status == UNINSTALLING) finishUninstall(mod, prefs)
            else if (mod.status != ERROR) reconcile(dao.record(mod.id)!!, prefs)
        }
    }

    private suspend fun reconcile(record: InstalledModRecord, prefs: GlobalPreferences) {
        val mod = record.mod
        if (!mod.desiredEnabled || prefs.safeMode) {
            val reason = if (mod.desiredEnabled && prefs.safeMode) safeMode.message else null
            if (mod.status != INSTALLED_DISABLED || mod.lastError != reason) {
                transition(mod, INSTALLED_DISABLED, prefs, error = reason)
            }
            return
        }
        val expected = requirementOutcome(record).first
        if (mod.status == expected) return
        if (mod.status !in setOf(ENABLE_REQUESTED, CHECKING_REQUIREMENTS)) {
            transition(mod, ENABLE_REQUESTED, prefs)
        }
        evaluate(dao.record(mod.id)!!, prefs)
    }

    private suspend fun evaluate(record: InstalledModRecord, prefs: GlobalPreferences): ModResult {
        val mod = record.mod
        if (prefs.safeMode) {
            transition(mod, INSTALLED_DISABLED, prefs, error = safeMode.message)
            return safeMode
        }
        val checking = if (mod.status == CHECKING_REQUIREMENTS) mod else transition(mod, CHECKING_REQUIREMENTS, prefs)
        val (status, rejection) = requirementOutcome(record)
        transition(checking, status, prefs, error = rejection?.message)
        return rejection ?: ModResult.Success
    }

    private fun requirementOutcome(record: InstalledModRecord): Pair<ModStatus, ModResult.Rejected?> = when {
        androidApi < record.mod.minAndroidApi -> INCOMPATIBLE to
            ModResult.Rejected(ModError.INCOMPATIBLE, "Requires Android API ${record.mod.minAndroidApi}.")
        record.mod.capabilities.any { DeviceCapabilityScanner(context).availability(it) in
            setOf("UNAVAILABLE", "UNSUPPORTED") } -> INCOMPATIBLE to
            ModResult.Rejected(ModError.INCOMPATIBLE, "A declared capability is unavailable on this device.")
        !record.approvedCapabilities.containsAll(record.mod.capabilities) -> PERMISSION_REQUIRED to
            ModResult.Rejected(ModError.PERMISSION_REQUIRED, "Approve the capabilities declared by this mod.")
        record.mod.capabilities.any { it == "overlay" || it == "overlay.render" } && !overlayAuthorized() ->
            PERMISSION_REQUIRED to ModResult.Rejected(ModError.PERMISSION_REQUIRED, "Grant Android overlay access.")
        else -> READY_TO_ACTIVATE to null
    }

    private suspend fun finishUninstall(mod: InstalledModEntity, prefs: GlobalPreferences): ModResult {
        check(mod.status == UNINSTALLING)
        try {
            withContext(Dispatchers.IO) {
                if (mod.packagePath != "asset:$sampleAsset") {
                    check(mod.packagePath == packageStorage.packagePath(mod.id))
                    Files.deleteIfExists(packageStorage.target(mod.id))
                }
            }
            dao.delete(mod.id)
            synchronized(verifiedDefinitions) { verifiedDefinitions.remove(mod.sha256) }
            return ModResult.Success
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            val message = "Could not remove the stored mod. Retry uninstall."
            transition(mod, ERROR, prefs, desired = false, error = message)
            return ModResult.Rejected(ModError.STORAGE, message)
        }
    }

    private suspend fun transition(
        from: InstalledModEntity,
        to: ModStatus,
        prefs: GlobalPreferences,
        desired: Boolean = from.desiredEnabled,
        error: String? = null
    ): InstalledModEntity = database.withTransaction {
        check(ModLifecycle.allows(from.status, to))
        val updated = from.copy(status = to, desiredEnabled = desired, lastError = error)
        dao.update(updated)
        if (to == ERROR || prefs.diagnosticLogging) {
            dao.log(ModExecutionLogEntity(modId = from.id, event = to.name,
                result = if (to == ERROR) "ERROR" else if (error != null) "BLOCKED" else "SUCCESS",
                message = error, timestamp = System.currentTimeMillis()))
            dao.trimLog()
        }
        updated
    }

    private suspend fun serialized(action: suspend (GlobalPreferences) -> ModResult): ModResult = lock.withLock {
        try {
            ensureInitialized()
            action(preferences.values.first())
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            ModResult.Rejected(ModError.STORAGE, "Could not update local mod state.")
        }
    }

    private suspend fun withMod(id: String, action: suspend (InstalledModRecord, GlobalPreferences) -> ModResult) =
        serialized { prefs ->
            val record = dao.record(id)
                ?: return@serialized ModResult.Rejected(ModError.NOT_FOUND, "Mod is unavailable.")
            action(record, prefs)
        }

    private fun invalid() = ModResult.Rejected(ModError.INVALID_TRANSITION, "This lifecycle request is unavailable.")

    private companion object {
        const val sampleId = "community.batteryglow"
        const val sampleAsset = "examples/$sampleId.doommod"
        val safeMode = ModResult.Rejected(ModError.SAFE_MODE, "Global safe mode is enabled.")
    }
}

private val InstalledModEntity.capabilities: List<String>
    get() = declaredCapabilities?.let { json ->
        val array = JSONArray(json)
        List(array.length()) { array.getString(it) }
    } ?: listOf("overlay")

private val InstalledModRecord.approvedCapabilities: Set<String>
    get() = grants.filter { it.approved }.map { it.capabilityId }.toSet()

private fun InstalledModRecord.toModel(): InstalledMod {
    val defaults = GlowAppearance(mod.defaultColor, mod.defaultIntensity, mod.ringDefaults?.let { JSONObject(it).toRingStyle() }, mod.options().associate { it.id to it.default })
    val settingsJson = settings?.settingsJson?.let { runCatching { JSONObject(it) }.getOrNull() }
    val appearance = settingsJson?.let { json -> runCatching {
        GlowAppearance(json.optString("color", defaults.color), json.optInt("intensity", defaults.intensity),
            json.optJSONObject("ring")?.toRingStyle() ?: defaults.ring,
            mod.options().associate { option -> option.id to (json.optJSONObject("options")?.optInt(option.id, option.default) ?: option.default) })
    }.getOrDefault(defaults) } ?: defaults
    val definitions = runCatching { decodeSettingDefinitions(mod.settingDefinitions) }.getOrDefault(emptyMap())
    val values = decodeSettingValues(definitions, settings?.settingsJson)
    return InstalledMod(mod.id, mod.name, mod.version, mod.desiredEnabled, mod.description, appearance,
        approvedCapabilities.any { it == "overlay" || it == "overlay.render" },
        mod.minAndroidApi, mod.status, mod.lastError, mod.capabilities, approvedCapabilities,
        mod.minimumIntensity..mod.maximumIntensity, mod.author, mod.license, mod.sha256 != null, mod.options(), definitions, values)
}

private fun InstalledModEntity.options(): List<IntegerSetting> = optionDefinitions?.let { text ->
    val array = JSONArray(text)
    List(array.length()) { i -> array.getJSONObject(i).let {
        IntegerSetting(it.getString("id"), it.getInt("min"), it.getInt("max"), it.getInt("default"))
    } }
} ?: emptyList()

private fun RingStyle.toJson() = JSONObject().put("thicknessDp", thicknessDp).put("diameterDp", diameterDp)
    .put("durationSeconds", durationSeconds).put("position", position.name)

private fun JSONObject.toRingStyle() = RingStyle(getInt("thicknessDp"), getInt("diameterDp"),
    getInt("durationSeconds"), RingPosition.valueOf(getString("position")))

private fun ExecutionDiagnosticEntity.toModel() = ExecutionDiagnostic(
    id = id, timestamp = timestamp, modId = modId, runtimeSessionId = runtimeSessionId,
    workflowId = workflowId, triggerOrOperationId = triggerOrOperationId, severity = severity,
    result = result, failureCategory = failureCategory, message = message,
    durationMillis = durationMillis, resourceOwner = resourceOwner
)

internal fun sanitizeDiagnosticMessage(input: String?): String {
    if (input == null) return ""
    return input.replace(Regex("[\\r\\n\\t\\u0000-\\u001f]"), " ")
        .replace(Regex("(?i)([a-z]:\\\\|/)(?:[^ ]+[\\\\/])+[^ ]*"), "[private path]")
        .replace(Regex("content://[^ ]+"), "[content uri]")
        .trim().take(240)
}

private val Context.preferences by preferencesDataStore("doom_preferences")

internal class DataStoreDoomPreferences(context: Context) : DoomPreferences {
    private val data = context.applicationContext.preferences
    private val preview = booleanPreferencesKey("preview_on_open")
    private val safeMode = booleanPreferencesKey("safe_mode")
    private val diagnostics = booleanPreferencesKey("diagnostics")
    private val theme = stringPreferencesKey("theme")

    override val values = data.data.map { values ->
        GlobalPreferences(
            safeMode = values[safeMode] ?: false,
            theme = ThemePreference.entries.find { it.name == values[theme] } ?: ThemePreference.DARK,
            diagnosticLogging = values[diagnostics] ?: false,
            previewOnOpen = values[preview] ?: true
        )
    }

    override suspend fun setSafeMode(enabled: Boolean) { data.edit { it[safeMode] = enabled } }
    override suspend fun setTheme(theme: ThemePreference) { data.edit { it[this.theme] = theme.name } }
    override suspend fun setDiagnosticLogging(enabled: Boolean) { data.edit { it[diagnostics] = enabled } }
    override suspend fun setPreviewOnOpen(enabled: Boolean) { data.edit { it[preview] = enabled } }
}

@Module
@InstallIn(SingletonComponent::class)
object PersistenceModule {
    @Provides @Singleton
    internal fun marketplaceRepository(@ApplicationContext context: Context, database: DoomDatabase): org.doom.domain.MarketplaceRepository =
        MarketplaceHttpRepository(context, database)
    @Provides @Singleton
    internal fun shizukuConnectionManager(@ApplicationContext context: Context) = ShizukuConnectionManager(context)

    @Provides @Singleton
    internal fun shizukuPrivilegedAdapter(manager: ShizukuConnectionManager) = ShizukuPrivilegedAdapter(manager)

    @Provides @Singleton
    internal fun deviceCapabilityScanner(@ApplicationContext context: Context, adapter: ShizukuPrivilegedAdapter): DeviceCapabilityScanner =
        DeviceCapabilityScanner(context, listOf(adapter))

    @Provides @Singleton
    internal fun database(@ApplicationContext context: Context): DoomDatabase =
        Room.databaseBuilder(context, DoomDatabase::class.java, "doom.db").addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9).build()

    @Provides @Singleton
    internal fun roomRepository(@ApplicationContext context: Context, database: DoomDatabase, preferences: DoomPreferences) =
        RoomModRepository(context, database, preferences)

    @Provides
    internal fun repository(implementation: RoomModRepository): ModRepository = implementation

    @Provides @Singleton
    internal fun preferences(@ApplicationContext context: Context): DoomPreferences = DataStoreDoomPreferences(context)

    @Provides
    fun modUseCases(repository: ModRepository, preferences: DoomPreferences) = ModUseCases(repository, preferences)
}
