package com.sunmi.tapro.taplink.communication.lan

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import com.sunmi.tapro.taplink.communication.util.LogUtil
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Device discovery manager for mDNS based device discovery
 * 
 * Supports discovering devices by device ID and monitoring service changes
 * 
 * @author TaPro Team
 * @since 2025-12-21
 */
class DeviceDiscoveryManager(private val context: Context) {
    
    private val TAG = "DeviceDiscoveryManager"
    
    companion object {
        private const val NSD_SERVICE_TYPE = "_taplink._tcp"
        private const val DISCOVERY_TIMEOUT = 15_000L // 15 seconds discovery timeout
        private const val MONITORING_INTERVAL = 30_000L // 30 seconds monitoring interval
    }
    
    /**
     * Device service information
     */
    data class DeviceServiceInfo(
        val deviceId: String,
        val serviceName: String,
        val host: String,
        val port: Int,
        val attributes: Map<String, String> = emptyMap(),
        val lastSeen: Long = System.currentTimeMillis()
    )
    
    /**
     * Service change listener
     */
    interface ServiceChangeListener {
        fun onServiceFound(deviceServiceInfo: DeviceServiceInfo)
        fun onServiceLost(deviceId: String)
        fun onServiceChanged(deviceId: String, oldInfo: DeviceServiceInfo, newInfo: DeviceServiceInfo)
    }
    
    private var nsdManager: NsdManager? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private var monitoringJob: Job? = null
    private var serviceChangeListener: ServiceChangeListener? = null
    
    // Discovered services map: deviceId -> DeviceServiceInfo
    private val discoveredServices = ConcurrentHashMap<String, DeviceServiceInfo>()
    
    // Monitoring services map: serviceName -> DeviceServiceInfo
    private val monitoringServices = ConcurrentHashMap<String, DeviceServiceInfo>()
    
    /**
     * Start device discovery by device ID
     * 
     * @param targetDeviceId Target device ID to discover, null to discover all devices
     * @param timeoutMs Discovery timeout in milliseconds
     * @return DeviceServiceInfo if found, null if not found or timeout
     */
    suspend fun discoverDevice(targetDeviceId: String?, timeoutMs: Long = DISCOVERY_TIMEOUT): DeviceServiceInfo? {
        return withContext(Dispatchers.IO) {
            try {
                LogUtil.d(TAG, "Starting device discovery for deviceId: $targetDeviceId")
                
                // Initialize NSD Manager
                nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
                discoveredServices.clear()
                
                val discoveryDeferred = CompletableDeferred<DeviceServiceInfo?>()
                
                // Create discovery listener
                discoveryListener = object : NsdManager.DiscoveryListener {
                    override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                        LogUtil.e(TAG, "Discovery start failed: $serviceType, errorCode: $errorCode")
                        discoveryDeferred.complete(null)
                    }
                    
                    override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                        LogUtil.e(TAG, "Discovery stop failed: $serviceType, errorCode: $errorCode")
                    }
                    
                    override fun onDiscoveryStarted(serviceType: String) {
                        LogUtil.d(TAG, "Discovery started: $serviceType")
                    }
                    
                    override fun onDiscoveryStopped(serviceType: String) {
                        LogUtil.d(TAG, "Discovery stopped: $serviceType")
                    }
                    
                    override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                        LogUtil.d(TAG, "Service found: ${serviceInfo.serviceName}")
                        
                        // Resolve service to get detailed information
                        nsdManager?.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                                LogUtil.e(TAG, "Resolve failed: ${serviceInfo.serviceName}, errorCode: $errorCode")
                            }
                            
                            override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                                LogUtil.d(TAG, "Service resolved: ${serviceInfo.serviceName}")
                                
                                val host = serviceInfo.host?.hostAddress ?: ""
                                val port = serviceInfo.port
                                
                                if (host.isNotEmpty() && port > 0) {
                                    // Extract device ID from service attributes or service name
                                    val deviceId = extractDeviceId(serviceInfo)
                                    
                                    if (deviceId != null) {
                                        val deviceServiceInfo = DeviceServiceInfo(
                                            deviceId = deviceId,
                                            serviceName = serviceInfo.serviceName,
                                            host = host,
                                            port = port,
                                            attributes = extractAttributes(serviceInfo)
                                        )
                                        
                                        discoveredServices[deviceId] = deviceServiceInfo
                                        LogUtil.d(TAG, "Added discovered device: $deviceServiceInfo")
                                        
                                        // Notify service change listener
                                        serviceChangeListener?.onServiceFound(deviceServiceInfo)
                                        
                                        // If this is the target device, complete discovery
                                        if (targetDeviceId == null || deviceId == targetDeviceId) {
                                            if (!discoveryDeferred.isCompleted) {
                                                discoveryDeferred.complete(deviceServiceInfo)
                                            }
                                        }
                                    }
                                }
                            }
                        })
                    }
                    
                    override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                        LogUtil.d(TAG, "Service lost: ${serviceInfo.serviceName}")
                        
                        val deviceId = extractDeviceId(serviceInfo)
                        if (deviceId != null) {
                            discoveredServices.remove(deviceId)
                            serviceChangeListener?.onServiceLost(deviceId)
                        }
                    }
                }
                
                // Start service discovery
                nsdManager?.discoverServices(NSD_SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
                
                // Wait for discovery result or timeout
                val result = withTimeoutOrNull(timeoutMs) {
                    discoveryDeferred.await()
                }
                
                // Stop discovery
                stopDiscovery()
                
                LogUtil.d(TAG, "Discovery completed. Result: $result")
                result
                
            } catch (e: Exception) {
                LogUtil.e(TAG, "Discovery failed: ${e.message}")
                stopDiscovery()
                null
            }
        }
    }
    
    /**
     * Start continuous monitoring for service changes
     * 
     * @param listener Service change listener
     */
    fun startMonitoring(listener: ServiceChangeListener) {
        LogUtil.d(TAG, "Starting service monitoring")
        
        serviceChangeListener = listener
        
        monitoringJob = CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
                try {
                    // Perform periodic discovery to detect changes
                    performMonitoringDiscovery()
                    delay(MONITORING_INTERVAL)
                } catch (e: Exception) {
                    LogUtil.e(TAG, "Monitoring error: ${e.message}")
                    delay(MONITORING_INTERVAL)
                }
            }
        }
    }
    
    /**
     * Stop service monitoring
     */
    fun stopMonitoring() {
        LogUtil.d(TAG, "Stopping service monitoring")
        
        monitoringJob?.cancel()
        monitoringJob = null
        serviceChangeListener = null
        stopDiscovery()
    }
    
    /**
     * Get all discovered services
     * 
     * @return Map of deviceId to DeviceServiceInfo
     */
    fun getDiscoveredServices(): Map<String, DeviceServiceInfo> {
        return discoveredServices.toMap()
    }
    
    /**
     * Get service info for specific device ID
     * 
     * @param deviceId Device ID to look up
     * @return DeviceServiceInfo if found, null otherwise
     */
    fun getServiceInfo(deviceId: String): DeviceServiceInfo? {
        return discoveredServices[deviceId]
    }
    
    /**
     * Perform monitoring discovery to detect service changes
     */
    private suspend fun performMonitoringDiscovery() {
        try {
            LogUtil.d(TAG, "Performing monitoring discovery")
            
            val currentServices = discoveredServices.toMap()
            
            // Discover current services
            discoverDevice(null, 5000L) // Short timeout for monitoring
            
            // Compare with previous services to detect changes
            val newServices = discoveredServices.toMap()
            
            // Check for service changes
            for ((deviceId, newInfo) in newServices) {
                val oldInfo = currentServices[deviceId]
                if (oldInfo != null) {
                    // Check if service info changed (IP/port)
                    if (oldInfo.host != newInfo.host || oldInfo.port != newInfo.port) {
                        LogUtil.i(TAG, "Service changed for device $deviceId: ${oldInfo.host}:${oldInfo.port} -> ${newInfo.host}:${newInfo.port}")
                        serviceChangeListener?.onServiceChanged(deviceId, oldInfo, newInfo)
                    }
                }
            }
            
        } catch (e: Exception) {
            LogUtil.e(TAG, "Monitoring discovery failed: ${e.message}")
        }
    }
    
    /**
     * Stop service discovery
     */
    private fun stopDiscovery() {
        try {
            discoveryListener?.let { listener ->
                nsdManager?.stopServiceDiscovery(listener)
            }
        } catch (e: Exception) {
            LogUtil.e(TAG, "Error stopping discovery: ${e.message}")
        }
        
        discoveryListener = null
        nsdManager = null
    }
    
    /**
     * Extract device ID from NSD service info
     * 
     * Device ID can be extracted from:
     * 1. Service attributes (TXT records)
     * 2. Service name pattern
     * 
     * @param serviceInfo NSD service info
     * @return Device ID if found, null otherwise
     */
    private fun extractDeviceId(serviceInfo: NsdServiceInfo): String? {
        try {
            // Try to get device ID from TXT records (attributes)
            val attributes = serviceInfo.attributes
            if (attributes != null) {
                val deviceIdBytes = attributes["deviceId"] ?: attributes["device_id"]
                if (deviceIdBytes != null) {
                    val deviceId = String(deviceIdBytes, Charsets.UTF_8)
                    LogUtil.d(TAG, "Found device ID in attributes: $deviceId")
                    return deviceId
                }
            }
            
            // Try to extract device ID from service name
            // Expected pattern: "taplink-{deviceId}" or "taplink_{deviceId}"
            val serviceName = serviceInfo.serviceName
            val patterns = listOf(
                Regex("taplink[-_](.+)"),
                Regex("(.+)[-_]taplink"),
                Regex("device[-_](.+)"),
                Regex("(.+)[-_]device")
            )
            
            for (pattern in patterns) {
                val match = pattern.find(serviceName)
                if (match != null) {
                    val deviceId = match.groupValues[1]
                    LogUtil.d(TAG, "Extracted device ID from service name: $deviceId")
                    return deviceId
                }
            }
            
            // If no specific pattern found, use service name as device ID
            LogUtil.d(TAG, "Using service name as device ID: $serviceName")
            return serviceName
            
        } catch (e: Exception) {
            LogUtil.e(TAG, "Failed to extract device ID: ${e.message}")
            return null
        }
    }
    
    /**
     * Extract attributes from NSD service info
     * 
     * @param serviceInfo NSD service info
     * @return Map of attribute key-value pairs
     */
    private fun extractAttributes(serviceInfo: NsdServiceInfo): Map<String, String> {
        return try {
            val attributes = mutableMapOf<String, String>()
            
            serviceInfo.attributes?.let { attrs ->
                for ((key, value) in attrs) {
                    if (value != null) {
                        attributes[key] = String(value, Charsets.UTF_8)
                    }
                }
            }
            
            LogUtil.d(TAG, "Extracted attributes: $attributes")
            attributes
            
        } catch (e: Exception) {
            LogUtil.e(TAG, "Failed to extract attributes: ${e.message}")
            emptyMap()
        }
    }
}