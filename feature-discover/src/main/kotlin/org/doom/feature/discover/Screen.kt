package org.doom.feature.discover

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import org.doom.core.designsystem.Page
import org.doom.model.*

@Composable
fun DiscoverScreen(
    examples: List<ModManifest>, installed: Set<String>, loading: Boolean, failed: Boolean, busy: Boolean,
    importMod: () -> Unit, importAvailable: Boolean, install: (String) -> Unit, retry: () -> Unit,
    marketplace: MarketplacePage? = null, marketplaceBusy: Boolean = false, marketplaceMessage: String? = null,
    marketplaceQuery: String = "", selectedMarketplaceMod: MarketplaceModDetails? = null,
    reviewName: String? = null, reviewId: String? = null, reviewDeveloper: String? = null, reviewAuthor: String? = null,
    reviewSha256: String? = null, reviewVersion: String? = null, reviewCapabilities: List<String> = emptyList(),
    reviewCompatible: Boolean = false, marketplaceActionBusy: Boolean = false,
    reviewIsUpdate: Boolean = false,
    compatibilityWarnings: List<String> = emptyList(),
    selectedCategory: String? = null, selectedSort: String = "recent", loadMoreAvailable: Boolean = false,
    onMarketplaceSearch: (String) -> Unit = {}, onMarketplaceCategory: (String?) -> Unit = {},
    onMarketplaceSort: (String) -> Unit = {}, onMarketplaceLoadMore: () -> Unit = {},
    onMarketplaceRetry: () -> Unit = {}, onMarketplaceOpen: (String) -> Unit = {},
    onMarketplaceCloseDetails: () -> Unit = {}, onMarketplacePrepareInstall: (MarketplaceVersion, Boolean) -> Unit = { _, _ -> },
    onMarketplaceConfirmInstall: () -> Unit = {}, onMarketplaceCancelReview: () -> Unit = {},
    installedVersions: Map<String, String> = emptyMap(),
    enabledMods: Set<String> = emptySet()
) = Page("Discover") {
    Button(onClick = importMod, enabled = importAvailable && !busy) { Text("Import mod") }
    Text("Local bundled examples", style = MaterialTheme.typography.titleLarge)
    when {
        loading -> CircularProgressIndicator()
        failed -> { Text("Could not read bundled examples."); TextButton(onClick = retry) { Text("Retry") } }
        examples.isEmpty() -> Text("No bundled examples available.")
        else -> examples.forEach { mod ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(mod.title, style = MaterialTheme.typography.titleMedium); Text(mod.description)
                    if (mod.id in installed) Text("Installed") else TextButton(onClick = { install(mod.id) }, enabled = !busy) { Text("Install ${mod.title}") }
                }
            }
        }
    }
    HorizontalDivider()
    Text("Community marketplace", style = MaterialTheme.typography.titleLarge)
    Text("Published packages from the DOOM marketplace. Moderation review is not a guarantee of safety.", style = MaterialTheme.typography.bodyMedium)
    OutlinedTextField(value = marketplaceQuery, onValueChange = onMarketplaceSearch, label = { Text("Search published mods") },
        singleLine = true, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), modifier = Modifier.fillMaxWidth())
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(selected = selectedCategory == null, onClick = { onMarketplaceCategory(null) }, label = { Text("All") })
        marketplace?.categories.orEmpty().forEach { category -> FilterChip(selected = selectedCategory == category, onClick = { onMarketplaceCategory(category) }, label = { Text(category) }) }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(selected = selectedSort == "recent", onClick = { onMarketplaceSort("recent") }, label = { Text("Recent") })
        FilterChip(selected = selectedSort == "name", onClick = { onMarketplaceSort("name") }, label = { Text("Name") })
    }
    if (marketplace?.isStale == true) Text(marketplace.notice ?: "Cached catalog; potentially outdated.", color = MaterialTheme.colorScheme.tertiary)
    if (marketplaceBusy && marketplace == null) repeat(3) {
        Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(18.dp)) { Text("Loading published mods…"); LinearProgressIndicator(Modifier.fillMaxWidth()) } }
    }
    if (loadMoreAvailable) TextButton(onClick = onMarketplaceLoadMore, enabled = !marketplaceBusy) { Text(if (marketplaceBusy) "Loading…" else "Load more") }
    marketplaceMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    when {
        marketplace == null && !marketplaceBusy -> Text("Marketplace is unavailable. No local examples are shown as marketplace results.")
        marketplace != null && marketplace.mods.isEmpty() -> Text(if (marketplace.isStale) "No cached results. Connect to browse published mods." else "No published mods match this search.")
        else -> marketplace?.mods.orEmpty().forEach { mod ->
            Card(onClick = { onMarketplaceOpen(mod.id) }, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(mod.name, style = MaterialTheme.typography.titleMedium)
                    Text("by ${mod.developer ?: "Unspecified developer"} · ${mod.latest.version}")
                    Text(mod.summary)
                    Text("Android API ${mod.latest.minAndroidApi}+ · ${mod.latest.capabilities.size} capabilities")
                    installedVersions[mod.id]?.let { old -> Text(if (old != mod.latest.version) "Update available: $old → ${mod.latest.version}" else "Installed") }
                }
            }
        }
    }
    if (marketplace != null || marketplaceMessage != null) TextButton(onClick = onMarketplaceRetry, enabled = !marketplaceBusy) { Text("Refresh marketplace") }
    selectedMarketplaceMod?.let { details ->
        val latest = details.versions.firstOrNull()
        AlertDialog(onDismissRequest = onMarketplaceCloseDetails, title = { Text(details.name) },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Developer: ${details.developer ?: "Unspecified developer"}"); Text(details.summary)
                latest?.let { version ->
                    Text("Version ${version.version} · manifest ${version.manifestSchema} · definition ${version.definitionVersion}")
                    Text("Requires Android API ${version.minAndroidApi}+ · SHA-256 ${version.sha256.take(12)}…")
                    Text("Requested capabilities", style = MaterialTheme.typography.titleSmall)
                    version.capabilities.forEach { Text("• $it: ${capabilityDescription(it)}") }
                    Text("Packages are locally authored and unverified. Marketplace approval does not guarantee safety.")
                    if (details.isStale) Text("Cached metadata is informational only; live server verification is required to install.")
                }
            } },
            confirmButton = { TextButton(onClick = { latest?.let { onMarketplacePrepareInstall(it, details.modId in installedVersions) } },
                enabled = !marketplaceActionBusy && !details.isStale && latest != null && installedVersions[details.modId] != latest.version && details.modId !in enabledMods) {
                Text(if (details.modId in enabledMods) "Disable before update" else if (details.modId in installedVersions) "Review update" else "Download for review")
            } },
            dismissButton = { TextButton(onClick = onMarketplaceCloseDetails) { Text("Close") } })
    }
    if (reviewName != null) AlertDialog(onDismissRequest = { if (!marketplaceActionBusy) onMarketplaceCancelReview() },
        title = { Text("${if (reviewIsUpdate) "Update" else "Install"} $reviewName?") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("${reviewId.orEmpty()} · version ${reviewVersion.orEmpty()}")
            Text("Marketplace developer: ${reviewDeveloper ?: "Unspecified"} · package author: ${reviewAuthor ?: "Unspecified"} (unverified)")
            Text("SHA-256: ${reviewSha256.orEmpty()}")
            Text(if (reviewIsUpdate) "This replaces the installed package using a recovery journal, preserves its settings and leaves the mod disabled. Newly requested capabilities are not approved." else "This installs the mod disabled. Capability approval and Android permissions are separate steps.")
            reviewCapabilities.forEach { Text("• $it: ${capabilityDescription(it)}") }
            compatibilityWarnings.forEach { Text("Compatibility: $it", color = MaterialTheme.colorScheme.tertiary) }
            Text("Developer identity is unverified. Review these requests before enabling.")
        } },
        confirmButton = { TextButton(onClick = onMarketplaceConfirmInstall, enabled = reviewCompatible && !marketplaceActionBusy) { Text(if (reviewIsUpdate) "Update disabled" else "Install disabled") } },
        dismissButton = { TextButton(onClick = onMarketplaceCancelReview, enabled = !marketplaceActionBusy) { Text("Cancel") } })
}
