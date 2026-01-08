//package com.sunmi.tapro.taplink.communication.util
//
//import android.content.Context
//import android.net.ConnectivityManager
//import android.net.Network
//import android.net.NetworkCapabilities
//import android.net.NetworkRequest
//import android.os.Build
//import com.sunmi.tapro.taplink.communication.util.LogUtil
//import kotlinx.coroutines.*
//import java.net.InetSocketAddress
//import java.net.Socket
//import kotlin.math.max
//
///**
// * 网络质量监测器
// * 通过延迟测试和连接稳定性评估网络质量
// */
//class NetworkQualityMonitor private constructor(private val context: Context) {
//
//    companion object {
//        private const val TAG = "NetworkQualityMonitor"
//
//        @Volatile
//        private var INSTANCE: NetworkQualityMonitor? = null
//
//        fun getInstance(context: Context): NetworkQualityMonitor {
//            return INSTANCE ?: synchronized(this) {
//                INSTANCE ?: NetworkQualityMonitor(context.applicationContext).also { INSTANCE = it }
//            }
//        }
//
//        // 网络质量评估阈值
//        private const val EXCELLENT_LATENCY_MS = 50
//        private const val GOOD_LATENCY_MS = 100
//        private const val FAIR_LATENCY_MS = 200
//        private const val POOR_LATENCY_MS = 500
//
//        // 测试参数
//        private const val PING_TIMEOUT_MS = 3000
//        private const val PING_TEST_COUNT = 3
//        private const val QUALITY_CHECK_INTERVAL_MS = 30_000L // 30秒检查一次
//    }
//
//    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
//    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
//
//    private var currentQuality = SmartHeartbeatConfig.NetworkQuality.GOOD
//    private var isMonitoring = false
//    private var monitoringJob: Job? = null
//
//    private val qualityChangeListeners = mutableSetOf<NetworkQualityChangeListener>()
//    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
//        override fun onAvailable(network: Network) {
//            LogUtil.d(TAG, "Network available: $network")
//            scheduleQualityCheck()
//        }
//
//        override fun onLost(network: Network) {
//            LogUtil.d(TAG, "Network lost: $network")
//            updateNetworkQuality(SmartHeartbeatConfig.NetworkQuality.VERY_POOR)
//        }
//
//        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
//            LogUtil.d(TAG, "Network capabilities changed")
//            scheduleQualityCheck()
//        }
//    }
//
//    /**
//     * 开始监测网络质量
//     */
//    fun startMonitoring() {
//        if (isMonitoring) return
//
//        isMonitoring = true
//        LogUtil.d(TAG, "Starting network quality monitoring")
//
//        // 注册网络状态监听
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
//            connectivityManager.registerDefaultNetworkCallback(networkCallback)
//        } else {
//            val networkRequest = NetworkRequest.Builder()
//                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
//                .build()
//            connectivityManager.registerNetworkCallback(networkRequest, networkCallback)
//        }
//
//        // 开始定期质量检测
//        startPeriodicQualityCheck()
//
//        // 立即进行一次质量检测
//        scheduleQualityCheck()
//    }
//
//    /**
//     * 停止监测网络质量
//     */
//    fun stopMonitoring() {
//        if (!isMonitoring) return
//
//        isMonitoring = false
//        LogUtil.d(TAG, "Stopping network quality monitoring")
//
//        try {
//            connectivityManager.unregisterNetworkCallback(networkCallback)
//        } catch (e: Exception) {
//            LogUtil.w(TAG, "Error unregistering network callback: ${e.message}")
//        }
//
//        monitoringJob?.cancel()
//        monitoringJob = null
//    }
//
//    /**
//     * 获取当前网络质量
//     */
//    fun getCurrentQuality(): SmartHeartbeatConfig.NetworkQuality = currentQuality
//
//    /**
//     * 添加网络质量变化监听器
//     */
//    fun addQualityChangeListener(listener: NetworkQualityChangeListener) {
//        synchronized(qualityChangeListeners) {
//            qualityChangeListeners.add(listener)
//        }
//    }
//
//    /**
//     * 移除网络质量变化监听器
//     */
//    fun removeQualityChangeListener(listener: NetworkQualityChangeListener) {
//        synchronized(qualityChangeListeners) {
//            qualityChangeListeners.remove(listener)
//        }
//    }
//
//    /**
//     * 手动触发网络质量检测
//     */
//    fun checkQualityNow() {
//        scheduleQualityCheck()
//    }
//
//    private fun startPeriodicQualityCheck() {
//        monitoringJob?.cancel()
//        monitoringJob = scope.launch {
//            while (isActive && isMonitoring) {
//                try {
//                    delay(QUALITY_CHECK_INTERVAL_MS)
//                    if (isMonitoring) {
//                        performQualityCheck()
//                    }
//                } catch (e: CancellationException) {
//                    break
//                } catch (e: Exception) {
//                    LogUtil.e(TAG, "Error in periodic quality check: ${e.message}")
//                }
//            }
//        }
//    }
//
//    private fun scheduleQualityCheck() {
//        scope.launch {
//            performQualityCheck()
//        }
//    }
//
//    private suspend fun performQualityCheck() {
//        try {
//            val latencies = mutableListOf<Long>()
//
//            // 进行多次延迟测试
//            repeat(PING_TEST_COUNT) { attempt ->
//                val latency = measureLatency("8.8.8.8", 53) // Google DNS
//                if (latency > 0) {
//                    latencies.add(latency)
//                }
//
//                if (attempt < PING_TEST_COUNT - 1) {
//                    delay(100) // 测试间隔
//                }
//            }
//
//            if (latencies.isNotEmpty()) {
//                val avgLatency = latencies.average().toLong()
//                val quality = evaluateQuality(avgLatency, latencies)
//
//                LogUtil.d(TAG, "Network quality check: avgLatency=${avgLatency}ms, quality=$quality")
//                updateNetworkQuality(quality)
//            } else {
//                LogUtil.w(TAG, "Failed to measure network latency")
//                updateNetworkQuality(SmartHeartbeatConfig.NetworkQuality.POOR)
//            }
//
//        } catch (e: Exception) {
//            LogUtil.e(TAG, "Error performing quality check: ${e.message}")
//            updateNetworkQuality(SmartHeartbeatConfig.NetworkQuality.FAIR)
//        }
//    }
//
//    private suspend fun measureLatency(host: String, port: Int): Long = withContext(Dispatchers.IO) {
//        return@withContext try {
//            val startTime = System.currentTimeMillis()
//
//            Socket().use { socket ->
//                socket.connect(InetSocketAddress(host, port), PING_TIMEOUT_MS)
//                val endTime = System.currentTimeMillis()
//                endTime - startTime
//            }
//        } catch (e: Exception) {
//            -1L // 表示测试失败
//        }
//    }
//
//    private fun evaluateQuality(avgLatency: Long, latencies: List<Long>): SmartHeartbeatConfig.NetworkQuality {
//        // 计算延迟稳定性（标准差）
//        val variance = latencies.map { (it - avgLatency) * (it - avgLatency) }.average()
//        val stability = kotlin.math.sqrt(variance)
//
//        // 基于平均延迟的基础质量评估
//        val baseQuality = when {
//            avgLatency <= EXCELLENT_LATENCY_MS -> SmartHeartbeatConfig.NetworkQuality.EXCELLENT
//            avgLatency <= GOOD_LATENCY_MS -> SmartHeartbeatConfig.NetworkQuality.GOOD
//            avgLatency <= FAIR_LATENCY_MS -> SmartHeartbeatConfig.NetworkQuality.FAIR
//            avgLatency <= POOR_LATENCY_MS -> SmartHeartbeatConfig.NetworkQuality.POOR
//            else -> SmartHeartbeatConfig.NetworkQuality.VERY_POOR
//        }
//
//        // 如果网络不稳定，降低质量等级
//        return if (stability > avgLatency * 0.5) {
//            when (baseQuality) {
//                SmartHeartbeatConfig.NetworkQuality.EXCELLENT -> SmartHeartbeatConfig.NetworkQuality.GOOD
//                SmartHeartbeatConfig.NetworkQuality.GOOD -> SmartHeartbeatConfig.NetworkQuality.FAIR
//                SmartHeartbeatConfig.NetworkQuality.FAIR -> SmartHeartbeatConfig.NetworkQuality.POOR
//                SmartHeartbeatConfig.NetworkQuality.POOR -> SmartHeartbeatConfig.NetworkQuality.VERY_POOR
//                SmartHeartbeatConfig.NetworkQuality.VERY_POOR -> SmartHeartbeatConfig.NetworkQuality.VERY_POOR
//            }
//        } else {
//            baseQuality
//        }
//    }
//
//    private fun updateNetworkQuality(newQuality: SmartHeartbeatConfig.NetworkQuality) {
//        if (newQuality != currentQuality) {
//            val oldQuality = currentQuality
//            currentQuality = newQuality
//
//            LogUtil.d(TAG, "Network quality changed: $oldQuality -> $newQuality")
//
//            synchronized(qualityChangeListeners) {
//                qualityChangeListeners.forEach { listener ->
//                    try {
//                        listener.onNetworkQualityChanged(oldQuality, newQuality)
//                    } catch (e: Exception) {
//                        LogUtil.e(TAG, "Error notifying quality change listener: ${e.message}")
//                    }
//                }
//            }
//        }
//    }
//
//    /**
//     * 网络质量变化监听器
//     */
//    interface NetworkQualityChangeListener {
//        fun onNetworkQualityChanged(
//            oldQuality: SmartHeartbeatConfig.NetworkQuality,
//            newQuality: SmartHeartbeatConfig.NetworkQuality
//        )
//    }
//}
//
//
//
//
//
