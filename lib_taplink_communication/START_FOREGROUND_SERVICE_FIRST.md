# 先启动前台服务再绑定 - 最佳实践

## 策略说明

在 Android 14 上绑定前台服务时，采用**先启动服务，再绑定服务**的策略，可以显著提高成功率。

## 实现流程

### 步骤 1: 创建显式 Intent

```kotlin
val explicitIntent = Intent()
explicitIntent.component = ComponentName(
    packageName,
    "$packageName.local.LocalService"  // 完整类名
)
explicitIntent.putExtra("app_id", appId)
explicitIntent.putExtra("secret_key", appSecretKey)
```

### 步骤 2: 启动前台服务

```kotlin
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
    context.startForegroundService(explicitIntent)
} else {
    context.startService(explicitIntent)
}

// 等待服务启动完成
Thread.sleep(1000)
```

### 步骤 3: 绑定服务

```kotlin
val bindFlags = Context.BIND_AUTO_CREATE or 
               Context.BIND_IMPORTANT or 
               Context.BIND_ABOVE_CLIENT

val bindResult = context.bindService(
    explicitIntent,
    serviceConnection,
    bindFlags
)
```

## 为什么这样做？

### 1. 确保服务正确启动为前台服务

使用 `startForegroundService()` 可以：
- ✅ 明确告诉系统这是一个前台服务
- ✅ 触发服务的 `onCreate()` 和 `onStartCommand()`
- ✅ 给服务时间调用 `startForeground()`
- ✅ 确保通知正确显示

### 2. 避免 "Service binding died"

如果直接 `bindService()`：
- ❌ 服务可能没有足够时间调用 `startForeground()`
- ❌ Android 14 可能会因为超时而杀死服务
- ❌ 触发 `onBindingDied()` 回调

先启动服务：
- ✅ 服务有足够时间初始化
- ✅ 前台通知已经显示
- ✅ 绑定时服务已经稳定运行

### 3. 符合 Android 14 的要求

Android 14 对前台服务有严格要求：
- 必须在启动后 5 秒内调用 `startForeground()`
- 必须显示通知
- 必须声明正确的 `foregroundServiceType`

先启动服务可以确保满足这些要求。

## 时间线对比

### ❌ 直接绑定（可能失败）

```
0ms:  bindService() 调用
100ms: 系统启动服务进程
200ms: 服务 onCreate() 被调用
300ms: 服务尝试 startForeground()
400ms: onBindingDied() - 超时！
```

### ✅ 先启动再绑定（成功）

```
0ms:   startForegroundService() 调用
100ms: 系统启动服务进程
200ms: 服务 onCreate() 被调用
300ms: 服务 onStartCommand() 被调用
400ms: 服务 startForeground() 成功
500ms: 通知显示
1000ms: 等待完成
1100ms: bindService() 调用
1200ms: onServiceConnected() - 成功！
```

## 完整代码示例

### 客户端（SDK）

```kotlin
override fun performConnect(parseResult: ProtocolParseResult, connectionCallback: ConnectionCallback) {
    val packageName = "com.sunmi.tapro"
    
    // 1. 创建显式 Intent
    val explicitIntent = Intent()
    explicitIntent.component = ComponentName(
        packageName,
        "$packageName.local.LocalService"
    )
    explicitIntent.putExtra("app_id", appId)
    explicitIntent.putExtra("secret_key", appSecretKey)
    
    // 2. 先启动前台服务
    try {
        LogUtil.d(TAG, "Starting foreground service...")
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(explicitIntent)
        } else {
            context.startService(explicitIntent)
        }
        
        // 等待服务启动
        Thread.sleep(1000)
        
        LogUtil.d(TAG, "Foreground service started")
    } catch (e: Exception) {
        LogUtil.w(TAG, "Failed to start service: ${e.message}")
    }
    
    // 3. 绑定服务
    try {
        LogUtil.d(TAG, "Binding to service...")
        
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
        }
    } catch (e: Exception) {
        LogUtil.e(TAG, "Exception during binding: ${e.message}")
    }
}
```

### 服务端

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
        
        // 启动为前台服务
        startForegroundService()
        
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder {
        Log.d(TAG, "Service onBind")
        
        // 如果还没有启动为前台服务，在这里启动
        if (!isForeground) {
            startForegroundService()
        }
        
        return binder
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Taplink Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
    
    private fun startForegroundService() {
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
        Log.d(TAG, "Foreground service started successfully")
    }
    
    private var isForeground = false
    
    private val binder = object : ITransaction.Stub() {
        override fun onTransaction(requestData: String?, callback: ITransactionCallback?) {
            try {
                Log.d(TAG, "Processing transaction")
                callback?.callback("SUCCESS")
            } catch (e: Exception) {
                Log.e(TAG, "Transaction error", e)
                callback?.onError("ERROR", e.message)
            }
        }
        
        override fun getExtraInfos(): MutableMap<String, String> {
            return mutableMapOf(
                "version" to "1.0.0",
                "isForeground" to isForeground.toString()
            )
        }
    }
}
```

## 注意事项

### 1. 等待时间

```kotlin
Thread.sleep(1000)  // 推荐 1000ms
```

**为什么需要 1000ms？**
- 启动进程：~200ms
- onCreate()：~100ms
- onStartCommand()：~100ms
- startForeground()：~200ms
- 创建通知：~200ms
- 系统处理：~200ms

**总计约 1000ms**

### 2. 异常处理

即使 `startForegroundService()` 失败，仍然尝试 `bindService()`：

```kotlin
try {
    context.startForegroundService(intent)
    Thread.sleep(1000)
} catch (e: Exception) {
    Log.w(TAG, "Failed to start service: ${e.message}")
    // 继续尝试绑定
}

// 仍然尝试绑定
context.bindService(intent, connection, flags)
```

### 3. 使用显式组件名

```kotlin
// ✅ 推荐：显式组件名
intent.component = ComponentName(
    "com.sunmi.tapro",
    "com.sunmi.tapro.local.LocalService"
)

// ❌ 不推荐：隐式 Intent（可能在 Android 14 上失败）
intent.action = "com.sunmi.tapro.taplink.service.local.ACTION_TRANSACTION"
intent.setPackage("com.sunmi.tapro")
```

### 4. 服务端必须正确实现

- ✅ 在 `onStartCommand()` 中调用 `startForeground()`
- ✅ 在 `onBind()` 中也检查是否需要调用 `startForeground()`
- ✅ 返回非 null 的 Binder
- ✅ 添加异常处理

## 诊断

### 查看服务是否启动

```bash
# 查看服务状态
adb shell dumpsys activity services com.sunmi.tapro | grep -A 20 "LocalService"

# 查看是否是前台服务
adb shell dumpsys activity services com.sunmi.tapro | grep "isForeground"

# 查看通知
adb shell dumpsys notification | grep com.sunmi.tapro
```

### 查看日志

```bash
# 客户端日志
adb logcat | grep LocalServiceKernel

# 服务端日志
adb logcat | grep LocalService

# 期望看到的日志顺序：
# 1. "Starting foreground service..."
# 2. "Service onCreate"
# 3. "Service onStartCommand"
# 4. "Starting as foreground service"
# 5. "Foreground service started successfully"
# 6. "Binding to service..."
# 7. "Service onBind"
# 8. "[onServiceConnected]"
```

## 优势总结

| 方面 | 直接绑定 | 先启动再绑定 |
|------|---------|------------|
| 成功率 | ⭐⭐⭐ | ⭐⭐⭐⭐⭐ |
| 稳定性 | ⭐⭐ | ⭐⭐⭐⭐⭐ |
| 前台服务支持 | ❌ | ✅ |
| Android 14 兼容性 | ⭐⭐ | ⭐⭐⭐⭐⭐ |
| 代码复杂度 | 简单 | 稍复杂 |

## 相关文档

- [前台服务说明](./FOREGROUND_SERVICE_NOTES.md)
- [解决方案总结](./SOLUTION_SUMMARY.md)
- [绑定标志详解](./BIND_FLAGS_GUIDE.md)

---

**文档版本**: 1.0.0  
**更新日期**: 2025-12-17  
**作者**: TaPro Team
