# 重连机制重复触发问题修复

## 问题描述

在某些情况下，`onDisconnected` 回调可能被多次调用，导致重连逻辑被重复触发。

### 问题场景

1. **网络波动** - 短时间内多次断开/重连
2. **多个断开事件** - 心跳超时、连接丢失、服务崩溃等同时触发
3. **异步回调** - 不同线程同时调用 `onDisconnected`

### 问题表现

```
Connection lost, attempting reconnect (1/5)
Connection lost, attempting reconnect (2/5)  // 重复触发
Connection lost, attempting reconnect (3/5)  // 重复触发
```

**后果：**
- ❌ 重连次数快速耗尽
- ❌ 多个重连任务同时执行
- ❌ 资源浪费
- ❌ 用户体验差

---

## 解决方案

### 添加重连状态标志

在 `ReconnectManager` 中添加 `isReconnecting` 标志，防止重复触发：

```kotlin
class ReconnectManager {
    /**
     * 是否正在重连中
     */
    private var isReconnecting = false
    
    fun onDisconnected(listener: ConnectionListener): Boolean {
        // 防止重复触发重连
        if (isReconnecting) {
            LogUtil.w(TAG, "Already reconnecting, ignoring duplicate onDisconnected call")
            return true // 返回 true 表示正在处理重连
        }
        
        val shouldAutoReconnect = config.autoReconnect && 
                                  !isManualDisconnect && 
                                  currentRetryCount < config.maxRetryCount
        
        if (shouldAutoReconnect) {
            isReconnecting = true  // 设置重连标志
            currentRetryCount++
            
            // 延迟重连
            retryHandler.postDelayed({
                attemptReconnect()
            }, config.retryDelayMs)
            
            return true
        }
        
        return false
    }
    
    fun onConnected() {
        currentRetryCount = 0
        isReconnecting = false  // 连接成功，重置标志
    }
    
    fun disconnect() {
        isManualDisconnect = true
        isReconnecting = false  // 主动断开，重置标志
        currentRetryCount = 0
        retryHandler.removeCallbacksAndMessages(null)
    }
}
```

---

## 工作流程

### 修复前（有问题）

```
1. onDisconnected() 被调用
   ↓
2. 开始重连 (currentRetryCount = 1)
   ↓
3. onDisconnected() 再次被调用（重复）
   ↓
4. 再次开始重连 (currentRetryCount = 2)  ❌ 重复
   ↓
5. 重连次数快速耗尽
```

### 修复后（正确）

```
1. onDisconnected() 被调用
   ↓
2. 检查 isReconnecting = false
   ↓
3. 设置 isReconnecting = true
   ↓
4. 开始重连 (currentRetryCount = 1)
   ↓
5. onDisconnected() 再次被调用（重复）
   ↓
6. 检查 isReconnecting = true
   ↓
7. 忽略重复调用，返回 true  ✅ 防止重复
   ↓
8. 连接成功后，重置 isReconnecting = false
```

---

## 状态转换图

```
[DISCONNECTED]
      ↓
  onDisconnected()
      ↓
  isReconnecting? ──Yes──> 忽略，返回 true
      ↓ No
  设置 isReconnecting = true
      ↓
  [RECONNECTING]
      ↓
  延迟后尝试重连
      ↓
  ┌─────────────┬─────────────┐
  ↓             ↓             ↓
成功          失败          主动断开
  ↓             ↓             ↓
onConnected() onDisconnected() disconnect()
  ↓             ↓             ↓
重置标志      检查重试次数    重置标志
  ↓             ↓             ↓
[CONNECTED]   继续重连/放弃   [DISCONNECTED]
```

---

## 关键点

### 1. 何时设置 `isReconnecting = true`

```kotlin
fun onDisconnected(listener: ConnectionListener): Boolean {
    if (isReconnecting) {
        return true  // 已经在重连中
    }
    
    if (shouldAutoReconnect) {
        isReconnecting = true  // ✅ 在开始重连前设置
        // 开始重连...
    }
}
```

### 2. 何时重置 `isReconnecting = false`

```kotlin
// 场景 1: 连接成功
fun onConnected() {
    isReconnecting = false  // ✅ 连接成功，重置
}

// 场景 2: 主动断开
fun disconnect() {
    isReconnecting = false  // ✅ 主动断开，重置
}

// 场景 3: 重连失败且不再重试
fun onDisconnected(listener: ConnectionListener): Boolean {
    if (currentRetryCount >= config.maxRetryCount) {
        isReconnecting = false  // ✅ 放弃重连，重置
        return false
    }
}
```

### 3. 保持状态一致性

```kotlin
private fun attemptReconnect() {
    // 保存当前重连状态
    val savedRetryCount = currentRetryCount
    val savedManualDisconnect = isManualDisconnect
    val savedReconnecting = isReconnecting  // ✅ 保存重连标志
    
    // 重新连接
    reconnectAction(config, listener)
    
    // 恢复重连状态
    currentRetryCount = savedRetryCount
    isManualDisconnect = savedManualDisconnect
    isReconnecting = savedReconnecting  // ✅ 恢复重连标志
}
```

---

## 测试场景

### 测试 1: 快速多次断开

```kotlin
// 模拟快速多次断开
repeat(5) {
    reconnectManager.onDisconnected(listener)
}

// 预期结果：
// - 只触发一次重连
// - currentRetryCount = 1（不是 5）
```

### 测试 2: 重连过程中再次断开

```kotlin
// 1. 第一次断开，开始重连
reconnectManager.onDisconnected(listener)
// isReconnecting = true, currentRetryCount = 1

// 2. 重连过程中再次断开
reconnectManager.onDisconnected(listener)
// 预期：忽略，isReconnecting 仍为 true, currentRetryCount 仍为 1

// 3. 连接成功
reconnectManager.onConnected()
// isReconnecting = false, currentRetryCount = 0
```

### 测试 3: 主动断开取消重连

```kotlin
// 1. 开始重连
reconnectManager.onDisconnected(listener)
// isReconnecting = true

// 2. 主动断开
reconnectManager.disconnect()
// isReconnecting = false, 取消所有重连任务

// 3. 再次断开事件到达
reconnectManager.onDisconnected(listener)
// 预期：不重连（因为 isManualDisconnect = true）
```

### 测试 4: 重连次数用完后手动重连 ⭐ 新增

```kotlin
// 配置：maxRetryCount = 3
val config = TaplinkConfig(...)
    .setAutoReconnect(true)
    .setRetryPolicy(maxRetries = 3, delayMs = 3000L)

// 1. 自动重连 3 次
reconnectManager.onDisconnected(listener) // 第 1 次
// 等待失败...
reconnectManager.onDisconnected(listener) // 第 2 次
// 等待失败...
reconnectManager.onDisconnected(listener) // 第 3 次
// 等待失败...

// 2. 第 4 次断开，重连次数用完
reconnectManager.onDisconnected(listener)
// 预期：
// - 不再自动重连
// - isReconnecting = false
// - currentRetryCount = 3
// - 返回 false，通知应用层

// 3. 用户手动调用 connect()
reconnectManager.prepareConnect(config, listener)
// 预期：
// - currentRetryCount 重置为 0
// - isReconnecting 重置为 false
// - isManualDisconnect 重置为 false
// - 可以重新开始连接

// 4. 手动连接成功
reconnectManager.onConnected()
// 预期：正常连接，可以再次使用自动重连
```

---

## 日志示例

### 修复前（有问题）

```
D/ReconnectManager: Connection lost, attempting reconnect (1/5)
D/ReconnectManager: Connection lost, attempting reconnect (2/5)  ❌ 重复
D/ReconnectManager: Connection lost, attempting reconnect (3/5)  ❌ 重复
E/ReconnectManager: Max retry attempts reached, giving up reconnection
```

### 修复后（正确）

```
D/ReconnectManager: Connection lost, attempting reconnect (1/5)
W/ReconnectManager: Already reconnecting, ignoring duplicate onDisconnected call  ✅
W/ReconnectManager: Already reconnecting, ignoring duplicate onDisconnected call  ✅
D/ReconnectManager: Attempting to reconnect...
D/ReconnectManager: Connection established, reset reconnect state
```

---

## 相关代码

### ReconnectManager.kt

```kotlin
class ReconnectManager(
    private val config: TaplinkConfig,
    private val reconnectAction: (ConnectionConfig?, ConnectionListener) -> Unit
) {
    private var isReconnecting = false  // ✅ 新增标志
    
    fun onDisconnected(listener: ConnectionListener): Boolean {
        // ✅ 防止重复触发
        if (isReconnecting) {
            LogUtil.w(TAG, "Already reconnecting, ignoring duplicate onDisconnected call")
            return true
        }
        
        if (shouldAutoReconnect) {
            isReconnecting = true  // ✅ 设置标志
            // 开始重连...
        }
    }
    
    fun onConnected() {
        isReconnecting = false  // ✅ 重置标志
    }
    
    fun disconnect() {
        isReconnecting = false  // ✅ 重置标志
    }
}
```

---

## 手动重连机制

### 场景：自动重连次数用完后

当自动重连次数用完后，用户仍然可以手动调用 `connect()` 重新连接。

```kotlin
// 配置自动重连
val config = TaplinkConfig(...)
    .setAutoReconnect(true)
    .setRetryPolicy(maxRetries = 3, delayMs = 3000L)

TaplinkSDK.init(context, config)

// 连接
TaplinkSDK.connect(null, object : ConnectionListener {
    override fun onConnected(deviceId: String, version: String) {
        Log.d(TAG, "Connected")
    }
    
    override fun onDisconnected(reason: String) {
        Log.w(TAG, "Disconnected: $reason")
        // 自动重连次数用完后会收到这个回调
    }
    
    override fun onReconnecting(currentAttempt: Int, maxAttempts: Int) {
        Log.d(TAG, "Reconnecting: $currentAttempt/$maxAttempts")
    }
    
    override fun onError(error: ConnectionError) {
        Log.e(TAG, "Error: ${error.message}")
    }
})

// 自动重连 3 次失败后...
// 用户可以手动重连
button.setOnClickListener {
    Log.d(TAG, "User manually reconnecting...")
    TaplinkSDK.connect(null, listener)  // ✅ 可以重新连接
}
```

### prepareConnect() 的作用

当用户调用 `connect()` 时，会调用 `prepareConnect()`，它会：

1. ✅ 重置 `currentRetryCount = 0` - 重新开始计数
2. ✅ 重置 `isReconnecting = false` - 清除重连状态
3. ✅ 重置 `isManualDisconnect = false` - 允许自动重连
4. ✅ 取消所有待处理的重连任务

```kotlin
fun prepareConnect(config: ConnectionConfig?, listener: ConnectionListener) {
    LogUtil.d(TAG, "Preparing connection, resetting reconnect state")
    lastConnectionConfig = config
    lastConnectionListener = listener
    isManualDisconnect = false
    currentRetryCount = 0  // ✅ 重置计数
    isReconnecting = false  // ✅ 重置状态
    
    // 取消所有待处理的重连任务
    retryHandler.removeCallbacksAndMessages(null)
}
```

### 完整流程示例

```
1. 用户调用 connect()
   ↓
2. 自动重连 3 次
   - 第 1 次失败 → onReconnecting(1, 3)
   - 第 2 次失败 → onReconnecting(2, 3)
   - 第 3 次失败 → onReconnecting(3, 3)
   ↓
3. 重连次数用完
   - onDisconnected("Max retry attempts reached")
   - currentRetryCount = 3
   - isReconnecting = false  ✅ 重置
   ↓
4. 用户手动调用 connect()
   - prepareConnect() 被调用
   - currentRetryCount = 0  ✅ 重置
   - isReconnecting = false
   - isManualDisconnect = false
   ↓
5. 重新开始连接
   - 可以再次使用自动重连（3 次机会）
```

---

## 总结

### 修复内容

1. ✅ 添加 `isReconnecting` 标志
2. ✅ 在 `onDisconnected()` 中检查标志，防止重复触发
3. ✅ 在 `onConnected()` 和 `disconnect()` 中重置标志
4. ✅ 在 `attemptReconnect()` 中保持状态一致性
5. ✅ 在 `prepareConnect()` 中重置所有状态，支持手动重连

### 效果

- ✅ 防止重连逻辑被重复触发
- ✅ 重连次数正确计数
- ✅ 避免资源浪费
- ✅ 提升用户体验
- ✅ 支持自动重连次数用完后手动重连

### 兼容性

- ✅ 不影响现有功能
- ✅ 向后兼容
- ✅ 不需要修改调用代码

---

**文档版本**: 1.1.0  
**更新日期**: 2025-12-17  
**作者**: TaPro Team
