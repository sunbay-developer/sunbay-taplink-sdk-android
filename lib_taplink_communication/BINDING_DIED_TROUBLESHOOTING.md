# Service Binding Died 问题排查指南

## 问题现象

```
OnBindingDied: Service binding died: ComponentInfo{com.sunmi.tapro/com.sunmi.tapro.taplink.service.local.LocalService}
```

## 可能的原因

### 1. 目标服务进程崩溃 ⭐ 最常见

**现象：**
- `bindService()` 返回 `true`
- 但立即触发 `onBindingDied()`
- 没有调用 `onServiceConnected()`

**原因：**
- 服务的 `onCreate()` 或 `onBind()` 方法中抛出异常
- 服务进程启动后立即崩溃
- AIDL 接口实现有问题

**排查方法：**
```bash
# 查看目标服务的崩溃日志
adb logcat | grep -E "AndroidRuntime|FATAL|LocalService"

# 查看服务进程状态
adb shell ps -A | grep com.sunmi.tapro
```

---

### 2. 目标服务返回 null Binder

**现象：**
- 服务的 `onBind()` 返回 `null`
- 触发 `onNullBinding()` 或 `onBindingDied()`

**原因：**
```kotlin
// ❌ 错误：返回 null
override fun onBind(intent: Intent?): IBinder? {
    return null  // 这会导致绑定失败
}
```

**解决方案：**
```kotlin
// ✅ 正确：返回有效的 Binder
override fun onBind(intent: Intent?): IBinder {
    return binder  // 必须返回非 null 的 Binder
}
```

---

### 3. 目标服务未正确声明

**现象：**
- `queryIntentServices()` 能找到服务
- 但绑定后立即失败

**检查 AndroidManifest.xml：**
```xml
<service
    android:name=".taplink.service.local.LocalService"
    android:enabled="true"
    android:exported="true">  <!-- 必须是 true -->
    <intent-filter>
        <action android:name="com.sunmi.tapro.taplink.service.local.ACTION_TRANSACTION" />
    </intent-filter>
</service>
```

**常见错误：**
- ❌ `android:exported="false"` - 其他应用无法绑定
- ❌ 缺少 `<intent-filter>` - 无法通过 action 找到服务
- ❌ 服务类名错误 - 找不到服务类

---

### 4. AIDL 接口不匹配

**现象：**
- 服务启动成功
- 但 `ITransaction.Stub.asInterface(binder)` 失败

**原因：**
- 客户端和服务端的 AIDL 文件不一致
- AIDL 包名不匹配
- AIDL 方法签名不同

**检查：**
```bash
# 检查客户端的 AIDL
ls -la lib_taplink_communication/src/main/aidl/

# 检查服务端的 AIDL（目标应用）
# 确保两边的 AIDL 文件完全一致
```

---

### 5. 权限问题

**现象：**
- Android 14+ 上出现
- 其他版本正常

**可能的权限问题：**
```xml
<!-- 目标服务可能需要权限 -->
<service
    android:name=".LocalService"
    android:permission="android.permission.BIND_SERVICE"
    android:exported="true">
</service>
```

**检查客户端是否有权限：**
```xml
<uses-permission android:name="android.permission.BIND_SERVICE" />
```

---

### 6. 进程处于异常状态

**现象：**
- 之前的服务崩溃了
- 系统标记进程为 "bad"
- 新的绑定请求失败

**解决方案：**
```bash
# 强制停止目标应用，清理异常状态
adb shell am force-stop com.sunmi.tapro

# 等待几秒后重试
# 或者重启设备
```

---

## 诊断步骤

### 步骤 1: 检查目标服务是否崩溃

```bash
# 清除日志
adb logcat -c

# 尝试连接
# 在你的应用中调用 connect()

# 查看崩溃日志
adb logcat | grep -E "AndroidRuntime|FATAL|LocalService|com.sunmi.tapro"
```

**如果看到崩溃日志，说明是服务端问题。**

---

### 步骤 2: 检查服务声明

```bash
# 查看目标应用的 Manifest
adb shell dumpsys package com.sunmi.tapro | grep -A 20 "Service"

# 检查服务是否正确声明
# 检查 exported 是否为 true
# 检查 intent-filter 是否正确
```

---

### 步骤 3: 检查 AIDL 接口

在目标服务的 `onBind()` 中添加日志：

```kotlin
class LocalService : Service() {
    
    private val TAG = "LocalService"
    
    private val binder = object : ITransaction.Stub() {
        override fun onTransaction(requestData: String?, callback: ITransactionCallback?) {
            Log.d(TAG, "onTransaction called")
            // ...
        }
        
        override fun getExtraInfos(): MutableMap<String, String> {
            Log.d(TAG, "getExtraInfos called")
            return mutableMapOf()
        }
    }
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service onCreate")
    }
    
    override fun onBind(intent: Intent?): IBinder? {
        Log.d(TAG, "Service onBind: $intent")
        Log.d(TAG, "Returning binder: $binder")
        return binder
    }
}
```

**查看日志：**
```bash
adb logcat | grep LocalService
```

**如果看到 "Service onCreate" 但没有 "Service onBind"，说明绑定请求没有到达服务。**

---

### 步骤 4: 验证绑定标志

尝试不同的标志组合：

```kotlin
// 方案 1: 最简单的标志
val bindFlags = Context.BIND_AUTO_CREATE
context.bindService(intent, serviceConnection, bindFlags)

// 方案 2: 添加 BIND_DEBUG_UNBIND（调试用）
val bindFlags = Context.BIND_AUTO_CREATE or Context.BIND_DEBUG_UNBIND
context.bindService(intent, serviceConnection, bindFlags)

// 方案 3: 当前使用的标志
val bindFlags = Context.BIND_AUTO_CREATE or 
               Context.BIND_IMPORTANT or 
               Context.BIND_ABOVE_CLIENT
context.bindService(intent, serviceConnection, bindFlags)
```

---

### 步骤 5: 检查进程状态

```bash
# 查看目标进程是否存在
adb shell ps -A | grep com.sunmi.tapro

# 如果进程存在但绑定失败，可能是进程异常
# 强制停止并重试
adb shell am force-stop com.sunmi.tapro
```

---

## 常见解决方案

### 解决方案 1: 修复目标服务崩溃

在目标服务中添加异常处理：

```kotlin
class LocalService : Service() {
    
    private val TAG = "LocalService"
    
    override fun onCreate() {
        super.onCreate()
        try {
            // 初始化代码
            Log.d(TAG, "Service onCreate")
        } catch (e: Exception) {
            Log.e(TAG, "Error in onCreate", e)
            // 不要让服务崩溃
        }
    }
    
    override fun onBind(intent: Intent?): IBinder? {
        return try {
            Log.d(TAG, "Service onBind")
            binder
        } catch (e: Exception) {
            Log.e(TAG, "Error in onBind", e)
            null
        }
    }
    
    private val binder = object : ITransaction.Stub() {
        override fun onTransaction(requestData: String?, callback: ITransactionCallback?) {
            try {
                // 业务逻辑
                Log.d(TAG, "Processing transaction")
                callback?.callback("SUCCESS")
            } catch (e: Exception) {
                Log.e(TAG, "Error in onTransaction", e)
                callback?.onError("ERROR", e.message)
            }
        }
        
        override fun getExtraInfos(): MutableMap<String, String> {
            return try {
                mutableMapOf(
                    "version" to "1.0.0",
                    "deviceId" to "xxx"
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error in getExtraInfos", e)
                mutableMapOf()
            }
        }
    }
}
```

---

### 解决方案 2: 添加重试机制

在客户端添加延迟重试：

```kotlin
override fun performConnect(parseResult: ProtocolParseResult, connectionCallback: ConnectionCallback) {
    // ... 现有代码 ...
    
    val bindResult = context.bindService(intent, serviceConnection!!, bindFlags)
    
    if (!bindResult) {
        // 绑定失败，延迟重试
        LogUtil.w(TAG, "Bind failed, will retry after 2 seconds")
        handler.postDelayed({
            performConnect(parseResult, connectionCallback)
        }, 2000)
    }
}
```

---

### 解决方案 3: 使用显式 Intent

```kotlin
// 不使用 action，直接指定组件
val intent = Intent()
intent.component = ComponentName(
    "com.sunmi.tapro",
    "com.sunmi.tapro.taplink.service.local.LocalService"
)
intent.putExtra("app_id", appId)
intent.putExtra("secret_key", appSecretKey)

val bindResult = context.bindService(intent, serviceConnection, bindFlags)
```

---

### 解决方案 4: 先启动目标应用

```kotlin
// 先启动目标应用的主 Activity
try {
    val launchIntent = context.packageManager.getLaunchIntentForPackage("com.sunmi.tapro")
    if (launchIntent != null) {
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(launchIntent)
        
        // 等待应用启动后再绑定服务
        handler.postDelayed({
            bindService()
        }, 1000)
    }
} catch (e: Exception) {
    LogUtil.e(TAG, "Failed to launch target app", e)
}
```

---

## 推荐的调试代码

在 `LocalServiceKernel` 中添加更详细的日志：

```kotlin
override fun performConnect(parseResult: ProtocolParseResult, connectionCallback: ConnectionCallback) {
    val localProtocol = parseResult as ProtocolParseResult.LocalProtocol
    val packageName = localProtocol.packageName
    val action = localProtocol.action

    LogUtil.d(TAG, "=== Starting connection ===")
    LogUtil.d(TAG, "Package: $packageName")
    LogUtil.d(TAG, "Action: $action")
    
    val context = TaplinkServiceKernel.getInstance()?.getContext()
    if (context == null) {
        LogUtil.e(TAG, "Context is null")
        notifyConnectionError("Application context is null", InnerErrorCode.C20)
        return
    }

    // 创建Intent
    val intent = Intent(action)
    intent.setPackage(packageName)
    intent.putExtra("app_id", appId)
    intent.putExtra("secret_key", appSecretKey)
    
    // 检查目标应用是否安装
    try {
        val packageInfo = context.packageManager.getPackageInfo(packageName, 0)
        LogUtil.d(TAG, "Target app installed: version=${packageInfo.versionName}")
    } catch (e: Exception) {
        LogUtil.e(TAG, "Target app not installed: ${e.message}")
        notifyConnectionError("Target app not installed", InnerErrorCode.C22)
        return
    }
    
    // 检查服务是否存在
    val resolveInfoList = context.packageManager.queryIntentServices(intent, 0)
    LogUtil.d(TAG, "Found ${resolveInfoList.size} matching services")
    
    resolveInfoList.forEach { resolveInfo ->
        LogUtil.d(TAG, "Service: ${resolveInfo.serviceInfo.name}, " +
                      "exported=${resolveInfo.serviceInfo.exported}, " +
                      "enabled=${resolveInfo.serviceInfo.enabled}")
    }

    val matchService = resolveInfoList.find {
        it.serviceInfo.packageName == packageName
    }

    if (matchService == null) {
        LogUtil.e(TAG, "No matching service found")
        notifyConnectionError(
            "Service not found: packageName=$packageName, action=$action",
            InnerErrorCode.C22
        )
        return
    }
    
    LogUtil.d(TAG, "Matched service: ${matchService.serviceInfo.name}")

    // 检查目标进程是否运行
    val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
    val runningProcesses = activityManager.runningAppProcesses ?: emptyList()
    val isProcessRunning = runningProcesses.any { it.processName == packageName }
    LogUtil.d(TAG, "Target process running: $isProcessRunning")

    currentIntent = intent
    serviceConnection = createServiceConnection(connectionCallback)

    try {
        val bindFlags = Context.BIND_AUTO_CREATE or 
                       Context.BIND_IMPORTANT or 
                       Context.BIND_ABOVE_CLIENT
        LogUtil.d(TAG, "Binding service with flags: 0x${bindFlags.toString(16)}")
        
        val bindResult = context.bindService(intent, serviceConnection!!, bindFlags)
        LogUtil.d(TAG, "Bind result: $bindResult")

        if (!bindResult) {
            LogUtil.e(TAG, "bindService returned false")
            notifyConnectionError("Bind service failed: $bindResult", InnerErrorCode.C25)
        } else {
            LogUtil.d(TAG, "Waiting for service connection...")
        }
    } catch (e: Exception) {
        LogUtil.e(TAG, "Exception during bindService", e)
        notifyConnectionError("Exception: ${e.message}", InnerErrorCode.C25)
    }
}
```

---

## 快速检查清单

在目标服务端（com.sunmi.tapro）检查：

- [ ] `AndroidManifest.xml` 中服务声明正确
- [ ] `android:exported="true"`
- [ ] `<intent-filter>` 包含正确的 action
- [ ] `onBind()` 返回非 null 的 Binder
- [ ] `onCreate()` 和 `onBind()` 没有抛出异常
- [ ] AIDL 文件与客户端一致
- [ ] 添加了异常处理和日志

在客户端（SDK）检查：

- [ ] 使用正确的 packageName 和 action
- [ ] 使用合适的绑定标志
- [ ] 实现了所有 ServiceConnection 回调
- [ ] 添加了详细的日志
- [ ] 实现了重连机制

---

## 下一步

1. **添加详细日志** - 使用上面的调试代码
2. **查看服务端日志** - 确认服务是否崩溃
3. **检查服务声明** - 确认 Manifest 配置正确
4. **测试简化版本** - 先用最简单的标志测试
5. **联系服务端开发** - 如果是服务端问题，需要他们修复

---

**文档版本**: 1.0.0  
**更新日期**: 2025-12-17  
**作者**: TaPro Team
