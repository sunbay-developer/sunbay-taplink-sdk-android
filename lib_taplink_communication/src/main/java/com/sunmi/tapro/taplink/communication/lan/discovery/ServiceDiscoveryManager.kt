package com.sunmi.tapro.taplink.communication.lan.discovery

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import com.sunmi.tapro.taplink.communication.lan.model.ServiceInfo
import com.sunmi.tapro.taplink.communication.util.LogUtil
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Android NSD-based service discovery manager
 * 
 * Responsibilities:
 * - Use Android NSD API for mDNS service discovery
 * - Manage service monitoring and change detection
 * - Maintain discovered service list
 * 
 * @param context Android context
 * 
 * @author TaPro Team
 * @since 2025-01-XX
 */
class ServiceDiscoveryManager(
    private val context: Context
) {
    
    companion object {
        private const val TAG = "ServiceDiscoveryManager"
        private const val DISCOVERY_TIMEOUT_MS = 30_000L
    }
    
    private val nsdManager: NsdManager by lazy {
        context.getSystemService(Context.NSD_SERVICE) as NsdManager
    }
    
    private var isMonitoring = false
    private var currentServiceType: String? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private var serviceChangeListener: ServiceChangeListener? = null
    
    private val discoveredServices = ConcurrentHashMap<String, ServiceInfo>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    // Used to prevent duplicate resolution of the same service
    private val resolvingServices = ConcurrentHashMap<String, AtomicBoolean>()
    
    // Used to synchronize service update operations
    private val serviceUpdateLock = kotlinx.coroutines.sync.Mutex()

    suspend fun discoverServices(serviceType: String, timeoutMs: Long = DISCOVERY_TIMEOUT_MS): List<ServiceInfo> {
        return withContext(Dispatchers.IO) {
            try {
                LogUtil.d(TAG, "Starting service discovery for: $serviceType")
                
                // Clear previous discovery results
                discoveredServices.clear()
                resolvingServices.clear()
                
                // Start discovery
                val tempListener = createDiscoveryListener()
                nsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, tempListener)
                
                // Wait for discovery to complete
                delay(timeoutMs)
                
                // Stop discovery
                try {
                    nsdManager.stopServiceDiscovery(tempListener)
                } catch (e: Exception) {
                    LogUtil.w(TAG, "Error stopping service discovery: ${e.message}")
                }
                
                val services = discoveredServices.values.toList()
                LogUtil.d(TAG, "Discovery completed, found ${services.size} services")
                
                services
                
            } catch (e: Exception) {
                LogUtil.e(TAG, "Service discovery failed: ${e.message}")
                emptyList()
            }
        }
    }
    
    fun startServiceMonitoring(serviceType: String, listener: ServiceChangeListener) {
        if (isMonitoring) {
            LogUtil.w(TAG, "Service monitoring already started")
            stopServiceMonitoring()
        }
        
        try {
            LogUtil.d(TAG, "Starting service monitoring for: $serviceType")
            
            this.currentServiceType = serviceType
            this.serviceChangeListener = listener
            this.discoveryListener = createDiscoveryListener()
            
            nsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, discoveryListener!!)
            isMonitoring = true
            
            listener.onDiscoveryStarted(serviceType)
            
        } catch (e: Exception) {
            LogUtil.e(TAG, "Failed to start service monitoring: ${e.message}")
            listener.onDiscoveryFailed(serviceType, -1, e.message ?: "Unknown error")
        }
    }
    
    fun stopServiceMonitoring() {
        if (!isMonitoring) return
        
        try {
            LogUtil.d(TAG, "Stopping service monitoring")
            
            discoveryListener?.let { listener ->
                nsdManager.stopServiceDiscovery(listener)
            }
            
            val serviceType = currentServiceType
            if (serviceType != null) {
                serviceChangeListener?.onDiscoveryStopped(serviceType)
            }
            
            cleanup()
            
        } catch (e: Exception) {
            LogUtil.e(TAG, "Error stopping service monitoring: ${e.message}")
        }
    }
    
    fun isMonitoring(): Boolean = isMonitoring
    
    fun getDiscoveredServices(): List<ServiceInfo> {
        return discoveredServices.values.toList()
    }
    
    /**
     * Create NSD discovery listener
     */
    private fun createDiscoveryListener(): NsdManager.DiscoveryListener {
        return object : NsdManager.DiscoveryListener {
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                LogUtil.e(TAG, "Discovery start failed: $serviceType, error: $errorCode")
                serviceChangeListener?.onDiscoveryFailed(serviceType, errorCode, "Start discovery failed")
            }
            
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                LogUtil.e(TAG, "Discovery stop failed: $serviceType, error: $errorCode")
                // No need to notify, because stop failure doesn't affect business
            }
            
            override fun onDiscoveryStarted(serviceType: String) {
                LogUtil.d(TAG, "Discovery started: $serviceType")
                // onDiscoveryStarted already called in startServiceMonitoring
            }
            
            override fun onDiscoveryStopped(serviceType: String) {
                LogUtil.d(TAG, "Discovery stopped: $serviceType")
                // onDiscoveryStopped already called in stopServiceMonitoring
            }
            
            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                LogUtil.d(TAG, "Service found: ${serviceInfo.serviceName}, host=${serviceInfo.host?.hostAddress}, port=${serviceInfo.port}")
                
                val serviceName = serviceInfo.serviceName
                
                // Check if service already exists, if exists, may be re-registered, need to re-resolve to detect address changes
                val existing = discoveredServices[serviceName]
                if (existing != null) {
                    LogUtil.i(TAG, "Service $serviceName already exists, but received onServiceFound again - may be re-registered, allowing re-resolution to detect address changes")
                    // Clear resolution status, allow re-resolution (even if currently resolving, allow re-resolution to detect address changes)
                    resolvingServices.remove(serviceName)
                }
                
                // Check if already resolving this service, prevent duplicate resolution
                val isResolving = resolvingServices.computeIfAbsent(serviceName) { AtomicBoolean(false) }
                
                if (isResolving.compareAndSet(false, true)) {
                    // Successfully set to resolving state, start resolution
                    LogUtil.d(TAG, "Starting to resolve service: $serviceName")
                    
                    // Resolve service information
                    nsdManager.resolveService(serviceInfo, createResolveListener(serviceName) { resolvedInfo ->
                        handleServiceResolved(serviceName, resolvedInfo)
                    })
                } else {
                    // Service already being resolved, skip
                    LogUtil.d(TAG, "Service $serviceName is already being resolved, skipping")
                }
            }
            
            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                LogUtil.d(TAG, "Service lost: ${serviceInfo.serviceName}")
                
                val serviceName = serviceInfo.serviceName
                
                // Clear resolution status
                resolvingServices.remove(serviceName)
                
                val service = discoveredServices.remove(serviceName)
                if (service != null) {
                    serviceChangeListener?.onServiceLost(service)
                }
            }
        }
    }
    
    /**
     * Handle service resolution completion
     */
    private fun handleServiceResolved(serviceName: String, resolvedInfo: NsdServiceInfo) {
        scope.launch {
            try {
                serviceUpdateLock.lock()
                
                val service = convertToServiceInfo(resolvedInfo)
                if (service != null) {
                    val existing = discoveredServices[service.name]
                    
                    if (existing == null) {
                        // New service
                        discoveredServices[service.name] = service
                        LogUtil.i(TAG, "New service discovered: ${service.name} at ${service.getAddress()}")
                        serviceChangeListener?.onServiceFound(service)
                    } else {
                        // Service already exists, check if there are changes
                        val addressChanged = existing.host != service.host || existing.port != service.port
                        if (addressChanged) {
                            LogUtil.i(
                                TAG,
                                "Service address changed: ${service.name}, ${existing.getAddress()} -> ${service.getAddress()}"
                            )
                            discoveredServices[service.name] = service
                            serviceChangeListener?.onServiceUpdated(existing, service)
                        } else if (existing != service) {
                            // Address unchanged, but other attributes changed
                            discoveredServices[service.name] = service
                            serviceChangeListener?.onServiceUpdated(existing, service)
                        } else {
                            // Service info completely identical, may be duplicate discovery, log but don't trigger callback
                            LogUtil.d(TAG, "Service found again with same info: ${service.name} at ${service.getAddress()}")
                        }
                    }
                }
            } catch (e: Exception) {
                LogUtil.e(TAG, "Error handling resolved service $serviceName: ${e.message}")
            } finally {
                serviceUpdateLock.unlock()
                // Clear resolution status
                resolvingServices.remove(serviceName)
            }
        }
    }
    
    /**
     * Create service resolution listener
     */
    private fun createResolveListener(serviceName: String, onResolved: (NsdServiceInfo) -> Unit): NsdManager.ResolveListener {
        return object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                LogUtil.w(TAG, "Resolve failed: ${serviceInfo.serviceName}, error: $errorCode")
                // Clear resolution status
                resolvingServices.remove(serviceName)
            }
            
            override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                LogUtil.d(TAG, "Service resolved: ${serviceInfo.serviceName}")
                onResolved(serviceInfo)
            }
        }
    }
    
    /**
     * Convert NsdServiceInfo to ServiceInfo
     */
    private fun convertToServiceInfo(nsdInfo: NsdServiceInfo): ServiceInfo? {
        return try {
            val host = nsdInfo.host?.hostAddress ?: return null
            val port = nsdInfo.port
            val name = nsdInfo.serviceName
            val type = nsdInfo.serviceType
            
            ServiceInfo(
                name = name,
                type = type,
                host = host,
                port = port,
                attributes = emptyMap() // NSD doesn't directly support TXT records, can be extended later
            )
        } catch (e: Exception) {
            LogUtil.e(TAG, "Failed to convert service info: ${e.message}")
            null
        }
    }
    
    /**
     * Clean up resources
     */
    private fun cleanup() {
        isMonitoring = false
        currentServiceType = null
        discoveryListener = null
        serviceChangeListener = null
        discoveredServices.clear()
        resolvingServices.clear()
    }
    
    /**
     * Service change listener
     */
    interface ServiceChangeListener {
        /**
         * New service discovered
         * 
         * @param service Newly discovered service
         */
        fun onServiceFound(service: ServiceInfo)
        
        /**
         * Service lost
         * 
         * @param service Lost service
         */
        fun onServiceLost(service: ServiceInfo)
        
        /**
         * Service information updated
         * 
         * @param oldService Old service information
         * @param newService New service information
         */
        fun onServiceUpdated(oldService: ServiceInfo, newService: ServiceInfo)
        
        /**
         * Service discovery started
         * 
         * @param serviceType Service type
         */
        fun onDiscoveryStarted(serviceType: String)
        
        /**
         * Service discovery stopped
         * 
         * @param serviceType Service type
         */
        fun onDiscoveryStopped(serviceType: String)
        
        /**
         * Service discovery failed
         * 
         * @param serviceType Service type
         * @param errorCode Error code
         * @param error Error message
         */
        fun onDiscoveryFailed(serviceType: String, errorCode: Int, error: String)
    }
}