plugins {
    `java-library`
    kotlin("jvm")
    application
}
kotlin { jvmToolchain(17) }
application { mainClass.set("org.doom.cli.MainKt") }
dependencies {
    implementation(project(":mod-engine"))
}
