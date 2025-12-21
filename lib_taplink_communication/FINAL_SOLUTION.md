# Android 14 本地服务绑定 - 最终解决方案

## 方案概述

**不启动 Activity，直接启动前台服务并绑定**

这个方案：
- ✅ 不会显示目标应用界面
- ✅ 直接启动前台服务
- ✅ 使用显式组件名
- ✅ 适用于 Android 14

## 完整流程

```
步骤 1: 启动前台服务
  ↓
等待 1200ms（让服务完全启动）
  ↓
步骤 2: 绑定服务
  ↓
等待 ServiceConnection 回调
```

## 实现代码

### 客户端（SDK）

```kotlin
override fun performConnect(parseResult: ProtocolParseResult, connectionCallback: ConnectionCallback) {
    val packageName = "com.sunmi.tapro"
    
    // 创建显式 Intent
    val explicitIntent = Intent()
    explicitIntent.component = ComponentName(
        packageName,
        "$packageName.local.LocalService"  // 完整类名
    )
    explicitIntent.putExtra("app_id", appId)
    explicitIntent.putExtra("secret_key", appSecretKey)
    
    // 步骤 1: 启动前台服务（不启动 Activity）
    try {
        LogUtil.d(TAG, "Step 1: Starting foreground service (without UI)...")
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(explicitIntent)
        } else {
            context.startService(explicitIntent)
        }
        
        // 等待服务启动
        Thread.sleep(1200)
        
        LogUtil.d(TAG, "Foreground service should be running now")
        
    } catch (e: Exception) {
        LogUtil.e(TAG, "Failed to start service: ${e.message}")
        
        // 检查 "process is bad" 错误
        if (e.message?.contains("process is bad") == true) {
            notifyConnectionError(
                "Target service process is in bad state. Please restart the target app.",
                InnerErrorCode.C25
            )
            return
        }
    }
    
    // 步骤 2: 绑定服务
    try {
        LogUtil.d(TAG, "Step 2: Binding to the service...")
        
        val bindFlags = Context.BIND_AUTO_CREATE or 
                       Context.BIND_IMPORTANT or 
                       Context.BIND_ABOVE_CLIENT
        
        val bindResult = context.bindService(
            explicitIntent,
            serviceConnection,
            bindFlags
        )
        
        if (bindResult) {
            LogUtil.d(TAG, "Binding initiated successfully")
        } else {
            LogUtil.e(TAG, "Binding failed")
            notifyConnectionError("Failed to bind service", InnerErrorCode.C25)
        }
        
    } catch (e: Exception) {
        LogUtil.e(TAG, "Exception during binding: ${e.message}")
        notifyConnectionError("Exception: ${e.message}", InnerErrorCode.C25)
    }
}
```

### 服务端（目标应用）

**关键要求：必须正确实现前台服务**

```kotlin
class LocalService : Service() {
    
    private val TAG = "LocalService"
    private val NOTIFICATION_ID = 1001
    private val CHANNEL_ID = "taplink_service"
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service onCreate")
        
        // 创建通知渠道
        createNotificationChannel()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service onStartCommand")
        
        // ⚠️ 关键：必须在这里调用 startForeground()
        // Android 14 要求在 5 秒内调用，否则会杀死服务
        startForegroundService()
        
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder {
        Log.d(TAG, "Service onBind")
        
        // 如果还没有启动为前台服务，在这里启动
        // （防止直接 bindService 而没有 startService 的情况）
        if (!isForeground) {
            startForegroundService()
        }
        
        return binder
    }
    
    private var isForeground = false
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Taplink Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Taplink payment service"
            }
            
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
            Log.d(TAG, "Notification channel created")
        }
    }
    
    private fun startForegroundService() {
        if (isForeground) {
            Log.d(TAG, "Already running as foreground service")
            return
        }
        
        Log.d(TAG, "Starting as foreground service")
        
        val notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("Taplink Service")
                .setContentText("Payment service is running")
                .setSmallIcon(R.drawable.ic_notification)
                .build()
        } else {
            Notification.Builder(this)
                .setContentTitle("Taplink Service")
                .setContentText("Payment service is running")
                .setSmallIcon(R.drawable.ic_notification)
                .build()
        }
        
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                // Android 14+
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            
            isForeground = true
            Log.d(TAG, "✅ Foreground service started successfully")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to start foreground service", e)
            throw e
        }
    }
    
    private val binder = object : ITransaction.Stub() {
        override fun onTransaction(requestData: String?, callback: ITransactionCallback?) {
            try {
                Log.d(TAG, "Processing transaction: $requestData")
                // 处理业务逻辑
                callback?.callback("SUCCESS")
            } catch (e: Exception) {
                Log.e(TAG, "Transaction error", e)
                callback?.onError("ERROR", e.message)
            }
        }
        
        override fun getExtraInfos(): MutableMap<String, String> {
            return try {
                mutableMapOf(
                    "version" to "1.0.0",
                    "deviceId" to "xxx",
                    "isForeground" to isForeground.toString()
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error getting extra infos", e)
                mutableMapOf()
            }
        }
    }
    
    override fun onDestroy() {
        Log.d(TAG, "Service onDestroy")
        isForeground = false
        super.onDestroy()
    }
}
```

### AndroidManifest.xml

```xml
<!-- 权限 -->
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

<!-- 服务声明 -->
<service
    android:name=".local.LocalService"
    android:enabled="true"
    android:exported="true"
    android:foregroundServiceType="dataSync">
    <intent-filter>
        <action android:name="com.sunmi.tapro.taplink.service.local.ACTION_TRANSACTION" />
    </intent-filter>
</service>
```

## 关键点

### 1. 不启动 Activity

```kotlin
// ❌ 不要这样做（会显示界面）
val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
context.startActivity(launchIntent)

// ✅ 直接启动服务（不显示界面）
context.startForegroundService(explicitIntent)
```

### 2. 使用显式组件名

```kotlin
// ✅ 推荐：显式组件名
intent.component = ComponentName(
    "com.sunmi.tapro",
    "com.sunmi.tapro.local.LocalService"
)

// ❌ 不推荐：隐式 Intent
intent.action = "com.sunmi.tapro.taplink.service.local.ACTION_TRANSACTION"
```

### 3. 等待足够的时间

```kotlin
// 启动服务后等待 1200ms
context.startForegroundService(intent)
Thread.sleep(1200)  // 给服务时间启动
```

### 4. 服务端必须调用 startForeground()

```kotlin
override fun onStartCommand(...): Int {
    startForeground(NOTIFICATION_ID, notification)  // 必须！
    return START_STICKY
}
```

## "Process is bad" 错误处理

如果遇到这个错误，说明目标进程处于异常状态。

### 错误信息

```
Unable to launch app com.sunmi.tapro/10140 for service: process is bad
```

### 解决方案

**方案 1: 用户手动重启目标应用**

在错误回调中提示用户：

```kotlin
if (e.message?.contains("process is bad") == true) {
    notifyConnectionError(
        "Target service process is in bad state. Please restart the target app.",
        InnerErrorCode.C25
    )
}
```

**方案 2: 使用 ADB 清理（开发/测试）**

```bash
# 强制停止应用
adb shell am force-stop com.sunmi.tapro

# 等待几秒
sleep 3

# 重新尝试连接
```

**方案 3: 重启设备（最后手段）**

```bash
adb reboot
```

### 预防措施

服务端必须：
- ✅ 添加异常处理，防止崩溃
- ✅ 及时调用 `startForeground()`
- ✅ 正确实现 `onBind()` 返回非 null
- ✅ 添加详细日志

## 测试验证

### 1. 查看日志

```bash
adb logcat | grep -E "LocalServiceKernel|LocalService"
```

**期望看到的日志：**

```
# 客户端
D/LocalServiceKernel: Step 1: Starting foreground service (without UI)...
D/LocalServiceKernel: ✅ startForegroundService() called (Android 8.0+)
D/LocalServiceKernel: Waiting for foreground service to start (1200ms)...
D/LocalServiceKernel: ✅ Foreground service should be running now
D/LocalServiceKernel: Step 2: Binding to the service...
D/LocalServiceKernel: Binding initiated successfully
D/LocalServiceKernel: ✅ [onServiceConnected] Service connected

# 服务端
D/LocalService: Service onCreate
D/LocalService: Notification channel created
D/LocalService: Service onStartCommand
D/LocalService: Starting as foreground service
D/LocalService: ✅ Foreground service started successfully
D/LocalService: Service onBind
```

### 2. 验证前台服务

```bash
# 检查服务是否作为前台服务运行
adb shell dumpsys activity services com.sunmi.tapro | grep -E "isForeground|LocalService"

# 应该看到 "isForeground=true"
```

### 3. 检查通知

```bash
# 查看通知
adb shell dumpsys notification | grep com.sunmi.tapro

# 应该看到服务的通知
```

## 常见问题

### Q1: 为什么不启动 Activity？

**A:** 
- 启动 Activity 会显示目标应用界面
- 用户体验不好
- 不符合需求

### Q2: 如果遇到 "process is bad" 怎么办？

**A:**
- 这是目标应用的问题（之前崩溃了）
- 需要用户重启目标应用
- 或者目标应用修复崩溃问题

### Q3: 为什么要等待 1200ms？

**A:**
- 前台服务需要时间启动
- 包括：启动进程、创建通知、调用 startForeground()
- 1200ms 是一个合理的等待时间

### Q4: 可以缩短等待时间吗？

**A:**
- 可以尝试减少到 800-1000ms
- 但可能导致绑定时服务还没完全启动
- 建议保持 1200ms

## 优势总结

| 方面 | 说明 |
|------|------|
| ✅ 不显示界面 | 直接启动服务，不启动 Activity |
| ✅ 使用显式组件名 | 更可靠，不依赖 intent-filter |
| ✅ 支持前台服务 | 正确处理 Android 14 的前台服务要求 |
| ✅ 详细日志 | 方便诊断问题 |
| ✅ 错误处理 | 检测并提示 "process is bad" 错误 |

## 相关文档

- [前台服务说明](./FOREGROUND_SERVICE_NOTES.md)
- [先启动服务再绑定](./START_FOREGROUND_SERVICE_FIRST.md)
- [Process is bad 处理](./PROCESS_IS_BAD_FIX.md)
- [绑定标志详解](./BIND_FLAGS_GUIDE.md)
- [问题排查指南](./BINDING_DIED_TROUBLESHOOTING.md)

---

**文档版本**: 1.0.0  
**更新日期**: 2025-12-17  
**作者**: TaPro Team

## 总结

这是最终的解决方案：

1. ✅ **不启动 Activity** - 避免显示界面
2. ✅ **先启动前台服务** - 确保服务正确启动
3. ✅ **使用显式组件名** - 更可靠
4. ✅ **等待足够时间** - 1200ms
5. ✅ **再绑定服务** - 服务已经稳定运行
6. ✅ **处理错误** - 检测 "process is bad"

如果还是失败，问题很可能在服务端：
- 服务没有正确调用 `startForeground()`
- 服务崩溃
- 服务 `onBind()` 返回 null

请检查服务端日志并修复问题。
