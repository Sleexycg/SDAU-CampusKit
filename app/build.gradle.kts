import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use(::load)
    }
}

android {
    namespace = "com.sdau.campuskit"
    compileSdk = 36
    buildToolsVersion = "34.0.0-rc1"

    defaultConfig {
        applicationId = "com.sdau.campuskit"
        minSdk = 26
        targetSdk = 34
        versionCode = 10
        versionName = "0.3.7 beta"
    }

    signingConfigs {
        if (keystorePropertiesFile.exists()) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
                enableV4Signing = true
            }
        }
    }

    buildTypes {
        release {
            signingConfigs.findByName("release")?.let { signingConfig = it }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        compose = true
    }

    lint {
        abortOnError = false
    }

}

tasks.configureEach {
    if (name == "checkDebugAarMetadata" || name == "checkReleaseAarMetadata") {
        enabled = false
    }
}

configurations.all {
    resolutionStrategy {
        val compose = "1.7.3"
        force(
            "androidx.compose.ui:ui:$compose",
            "androidx.compose.ui:ui-android:$compose",
            "androidx.compose.ui:ui-graphics:$compose",
            "androidx.compose.ui:ui-graphics-android:$compose",
            "androidx.compose.ui:ui-text:$compose",
            "androidx.compose.ui:ui-text-android:$compose",
            "androidx.compose.ui:ui-unit:$compose",
            "androidx.compose.ui:ui-unit-android:$compose",
            "androidx.compose.ui:ui-geometry:$compose",
            "androidx.compose.ui:ui-geometry-android:$compose",
            "androidx.compose.ui:ui-util:$compose",
            "androidx.compose.ui:ui-util-android:$compose",
            "androidx.compose.runtime:runtime:$compose",
            "androidx.compose.runtime:runtime-android:$compose",
            "androidx.compose.runtime:runtime-saveable:$compose",
            "androidx.compose.runtime:runtime-saveable-android:$compose",
            "androidx.compose.foundation:foundation:$compose",
            "androidx.compose.foundation:foundation-android:$compose",
            "androidx.compose.foundation:foundation-layout:$compose",
            "androidx.compose.foundation:foundation-layout-android:$compose",
            "androidx.compose.animation:animation:$compose",
            "androidx.compose.animation:animation-android:$compose",
            "androidx.compose.animation:animation-core:$compose",
            "androidx.compose.animation:animation-core-android:$compose",
            "androidx.core:core-ktx:1.13.0",
            "androidx.core:core:1.13.0"
        )
    }
}

dependencies {
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.core:core-ktx:1.13.0")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("org.jetbrains.compose.foundation:foundation:1.7.3")
    implementation("org.jetbrains.compose.ui:ui:1.7.3")
    implementation("io.github.kyant0:backdrop:2.0.1")
    implementation("io.github.kyant0:shapes:1.2.1")
}
