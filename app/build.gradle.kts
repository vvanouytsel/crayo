plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Changed automatically by the release process; do not edit by hand.
val appVersion = "1.0.0" // x-release-please-version
// 1.2.3 becomes 10203, so every new release counts as newer to Android.
val appVersionCode = appVersion.split(".").map { it.toInt() }.let { (major, minor, patch) -> major * 10000 + minor * 100 + patch }

android {
    namespace = "app.crayo"
    compileSdk = 37

    defaultConfig {
        applicationId = "app.crayo"
        minSdk = 26
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersion
    }

    // The signing key lives outside the project (see the README); without it a release build stays unsigned.
    signingConfigs {
        create("release") {
            val keystorePath = providers.gradleProperty("crayoKeystore").orNull
            if (keystorePath != null) {
                val password = providers.gradleProperty("crayoKeystorePassword").get()
                storeFile = file(keystorePath)
                storePassword = password
                keyAlias = "crayo"
                keyPassword = password
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.09.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
    implementation("com.squareup.okhttp3:okhttp:5.5.0")
    implementation("io.coil-kt.coil3:coil-compose:3.6.3")
}
