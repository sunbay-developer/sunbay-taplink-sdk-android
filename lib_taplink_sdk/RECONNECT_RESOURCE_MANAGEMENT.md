# 重连期间的资源管理策略

## 问题背景

在实现自动重连功能时，需要仔细考虑资源管理策略：
- 什么时候清理资源？
- 重连期间应该保留哪些信息？
- 如何避免资源泄漏？

## 设计决策

### ✅ 正确的做法：只在不重连时清理资源

```kotlin
override fun onDisconnected(code: String, msg: String) {
    updateConnectionStatus(ConnectionStatus.DISCONNECTED)
    val reason = "Code: $code, Message: $msg"
    
    // 尝试自动重连
    val reconnected = reconnectManager?.onDisconnected(listener) ?: false
    
    if (!reconnected) {
        // 不重连，通知应用层并清理资源
        listener.onDisconnected(reason)
        connectionListener?.onDisconnected(reason)
        
        // 清理资源（只在不重连时清理）
        connectedDeviceId = null
        taproVersion = null
        currentConnectionMode = null
        callbackManager.clearAll()
    }
    // 如果正在重连，保留设备信息和连接模式，不清理回调管理器
}
```

### ❌ 错误的做法：总是清理资源

```kotlin
override fun onDisconnected(code: String, msg: String) {
    updateConnectionStatus(ConnectionStatus.DISCONNECTED)
    val reason = "Code: $code, Message: $msg"
    
    // 尝试自动重连
    val reconnected = reconnectManager?.onDisconnected(listener) ?: false
    
    if (!reconnected) {
        listener.onDisconnected(reason)
        connectionListener?.onDisconnected(reason)
    }
    
    // ❌ 错误：总是清理资源，即使正在重连
    connectedDeviceId = null
    taproVersion = null
    currentConnectionMode = null
    callbackManager.clearAll()
}
```

## 原因分析

### 1. 保留设备信息的必要性

**重连期间需要设备信息：**

```kotlin
override fun onReconnecting(attempt: Int, maxRetries: Int) {
    // 需要显示正在重连到哪个设备
    val deviceId = TaplinkSDK.getConnectedDeviceId()  // 如果清理了就是 null
    showToast("正在重连到设备 $deviceId... ($attempt/$maxRetries)")
}
```

**如果清理了设备信息：**
- ❌ 无法显示正在重连的设备 ID
- ❌ 用户不知道重连的目标设备
- ❌ 日志中缺少关键信息

### 2. 保留连接模式的必要性

**重连需要知道连接模式：**

```kotlin
// 重连时需要知道之前的连接模式
val mode = TaplinkSDK.getConnectionMode()  // 如果清理了就是 null
when (mode) {
    ConnectionMode.LAN -> showMessage("网络连接中断，正在重连...")
    ConnectionMode.CABLE -> showMessage("线缆连接中断，正在重连...")
    ConnectionMode.APP_TO_APP -> showMessage("服务连接中断，正在重连...")
}
```

**如果清理了连接模式：**
- ❌ 无法显示准确的重连提示
- ❌ 无法根据连接模式调整 UI
- ❌ 日志中缺少连接模式信息

### 3. 保留回调管理器的必要性

**重连期间可能有待处理的请求：**

```kotlin
// 场景：支付请求发送后，连接断开，正在重连
TaplinkSDK.execute(paymentRequest, callback)
// 连接断开...
// 正在重连...
// 重连成功，收到支付响应

// 如果清理了 callbackManager，响应无法找到对应的回调
```

**如果清理了回调管理器：**
- ❌ 重连成功后收到的响应无法处理
- ❌ 应用层永远收不到回调
- ❌ 可能导致交易状态不一致

**注意：** 虽然保留回调管理器，但由于重连期间连接已断开，实际上不太可能收到响应。这里保留是为了处理边界情况（如重连非常快，响应延迟到达）。

## 资源管理策略

### 重连期间保留的资源

| 资源 | 保留原因 | 使用场景 |
|------|----------|----------|
| `connectedDeviceId` | 显示重连目标 | UI 提示、日志记录 |
| `taproVersion` | 设备信息完整性 | 版本兼容性检查 |
| `currentConnectionMode` | 重连策略调整 | UI 提示、日志记录 |
| `callbackManager` | 处理延迟响应 | 边界情况处理 |

### 不重连时清理的资源

当确定不重连时（主动断开或达到最大重试次数），清理所有资源：

```kotlin
if (!reconnected) {
    // 清理设备信息
    connectedDeviceId = null
    taproVersion = null
    currentConnectionMode = null
    
    // 清理所有待处理的回调
    callbackManager.clearAll()
    
    // 通知应用层
    listener.onDisconnected(reason)
    connectionListener?.onDisconnected(reason)
}
```

### 重连成功后的资源更新

重连成功后，设备信息会被更新：

```kotlin
override fun onConnected(extraInfoMap: Map<String, String?>?) {
    // 更新设备信息（可能与之前相同，也可能不同）
    connectedDeviceId = extraInfoMap?.getOrDefault("deviceId", "unknown")
    taproVersion = extraInfoMap?.getOrDefault("taproVersion", "unknown")
    
    // 连接模式保持不变（因为重连使用相同的连接方式）
}
```

## 实际场景示例

### 场景 1：局域网重连（保留设备信息）

```kotlin
// 1. 初始连接
TaplinkSDK.connect(lanConfig, listener)
// connectedDeviceId = "DEVICE_12345"
// currentConnectionMode = ConnectionMode.LAN

// 2. 网络波动，连接断开
// onDisconnected() 被调用
// reconnected = true（触发自动重连）
// 保留：connectedDeviceId = "DEVICE_12345"
// 保留：currentConnectionMode = ConnectionMode.LAN

// 3. 重连期间
override fun onReconnecting(attempt: Int, maxRetries: Int) {
    val deviceId = TaplinkSDK.getConnectedDeviceId()  // "DEVICE_12345"
    val mode = TaplinkSDK.getConnectionMode()  // ConnectionMode.LAN
    showToast("正在重连到设备 $deviceId (网络)... ($attempt/$maxRetries)")
}

// 4. 重连成功
override fun onConnected(deviceId: String, taproVersion: String) {
    // deviceId = "DEVICE_12345"（相同设备）
    showToast("已重新连接到设备 $deviceId")
}
```

### 场景 2：达到最大重试次数（清理资源）

```kotlin
// 1. 初始连接
TaplinkSDK.connect(cableConfig, listener)
// connectedDeviceId = "DEVICE_67890"

// 2. 线缆断开
// onDisconnected() 被调用
// 第 1 次重试失败...
// 第 2 次重试失败...
// 第 3 次重试失败...

// 3. 达到最大重试次数
// reconnected = false
// 清理：connectedDeviceId = null
// 清理：currentConnectionMode = null
// 清理：callbackManager.clearAll()

override fun onDisconnected(reason: String) {
    val deviceId = TaplinkSDK.getConnectedDeviceId()  // null
    showDialog("连接失败", "无法连接到设备，请检查线缆连接")
}
```

### 场景 3：主动断开（清理资源）

```kotlin
// 1. 用户点击断开按钮
TaplinkSDK.disconnect()

// 2. onDisconnected() 被调用
// isManualDisconnect = true
// reconnected = false（不触发重连）
// 清理所有资源

// 3. 资源已清理
val deviceId = TaplinkSDK.getConnectedDeviceId()  // null
val mode = TaplinkSDK.getConnectionMode()  // null
```

## 边界情况处理

### 情况 1：重连期间收到响应

虽然不太可能，但理论上可能发生：

```kotlin
// 1. 发送支付请求
TaplinkSDK.execute(paymentRequest, callback)

// 2. 连接断开（但请求已发送）
// 保留 callbackManager

// 3. 正在重连...

// 4. 重连成功

// 5. 收到延迟的响应
// callbackManager 找到对应的回调并处理
callback.onSuccess(result)
```

### 情况 2：重连期间发起新请求

```kotlin
// 1. 连接断开，正在重连
// isConnected() = false

// 2. 用户尝试发起支付
TaplinkSDK.execute(paymentRequest, callback)

// 3. SDK 检查连接状态
if (!isConnected()) {
    callback.onFailure(PaymentError("NOT_CONNECTED", "Not connected"))
    return
}
```

### 情况 3：重连失败后再次连接

```kotlin
// 1. 重连失败，资源已清理
// connectedDeviceId = null

// 2. 用户手动重新连接
TaplinkSDK.connect(config, listener)

// 3. 连接成功，设备信息重新设置
// connectedDeviceId = "DEVICE_12345"
```

## 最佳实践

### 1. 在 UI 中显示设备信息

```kotlin
override fun onReconnecting(attempt: Int, maxRetries: Int) {
    val deviceId = TaplinkSDK.getConnectedDeviceId()
    val mode = TaplinkSDK.getConnectionMode()
    
    val modeText = when (mode) {
        ConnectionMode.LAN -> "网络"
        ConnectionMode.CABLE -> "线缆"
        ConnectionMode.APP_TO_APP -> "服务"
        else -> "未知"
    }
    
    statusTextView.text = "正在重连到设备 $deviceId ($modeText)... ($attempt/$maxRetries)"
}
```

### 2. 重连期间禁用操作

```kotlin
override fun onReconnecting(attempt: Int, maxRetries: Int) {
    // 禁用支付按钮
    paymentButton.isEnabled = false
    
    // 显示重连进度
    progressBar.visibility = View.VISIBLE
}

override fun onConnected(deviceId: String, taproVersion: String) {
    // 启用支付按钮
    paymentButton.isEnabled = true
    
    // 隐藏进度
    progressBar.visibility = View.GONE
}
```

### 3. 记录详细日志

```kotlin
override fun onDisconnected(code: String, msg: String) {
    val deviceId = connectedDeviceId
    val mode = currentConnectionMode
    
    if (reconnected) {
        Log.d(TAG, "Connection lost to device $deviceId ($mode), attempting reconnect...")
    } else {
        Log.e(TAG, "Connection lost to device $deviceId ($mode), giving up")
    }
}
```

## 总结

### 关键原则

1. ✅ **重连期间保留资源**：设备信息、连接模式、回调管理器
2. ✅ **不重连时清理资源**：主动断开或达到最大重试次数
3. ✅ **重连成功后更新资源**：设备信息可能变化
4. ✅ **边界情况处理**：延迟响应、重连期间的请求

### 优势

- ✅ 更好的用户体验：显示准确的重连信息
- ✅ 更完整的日志：包含设备和连接模式信息
- ✅ 更健壮的处理：处理边界情况
- ✅ 更清晰的状态：区分重连和断开

### 注意事项

- ⚠️ 重连期间 `isConnected()` 返回 `false`
- ⚠️ 重连期间无法发起新的支付请求
- ⚠️ 回调管理器保留但实际响应可能丢失
- ⚠️ 重连失败后资源会被清理

---

**文档版本**: 1.0  
**更新日期**: 2025-12-11  
**作者**: TaPro Team
