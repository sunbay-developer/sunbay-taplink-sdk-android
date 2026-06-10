# 连接回调多次调用问题分析

## 问题现象

从日志中可以看到，连接错误回调被重复调用了多次：

```
2026-01-11 11:16:50.568 Taplin...ervice: Connection error - Code: T04, Message: WebSocket connection failed (LAN mode):Heartbeat failed: Failed to send heartbeat
2026-01-11 11:16:50.568 Taplin...ervice: Connection error - Code: T04, Message: WebSocket connection failed (LAN mode):Heartbeat failed: Failed to send heartbeat
2026-01-11 11:16:50.569 com.sunmi.cb: ===========onError======T04,WebSocket connection failed (LAN mode):Heartbeat failed: Failed to send heartbeat

2026-01-11 11:16:50.579 Taplin...ervice: Connection error - Code: T04, Message: WebSocket connection failed (LAN mode):Heartbeat failed: Heartbeat exception: StandaloneCoroutine was cancelled
2026-01-11 11:16:50.580 Taplin...ervice: Connection error - Code: T04, Message: WebSocket connection failed (LAN mode):Heartbeat failed: Heartbeat exception: StandaloneCoroutine was cancelled
2026-01-11 11:16:50.580 com.sunmi.cb: ===========onError======T04,WebSocket connection failed (LAN mode):Heartbeat failed: Heartbeat exception: StandaloneCoroutine was cancelled

2026-01-11 11:16:52.470 Taplin...ervice: Connection error - Code: T04, Message: WebSocket connection failed (LAN mode):Heartbeat failed: Failed to send heartbeat
2026-01-11 11:16:52.470 Taplin...ervice: Connection error - Code: T04, Message: WebSocket connection failed (LAN mode):Heartbeat failed: Failed to send heartbeat
2026-01-11 11:16:52.471 com.sunmi.cb: ===========onError======T04,WebSocket connection failed (LAN mode):Heartbeat failed: Failed to send heartbeat
```

## 根本原因分析

### 1. 心跳失败触发多次断开

从日志时间线分析：

```
11:16:46.421 - Heartbeat error (1/3): Failed to send heartbeat
11:16:48.433 - Heartbeat error (2/3): Failed to send heartbeat  
11:16:50.451 - Heartbeat error (3/3): Failed to send heartbeat
11:16:50.452 - Heartbeat failed: Failed to send heartbeat, consecutive failures: 3
11:16:50.453 - Too many heartbeat failures, disconnecting connection
```

**问题1**: 心跳管理器达到最大失败次数(3次)后触发断开，但断开过程中又产生了新的心跳失败。

### 2. 协程取消导致的级联错误

```
11:16:50.574 - Heartbeat error: StandaloneCoroutine was cancelled
11:16:50.575 - Heartbeat failed: Heartbeat exception: StandaloneCoroutine was cancelled, consecutive failures: 4
11:16:50.576 - Too many heartbeat failures, disconnecting connection
```

**问题2**: 当第一次断开时，心跳协程被取消，但取消操作本身又被当作心跳失败处理，触发了第二次断开。

### 3. 重复的断开逻辑调用

```
11:16:50.570 - Disconnecting due to heartbeat failure, keeping service discovery active
11:16:50.581 - Disconnecting due to heartbeat failure, keeping service discovery active
```

**问题3**: `disconnectOnHeartbeatFailure()` 方法被调用了两次，每次都触发了完整的断开流程。

### 4. 多个监听器注册

从日志中可以看到相同的错误信息被输出了多次：
- `Taplin...ervice` 输出了两次相同的错误
- `com.sunmi.cb` 输出了一次错误

**问题4**: 可能存在多个连接状态监听器被注册，导致同一个错误被多次通知。

## 问题根源

### 心跳管理器的状态管理问题

1. **缺乏状态保护**: 心跳失败处理没有检查当前是否已经在断开过程中
2. **协程取消处理不当**: 协程取消异常被当作普通心跳失败处理
3. **重复断开保护缺失**: 没有防止重复调用断开逻辑的机制

### 连接管理器的监听器管理问题

1. **监听器重复注册**: 可能存在同一个监听器被多次注册
2. **监听器清理不彻底**: 断开时没有正确清理所有监听器
3. **状态同步问题**: 多个组件的状态没有正确同步

## 修复方案

### 1. 心跳管理器状态保护

```kotlin
class HeartbeatManager {
    private val isDisconnecting = AtomicBoolean(false)
    private val maxConsecutiveFailures = 3
    
    private fun handleHeartbeatError(error: String) {
        val currentFailures = consecutiveFailures.incrementAndGet()
        
        if (currentFailures >= maxConsecutiveFailures) {
            // 使用原子操作防止重复断开
            if (isDisconnecting.compareAndSet(false, true)) {
                LogUtil.e(TAG, "Max heartbeat failures reached, initiating disconnect")
                heartbeatListener?.onHeartbeatFailed(error, currentFailures)
            } else {
                LogUtil.d(TAG, "Disconnect already in progress, ignoring additional failure")
            }
        }
    }
    
    fun stopHeartbeat() {
        try {
            heartbeatJob?.cancel()
            isDisconnecting.set(false) // 重置状态
        } catch (e: Exception) {
            // 协程取消异常不应该被当作心跳失败处理
            LogUtil.d(TAG, "Heartbeat stopped: ${e.message}")
        }
    }
}
```

### 2. 连接管理器重复保护

```kotlin
class LanClientKernel {
    private val isDisconnecting = AtomicBoolean(false)
    
    override fun onHeartbeatFailed(error: String, consecutiveFailures: Int) {
        LogUtil.e(TAG, "Heartbeat failed: $error, consecutive failures: $consecutiveFailures")
        
        if (consecutiveFailures >= 3) {
            // 防止重复断开
            if (isDisconnecting.compareAndSet(false, true)) {
                LogUtil.e(TAG, "Too many heartbeat failures, disconnecting connection")
                disconnectOnHeartbeatFailure(error)
            } else {
                LogUtil.d(TAG, "Disconnect already in progress, ignoring heartbeat failure")
            }
        }
    }
    
    private fun disconnectOnHeartbeatFailure(reason: String) {
        try {
            // 执行断开逻辑
            stopHeartbeat()
            connectionManager.disconnect()
            updateStatus(InnerConnectionStatus.ERROR)
            
            // 通知回调
            connectionCallback?.onDisconnected("T04", "WebSocket connection failed (LAN mode):$reason")
            
        } finally {
            // 重置断开状态
            isDisconnecting.set(false)
        }
    }
}
```

### 3. 监听器管理优化

```kotlin
class ConnectionManager {
    private val statusListeners = ConcurrentHashMap.newKeySet<ConnectionStatusListener>()
    private val listenerLock = Mutex()
    
    suspend fun addStatusListener(listener: ConnectionStatusListener) {
        listenerLock.withLock {
            if (statusListeners.add(listener)) {
                LogUtil.d(TAG, "Status listener added, total: ${statusListeners.size}")
            } else {
                LogUtil.w(TAG, "Status listener already exists, skipping")
            }
        }
    }
    
    suspend fun removeStatusListener(listener: ConnectionStatusListener) {
        listenerLock.withLock {
            if (statusListeners.remove(listener)) {
                LogUtil.d(TAG, "Status listener removed, remaining: ${statusListeners.size}")
            }
        }
    }
    
    private suspend fun notifyStatusChanged(status: InnerConnectionStatus, error: String? = null) {
        listenerLock.withLock {
            statusListeners.forEach { listener ->
                try {
                    if (error != null) {
                        listener.onError("T04", error)
                    } else {
                        listener.onStatusChanged(status)
                    }
                } catch (e: Exception) {
                    LogUtil.e(TAG, "Error notifying listener: ${e.message}")
                }
            }
        }
    }
}
```

### 4. 协程取消异常处理

```kotlin
class HeartbeatManager {
    private suspend fun heartbeatLoop() {
        try {
            while (isActive) {
                delay(heartbeatInterval)
                sendHeartbeat()
            }
        } catch (e: CancellationException) {
            // 协程取消是正常操作，不应该当作错误处理
            LogUtil.d(TAG, "Heartbeat loop cancelled normally")
            throw e // 重新抛出，让协程框架处理
        } catch (e: Exception) {
            // 只有真正的异常才当作心跳失败处理
            LogUtil.e(TAG, "Heartbeat exception: ${e.message}")
            handleHeartbeatError("Heartbeat exception: ${e.message}")
        }
    }
}
```

## 验证方案

### 1. 日志验证

修复后的日志应该是：

```
11:16:46.421 - Heartbeat error (1/3): Failed to send heartbeat
11:16:48.433 - Heartbeat error (2/3): Failed to send heartbeat  
11:16:50.451 - Heartbeat error (3/3): Failed to send heartbeat
11:16:50.452 - Max heartbeat failures reached, initiating disconnect
11:16:50.453 - Disconnecting due to heartbeat failure
11:16:50.570 - Heartbeat stopped normally
11:16:50.571 - Connection error - Code: T04, Message: WebSocket connection failed (LAN mode):Heartbeat failed: Failed to send heartbeat
```

**关键改进**:
- 只有一次 "initiating disconnect" 日志
- 只有一次 "Connection error" 日志
- 协程取消不再被当作心跳失败

### 2. 单元测试

```kotlin
@Test
fun testHeartbeatFailureNoDuplicateDisconnect() {
    val kernel = LanClientKernel()
    val callbackCount = AtomicInteger(0)
    
    kernel.setConnectionCallback(object : ConnectionCallback {
        override fun onDisconnected(code: String, message: String) {
            callbackCount.incrementAndGet()
        }
    })
    
    // 模拟多次心跳失败
    repeat(5) {
        kernel.onHeartbeatFailed("Test failure", it + 1)
    }
    
    // 验证只有一次断开回调
    assertEquals(1, callbackCount.get())
}
```

### 3. 集成测试

```kotlin
@Test
fun testNetworkInstabilityScenario() {
    // 模拟网络不稳定场景
    // 验证不会产生重复的错误回调
}
```

## 性能影响

### 内存优化
- 使用 `AtomicBoolean` 替代复杂的状态管理，内存开销极小
- `ConcurrentHashMap.newKeySet()` 比 `CopyOnWriteArraySet` 更高效

### CPU优化  
- 原子操作比同步块更高效
- 减少了重复的断开操作，降低CPU使用

### 网络优化
- 避免重复的断开请求，减少网络开销
- 更快的错误恢复，提升用户体验

## 总结

连接回调被多次调用的根本原因是：

1. **心跳失败处理缺乏状态保护**，导致重复断开
2. **协程取消异常被误处理**，触发级联错误
3. **监听器管理不当**，可能存在重复注册
4. **缺乏重复操作保护机制**

通过引入原子状态管理、正确处理协程取消异常、优化监听器管理，可以彻底解决这个问题，确保每次网络问题只触发一次错误回调。