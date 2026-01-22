# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# Keep Taplink SDK classes
-keep class com.sunmi.tapro.taplink.sdk.** { *; }
-keep class com.sunmi.tapro.taplink.demo.** { *; }

# Keep data classes
-keepclassmembers class * {
    @kotlinx.serialization.SerialName <fields>;
}

# Keep callbacks
-keep interface com.sunmi.tapro.taplink.sdk.api.callback.** { *; }

# Keep models
-keep class com.sunmi.tapro.taplink.sdk.api.model.** { *; }
-keep class com.sunmi.tapro.taplink.sdk.api.request.** { *; }
-keep class com.sunmi.tapro.taplink.sdk.api.config.** { *; }


