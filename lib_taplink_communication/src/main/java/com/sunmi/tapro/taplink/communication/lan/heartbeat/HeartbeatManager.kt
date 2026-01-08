package com.sunmi.tapro.taplink.communication.lan.heartbeat

import com.sunmi.tapro.taplink.communication.util.LogUtil
import kotlinx.coroutines.*

/**
 * 心跳管理器
 *
 * 职责：
 * - 心跳机制管理
 * - 心跳配置动态调整
 * - 心跳状态监控
 *
 * 简化设计：不再使用接口，直接使用具体类
 * 避免过度设计，保持简单实用
 *
 * @author TaPro Team
 * @since 2025-01-XX
 */
class HeartbeatManager {

    companion object {
        private const val TAG = "HeartbeatManager"
    }

    private var config: HeartbeatConfig = HeartbeatConfig()
    private var isRunning = false
    private var heartbeatJob: Job? = null
    private var listener: HeartbeatListener? = null
    private var sender: HeartbeatSender? = null
    private var stats = HeartbeatStats()

    private var lastSentTime = 0L
    private var consecutiveFailures = 0

    /**
     * 开始心跳
     *
     * @param config 心跳配置
     */
    fun startHeartbeat(config: HeartbeatConfig, sender: HeartbeatSender) {
        if (isRunning) {
            LogUtil.w(TAG, "Heartbeat already running, stopping first")
            stopHeartbeat()
        }

        this.config = config
        this.sender = sender
        this.isRunning = true
        this.consecutiveFailures = 0

        LogUtil.d(TAG, "Starting heartbeat with interval: ${config.intervalMs}ms")

        heartbeatJob = CoroutineScope(Dispatchers.IO).launch {
            while (isActive && isRunning) {
                try {
                    delay(config.intervalMs)

                    if (!isRunning) break

                    sendHeartbeat()

                } catch (e: Exception) {
                    LogUtil.e(TAG, "Heartbeat error: ${e.message}")
                    handleHeartbeatError("Heartbeat exception: ${e.message}")

                    if (consecutiveFailures >= config.maxRetryCount) {
                        LogUtil.e(TAG, "Max heartbeat failures reached, stopping")
                        break
                    }
                }
            }

            isRunning = false
            LogUtil.d(TAG, "Heartbeat loop ended")
        }
    }

    /**
     * 停止心跳
     */
    fun stopHeartbeat() {
        if (!isRunning) return

        LogUtil.d(TAG, "Stopping heartbeat")
        isRunning = false
        heartbeatJob?.cancel()
        heartbeatJob = null
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
        if (isRunning && sender != null) {
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
    fun isRunning(): Boolean = isRunning

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

        // 使用协程发送心跳（支持异步连通性检查）
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val success = sender?.sendHeartbeat(config.message) ?: false

                if (success) {
                    // 更新统计
                    stats = stats.copy(
                        totalSent = stats.totalSent + 1,
                        lastSentTime = currentTime
                    )

                    LogUtil.d(TAG, "Heartbeat sent successfully")
                    listener?.onHeartbeatSent(currentTime)

                    // 启动超时检测
                    startHeartbeatTimeoutCheck()
                } else {
                    handleHeartbeatError("Failed to send heartbeat")
                }
            } catch (e: Exception) {
                LogUtil.e(TAG, "Exception during heartbeat send: ${e.message}")
                handleHeartbeatError("Heartbeat send exception: ${e.message}")
            }
        }
    }

    /**
     * 启动心跳超时检测
     */
    private fun startHeartbeatTimeoutCheck() {
        CoroutineScope(Dispatchers.IO).launch {
            delay(config.timeoutMs)

            // 检查是否在超时时间内收到了响应
            val timeSinceLastSent = System.currentTimeMillis() - lastSentTime
            val timeSinceLastReceived = System.currentTimeMillis() - stats.lastReceivedTime

            // 如果发送后超过超时时间还没收到响应，认为超时
            if (timeSinceLastSent >= config.timeoutMs &&
                (stats.lastReceivedTime == 0L || timeSinceLastReceived > timeSinceLastSent)
            ) {
                LogUtil.w(TAG, "Heartbeat timeout detected: ${config.timeoutMs}ms")
                handleHeartbeatTimeout()
            }
        }
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
        val intervalMs: Long = 2_000L,  // 2秒间隔，总共3次心跳在6秒内完成
        val timeoutMs: Long = 5_000L,   // 5秒超时，与间隔时间一致
        val maxRetryCount: Int = 3,     // 最大重试3次
        val message: String = "{\"type\":\"ping\"}"
    ) {
        /**
         * 验证配置是否有效
         *
         * @return true if valid, false otherwise
         */
        fun isValid(): Boolean {
            return intervalMs > 0 && timeoutMs > 0 && maxRetryCount > 0 && message.isNotEmpty()
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