package com.sunmi.tapro.taplink.communication.lan.heartbeat

import com.sunmi.tapro.taplink.communication.util.LogUtil
import kotlinx.coroutines.*
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Heartbeat manager
 *
 * Responsibilities:
 * - Heartbeat mechanism management
 * - Dynamic heartbeat configuration adjustment
 * - Heartbeat status monitoring
 *
 * Fixed version:
 * - Use independent heartbeat thread to avoid being blocked by other coroutines
 * - Optimize timeout detection mechanism
 * - Enhance error handling and recovery capabilities
 *
 * @author TaPro Team
 * @since 2025-01-XX
 */
class HeartbeatManager {

    companion object {
        private const val TAG = "HeartbeatManager"
    }

    // Use dedicated heartbeat dispatcher to ensure heartbeat is not affected by other operations
    private val heartbeatDispatcher = Executors.newSingleThreadExecutor { r ->
        Thread(r, "HeartbeatThread").apply {
            isDaemon = true
            priority = Thread.MAX_PRIORITY // High priority ensures heartbeat executes timely
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
     * Start heartbeat
     *
     * @param config Heartbeat configuration
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
     * Stop heartbeat
     */
    fun stopHeartbeat() {
        if (!isRunning.get()) return

        LogUtil.d(TAG, "Stopping heartbeat")
        isRunning.set(false)
        
        // Cancel heartbeat task
        heartbeatJob?.cancel()
        heartbeatJob = null
        
        // Cancel timeout check task
        timeoutCheckJob?.cancel()
        timeoutCheckJob = null
    }

    /**
     * Update heartbeat configuration
     *
     * @param config New heartbeat configuration
     */
    fun updateConfig(config: HeartbeatConfig) {
        val oldConfig = this.config
        this.config = config

        LogUtil.d(TAG, "Heartbeat config updated: ${oldConfig.intervalMs}ms -> ${config.intervalMs}ms")
        listener?.onConfigUpdated(oldConfig, config)

        // If running, restart to apply new configuration
        if (isRunning.get() && sender != null) {
            LogUtil.d(TAG, "Restarting heartbeat with new config")
            val currentSender = sender!!
            stopHeartbeat()
            startHeartbeat(config, currentSender)
        }
    }

    /**
     * Check if heartbeat is running
     *
     * @return true if running, false otherwise
     */
    fun isRunning(): Boolean = isRunning.get()

    /**
     * Get current heartbeat configuration
     *
     * @return Current heartbeat configuration
     */
    fun getCurrentConfig(): HeartbeatConfig = config

    /**
     * Set heartbeat listener
     *
     * @param listener Heartbeat listener
     */
    fun setHeartbeatListener(listener: HeartbeatListener?) {
        this.listener = listener
        LogUtil.d(TAG, "Heartbeat listener ${if (listener != null) "set" else "removed"}")
    }

    /**
     * Check if it's a heartbeat response
     *
     * @param message Received message
     * @return true if it's a heartbeat response, false otherwise
     */
    fun isHeartbeatResponse(message: String): Boolean {
        // Stricter heartbeat response detection
        return try {
            // Check if it's a JSON format pong response
            message.contains("\"type\":\"pong\"") ||
            message.contains("pong") ||
            // Or a simple pong response
            message.trim().equals("pong", ignoreCase = true)
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Handle heartbeat response
     *
     * @param responseTime Response timestamp
     */
    fun handleHeartbeatResponse(responseTime: Long = System.currentTimeMillis()) {
        val roundTripTime = responseTime - lastSentTime

        // Reset failure count
        consecutiveFailures = 0

        // Update statistics
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
     * Get heartbeat statistics
     *
     * @return Heartbeat statistics
     */
    fun getHeartbeatStats(): HeartbeatStats = stats

    /**
     * Send heartbeat message
     */
    private fun sendHeartbeat() {
        val currentTime = System.currentTimeMillis()
        lastSentTime = currentTime

        // Use dedicated dispatcher to send heartbeat and set send timeout
        CoroutineScope(heartbeatDispatcher).launch {
            try {
                val success = withTimeout(config.sendTimeoutMs) {
                    sender?.sendHeartbeat(config.message) ?: false
                }

                if (success) {
                    // Update statistics
                    stats = stats.copy(
                        totalSent = stats.totalSent + 1,
                        lastSentTime = currentTime
                    )

                    LogUtil.d(TAG, "Heartbeat sent successfully at $currentTime")
                    listener?.onHeartbeatSent(currentTime)

                    // Start enhanced timeout check
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
     * Start heartbeat timeout check (simplified version)
     */
    private fun startEnhancedTimeoutCheck() {
        // Cancel previous timeout check
        timeoutCheckJob?.cancel()
        
        timeoutCheckJob = CoroutineScope(heartbeatDispatcher).launch {
            val sentTime = lastSentTime
            
            // Wait for timeout duration
            delay(config.timeoutMs)
            
            // Check if response was received within timeout period
            if (!hasReceivedResponseSince(sentTime)) {
                LogUtil.w(TAG, "Heartbeat timeout detected: ${config.timeoutMs}ms")
                handleHeartbeatTimeout()
            }
        }
    }

    /**
     * Check if response was received after specified time
     */
    private fun hasReceivedResponseSince(sentTime: Long): Boolean {
        return stats.lastReceivedTime > sentTime
    }

    /**
     * Handle heartbeat timeout
     */
    private fun handleHeartbeatTimeout() {
        consecutiveFailures++

        // Update statistics
        stats = stats.copy(consecutiveFailures = consecutiveFailures)

        LogUtil.w(TAG, "Heartbeat timeout ($consecutiveFailures/${config.maxRetryCount})")

        if (consecutiveFailures >= config.maxRetryCount) {
            listener?.onHeartbeatFailed("Heartbeat timeout after ${config.maxRetryCount} attempts", consecutiveFailures)
        } else {
            listener?.onHeartbeatTimeout(config.timeoutMs, consecutiveFailures)
        }
    }

    /**
     * Handle heartbeat error
     */
    private fun handleHeartbeatError(error: String) {
        consecutiveFailures++

        // Update statistics
        stats = stats.copy(consecutiveFailures = consecutiveFailures)

        LogUtil.w(TAG, "Heartbeat error ($consecutiveFailures/${config.maxRetryCount}): $error")

        if (consecutiveFailures >= config.maxRetryCount) {
            listener?.onHeartbeatFailed(error, consecutiveFailures)
        } else {
            listener?.onHeartbeatTimeout(config.timeoutMs, consecutiveFailures)
        }
    }

    /**
     * Heartbeat configuration
     */
    data class HeartbeatConfig(
        val intervalMs: Long = 5_000L,      // 5 second interval
        val timeoutMs: Long = 5_000L,      // 5 second timeout per check
        val maxRetryCount: Int = 3,         // 3 consecutive failures, total 15 seconds (3 × 5 seconds)
        val sendTimeoutMs: Long = 3_000L,   // Send timeout duration
        val message: String = "{\"type\":\"ping\",\"timestamp\":${System.currentTimeMillis()}}"
    ) {
        /**
         * Validate if configuration is valid
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
     * Heartbeat statistics
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
         * Get success rate
         *
         * @return Success rate (0.0 - 1.0)
         */
        fun getSuccessRate(): Double {
            return if (totalSent > 0) totalReceived.toDouble() / totalSent.toDouble() else 0.0
        }

        /**
         * Check if heartbeat is healthy
         *
         * @param maxConsecutiveFailures Maximum consecutive failures
         * @return true if healthy, false otherwise
         */
        fun isHealthy(maxConsecutiveFailures: Int = 3): Boolean {
            return consecutiveFailures < maxConsecutiveFailures
        }
    }

    /**
     * Heartbeat listener
     */
    interface HeartbeatListener {
        /**
         * Heartbeat sent successfully
         *
         * @param sentTime Send time
         */
        fun onHeartbeatSent(sentTime: Long)

        /**
         * Heartbeat response received
         *
         * @param responseTime Response time
         * @param roundTripTime Round trip time
         */
        fun onHeartbeatResponse(responseTime: Long, roundTripTime: Long)

        /**
         * Heartbeat response delay warning
         *
         * @param timeoutMs Timeout duration
         * @param ratio Delay ratio (0.0-1.0)
         */
        fun onHeartbeatDelayed(timeoutMs: Long, ratio: Double) {}

        /**
         * Heartbeat timeout
         *
         * @param timeoutMs Timeout duration
         * @param consecutiveFailures Consecutive failure count
         */
        fun onHeartbeatTimeout(timeoutMs: Long, consecutiveFailures: Int)

        /**
         * Heartbeat failed
         *
         * @param error Error message
         * @param consecutiveFailures Consecutive failure count
         */
        fun onHeartbeatFailed(error: String, consecutiveFailures: Int)

        /**
         * Heartbeat configuration updated
         *
         * @param oldConfig Old configuration
         * @param newConfig New configuration
         */
        fun onConfigUpdated(oldConfig: HeartbeatConfig, newConfig: HeartbeatConfig)
    }

    /**
     * Heartbeat sender
     */
    interface HeartbeatSender {
        /**
         * Send heartbeat message
         *
         * @param message Heartbeat message
         * @return true if sent successfully, false otherwise
         */
        suspend fun sendHeartbeat(message: String): Boolean
    }
}