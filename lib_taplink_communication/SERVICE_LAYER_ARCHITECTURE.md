# Service 层架构说明

## 📋 架构原则

### 职责分离

Service 层和 SDK 层有明确的职责划分：

```
┌─────────────────────────────────────────────┐
│           SDK 层 (lib_taplink_sdk)           │
│  - 业务逻辑                                  │
│  - 重连管理 (ReconnectManager)              │
│  - 状态管理                                  │
│  - 用户 API                                  │
└────────────────────┬────────────────────────┘
                     │
                     ▼
┌─────────────────────────────────────────────┐
│        Service 层 (lib_taplink_communication)      │
│  - 底层通信协议                              │
│  - 心跳检测                                  │
│  - 数据传输                                  │
│  - mDNS 服务                                 │
└─────────────────────────────────────────────┘
```

## 🎯 Service 层职责

### ✅ 应该做的事情

1. **底层通信**
   - WebSocket 连接管理
   - USB/串口通信
   - 数据发送和接收

2. **心跳检测**
   - 检测连接是否活跃
   - 发送心跳消息
   - 检测心跳超时

3. **服务发现**
   - mDNS 服务注册
   - mDNS 服务发现
   - 网络状态监控

4. **协议处理**
   - 协议解析
   - 数据封装
   - 消息路由

### ❌ 不应该做的事情

1. **重连逻辑**
   - ❌ 不应该在 Service 层实现重连
   - ❌ 不应该管理重试次数
   - ❌ 不应该决定重连策略
   - ✅ 只需要通知上层连接断开

2. **业务逻辑**
   - ❌ 不应该处理支付逻辑
   - ❌ 不应该管理交易状态
   - ❌ 不应该处理用户回调

3. **状态管理**
   - ❌ 不应该管理应用级状态
   - ✅ 只管理连接级状态

## 🔄 重连机制的正确实现

### 为什么重连应该在 SDK 层？

1. **统一管理**
   - 所有连接模式（WebSocket、USB、串口）使用相同的重连策略
   - 避免重复代码
   - 易于维护和修改

2. **灵活配置**
   - 用户可以配置重连策略
   - 不同场景可以使用不同策略
   - Service 层保持简单

3. **职责清晰**
   - Service 层：负责"如何连接"
   - SDK 层：负责"何时重连"

### 架构对比

#### ❌ 错误的架构（重连在 Service 层）

```
用户应用
    ↓
SDK 层
    ↓
Service 层 (WebSocketClientKernel)
    ├─ 连接管理
    ├─ 心跳检测
    └─ 重连逻辑 ❌ (重复实现)
    
Service 层 (UsbServiceKernel)
    ├─ 连接管理
    ├─ 心跳检测
    └─ 重连逻辑 ❌ (重复实现)
```

**问题：**
- 每个 Service Kernel 都要实现重连
- 重连策略不一致
- 难以统一配置

#### ✅ 正确的架构（重连在 SDK 层）

```
用户应用
    ↓
SDK 层
    ├─ TaplinkApiImpl
    └─ ReconnectManager ✅ (统一重连管理)
        ↓
Service 层 (WebSocketClientKernel)
    ├─ 连接管理
    ├─ 心跳检测
    └─ 通知断开 ✅
    
Service 层 (UsbServiceKernel)
    ├─ 连接管理
    ├─ 心跳检测
    └─ 通知断开 ✅
```

**优势：**
- 重连逻辑统一管理
- Service 层保持简单
- 易于配置和扩展

## 📝 实现细节

### Service 层的实现

```kotlin
// WebSocketClientKernel.kt
class WebSocketClientKernel : BaseServiceKernel() {
    
    /**
     * 处理连接丢失
     * 
     * 注意：不在 Service 层处理重连，由 SDK 层统一管理重连逻辑
     */
    private fun handleConnectionLost() {
        if (isManualDisconnect.get()) {
            LogUtil.d(TAG, "Manual disconnect")
            return
        }
        
        LogUtil.w(TAG, "Connection lost, notifying upper layer")
        updateStatus(ConnectionStatus.ERROR)
        
        // 通知上层连接丢失，由 SDK 层决定是否重连
        currentConnectionCallback?.onDisconnected(
            InnerErrorCode.C35.code,
            "Connection lost"
        )
    }
    
    // ❌ 移除了这些方法：
    // - startReconnect()
    // - stopReconnect()
    // - reconnectAttempts
    // - MAX_RECONNECT_ATTEMPTS
}
```

### SDK 层的实现

```kotlin
// TaplinkApiImpl.kt
class TaplinkApiImpl : TaplinkApi {
    
    private var reconnectManager: ReconnectManager? = null
    
    override fun init(context: Context, config: TaplinkConfig) {
        // 初始化重连管理器
        reconnectManager = ReconnectManager(config) { connectionConfig, listener ->
            connectInternal(connectionConfig, listener)
        }
    }
    
    override fun connect(config: ConnectionConfig?, listener: ConnectionListener) {
        // 准备重连管理器
        reconnectManager?.prepareConnect(config, listener)
        
        // 执行实际连接
        connectInternal(config, listener)
    }
    
    private fun connectInternal(config: ConnectionConfig?, listener: ConnectionListener) {
        // 通过 ServiceKernel 连接
        TaplinkServiceKernel.getInstance()
            ?.connect(protocol, appId, secretKey, object : ServiceConnectionCallback {
                override fun onConnected(extraInfoMap: Map<String, String?>?) {
                    // 通知重连管理器连接成功
                    reconnectManager?.onConnected()
                    // ...
                }
                
                override fun onDisconnected(code: String, msg: String) {
                    // 尝试自动重连
                    val reconnected = reconnectManager?.onDisconnected(listener) ?: false
                    
                    if (!reconnected) {
                        // 不重连，通知应用层
                        listener.onDisconnected(reason)
                    }
                    // ...
                }
            })
    }
}
```

## 🔍 连接断开的处理流程

### 完整流程

```
1. 网络断开或心跳超时
    ↓
2. WebSocketClientKernel 检测到连接丢失
    ↓
3. 调用 handleConnectionLost()
    ↓
4. 通知 SDK 层：onDisconnected(code, msg)
    ↓
5. TaplinkApiImpl 收到通知
    ↓
6. 调用 ReconnectManager.onDisconnected()
    ↓
7. ReconnectManager 判断是否需要重连
    ├─ 需要重连 → 延迟后调用 connectInternal()
    └─ 不需要重连 → 通知应用层 onDisconnected()
    ↓
8. 如果重连，回到步骤 2
```

### 代码流程

```kotlin
// 1. Service 层检测到连接丢失
private fun handleConnectionLost() {
    currentConnectionCallback?.onDisconnected("C35", "Connection lost")
}

// 2. SDK 层收到通知
override fun onDisconnected(code: String, msg: String) {
    // 3. 交给 ReconnectManager 处理
    val reconnected = reconnectManager?.onDisconnected(listener) ?: false
    
    if (!reconnected) {
        // 4. 不重连，通知应用层
        listener.onDisconnected(reason)
    }
}

// 5. ReconnectManager 决定是否重连
fun onDisconnected(listener: ConnectionListener): Boolean {
    val shouldAutoReconnect = config.autoReconnect && 
                              !isManualDisconnect && 
                              currentRetryCount < config.maxRetryCount
    
    if (shouldAutoReconnect) {
        // 6. 延迟重连
        retryHandler.postDelayed({
            attemptReconnect()
        }, config.retryDelayMs)
        return true
    }
    
    return false
}
```

## 📊 对比总结

| 方面 | Service 层重连 ❌ | SDK 层重连 ✅ |
|------|------------------|--------------|
| 代码重复 | 每个 Kernel 都要实现 | 统一实现 |
| 配置灵活性 | 难以统一配置 | 易于配置 |
| 维护成本 | 高（多处修改） | 低（单处修改） |
| 职责清晰度 | 不清晰 | 清晰 |
| 可测试性 | 难以测试 | 易于测试 |
| 扩展性 | 差 | 好 |

## 🎯 最佳实践

### 1. Service 层只负责通信

```kotlin
// ✅ 正确：只负责连接和通知
class WebSocketClientKernel : BaseServiceKernel() {
    override fun connect(protocol: String, callback: ConnectionCallback) {
        // 建立连接
        client = TaplinkWebSocketClient(uri, callback)
        client?.connect()
    }
    
    private fun handleConnectionLost() {
        // 通知上层
        callback?.onDisconnected("C35", "Connection lost")
    }
}

// ❌ 错误：在 Service 层处理重连
class WebSocketClientKernel : BaseServiceKernel() {
    private fun handleConnectionLost() {
        // ❌ 不应该在这里重连
        startReconnect()
    }
}
```

### 2. SDK 层统一管理重连

```kotlin
// ✅ 正确：在 SDK 层统一管理
class TaplinkApiImpl : TaplinkApi {
    private var reconnectManager: ReconnectManager? = null
    
    override fun connect(config: ConnectionConfig?, listener: ConnectionListener) {
        reconnectManager?.prepareConnect(config, listener)
        connectInternal(config, listener)
    }
}
```

### 3. 配置在 SDK 层

```kotlin
// ✅ 正确：重连配置在 SDK 层
val config = TaplinkConfig(
    appId = "your_app_id",
    merchantId = "your_merchant_id",
    secretKey = "your_secret_key"
)
    .setAutoReconnect(true)
    .setRetryPolicy(maxRetries = 5, delayMs = 3000L)

TaplinkSDK.init(context, config)
```

## 🔧 迁移指南

### 如果你之前在 Service 层实现了重连

1. **移除 Service 层的重连代码**
   ```kotlin
   // 移除这些：
   - startReconnect()
   - stopReconnect()
   - reconnectAttempts
   - MAX_RECONNECT_ATTEMPTS
   - RECONNECT_DELAY_BASE
   - RECONNECT_DELAY_MAX
   ```

2. **简化连接丢失处理**
   ```kotlin
   // 之前：
   private fun handleConnectionLost() {
       startReconnect() // ❌
   }
   
   // 现在：
   private fun handleConnectionLost() {
       callback?.onDisconnected("C35", "Connection lost") // ✅
   }
   ```

3. **在 SDK 层使用 ReconnectManager**
   ```kotlin
   // 已经实现，无需额外代码
   ```

## 📚 相关文档

- [ReconnectManager 实现](../lib_taplink_sdk/src/main/java/com/sunmi/tapro/taplink/sdk/reconnect/ReconnectManager.kt)
- [自动重连使用指南](../lib_taplink_sdk/AUTO_RECONNECT_USAGE.md)
- [重构总结](../lib_taplink_sdk/REFACTORING_SUMMARY.md)

## 🎉 总结

通过将重连逻辑从 Service 层移到 SDK 层：

1. ✅ **职责更清晰**：Service 层专注于通信，SDK 层管理重连
2. ✅ **代码更简洁**：避免重复实现
3. ✅ **配置更灵活**：统一的重连策略配置
4. ✅ **维护更容易**：单点修改，全局生效
5. ✅ **测试更简单**：独立的重连逻辑易于测试

这是一个更好的架构设计！

---

**文档版本**: 1.0  
**更新日期**: 2025-12-11  
**作者**: TaPro Team
