# 自动重连功能更新日志

## 版本 1.1.0 - 2025-12-11

### 新增功能

#### 🔄 自动重连机制

添加了智能自动重连功能，提升 SDK 的健壮性和用户体验。

**核心特性：**

1. **自动检测断线**
   - 区分主动断开（`disconnect()`）和非主动断开
   - 仅对非主动断开触发自动重连
   - 主动断开不会触发重连逻辑

2. **可配置的重试策略**
   - `autoReconnect`: 是否启用自动重连（默认 `true`）
   - `maxRetryCount`: 最大重试次数（默认 `3`）
   - `retryDelayMs`: 重试延迟时间（默认 `2000ms`）

3. **完整的状态通知**
   - `onReconnecting(attempt, maxRetries)`: 重连开始时通知
   - `onConnected(deviceId, taproVersion)`: 重连成功时通知
   - `onDisconnected(reason)`: 重连失败时通知

4. **智能重连逻辑**
   - 连接成功后自动重置重试计数
   - 达到最大重试次数后停止重连
   - 主动断开时取消所有待处理的重试任务

### 修改的文件

#### 1. `TaplinkConfig.kt`

**新增配置项：**
```kotlin
val autoReconnect: Boolean = true
val maxRetryCount: Int = 3
val retryDelayMs: Long = 2000L
```

**新增方法：**
```kotlin
fun setAutoReconnect(enabled: Boolean): TaplinkConfig
fun setRetryPolicy(maxRetries: Int, delayMs: Long): TaplinkConfig
```

#### 2. `ConnectionListener.kt`

**新增回调方法：**
```kotlin
fun onReconnecting(attempt: Int, maxRetries: Int)
```

- 默认空实现，应用层可选择性覆盖
- 在每次重连尝试前调用
- 提供当前重试次数和最大重试次数

#### 3. `PaymentEvent.kt`

**新增事件类型：**
```kotlin
data class Reconnecting(
    val attempt: Int,
    val maxRetries: Int,
    override val eventCode: String = "RECONNECTING",
    override val eventMsg: String = "Reconnecting... (attempt $attempt/$maxRetries)"
) : PaymentEvent(eventCode, eventMsg, 0)
```

#### 4. `TaplinkApiImpl.kt`

**新增字段：**
```kotlin
private var isManualDisconnect = false
private var currentRetryCount = 0
private var lastConnectionConfig: ConnectionConfig? = null
private var lastConnectionListener: ConnectionListener? = null
private val retryHandler = Handler(Looper.getMainLooper())
```

**修改的方法：**

- `connect()`: 保存连接参数，重置重连状态
- `disconnect()`: 标记为主动断开，取消所有重试任务
- `onConnected()`: 重置重试计数
- `onDisconnected()`: 判断是否需要自动重连

**新增方法：**
```kotlin
private fun attemptReconnect()
```

### 使用示例

#### 基本使用（使用默认配置）

```kotlin
val config = TaplinkConfig(
    appId = "your_app_id",
    merchantId = "your_merchant_id",
    secretKey = "your_secret_key"
)
// 自动重连已默认启用

TaplinkSDK.init(context, config)
```

#### 自定义重连策略

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

#### 监听重连状态

```kotlin
TaplinkSDK.connect(connectionConfig, object : ConnectionListener {
    override fun onConnected(deviceId: String, taproVersion: String) {
        Log.d(TAG, "Connected: $deviceId")
    }
    
    override fun onDisconnected(reason: String) {
        Log.e(TAG, "Disconnected: $reason")
    }
    
    override fun onError(error: ConnectionError) {
        Log.e(TAG, "Error: ${error.message}")
    }
    
    override fun onReconnecting(attempt: Int, maxRetries: Int) {
        Log.d(TAG, "Reconnecting... ($attempt/$maxRetries)")
        showToast("正在重连... ($attempt/$maxRetries)")
    }
})
```

#### 禁用自动重连

```kotlin
val config = TaplinkConfig(
    appId = "your_app_id",
    merchantId = "your_merchant_id",
    secretKey = "your_secret_key"
)
    .setAutoReconnect(false)

TaplinkSDK.init(context, config)
```

### 推荐配置

根据不同的连接模式，推荐使用不同的重试策略：

| 连接模式 | 推荐重试次数 | 推荐延迟 | 原因 |
|---------|------------|---------|------|
| APP_TO_APP | 3 | 1000ms | IPC 连接快，快速重试 |
| CABLE | 3 | 2000ms | 线缆连接中等速度 |
| LAN | 5 | 3000ms | 网络连接慢，需要更多时间 |

**示例代码：**

```kotlin
val (maxRetries, delayMs) = when (connectionMode) {
    ConnectionMode.APP_TO_APP -> Pair(3, 1000L)
    ConnectionMode.CABLE -> Pair(3, 2000L)
    ConnectionMode.LAN -> Pair(5, 3000L)
    else -> Pair(3, 2000L)
}

val config = TaplinkConfig(
    appId = "your_app_id",
    merchantId = "your_merchant_id",
    secretKey = "your_secret_key"
)
    .setConnectionMode(connectionMode)
    .setRetryPolicy(maxRetries, delayMs)
```

### 行为说明

#### 触发重连的条件

自动重连会在以下条件**全部满足**时触发：

1. ✅ `autoReconnect` 配置为 `true`
2. ✅ 连接断开不是主动调用 `disconnect()` 导致的
3. ✅ 当前重试次数 < 最大重试次数

#### 不触发重连的情况

以下情况**不会**触发自动重连：

1. ❌ 主动调用 `disconnect()` 断开连接
2. ❌ `autoReconnect` 配置为 `false`
3. ❌ 已达到最大重试次数
4. ❌ SDK 未初始化

#### 重连流程

```
连接断开（非主动）
    ↓
检查重连条件
    ↓
调用 onReconnecting(attempt, maxRetries)
    ↓
等待 retryDelayMs 毫秒
    ↓
调用 connect() 尝试重连
    ↓
成功？
├─ 是 → 调用 onConnected() → 重置重试计数 → 恢复正常
└─ 否 → attempt++
         ↓
         attempt < maxRetries？
         ├─ 是 → 继续重连流程
         └─ 否 → 调用 onDisconnected() → 停止重连
```

### 注意事项

1. **线程安全**
   - 所有回调都在主线程执行
   - 可以直接更新 UI

2. **资源管理**
   - 重连失败后会自动清理所有待处理的请求回调
   - 主动断开时会取消所有待处理的重试任务

3. **状态一致性**
   - 重连期间 `isConnected()` 返回 `false`
   - 重连成功后 `isConnected()` 返回 `true`

4. **请求处理**
   - 重连期间发起的支付请求会失败
   - 建议在 UI 中禁用支付功能，直到重连成功

### 向后兼容性

✅ **完全向后兼容**

- 默认启用自动重连，不影响现有代码
- `onReconnecting()` 是可选实现，不强制覆盖
- 如果不需要自动重连，可以通过配置禁用

### 测试

新增测试文件：`AutoReconnectTest.kt`

测试覆盖：
- ✅ 默认配置测试
- ✅ 自定义配置测试
- ✅ 不同连接模式的重连配置
- ✅ ConnectionListener 回调测试
- ✅ 推荐重试策略测试
- ✅ 禁用自动重连测试
- ✅ 链式配置测试

### 文档

新增文档：
- `AUTO_RECONNECT_USAGE.md`: 详细的使用指南
- `CHANGELOG_AUTO_RECONNECT.md`: 本更新日志

### 性能影响

- ✅ **无性能影响**：重连是异步执行，不阻塞主线程
- ✅ **低资源消耗**：重试间隔期间 SDK 处于空闲状态
- ✅ **智能管理**：自动清理资源，防止内存泄漏

### 已知限制

1. 重连期间无法发起新的支付请求
2. 达到最大重试次数后需要手动重新连接
3. 重连不会恢复未完成的交易（需要应用层处理）

### 未来计划

- [ ] 支持指数退避重试策略
- [ ] 支持自定义重连条件判断
- [ ] 支持重连成功后自动恢复未完成的交易
- [ ] 添加重连统计和监控

### 反馈

如有问题或建议，请联系 TaPro 团队。

---

**版本**: 1.1.0  
**日期**: 2025-12-11  
**作者**: TaPro Team
