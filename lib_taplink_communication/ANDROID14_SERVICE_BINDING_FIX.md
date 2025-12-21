# Android 14 服务绑定修复说明

## 问题描述

在 Android 上绑定服务时可能遇到以下错误：

### 错误 1: Service binding died

```
Service binding died: ComponentInfo{...}
```

**原因**: 目标进程未启动或处于异常状态

### 错误 2: Process is bad

```
Unable to start service: Unable to launch app xxx/xxx for service: process is bad
```

**原因**: 目标进程之前崩溃或处于异常状态，系统认为该进程不健康

### 根本原因

1. **进程未启动**: 目标服务所在的进程未运行
2. **进程异常**: 目标进程之前崩溃，系统标记为 "bad process"
3. **后台限制**: Android 14+ 加强了对后台服务的限制

## 解决方案

### 最佳实践：使用正确的标志组合

```kotlin
// 推荐的标志组合（适合支付等关键服务）
val bindFlags = Context.BIND_AUTO_CREATE or 
               Context.BIND_IMPORTANT or 
               Context.BIND_ABOVE_CLIENT
val bindResult = context.bindService(
    intent,
    serviceConnection,
    bindFlags
)
```

### 为什么这样有效？

1. **BIND_AUTO_CREATE (0x0001)**
   - 如果服务不存在，自动创建服务
   - 如果进程未启动，自动启动进程
   - ✅ 可以启动未运行的目标进程

2. **BIND_IMPORTANT (0x0040)**
   - 提高服务的优先级
   - 告诉系统这是一个重要的服务连接
   - 减少服务被系统杀死的可能性
   - ✅ 适合关键业务（如支付）

3. **BIND_ABOVE_CLIENT (0x0008)**
   - 服务进程优先级高于客户端进程
   - 系统内存不足时，优先保留服务而不是客户端
   - ✅ 确保支付服务比 POS 应用更稳定
   - ✅ 适合多个客户端共享的服务

4. **让系统处理进程状态**
   - 不使用 `startService()`，避免 "process is bad" 错误
   - 让系统自动处理进程启动和清理
   - 如果进程异常，系统会自动重启

### 为什么不使用 startService()?

使用 `startService()` 的问题：
- ❌ 如果进程处于异常状态（process is bad），`startService()` 会失败
- ❌ 需要额外的错误处理
- ❌ 可能导致服务无法正常停止

直接使用 `bindService()` 的优势：
- ✅ 系统自动处理进程启动
- ✅ 系统自动清理异常进程
- ✅ 代码更简洁
- ✅ 更好的生命周期管理

## 完整实现

### 连接服务

```kotlin
override fun performConnect(parseResult: ProtocolParseResult, connectionCallback: ConnectionCallback) {
    // ... 创建 Intent 和 ServiceConnection ...

    try {
        // 使用标志组合：
        // - BIND_AUTO_CREATE: 自动创建服务和启动进程
        // - BIND_IMPORTANT: 提高服务优先级
        // - BIND_ABOVE_CLIENT: 服务优先级高于客户端
        val bindFlags = Context.BIND_AUTO_CREATE or 
                       Context.BIND_IMPORTANT or 
                       Context.BIND_ABOVE_CLIENT
        val bindResult = context.bindService(
            intent,
            serviceConnection!!,
            bindFlags
        )
        LogUtil.d(TAG, "Bind service result: $bindResult, flags: 0x${bindFlags.toString(16)}")
        
        if (!bindResult) {
            notifyConnectionError("Bind service failed", InnerErrorCode.C25)
        }
        // 绑定成功会在 ServiceConnection 的 onServiceConnected 中处理
        // 如果目标进程异常，会在 onBindingDied 回调中处理
    } catch (e: Exception) {
        LogUtil.e(TAG, "Failed to bind service: ${e.message}")
        notifyConnectionError("Exception: ${e.message}", InnerErrorCode.C25)
    }
}
```

### 断开服务

```kotlin
override fun performDisconnect() {
    val context = TaplinkServiceKernel.getInstance()?.getContext()

    // 解绑服务
    if (isServiceBound && serviceConnection != null && context != null) {
        try {
            context.unbindService(serviceConnection!!)
            LogUtil.d(TAG, "Service unbound successfully")
        } catch (e: Exception) {
            LogUtil.e(TAG, "Failed to unbind service: ${e.message}")
        }
        isServiceBound = false
    }

    // 清理资源
    serviceConnection = null
    currentIntent = null
    iTransaction = null
}
```

## ServiceConnection 回调处理

确保实现所有回调方法以处理各种异常情况：

```kotlin
private fun createServiceConnection(connectionCallback: ConnectionCallback): ServiceConnection {
    return object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            // 服务连接成功
            isServiceBound = true
            // ... 处理连接 ...
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            // 服务异常断开（进程崩溃等）
            isServiceBound = false
            notifyConnectionDisconnected(InnerErrorCode.C36.code, "Service disconnected")
        }

        override fun onBindingDied(name: ComponentName?) {
            // 服务绑定死亡（进程崩溃或异常）
            // 这个回调会在以下情况触发：
            // 1. 目标进程崩溃
            // 2. 目标进程被系统杀死
            // 3. 目标进程处于 "bad" 状态
            LogUtil.w(TAG, "Service binding died: $name")
            isServiceBound = false
            iTransaction = null
            
            updateStatus(ConnectionStatus.ERROR)
            notifyConnectionDisconnected(InnerErrorCode.C36.code, "Service binding died")
        }

        override fun onNullBinding(name: ComponentName?) {
            // 服务返回 null 绑定
            isServiceBound = false
            notifyConnectionError("Service returned null binding", InnerErrorCode.C36)
        }
    }
}
```

## 处理 "Process is bad" 错误

### 什么是 "Process is bad"？

当目标进程之前崩溃或异常退出时，系统会将其标记为 "bad process"。在这种状态下：
- `startService()` 会失败并抛出异常
- `bindService()` 可能会触发 `onBindingDied()` 回调
- 系统需要时间来清理异常进程

### 解决方案

1. **不使用 startService()**
   - 避免触发 "process is bad" 错误
   - 让系统自动处理进程启动

2. **实现 onBindingDied() 回调**
   - 正确处理进程异常情况
   - 通知上层连接失败
   - 可以触发重连机制

3. **使用重连机制**
   - 在 SDK 层实现自动重连
   - 给系统时间清理异常进程
   - 延迟重试（建议 3-5 秒）

### 示例：处理进程异常

```kotlin
override fun onBindingDied(name: ComponentName?) {
    LogUtil.w(TAG, "Service binding died (process may be bad): $name")
    isServiceBound = false
    iTransaction = null
    
    // 通知上层连接失败
    updateStatus(ConnectionStatus.ERROR)
    notifyConnectionDisconnected(InnerErrorCode.C36.code, "Service binding died")
    
    // SDK 层的 ReconnectManager 会自动处理重连
    // 重连时系统会清理异常进程并重新启动
}
```

## 目标服务端要求

确保目标服务正确实现：

### 1. 在 AndroidManifest.xml 中声明服务

```xml
<service
    android:name=".YourService"
    android:enabled="true"
    android:exported="true"
    android:process=":remote">  <!-- 可选：在独立进程中运行 -->
    <intent-filter>
        <action android:name="your.service.action" />
    </intent-filter>
</service>
```

### 2. 服务实现

```kotlin
class YourService : Service() {
    
    private val binder = object : ITransaction.Stub() {
        override fun onTransaction(requestData: String?, callback: ITransactionCallback?) {
            // 处理交易
            try {
                // 业务逻辑
                callback?.callback("SUCCESS")
            } catch (e: Exception) {
                callback?.onError("ERROR", e.message)
            }
        }
        
        override fun getExtraInfos(): MutableMap<String, String> {
            return mutableMapOf(
                "version" to "1.0.0",
                "deviceId" to "xxx"
            )
        }
    }
    
    override fun onBind(intent: Intent?): IBinder {
        Log.d(TAG, "Service onBind called")
        return binder
    }
    
    override fun onUnbind(intent: Intent?): Boolean {
        Log.d(TAG, "Service onUnbind called")
        return super.onUnbind(intent)
    }
    
    override fun onDestroy() {
        Log.d(TAG, "Service onDestroy called")
        super.onDestroy()
    }
}
```

### 3. 避免服务崩溃

```kotlin
// 在服务中添加全局异常处理
class YourService : Service() {
    
    override fun onCreate() {
        super.onCreate()
        
        // 设置未捕获异常处理器
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e(TAG, "Uncaught exception in service", throwable)
            // 不要让服务崩溃，尝试恢复
        }
    }
    
    private val binder = object : ITransaction.Stub() {
        override fun onTransaction(requestData: String?, callback: ITransactionCallback?) {
            try {
                // 所有业务逻辑都要 try-catch
                processTransaction(requestData, callback)
            } catch (e: Exception) {
                Log.e(TAG, "Transaction error", e)
                callback?.onError("TRANSACTION_ERROR", e.message)
            }
        }
    }
}
```

## 测试建议

### 1. 测试场景

- ✅ 目标进程未启动时绑定服务
- ✅ 目标进程已启动时绑定服务
- ✅ 目标进程崩溃后重新连接（测试 "process is bad"）
- ✅ 服务异常断开后重新连接
- ✅ 在 Android 14+ 设备上测试
- ✅ 在低版本 Android 设备上测试

### 2. 模拟 "Process is bad" 场景

```bash
# 1. 强制停止目标应用
adb shell am force-stop com.sunmi.tapro

# 2. 立即尝试连接（会触发 "process is bad"）
# 在你的应用中调用 connect()

# 3. 查看日志
adb logcat | grep -E "LocalServiceKernel|Service binding"
```

### 3. 日志验证

#### 正常连接流程：

```
D/LocalServiceKernel: Connecting to local service: packageName=com.sunmi.tapro, action=...
D/LocalServiceKernel: Query intent services result: 1
D/LocalServiceKernel: Bind service result: true, flags: 5
D/LocalServiceKernel: Service connected: ComponentInfo{...}
D/LocalServiceKernel: ITransaction interface obtained successfully
```

#### 进程异常流程：

```
D/LocalServiceKernel: Bind service result: true, flags: 5
W/LocalServiceKernel: Service binding died: ComponentInfo{...}
D/LocalServiceKernel: Connection status changed: ERROR
```

### 4. 验证重连机制

```kotlin
// 在 SDK 层配置自动重连
val config = TaplinkConfig(...)
    .setAutoReconnect(true)
    .setRetryPolicy(maxRetries = 5, delayMs = 3000L)  // 3秒延迟重试

// 连接
TaplinkSDK.connect(config, object : ConnectionListener {
    override fun onConnected() {
        Log.d(TAG, "Connected successfully")
    }
    
    override fun onDisconnected(reason: DisconnectReason) {
        Log.w(TAG, "Disconnected: $reason")
        // ReconnectManager 会自动重连
    }
})
```

## 标志组合对比

| 标志组合 | 启动进程 | 优先级 | 稳定性 | 适用场景 |
|---------|---------|--------|--------|---------|
| `BIND_AUTO_CREATE` | ✅ | 普通 | ⭐⭐⭐ | 一般服务 |
| `BIND_AUTO_CREATE \| BIND_IMPORTANT` | ✅ | 高 | ⭐⭐⭐⭐ | 重要服务 |
| `BIND_AUTO_CREATE \| BIND_ABOVE_CLIENT` | ✅ | 高 | ⭐⭐⭐⭐ | 共享服务 |
| `BIND_AUTO_CREATE \| BIND_IMPORTANT \| BIND_ABOVE_CLIENT` | ✅ | 很高 | ⭐⭐⭐⭐⭐ | **关键服务（推荐）** |

### 为什么选择这个组合？

对于 Taplink 支付服务：
- ✅ 支付服务是关键业务，需要高优先级
- ✅ 支付服务可能为多个 POS 应用提供服务
- ✅ 支付服务不应该因为 POS 应用被杀死而中断
- ✅ 在 Android 14 上测试通过，可以启动未运行的进程

## 相关资源

- [绑定标志详解](./BIND_FLAGS_GUIDE.md) - 详细的标志说明和使用指南
- [Android 14 行为变更](https://developer.android.com/about/versions/14/behavior-changes-14)
- [绑定服务概览](https://developer.android.com/guide/components/bound-services)
- [后台执行限制](https://developer.android.com/about/versions/oreo/background)

## 版本历史

| 版本 | 日期 | 说明 |
|------|------|------|
| 1.0.1 | 2025-12-17 | 添加 BIND_ABOVE_CLIENT 标志，优化标志组合 |
| 1.0.0 | 2025-12-17 | 修复 Android 14 服务绑定问题 |

---

**文档版本**: 1.0.0  
**更新日期**: 2025-12-17  
**作者**: TaPro Team
