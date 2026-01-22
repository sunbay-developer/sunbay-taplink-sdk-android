import java.text.SimpleDateFormat
import java.util.Date

plugins {
    alias(libs.plugins.android.application)
    kotlin("android") version "1.7.10"
}

android {
    namespace = "com.sunmi.tapro.taplink.demo"
    compileSdk = Integer.parseInt(libs.versions.compileSdk.get())

    defaultConfig {
        applicationId = "com.sunmi.tapro.taplink.demo"
        minSdk = Integer.parseInt(libs.versions.minSdk.get())
        targetSdk = Integer.parseInt(libs.versions.compileSdk.get())
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
        freeCompilerArgs += listOf("-Xskip-metadata-version-check")
    }

    buildFeatures {
        buildConfig = true
        aidl = true  // Enable AIDL support
    }

    applicationVariants.all {
        val variant = this
        outputs.all {
            val output = this as com.android.build.gradle.internal.api.BaseVariantOutputImpl
            val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss").format(Date())
            val newApkName = "TaplinkDemo-${variant.name}-${variant.versionName}-${timestamp}.apk"
            output.outputFileName = newApkName
        }
    }
}

dependencies {
    implementation(project(":lib_taplink_sdk"))
    // JSON processing
    implementation(libs.gson)
    implementation(libs.java.websocket)
    // Android core libraries
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)

    // Kotlin coroutines
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    // Lifecycle
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.billing)

    // Test
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit.v112)
    androidTestImplementation(libs.androidx.espresso.core.v330)
}

