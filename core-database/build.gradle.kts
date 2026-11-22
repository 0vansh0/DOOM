plugins {
    id("com.android.library")
    kotlin("android")
    kotlin("kapt")
}
android {
    namespace = "org.doom.core.database"
    compileSdk = 35
    defaultConfig {
        minSdk = 31
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
kotlin { jvmToolchain(17) }
kapt { arguments { arg("room.schemaLocation", "$projectDir/schemas") } }
dependencies {
    api(project(":core-model"))

    api("androidx.room:room-runtime:2.6.1")
    api("androidx.room:room-ktx:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")
}

