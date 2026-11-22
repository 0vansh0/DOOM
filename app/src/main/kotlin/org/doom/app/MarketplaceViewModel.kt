package org.doom.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.doom.data.ImportCandidate
import org.doom.data.ModImportCoordinator
import org.doom.data.DeviceCapabilityScanner
import org.doom.domain.MarketplaceRepository
import org.doom.domain.ModResult
import org.doom.model.*

data class MarketplaceUiState(
    val page: MarketplacePage? = null,
    val selected: MarketplaceModDetails? = null,
    val candidate: ImportCandidate? = null,
    val busy: Boolean = false,
    val message: String? = null,
    val query: String = "",
    val category: String? = null,
    val sort: String = "recent",
    val compatibilityWarnings: List<String> = emptyList(),
    val canInstallReviewedPackage: Boolean = false,
    val reviewIsUpdate: Boolean = false,
)

@HiltViewModel
class MarketplaceViewModel @Inject constructor(
    private val repository: MarketplaceRepository,
    private val importer: ModImportCoordinator,
    private val capabilityScanner: DeviceCapabilityScanner,
) : ViewModel() {
    private val mutable = MutableStateFlow(MarketplaceUiState())
    val state = mutable.asStateFlow()
    private var request: Job? = null
    private var searchRequest: Job? = null

    init { refresh() }

    fun search(value: String) {
        mutable.value = mutable.value.copy(query = value)
        if (mutable.value.busy || mutable.value.selected != null) return
        searchRequest?.cancel()
        searchRequest = viewModelScope.launch {
            delay(350)
            refresh(search = value)
        }
    }

    fun refresh(search: String = state.value.query, category: String? = state.value.category, sort: String = state.value.sort) {
        if (mutable.value.busy) return
        mutable.value = mutable.value.copy(query = search, category = category, sort = sort, busy = true, message = null)
        request?.cancel()
        request = viewModelScope.launch {
            try { mutable.value = mutable.value.copy(page = repository.browse(search, category, sort = sort), busy = false) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { mutable.value = mutable.value.copy(busy = false, message = e.message ?: "Marketplace is unavailable.") }
        }
    }

    fun loadMore() {
        val current = mutable.value
        val cursor = current.page?.nextCursor ?: return
        if (current.busy) return
        mutable.value = current.copy(busy = true, message = null)
        request = viewModelScope.launch {
            try {
                val next = repository.browse(current.query, current.category, cursor, sort = current.sort)
                val combined = current.page.mods + next.mods.filterNot { item -> current.page.mods.any { it.id == item.id } }
                mutable.value = mutable.value.copy(page = next.copy(mods = combined, categories = (current.page.categories + next.categories).distinct()), busy = false)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { mutable.value = mutable.value.copy(busy = false, message = e.message ?: "Could not load more mods.") }
        }
    }

    fun open(modId: String) {
        if (mutable.value.busy) return
        mutable.value = mutable.value.copy(busy = true, message = null)
        request = viewModelScope.launch {
            try { mutable.value = mutable.value.copy(selected = repository.details(modId), busy = false) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { mutable.value = mutable.value.copy(busy = false, message = e.message ?: "Mod details are unavailable.") }
        }
    }

    fun closeDetails() { if (!mutable.value.busy) mutable.value = mutable.value.copy(selected = null, candidate = null) }

    fun prepareInstall(version: MarketplaceVersion, isUpdate: Boolean = false) {
        if (mutable.value.busy || mutable.value.candidate != null) return
        mutable.value = mutable.value.copy(busy = true, message = "Downloading and verifying published package…")
        request = viewModelScope.launch {
            var reviewCreated = false
            try {
                val downloaded = repository.download(version.modId, version.version, version.sha256)
                mutable.value = mutable.value.copy(message = "Validating package with DOOM importer…")
                val candidate = importer.reviewDownloaded(downloaded.file, version.modId, version.version, version.sha256)
                reviewCreated = true
                val device = capabilityScanner.scan(appForeground = true)
                val report = ModCompatibilityEvaluator.evaluate(candidate.manifest.capabilities, emptySet(), candidate.manifest.minAndroidApi,
                    device.androidApi, definitionSupported = true, manifestSupported = true,
                    capabilities = device.capabilities.associateBy { it.id })
                val installable = candidate.compatible && report.androidCompatible && report.capabilities.all {
                    it.availability in setOf(CapabilityAvailability.AVAILABLE, CapabilityAvailability.DENIED)
                }
                val notices = buildList {
                    report.capabilities.filter { it.availability != CapabilityAvailability.AVAILABLE }.forEach { add("${it.capabilityId}: ${it.reason}") }
                    device.scanError?.let { add(it) }
                }
                mutable.value = mutable.value.copy(candidate = candidate, busy = false,
                    compatibilityWarnings = notices,
                    canInstallReviewedPackage = installable,
                    reviewIsUpdate = isUpdate,
                    message = if (installable) "Package verified. Review the requested capabilities before installing." else "This package has an unsupported requirement and cannot be installed on this device.")
            } catch (e: CancellationException) { if (reviewCreated) withContext(NonCancellable) { runCatching { importer.discard() } }; throw e }
            catch (e: Exception) { if (reviewCreated) runCatching { importer.discard() }; mutable.value = mutable.value.copy(busy = false, candidate = null, message = e.message ?: "Package could not be verified.") }
        }
    }

    fun confirmInstall() {
        val candidate = mutable.value.candidate ?: return
        if (mutable.value.busy || !candidate.compatible || !mutable.value.canInstallReviewedPackage) return
        mutable.value = mutable.value.copy(busy = true, message = "Installing disabled mod…")
        request = viewModelScope.launch {
            try {
                when (val result = if (mutable.value.reviewIsUpdate) importer.update(candidate) else importer.install(candidate)) {
                    ModResult.Success -> mutable.value = mutable.value.copy(busy = false, candidate = null, compatibilityWarnings = emptyList(), canInstallReviewedPackage = false,
                        reviewIsUpdate = false, message = if (state.value.reviewIsUpdate) "${candidate.manifest.name} updated. Its settings were preserved and it remains disabled; review any new capability requests." else "${candidate.manifest.name} installed disabled. Review and approve capabilities before enabling.")
                    is ModResult.Rejected -> mutable.value = mutable.value.copy(busy = false, candidate = null, compatibilityWarnings = emptyList(), canInstallReviewedPackage = false, reviewIsUpdate = false, message = result.message)
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { mutable.value = mutable.value.copy(busy = false, candidate = null, compatibilityWarnings = emptyList(), canInstallReviewedPackage = false, reviewIsUpdate = false, message = e.message ?: "Installation failed.") }
        }
    }

    fun cancelReview() {
        if (mutable.value.busy) return
        viewModelScope.launch { runCatching { importer.discard() }; mutable.value = mutable.value.copy(candidate = null, compatibilityWarnings = emptyList(), canInstallReviewedPackage = false, reviewIsUpdate = false, message = null) }
    }
}
