// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.android.library) apply false
}

allprojects {
    configurations.all {
        resolutionStrategy {
            force("org.jetbrains.kotlin:kotlin-stdlib:1.7.10")
            force("org.jetbrains.kotlin:kotlin-stdlib-common:1.7.10")
            force("org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.7.10")
            force("org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.7.10")
        }
    }
}