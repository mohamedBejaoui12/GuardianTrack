import java.util.Properties

// Load local.properties for the API base URL (never committed to git)
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) load(f.inputStream())
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.hilt)
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.0"
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.guardian.track"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.guardian.track"
        minSdk = 26          // Android 8.0 — required by spec
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        // Inject API base URL from local.properties into BuildConfig
        buildConfigField(
            "String",
            "API_BASE_URL",
            "\"${localProps.getProperty("api.base.url", "https://your-mock-api.mockapi.io/api/v1/")}\""
        )
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = libs.versions.composeCompiler.get()
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(libs.core.ktx)
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.constraintlayout)


    implementation("androidx.activity:activity-compose:1.9.0")

    // Compose BOM
    implementation(platform(libs.compose.bom))

    // Compose core
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.runtime)

    // Tooling
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
    // Lifecycle
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.lifecycle.runtime)
    //Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")



    // Navigation
    implementation(libs.navigation.fragment)
    implementation(libs.navigation.ui)

    // Room — KSP generates the DAO implementations
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // Hilt — KSP generates the DI glue code
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.work)

    // Retrofit + OkHttp
    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)
    implementation(libs.okhttp.logging)

    // Coroutines
    implementation(libs.coroutines.android)


    implementation(libs.recyclerview)

    // DataStore
    implementation(libs.datastore)

    // WorkManager
    implementation(libs.workmanager)

    // Security (EncryptedSharedPreferences)
    implementation(libs.security.crypto)

    // Location
    implementation(libs.play.services.location)
}
