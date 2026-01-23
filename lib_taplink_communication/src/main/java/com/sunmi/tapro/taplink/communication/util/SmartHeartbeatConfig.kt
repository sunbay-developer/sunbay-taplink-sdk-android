//package com.sunmi.tapro.taplink.communication.util
//
//import com.sunmi.tapro.taplink.communication.util.LogUtil
//
///**
// * Smart Heartbeat Configuration Manager
// * Dynamically adjusts heartbeat parameters based on application state
// */
//class SmartHeartbeatConfig private constructor() : AppStateMonitor.AppStateChangeListener {
//
//    companion object {
//        private const val TAG = "SmartHeartbeatConfig"
//
//        @Volatile
//        private var INSTANCE: SmartHeartbeatConfig? = null
//
//        fun getInstance(): SmartHeartbeatConfig {
//            return INSTANCE ?: synchronized(this) {
//                INSTANCE ?: SmartHeartbeatConfig().also { INSTANCE = it }
//            }
//        }
//
//        // Default heartbeat configuration
//        private const val DEFAULT_FOREGROUND_INTERVAL = 30_000L      // Foreground 30 seconds
//        private const val DEFAULT_BACKGROUND_INTERVAL = 60_000L      // Background 60 seconds
//        private const val DEFAULT_LOW_MEMORY_INTERVAL = 120_000L     // Low memory 120 seconds
//        private const val DEFAULT_HEARTBEAT_TIMEOUT = 15_000L        // Timeout 15 seconds
//        private const val DEFAULT_MAX_RETRY_COUNT = 3                // Maximum retry count
//    }
//
//    private var currentConfig = HeartbeatConfig()
//    private val configChangeListeners = mutableSetOf<HeartbeatConfigChangeListener>()
//
//    init {
//        // Register app state listener
//        AppStateMonitor.getInstance().addStateChangeListener(this)
//    }
//
//    /**
//     * Get current heartbeat configuration
//     */
//    fun getCurrentConfig(): HeartbeatConfig = currentConfig.copy()
//
//    /**
//     * Add configuration change listener
//     */
//    fun addConfigChangeListener(listener: HeartbeatConfigChangeListener) {
//        synchronized(configChangeListeners) {
//            configChangeListeners.add(listener)
//        }
//    }
//
//    /**
//     * Remove configuration change listener
//     */
//    fun removeConfigChangeListener(listener: HeartbeatConfigChangeListener) {
//        synchronized(configChangeListeners) {
//            configChangeListeners.remove(listener)
//        }
//    }
//
//    /**
//     * Manually set custom configuration
//     */
//    fun setCustomConfig(
//        foregroundInterval: Long? = null,
//        backgroundInterval: Long? = null,
//        lowMemoryInterval: Long? = null,
//        timeout: Long? = null,
//        maxRetryCount: Int? = null
//    ) {
//        val newConfig = currentConfig.copy(
//            foregroundInterval = foregroundInterval ?: currentConfig.foregroundInterval,
//            backgroundInterval = backgroundInterval ?: currentConfig.backgroundInterval,
//            lowMemoryInterval = lowMemoryInterval ?: currentConfig.lowMemoryInterval,
//            timeout = timeout ?: currentConfig.timeout,
//            maxRetryCount = maxRetryCount ?: currentConfig.maxRetryCount
//        )
//
//        if (newConfig != currentConfig) {
//            currentConfig = newConfig
//            LogUtil.d(TAG, "Custom heartbeat config updated: $currentConfig")
//            notifyConfigChange()
//        }
//    }
//
//    /**
//     * Reset to default configuration
//     */
//    fun resetToDefault() {
//        val newConfig = HeartbeatConfig()
//        if (newConfig != currentConfig) {
//            currentConfig = newConfig
//            LogUtil.d(TAG, "Heartbeat config reset to default: $currentConfig")
//            notifyConfigChange()
//        }
//    }
//
//    /**
//     * Adjust configuration based on network quality
//     */
//    fun adjustForNetworkQuality(networkQuality: NetworkQuality) {
//        val multiplier = when (networkQuality) {
//            NetworkQuality.EXCELLENT -> 1.0
//            NetworkQuality.GOOD -> 1.2
//            NetworkQuality.FAIR -> 1.5
//            NetworkQuality.POOR -> 2.0
//            NetworkQuality.VERY_POOR -> 3.0
//        }
//
//        val newConfig = currentConfig.copy(
//            foregroundInterval = (currentConfig.foregroundInterval * multiplier).toLong(),
//            backgroundInterval = (currentConfig.backgroundInterval * multiplier).toLong(),
//            lowMemoryInterval = (currentConfig.lowMemoryInterval * multiplier).toLong(),
//            timeout = (currentConfig.timeout * multiplier).toLong()
//        )
//
//        if (newConfig != currentConfig) {
//            currentConfig = newConfig
//            LogUtil.d(TAG, "Heartbeat config adjusted for network quality $networkQuality: $currentConfig")
//            notifyConfigChange()
//        }
//    }
//
//    /**
//     * Get current heartbeat interval that should be used
//     */
//    fun getCurrentInterval(): Long {
//        return when (AppStateMonitor.getInstance().getAppState()) {
//            AppStateMonitor.AppState.FOREGROUND -> currentConfig.foregroundInterval
//            AppStateMonitor.AppState.FOREGROUND_LOW_MEMORY -> currentConfig.lowMemoryInterval
//            AppStateMonitor.AppState.BACKGROUND -> currentConfig.backgroundInterval
//            AppStateMonitor.AppState.BACKGROUND_LOW_MEMORY -> currentConfig.lowMemoryInterval
//        }
//    }
//
//    override fun onAppStateChanged(newState: AppStateMonitor.AppState) {
//        LogUtil.d(TAG, "App state changed to: $newState, adjusting heartbeat interval")
//        notifyConfigChange()
//    }
//
//    private fun notifyConfigChange() {
//        synchronized(configChangeListeners) {
//            configChangeListeners.forEach { listener ->
//                try {
//                    listener.onHeartbeatConfigChanged(currentConfig)
//                } catch (e: Exception) {
//                    LogUtil.e(TAG, "Error notifying config change listener: ${e.message}")
//                }
//            }
//        }
//    }
//
//    /**
//     * Heartbeat configuration data class
//     */
//    data class HeartbeatConfig(
//        val foregroundInterval: Long = DEFAULT_FOREGROUND_INTERVAL,
//        val backgroundInterval: Long = DEFAULT_BACKGROUND_INTERVAL,
//        val lowMemoryInterval: Long = DEFAULT_LOW_MEMORY_INTERVAL,
//        val timeout: Long = DEFAULT_HEARTBEAT_TIMEOUT,
//        val maxRetryCount: Int = DEFAULT_MAX_RETRY_COUNT
//    )
//
//    /**
//     * Network quality enum
//     */
//    enum class NetworkQuality {
//        EXCELLENT,  // Excellent network
//        GOOD,       // Good network
//        FAIR,       // Fair network
//        POOR,       // Poor network
//        VERY_POOR   // Very poor network
//    }
//
//    /**
//     * Heartbeat configuration change listener
//     */
//    interface HeartbeatConfigChangeListener {
//        fun onHeartbeatConfigChanged(newConfig: HeartbeatConfig)
//    }
//}
//
//
//
//
//
