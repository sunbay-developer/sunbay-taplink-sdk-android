# TaplinkApiImpl 重构总结

## 重构目标

将 `TaplinkApiImpl` 中的自动重连逻辑提取到独立的 `ReconnectManager` 类中，实现：
- ✅ 代码模块化和职责分离
- ✅ 提高代码可读性和可维护性
- ✅ 便于单元测试
- ✅ 降低类的复杂度

## 重构前后对比

### 重构前

`TaplinkApiImpl` 包含所有逻辑：
- 连接管理
- 重连逻辑
- 支付请求处理
- 状态管理
- 回调管理

**问题：**
- 类过于庞大（900+ 行）
- 职责不清晰
- 重连逻辑与连接逻辑耦合
- 难以测试和维护

### 重构后

#### 1. `ReconnectManager` - 重连管理器

**职责：**
- 管理重连状态（主动/被动断开）
- 管理重试计数
- 调度延迟重连
- 保存连接参数用于重连

**优势：**
- 单一职责原则
- 可独立测试
- 可复用

#### 2. `TaplinkApiImpl` - API 实现类

**职责：**
- SDK 初始化
- 连接管理（委托给 ReconnectManager）
- 支付请求处理
- 状态管理
- 回调管理

**优势：**
- 代码更清晰
- 职责更明确
- 易于维护

## 文件结构

```
lib_taplink_sdk/
├── src/main/java/com/sunmi/tapro/taplink/sdk/
│   ├── impl/
│   │   └── TaplinkApiImpl.kt          # API 实现（精简后）
│   └── reconnect/
│       └── ReconnectManager.kt        # 重连管理器（新增）
```

## 核心改动

### 1. 新增 `ReconnectManager` 类

```kotlin
class ReconnectManager(
    private val config: TaplinkConfig,
    private val reconnectAction: (ConnectionConfig?, ConnectionListener) -> Unit
)
```

**核心方法：**
- `prepareConnect()`: 准备连接，保存参数
- `onConnected()`: 连接成功，重置重试计数
- `onDisconnected()`: 连接断开，判断是否重连
- `disconnect()`: 主动断开，取消重试
- `attemptReconnect()`: 执行重连

### 2. 精简 `TaplinkApiImpl`

**移除的字段：**
```kotlin
// 移除
private var isManualDisconnect = false
private var currentRetryCount = 0
private var lastConnectionConfig: ConnectionConfig? = null
private var lastConnectionListener: ConnectionListener? = null
private val retryHandler = Handler(Looper.getMainLooper())

// 替换为
private var reconnectManager: ReconnectManager? = null
```

**简化的方法：**

#### `init()` - 初始化重连管理器
```kotlin
override fun init(context: Context, config: TaplinkConfig) {
    this.config = config
    
    // 初始化重连管理器
    reconnectManager = ReconnectManager(config) { connectionConfig, listener ->
        connectInternal(connectionConfig, listener)
    }
    
    // ... 其他初始化逻辑
}
```

#### `connect()` - 委托给重连管理器
```kotlin
override fun connect(config: ConnectionConfig?, listener: ConnectionListener) {
    // 准备重连管理器
    reconnectManager?.prepareConnect(config, listener)
    
    // 执行实际连接
    connectInternal(config, listener)
}
```

#### `connectInternal()` - 新增内部连接方法
```kotlin
private fun connectInternal(config: ConnectionConfig?, listener: ConnectionListener) {
    // 实际连接逻辑
    // 供 connect() 和 ReconnectManager 调用
}
```

#### `onConnected()` - 通知重连管理器
```kotlin
override fun onConnected(extraInfoMap: Map<String, String?>?) {
    // 通知重连管理器连接成功
    reconnectManager?.onConnected()
    
    // ... 其他逻辑
}
```

#### `onDisconnected()` - 委托重连判断
```kotlin
override fun onDisconnected(code: String, msg: String) {
    // 尝试自动重连
    val reconnected = reconnectManager?.onDisconnected(listener) ?: false
    
    if (!reconnected) {
        // 不重连，通知应用层
        listener.onDisconnected(reason)
    }
    
    // 清理资源
}
```

#### `disconnect()` - 通知重连管理器
```kotlin
override fun disconnect() {
    // 通知重连管理器主动断开
    reconnectManager?.disconnect()
    
    // ... 其他逻辑
}
```

**移除的方法：**
```kotlin
// 移除
private fun attemptReconnect()
```

## 代码行数对比

| 文件 | 重构前 | 重构后 | 减少 |
|------|--------|--------|------|
| TaplinkApiImpl.kt | ~900 行 | ~850 行 | ~50 行 |
| ReconnectManager.kt | 0 行 | ~150 行 | +150 行 |
| **总计** | ~900 行 | ~1000 行 | +100 行 |

**说明：**
- 虽然总代码行数略有增加，但代码结构更清晰
- 职责分离使得每个类更易于理解和维护
- 新增的代码主要是注释和文档

## 优势总结

### 1. 代码可读性 ⬆️
- 重连逻辑独立，一目了然
- TaplinkApiImpl 更专注于核心功能
- 方法职责更清晰

### 2. 可维护性 ⬆️
- 修改重连逻辑只需修改 ReconnectManager
- 不影响其他功能
- 降低代码耦合度

### 3. 可测试性 ⬆️
- ReconnectManager 可独立测试
- 测试用例更简单
- Mock 更容易

### 4. 可扩展性 ⬆️
- 可以轻松添加新的重连策略
- 可以支持不同的重连算法（如指数退避）
- 不影响现有代码

### 5. 复用性 ⬆️
- ReconnectManager 可以在其他项目中复用
- 重连逻辑与具体实现解耦

## 设计模式

### 1. 策略模式（Strategy Pattern）
- `ReconnectManager` 封装重连策略
- 通过 `reconnectAction` 回调执行重连
- 可以轻松替换不同的重连策略

### 2. 委托模式（Delegation Pattern）
- `TaplinkApiImpl` 将重连逻辑委托给 `ReconnectManager`
- 保持接口不变，内部实现解耦

### 3. 单一职责原则（Single Responsibility Principle）
- `ReconnectManager` 只负责重连逻辑
- `TaplinkApiImpl` 负责 API 实现
- 各司其职，互不干扰

## 测试建议

### 1. ReconnectManager 单元测试

```kotlin
@Test
fun testAutoReconnectOnDisconnect() {
    var reconnectCalled = false
    val config = TaplinkConfig(
        appId = "test",
        merchantId = "test",
        secretKey = "test"
    ).setAutoReconnect(true).setRetryPolicy(3, 1000L)
    
    val manager = ReconnectManager(config) { _, _ ->
        reconnectCalled = true
    }
    
    val listener = mock<ConnectionListener>()
    manager.prepareConnect(null, listener)
    
    // 模拟断开
    val reconnected = manager.onDisconnected(listener)
    
    assertTrue(reconnected)
    verify(listener).onReconnecting(1, 3)
}
```

### 2. TaplinkApiImpl 集成测试

```kotlin
@Test
fun testConnectWithAutoReconnect() {
    val sdk = TaplinkApiImpl()
    val config = TaplinkConfig(
        appId = "test",
        merchantId = "test",
        secretKey = "test"
    ).setAutoReconnect(true)
    
    sdk.init(context, config)
    
    val listener = mock<ConnectionListener>()
    sdk.connect(null, listener)
    
    // 验证连接逻辑
}
```

## 向后兼容性

✅ **完全向后兼容**

- 公共 API 没有变化
- 行为保持一致
- 配置方式不变
- 回调接口不变

## 性能影响

✅ **无性能影响**

- 重连逻辑没有变化
- 只是代码组织方式改变
- 内存占用略有增加（一个 ReconnectManager 实例）
- 可以忽略不计

## 未来优化方向

### 1. 支持多种重连策略
```kotlin
interface ReconnectStrategy {
    fun shouldRetry(attempt: Int): Boolean
    fun getRetryDelay(attempt: Int): Long
}

class ExponentialBackoffStrategy : ReconnectStrategy {
    override fun shouldRetry(attempt: Int) = attempt < 5
    override fun getRetryDelay(attempt: Int) = (1000L * Math.pow(2.0, attempt.toDouble())).toLong()
}
```

### 2. 支持自定义重连条件
```kotlin
interface ReconnectCondition {
    fun shouldReconnect(errorCode: String, errorMsg: String): Boolean
}
```

### 3. 添加重连事件监听
```kotlin
interface ReconnectEventListener {
    fun onReconnectStart(attempt: Int)
    fun onReconnectSuccess(attempt: Int)
    fun onReconnectFailed(attempt: Int, reason: String)
    fun onReconnectGiveUp(totalAttempts: Int)
}
```

## 总结

通过将重连逻辑提取到 `ReconnectManager`，我们实现了：

1. ✅ **代码更清晰**：职责分离，易于理解
2. ✅ **更易维护**：修改重连逻辑不影响其他功能
3. ✅ **更易测试**：可以独立测试重连逻辑
4. ✅ **更易扩展**：可以轻松添加新的重连策略
5. ✅ **完全兼容**：不影响现有代码和 API

这是一次成功的重构，提升了代码质量，为未来的功能扩展打下了良好的基础。

---

**重构日期**: 2025-12-11  
**重构人员**: TaPro Team
