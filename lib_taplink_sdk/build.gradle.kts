plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    id("com.kezong.fat-aar")
}

// ==================== 常量配置 ====================
object SdkVersion {
    const val CODE = 6

    //    const val NAME = "1.0.3"
    const val NAME = "1.0.7.14"
}

object BuildConfig {
    const val MODULE_NAME = "lib_taplink_sdk"
    const val SDK_NAME = "taplink-sdk"
    const val OUTPUT_DIR = "../build"
    val JAVA_VERSION = JavaVersion.VERSION_11
}

// 设置版本信息供Groovy脚本访问
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

        // 设置版本信息到BuildConfig
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
    }

    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    // AndroidX核心库
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)

    // 内部模块依赖
    embed(project(":lib_taplink_communication"))

    // 第三方库
    implementation(libs.gson)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    // 测试依赖
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit.v112)
    androidTestImplementation(libs.androidx.espresso.core.v330)
}

// ==================== 自定义任务配置 ====================
/**
 * 创建AAR拷贝任务
 *
 * @param buildType 构建类型 (debug/release)
 * @param taskName 任务名称
 */
fun createCopyAarTask(buildType: String, taskName: String) {
    tasks.register<Copy>(taskName) {
        description = "拷贝并重命名${buildType} AAR文件"
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

// 创建debug和release的拷贝任务
createCopyAarTask("debug", "copyDebugAar")
createCopyAarTask("release", "copyReleaseAar")

// 自动执行拷贝任务
afterEvaluate {
    listOf("debug", "release").forEach { buildType ->
        val assembleTask = "assemble${buildType.replaceFirstChar { it.uppercaseChar() }}"
        val copyTask = "copy${buildType.replaceFirstChar { it.uppercaseChar() }}Aar"

        tasks.named(assembleTask) {
            finalizedBy(copyTask)
        }
    }
}

apply(from = "./sdk_publish.gradle")