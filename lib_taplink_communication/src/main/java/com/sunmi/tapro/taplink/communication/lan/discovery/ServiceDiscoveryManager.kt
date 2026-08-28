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
 * @since 2025-01-01
 */
class ServiceDiscoveryManager(
    private val context: Context
) {
    
    companion object {
        private const val TAG = "ServiceDiscoveryManager"
        private const val DISCOVERY_TIMEOUT_MS = 30_000L

        /** Upper bound on a single NsdManager resolve before its slot is force-released. */
        private const val RESOLVE_TIMEOUT_MS = 10_000L
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

    /**
     * Optional filter deciding whether a discovered service name is worth resolving.
     *
     * Android's NsdManager only tolerates a single in-flight resolveService() call; issuing
     * concurrent resolves for unrelated devices makes every extra call fail with
     * FAILURE_ALREADY_ACTIVE (error 3). In a shop there are typically several Tapro terminals
     * advertising on the same subnet, so resolving all of them both floods the logs and starves
     * the resolve we actually care about. Returning false here skips the resolve entirely.
     */
    @Volatile
    private var serviceNameFilter: ((String) -> Boolean)? = null

    /**
     * Serializes resolveService() calls, since NsdManager rejects concurrent resolutions
     * with FAILURE_ALREADY_ACTIVE (error 3).
     */
    private val resolveQueue = ArrayDeque<Pair<Long, (Long) -> Unit>>()
    private val resolveQueueLock = Any()
    private var resolveInFlight = false

    /** Identifies the resolve currently occupying the single NSD resolve slot. */
    private var inFlightResolveToken = 0L
    private var nextResolveToken = 0L
    private var resolveWatchdog: Job? = null

    /**
     * Install a filter restricting which discovered services get resolved.
     *
     * @param filter Receives the mDNS service name, returns true to resolve it. Pass null to
     *               resolve every discovered service.
     */
    fun setServiceNameFilter(filter: ((String) -> Boolean)?) {
        serviceNameFilter = filter
        LogUtil.d(TAG, "Service name filter ${if (filter != null) "set" else "cleared"}")
    }

    private fun shouldResolve(serviceName: String): Boolean {
        val filter = serviceNameFilter ?: return true
        return try {
            filter(serviceName)
        } catch (e: Exception) {
            LogUtil.w(TAG, "Service name filter threw for $serviceName, resolving anyway: ${e.message}")
            true
        }
    }

    /**
     * Queue a resolve request, running it only once any previous resolve has settled.
     */
    private fun enqueueResolve(serviceName: String, resolve: (Long) -> Unit) {
        synchronized(resolveQueueLock) {
            resolveQueue.addLast(nextResolveToken++ to resolve)
            if (resolveInFlight) {
                LogUtil.d(TAG, "Resolve already in flight, queued: $serviceName (depth=${resolveQueue.size})")
                return
            }
        }
        pumpResolveQueue()
    }

    /**
     * Release the resolve slot and start the next queued resolve, if any.
     *
     * @param token Token of the resolve that finished; ignored when it no longer owns the slot,
     *              which keeps a late callback from evicting a newer resolve.
     */
    private fun onResolveSettled(token: Long) {
        synchronized(resolveQueueLock) {
            if (!resolveInFlight || token != inFlightResolveToken) {
                return
            }
            resolveInFlight = false
            resolveWatchdog?.cancel()
            resolveWatchdog = null
        }
        pumpResolveQueue()
    }

    private fun pumpResolveQueue() {
        val token: Long
        val next: (Long) -> Unit
        synchronized(resolveQueueLock) {
            if (resolveInFlight) return
            val (queuedToken, task) = resolveQueue.removeFirstOrNull() ?: return
            resolveInFlight = true
            inFlightResolveToken = queuedToken
            token = queuedToken
            next = task

            // NsdManager occasionally drops a resolve on the floor without invoking either
            // callback. Without this watchdog the single resolve slot would stay occupied and
            // service discovery would stop permanently.
            resolveWatchdog?.cancel()
            resolveWatchdog = scope.launch {
                delay(RESOLVE_TIMEOUT_MS)
                LogUtil.w(TAG, "Resolve timed out, releasing resolve slot")
                onResolveSettled(token)
            }
        }
        try {
            next.invoke(token)
        } catch (e: Exception) {
            LogUtil.e(TAG, "Failed to start queued resolve: ${e.message}")
            onResolveSettled(token)
        }
    }

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
    
    /**
     * Isolated one-shot service discovery with independent result set.
     * Does NOT share state with startServiceMonitoring/stopServiceMonitoring.
     * Supports coroutine cancellation; NSD listener is always cleaned up in finally.
     *
     * @param serviceType mDNS service type (e.g. "_taplink._tcp")
     * @param timeoutMs Discovery window in milliseconds
     * @return List of discovered services (may be empty)
     */
    suspend fun discoverServicesIsolated(
        serviceType: String,
        timeoutMs: Long = DISCOVERY_TIMEOUT_MS
    ): List<ServiceInfo> {
        return withContext(Dispatchers.IO) {
            val isolatedResults = ConcurrentHashMap<String, ServiceInfo>()
            val isolatedResolving = ConcurrentHashMap<String, AtomicBoolean>()
            var tempListener: NsdManager.DiscoveryListener? = null

            try {
                LogUtil.d(TAG, "Starting isolated service discovery for: $serviceType (timeout=${timeoutMs}ms)")

                tempListener = object : NsdManager.DiscoveryListener {
                    override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                        LogUtil.e(TAG, "Isolated discovery start failed: $serviceType, error: $errorCode")
                    }

                    override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                        LogUtil.w(TAG, "Isolated discovery stop failed: $serviceType, error: $errorCode")
                    }

                    override fun onDiscoveryStarted(serviceType: String) {
                        LogUtil.d(TAG, "Isolated discovery started: $serviceType")
                    }

                    override fun onDiscoveryStopped(serviceType: String) {
                        LogUtil.d(TAG, "Isolated discovery stopped: $serviceType")
                    }

                    override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                        val serviceName = serviceInfo.serviceName
                        LogUtil.d(TAG, "Isolated: service found: $serviceName")

                        val isResolving = isolatedResolving.computeIfAbsent(serviceName) { AtomicBoolean(false) }
                        if (isResolving.compareAndSet(false, true)) {
                            nsdManager.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                                override fun onResolveFailed(si: NsdServiceInfo, errorCode: Int) {
                                    LogUtil.w(TAG, "Isolated: resolve failed: ${si.serviceName}, error: $errorCode")
                                    isolatedResolving.remove(serviceName)
                                }

                                override fun onServiceResolved(si: NsdServiceInfo) {
                                    val service = convertToServiceInfo(si)
                                    if (service != null) {
                                        isolatedResults[service.name] = service
                                        LogUtil.d(TAG, "Isolated: resolved ${service.name} at ${service.getAddress()}")
                                    }
                                    isolatedResolving.remove(serviceName)
                                }
                            })
                        }
                    }

                    override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                        LogUtil.d(TAG, "Isolated: service lost: ${serviceInfo.serviceName}")
                        isolatedResults.remove(serviceInfo.serviceName)
                        isolatedResolving.remove(serviceInfo.serviceName)
                    }
                }

                nsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, tempListener)

                // Wait for discovery window, checking for cancellation
                delay(timeoutMs)

                val results = isolatedResults.values.toList()
                LogUtil.d(TAG, "Isolated discovery completed, found ${results.size} services")
                results

            } catch (e: CancellationException) {
                LogUtil.d(TAG, "Isolated discovery cancelled")
                throw e
            } catch (e: Exception) {
                LogUtil.e(TAG, "Isolated discovery failed: ${e.message}")
                emptyList()
            } finally {
                // Always stop discovery listener
                tempListener?.let { listener ->
                    try {
                        nsdManager.stopServiceDiscovery(listener)
                    } catch (e: Exception) {
                        LogUtil.w(TAG, "Error stopping isolated discovery: ${e.message}")
                    }
                }
                isolatedResults.clear()
                isolatedResolving.clear()
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

                // Skip services belonging to other Tapro terminals: resolving them would
                // contend for NsdManager's single resolve slot and fail with error 3.
                if (!shouldResolve(serviceName)) {
                    LogUtil.d(TAG, "Skipping resolve for non-target service: $serviceName")
                    return
                }
                
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
                    // Successfully set to resolving state, queue resolution. NsdManager only
                    // supports one in-flight resolve, so requests are serialized.
                    LogUtil.d(TAG, "Queueing resolve for service: $serviceName")

                    enqueueResolve(serviceName) { token ->
                        nsdManager.resolveService(serviceInfo, createResolveListener(serviceName, token) { resolvedInfo ->
                            handleServiceResolved(serviceName, resolvedInfo)
                        })
                    }
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
    private fun createResolveListener(
        serviceName: String,
        resolveToken: Long,
        onResolved: (NsdServiceInfo) -> Unit
    ): NsdManager.ResolveListener {
        return object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                LogUtil.w(TAG, "Resolve failed: ${serviceInfo.serviceName}, error: $errorCode")
                // Clear resolution status
                resolvingServices.remove(serviceName)
                // Release the resolve slot so queued resolves can proceed
                onResolveSettled(resolveToken)
            }
            
            override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                LogUtil.d(TAG, "Service resolved: ${serviceInfo.serviceName}")
                // Release the resolve slot so queued resolves can proceed
                onResolveSettled(resolveToken)
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
        synchronized(resolveQueueLock) {
            resolveQueue.clear()
            resolveInFlight = false
            resolveWatchdog?.cancel()
            resolveWatchdog = null
        }
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