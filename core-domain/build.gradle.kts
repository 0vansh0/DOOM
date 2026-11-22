plugins {
    `java-library`
    kotlin("jvm")
}
kotlin { jvmToolchain(17) }
dependencies {
    testImplementation("junit:junit:4.13.2")
    api(project(":core-model"))
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
}

