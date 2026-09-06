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

// The version lives in version.properties at the repo root so that CI has exactly
// one line to rewrite when it bumps a release (see .github/workflows/release.yml).
val versionProperties = Properties().apply {
    val file = rootProject.file("version.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

val appVersionName: String = versionProperties.getProperty("versionName")?.trim()
    ?: error("versionName missing from version.properties")

// versionCode is derived from versionName (1.2 -> 10200, 1.3.1 -> 10301) so it can
// never collide or go backwards — Android rejects installs whose code decreases.
val appVersionCode: Int = appVersionName.split(".").let { parts ->
    val major = parts.getOrNull(0)?.trim()?.toIntOrNull() ?: 0
    val minor = parts.getOrNull(1)?.trim()?.toIntOrNull() ?: 0
    val patch = parts.getOrNull(2)?.trim()?.toIntOrNull() ?: 0
    major * 10000 + minor * 100 + patch
}

android {
    namespace = "com.example.dailyfocus"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.example.dailyfocus"
        minSdk = 24
        targetSdk = 36
        // Both come from version.properties, bumped automatically by CI — do not
        // hardcode them here.
        versionCode = appVersionCode
        versionName = appVersionName

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