# mDNS 地址变更通知机制

## 概述

在LAN模式下，当WebSocket客户端通过mDNS检测到服务地址（IP或端口）发生变更时，通信层会通知SDK层进行重连操作，而不是在通信层直接处理重连。这样的设计符合分层架构原则，让SDK层统一管理重连逻辑。

## 架构设计

```
┌─────────────────┐    地址变更通知    ┌─────────────────┐
│   SDK 层        │ ←──────────────── │  通信层          │
│ TaplinkApiImpl  │                   │ WebSocketClient │
│ ReconnectManager│    重连操作        │ Kernel          │
│                 │ ──────────────→   │                 │
└─────────────────┘                   └─────────────────┘
```

## 实现机制

### 1. 通信层检测地址变更

**WebSocketClientKernel** 通过mDNS服务监听检测到地址变更：

```kotlin
private fun checkAndHandleServiceChange(newHost: String, newPort: Int) {
    val currentService = currentServiceInfo
    if (currentService == null) return
    
    // 检查 IP 或端口是否发生变化
    if (currentService.host != newHost || currentService.port != newPort) {
        LogUtil.i(TAG, "Service address changed: ${currentService.host}:${currentService.port} -> $newHost:$newPort")
        
        // 通知SDK层地址发生变更，让SDK层决定是否重连
        currentConnectionCallback?.onServiceAddressChanged(
            newHost = newHost,
            newPort = newPort,
            oldHost = currentService.host,
            oldPort = currentService.port
        )
        
        // 更新服务信息
        currentServiceInfo = ServiceInfo(
            name = currentService.name,
            host = newHost,
            port = newPort
        )
    }
}
```

### 2. 扩展连接回调接口

**ConnectionCallback** 接口新增地址变更通知方法：

```kotlin
interface ConnectionCallback {
    fun onConnected(extraInfoMap: Map<String, String?>?)
    fun onWaitingConnect()
    fun onDisconnected(code: String, msg: String)
    
    /**
     * 当检测到服务地址变更时调用
     */
    fun onServiceAddressChanged(newHost: String, newPort: Int, oldHost: String, oldPort: Int) {
        // 默认空实现，子类可选择性重写
    }
}
```

### 3. SDK层处理地址变更

**TaplinkApiImpl** 在ServiceConnectionCallback中处理地址变更：

```kotlin
override fun onServiceAddressChanged(newHost: String, newPort: Int, oldHost: String, oldPort: Int) {
    LogUtil.i(TAG, "Service address changed detected: $oldHost:$oldPort -> $newHost:$newPort")
    
    // 创建新的连接配置
    val newConnectionConfig = ConnectionConfig(
        host = newHost,
        port = newPort
    )
    
    LogUtil.d(TAG, "Triggering reconnection with new address: $newHost:$newPort")
    
    // 通知重连管理器地址变更，触发重连
    reconnectManager?.onAddressChanged(newConnectionConfig, listener)
}
```

### 4. 重连管理器处理地址变更

**ReconnectManager** 新增地址变更处理方法：

```kotlin
fun onAddressChanged(newConnectionConfig: ConnectionConfig, listener: ConnectionListener) {
    LogUtil.i(TAG, "Service address changed, updating connection config and reconnecting")
    
    // 更新保存的连接配置
    lastConnectionConfig = newConnectionConfig
    
    // 重置重连状态，因为这是地址变更导致的重连，不是失败重试
    currentRetryCount = 0
    isManualDisconnect = false
    isReconnecting = true
    
    // 取消现有的重连任务
    retryHandler.removeCallbacksAndMessages(null)
    
    // 通知监听器正在重连（地址变更）
    listener.onReconnecting(0, config.maxRetryCount)
    lastConnectionListener?.onReconnecting(0, config.maxRetryCount)
    
    // 立即尝试连接到新地址（不延迟）
    LogUtil.d(TAG, "Attempting to connect to new address immediately")
    reconnectAction(newConnectionConfig, listener)
}
```

## 工作流程

1. **地址检测**：WebSocketClientKernel通过mDNS监听检测到服务地址变更
2. **变更通知**：通信层调用`onServiceAddressChanged()`通知SDK层
3. **配置更新**：SDK层创建新的ConnectionConfig
4. **重连触发**：ReconnectManager处理地址变更，立即触发重连
5. **状态通知**：通过ConnectionListener通知应用层正在重连
6. **连接建立**：使用新地址重新建立连接

## 设计优势

### 1. 分层清晰
- **通信层**：只负责检测地址变更，不处理重连逻辑
- **SDK层**：统一管理所有重连逻辑，包括地址变更导致的重连

### 2. 状态一致
- 重连状态由ReconnectManager统一管理
- 避免通信层和SDK层重连状态不一致的问题

### 3. 灵活控制
- SDK层可以根据配置决定是否响应地址变更
- 可以与其他重连场景（如网络断开）统一处理

### 4. 向后兼容
- 新增的回调方法有默认实现，不影响现有代码
- 现有的重连逻辑保持不变

## 配置选项

可以通过TaplinkConfig控制地址变更重连行为：

```kotlin
val config = TaplinkConfig.Builder()
    .autoReconnect(true)        // 启用自动重连
    .maxRetryCount(5)           // 最大重试次数
    .retryDelayMs(3000)         // 重试延迟（地址变更时立即重连）
    .build()
```

## 日志输出

系统会输出详细的日志来跟踪地址变更和重连过程：

```
I/WebSocketClientKernel: Service address changed: 192.168.1.100:8443 -> 192.168.1.101:8443
I/TaplinkApiImpl: Service address changed detected: 192.168.1.100:8443 -> 192.168.1.101:8443
D/TaplinkApiImpl: Triggering reconnection with new address: 192.168.1.101:8443
I/ReconnectManager: Service address changed, updating connection config and reconnecting
D/ReconnectManager: Attempting to connect to new address immediately
```

## 测试场景

1. **IP地址变更**：设备IP从192.168.1.100变更为192.168.1.101
2. **端口变更**：服务端口从8443变更为8444
3. **同时变更**：IP和端口同时发生变化
4. **频繁变更**：短时间内多次地址变更
5. **变更失败**：新地址无法连接的情况

## 注意事项

1. **及时性**：地址变更检测依赖mDNS响应速度
2. **网络环境**：某些网络环境可能不支持mDNS
3. **资源管理**：确保旧连接及时释放，避免资源泄漏
4. **错误处理**：新地址连接失败时的降级处理

这个机制确保了在LAN模式下，当服务地址发生变更时，SDK能够及时感知并自动重连到新地址，提供更好的用户体验。