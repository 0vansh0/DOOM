package org.doom.domain

import kotlinx.coroutines.flow.Flow
import org.doom.model.*

interface ModRepository {
    fun observeInstalled(): Flow<List<InstalledMod>>
    fun observeDetails(id: String): Flow<InstalledMod?>
    suspend fun bundledExamples(): List<ModManifest>
    suspend fun installExample(id: String)
    suspend fun requestEnable(id: String): ModResult
    suspend fun disable(id: String): ModResult
    suspend fun setGrant(id: String, granted: Boolean, capabilityId: String = "overlay"): ModResult
    suspend fun configure(id: String, appearance: GlowAppearance): ModResult
    suspend fun configureSettings(id: String, values: Map<String, ModSettingValue>): ModResult
    suspend fun resetSettings(id: String): ModResult
    suspend fun uninstall(id: String): ModResult
    suspend fun recordFailure(id: String, message: String): ModResult
    suspend fun disableAll(): ModResult
    suspend fun reconcileRequirements(): ModResult
    suspend fun setSafeMode(enabled: Boolean): ModResult
    fun observeExecutionDiagnostics(): Flow<List<ExecutionDiagnostic>>
    suspend fun clearExecutionDiagnostics()
}

interface DoomPreferences {
    val values: Flow<GlobalPreferences>
    suspend fun setSafeMode(enabled: Boolean)
    suspend fun setTheme(theme: ThemePreference)
    suspend fun setDiagnosticLogging(enabled: Boolean)
    suspend fun setPreviewOnOpen(enabled: Boolean)
}

/** Public catalog contains only server-published metadata; cache entries are never install authorization. */
interface MarketplaceRepository {
    suspend fun browse(search: String? = null, category: String? = null, cursor: String? = null, limit: Int = 20, sort: String = "recent"): MarketplacePage
    suspend fun details(modId: String): MarketplaceModDetails
    suspend fun download(modId: String, version: String, expectedSha256: String): MarketplaceDownloadedPackage
}

class ObserveInstalledMods(private val repository: ModRepository) {
    operator fun invoke() = repository.observeInstalled()
}

class ObserveModDetails(private val repository: ModRepository) {
    operator fun invoke(id: String) = repository.observeDetails(id)
}

class RequestEnableMod(private val repository: ModRepository) {
    suspend operator fun invoke(id: String) = repository.requestEnable(id)
}

class DisableMod(private val repository: ModRepository) {
    suspend operator fun invoke(id: String) = repository.disable(id)
}

class UpdateModSettings(private val repository: ModRepository) {
    suspend operator fun invoke(id: String, appearance: GlowAppearance) = repository.configure(id, appearance)
    suspend fun declarative(id: String, values: Map<String, ModSettingValue>) = repository.configureSettings(id, values)
}

class UninstallMod(private val repository: ModRepository) {
    suspend operator fun invoke(id: String) = repository.uninstall(id)
}

class RecordModFailure(private val repository: ModRepository) {
    suspend operator fun invoke(id: String, message: String) = repository.recordFailure(id, message)
}

class ModUseCases(private val repository: ModRepository, private val preferences: DoomPreferences) {
    val installed = ObserveInstalledMods(repository)
    val details = ObserveModDetails(repository)
    val requestEnable = RequestEnableMod(repository)
    val disable = DisableMod(repository)
    val configure = UpdateModSettings(repository)
    suspend fun configureSettings(id: String, values: Map<String, ModSettingValue>) = repository.configureSettings(id, values)
    val uninstall = UninstallMod(repository)
    val recordFailure = RecordModFailure(repository)
    val globalPreferences = preferences.values
    suspend fun resetSettings(id: String) = repository.resetSettings(id)
    suspend fun bundledExamples() = repository.bundledExamples()
    suspend fun installExample(id: String) = repository.installExample(id)
    suspend fun reconcileRequirements() = repository.reconcileRequirements()
    suspend fun setGrant(id: String, granted: Boolean, capabilityId: String = "overlay") = repository.setGrant(id, granted, capabilityId)
    suspend fun disableAll() = repository.disableAll()
    suspend fun setSafeMode(enabled: Boolean) = repository.setSafeMode(enabled)
    fun executionDiagnostics() = repository.observeExecutionDiagnostics()
    suspend fun clearExecutionDiagnostics() = repository.clearExecutionDiagnostics()
    suspend fun setTheme(theme: ThemePreference) = preferences.setTheme(theme)
    suspend fun setDiagnosticLogging(enabled: Boolean) = preferences.setDiagnosticLogging(enabled)
    suspend fun setPreviewOnOpen(enabled: Boolean) = preferences.setPreviewOnOpen(enabled)
}
