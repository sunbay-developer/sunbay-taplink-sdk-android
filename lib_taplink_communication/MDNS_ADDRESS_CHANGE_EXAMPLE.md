# mDNS 地址变更通知使用示例

## 基本使用

### 1. 启用自动重连配置

```kotlin
val taplinkConfig = TaplinkConfig.Builder()
    .appId("your_app_id")
    .secretKey("your_secret_key")
    .autoReconnect(true)        // 启用自动重连
    .maxRetryCount(5)           // 最大重试次数
    .retryDelayMs(3000)         // 重试延迟
    .build()

// 初始化SDK
TaplinkApi.init(context, taplinkConfig)
```

### 2. 设置连接监听器

```kotlin
val connectionListener = object : ConnectionListener {
    override fun onConnected(deviceId: String, taproVersion: String) {
        Log.d(TAG, "Connected to device: $deviceId, version: $taproVersion")
    }
    
    override fun onDisconnected(reason: String) {
        Log.d(TAG, "Disconnected: $reason")
    }
    
    override fun onError(error: ConnectionError) {
        Log.e(TAG, "Connection error: ${error.message}")
    }
    
    override fun onReconnecting(attempt: Int, maxRetries: Int) {
        Log.i(TAG, "Reconnecting... ($attempt/$maxRetries)")
        // 可以在这里显示重连进度给用户
        showReconnectingDialog(attempt, maxRetries)
    }
}

TaplinkApi.setConnectionListener(connectionListener)
```

### 3. 连接到LAN设备

```kotlin
val connectionConfig = ConnectionConfig(
    host = "192.168.1.100",
    port = 8443
)

TaplinkApi.connect(connectionConfig, object : ConnectionListener {
    override fun onConnected(deviceId: String, taproVersion: String) {
        Log.d(TAG, "Initial connection successful")
    }
    
    override fun onDisconnected(reason: String) {
        Log.d(TAG, "Connection lost: $reason")
    }
    
    override fun onError(error: ConnectionError) {
        Log.e(TAG, "Connection failed: ${error.message}")
    }
    
    override fun onReconnecting(attempt: Int, maxRetries: Int) {
        Log.i(TAG, "Auto-reconnecting due to address change... ($attempt/$maxRetries)")
    }
})
```

## 地址变更场景示例

### 场景1：设备IP地址变更

```
初始连接: 192.168.1.100:8443
↓
mDNS检测到地址变更: 192.168.1.101:8443
↓
SDK自动重连到新地址
↓
连接恢复正常
```

**日志输出：**
```
I/WebSocketClientKernel: Service address changed: 192.168.1.100:8443 -> 192.168.1.101:8443
I/TaplinkApiImpl: Service address changed detected: 192.168.1.100:8443 -> 192.168.1.101:8443
I/ReconnectManager: Service address changed, updating connection config and reconnecting
D/ReconnectManager: Attempting to connect to new address immediately
D/TaplinkApiImpl: Connected to device: DEVICE_001, version: 1.0.0
```

### 场景2：服务端口变更

```
初始连接: 192.168.1.100:8443
↓
mDNS检测到端口变更: 192.168.1.100:8444
↓
SDK自动重连到新端口
↓
连接恢复正常
```

### 场景3：IP和端口同时变更

```
初始连接: 192.168.1.100:8443
↓
mDNS检测到完全变更: 192.168.1.101:8444
↓
SDK自动重连到新地址
↓
连接恢复正常
```

## 高级用法

### 1. 自定义重连策略

```kotlin
class CustomConnectionListener : ConnectionListener {
    private var addressChangeCount = 0
    
    override fun onReconnecting(attempt: Int, maxRetries: Int) {
        if (attempt == 0) {
            // attempt为0表示是地址变更导致的重连
            addressChangeCount++
            Log.i(TAG, "Address changed $addressChangeCount times, reconnecting...")
            
            // 可以根据地址变更频率调整策略
            if (addressChangeCount > 5) {
                Log.w(TAG, "Too many address changes, consider checking network stability")
            }
        } else {
            // attempt > 0表示是连接失败的重试
            Log.i(TAG, "Connection retry $attempt/$maxRetries")
        }
    }
}
```

### 2. 监控地址变更

```kotlin
class AddressChangeMonitor : ConnectionListener {
    private val addressHistory = mutableListOf<String>()
    
    override fun onConnected(deviceId: String, taproVersion: String) {
        // 记录成功连接的地址（可以从其他途径获取当前地址）
        val currentAddress = getCurrentConnectedAddress()
        addressHistory.add(currentAddress)
        
        Log.d(TAG, "Connected to $currentAddress")
        Log.d(TAG, "Address history: $addressHistory")
    }
    
    override fun onReconnecting(attempt: Int, maxRetries: Int) {
        if (attempt == 0) {
            Log.i(TAG, "Device address changed, attempting reconnection")
            // 可以通知用户设备地址发生了变更
            notifyUserAddressChanged()
        }
    }
    
    private fun getCurrentConnectedAddress(): String {
        // 实现获取当前连接地址的逻辑
        return "unknown"
    }
    
    private fun notifyUserAddressChanged() {
        // 通知用户地址变更
    }
}
```

### 3. 错误处理和降级

```kotlin
class RobustConnectionListener : ConnectionListener {
    private var consecutiveAddressChanges = 0
    private val maxAddressChanges = 10
    
    override fun onReconnecting(attempt: Int, maxRetries: Int) {
        if (attempt == 0) {
            consecutiveAddressChanges++
            
            if (consecutiveAddressChanges >= maxAddressChanges) {
                Log.w(TAG, "Too many consecutive address changes, stopping auto-reconnect")
                // 可以选择停止自动重连，让用户手动处理
                TaplinkApi.disconnect()
                showManualReconnectDialog()
                return
            }
        }
    }
    
    override fun onConnected(deviceId: String, taproVersion: String) {
        // 连接成功后重置计数器
        consecutiveAddressChanges = 0
    }
    
    override fun onError(error: ConnectionError) {
        if (consecutiveAddressChanges > 0) {
            Log.e(TAG, "Connection failed after address change: ${error.message}")
            // 可能是新地址无效，考虑回退到旧地址
        }
    }
    
    private fun showManualReconnectDialog() {
        // 显示对话框让用户手动重连
    }
}
```

## 测试和调试

### 1. 模拟地址变更

```kotlin
// 在测试环境中，可以通过修改mDNS响应来模拟地址变更
class TestAddressChangeSimulator {
    fun simulateAddressChange(newHost: String, newPort: Int) {
        // 模拟mDNS发现新地址
        val mockServiceInfo = createMockServiceInfo(newHost, newPort)
        webSocketKernel.onServiceFound(mockServiceInfo)
    }
}
```

### 2. 日志监控

```kotlin
// 启用详细日志来监控地址变更过程
LogUtil.setLevel(Log.DEBUG)

// 关键日志点：
// - WebSocketClientKernel: 地址变更检测
// - TaplinkApiImpl: 地址变更处理
// - ReconnectManager: 重连逻辑执行
```

### 3. 性能监控

```kotlin
class PerformanceMonitor : ConnectionListener {
    private var reconnectStartTime = 0L
    
    override fun onReconnecting(attempt: Int, maxRetries: Int) {
        if (attempt == 0) {
            reconnectStartTime = System.currentTimeMillis()
        }
    }
    
    override fun onConnected(deviceId: String, taproVersion: String) {
        if (reconnectStartTime > 0) {
            val reconnectDuration = System.currentTimeMillis() - reconnectStartTime
            Log.i(TAG, "Address change reconnection took ${reconnectDuration}ms")
            reconnectStartTime = 0L
        }
    }
}
```

## 最佳实践

1. **启用自动重连**：确保TaplinkConfig中启用了autoReconnect
2. **合理设置重试次数**：根据网络环境设置合适的maxRetryCount
3. **监听重连事件**：实现onReconnecting方法来处理重连状态
4. **错误处理**：对频繁的地址变更进行监控和处理
5. **用户体验**：在重连过程中给用户适当的提示
6. **日志记录**：记录地址变更历史，便于问题排查

这个机制确保了在LAN环境下，即使设备地址发生变更，SDK也能自动适应并保持连接的稳定性。