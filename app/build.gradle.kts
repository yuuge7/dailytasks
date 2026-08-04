import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

// Signing material is read from `keystore.properties` (local builds, git-ignored) or
// from environment variables (CI). If neither is present, release builds stay unsigned
// so that contributors can build the project without owning the release key.
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

fun signingValue(key: String, envName: String): String? =
    keystoreProperties.getProperty(key) ?: System.getenv(envName)

val releaseStoreFile: String? = signingValue("storeFile", "KEYSTORE_FILE")

android {
    namespace = "com.example.dailyfocus"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.example.dailyfocus"
        minSdk = 24
        targetSdk = 36
        // versionName drives the release tag and title published by the CI workflow
        // (.github/workflows/release.yml) — bump it to cut a new release.
        versionCode = 4
        versionName = "1.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            if (releaseStoreFile != null) {
                storeFile = rootProject.file(releaseStoreFile)
                storePassword = signingValue("storePassword", "KEYSTORE_PASSWORD")
                keyAlias = signingValue("keyAlias", "KEY_ALIAS")
                keyPassword = signingValue("keyPassword", "KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (releaseStoreFile != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    implementation(libs.appcompat.v161)
    implementation(libs.material.v190)
    implementation(libs.constraintlayout.v214)
    implementation("androidx.room:room-runtime:2.8.4")
    implementation("com.google.code.gson:gson:2.10.1")
    annotationProcessor("androidx.room:room-compiler:2.8.4")
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}