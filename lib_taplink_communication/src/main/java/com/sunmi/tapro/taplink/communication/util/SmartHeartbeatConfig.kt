//package com.sunmi.tapro.taplink.communication.util
//
//import com.sunmi.tapro.taplink.communication.util.LogUtil
//
///**
// * 智能心跳配置管理器
// * 根据应用状态动态调整心跳参数
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
//        // 默认心跳配置
//        private const val DEFAULT_FOREGROUND_INTERVAL = 30_000L      // 前台30秒
//        private const val DEFAULT_BACKGROUND_INTERVAL = 60_000L      // 后台60秒
//        private const val DEFAULT_LOW_MEMORY_INTERVAL = 120_000L     // 低内存120秒
//        private const val DEFAULT_HEARTBEAT_TIMEOUT = 15_000L        // 超时15秒
//        private const val DEFAULT_MAX_RETRY_COUNT = 3                // 最大重试次数
//    }
//
//    private var currentConfig = HeartbeatConfig()
//    private val configChangeListeners = mutableSetOf<HeartbeatConfigChangeListener>()
//
//    init {
//        // 注册应用状态监听
//        AppStateMonitor.getInstance().addStateChangeListener(this)
//    }
//
//    /**
//     * 获取当前心跳配置
//     */
//    fun getCurrentConfig(): HeartbeatConfig = currentConfig.copy()
//
//    /**
//     * 添加配置变化监听器
//     */
//    fun addConfigChangeListener(listener: HeartbeatConfigChangeListener) {
//        synchronized(configChangeListeners) {
//            configChangeListeners.add(listener)
//        }
//    }
//
//    /**
//     * 移除配置变化监听器
//     */
//    fun removeConfigChangeListener(listener: HeartbeatConfigChangeListener) {
//        synchronized(configChangeListeners) {
//            configChangeListeners.remove(listener)
//        }
//    }
//
//    /**
//     * 手动设置自定义配置
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
//     * 重置为默认配置
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
//     * 根据网络质量调整配置
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
//     * 获取当前应该使用的心跳间隔
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
//     * 心跳配置数据类
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
//     * 网络质量枚举
//     */
//    enum class NetworkQuality {
//        EXCELLENT,  // 网络极好
//        GOOD,       // 网络良好
//        FAIR,       // 网络一般
//        POOR,       // 网络较差
//        VERY_POOR   // 网络很差
//    }
//
//    /**
//     * 心跳配置变化监听器
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
