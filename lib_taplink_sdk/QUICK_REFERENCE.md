# 自动重连功能 - 快速参考

## 🚀 快速开始

### 1. 使用默认配置（推荐）

```kotlin
val config = TaplinkConfig(
    appId = "your_app_id",
    merchantId = "your_merchant_id",
    secretKey = "your_secret_key"
)
// 自动重连已默认启用

TaplinkSDK.init(context, config)
```

### 2. 自定义重连策略

```kotlin
val config = TaplinkConfig(
    appId = "your_app_id",
    merchantId = "your_merchant_id",
    secretKey = "your_secret_key"
)
    .setAutoReconnect(true)
    .setRetryPolicy(maxRetries = 5, delayMs = 3000L)

TaplinkSDK.init(context, config)
```

### 3. 监听重连事件

```kotlin
TaplinkSDK.connect(connectionConfig, object : ConnectionListener {
    override fun onConnected(deviceId: String, taproVersion: String) {
        // 连接成功
    }
    
    override fun onDisconnected(reason: String) {
        // 连接断开
    }
    
    override fun onError(error: ConnectionError) {
        // 连接错误
    }
    
    override fun onReconnecting(attempt: Int, maxRetries: Int) {
        // 正在重连（可选）
        showToast("正在重连... ($attempt/$maxRetries)")
    }
})
```

## ⚙️ 配置参数

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `autoReconnect` | Boolean | `true` | 是否启用自动重连 |
| `maxRetryCount` | Int | `3` | 最大重试次数 |
| `retryDelayMs` | Long | `2000L` | 重试延迟（毫秒）|

## 📋 推荐配置

| 连接模式 | 重试次数 | 延迟时间 |
|---------|---------|---------|
| APP_TO_APP | 3 | 1000ms |
| CABLE | 3 | 2000ms |
| LAN | 5 | 3000ms |

## 🔄 重连触发条件

自动重连在以下条件**全部满足**时触发：

- ✅ `autoReconnect = true`
- ✅ 非主动断开（不是调用 `disconnect()`）
- ✅ 当前重试次数 < 最大重试次数

## 🚫 不触发重连的情况

- ❌ 主动调用 `disconnect()`
- ❌ `autoReconnect = false`
- ❌ 已达到最大重试次数
- ❌ SDK 未初始化

## 📞 回调方法

### onReconnecting()
```kotlin
override fun onReconnecting(attempt: Int, maxRetries: Int) {
    // 每次重连尝试前调用
    // attempt: 当前重试次数（1-based）
    // maxRetries: 最大重试次数
}
```

### onConnected()
```kotlin
override fun onConnected(deviceId: String, taproVersion: String) {
    // 连接成功时调用（包括重连成功）
}
```

### onDisconnected()
```kotlin
override fun onDisconnected(reason: String) {
    // 连接断开时调用（重试失败或达到最大次数）
}
```

## 💡 常见用法

### 禁用自动重连

```kotlin
val config = TaplinkConfig(...)
    .setAutoReconnect(false)
```

### 根据连接模式配置

```kotlin
val (maxRetries, delayMs) = when (connectionMode) {
    ConnectionMode.APP_TO_APP -> Pair(3, 1000L)
    ConnectionMode.CABLE -> Pair(3, 2000L)
    ConnectionMode.LAN -> Pair(5, 3000L)
    else -> Pair(3, 2000L)
}

val config = TaplinkConfig(...)
    .setConnectionMode(connectionMode)
    .setRetryPolicy(maxRetries, delayMs)
```

### 显示重连进度

```kotlin
override fun onReconnecting(attempt: Int, maxRetries: Int) {
    runOnUiThread {
        progressBar.visibility = View.VISIBLE
        statusText.text = "正在重连... ($attempt/$maxRetries)"
    }
}

override fun onConnected(deviceId: String, taproVersion: String) {
    runOnUiThread {
        progressBar.visibility = View.GONE
        statusText.text = "已连接"
    }
}
```

## 🔍 调试技巧

### 启用日志

```kotlin
val config = TaplinkConfig(...)
    .setLogEnabled(true)
    .setLogLevel(LogLevel.DEBUG)
```

### 检查重连状态

```kotlin
// 检查是否已连接
if (TaplinkSDK.isConnected()) {
    // 已连接
} else {
    // 未连接（可能正在重连）
}
```

## ⚠️ 注意事项

1. **重连期间无法发起支付**
   - `isConnected()` 返回 `false`
   - 建议禁用支付按钮

2. **主动断开不会重连**
   - 调用 `disconnect()` 后需要手动 `connect()`

3. **达到最大重试次数后停止**
   - 需要用户手动重新连接

4. **所有回调在主线程执行**
   - 可以直接更新 UI

## 📖 完整文档

- [详细使用指南](AUTO_RECONNECT_USAGE.md)
- [更新日志](CHANGELOG_AUTO_RECONNECT.md)
- [示例代码](EXAMPLE_AUTO_RECONNECT.kt)
- [重构总结](REFACTORING_SUMMARY.md)
- [实现总结](AUTO_RECONNECT_IMPLEMENTATION.md)

## 🆘 常见问题

**Q: 如何判断是首次连接还是重连？**

A: `onConnected()` 对两者都会触发，可以在应用层维护标志：

```kotlin
private var isFirstConnection = true

override fun onConnected(deviceId: String, taproVersion: String) {
    if (isFirstConnection) {
        // 首次连接
        isFirstConnection = false
    } else {
        // 重连成功
    }
}
```

**Q: 重连期间可以发起支付吗？**

A: 不可以。建议禁用支付功能：

```kotlin
override fun onReconnecting(attempt: Int, maxRetries: Int) {
    paymentButton.isEnabled = false
}

override fun onConnected(deviceId: String, taproVersion: String) {
    paymentButton.isEnabled = true
}
```

**Q: 如何手动触发重连？**

A: 如果禁用了自动重连，可以手动调用：

```kotlin
override fun onDisconnected(reason: String) {
    showReconnectButton {
        TaplinkSDK.connect(lastConfig, this)
    }
}
```

---

**版本**: 1.1.0  
**更新日期**: 2025-12-11
