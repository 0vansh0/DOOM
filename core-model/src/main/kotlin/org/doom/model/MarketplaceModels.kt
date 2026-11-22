package org.doom.model

import java.io.File

data class MarketplaceVersion(
    val modId: String,
    val version: String,
    val manifestSchema: Int,
    val definitionVersion: Int,
    val minAndroidApi: Int,
    val capabilities: List<String>,
    val compatibility: Map<String, String>,
    val sha256: String,
    val publishedAt: String?,
    val versionId: String
)

data class MarketplaceMod(
    val id: String,
    val name: String,
    val summary: String,
    val category: String,
    val developer: String?,
    val latest: MarketplaceVersion
)

data class MarketplacePage(
    val mods: List<MarketplaceMod>,
    val nextCursor: String?,
    val categories: List<String>,
    val refreshedAt: Long,
    val isStale: Boolean,
    val notice: String? = null
)

data class MarketplaceModDetails(
    val modId: String,
    val name: String,
    val summary: String,
    val category: String,
    val developer: String?,
    val versions: List<MarketplaceVersion>,
    val isStale: Boolean = false
)

data class MarketplaceDownloadedPackage(
    val file: File,
    val modId: String,
    val version: String,
    val sha256: String
)

enum class MarketplaceInstallPhase {
    IDLE, LOADING, DOWNLOADING, VERIFYING, VALIDATING, AWAITING_CONFIRMATION,
    INSTALLING, INSTALLED, UPDATE_AVAILABLE, FAILED, CANCELLED
}
