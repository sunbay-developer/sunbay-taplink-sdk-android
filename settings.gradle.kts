pluginManagement {
    repositories {
        // Use AliCloud mirror for better access in China (uncomment if needed)
        // maven("https://maven.aliyun.com/repository/public")
        // maven("https://maven.aliyun.com/repository/google")
        // maven("https://maven.aliyun.com/repository/gradle-plugin")
        
        // Prioritize Maven Central for Kotlin plugins
        mavenCentral()
        gradlePluginPortal()
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        maven("https://jitpack.io")
        // Add JetBrains repository as fallback
        maven("https://maven.pkg.jetbrains.space/public/p/com/android/dev")
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        maven("https://jitpack.io")
        mavenCentral()
    }
}

rootProject.name = "Taplink_SDK"
include(":app")
include(":lib_taplink_sdk")
include(":lib_taplink_communication")
