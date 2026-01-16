# LanClientKernel Race Condition Fix

## 修复方案概述

基于对 LanClientKernel 竞态条件问题的分析，提出以下修复方案：

1. **操作互斥机制**: 确保同时只有一个连接操作在进行
2. **回调队列管理**: 支持多个回调，避免回调替换问题
3. **状态同步保护**: 使用互斥锁保护状态更新
4. **操作标识机制**: 为每个操作分配唯一标识，避免操作混乱

## 详细修复实现

### 1. 添加并发控制机制

```kotlin
class LanClientKernel(
    appId: String,
    appSecretKey: String,
    private val context: Context
) : AsyncServiceKernel(appId, appSecretKey) {

    // ==================== 并发控制 ====================
    
    /**
     * 操作互斥锁，确保同时只有一个连接操作
     */
    private val operationMutex = Mutex()
    
    /**
     * 状态更新互斥锁，保护状态更新操作
     */
    private val statusMutex = Mutex()
    
    /**
     * 当前正在执行的操作类型
     */
    private var currentOperation: String? = null
    
    /**
     * 操作标识生成器
     */
    private val operationIdGenerator = AtomicLong(0)
    
    /**
     * 当前操作ID
     */
    private var currentOperationId: String? = null
    
    /**
     * 回调管理器
     */
    private val callbackManager = ConnectionCallbackManager()
    
    // ==================== 回调管理器 ====================
    
    /**
     * 连接回调管理器
     * 支持多个回调，避免回调替换问题
     */
    private class ConnectionCallbackManager {
        private val callbacks = Collections.synchronizedList(mutableListOf<CallbackEntry>())
        
        data class CallbackEntry(
            val operationId: String,
            val operationType: String,
            val callback: ConnectionCallback,
            val timestamp: Long = System.currentTimeMillis()
        )
        
        fun addCallback(operationId: String, operationType: String, callback: ConnectionCallback) {
            synchronized(callbacks) {
                callbacks.add(CallbackEntry(operationId, operationType, callback))
                LogUtil.d(TAG, "Added callback for operation: $operationType ($operationId)")
            }
        }
        
        fun removeCallback(operationId: String) {
            synchronized(callbacks) {
                val removed = callbacks.removeAll { it.operationId == operationId }
                if (removed) {
                    LogUtil.d(TAG, "Removed callback for operation: $operationId")
                }
            }
        }
        
        fun notifyConnected(data: Map<String, Any?>) {
            synchronized(callbacks) {
                LogUtil.d(TAG, "Notifying ${callbacks.size} callbacks of connection success")
                callbacks.forEach { entry ->
                    try {
                        entry.callback.onConnected(data)
                        LogUtil.d(TAG, "Notified ${entry.operationType} callback of success")
                    } catch (e: Exception) {
                        LogUtil.e(TAG, "Error notifying callback: ${e.message}")
                    }
                }
                callbacks.clear()
            }
        }
        
        fun notifyError(code: Int, message: String) {
            synchronized(callbacks) {
                LogUtil.d(TAG, "Notifying ${callbacks.size} callbacks of connection error")
                callbacks.forEach { entry ->
                    try {
                        entry.callback.onError(code, message)
                        LogUtil.d(TAG, "Notified ${entry.operationType} callback of error")
                    } catch (e: Exception) {
                        LogUtil.e(TAG, "Error notifying callback: ${e.message}")
                    }
                }
                callbacks.clear()
            }
        }
        
        fun notifyServiceAddressChanged(
            serviceName: String, newHost: String, newPort: Int,
            oldHost: String, oldPort: Int
        ): Boolean {
            synchronized(callbacks) {
                var shouldReconnect = false
                callbacks.forEach { entry ->
                    try {
                        val result = entry.callback.onServiceAddressChanged(
                            serviceName, newHost, newPort, oldHost, oldPort
                        )
                        if (result) {
                            shouldReconnect = true
                        }
                    } catch (e: Exception) {
                        LogUtil.e(TAG, "Error notifying service address change: ${e.message}")
                    }
                }
                return shouldReconnect
            }
        }
        
        fun hasCallbacks(): Boolean {
            synchronized(callbacks) {
                return callbacks.isNotEmpty()
            }
        }
        
        fun getCallbackCount(): Int {
            synchronized(callbacks) {
                return callbacks.size
            }
        }
        
        fun clearExpiredCallbacks(timeoutMs: Long = 30000) {
            synchronized(callbacks) {
                val currentTime = System.currentTimeMillis()
                val removed = callbacks.removeAll { currentTime - it.timestamp > timeoutMs }
                if (removed) {
                    LogUtil.w(TAG, "Removed expired callbacks")
                }
            }
        }
    }
    
    // ==================== 安全操作执行器 ====================
    
    /**
     * 安全执行连接操作，确保操作互斥
     */
    private suspend fun executeConnectionOperation(
        operationType: String,
        callback: ConnectionCallback,
        operation: suspend (String) -> Unit
    ) {
        // 检查是否已有操作在进行
        if (!operationMutex.tryLock()) {
            LogUtil.w(TAG, "Another operation is in progress, rejecting $operationType")
            callback.onError(
                InnerErrorCode.T04.code,
                "Another connection operation is in progress"
            )
            return
        }
        
        val operationId = generateOperationId()
        
        try {
            // 设置当前操作信息
            currentOperation = operationType
            currentOperationId = operationId
            
            // 添加回调到管理器
            callbackManager.addCallback(operationId, operationType, callback)
            
            LogUtil.d(TAG, "Starting $operationType operation: $operationId")
            
            // 执行操作
            operation(operationId)
            
        } catch (e: Exception) {
            LogUtil.e(TAG, "Error in $operationType operation: ${e.message}")
            
            // 通知错误
            callbackManager.notifyError(
                InnerErrorCode.T04.code,
                "Operation failed: ${e.message}"
            )
            
        } finally {
            // 清理操作状态
            currentOperation = null
            currentOperationId = null
            
            // 释放互斥锁
            operationMutex.unlock()
            
            LogUtil.d(TAG, "Completed $operationType operation: $operationId")
        }
    }
    
    /**
     * 生成操作ID
     */
    private fun generateOperationId(): String {
        return "${System.currentTimeMillis()}_${operationIdGenerator.incrementAndGet()}"
    }
    
    /**
     * 检查操作是否仍然有效
     */
    private fun isOperationValid(operationId: String): Boolean {
        return currentOperationId == operationId
    }
    
    /**
     * 安全更新状态
     */
    private suspend fun updateStatusSafely(newStatus: InnerConnectionStatus) {
        statusMutex.withLock {
            if (currentInnerConnectionStatus != newStatus) {
                LogUtil.d(TAG, "Status update: ${currentInnerConnectionStatus} -> $newStatus")
                updateStatus(newStatus)
            }
        }
    }
    
    // ==================== 重写连接方法 ====================
    
    override fun performConnect(parseResult: ProtocolParseResult, connectionCallback: ConnectionCallback) {
        launchInScope {
            executeConnectionOperation("CONNECT", connectionCallback) { operationId ->
                try {
                    val lanResult = parseResult as ProtocolParseResult.LanProtocol
                    LogUtil.d(TAG, "Performing LAN connection to: ${lanResult.ip}:${lanResult.port} ($operationId)")
                    
                    // 检查操作是否仍然有效
                    if (!isOperationValid(operationId)) {
                        LogUtil.w(TAG, "Connect operation cancelled: $operationId")
                        return@executeConnectionOperation
                    }
                    
                    // 构建WebSocket URI
                    val uri = try {
                        URI("ws://${lanResult.ip}:${lanResult.port}")
                    } catch (e: Exception) {
                        throw IllegalArgumentException("Invalid URI: ${lanResult.ip}:${lanResult.port}", e)
                    }
                    
                    currentUri = uri
                    
                    // 更新状态为连接中
                    updateStatusSafely(InnerConnectionStatus.CONNECTING)
                    
                    // 检查操作是否仍然有效
                    if (!isOperationValid(operationId)) {
                        LogUtil.w(TAG, "Connect operation cancelled before connection: $operationId")
                        return@executeConnectionOperation
                    }
                    
                    // 尝试直接连接
                    val result = connectionManager.connect(uri)
                    when (result) {
                        is ConnectionManager.ConnectionResult.Success -> {
                            LogUtil.d(TAG, "Direct connection successful ($operationId)")
                            
                            // 检查操作是否仍然有效
                            if (!isOperationValid(operationId)) {
                                LogUtil.w(TAG, "Connect operation cancelled after connection: $operationId")
                                // 连接已建立但操作被取消，需要断开连接
                                connectionManager.disconnect()
                                return@executeConnectionOperation
                            }
                            
                            // 连接成功的处理会通过 ConnectionManager 的监听器触发
                            // 这里不需要额外处理，监听器会调用 callbackManager.notifyConnected()
                        }
                        
                        is ConnectionManager.ConnectionResult.Failure -> {
                            LogUtil.w(TAG, "Direct connection failed: ${result.error} ($operationId)")
                            
                            // 检查操作是否仍然有效
                            if (!isOperationValid(operationId)) {
                                LogUtil.w(TAG, "Connect operation cancelled after connection failure: $operationId")
                                return@executeConnectionOperation
                            }
                            
                            // 尝试服务发现
                            tryServiceDiscoveryForOperation(operationId)
                        }
                    }
                    
                } catch (e: Exception) {
                    LogUtil.e(TAG, "Error in connect operation: ${e.message} ($operationId)")
                    throw e
                }
            }
        }
    }
    
    /**
     * 为特定操作尝试服务发现
     */
    private suspend fun tryServiceDiscoveryForOperation(operationId: String) {
        try {
            LogUtil.d(TAG, "Starting service discovery for operation: $operationId")
            
            // 检查操作是否仍然有效
            if (!isOperationValid(operationId)) {
                LogUtil.w(TAG, "Service discovery cancelled: $operationId")
                return
            }
            
            val services = serviceDiscoveryManager.discoverServices(NSD_SERVICE_TYPE)
            if (services.isNotEmpty()) {
                LogUtil.d(TAG, "Found ${services.size} services for operation: $operationId")
                
                var hasValidService = false
                for (service in services) {
                    // 检查操作是否仍然有效
                    if (!isOperationValid(operationId)) {
                        LogUtil.w(TAG, "Service discovery cancelled during processing: $operationId")
                        return
                    }
                    
                    if (service.isValid()) {
                        hasValidService = true
                        
                        // 通知服务地址变化
                        val shouldReconnect = callbackManager.notifyServiceAddressChanged(
                            service.name,
                            service.host, service.port,
                            currentService?.host ?: "", currentService?.port ?: -1
                        )
                        
                        if (shouldReconnect) {
                            LogUtil.d(TAG, "Service address changed, reconnection will be handled ($operationId)")
                            return
                        } else {
                            LogUtil.d(TAG, "Service address change ignored ($operationId)")
                            return
                        }
                    }
                }
                
                if (hasValidService) {
                    LogUtil.d(TAG, "Found services but no reconnection needed ($operationId)")
                    return
                }
            }
            
            // 检查当前连接状态
            val currentStatus = currentInnerConnectionStatus
            if (currentStatus == InnerConnectionStatus.CONNECTED ||
                currentStatus == InnerConnectionStatus.CONNECTING
            ) {
                LogUtil.d(TAG, "Service discovery found no services, but already connected/connecting ($operationId)")
                return
            }
            
            LogUtil.w(TAG, "No available services found via service discovery ($operationId)")
            throw Exception("No available services found")
            
        } catch (e: Exception) {
            LogUtil.e(TAG, "Service discovery failed for operation: ${e.message} ($operationId)")
            
            // 检查当前连接状态
            val currentStatus = currentInnerConnectionStatus
            if (currentStatus == InnerConnectionStatus.CONNECTED ||
                currentStatus == InnerConnectionStatus.CONNECTING
            ) {
                LogUtil.d(TAG, "Service discovery failed, but already connected/connecting ($operationId)")
                return
            }
            
            throw e
        }
    }
    
    // ==================== 更新连接管理器设置 ====================
    
    /**
     * 设置WebSocket连接管理器（更新版本）
     */
    private fun setupConnectionManager() {
        // 设置连接状态监听器
        connectionManager.setConnectionListener(object : ConnectionManager.ConnectionListener {
            override fun onConnected() {
                launchInScope {
                    LogUtil.d(TAG, "LAN connection established")
                    
                    // 安全更新状态
                    updateStatusSafely(InnerConnectionStatus.CONNECTED)
                    
                    // 创建服务信息（如果需要）
                    if (currentService == null && currentUri != null) {
                        try {
                            val host = currentUri?.host ?: ""
                            val port = currentUri?.port ?: -1
                            if (host.isNotEmpty() && port > 0) {
                                currentService = ServiceInfo(
                                    name = "",
                                    type = NSD_SERVICE_TYPE,
                                    host = host,
                                    port = port,
                                    attributes = emptyMap()
                                )
                                LogUtil.d(TAG, "Created ServiceInfo for direct connection: ${currentService?.getAddress()}")
                            }
                        } catch (e: Exception) {
                            LogUtil.w(TAG, "Failed to create ServiceInfo from URI: ${e.message}")
                        }
                    }
                    
                    // 启动心跳
                    startHeartbeat()
                    
                    // 启动服务监听
                    startServiceMonitoring()
                    
                    // 通知所有回调连接成功
                    val connectionData = mapOf("uri" to currentUri?.toString())
                    callbackManager.notifyConnected(connectionData)
                    
                    // 同时通知基类（保持兼容性）
                    notifyConnectionSuccess(connectionData)
                }
            }
            
            override fun onDisconnected(code: Int, reason: String, remote: Boolean) {
                launchInScope {
                    LogUtil.d(TAG, "LAN connection closed: code=$code, reason=$reason, remote=$remote, isManual=$isManualDisconnect")
                    
                    // 停止心跳
                    stopHeartbeat()
                    
                    // 构建断开信息
                    val disconnectCode = if (isManualDisconnect) {
                        InnerErrorCode.T08.code
                    } else {
                        InnerErrorCode.T06.code
                    }
                    val disconnectMessage = if (isManualDisconnect) {
                        "${InnerErrorCode.T08.description}:Manual disconnect, $reason($code)"
                    } else {
                        "${InnerErrorCode.T06.description}:Passive disconnect, $reason($code)"
                    }
                    
                    val wasManual = isManualDisconnect
                    isManualDisconnect = false
                    
                    // 判断是否是连接错误
                    val isError = code == 1006 || code >= 1002
                    
                    // 安全更新状态
                    if (isError) {
                        updateStatusSafely(InnerConnectionStatus.ERROR)
                    } else {
                        updateStatusSafely(InnerConnectionStatus.DISCONNECTED)
                    }
                    
                    // 通知所有回调连接断开
                    callbackManager.notifyError(disconnectCode, disconnectMessage)
                    
                    // 同时通知基类（保持兼容性）
                    notifyConnectionDisconnected(disconnectCode, disconnectMessage)
                    
                    // 如果不是主动断开，启动服务监听
                    if (!wasManual && callbackManager.hasCallbacks()) {
                        LogUtil.d(TAG, "Passive disconnect detected, starting service monitoring")
                        if (!isServiceMonitoring) {
                            startServiceMonitoring()
                        }
                    }
                }
            }
            
            override fun onError(exception: Exception) {
                launchInScope {
                    LogUtil.e(TAG, "LAN connection error: ${exception.message}")
                    
                    // 安全更新状态
                    updateStatusSafely(InnerConnectionStatus.ERROR)
                    
                    // 通知所有回调连接错误
                    callbackManager.notifyError(
                        InnerErrorCode.T04.code,
                        "Connection error: ${exception.message}"
                    )
                    
                    // 同时通知基类（保持兼容性）
                    notifyConnectionError("Connection error: ${exception.message}", InnerErrorCode.T04)
                }
            }
        })
        
        // 消息监听器保持不变
        connectionManager.setMessageListener(object : ConnectionManager.MessageListener {
            override fun onTextMessage(message: String) {
                LogUtil.d(TAG, "Text message received: $message")
                
                if (isHeartbeatResponse(message)) {
                    handleHeartbeatResponse()
                    return
                }
                
                dataReceiver?.invoke(message.toByteArray(Charsets.UTF_8))
            }
            
            override fun onBinaryMessage(data: ByteArray) {
                LogUtil.d(TAG, "Binary message received: ${data.size} bytes")
                dataReceiver?.invoke(data)
            }
        })
    }
    
    // ==================== 更新服务监听 ====================
    
    /**
     * 启动服务监听（更新版本）
     */
    private fun startServiceMonitoring() {
        if (isServiceMonitoring) {
            LogUtil.d(TAG, "Service monitoring already started")
            return
        }
        
        try {
            LogUtil.d(TAG, "Starting service monitoring")
            
            val listener = object : ServiceDiscoveryManager.ServiceChangeListener {
                override fun onServiceFound(service: ServiceInfo) {
                    LogUtil.d(TAG, "New service found: ${service.name} at ${service.getAddress()}")
                    
                    // 检查当前连接状态
                    val currentStatus = currentInnerConnectionStatus
                    val isSameAddress = currentService != null &&
                            currentService?.host == service.host &&
                            currentService?.port == service.port
                    
                    if (isSameAddress &&
                        (currentStatus == InnerConnectionStatus.CONNECTED ||
                                currentStatus == InnerConnectionStatus.CONNECTING ||
                                currentStatus == InnerConnectionStatus.WAITING_CONNECT)
                    ) {
                        LogUtil.d(TAG, "Service found but already connected/connecting to same address")
                        return
                    }
                    
                    // 防抖检查
                    val currentTime = System.currentTimeMillis()
                    if (currentTime - lastServiceAddressChangeTime < SERVICE_ADDRESS_CHANGE_DEBOUNCE_MS) {
                        LogUtil.d(TAG, "Service address change debounced")
                        return
                    }
                    lastServiceAddressChangeTime = currentTime
                    
                    // 通知服务地址变化
                    val shouldReconnect = callbackManager.notifyServiceAddressChanged(
                        service.name,
                        service.host,
                        service.port,
                        currentService?.host ?: "",
                        currentService?.port ?: -1
                    )
                    
                    if (shouldReconnect) {
                        LogUtil.d(TAG, "Service address changed, triggering reconnection")
                        // 这里可以触发重连逻辑
                    }
                }
                
                override fun onServiceLost(service: ServiceInfo) {
                    LogUtil.w(TAG, "Service lost: ${service.name} at ${service.getAddress()}")
                    
                    if (currentService != null && service.name == currentService?.name) {
                        LogUtil.w(TAG, "Current connected service is lost")
                    }
                }
                
                override fun onServiceUpdated(oldService: ServiceInfo, newService: ServiceInfo) {
                    LogUtil.i(TAG, "Service updated: ${newService.name}, ${oldService.getAddress()} -> ${newService.getAddress()}")
                    
                    val addressChanged = oldService.host != newService.host || oldService.port != newService.port
                    if (!addressChanged) {
                        LogUtil.d(TAG, "Service updated but address unchanged")
                        return
                    }
                    
                    // 防抖检查
                    val currentTime = System.currentTimeMillis()
                    if (currentTime - lastServiceAddressChangeTime < SERVICE_ADDRESS_CHANGE_DEBOUNCE_MS) {
                        LogUtil.d(TAG, "Service address change debounced")
                        return
                    }
                    lastServiceAddressChangeTime = currentTime
                    
                    // 通知服务地址变化
                    val shouldReconnect = callbackManager.notifyServiceAddressChanged(
                        newService.name,
                        newService.host,
                        newService.port,
                        oldService.host,
                        oldService.port
                    )
                    
                    if (shouldReconnect) {
                        LogUtil.d(TAG, "Service address updated, triggering reconnection")
                    }
                }
                
                override fun onDiscoveryStarted(serviceType: String) {
                    LogUtil.d(TAG, "Service monitoring started for: $serviceType")
                    isServiceMonitoring = true
                }
                
                override fun onDiscoveryStopped(serviceType: String) {
                    LogUtil.d(TAG, "Service monitoring stopped for: $serviceType")
                    isServiceMonitoring = false
                }
                
                override fun onDiscoveryFailed(serviceType: String, errorCode: Int, error: String) {
                    LogUtil.e(TAG, "Service monitoring failed: $error")
                    isServiceMonitoring = false
                }
            }
            
            serviceDiscoveryManager.startServiceMonitoring(NSD_SERVICE_TYPE, listener)
            
        } catch (e: Exception) {
            LogUtil.e(TAG, "Failed to start service monitoring: ${e.message}")
        }
    }
    
    // ==================== 清理资源（更新版本） ====================
    
    /**
     * 清理资源（更新版本）
     */
    private fun cleanupResources() {
        // 清理回调
        callbackManager.clearExpiredCallbacks(0) // 清理所有回调
        
        // 重置状态变量
        currentUri = null
        currentService = null
        isServiceMonitoring = false
        isManualDisconnect = false
        lastServiceAddressChangeTime = 0
        currentOperation = null
        currentOperationId = null
        
        // 确保所有管理器组件完全清理
        try {
            stopHeartbeat()
            stopServiceDiscovery()
        } catch (e: Exception) {
            LogUtil.e(TAG, "Error during cleanup: ${e.message}")
        }
        
        LogUtil.d(TAG, "Resources cleaned up")
    }
    
    // ==================== 添加新的公共方法 ====================
    
    /**
     * 获取当前操作状态
     */
    fun getCurrentOperation(): String? = currentOperation
    
    /**
     * 获取当前操作ID
     */
    fun getCurrentOperationId(): String? = currentOperationId
    
    /**
     * 获取活跃回调数量
     */
    fun getActiveCallbackCount(): Int = callbackManager.getCallbackCount()
    
    /**
     * 检查是否有操作在进行
     */
    fun hasOperationInProgress(): Boolean = currentOperation != null
}
```

## 关键改进点

### 1. 操作互斥机制
- 使用 `Mutex` 确保同时只有一个连接操作
- 为每个操作分配唯一ID，便于跟踪和验证
- 操作开始前检查是否有其他操作在进行

### 2. 回调队列管理
- `ConnectionCallbackManager` 支持多个回调
- 避免回调替换问题
- 统一通知所有相关回调
- 自动清理过期回调

### 3. 状态同步保护
- 使用 `statusMutex` 保护状态更新
- 确保状态转换的原子性
- 避免并发状态更新冲突

### 4. 操作有效性检查
- 在关键点检查操作是否仍然有效
- 支持操作取消和清理
- 避免已取消操作的副作用

### 5. 增强的错误处理
- 更详细的错误信息和日志
- 操作级别的异常处理
- 资源清理保证

## 向后兼容性

修复方案保持了与现有代码的兼容性：
- 保留原有的公共接口
- 继续支持基类的通知机制
- 不改变外部调用方式

## 性能影响

- **互斥锁开销**: 最小，只在操作开始/结束时使用
- **回调管理开销**: 轻微，使用同步集合管理
- **内存开销**: 很小，主要是回调队列和操作状态
- **整体影响**: 可忽略，换来了更好的稳定性

## 测试验证

使用提供的测试用例验证修复效果：
1. 并发操作测试
2. 回调正确性测试  
3. 状态一致性测试
4. 资源清理测试

修复后应该能够通过所有竞态条件测试用例。