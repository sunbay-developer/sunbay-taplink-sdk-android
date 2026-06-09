// Top-level build file where you can add configuration options common to all sub-projects/modules.
buildscript {
    repositories {
        // Use AliCloud mirror for better access in China
        maven("https://maven.aliyun.com/repository/public")
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/gradle-plugin")
        
        // Fallback repositories
        mavenCentral()
        google()
        gradlePluginPortal()
    }
    dependencies {
        // Only keep fat-aar plugin here, Kotlin plugin is applied via plugins DSL in submodules
        classpath(libs.fat)
    }
}
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
}

// Force Kotlin stdlib version to 1.7.10 for all subprojects
// This ensures compatibility and prevents enum class issues when AAR is used in projects with older Kotlin versions
allprojects {
    configurations.all {
        resolutionStrategy {
            // Force Kotlin standard library versions to match Kotlin plugin version 1.7.10
            force("org.jetbrains.kotlin:kotlin-stdlib:1.7.10")
            force("org.jetbrains.kotlin:kotlin-stdlib-common:1.7.10")
            force("org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.7.10")
            force("org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.7.10")
            
            // Force Kotlin annotations to compatible version
            force("org.jetbrains:annotations:13.0")
            
            // Force kotlinx-coroutines to version 1.7.1 (compatible with Kotlin 1.7.10)
            force("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.1")
            force("org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.7.1")
            force("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.1")
            
            // Force AndroidX Lifecycle to compatible version (2.6.2 is compatible with Kotlin 1.7)
            force("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2")
            force("androidx.lifecycle:lifecycle-runtime:2.6.2")
            force("androidx.lifecycle:lifecycle-common:2.6.2")
            force("androidx.lifecycle:lifecycle-common-jvm:2.6.2")
            force("androidx.lifecycle:lifecycle-process:2.6.2")
            force("androidx.lifecycle:lifecycle-livedata:2.6.2")
            force("androidx.lifecycle:lifecycle-livedata-core:2.6.2")
            force("androidx.lifecycle:lifecycle-viewmodel:2.6.2")
            force("androidx.lifecycle:lifecycle-viewmodel-savedstate:2.6.2")
            force("androidx.savedstate:savedstate:1.2.1")
        }
    }
}