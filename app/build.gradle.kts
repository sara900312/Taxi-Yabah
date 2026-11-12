import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.roborazzi)
}

// ---- Build-time switches (all default to the SAFE value) -------------------------------------------
// Phone OTP sign-in: off until the Supabase Phone provider and an SMS vendor are enabled by the owner.
val phoneOtpEnabled = providers.gradleProperty("yabah.phoneOtp").orNull == "true"
// Native Mapbox: only when the build machine holds a Mapbox DOWNLOADS:READ secret (see settings.gradle.kts).
val mapboxDownloadsToken: String? =
    providers.gradleProperty("MAPBOX_DOWNLOADS_TOKEN").orNull
        ?: providers.environmentVariable("MAPBOX_DOWNLOADS_TOKEN").orNull
val mapboxNative = !mapboxDownloadsToken.isNullOrBlank()
val mapboxVersion = providers.gradleProperty("yabah.mapboxVersion").orNull ?: "11.18.0"
// Supabase client configuration. The publishable key is designed to ship in clients (RLS applies);
// a service-role / secret key must NEVER be placed here.
val supabaseUrl = providers.gradleProperty("yabah.supabaseUrl").orNull
    ?: "https://ifawmzjggjrgboufrxzk.supabase.co"
val supabasePublishableKey = providers.gradleProperty("yabah.supabasePublishableKey").orNull
    ?: "sb_publishable_IicpBVFURMAY1mRsTzhASA_KCaYJJDL"
require(!supabasePublishableKey.startsWith("sb_secret_")) { "Refusing to package a Supabase SECRET key" }

android {
    namespace = "com.yabah.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.yabah.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0-native"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "SUPABASE_URL", "\"$supabaseUrl\"")
        buildConfigField("String", "SUPABASE_PUBLISHABLE_KEY", "\"$supabasePublishableKey\"")
        buildConfigField("boolean", "PHONE_OTP_ENABLED", phoneOtpEnabled.toString())
        buildConfigField("boolean", "MAPBOX_NATIVE", mapboxNative.toString())
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    sourceSets {
        getByName("main") {
            // Exactly one MapSurface implementation is compiled in.
            kotlin.srcDir(if (mapboxNative) "src/mapbox/kotlin" else "src/demomap/kotlin")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all {
                it.systemProperty("robolectric.graphicsMode", "NATIVE")
                it.systemProperty("robolectric.pixelCopyRenderMode", "hardware")
                it.maxHeapSize = "3g"
            }
        }
    }
    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "/META-INF/LICENSE*")
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        freeCompilerArgs.add("-opt-in=androidx.compose.material3.ExperimentalMaterial3Api")
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.core)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)

    if (mapboxNative) {
        implementation("com.mapbox.maps:android-ndk27:$mapboxVersion")
        implementation("com.mapbox.extension:maps-compose-ndk27:$mapboxVersion")
    }

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.junit.rule)
    debugImplementation(libs.compose.ui.test.manifest)
}
