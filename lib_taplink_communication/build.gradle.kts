plugins {
    alias(libs.plugins.android.library)
    kotlin("android") version "1.7.10"
    id("com.kezong.fat-aar")
}

android {
    namespace = "com.sunmi.tapro.taplink.communication"
    compileSdk = Integer.parseInt(libs.versions.compileSdk.get())

    defaultConfig {
        minSdk = Integer.parseInt(libs.versions.minSdk.get())

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
        freeCompilerArgs += listOf("-Xskip-metadata-version-check")
    }

    buildFeatures {
        buildConfig = true
        aidl = true
    }
}

dependencies {
    // Explicitly declare Kotlin stdlib to ensure version 1.7.10
    // Note: Root build.gradle.kts also forces this version via resolutionStrategy,
    // but explicit declaration ensures IDE recognition and makes dependencies clear
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.7.10")
    
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    
    // Exclude transitive kotlin-stdlib from coroutines to use our forced version
    implementation(libs.kotlinx.coroutines.core) {
        exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib")
        exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib-jdk7")
        exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib-jdk8")
    }
    implementation(libs.kotlinx.coroutines.android) {
        exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib")
        exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib-jdk7")
        exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib-jdk8")
    }
    
    implementation(libs.gson)
    implementation(libs.java.websocket)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.usb.serial.android)
    implementation(fileTree("libs") { include("*.jar") })
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit.v112)
    androidTestImplementation(libs.androidx.espresso.core.v330)
}
