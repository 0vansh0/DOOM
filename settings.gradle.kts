pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
rootProject.name = "DOOM"
include("app", "feature-home", "feature-discover", "feature-installed", "feature-mod-details",
    "feature-settings", "core-model", "core-domain", "core-data", "core-database",
    "core-designsystem", "mod-engine", "manifest-schema", "doom-cli", "sample-mods")

include(":benchmark")
