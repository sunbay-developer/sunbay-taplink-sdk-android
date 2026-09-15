# TapLink's dependency-free JSON bridge reads request/config backing fields reflectively.
# Preserve those fields when an integrating application enables R8/ProGuard.
-keepclassmembers class com.sunmi.tapro.taplink.sdk.model.** {
    <fields>;
}
-keepclassmembers class com.sunmi.tapro.taplink.sdk.config.ConnectionConfig {
    <fields>;
}
