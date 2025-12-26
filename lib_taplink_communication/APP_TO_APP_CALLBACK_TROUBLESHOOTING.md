# APP TO APP 连接没有回调问题排查指南

## 问题现象

调用 `TaplinkSDK.connect()` 进行 APP TO APP 连接时，没有收到 `onConnected` 或 `onError` 回调。

## 连接流程分析

APP TO APP 连接的完整流程：

```
1. TaplinkSDK.connect() 调用
2. ConnectionManager.connectInternal()
3. LocalServiceKernel.performConnect()
4. bindService() 绑定目标服务
5. ServiceConnection.onServiceConnected() 回调
6. 获取 ITransaction 接口
7. 发送 INIT 命令
8. 收到 INIT 响应
9. 调用 onConnected() 回调
```

**只有所有步骤都成功，才会调用 `onConnected` 回调。**

## 可能的问题点

### 1. 服务绑定失败 ⭐ 最常见

**现象：**
- 没有任何回调
- 日志显示 `bindService returned false`

**原因：**
- Tapro 应用未安装
- Tapro 应用中的服务未正确声明
- 权限问题

**排查方法：**
```bash
# 检查 Tapro 应用是否安装
adb shell pm list packages | grep com.sunmi.tapro

# 检查服务声明
adb shell dumpsys package com.sunmi.tapro | grep -A 10 "Service"
```

**解决方案：**
- 确保 Tapro 应用已安装
- 确保服务在 AndroidManifest.xml 中正确声明：
```xml
<service
    android:name=".taplink.service.local.LocalService"
    android:enabled="true"
    android:exported="true">
    <intent-filter>
        <action android:name="com.sunmi.tapro.taplink.service.local.ACTION_TRANSACTION" />
    </intent-filter>
</service>
```

---

### 2. 服务绑定死亡 ⭐ 常见

**现象：**
- `bindService()` 返回 `true`
- 但立即触发 `onBindingDied()` 回调
- 没有调用 `onServiceConnected()`

**日志特征：**
```
D/LocalServiceKernel: Bind service result: true
W/LocalServiceKernel: Service binding died: ComponentInfo{...}
```

**原因：**
- 目标服务进程崩溃
- 服务的 `onCreate()` 或 `onBind()` 方法抛出异常
- AIDL 接口不匹配

**排查方法：**
```bash
# 查看目标服务的崩溃日志
adb logcat | grep -E "AndroidRuntime|FATAL|LocalService|com.sunmi.tapro"
```

**解决方案：**
- 检查 Tapro 应用是否有崩溃
- 确保 AIDL 接口文件一致
- 联系 Tapro 应用开发者修复服务端问题

---

### 3. INIT 命令失败 ⭐ 常见

**现象：**
- 服务绑定成功
- 但 INIT 命令失败或没有响应
- 收到 `onError` 回调而不是 `onConnected`

**日志特征：**
```
D/LocalServiceKernel: Service connected successfully
D/ConnectionManager: Starting INIT command...
E/ConnectionManager: INIT command failed: code=AUTH_FAILED, msg=Invalid credentials
```

或者：
```
D/ConnectionManager: Starting INIT command...
D/ConnectionManager: INIT command sent successfully
# 然后没有任何响应，超时
```

**原因：**
- `appId` 或 `secretKey` 错误
- Tapro 应用的认证逻辑有问题
- INIT 命令超时
- **INIT 命令的回调没有正确注册到 callbackManager（已修复）**

**排查方法：**
- 检查初始化时使用的 `appId` 和 `secretKey`
- 查看 INIT 命令的详细错误信息
- 检查是否有 INIT 响应返回

**解决方案：**
- 确认使用正确的 `appId` 和 `secretKey`
- 联系 Tapro 应用开发者确认认证逻辑
- **更新到最新版本的 SDK（已修复回调注册问题）**

---

### 4. 回调接口问题

**现象：**
- 连接流程正常
- 但应用层没有收到回调

**原因：**
- `ConnectionListener` 实现有问题
- 回调在错误的线程中执行
- 回调被异常中断

**排查方法：**
- 检查 `ConnectionListener` 的实现
- 在回调方法中添加日志

**解决方案：**
```kotlin
TaplinkSDK.connect(null, object : ConnectionListener {
    override fun onConnected(deviceId: String, taproVersion: String) {
        Log.d(TAG, "=== onConnected callback received ===")
        Log.d(TAG, "deviceId: $deviceId")
        Log.d(TAG, "taproVersion: $taproVersion")
        Log.d(TAG, "Thread: ${Thread.currentThread().name}")
        
        // 确保在主线程中更新UI
        runOnUiThread {
            // 更新UI
        }
    }
    
    override fun onError(error: ConnectionError) {
        Log.e(TAG, "=== onError callback received ===")
        Log.e(TAG, "errorCode: ${error.code}")
        Log.e(TAG, "errorMessage: ${error.message}")
        Log.e(TAG, "Thread: ${Thread.currentThread().name}")
    }
    
    override fun onDisconnected(reason: String) {
        Log.w(TAG, "=== onDisconnected callback received ===")
        Log.w(TAG, "reason: $reason")
    }
    
    override fun onReconnecting(currentAttempt: Int, maxAttempts: Int) {
        Log.i(TAG, "=== onReconnecting callback received ===")
        Log.i(TAG, "attempt: $currentAttempt/$maxAttempts")
    }
})
```

---

## 诊断步骤

### 步骤 1: 运行诊断脚本

```bash
./lib_taplink_communication/diagnose_app_to_app.sh
```

这个脚本会检查：
- Tapro 应用是否安装
- 服务是否正确声明
- 进程状态
- 服务查询结果

### 步骤 2: 启用详细日志

确保在初始化时启用调试日志：

```kotlin
val config = TaplinkConfig(
    appId = "your_app_id",
    merchantId = "your_merchant_id", 
    secretKey = "your_secret_key"
)
    .setConnectionMode(ConnectionMode.APP_TO_APP)
    .setLogEnabled(true)  // 启用日志
    .setLogLevel(LogLevel.DEBUG)  // 设置为调试级别

TaplinkSDK.init(context, config)
```

### 步骤 3: 监控连接日志

在终端中运行：

```bash
adb logcat | grep -E "LocalServiceKernel|ConnectionManager|TaplinkApiImpl"
```

### 步骤 4: 分析日志输出

#### 正常连接流程的日志：

```
D/ConnectionManager: === APP TO APP Connection Debug ===
D/ConnectionManager: Connection mode: APP_TO_APP
D/LocalServiceKernel: === APP TO APP Service Binding Debug ===
D/LocalServiceKernel: Target package: com.sunmi.tapro
D/LocalServiceKernel: Target action: com.sunmi.tapro.taplink.service.local.ACTION_TRANSACTION
D/LocalServiceKernel: Target app installed: version=1.0.0
D/LocalServiceKernel: Found matching service: com.sunmi.tapro.taplink.service.local.LocalService
D/LocalServiceKernel: Bind service result: true
D/LocalServiceKernel: === ServiceConnection.onServiceConnected ===
D/LocalServiceKernel: ITransaction interface obtained successfully
D/ConnectionManager: === Service Connection Success ===
D/ConnectionManager: Starting INIT command...
D/ConnectionManager: === INIT Command Result ===
D/ConnectionManager: INIT success: true
D/ConnectionManager: === Calling onConnected Callbacks ===
D/ConnectionManager: === All onConnected Callbacks Completed ===
```

#### 服务绑定失败的日志：

```
D/LocalServiceKernel: Bind service result: false
E/LocalServiceKernel: bindService returned false - binding failed immediately
```

#### 服务绑定死亡的日志：

```
D/LocalServiceKernel: Bind service result: true
W/LocalServiceKernel: === ServiceConnection.onBindingDied ===
W/LocalServiceKernel: Service binding died: ComponentInfo{...}
W/LocalServiceKernel: This means the target process crashed during or after binding
```

#### INIT 命令失败的日志：

```
D/ConnectionManager: Starting INIT command...
D/ConnectionManager: === INIT Command Result ===
D/ConnectionManager: INIT success: false
D/ConnectionManager: INIT errorCode: AUTH_FAILED
D/ConnectionManager: INIT errorMsg: Invalid app credentials
```

---

## 常见解决方案

### 解决方案 1: 修复服务绑定问题

如果看到 `bindService returned false`：

1. **检查 Tapro 应用安装**
```bash
adb shell pm list packages | grep com.sunmi.tapro
```

2. **检查服务声明**
```bash
adb shell dumpsys package com.sunmi.tapro | grep -A 20 "Service"
```

3. **重新安装 Tapro 应用**

### 解决方案 2: 修复服务绑定死亡问题

如果看到 `Service binding died`：

1. **查看崩溃日志**
```bash
adb logcat | grep -E "AndroidRuntime|FATAL|com.sunmi.tapro"
```

2. **强制停止并重启 Tapro 应用**
```bash
adb shell am force-stop com.sunmi.tapro
# 等待几秒后重试连接
```

3. **检查 AIDL 接口一致性**
   - 确保客户端和服务端的 AIDL 文件完全一致

### 解决方案 3: 修复 INIT 命令问题

如果看到 `INIT command failed`：

1. **检查认证信息**
```kotlin
// 确保使用正确的 appId 和 secretKey
val config = TaplinkConfig(
    appId = "correct_app_id",  // 检查这个值
    merchantId = "correct_merchant_id",
    secretKey = "correct_secret_key"  // 检查这个值
)
```

2. **联系 Tapro 应用开发者**
   - 提供详细的错误码和错误信息
   - 确认认证逻辑是否正确

### 解决方案 4: 添加重连机制

```kotlin
val config = TaplinkConfig(...)
    .setAutoReconnect(true)
    .setRetryPolicy(maxRetries = 3, delayMs = 2000L)

TaplinkSDK.init(context, config)
```

---

## 预防措施

### 1. 在应用中添加连接状态监控

```kotlin
class ConnectionMonitor {
    private var connectionStartTime = 0L
    
    fun startConnection() {
        connectionStartTime = System.currentTimeMillis()
        Log.d(TAG, "Starting APP TO APP connection...")
        
        TaplinkSDK.connect(null, object : ConnectionListener {
            override fun onConnected(deviceId: String, taproVersion: String) {
                val duration = System.currentTimeMillis() - connectionStartTime
                Log.d(TAG, "Connection successful in ${duration}ms")
            }
            
            override fun onError(error: ConnectionError) {
                val duration = System.currentTimeMillis() - connectionStartTime
                Log.e(TAG, "Connection failed after ${duration}ms: ${error.message}")
            }
        })
        
        // 设置超时检查
        Handler(Looper.getMainLooper()).postDelayed({
            if (!TaplinkSDK.isConnected()) {
                Log.w(TAG, "Connection timeout after 30 seconds")
                // 可以触发重试或显示错误信息
            }
        }, 30000)
    }
}
```

### 2. 实现连接健康检查

```kotlin
fun checkConnectionHealth() {
    if (TaplinkSDK.isConnected()) {
        Log.d(TAG, "Connection is healthy")
        Log.d(TAG, "Connected device: ${TaplinkSDK.getConnectedDeviceId()}")
        Log.d(TAG, "Connection mode: ${TaplinkSDK.getConnectionMode()}")
        Log.d(TAG, "Tapro version: ${TaplinkSDK.getTaproVersion()}")
    } else {
        Log.w(TAG, "Connection is not healthy, attempting reconnection...")
        TaplinkSDK.connect(null, connectionListener)
    }
}
```

### 3. 添加异常处理

```kotlin
try {
    TaplinkSDK.connect(null, object : ConnectionListener {
        override fun onConnected(deviceId: String, taproVersion: String) {
            try {
                // 处理连接成功
                handleConnectionSuccess(deviceId, taproVersion)
            } catch (e: Exception) {
                Log.e(TAG, "Error handling connection success", e)
            }
        }
        
        override fun onError(error: ConnectionError) {
            try {
                // 处理连接错误
                handleConnectionError(error)
            } catch (e: Exception) {
                Log.e(TAG, "Error handling connection error", e)
            }
        }
    })
} catch (e: Exception) {
    Log.e(TAG, "Error starting connection", e)
}
```

---

## 联系支持

如果按照以上步骤仍无法解决问题，请提供以下信息：

1. **设备信息**
   - Android 版本
   - 设备型号
   - Tapro 应用版本

2. **完整日志**
```bash
adb logcat -c  # 清除日志
# 尝试连接
adb logcat > connection_log.txt  # 保存日志
```

3. **配置信息**
   - TaplinkConfig 的配置
   - 使用的 appId（脱敏处理）
   - 连接模式

4. **问题复现步骤**
   - 详细的操作步骤
   - 问题出现的频率
   - 是否在特定条件下出现

---

**文档版本**: 1.0.0  
**更新日期**: 2025-12-22  
**作者**: TaPro Team