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
 * 基于Android NSD的服务发现管理器
 * 
 * 职责：
 * - 使用Android NSD API进行mDNS服务发现
 * - 管理服务监听和变化检测
 * - 维护发现的服务列表
 * 
 * @param context Android上下文
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
    
    // 用于防止重复解析同一个服务
    private val resolvingServices = ConcurrentHashMap<String, AtomicBoolean>()
    
    // 用于同步服务更新操作
    private val serviceUpdateLock = kotlinx.coroutines.sync.Mutex()

    suspend fun discoverServices(serviceType: String, timeoutMs: Long = DISCOVERY_TIMEOUT_MS): List<ServiceInfo> {
        return withContext(Dispatchers.IO) {
            try {
                LogUtil.d(TAG, "Starting service discovery for: $serviceType")
                
                // 清理之前的发现结果
                discoveredServices.clear()
                resolvingServices.clear()
                
                // 开始发现
                val tempListener = createDiscoveryListener()
                nsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, tempListener)
                
                // 等待发现完成
                delay(timeoutMs)
                
                // 停止发现
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
     * 创建NSD发现监听器
     */
    private fun createDiscoveryListener(): NsdManager.DiscoveryListener {
        return object : NsdManager.DiscoveryListener {
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                LogUtil.e(TAG, "Discovery start failed: $serviceType, error: $errorCode")
                serviceChangeListener?.onDiscoveryFailed(serviceType, errorCode, "Start discovery failed")
            }
            
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                LogUtil.e(TAG, "Discovery stop failed: $serviceType, error: $errorCode")
                // 不需要通知，因为停止失败不影响业务
            }
            
            override fun onDiscoveryStarted(serviceType: String) {
                LogUtil.d(TAG, "Discovery started: $serviceType")
                // onDiscoveryStarted 已在 startServiceMonitoring 中调用
            }
            
            override fun onDiscoveryStopped(serviceType: String) {
                LogUtil.d(TAG, "Discovery stopped: $serviceType")
                // onDiscoveryStopped 已在 stopServiceMonitoring 中调用
            }
            
            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                LogUtil.d(TAG, "Service found: ${serviceInfo.serviceName}, host=${serviceInfo.host?.hostAddress}, port=${serviceInfo.port}")
                
                val serviceName = serviceInfo.serviceName
                
                // 检查服务是否已存在，如果存在，可能是重新注册，需要重新解析以检测地址变化
                val existing = discoveredServices[serviceName]
                if (existing != null) {
                    LogUtil.i(TAG, "Service $serviceName already exists, but received onServiceFound again - may be re-registered, allowing re-resolution to detect address changes")
                    // 清理解析状态，允许重新解析（即使当前正在解析中，也允许重新解析以检测地址变化）
                    resolvingServices.remove(serviceName)
                }
                
                // 检查是否已经在解析这个服务，防止重复解析
                val isResolving = resolvingServices.computeIfAbsent(serviceName) { AtomicBoolean(false) }
                
                if (isResolving.compareAndSet(false, true)) {
                    // 成功设置为正在解析状态，开始解析
                    LogUtil.d(TAG, "Starting to resolve service: $serviceName")
                    
                    // 解析服务信息
                    nsdManager.resolveService(serviceInfo, createResolveListener(serviceName) { resolvedInfo ->
                        handleServiceResolved(serviceName, resolvedInfo)
                    })
                } else {
                    // 服务已经在解析中，跳过
                    LogUtil.d(TAG, "Service $serviceName is already being resolved, skipping")
                }
            }
            
            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                LogUtil.d(TAG, "Service lost: ${serviceInfo.serviceName}")
                
                val serviceName = serviceInfo.serviceName
                
                // 清理解析状态
                resolvingServices.remove(serviceName)
                
                val service = discoveredServices.remove(serviceName)
                if (service != null) {
                    serviceChangeListener?.onServiceLost(service)
                }
            }
        }
    }
    
    /**
     * 处理服务解析完成
     */
    private fun handleServiceResolved(serviceName: String, resolvedInfo: NsdServiceInfo) {
        scope.launch {
            try {
                serviceUpdateLock.lock()
                
                val service = convertToServiceInfo(resolvedInfo)
                if (service != null) {
                    val existing = discoveredServices[service.name]
                    
                    if (existing == null) {
                        // 新服务
                        discoveredServices[service.name] = service
                        LogUtil.i(TAG, "New service discovered: ${service.name} at ${service.getAddress()}")
                        serviceChangeListener?.onServiceFound(service)
                    } else {
                        // 服务已存在，检查是否有变化
                        val addressChanged = existing.host != service.host || existing.port != service.port
                        if (addressChanged) {
                            LogUtil.i(
                                TAG,
                                "Service address changed: ${service.name}, ${existing.getAddress()} -> ${service.getAddress()}"
                            )
                            discoveredServices[service.name] = service
                            serviceChangeListener?.onServiceUpdated(existing, service)
                        } else if (existing != service) {
                            // 地址未变化，但其他属性变化了
                            discoveredServices[service.name] = service
                            serviceChangeListener?.onServiceUpdated(existing, service)
                        } else {
                            // 服务信息完全相同，可能是重复发现，记录日志但不触发回调
                            LogUtil.d(TAG, "Service found again with same info: ${service.name} at ${service.getAddress()}")
                        }
                    }
                }
            } catch (e: Exception) {
                LogUtil.e(TAG, "Error handling resolved service $serviceName: ${e.message}")
            } finally {
                serviceUpdateLock.unlock()
                // 清理解析状态
                resolvingServices.remove(serviceName)
            }
        }
    }
    
    /**
     * 创建服务解析监听器
     */
    private fun createResolveListener(serviceName: String, onResolved: (NsdServiceInfo) -> Unit): NsdManager.ResolveListener {
        return object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                LogUtil.w(TAG, "Resolve failed: ${serviceInfo.serviceName}, error: $errorCode")
                // 清理解析状态
                resolvingServices.remove(serviceName)
            }
            
            override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                LogUtil.d(TAG, "Service resolved: ${serviceInfo.serviceName}")
                onResolved(serviceInfo)
            }
        }
    }
    
    /**
     * 转换NsdServiceInfo为ServiceInfo
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
                attributes = emptyMap() // NSD不直接支持TXT记录，可以后续扩展
            )
        } catch (e: Exception) {
            LogUtil.e(TAG, "Failed to convert service info: ${e.message}")
            null
        }
    }
    
    /**
     * 清理资源
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
     * 服务变化监听器
     */
    interface ServiceChangeListener {
        /**
         * 发现新服务
         * 
         * @param service 新发现的服务
         */
        fun onServiceFound(service: ServiceInfo)
        
        /**
         * 服务丢失
         * 
         * @param service 丢失的服务
         */
        fun onServiceLost(service: ServiceInfo)
        
        /**
         * 服务信息更新
         * 
         * @param oldService 旧的服务信息
         * @param newService 新的服务信息
         */
        fun onServiceUpdated(oldService: ServiceInfo, newService: ServiceInfo)
        
        /**
         * 服务发现开始
         * 
         * @param serviceType 服务类型
         */
        fun onDiscoveryStarted(serviceType: String)
        
        /**
         * 服务发现停止
         * 
         * @param serviceType 服务类型
         */
        fun onDiscoveryStopped(serviceType: String)
        
        /**
         * 服务发现失败
         * 
         * @param serviceType 服务类型
         * @param errorCode 错误代码
         * @param error 错误信息
         */
        fun onDiscoveryFailed(serviceType: String, errorCode: Int, error: String)
    }
}