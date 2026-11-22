import java.net.URI

plugins {
    id("com.android.library")
    kotlin("android")
    kotlin("kapt")
    id("com.google.dagger.hilt.android")
}
android {
    namespace = "org.doom.core.data"
    compileSdk = 35
    defaultConfig {
        minSdk = 31
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        val marketplaceUrl = providers.gradleProperty("doomMarketplaceBaseUrl").orElse("https://marketplace.doom.invalid/").get()
        buildConfigField("String", "MARKETPLACE_BASE_URL", "\"${marketplaceUrl.trimEnd('/')}/\"")
    }
    buildTypes.named("release") {
        // No production hostname is assumed. Until an approved HTTPS URL is
        // supplied, the reserved .invalid origin makes marketplace calls fail closed.
        val configured = providers.gradleProperty("doomMarketplaceProductionBaseUrl")
            .orElse(providers.environmentVariable("DOOM_MARKETPLACE_PRODUCTION_BASE_URL"))
            .orElse("https://marketplace.doom.invalid/")
            .get().trimEnd('/')
        val endpoint = URI(configured)
        require(endpoint.scheme == "https" && endpoint.host != null && endpoint.host !in setOf("localhost", "127.0.0.1", "::1")) {
            "Release marketplace URL must be an approved HTTPS origin; localhost and HTTP are not allowed."
        }
        buildConfigField("String", "MARKETPLACE_BASE_URL", "\"$configured/\"")
    }
    buildFeatures { buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
kotlin { jvmToolchain(17) }
dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("junit:junit:4.13.2")
    implementation(project(":core-domain"))
    api(project(":core-database"))
    implementation(project(":mod-engine"))
    implementation("com.google.dagger:hilt-android:2.52")
    kapt("com.google.dagger:hilt-compiler:2.52")
    implementation("androidx.datastore:datastore-preferences:1.1.2")
    implementation("dev.rikka.shizuku:api:12.2.0")
    implementation("dev.rikka.shizuku:provider:12.2.0")
}


android.sourceSets.named("androidTest") { assets.srcDir(project(":core-database").file("schemas")) }

android.sourceSets.named("androidTest") {
    assets.srcDir(project(":sample-mods").layout.buildDirectory.dir("bundled"))
}
tasks.matching { it.name == "preDebugAndroidTestBuild" }.configureEach { dependsOn(":sample-mods:batteryGlow") }

android.sourceSets.named("androidTest") { assets.srcDir(rootProject.file("sample-mods/releases")) }

android.sourceSets.named("androidTest") { assets.srcDir(rootProject.file("sample-mods/sdk2")) }
