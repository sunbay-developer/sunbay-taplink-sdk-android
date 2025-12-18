package com.sunmi.tapro.taplink.demo.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import kotlinx.coroutines.*
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Connection status monitor for cross-device connection modes
 * 
 * Provides network quality monitoring and connection health checks:
 * - Network connectivity monitoring for LAN mode
 * - Connection quality assessment
 * - Automatic reconnection suggestions
 * - Real-time status updates
 */
class ConnectionStatusMonitor(private val context: Context) {
    
    companion object {
        private const val TAG = "ConnectionStatusMonitor"
        private const val PING_TIMEOUT_MS = 3000
        private const val QUALITY_CHECK_INTERVAL_MS = 10000L
    }
    
    private var isMonitoring = false
    private var monitoringJob: Job? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var statusListener: ConnectionStatusListener? = null
    
    /**
     * Interface for connection status updates
     */
    interface ConnectionStatusListener {
        fun onNetworkAvailable(isWifi: Boolean)
        fun onNetworkLost()
        fun onConnectionQualityChanged(quality: NetworkQuality)
        fun onLanDeviceReachable(ip: String, responseTime: Long)
        fun onLanDeviceUnreachable(ip: String)
    }
    
    /**
     * Network quality levels
     */
    enum class NetworkQuality {
        EXCELLENT,  // < 50ms, stable connection
        GOOD,       // 50-150ms, good connection
        FAIR,       // 150-300ms, acceptable connection
        POOR,       // 300-1000ms, poor connection
        UNAVAILABLE // > 1000ms or no connection
    }
    
    /**
     * Start monitoring connection status
     */
    fun startMonitoring(listener: ConnectionStatusListener) {
        if (isMonitoring) {
            Log.w(TAG, "Already monitoring connection status")
            return
        }
        
        this.statusListener = listener
        isMonitoring = true
        
        Log.d(TAG, "Starting connection status monitoring")
        
        // Register network callback for connectivity changes
        registerNetworkCallback()
        
        // Start periodic quality checks
        startQualityMonitoring()
    }
    
    /**
     * Stop monitoring connection status
     */
    fun stopMonitoring() {
        if (!isMonitoring) {
            return
        }
        
        Log.d(TAG, "Stopping connection status monitoring")
        
        isMonitoring = false
        statusListener = null
        
        // Cancel monitoring job
        monitoringJob?.cancel()
        monitoringJob = null
        
        // Unregister network callback
        unregisterNetworkCallback()
    }
    
    /**
     * Check LAN device reachability
     */
    suspend fun checkLanDeviceReachability(ip: String, port: Int = 8443): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val startTime = System.currentTimeMillis()
                
                // Try to connect to the device
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(ip, port), PING_TIMEOUT_MS)
                    val responseTime = System.currentTimeMillis() - startTime
                    
                    Log.d(TAG, "LAN device reachable - IP: $ip, Response time: ${responseTime}ms")
                    statusListener?.onLanDeviceReachable(ip, responseTime)
                    
                    return@withContext true
                }
            } catch (e: Exception) {
                Log.w(TAG, "LAN device unreachable - IP: $ip, Error: ${e.message}")
                statusListener?.onLanDeviceUnreachable(ip)
                return@withContext false
            }
        }
    }
    
    /**
     * Get current network quality
     */
    suspend fun getCurrentNetworkQuality(): NetworkQuality {
        return withContext(Dispatchers.IO) {
            try {
                val startTime = System.currentTimeMillis()
                
                // Ping a reliable host (Google DNS)
                val address = InetAddress.getByName("8.8.8.8")
                val reachable = address.isReachable(PING_TIMEOUT_MS)
                
                if (!reachable) {
                    return@withContext NetworkQuality.UNAVAILABLE
                }
                
                val responseTime = System.currentTimeMillis() - startTime
                
                val quality = when {
                    responseTime < 50 -> NetworkQuality.EXCELLENT
                    responseTime < 150 -> NetworkQuality.GOOD
                    responseTime < 300 -> NetworkQuality.FAIR
                    responseTime < 1000 -> NetworkQuality.POOR
                    else -> NetworkQuality.UNAVAILABLE
                }
                
                Log.d(TAG, "Network quality check - Response time: ${responseTime}ms, Quality: $quality")
                return@withContext quality
                
            } catch (e: Exception) {
                Log.w(TAG, "Network quality check failed: ${e.message}")
                return@withContext NetworkQuality.UNAVAILABLE
            }
        }
    }
    
    /**
     * Check if device is connected to WiFi
     */
    fun isWifiConnected(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }
    
    /**
     * Get network quality description
     */
    fun getNetworkQualityDescription(quality: NetworkQuality): String {
        return when (quality) {
            NetworkQuality.EXCELLENT -> "Excellent connection quality"
            NetworkQuality.GOOD -> "Good connection quality"
            NetworkQuality.FAIR -> "Fair connection quality - may affect performance"
            NetworkQuality.POOR -> "Poor connection quality - expect delays"
            NetworkQuality.UNAVAILABLE -> "No network connection available"
        }
    }
    
    /**
     * Register network connectivity callback
     */
    private fun registerNetworkCallback() {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        
        val networkRequest = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        
        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                super.onAvailable(network)
                Log.d(TAG, "Network available")
                
                val isWifi = isWifiConnected()
                statusListener?.onNetworkAvailable(isWifi)
            }
            
            override fun onLost(network: Network) {
                super.onLost(network)
                Log.d(TAG, "Network lost")
                statusListener?.onNetworkLost()
            }
        }
        
        connectivityManager.registerNetworkCallback(networkRequest, networkCallback!!)
    }
    
    /**
     * Unregister network connectivity callback
     */
    private fun unregisterNetworkCallback() {
        networkCallback?.let { callback ->
            try {
                val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                connectivityManager.unregisterNetworkCallback(callback)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to unregister network callback: ${e.message}")
            }
        }
        networkCallback = null
    }
    
    /**
     * Start periodic quality monitoring
     */
    private fun startQualityMonitoring() {
        monitoringJob = CoroutineScope(Dispatchers.IO).launch {
            while (isMonitoring) {
                try {
                    val quality = getCurrentNetworkQuality()
                    statusListener?.onConnectionQualityChanged(quality)
                    
                    delay(QUALITY_CHECK_INTERVAL_MS)
                } catch (e: Exception) {
                    Log.e(TAG, "Quality monitoring error: ${e.message}")
                    delay(QUALITY_CHECK_INTERVAL_MS)
                }
            }
        }
    }
}