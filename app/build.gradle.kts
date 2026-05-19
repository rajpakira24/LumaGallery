import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

val localProperties = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val unityGameId: String = localProperties.getProperty("UNITY_GAME_ID", "")
val unityBannerPlacementId: String = localProperties.getProperty("UNITY_BANNER_PLACEMENT_ID", "")
val unityRewardedPlacementId: String = localProperties.getProperty("UNITY_REWARDED_PLACEMENT_ID", "")
val geminiApiKey: String = localProperties.getProperty("GEMINI_API_KEY", "")
val dashscopeApiKey: String = localProperties.getProperty("DASHSCOPE_API_KEY", "")

android {
    namespace = "com.webstudio.lumagallery"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.webstudio.lumagallery"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        vectorDrawables {
            useSupportLibrary = true
        }
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "UNITY_GAME_ID", "\"$unityGameId\"")
        buildConfigField("String", "UNITY_BANNER_PLACEMENT_ID", "\"$unityBannerPlacementId\"")
        buildConfigField("String", "UNITY_REWARDED_PLACEMENT_ID", "\"$unityRewardedPlacementId\"")
        buildConfigField("String", "GEMINI_API_KEY", "\"$geminiApiKey\"")
        buildConfigField("String", "DASHSCOPE_API_KEY", "\"$dashscopeApiKey\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // ViewModel
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    // Image & Video Loading - Coil
    implementation(libs.coil.compose)
    implementation(libs.coil.video)

    // ExoPlayer for video playback
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.common)

    // Accompanist (for permissions and system UI)
    implementation(libs.accompanist.permissions)
    implementation(libs.accompanist.systemuicontroller)

    // Lottie for animations
    implementation(libs.lottie.compose)

    // Zoomable for pinch-to-zoom
    implementation(libs.telephoto.zoomable.image.coil)

    // Unity Ads — banner + rewarded ads
    implementation(libs.unity.ads)

    // ML Kit on-device Selfie Segmentation (background removal)
    implementation(libs.mlkit.segmentation.selfie)

    // uCrop — crop/rotate/flip UI
    implementation(libs.ucrop)

    // Retrofit + OkHttp + kotlinx-serialization (cloud AI APIs)
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.kotlinx.serialization.json)

    //Test & Debug
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
