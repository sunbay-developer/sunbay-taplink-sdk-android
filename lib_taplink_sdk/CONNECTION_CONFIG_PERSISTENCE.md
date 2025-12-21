# 连接参数持久化功能

## 功能说明

SDK 现在支持保存上次成功连接的参数，当调用 `connect()` 方法时如果没有传递 `ConnectionConfig`，会自动使用上次的连接参数进行连接。

## 实现原理

### 1. 参数保存时机

连接参数在以下时机被保存：

```kotlin
// 调用 connect() 时，参数被保存到 ReconnectManager
reconnectManager?.prepareConnect(actualConfig, listener)
```

### 2. 参数使用时机

当没有传递 `ConnectionConfig` 时，自动使用上次的参数：

```kotlin
override fun connect(config: ConnectionConfig?, listener: ConnectionListener) {
    // 如果没有传递 config，尝试从 ReconnectManager 获取上次的连接参数
    val actualConfig = config ?: reconnectManager?.getLastConnectionConfig()
    
    if (actualConfig == null) {
        // 没有可用的连接参数，返回错误
        listener.onError(ConnectionError("C21", "No connection config available"))
        return
    }
    
    // 使用获取到的配置进行连接
    connectInternal(actualConfig, listener)
}
```

### 3. 参数清空时机

连接参数在主动断开连接时被清空：

```kotlin
override fun disconnect() {
    // ReconnectManager 会清空保存的连接参数
    reconnectManager?.disconnect()
}
```

## 使用示例

### 首次连接

```kotlin
// 首次连接，必须提供 ConnectionConfig
val config = ConnectionConfig.Builder()
    .setCableProtocol("lan://192.168.1.100:8080")
    .build()

TaplinkSDK.connect(config, object : ConnectionListener {
    override fun onConnected(deviceId: String, taproVersion: String) {
        Log.d(TAG, "Connected successfully")
        // 连接参数已自动保存
    }
    
    override fun onError(error: ConnectionError) {
        Log.e(TAG, "Connection failed: ${error.message}")
    }
})
```

### 重连（使用保存的参数）

```kotlin
// 重连时可以不传递 ConnectionConfig，会自动使用上次的参数
TaplinkSDK.connect(null, object : ConnectionListener {
    override fun onConnected(deviceId: String, taproVersion: String) {
        Log.d(TAG, "Reconnected using saved config")
    }
    
    override fun onError(error: ConnectionError) {
        if (error.code == "C21") {
            Log.e(TAG, "No saved connection config available")
            // 需要重新提供 ConnectionConfig
        }
    }
})
```

### 主动断开（清空参数）

```kotlin
// 主动断开连接，会清空保存的连接参数
TaplinkSDK.disconnect()

// 下次连接必须重新提供 ConnectionConfig
TaplinkSDK.connect(newConfig, listener)
```

## 生命周期管理

### 参数保存

```
connect(config) → prepareConnect(config) → 保存到 ReconnectManager
                ↓
            连接成功 → 参数保存确认
```

### 参数使用

```
connect(null) → getLastConnectionConfig() → 获取保存的参数
               ↓
           使用保存的参数连接
```

### 参数清空

```
disconnect() → ReconnectManager.disconnect() → 清空保存的参数
              ↓
          下次连接需要重新提供参数
```

## 错误处理

### 1. 没有保存的连接参数

**场景：** 首次连接或主动断开后，调用 `connect(null)`

**错误码：** `C21`

**错误信息：** `"No connection config provided and no previous connection config available"`

**处理方式：**
```kotlin
TaplinkSDK.connect(null, object : ConnectionListener {
    override fun onError(error: ConnectionError) {
        if (error.code == "C21") {
            // 需要提供新的连接配置
            val config = ConnectionConfig.Builder()
                .setCableProtocol("lan://192.168.1.100:8080")
                .build()
            TaplinkSDK.connect(config, this)
        }
    }
})
```

### 2. 保存的参数无效

**场景：** 保存的连接参数已过期或无效（如 IP 地址变化）

**处理方式：** 正常的连接错误处理，可以提供新的配置重试

## 与自动重连的关系

### 自动重连

自动重连使用相同的参数保存机制：

```kotlin
// 连接断开时，ReconnectManager 使用保存的参数自动重连
private fun attemptReconnect() {
    val config = lastConnectionConfig  // 使用保存的参数
    val listener = lastConnectionListener
    
    if (config != null && listener != null) {
        connectFunction(config, listener)
    }
}
```

### 手动重连

手动重连可以选择使用保存的参数或提供新参数：

```kotlin
// 使用保存的参数
TaplinkSDK.connect(null, listener)

// 或提供新参数
TaplinkSDK.connect(newConfig, listener)
```

## 配置更新

### 更新连接参数

如果需要更新连接参数，只需提供新的 `ConnectionConfig`：

```kotlin
// 原来连接到 192.168.1.100:8080
val oldConfig = ConnectionConfig.Builder()
    .setCableProtocol("lan://192.168.1.100:8080")
    .build()
TaplinkSDK.connect(oldConfig, listener)

// 更新到新地址，会覆盖保存的参数
val newConfig = ConnectionConfig.Builder()
    .setCableProtocol("lan://192.168.1.101:9090")
    .build()
TaplinkSDK.connect(newConfig, listener)

// 下次调用 connect(null) 会使用新地址
```

## 调试信息

### 关键日志

```
D/TaplinkApiImpl: Using connection config: provided
D/TaplinkApiImpl: Connection successful, config saved in ReconnectManager

D/TaplinkApiImpl: Using connection config: previous
D/TaplinkApiImpl: Connection successful, config saved in ReconnectManager

D/TaplinkApiImpl: Manual disconnect
D/ReconnectManager: Cleared saved connection config due to manual disconnect
```

### 状态检查

可以通过以下方式检查是否有保存的连接参数：

```kotlin
// 尝试使用保存的参数连接，如果失败说明没有保存的参数
TaplinkSDK.connect(null, object : ConnectionListener {
    override fun onError(error: ConnectionError) {
        if (error.code == "C21") {
            Log.d(TAG, "No saved connection config")
        }
    }
})
```

## 最佳实践

### 1. 首次连接

```kotlin
// 总是提供完整的连接配置
val config = ConnectionConfig.Builder()
    .setCableProtocol(protocol)
    .build()
TaplinkSDK.connect(config, listener)
```

### 2. 重连场景

```kotlin
// 可以不提供配置，使用保存的参数
TaplinkSDK.connect(null, listener)
```

### 3. 地址变更

```kotlin
// 提供新的配置，会自动更新保存的参数
val newConfig = ConnectionConfig.Builder()
    .setCableProtocol(newProtocol)
    .build()
TaplinkSDK.connect(newConfig, listener)
```

### 4. 完全重置

```kotlin
// 主动断开，清空所有保存的参数
TaplinkSDK.disconnect()

// 下次连接需要重新提供配置
TaplinkSDK.connect(config, listener)
```

## 兼容性

### 向后兼容

现有代码无需修改，仍然可以每次都提供 `ConnectionConfig`：

```kotlin
// 现有代码继续工作
TaplinkSDK.connect(config, listener)
```

### 新功能

新功能是可选的，可以选择使用：

```kotlin
// 新功能：使用保存的参数
TaplinkSDK.connect(null, listener)
```

## 相关文件

- `lib_taplink_sdk/src/main/java/com/sunmi/tapro/taplink/sdk/impl/TaplinkApiImpl.kt` - 主要实现
- `lib_taplink_sdk/src/main/java/com/sunmi/tapro/taplink/sdk/reconnect/ReconnectManager.kt` - 参数保存和管理

---

**文档版本**: 1.0.0  
**更新日期**: 2025-12-19  
**作者**: TaPro Team