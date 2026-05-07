plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

fun readEnvValue(name: String): String {
    val envFile = rootProject.projectDir.parentFile.resolve(".env")
    if (!envFile.exists()) return ""
    return envFile.readLines()
        .map { it.trim() }
        .firstOrNull { it.startsWith("$name=") }
        ?.substringAfter("=")
        ?.trim()
        ?: ""
}

android {
    namespace = "dev.rootcause.cape"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.rootcause.cape"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        manifestPlaceholders["googleMapsApiKey"] = readEnvValue("GOOGLE_MAPS_API_KEY")
    }

    // compileOptions targets Android bytecode — must stay at 17 (Android max supported)
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // jvmToolchain tells Kotlin which JDK to use on this machine (Java 25 is installed)
    kotlin {
        jvmToolchain(17)
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.ui:ui:1.7.5")
    implementation("androidx.compose.material3:material3:1.3.1")
    implementation("androidx.compose.ui:ui-tooling-preview:1.7.5")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("com.google.android.gms:play-services-maps:19.0.0")
    debugImplementation("androidx.compose.ui:ui-tooling:1.7.5")
}
