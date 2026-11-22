import java.security.KeyStore
import java.security.MessageDigest
import java.security.cert.X509Certificate
import java.net.URI

plugins {
    id("com.android.application")
    kotlin("android")
    id("org.jetbrains.kotlin.plugin.compose")
    kotlin("kapt")
    id("com.google.dagger.hilt.android")
}
val unsignedCheck = providers.gradleProperty("doomUnsignedVerification").orNull == "true"
val signingFields = listOf("DOOM_KEYSTORE", "DOOM_STORE_PASSWORD", "DOOM_KEY_ALIAS", "DOOM_KEY_PASSWORD")
val signingValues = signingFields.map { providers.environmentVariable(it).orNull }
val signingReady = signingValues.all { !it.isNullOrBlank() }
val approvedFingerprint = providers.gradleProperty("doomApprovedSignerSha256")
    .orElse(providers.environmentVariable("DOOM_APPROVED_SIGNER_SHA256"))
val productionMarketplaceBaseUrl = providers.gradleProperty("doomMarketplaceProductionBaseUrl")
    .orElse(providers.environmentVariable("DOOM_MARKETPLACE_PRODUCTION_BASE_URL"))
val checkReleaseSigning = tasks.register("checkReleaseSigning") {
    doLast {
        if (unsignedCheck) return@doLast
        val marketplaceUrl = productionMarketplaceBaseUrl.orNull?.trim()
            ?: error("Set doomMarketplaceProductionBaseUrl or DOOM_MARKETPLACE_PRODUCTION_BASE_URL to the approved production HTTPS endpoint before signing.")
        val marketplaceUri = runCatching { URI(marketplaceUrl) }.getOrNull()
            ?: error("The production marketplace endpoint must be a valid HTTPS URL.")
        val host = marketplaceUri.host?.lowercase()
        check(marketplaceUri.scheme.equals("https", ignoreCase = true) && host != null &&
            marketplaceUri.userInfo == null && marketplaceUri.query == null && marketplaceUri.fragment == null &&
            host !in setOf("localhost", "127.0.0.1", "::1", "0.0.0.0") && !host.endsWith(".invalid")) {
            "The signed release requires an approved, non-local HTTPS marketplace endpoint without credentials, query, or fragment."
        }
        check(signingReady) {
            "Release signing requires the four DOOM signing variables. Use -PdoomUnsignedVerification=true only for local unsigned inspection."
        }
        val key = file(signingValues[0]!!).canonicalFile
        check(key.isFile && !key.toPath().startsWith(rootDir.canonicalFile.toPath())) {
            "Release keystore must exist outside the repository."
        }
        val expected = approvedFingerprint.orNull?.trim()?.replace(":", "")?.lowercase()
        check(expected != null && expected.matches(Regex("[0-9a-f]{64}"))) {
            "Missing or invalid signer fingerprint. Set DOOM_APPROVED_SIGNER_SHA256 in this shell or the Gradle property doomApprovedSignerSha256 to the approved signing certificate's SHA-256 (64 hex digits, optional colons). See docs/release/SIGNING_GUIDE.md."
        }
        val certificate = runCatching {
            KeyStore.getInstance(key, signingValues[1]!!.toCharArray())
                .getCertificate(signingValues[2]) as X509Certificate
        }.getOrNull() ?: error("Unable to read the selected release certificate.")
        check(!certificate.subjectX500Principal.name.contains("CN=Android Debug", ignoreCase = true)
                && !signingValues[2].equals("androiddebugkey", ignoreCase = true)) {
            "Android debug signing identities cannot sign a release."
        }
        val actual = MessageDigest.getInstance("SHA-256").digest(certificate.encoded)
            .joinToString("") { "%02x".format(it) }
        check(actual == expected) { "Release certificate differs from the approved channel identity." }
    }
}
tasks.matching { it.name == "preReleaseBuild" }.configureEach { dependsOn(checkReleaseSigning) }
android {
    namespace = "org.doom.app"
    compileSdk = 35
    defaultConfig {
        minSdk = 31
        applicationId = "org.doom.app"
        targetSdk = 35
        versionCode = 3
        versionName = "0.1.0-beta.2"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    signingConfigs {
        if (signingReady && !unsignedCheck) create("approvedRelease") {
            val key = file(signingValues[0]!!).canonicalFile
            storeFile = key
            storePassword = signingValues[1]
            keyAlias = signingValues[2]
            keyPassword = signingValues[3]
        }
    }
    buildTypes {
        getByName("release") { isDebuggable = false; signingConfig = signingConfigs.findByName("approvedRelease") }
        create("benchmark") {
            initWith(getByName("release"))
            applicationIdSuffix = ".perf"
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += "release"
        }
    }
    buildFeatures { compose = true }
}
kotlin { jvmToolchain(17) }
dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    implementation(project(":feature-home"))
    implementation(project(":feature-discover"))
    implementation(project(":feature-installed"))
    implementation(project(":feature-mod-details"))
    implementation(project(":feature-settings"))
    implementation(project(":core-designsystem"))
    implementation(project(":core-data"))
    implementation(project(":core-domain"))
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.12.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test:runner:1.6.2")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    implementation("com.google.dagger:hilt-android:2.52")
    kapt("com.google.dagger:hilt-compiler:2.52")
}

android.sourceSets.named("main") {
    assets.srcDir(project(":sample-mods").layout.buildDirectory.dir("bundled"))
}
tasks.named("preBuild") { dependsOn(":sample-mods:batteryGlow") }
