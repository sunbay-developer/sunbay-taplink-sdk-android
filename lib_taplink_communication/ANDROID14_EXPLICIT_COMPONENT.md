# Android 14 显式组件绑定方案

## 问题背景

在 Android 14 上，即使使用了所有推荐的标志，服务仍然可能无法启动。这可能是因为：

1. 隐式 Intent（通过 action）在 Android 14 上受到更严格的限制
2. 目标进程未运行时，系统可能拒绝启动
3. 后台启动限制更加严格

## 解决方案：使用显式组件名

### 方案 1: 先启动应用，再绑定服务（当前实现）

```kotlin
// 1. 先启动目标应用的主 Activity
val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
if (launchIntent != null) {
    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    launchIntent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
    context.startActivity(launchIntent)
    
    // 等待进程启动
    Thread.sleep(500)
}

// 2. 绑定服务
val bindResult = context.bindService(intent, serviceConnection, bindFlags)
```

**优点：**
- ✅ 确保目标进程已启动
- ✅ 避免 "process is bad" 问题
- ✅ 兼容性好

**缺点：**
- ⚠️ 会短暂显示目标应用（虽然使用了 NO_ANIMATION）
- ⚠️ 需要等待时间

---

### 方案 2: 使用显式组件名（推荐）

```kotlin
// 不使用 action，直接指定组件
val intent = Intent()
intent.component = ComponentName(
    packageName,
    "com.sunmi.tapro.taplink.service.local.LocalService"  // 完整的服务类名
)
intent.putExtra("app_id", appId)
intent.putExtra("secret_key", appSecretKey)

val bindResult = context.bindService(intent, serviceConnection, bindFlags)
```

**优点：**
- ✅ 更直接，不依赖 intent-filter
- ✅ 避免隐式 Intent 的限制
- ✅ 不需要启动 Activity
- ✅ 更快

**缺点：**
- ⚠️ 需要知道服务的完整类名
- ⚠️ 如果服务类名改变，需要更新

---

### 方案 3: 混合方案（最推荐）

先尝试显式组件名，失败后再尝试隐式 Intent：

```kotlin
fun performConnect(parseResult: ProtocolParseResult, connectionCallback: ConnectionCallback) {
    val localProtocol = parseResult as ProtocolParseResult.LocalProtocol
    val packageName = localProtocol.packageName
    val action = localProtocol.action
    
    // 尝试 1: 使用显式组件名
    val explicitIntent = Intent()
    explicitIntent.component = ComponentName(
        packageName,
        "$packageName.taplink.service.local.LocalService"
    )
    explicitIntent.putExtra("app_id", appId)
    explicitIntent.putExtra("secret_key", appSecretKey)
    
    var bindResult = tryBindService(context, explicitIntent, serviceConnection, bindFlags)
    
    if (!bindResult) {
        LogUtil.w(TAG, "Explicit component binding failed, trying implicit intent...")
        
        // 尝试 2: 使用隐式 Intent（通过 action）
        val implicitIntent = Intent(action)
        implicitIntent.setPackage(packageName)
        implicitIntent.putExtra("app_id", appId)
        implicitIntent.putExtra("secret_key", appSecretKey)
        
        bindResult = tryBindService(context, implicitIntent, serviceConnection, bindFlags)
    }
    
    if (!bindResult) {
        notifyConnectionError("All binding attempts failed", InnerErrorCode.C25)
    }
}
```

---

## 实现代码

### 修改 LocalServiceKernel

```kotlin
override fun performConnect(parseResult: ProtocolParseResult, connectionCallback: ConnectionCallback) {
    val localProtocol = parseResult as ProtocolParseResult.LocalProtocol
    val packageName = localProtocol.packageName
    val action = localProtocol.action

    LogUtil.d(TAG, "=== Starting Local Service Connection ===")
    LogUtil.d(TAG, "Target package: $packageName")
    LogUtil.d(TAG, "Service action: $action")

    val context = TaplinkServiceKernel.getInstance()?.getContext()
    if (context == null) {
        LogUtil.e(TAG, "Application context is null")
        notifyConnectionError("Application context is null", InnerErrorCode.C20)
        return
    }

    // 检查目标应用是否安装
    try {
        val packageInfo = context.packageManager.getPackageInfo(packageName, 0)
        LogUtil.d(TAG, "Target app installed: version=${packageInfo.versionName}")
    } catch (e: Exception) {
        LogUtil.e(TAG, "Target app not installed: ${e.message}")
        notifyConnectionError("Target app not installed: $packageName", InnerErrorCode.C22)
        return
    }

    // 创建服务连接
    serviceConnection = createServiceConnection(connectionCallback)

    // 策略 1: 尝试使用显式组件名
    LogUtil.d(TAG, "Strategy 1: Trying explicit component binding...")
    val explicitIntent = Intent()
    explicitIntent.component = ComponentName(
        packageName,
        "$packageName.taplink.service.local.LocalService"
    )
    explicitIntent.putExtra("app_id", appId)
    explicitIntent.putExtra("secret_key", appSecretKey)
    
    var bindResult = tryBindService(context, explicitIntent, serviceConnection!!)
    
    if (!bindResult) {
        LogUtil.w(TAG, "Explicit component binding failed")
        
        // 策略 2: 先启动应用，再使用隐式 Intent
        LogUtil.d(TAG, "Strategy 2: Waking up target app first...")
        wakeUpTargetApp(context, packageName)
        
        // 使用隐式 Intent
        val implicitIntent = Intent(action)
        implicitIntent.setPackage(packageName)
        implicitIntent.putExtra("app_id", appId)
        implicitIntent.putExtra("secret_key", appSecretKey)
        
        bindResult = tryBindService(context, implicitIntent, serviceConnection!!)
    }
    
    if (!bindResult) {
        LogUtil.e(TAG, "All binding strategies failed")
        notifyConnectionError("Failed to bind service", InnerErrorCode.C25)
    } else {
        currentIntent = if (bindResult) explicitIntent else null
        LogUtil.d(TAG, "Binding initiated, waiting for callbacks...")
    }
    
    LogUtil.d(TAG, "=== Connection attempt completed ===")
}

/**
 * 尝试绑定服务
 */
private fun tryBindService(
    context: Context,
    intent: Intent,
    serviceConnection: ServiceConnection
): Boolean {
    return try {
        val bindFlags = Context.BIND_AUTO_CREATE or 
                       Context.BIND_IMPORTANT or 
                       Context.BIND_ABOVE_CLIENT
        
        LogUtil.d(TAG, "Binding with intent: ${intent.component ?: intent.action}")
        LogUtil.d(TAG, "Bind flags: 0x${bindFlags.toString(16)}")
        
        val result = context.bindService(intent, serviceConnection, bindFlags)
        LogUtil.d(TAG, "bindService() returned: $result")
        result
    } catch (e: Exception) {
        LogUtil.e(TAG, "Exception during bindService(): ${e.message}", e)
        false
    }
}

/**
 * 唤醒目标应用（启动主 Activity）
 */
private fun wakeUpTargetApp(context: Context, packageName: String) {
    try {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
            
            context.startActivity(launchIntent)
            LogUtil.d(TAG, "Target app launch intent sent")
            
            // 等待进程启动
            Thread.sleep(500)
        } else {
            LogUtil.w(TAG, "No launch intent found for: $packageName")
        }
    } catch (e: Exception) {
        LogUtil.w(TAG, "Failed to wake up target app: ${e.message}")
    }
}
```

---

## 目标服务端配置

确保服务可以通过显式组件名访问：

```xml
<service
    android:name=".taplink.service.local.LocalService"
    android:enabled="true"
    android:exported="true">
    <!-- 保留 intent-filter 以支持隐式 Intent -->
    <intent-filter>
        <action android:name="com.sunmi.tapro.taplink.service.local.ACTION_TRANSACTION" />
    </intent-filter>
</service>
```

**注意：**
- `android:exported="true"` 是必须的
- 服务类名必须与代码中的一致
- 可以同时支持显式和隐式两种方式

---

## 测试验证

### 1. 测试显式组件名

```bash
# 使用 adb 测试显式绑定
adb shell am start-service \
  -n com.sunmi.tapro/.taplink.service.local.LocalService \
  --es app_id "test_app_id" \
  --es secret_key "test_secret"
```

### 2. 测试隐式 Intent

```bash
# 使用 adb 测试隐式绑定
adb shell am start-service \
  -a com.sunmi.tapro.taplink.service.local.ACTION_TRANSACTION \
  -p com.sunmi.tapro \
  --es app_id "test_app_id" \
  --es secret_key "test_secret"
```

### 3. 查看日志

```bash
adb logcat | grep -E "LocalServiceKernel|LocalService|ActivityManager"
```

---

## 优先级建议

对于 Android 14，推荐的策略优先级：

1. **首选：显式组件名** - 最直接，最可靠
2. **备选：先启动应用 + 隐式 Intent** - 兼容性好
3. **最后：纯隐式 Intent** - 可能在 Android 14 上失败

---

## 常见问题

### Q: 如何获取服务的完整类名？

**A:** 有几种方法：

1. 查看目标应用的 AndroidManifest.xml
2. 使用 adb 查询：
   ```bash
   adb shell dumpsys package com.sunmi.tapro | grep "Service"
   ```
3. 联系目标应用开发者

### Q: 显式组件名会不会失效？

**A:** 
- 如果服务类名不变，不会失效
- 建议目标应用提供稳定的服务类名
- 可以在协议中传递服务类名

### Q: 为什么要先启动 Activity？

**A:**
- 确保目标进程已运行
- 避免 "process is bad" 状态
- 提高绑定成功率

---

**文档版本**: 1.0.0  
**更新日期**: 2025-12-17  
**作者**: TaPro Team
