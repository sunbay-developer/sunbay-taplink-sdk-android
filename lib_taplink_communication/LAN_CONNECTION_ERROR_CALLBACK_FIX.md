# LAN 连接错误回调修复说明

## 问题描述

在 LAN 连接过程中，当连接失败时，用户期望收到 `ConnectionListener.onError()` 回调，但实际上收到的是 `onDisconnected()` 回调。这导致用户无法正确区分连接错误和正常断开连接。

## 问题原因分析

### 1. 回调链路分析

```
WebSocketClientKernel.onError()
    ↓
BaseServiceKernel.handleConnectionError()
    ↓
ConnectionCallback.onDisconnected(code, msg)  // ❌ 调用的是 onDisconnected
    ↓
ConnectionManager.onDisconnected()
    ↓
ConnectionListener.onDisconnected(reason)     // ❌ 用户收到的是 onDisconnected
```

**期望的回调链路：**
```
WebSocketClientKernel.onError()
    ↓
ConnectionManager 判断错误类型
    ↓
ConnectionListener.onError(ConnectionError)   // ✅ 用户应该收到 onError
```

### 2. 接口设计问题

- **ConnectionCallback** 接口只有 `onDisconnected(code, msg)` 方法
- **ConnectionListener** 接口有 `onError(ConnectionError)` 和 `onDisconnected(reason)` 两个方法
- **ConnectionManager** 没有区分连接错误和正常断开

### 3. 具体场景

**连接错误场景：**
- WebSocket 连接失败
- 服务发现失败
- 协议解析错误
- 网络不可达

**正常断开场景：**
- 用户主动断开
- 服务端正常关闭
- 心跳超时（网络问题）

## 修复方案

### 1. 修复 ConnectionManager.onDisconnected()

在 `ConnectionManager` 中添加错误类型判断逻辑：

```kotlin
override fun onDisconnected(code: String, msg: String) {
    // 判断是连接错误还是正常断开
    val isConnectionError = isConnectionError(code, msg)
    
    if (isConnectionError) {
        // 创建连接错误对象并调用 onError
        val connectionError = ConnectionError(code, msg)
        listener.onError(connectionError)
        connectionListener?.onError(connectionError)
    } else {
        // 正常断开，调用 onDisconnected
        listener.onDisconnected(reason)
        connectionListener?.onDisconnected(reason)
    }
}
```

### 2. 添加错误类型判断方法

```kotlin
private fun isConnectionError(code: String, msg: String): Boolean {
    // 连接阶段的错误码
    val connectionErrorCodes = setOf(
        "C20", "C22", "C25", "C33", "C35", "C36", "C41", // 通用连接错误
        "-1", // BaseServiceKernel 默认错误码
        "1006", "1002", "1015", // WebSocket 连接错误码
        "CONNECTION_FAILED", "WEBSOCKET_NULL", "SEND_FAILED" // 自定义错误码
    )
    
    // 正常断开的错误码
    val normalDisconnectCodes = setOf(
        "1000", // WebSocket 正常关闭
        "1001", // WebSocket 端点离开
        "MANUAL_DISCONNECT", // 手动断开
        "HEARTBEAT_TIMEOUT" // 心跳超时
    )
    
    // 判断逻辑
    return when {
        normalDisconnectCodes.contains(code) -> false
        connectionErrorCodes.contains(code) -> true
        connectionStatus == ConnectionStatus.CONNECTING -> true
        else -> false
    }
}
```

## 修复效果

### 修复前的用户体验

```kotlin
TaplinkSDK.connect(config, object : ConnectionListener {
    override fun onConnected(deviceId: String, taproVersion: String) {
        // 连接成功
    }
    
    override fun onError(error: ConnectionError) {
        // ❌ 连接错误时不会调用这个方法
        Log.e(TAG, "Connection error: ${error.message}")
    }
    
    override fun onDisconnected(reason: String) {
        // ❌ 连接错误时会错误地调用这个方法
        Log.w(TAG, "Disconnected: $reason")
        // 用户无法区分是连接失败还是正常断开
    }
})
```

### 修复后的用户体验

```kotlin
TaplinkSDK.connect(config, object : ConnectionListener {
    override fun onConnected(deviceId: String, taproVersion: String) {
        // 连接成功
    }
    
    override fun onError(error: ConnectionError) {
        // ✅ 连接错误时正确调用这个方法
        Log.e(TAG, "Connection error: ${error.code} - ${error.message}")
        
        // 用户可以根据错误码进行相应处理
        when (error.code) {
            "C35" -> showMessage("未找到 Tapro 设备")
            "C33" -> showMessage("网络连接失败")
            "C41" -> showMessage("协议格式错误")
            else -> showMessage("连接失败：${error.message}")
        }
    }
    
    override fun onDisconnected(reason: String) {
        // ✅ 只有正常断开时才会调用这个方法
        Log.w(TAG, "Connection disconnected: $reason")
        showMessage("连接已断开")
    }
})
```

## 错误码映射

### 连接错误码

| 错误码 | 含义 | 来源 |
|--------|------|------|
| C20 | SDK 未初始化 | BaseServiceKernel |
| C22 | 服务未找到 | LocalServiceKernel |
| C25 | 服务绑定失败 | LocalServiceKernel |
| C33 | 连接失败 | WebSocketClientKernel |
| C35 | 服务发现失败 | WebSocketClientKernel |
| C36 | 服务连接死亡 | LocalServiceKernel |
| C41 | 协议格式错误 | ProtocolManager |
| -1 | 默认错误 | BaseServiceKernel |
| 1006 | WebSocket 异常关闭 | WebSocket |
| 1002 | WebSocket 协议错误 | WebSocket |
| 1015 | TLS 握手失败 | WebSocket |

### 正常断开码

| 错误码 | 含义 | 来源 |
|--------|------|------|
| 1000 | 正常关闭 | WebSocket |
| 1001 | 端点离开 | WebSocket |
| MANUAL_DISCONNECT | 手动断开 | SDK |
| HEARTBEAT_TIMEOUT | 心跳超时 | WebSocketClientKernel |

## 测试验证

### 1. 连接错误测试

```kotlin
@Test
fun testConnectionError() {
    val errorReceived = CountDownLatch(1)
    var receivedError: ConnectionError? = null
    
    val config = ConnectionConfig().apply {
        setHost("192.168.1.999") // 无效IP
        setPort(8080)
    }
    
    TaplinkSDK.connect(config, object : ConnectionListener {
        override fun onError(error: ConnectionError) {
            receivedError = error
            errorReceived.countDown()
        }
        
        override fun onDisconnected(reason: String) {
            fail("Should receive onError, not onDisconnected")
        }
        
        override fun onConnected(deviceId: String, taproVersion: String) {
            fail("Should not connect to invalid IP")
        }
    })
    
    assertTrue(errorReceived.await(10, TimeUnit.SECONDS))
    assertNotNull(receivedError)
    assertEquals("C33", receivedError?.code) // 连接失败
}
```

### 2. 正常断开测试

```kotlin
@Test
fun testNormalDisconnection() {
    val disconnectedReceived = CountDownLatch(1)
    var disconnectReason: String? = null
    
    // 先建立连接
    TaplinkSDK.connect(validConfig, connectionListener)
    // 等待连接成功...
    
    // 手动断开
    TaplinkSDK.disconnect()
    
    // 验证收到 onDisconnected 而不是 onError
    assertTrue(disconnectedReceived.await(5, TimeUnit.SECONDS))
    assertNotNull(disconnectReason)
    assertTrue(disconnectReason!!.contains("1000")) // 正常关闭
}
```

### 3. 日志验证

**连接错误的日志：**
```
D/ConnectionManager: === Service Connection Disconnected ===
D/ConnectionManager: Disconnect code: C33
D/ConnectionManager: Disconnect message: Connection failed
E/ConnectionManager: Treating as connection error: Code: C33, Message: Connection failed
D/ConnectionManager: === Calling onError Callbacks ===
D/ConnectionManager: === All onError Callbacks Completed ===
```

**正常断开的日志：**
```
D/ConnectionManager: === Service Connection Disconnected ===
D/ConnectionManager: Disconnect code: 1000
D/ConnectionManager: Disconnect message: Normal closure
D/ConnectionManager: Treating as normal disconnection: Code: 1000, Message: Normal closure
D/ConnectionManager: === Calling onDisconnected Callbacks ===
D/ConnectionManager: === All onDisconnected Callbacks Completed ===
```

## 向后兼容性

这个修复是向后兼容的：

- ✅ 现有的 `onDisconnected` 处理逻辑保持不变
- ✅ 只是将连接错误正确路由到 `onError` 回调
- ✅ 不影响现有的 API 接口
- ✅ 改进了用户体验，没有破坏性变更

## 相关改进建议

### 1. 统一错误码管理

```kotlin
object ConnectionErrorCodes {
    const val SDK_NOT_INITIALIZED = "C20"
    const val SERVICE_NOT_FOUND = "C22"
    const val SERVICE_BIND_FAILED = "C25"
    const val CONNECTION_FAILED = "C33"
    const val SERVICE_DISCOVERY_FAILED = "C35"
    const val SERVICE_BINDING_DIED = "C36"
    const val PROTOCOL_ERROR = "C41"
}
```

### 2. 增强错误信息

```kotlin
class ConnectionError(
    val code: String,
    val message: String,
    val cause: Throwable? = null,
    val suggestions: List<String> = emptyList()
) {
    companion object {
        fun createWithSuggestions(code: String, message: String, vararg suggestions: String): ConnectionError {
            return ConnectionError(code, message, suggestions = suggestions.toList())
        }
    }
}
```

### 3. 添加重试建议

```kotlin
private fun createConnectionErrorWithSuggestions(code: String, message: String): ConnectionError {
    val suggestions = when (code) {
        "C35" -> listOf("检查设备是否在同一网络", "确认设备已开启", "重新扫描设备")
        "C33" -> listOf("检查网络连接", "确认IP地址和端口", "重试连接")
        "C41" -> listOf("检查连接配置", "确认协议格式", "联系技术支持")
        else -> emptyList()
    }
    
    return ConnectionError.createWithSuggestions(code, message, *suggestions.toTypedArray())
}
```

## 部署建议

1. **立即部署**：这是一个用户体验改进，建议尽快部署
2. **更新文档**：更新集成指南，说明正确的错误处理方式
3. **通知开发者**：通知使用 SDK 的开发者，现在可以正确处理连接错误了
4. **监控反馈**：收集用户反馈，确认错误分类是否准确

---

**修复版本**: 1.0.3  
**修复日期**: 2025-12-22  
**作者**: TaPro Team  
**类型**: 用户体验改进