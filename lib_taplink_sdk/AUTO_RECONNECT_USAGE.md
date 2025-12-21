# 自动重连功能使用指南

## 概述

Taplink SDK 提供了自动重连功能，当连接非主动断开时（如网络波动、设备临时离线等），SDK 会自动尝试重新连接，无需应用层手动处理。

## 功能特性

- ✅ **自动检测断线**：区分主动断开（调用 `disconnect()`）和非主动断开
- ✅ **智能重试**：可配置重试次数和重试间隔
- ✅ **状态通知**：通过回调通知应用层重连进度
- ✅ **透明重连**：重连成功后自动恢复连接状态
- ✅ **默认启用**：开箱即用，无需额外配置

## 配置方式

### 1. 使用默认配置（推荐）

默认情况下，自动重连功能已启用：
- 最大重试次数：3 次
- 重试间隔：2000 毫秒（2 秒）

```kotlin
val config = TaplinkConfig(
    appId = "your_app_id",
    merchantId = "your_merchant_id",
    secretKey = "your_secret_key"
    // autoReconnect = true (默认)
    // maxRetryCount = 3 (默认)
    // retryDelayMs = 2000L (默认)
)

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
    .setRetryPolicy(
        maxRetries = 5,      // 最多重试 5 次
        delayMs = 3000L      // 每次重试间隔 3 秒
    )

TaplinkSDK.init(context, config)
```

### 3. 禁用自动重连

如果需要手动控制重连逻辑：

```kotlin
val config = TaplinkConfig(
    appId = "your_app_id",
    merchantId = "your_merchant_id",
    secretKey = "your_secret_key"
)
    .setAutoReconnect(false)  // 禁用自动重连

TaplinkSDK.init(context, config)
```

## 监听重连状态

通过 `ConnectionListener` 接口监听重连事件：

```kotlin
TaplinkSDK.connect(connectionConfig, object : ConnectionListener {
    override fun onConnected(deviceId: String, taproVersion: String) {
        // 连接成功（包括重连成功）
        Log.d(TAG, "Connected to device: $deviceId")
        updateUI(connected = true)
    }
    
    override fun onDisconnected(reason: String) {
        // 连接断开（重试失败或达到最大重试次数）
        Log.e(TAG, "Disconnected: $reason")
        updateUI(connected = false)
        showReconnectDialog()
    }
    
    override fun onError(error: ConnectionError) {
        // 连接错误
        Log.e(TAG, "Connection error: ${error.message}")
        showErrorDialog(error)
    }
    
    override fun onReconnecting(attempt: Int, maxRetries: Int) {
        // 正在重连（可选实现）
        Log.d(TAG, "Reconnecting... ($attempt/$maxRetries)")
        showReconnectingToast("正在重连... ($attempt/$maxRetries)")
    }
})
```

## 工作流程

```
连接断开（非主动）
    ↓
SDK 检测到断线
    ↓
调用 onReconnecting(1, 3)
    ↓
等待 2 秒
    ↓
尝试重连
    ↓
成功？
├─ 是 → 调用 onConnected() → 恢复正常
└─ 否 → 重试次数 < 最大次数？
         ├─ 是 → 调用 onReconnecting(2, 3) → 继续重试
         └─ 否 → 调用 onDisconnected() → 停止重连
```

## 使用场景

### 场景 1：局域网模式（网络波动）

```kotlin
// 初始化时启用自动重连
val config = TaplinkConfig(
    appId = "your_app_id",
    merchantId = "your_merchant_id",
    secretKey = "your_secret_key"
)
    .setConnectionMode(ConnectionMode.LAN)
    .setAutoReconnect(true)
    .setRetryPolicy(maxRetries = 5, delayMs = 3000L)  // 网络重连可能需要更多时间

TaplinkSDK.init(context, config)

// 连接
val lanConfig = ConnectionConfig()
    .setHost("192.168.1.100")
    .setPort(8443)

TaplinkSDK.connect(lanConfig, object : ConnectionListener {
    override fun onReconnecting(attempt: Int, maxRetries: Int) {
        // 显示重连提示
        showToast("网络连接中断，正在重连... ($attempt/$maxRetries)")
    }
    
    override fun onConnected(deviceId: String, taproVersion: String) {
        showToast("连接已恢复")
    }
    
    override fun onDisconnected(reason: String) {
        // 重连失败，提示用户检查网络
        showDialog("连接失败", "请检查网络连接或重新配置设备")
    }
    
    override fun onError(error: ConnectionError) {
        showErrorDialog(error)
    }
})
```

### 场景 2：线缆模式（线缆松动）

```kotlin
val config = TaplinkConfig(
    appId = "your_app_id",
    merchantId = "your_merchant_id",
    secretKey = "your_secret_key"
)
    .setConnectionMode(ConnectionMode.CABLE)
    .setAutoReconnect(true)
    .setRetryPolicy(maxRetries = 3, delayMs = 2000L)

TaplinkSDK.init(context, config)

TaplinkSDK.connect(null, object : ConnectionListener {
    override fun onReconnecting(attempt: Int, maxRetries: Int) {
        showToast("线缆连接中断，正在重连...")
    }
    
    override fun onConnected(deviceId: String, taproVersion: String) {
        showToast("线缆连接已恢复")
    }
    
    override fun onDisconnected(reason: String) {
        showDialog("连接失败", "请检查线缆连接是否正常")
    }
    
    override fun onError(error: ConnectionError) {
        showErrorDialog(error)
    }
})
```

### 场景 3：应用间模式（服务重启）

```kotlin
val config = TaplinkConfig(
    appId = "your_app_id",
    merchantId = "your_merchant_id",
    secretKey = "your_secret_key"
)
    .setConnectionMode(ConnectionMode.APP_TO_APP)
    .setAutoReconnect(true)
    .setRetryPolicy(maxRetries = 3, delayMs = 1000L)  // IPC 重连较快

TaplinkSDK.init(context, config)

TaplinkSDK.connect(null, object : ConnectionListener {
    override fun onReconnecting(attempt: Int, maxRetries: Int) {
        Log.d(TAG, "Tapro service reconnecting...")
    }
    
    override fun onConnected(deviceId: String, taproVersion: String) {
        Log.d(TAG, "Tapro service reconnected")
    }
    
    override fun onDisconnected(reason: String) {
        showDialog("服务连接失败", "请确保 Tapro 应用正在运行")
    }
    
    override fun onError(error: ConnectionError) {
        if (error.code == "C22") {
            // Tapro 未安装
            showInstallTaproDialog()
        }
    }
})
```

## 最佳实践

### 1. 根据连接模式调整重试策略

```kotlin
val retryConfig = when (connectionMode) {
    ConnectionMode.APP_TO_APP -> {
        // IPC 连接快，重试间隔短
        Pair(3, 1000L)
    }
    ConnectionMode.CABLE -> {
        // 线缆连接中等，适中的重试策略
        Pair(3, 2000L)
    }
    ConnectionMode.LAN -> {
        // 网络连接慢，重试间隔长，次数多
        Pair(5, 3000L)
    }
    else -> Pair(3, 2000L)
}

val config = TaplinkConfig(
    appId = "your_app_id",
    merchantId = "your_merchant_id",
    secretKey = "your_secret_key"
)
    .setConnectionMode(connectionMode)
    .setRetryPolicy(maxRetries = retryConfig.first, delayMs = retryConfig.second)
```

### 2. 在 UI 中显示重连状态

```kotlin
class PaymentActivity : AppCompatActivity() {
    private lateinit var reconnectProgressBar: ProgressBar
    private lateinit var reconnectTextView: TextView
    
    private val connectionListener = object : ConnectionListener {
        override fun onReconnecting(attempt: Int, maxRetries: Int) {
            runOnUiThread {
                reconnectProgressBar.visibility = View.VISIBLE
                reconnectTextView.text = "正在重连... ($attempt/$maxRetries)"
            }
        }
        
        override fun onConnected(deviceId: String, taproVersion: String) {
            runOnUiThread {
                reconnectProgressBar.visibility = View.GONE
                reconnectTextView.text = "已连接"
                Toast.makeText(this@PaymentActivity, "连接成功", Toast.LENGTH_SHORT).show()
            }
        }
        
        override fun onDisconnected(reason: String) {
            runOnUiThread {
                reconnectProgressBar.visibility = View.GONE
                reconnectTextView.text = "未连接"
                showReconnectDialog(reason)
            }
        }
        
        override fun onError(error: ConnectionError) {
            runOnUiThread {
                reconnectProgressBar.visibility = View.GONE
                showErrorDialog(error)
            }
        }
    }
}
```

### 3. 处理支付过程中的断线

```kotlin
TaplinkSDK.execute(paymentRequest, object : PaymentCallback {
    override fun onSuccess(result: PaymentResult) {
        // 支付成功
        handlePaymentSuccess(result)
    }
    
    override fun onFailure(error: PaymentError) {
        // 检查是否是连接错误
        if (error.code == "NOT_CONNECTED" || error.code == "CONNECTION_LOST") {
            // 连接断开，SDK 会自动重连
            showDialog(
                "连接中断",
                "正在尝试重新连接，请稍候...",
                positiveButton = "等待",
                negativeButton = "取消"
            ) { cancelled ->
                if (cancelled) {
                    // 用户取消，停止重连
                    TaplinkSDK.disconnect()
                }
            }
        } else {
            handlePaymentError(error)
        }
    }
    
    override fun onProgress(event: PaymentEvent) {
        updatePaymentProgress(event)
    }
})
```

## 注意事项

1. **主动断开不会触发重连**
   - 调用 `TaplinkSDK.disconnect()` 后不会自动重连
   - 需要手动调用 `TaplinkSDK.connect()` 重新连接

2. **重连期间的请求处理**
   - 重连期间发起的支付请求会失败
   - 建议在 UI 中禁用支付按钮，直到重连成功

3. **重试次数限制**
   - 达到最大重试次数后会停止重连
   - 需要用户手动重新连接或重启应用

4. **资源清理**
   - 重连失败后会自动清理所有待处理的请求回调
   - 应用层需要处理未完成的交易

5. **线程安全**
   - 所有回调都在主线程执行
   - 可以直接更新 UI，无需切换线程

## 常见问题

### Q: 如何判断是重连成功还是首次连接成功？

A: `onConnected()` 回调对首次连接和重连成功都会触发。如果需要区分，可以在应用层维护一个状态标志：

```kotlin
private var isFirstConnection = true

override fun onConnected(deviceId: String, taproVersion: String) {
    if (isFirstConnection) {
        Log.d(TAG, "First connection successful")
        isFirstConnection = false
    } else {
        Log.d(TAG, "Reconnection successful")
    }
}

override fun onDisconnected(reason: String) {
    // 断开后，下次连接算作重连
}
```

### Q: 重连期间可以发起支付吗？

A: 不可以。重连期间 `isConnected()` 返回 `false`，发起支付会返回 `NOT_CONNECTED` 错误。建议在 UI 中禁用支付功能：

```kotlin
override fun onReconnecting(attempt: Int, maxRetries: Int) {
    enablePaymentButtons(false)
}

override fun onConnected(deviceId: String, taproVersion: String) {
    enablePaymentButtons(true)
}
```

### Q: 如何手动触发重连？

A: 如果禁用了自动重连，可以在 `onDisconnected()` 中手动调用 `connect()`：

```kotlin
override fun onDisconnected(reason: String) {
    // 显示重连按钮
    showReconnectButton {
        TaplinkSDK.connect(lastConfig, this)
    }
}
```

### Q: 重连会影响性能吗？

A: 不会。重连是异步执行的，不会阻塞主线程。重试间隔期间 SDK 处于空闲状态，不消耗资源。

## 总结

自动重连功能让 Taplink SDK 更加健壮和易用：

- ✅ **开箱即用**：默认启用，无需额外配置
- ✅ **灵活配置**：可根据场景调整重试策略
- ✅ **透明处理**：自动处理断线重连，减少应用层代码
- ✅ **状态通知**：完整的回调机制，便于 UI 更新

建议在生产环境中启用自动重连功能，提升用户体验。
