plugins {
    alias(libs.plugins.android.library)
    kotlin("android") version "1.7.10"
    id("com.kezong.fat-aar")
}

// ==================== Constants Configuration ====================
object SdkVersion {
    const val CODE = 6

        const val NAME = "1.0.4"
//    const val NAME = "1.0.7.14"
}

object BuildConfig {
    const val MODULE_NAME = "lib_taplink_sdk"
    const val SDK_NAME = "taplink-sdk"
    const val OUTPUT_DIR = "../build"
    val JAVA_VERSION = JavaVersion.VERSION_11
}

// Set version information for Groovy script access
extra.apply {
    set("versionName", SdkVersion.NAME)
    set("versionCode", SdkVersion.CODE)
}

android {
    namespace = "com.sunmi.tapro.taplink.sdk"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")

        // Set version information to BuildConfig
        buildConfigField("String", "VERSION_NAME", "\"${SdkVersion.NAME}\"")
        buildConfigField("int", "VERSION_CODE", "${SdkVersion.CODE}")
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
        sourceCompatibility = BuildConfig.JAVA_VERSION
        targetCompatibility = BuildConfig.JAVA_VERSION
    }

    kotlinOptions {
        jvmTarget = BuildConfig.JAVA_VERSION.toString()
        freeCompilerArgs += listOf("-Xskip-metadata-version-check")
    }

    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    // Explicitly declare Kotlin stdlib to ensure version 1.7.10
    // Note: Root build.gradle.kts also forces this version via resolutionStrategy,
    // but explicit declaration ensures IDE recognition and makes dependencies clear
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.7.10")
    
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)

    embed(project(":lib_taplink_communication"))

    implementation(libs.gson)
    
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

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit.v112)
    androidTestImplementation(libs.androidx.espresso.core.v330)
}

fun createCopyAarTask(buildType: String, taskName: String) {
    tasks.register<Copy>(taskName) {
        description = "Copy and rename ${buildType} AAR file"
        group = "build"

        val sourceAar = "${BuildConfig.MODULE_NAME}-${buildType}.aar"
        val targetAar = "sunbay-taplink-sdk-android-${SdkVersion.NAME}-${buildType}.aar"

        from(layout.buildDirectory.dir("outputs/aar")) {
            include(sourceAar)
        }

        into(BuildConfig.OUTPUT_DIR)
        rename(sourceAar, targetAar)

        dependsOn("assemble${buildType.replaceFirstChar { it.uppercaseChar() }}")
    }
}

createCopyAarTask("debug", "copyDebugAar")
createCopyAarTask("release", "copyReleaseAar")

afterEvaluate {
    listOf("debug", "release").forEach { buildType ->
        val assembleTask = "assemble${buildType.replaceFirstChar { it.uppercaseChar() }}"
        val copyTask = "copy${buildType.replaceFirstChar { it.uppercaseChar() }}Aar"

        tasks.named(assembleTask) {
            finalizedBy(copyTask)
        }
    }
}