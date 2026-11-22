plugins {
    `java-library`
    kotlin("jvm")
}
kotlin { jvmToolchain(17) }
dependencies {
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    api(project(":core-model"))
    implementation("com.google.code.gson:gson:2.11.0")
    testImplementation("junit:junit:4.13.2")
}

sourceSets.named("main") { resources.srcDir(rootProject.file("packages/manifest-schema")); resources.include("v2/*.json") }

sourceSets.named("test") {
    resources.srcDir(rootProject.file("sample-mods/releases"))
    resources.srcDir(rootProject.file("sample-mods/sdk2"))
    // Keep community packages under their own resource path to avoid shadowing
    // legacy fixtures such as Battery-Glow.doommod on case-insensitive filesystems.
    resources.srcDir(rootProject.file("sample-mods/community-examples"))
    resources.srcDir(rootProject.file("packages/manifest-schema/conformance"))
    resources.srcDir(rootProject.file("packages/manifest-schema/fixtures/battery-glow"))
    resources.srcDir(rootProject.file("visual-builder/kotlin-fixtures"))
}
