package com.sunmi.tapro.taplink.communication.lan.heartbeat

import com.sunmi.tapro.taplink.communication.util.LogUtil
import kotlinx.coroutines.*
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 心跳管理器
 *
 * 职责：
 * - 心跳机制管理
 * - 心跳配置动态调整
 * - 心跳状态监控
 *
 * 修复版本：
 * - 使用独立心跳线程，避免被其他协程阻塞
 * - 优化超时检测机制
 * - 增强错误处理和恢复能力
 *
 * @author TaPro Team
 * @since 2025-01-XX
 */
class HeartbeatManager {

    companion object {
        private const val TAG = "HeartbeatManager"
    }

    // 使用专用的心跳调度器，确保心跳不被其他操作影响
    private val heartbeatDispatcher = Executors.newSingleThreadExecutor { r ->
        Thread(r, "HeartbeatThread").apply {
            isDaemon = true
            priority = Thread.MAX_PRIORITY // 高优先级确保心跳及时执行
        }
    }.asCoroutineDispatcher()

    private var config: HeartbeatConfig = HeartbeatConfig()
    private val isRunning = AtomicBoolean(false)
    private var heartbeatJob: Job? = null
    private var listener: HeartbeatListener? = null
    private var sender: HeartbeatSender? = null
    private var stats = HeartbeatStats()

    private var lastSentTime = 0L
    private var consecutiveFailures = 0
    private var timeoutCheckJob: Job? = null

    /**
     * 开始心跳
     *
     * @param config 心跳配置
     */
    fun startHeartbeat(config: HeartbeatConfig, sender: HeartbeatSender) {
        if (isRunning.get()) {
            LogUtil.w(TAG, "Heartbeat already running, stopping first")
            stopHeartbeat()
        }

        this.config = config
        this.sender = sender
        this.isRunning.set(true)
        this.consecutiveFailures = 0

        LogUtil.d(TAG, "Starting heartbeat with interval: ${config.intervalMs}ms, timeout: ${config.timeoutMs}ms")

        heartbeatJob = CoroutineScope(heartbeatDispatcher).launch {
            while (isActive && isRunning.get()) {
                try {
                    delay(config.intervalMs)

                    if (!isRunning.get()) break

                    sendHeartbeat()

                } catch (e: CancellationException) {
                    LogUtil.d(TAG, "Heartbeat cancelled")
                    break
                } catch (e: Exception) {
                    LogUtil.e(TAG, "Heartbeat error: ${e.message}")
                    handleHeartbeatError("Heartbeat exception: ${e.message}")

                    if (consecutiveFailures >= config.maxRetryCount) {
                        LogUtil.e(TAG, "Max heartbeat failures reached, stopping")
                        break
                    }
                }
            }

            isRunning.set(false)
            LogUtil.d(TAG, "Heartbeat loop ended")
        }
    }

    /**
     * 停止心跳
     */
    fun stopHeartbeat() {
        if (!isRunning.get()) return

        LogUtil.d(TAG, "Stopping heartbeat")
        isRunning.set(false)
        
        // 取消心跳任务
        heartbeatJob?.cancel()
        heartbeatJob = null
        
        // 取消超时检测任务
        timeoutCheckJob?.cancel()
        timeoutCheckJob = null
    }

    /**
     * 更新心跳配置
     *
     * @param config 新的心跳配置
     */
    fun updateConfig(config: HeartbeatConfig) {
        val oldConfig = this.config
        this.config = config

        LogUtil.d(TAG, "Heartbeat config updated: ${oldConfig.intervalMs}ms -> ${config.intervalMs}ms")
        listener?.onConfigUpdated(oldConfig, config)

        // 如果正在运行，重启以应用新配置
        if (isRunning.get() && sender != null) {
            LogUtil.d(TAG, "Restarting heartbeat with new config")
            val currentSender = sender!!
            stopHeartbeat()
            startHeartbeat(config, currentSender)
        }
    }

    /**
     * 检查心跳是否正在运行
     *
     * @return true if running, false otherwise
     */
    fun isRunning(): Boolean = isRunning.get()

    /**
     * 获取当前心跳配置
     *
     * @return 当前心跳配置
     */
    fun getCurrentConfig(): HeartbeatConfig = config

    /**
     * 设置心跳监听器
     *
     * @param listener 心跳监听器
     */
    fun setHeartbeatListener(listener: HeartbeatListener?) {
        this.listener = listener
        LogUtil.d(TAG, "Heartbeat listener ${if (listener != null) "set" else "removed"}")
    }

    /**
     * 检查是否是心跳响应
     *
     * @param message 接收到的消息
     * @return true 如果是心跳响应，false 否则
     */
    fun isHeartbeatResponse(message: String): Boolean {
        // 更严格的心跳响应检测
        return try {
            // 检查是否是JSON格式的pong响应
            message.contains("\"type\":\"pong\"") ||
            message.contains("pong") ||
            // 或者是简单的pong响应
            message.trim().equals("pong", ignoreCase = true)
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 处理心跳响应
     *
     * @param responseTime 响应时间戳
     */
    fun handleHeartbeatResponse(responseTime: Long = System.currentTimeMillis()) {
        val roundTripTime = responseTime - lastSentTime

        // 重置失败计数
        consecutiveFailures = 0

        // 更新统计
        stats = stats.copy(
            totalReceived = stats.totalReceived + 1,
            lastReceivedTime = responseTime,
            averageResponseTime = if (stats.totalReceived > 0) {
                (stats.averageResponseTime * stats.totalReceived + roundTripTime) / (stats.totalReceived + 1)
            } else {
                roundTripTime
            },
            consecutiveFailures = 0
        )

        LogUtil.d(TAG, "Heartbeat response received, RTT: ${roundTripTime}ms")
        listener?.onHeartbeatResponse(responseTime, roundTripTime)
    }

    /**
     * 获取心跳统计信息
     *
     * @return 心跳统计信息
     */
    fun getHeartbeatStats(): HeartbeatStats = stats

    /**
     * 发送心跳消息
     */
    private fun sendHeartbeat() {
        val currentTime = System.currentTimeMillis()
        lastSentTime = currentTime

        // 使用专用调度器发送心跳，并设置发送超时
        CoroutineScope(heartbeatDispatcher).launch {
            try {
                val success = withTimeout(config.sendTimeoutMs) {
                    sender?.sendHeartbeat(config.message) ?: false
                }

                if (success) {
                    // 更新统计
                    stats = stats.copy(
                        totalSent = stats.totalSent + 1,
                        lastSentTime = currentTime
                    )

                    LogUtil.d(TAG, "Heartbeat sent successfully at $currentTime")
                    listener?.onHeartbeatSent(currentTime)

                    // 启动增强的超时检测
                    startEnhancedTimeoutCheck()
                } else {
                    handleHeartbeatError("Failed to send heartbeat")
                }
            } catch (e: TimeoutCancellationException) {
                LogUtil.e(TAG, "Heartbeat send timeout: ${config.sendTimeoutMs}ms")
                handleHeartbeatError("Heartbeat send timeout")
            } catch (e: Exception) {
                LogUtil.e(TAG, "Exception during heartbeat send: ${e.message}")
                handleHeartbeatError("Heartbeat send exception: ${e.message}")
            }
        }
    }

    /**
     * 启动心跳超时检测（简化版）
     */
    private fun startEnhancedTimeoutCheck() {
        // 取消之前的超时检测
        timeoutCheckJob?.cancel()
        
        timeoutCheckJob = CoroutineScope(heartbeatDispatcher).launch {
            val sentTime = lastSentTime
            
            // 等待超时时间
            delay(config.timeoutMs)
            
            // 检查是否在超时时间内收到了响应
            if (!hasReceivedResponseSince(sentTime)) {
                LogUtil.w(TAG, "Heartbeat timeout detected: ${config.timeoutMs}ms")
                handleHeartbeatTimeout()
            }
        }
    }

    /**
     * 检查是否在指定时间后收到了响应
     */
    private fun hasReceivedResponseSince(sentTime: Long): Boolean {
        return stats.lastReceivedTime > sentTime
    }

    /**
     * 处理心跳超时
     */
    private fun handleHeartbeatTimeout() {
        consecutiveFailures++

        // 更新统计
        stats = stats.copy(consecutiveFailures = consecutiveFailures)

        LogUtil.w(TAG, "Heartbeat timeout ($consecutiveFailures/${config.maxRetryCount})")

        if (consecutiveFailures >= config.maxRetryCount) {
            listener?.onHeartbeatFailed("Heartbeat timeout after ${config.maxRetryCount} attempts", consecutiveFailures)
        } else {
            listener?.onHeartbeatTimeout(config.timeoutMs, consecutiveFailures)
        }
    }

    /**
     * 处理心跳错误
     */
    private fun handleHeartbeatError(error: String) {
        consecutiveFailures++

        // 更新统计
        stats = stats.copy(consecutiveFailures = consecutiveFailures)

        LogUtil.w(TAG, "Heartbeat error ($consecutiveFailures/${config.maxRetryCount}): $error")

        if (consecutiveFailures >= config.maxRetryCount) {
            listener?.onHeartbeatFailed(error, consecutiveFailures)
        } else {
            listener?.onHeartbeatTimeout(config.timeoutMs, consecutiveFailures)
        }
    }

    /**
     * 心跳配置
     */
    data class HeartbeatConfig(
        val intervalMs: Long = 5_000L,      // 5秒间隔
        val timeoutMs: Long = 5_000L,      // 每次检测5秒超时
        val maxRetryCount: Int = 3,         // 连续3次失败，总共15秒（3次 × 5秒）
        val sendTimeoutMs: Long = 3_000L,   // 发送超时时间
        val message: String = "{\"type\":\"ping\",\"timestamp\":${System.currentTimeMillis()}}"
    ) {
        /**
         * 验证配置是否有效
         *
         * @return true if valid, false otherwise
         */
        fun isValid(): Boolean {
            return intervalMs > 0 && 
                   timeoutMs > 0 && 
                   maxRetryCount > 0 && 
                   sendTimeoutMs > 0 &&
                   sendTimeoutMs < timeoutMs &&
                   message.isNotEmpty()
        }
    }

    /**
     * 心跳统计信息
     */
    data class HeartbeatStats(
        val totalSent: Long = 0,
        val totalReceived: Long = 0,
        val consecutiveFailures: Int = 0,
        val lastSentTime: Long = 0,
        val lastReceivedTime: Long = 0,
        val averageResponseTime: Long = 0
    ) {
        /**
         * 获取成功率
         *
         * @return 成功率（0.0 - 1.0）
         */
        fun getSuccessRate(): Double {
            return if (totalSent > 0) totalReceived.toDouble() / totalSent.toDouble() else 0.0
        }

        /**
         * 检查心跳是否健康
         *
         * @param maxConsecutiveFailures 最大连续失败次数
         * @return true if healthy, false otherwise
         */
        fun isHealthy(maxConsecutiveFailures: Int = 3): Boolean {
            return consecutiveFailures < maxConsecutiveFailures
        }
    }

    /**
     * 心跳监听器
     */
    interface HeartbeatListener {
        /**
         * 心跳发送成功
         *
         * @param sentTime 发送时间
         */
        fun onHeartbeatSent(sentTime: Long)

        /**
         * 心跳响应接收
         *
         * @param responseTime 响应时间
         * @param roundTripTime 往返时间
         */
        fun onHeartbeatResponse(responseTime: Long, roundTripTime: Long)

        /**
         * 心跳响应延迟警告
         *
         * @param timeoutMs 超时时间
         * @param ratio 延迟比例（0.0-1.0）
         */
        fun onHeartbeatDelayed(timeoutMs: Long, ratio: Double) {}

        /**
         * 心跳超时
         *
         * @param timeoutMs 超时时间
         * @param consecutiveFailures 连续失败次数
         */
        fun onHeartbeatTimeout(timeoutMs: Long, consecutiveFailures: Int)

        /**
         * 心跳失败
         *
         * @param error 错误信息
         * @param consecutiveFailures 连续失败次数
         */
        fun onHeartbeatFailed(error: String, consecutiveFailures: Int)

        /**
         * 心跳配置更新
         *
         * @param oldConfig 旧配置
         * @param newConfig 新配置
         */
        fun onConfigUpdated(oldConfig: HeartbeatConfig, newConfig: HeartbeatConfig)
    }

    /**
     * 心跳发送器
     */
    interface HeartbeatSender {
        /**
         * 发送心跳消息
         *
         * @param message 心跳消息
         * @return true if sent successfully, false otherwise
         */
        suspend fun sendHeartbeat(message: String): Boolean
    }
}