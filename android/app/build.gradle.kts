import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jlleitschuh.gradle.ktlint")
    id("org.jetbrains.kotlinx.kover")
}

val keystoreProperties =
    Properties().apply {
        val file = rootProject.file("keystore.properties")
        if (file.exists()) file.inputStream().use { load(it) }
    }

fun signingValue(
    property: String,
    variable: String,
): String? = keystoreProperties.getProperty(property) ?: System.getenv(variable)

val releaseStoreFile = signingValue("storeFile", "KEYSTORE_FILE")
val hasReleaseKeystore = releaseStoreFile != null && rootProject.file(releaseStoreFile).exists()

android {
    namespace = "be.pascu.mapsforpebble"
    compileSdk = 37

    defaultConfig {
        applicationId = "be.pascu.mapsforpebble"
        minSdk = 26
        targetSdk = 36
        versionCode = 3
        versionName = "1.2.0"
    }

    signingConfigs {
        if (hasReleaseKeystore) {
            create("release") {
                storeFile = rootProject.file(releaseStoreFile!!)
                storePassword = signingValue("storePassword", "KEYSTORE_PASSWORD")
                keyAlias = signingValue("keyAlias", "KEY_ALIAS")
                keyPassword = signingValue("keyPassword", "KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName(if (hasReleaseKeystore) "release" else "debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

kotlin {
    jvmToolchain(21)
}

ktlint {
    version.set("1.8.0")
}

kover {
    reports {
        filters {
            excludes {
                classes(
                    "be.pascu.mapsforpebble.MainActivity*",
                    "be.pascu.mapsforpebble.MapsForPebbleApp",
                    "be.pascu.mapsforpebble.Navigator*",
                    "be.pascu.mapsforpebble.Preferences",
                    "be.pascu.mapsforpebble.service.*",
                    "be.pascu.mapsforpebble.pebble.WatchLink*",
                    "be.pascu.mapsforpebble.pebble.WatchListenerService",
                    "be.pascu.mapsforpebble.map.MapRenderer*",
                    "be.pascu.mapsforpebble.map.TileStore*",
                    "be.pascu.mapsforpebble.nav.GoogleMapsNotification*",
                    "be.pascu.mapsforpebble.nav.NotificationRead*",
                )
            }
        }
        verify {
            rule {
                minBound(100, coverageUnits = kotlinx.kover.gradle.plugin.dsl.CoverageUnit.LINE)
            }
            rule {
                minBound(100, coverageUnits = kotlinx.kover.gradle.plugin.dsl.CoverageUnit.INSTRUCTION)
            }
            rule {
                minBound(100, coverageUnits = kotlinx.kover.gradle.plugin.dsl.CoverageUnit.BRANCH)
            }
        }
    }
}

dependencies {
    implementation("io.rebble.pebblekit2:client:1.3.0")
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    testImplementation("junit:junit:4.13.2")
}
