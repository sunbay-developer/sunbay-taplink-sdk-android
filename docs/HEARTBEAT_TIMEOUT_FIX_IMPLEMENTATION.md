# 心跳超时问题修复实施方案

## 问题分析

根据日志分析，发现以下关键问题：

### 1. 心跳中断问题
- **时间**: 18:49:48.543 超时，超时时长 16.8秒
- **根源**: 客户端在 18:49:31.725 后完全停止发送心跳
- **持续时间**: 约17秒心跳中断
- **触发**: 服务端15秒超时机制

### 2. 代码层面问题
通过代码分析发现：

1. **心跳发送依赖协程**: 心跳发送使用 `CoroutineScope(Dispatchers.IO).launch`，可能受到协程调度影响
2. **交易处理可能阻塞**: 交易完成后的处理逻辑可能影响心跳线程
3. **网络连通性检查开销**: 每次心跳都进行网络连通性检查，增加延迟
4. **超时检测机制不够健壮**: 当前超时检测依赖单次检查

## 修复方案

### 1. 心跳发送稳定性增强

#### 1.1 独立心跳线程
```kotlin
class HeartbeatManager {
    // 使用专用的心跳调度器，避免被其他协程影响
    private val heartbeatDispatcher = Executors.newSingleThreadExecutor { r ->
        Thread(r, "HeartbeatThread").apply {
            isDaemon = true
            priority = Thread.MAX_PRIORITY // 高优先级
        }
    }.asCoroutineDispatcher()
    
    private fun sendHeartbeat() {
        val currentTime = System.currentTimeMillis()
        lastSentTime = currentTime

        // 使用专用调度器，确保心跳发送不被阻塞
        CoroutineScope(heartbeatDispatcher).launch {
            try {
                val success = withTimeout(config.timeoutMs / 2) { // 使用一半超时时间作为发送超时
                    sender?.sendHeartbeat(config.message) ?: false
                }

                if (success) {
                    stats = stats.copy(
                        totalSent = stats.totalSent + 1,
                        lastSentTime = currentTime
                    )
                    LogUtil.d(TAG, "Heartbeat sent successfully at $currentTime")
                    listener?.onHeartbeatSent(currentTime)
                    startHeartbeatTimeoutCheck()
                } else {
                    handleHeartbeatError("Failed to send heartbeat")
                }
            } catch (e: TimeoutCancellationException) {
                LogUtil.e(TAG, "Heartbeat send timeout: ${e.message}")
                handleHeartbeatError("Heartbeat send timeout")
            } catch (e: Exception) {
                LogUtil.e(TAG, "Exception during heartbeat send: ${e.message}")
                handleHeartbeatError("Heartbeat send exception: ${e.message}")
            }
        }
    }
}
```

#### 1.2 优化心跳配置
```kotlin
data class HeartbeatConfig(
    val intervalMs: Long = 5_000L,      // 增加到5秒间隔，减少网络压力
    val timeoutMs: Long = 8_000L,       // 8秒超时，给网络更多时间
    val maxRetryCount: Int = 2,         // 减少到2次重试，更快检测问题
    val sendTimeoutMs: Long = 3_000L,   // 新增：发送超时时间
    val message: String = "{\"type\":\"ping\",\"timestamp\":${System.currentTimeMillis()}}"
) {
    // 添加时间戳，便于调试和去重
}
```

### 2. 连接管理器优化

#### 2.1 简化心跳发送逻辑
```kotlin
class ConnectionManager {
    /**
     * 简化的心跳发送方法，减少检查开销
     */
    suspend fun sendHeartbeat(message: String): Boolean {
        return try {
            val client = this.client
            if (client == null || client.isClosed || client.isClosing) {
                LogUtil.w(TAG, "Cannot send heartbeat: WebSocket not available")
                isConnected.set(false)
                return false
            }

            // 直接发送，不进行额外的网络连通性检查
            // 网络问题会通过WebSocket的onError/onClose回调检测到
            client.send(message)
            LogUtil.d(TAG, "Heartbeat sent: $message")
            true
            
        } catch (e: Exception) {
            LogUtil.e(TAG, "Failed to send heartbeat: ${e.message}")
            isConnected.set(false)
            false
        }
    }
    
    /**
     * 保留带连通性检查的方法，仅在需要时使用
     */
    suspend fun sendHeartbeatWithConnectivityCheck(message: String): Boolean {
        // 现有实现保持不变，但仅在特殊情况下使用
        return sendHeartbeat(message) // 暂时简化为直接发送
    }
}
```

### 3. 增强超时检测机制

#### 3.1 分级超时处理
```kotlin
class HeartbeatManager {
    private fun startHeartbeatTimeoutCheck() {
        CoroutineScope(heartbeatDispatcher).launch {
            // 第一级：警告超时（60%超时时间）
            delay((config.timeoutMs * 0.6).toLong())
            
            if (!hasReceivedResponseSince(lastSentTime)) {
                LogUtil.w(TAG, "Heartbeat response delayed (60% timeout)")
                listener?.onHeartbeatDelayed(config.timeoutMs, 0.6)
            }
            
            // 第二级：严重超时（100%超时时间）
            delay((config.timeoutMs * 0.4).toLong())
            
            if (!hasReceivedResponseSince(lastSentTime)) {
                LogUtil.w(TAG, "Heartbeat timeout detected: ${config.timeoutMs}ms")
                handleHeartbeatTimeout()
            }
        }
    }
    
    private fun hasReceivedResponseSince(sentTime: Long): Boolean {
        return stats.lastReceivedTime > sentTime
    }
    
    // 新增监听器方法
    interface HeartbeatListener {
        // 现有方法...
        
        /**
         * 心跳响应延迟警告
         */
        fun onHeartbeatDelayed(timeoutMs: Long, ratio: Double) {}
    }
}
```

### 4. 交易处理隔离

#### 4.1 确保交易处理不影响心跳
```kotlin
class LanClientKernel {
    // 使用独立的调度器处理交易响应
    private val transactionDispatcher = Dispatchers.IO.limitedParallelism(2)
    
    override fun onMessage(message: String) {
        // 优先处理心跳响应
        if (heartbeatManager.isHeartbeatResponse(message)) {
            handleHeartbeatResponse()
            return
        }
        
        // 交易响应使用独立调度器处理，避免阻塞心跳
        CoroutineScope(transactionDispatcher).launch {
            try {
                processTransactionResponse(message)
            } catch (e: Exception) {
                LogUtil.e(TAG, "Error processing transaction response: ${e.message}")
            }
        }
    }
    
    private fun processTransactionResponse(message: String) {
        // 现有的交易响应处理逻辑
        val parseResult = protocolManager.parseResponse(message)
        handleProtocolResponse(parseResult)
    }
}
```

### 5. 监控和告警增强

#### 5.1 心跳健康监控
```kotlin
class HeartbeatHealthMonitor {
    private var healthStats = HealthStats()
    
    data class HealthStats(
        val totalHeartbeats: Long = 0,
        val successfulHeartbeats: Long = 0,
        val timeouts: Long = 0,
        val averageRtt: Long = 0,
        val maxRtt: Long = 0,
        val lastHealthCheck: Long = 0
    )
    
    fun checkHealth(): HealthReport {
        val now = System.currentTimeMillis()
        val successRate = if (healthStats.totalHeartbeats > 0) {
            healthStats.successfulHeartbeats.toDouble() / healthStats.totalHeartbeats
        } else 0.0
        
        return HealthReport(
            isHealthy = successRate >= 0.8 && healthStats.maxRtt < 5000,
            successRate = successRate,
            averageRtt = healthStats.averageRtt,
            maxRtt = healthStats.maxRtt,
            recommendation = getRecommendation(successRate, healthStats.maxRtt)
        )
    }
    
    private fun getRecommendation(successRate: Double, maxRtt: Long): String {
        return when {
            successRate < 0.5 -> "网络连接不稳定，建议检查网络环境"
            maxRtt > 10000 -> "网络延迟过高，建议优化网络配置"
            successRate < 0.8 -> "心跳成功率偏低，建议调整心跳间隔"
            else -> "心跳状态正常"
        }
    }
}
```

### 6. 自动重连机制

#### 6.1 智能重连策略
```kotlin
class LanClientKernel {
    private var reconnectAttempts = 0
    private val maxReconnectAttempts = 5
    private val baseReconnectDelay = 2000L // 2秒基础延迟
    
    private fun disconnectOnHeartbeatFailure() {
        launchInScope {
            try {
                LogUtil.d(TAG, "Disconnecting due to heartbeat failure")
                
                stopHeartbeat()
                
                // 更新状态
                updateStatus(InnerConnectionStatus.DISCONNECTED)
                
                // 通知错误
                callbackManager.notifyError(
                    InnerErrorCode.T06.code,
                    "Connection lost due to heartbeat failure"
                )
                
                // 启动智能重连
                startSmartReconnect()
                
            } catch (e: Exception) {
                LogUtil.e(TAG, "Error during heartbeat failure disconnect: ${e.message}")
            }
        }
    }
    
    private fun startSmartReconnect() {
        if (reconnectAttempts >= maxReconnectAttempts) {
            LogUtil.e(TAG, "Max reconnect attempts reached, stopping auto-reconnect")
            return
        }
        
        reconnectAttempts++
        val delay = baseReconnectDelay * (1 shl (reconnectAttempts - 1)) // 指数退避
        
        LogUtil.d(TAG, "Starting smart reconnect attempt $reconnectAttempts after ${delay}ms")
        
        launchInScope {
            delay(delay)
            
            // 检查是否还需要重连
            if (currentInnerConnectionStatus == InnerConnectionStatus.DISCONNECTED) {
                // 尝试重新发现和连接服务
                val services = serviceDiscoveryManager.getDiscoveredServices()
                val targetService = services.find { it.name == currentService?.name }
                
                if (targetService != null) {
                    LogUtil.d(TAG, "Found target service for reconnection: ${targetService.name}")
                    connectToService(targetService)
                } else {
                    LogUtil.w(TAG, "Target service not found, continuing service discovery")
                    // 继续等待服务发现
                    startSmartReconnect()
                }
            }
        }
    }
}
```

## 实施优先级

### 高优先级（立即修复）
1. **独立心跳线程**: 防止心跳被其他操作阻塞
2. **简化心跳发送**: 移除不必要的网络检查
3. **优化心跳配置**: 调整间隔和超时时间

### 中优先级（短期优化）
1. **分级超时处理**: 提供更好的超时检测
2. **交易处理隔离**: 确保交易不影响心跳
3. **健康监控**: 添加心跳健康状态监控

### 低优先级（长期改进）
1. **智能重连**: 实现自动重连机制
2. **性能优化**: 进一步优化心跳性能
3. **监控告警**: 完善监控和告警系统

## 测试验证

### 1. 心跳稳定性测试
- 长时间运行测试（24小时）
- 高负载交易测试
- 网络不稳定环境测试

### 2. 超时检测测试
- 模拟网络延迟
- 模拟服务端无响应
- 验证分级超时机制

### 3. 重连机制测试
- 模拟网络断开
- 验证自动重连功能
- 测试重连成功率

## 预期效果

1. **心跳稳定性提升**: 消除17秒心跳中断问题
2. **连接可靠性增强**: 减少因心跳超时导致的连接断开
3. **用户体验改善**: 减少重新连接的频率
4. **系统稳定性提升**: 提高整体系统的健壮性