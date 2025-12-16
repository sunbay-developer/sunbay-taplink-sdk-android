# WebSocketServerKernel 使用指南

## 概述

`WebSocketServerKernel` 是一个功能完整的 WebSocket 服务端实现，支持：

- ✅ **多客户端连接**：同时支持多个客户端连接
- ✅ **心跳检测**：自动检测客户端连接状态，超时自动断开
- ✅ **mDNS 广播**：自动在局域网中广播服务，支持客户端自动发现
- ✅ **线程安全**：支持并发数据发送
- ✅ **客户端管理**：跟踪所有连接的客户端

## 核心功能

### 1. WebSocket 服务端

启动 WebSocket 服务器，监听指定端口：

```kotlin
val serverKernel = WebSocketServerKernel(
    appId = "your_app_id",
    appSecretKey = "your_secret_key",
    context = applicationContext
)

// 启动服务器（默认端口 8443）
serverKernel.connect("server://", object : ConnectionCallback {
    override fun onConnected(extraInfoMap: Map<String, String?>?) {
        val serverIp = extraInfoMap?.get("serverIp")
        val serverPort = extraInfoMap?.get("serverPort")
        Log.d(TAG, "Server started on $serverIp:$serverPort")
    }
    
    override fun onWaitingConnect() {
        Log.d(TAG, "Starting server...")
    }
    
    override fun onDisconnected(code: String, msg: String) {
        Log.d(TAG, "Server stopped: $msg")
    }
})

// 指定端口启动
serverKernel.connect("server://8443", connectionCallback)
```

### 2. 心跳检测

服务器自动检测客户端心跳：

**配置参数：**
- `HEARTBEAT_INTERVAL`: 30秒（检查间隔）
- `HEARTBEAT_TIMEOUT`: 60秒（超时时间）

**工作原理：**
1. 服务器每 30 秒检查一次所有客户端
2. 如果客户端超过 60 秒没有心跳，自动断开连接
3. 客户端发送任何消息都会更新心跳时间
4. 客户端发送 `{"type":"ping"}` 会收到 `{"type":"pong"}` 响应

**客户端心跳示例：**
```kotlin
// 客户端每 30 秒发送心跳
val heartbeatJob = scope.launch {
    while (isActive) {
        delay(30_000)
        webSocket.send("{\"type\":\"ping\"}")
    }
}
```

### 3. mDNS 服务广播

服务器启动后自动在局域网中广播：

**广播信息：**
- 服务类型：`_taplink._tcp`
- 服务名称：`Taplink-Server`
- 端口：实际监听端口
- 额外信息：appId, version

**广播频率：**
- 启动时立即广播
- 每 30 秒重新广播一次
- 网络变化时自动重新注册

**客户端发现示例：**
```kotlin
val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager

val discoveryListener = object : NsdManager.DiscoveryListener {
    override fun onServiceFound(serviceInfo: NsdServiceInfo) {
        if (serviceInfo.serviceType == "_taplink._tcp") {
            // 找到 Taplink 服务
            nsdManager.resolveService(serviceInfo, resolveListener)
        }
    }
    // ... 其他回调
}

nsdManager.discoverServices("_taplink._tcp", NsdManager.PROTOCOL_DNS_SD, discoveryListener)
```

### 4. 数据发送

**广播到所有客户端：**
```kotlin
val data = "Hello, clients!".toByteArray()
serverKernel.sendData("trace_001", data, object : InnerCallback {
    override fun onResponse(responseData: String) {
        Log.d(TAG, "Broadcast success: $responseData")
    }
    
    override fun onError(code: String, msg: String) {
        Log.e(TAG, "Broadcast failed: $msg")
    }
})
```

**发送到指定客户端：**
```kotlin
val clientId = "client_12345"
val data = "Hello, specific client!".toByteArray()
serverKernel.sendDataToClient(clientId, "trace_002", data, object : InnerCallback {
    override fun onResponse(responseData: String) {
        Log.d(TAG, "Sent to client $clientId")
    }
    
    override fun onError(code: String, msg: String) {
        Log.e(TAG, "Failed to send to client: $msg")
    }
})
```

### 5. 客户端管理

**获取连接的客户端数量：**
```kotlin
val clientCount = serverKernel.getClientCount()
Log.d(TAG, "Connected clients: $clientCount")
```

**获取所有客户端信息：**
```kotlin
val clients = serverKernel.getClients()
clients.forEach { clientInfo ->
    Log.d(TAG, "Client: ${clientInfo.id}, address: ${clientInfo.remoteAddress}")
    val timeSinceHeartbeat = System.currentTimeMillis() - clientInfo.lastHeartbeat
    Log.d(TAG, "Last heartbeat: ${timeSinceHeartbeat}ms ago")
}
```

### 6. 接收数据

**注册数据接收器：**
```kotlin
serverKernel.registerDataReceiver { data ->
    val message = String(data, Charsets.UTF_8)
    Log.d(TAG, "Received from client: $message")
    
    // 处理接收到的数据
    handleClientMessage(message)
}
```

### 7. 停止服务器

```kotlin
serverKernel.disconnect()
```

停止服务器会：
1. 断开所有客户端连接
2. 停止心跳检测
3. 注销 mDNS 服务
4. 停止定期广播
5. 清理所有资源

## 完整示例

### 服务端应用

```kotlin
class ServerActivity : AppCompatActivity() {
    private lateinit var serverKernel: WebSocketServerKernel
    private val TAG = "ServerActivity"
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 创建服务器内核
        serverKernel = WebSocketServerKernel(
            appId = "taplink_server",
            appSecretKey = "secret_key_123",
            context = applicationContext
        )
        
        // 注册数据接收器
        serverKernel.registerDataReceiver { data ->
            val message = String(data, Charsets.UTF_8)
            Log.d(TAG, "Received: $message")
            
            runOnUiThread {
                handleClientMessage(message)
            }
        }
        
        // 启动服务器
        startServer()
    }
    
    private fun startServer() {
        serverKernel.connect("server://8443", object : ConnectionCallback {
            override fun onConnected(extraInfoMap: Map<String, String?>?) {
                val serverIp = extraInfoMap?.get("serverIp")
                val serverPort = extraInfoMap?.get("serverPort")
                
                runOnUiThread {
                    statusTextView.text = "服务器运行中\n$serverIp:$serverPort"
                    showToast("服务器已启动")
                    
                    // 显示二维码供客户端扫描连接
                    showQRCode("ws://$serverIp:$serverPort")
                }
                
                // 启动客户端监控
                startClientMonitoring()
            }
            
            override fun onWaitingConnect() {
                runOnUiThread {
                    statusTextView.text = "正在启动服务器..."
                }
            }
            
            override fun onDisconnected(code: String, msg: String) {
                runOnUiThread {
                    statusTextView.text = "服务器已停止"
                    showToast("服务器已停止: $msg")
                }
            }
        })
    }
    
    private fun startClientMonitoring() {
        // 每 5 秒更新客户端列表
        lifecycleScope.launch {
            while (isActive) {
                delay(5000)
                updateClientList()
            }
        }
    }
    
    private fun updateClientList() {
        val clients = serverKernel.getClients()
        val clientCount = serverKernel.getClientCount()
        
        runOnUiThread {
            clientCountTextView.text = "连接的客户端: $clientCount"
            
            val clientList = clients.joinToString("\n") { client ->
                val timeSinceHeartbeat = System.currentTimeMillis() - client.lastHeartbeat
                "${client.id} - ${client.remoteAddress} (${timeSinceHeartbeat}ms)"
            }
            clientListTextView.text = clientList
        }
    }
    
    private fun handleClientMessage(message: String) {
        try {
            val json = JSONObject(message)
            val type = json.optString("type")
            
            when (type) {
                "payment_request" -> handlePaymentRequest(json)
                "query_request" -> handleQueryRequest(json)
                else -> Log.w(TAG, "Unknown message type: $type")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse message: ${e.message}")
        }
    }
    
    private fun handlePaymentRequest(json: JSONObject) {
        val amount = json.optDouble("amount")
        val orderId = json.optString("orderId")
        
        Log.d(TAG, "Payment request: amount=$amount, orderId=$orderId")
        
        // 处理支付请求...
        
        // 发送响应到所有客户端
        val response = JSONObject().apply {
            put("type", "payment_response")
            put("orderId", orderId)
            put("status", "success")
        }
        
        serverKernel.sendData(
            "payment_response",
            response.toString().toByteArray(),
            null
        )
    }
    
    private fun broadcastMessage(message: String) {
        serverKernel.sendData(
            "broadcast_${System.currentTimeMillis()}",
            message.toByteArray(),
            object : InnerCallback {
                override fun onResponse(responseData: String) {
                    showToast("消息已广播: $responseData")
                }
                
                override fun onError(code: String, msg: String) {
                    showToast("广播失败: $msg")
                }
            }
        )
    }
    
    override fun onDestroy() {
        super.onDestroy()
        serverKernel.disconnect()
    }
}
```

### 客户端应用（使用 WebSocketClientKernel）

```kotlin
class ClientActivity : AppCompatActivity() {
    private lateinit var clientKernel: WebSocketClientKernel
    private val TAG = "ClientActivity"
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 创建客户端内核
        clientKernel = WebSocketClientKernel(
            appId = "taplink_client",
            appSecretKey = "secret_key_123",
            context = applicationContext
        )
        
        // 注册数据接收器
        clientKernel.registerDataReceiver { data ->
            val message = String(data, Charsets.UTF_8)
            Log.d(TAG, "Received from server: $message")
            
            runOnUiThread {
                handleServerMessage(message)
            }
        }
        
        // 方式 1: 直接连接（如果知道服务器地址）
        connectToServer("192.168.1.100", 8443)
        
        // 方式 2: 通过 mDNS 自动发现并连接
        // clientKernel.connect("lan://", connectionCallback)
    }
    
    private fun connectToServer(ip: String, port: Int) {
        clientKernel.connect("ws://$ip:$port", object : ConnectionCallback {
            override fun onConnected(extraInfoMap: Map<String, String?>?) {
                runOnUiThread {
                    statusTextView.text = "已连接到服务器"
                    showToast("连接成功")
                }
                
                // 启动心跳
                startHeartbeat()
            }
            
            override fun onWaitingConnect() {
                runOnUiThread {
                    statusTextView.text = "正在连接..."
                }
            }
            
            override fun onDisconnected(code: String, msg: String) {
                runOnUiThread {
                    statusTextView.text = "已断开连接"
                    showToast("连接断开: $msg")
                }
            }
        })
    }
    
    private fun startHeartbeat() {
        lifecycleScope.launch {
            while (isActive) {
                delay(30_000) // 每 30 秒发送心跳
                
                val heartbeat = JSONObject().apply {
                    put("type", "ping")
                }
                
                clientKernel.sendData(
                    "heartbeat",
                    heartbeat.toString().toByteArray(),
                    null
                )
            }
        }
    }
    
    private fun sendPaymentRequest(amount: Double, orderId: String) {
        val request = JSONObject().apply {
            put("type", "payment_request")
            put("amount", amount)
            put("orderId", orderId)
        }
        
        clientKernel.sendData(
            "payment_$orderId",
            request.toString().toByteArray(),
            object : InnerCallback {
                override fun onResponse(responseData: String) {
                    Log.d(TAG, "Payment request sent")
                }
                
                override fun onError(code: String, msg: String) {
                    showToast("发送失败: $msg")
                }
            }
        )
    }
    
    private fun handleServerMessage(message: String) {
        try {
            val json = JSONObject(message)
            val type = json.optString("type")
            
            when (type) {
                "payment_response" -> handlePaymentResponse(json)
                "pong" -> Log.d(TAG, "Heartbeat response received")
                else -> Log.w(TAG, "Unknown message type: $type")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse message: ${e.message}")
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        clientKernel.disconnect()
    }
}
```

## 配置说明

### 端口配置

```kotlin
// 默认端口
private const val DEFAULT_PORT = 8443

// 端口范围（如果默认端口被占用，会尝试范围内的其他端口）
private const val PORT_RANGE_START = 8443
private const val PORT_RANGE_END = 8453
```

### 心跳配置

```kotlin
// 心跳检查间隔（30秒）
private const val HEARTBEAT_INTERVAL = 30_000L

// 心跳超时时间（60秒）
private const val HEARTBEAT_TIMEOUT = 60_000L

// 心跳响应消息
private const val HEARTBEAT_MESSAGE = "{\"type\":\"pong\"}"
```

### mDNS 配置

```kotlin
// 服务类型
private const val NSD_SERVICE_TYPE = "_taplink._tcp"

// 服务名称
private const val NSD_SERVICE_NAME = "Taplink-Server"

// 广播间隔（30秒）
private const val NSD_BROADCAST_INTERVAL = 30_000L
```

## 最佳实践

### 1. 服务器生命周期管理

```kotlin
class ServerService : Service() {
    private lateinit var serverKernel: WebSocketServerKernel
    
    override fun onCreate() {
        super.onCreate()
        
        serverKernel = WebSocketServerKernel(
            appId = "server_app",
            appSecretKey = "secret",
            context = applicationContext
        )
        
        // 启动前台服务，防止被系统杀死
        startForeground(NOTIFICATION_ID, createNotification())
        
        // 启动服务器
        serverKernel.connect("server://", connectionCallback)
    }
    
    override fun onDestroy() {
        super.onDestroy()
        serverKernel.disconnect()
    }
}
```

### 2. 客户端重连

```kotlin
// 客户端应该实现自动重连
private fun connectWithRetry(maxRetries: Int = 5) {
    var attempts = 0
    
    fun attemptConnect() {
        clientKernel.connect("lan://", object : ConnectionCallback {
            override fun onConnected(extraInfoMap: Map<String, String?>?) {
                attempts = 0 // 重置重试次数
                onConnectionSuccess()
            }
            
            override fun onDisconnected(code: String, msg: String) {
                if (attempts < maxRetries) {
                    attempts++
                    val delay = minOf(2000L * attempts, 30000L)
                    Handler(Looper.getMainLooper()).postDelayed({
                        attemptConnect()
                    }, delay)
                } else {
                    onConnectionFailed()
                }
            }
            
            override fun onWaitingConnect() {}
        })
    }
    
    attemptConnect()
}
```

### 3. 消息协议设计

```kotlin
// 统一的消息格式
data class Message(
    val type: String,        // 消息类型
    val traceId: String,     // 追踪ID
    val timestamp: Long,     // 时间戳
    val data: Any?           // 数据
)

// 序列化和反序列化
fun Message.toJson(): String = Gson().toJson(this)
fun String.toMessage(): Message = Gson().fromJson(this, Message::class.java)
```

### 4. 错误处理

```kotlin
serverKernel.sendData(traceId, data, object : InnerCallback {
    override fun onResponse(responseData: String) {
        // 成功
    }
    
    override fun onError(code: String, msg: String) {
        when (code) {
            "NO_CLIENTS" -> {
                // 没有客户端连接
                showToast("没有客户端连接")
            }
            "SEND_FAILED" -> {
                // 发送失败，可能需要重试
                retryS send(traceId, data)
            }
            else -> {
                Log.e(TAG, "Unknown error: $code - $msg")
            }
        }
    }
})
```

## 注意事项

1. **权限要求**
   - `INTERNET` - 网络通信
   - `ACCESS_NETWORK_STATE` - 检查网络状态
   - `CHANGE_WIFI_MULTICAST_STATE` - mDNS 广播

2. **线程安全**
   - 所有回调都在后台线程执行
   - UI 更新需要切换到主线程

3. **资源管理**
   - 及时调用 `disconnect()` 释放资源
   - 避免内存泄漏

4. **网络环境**
   - 确保服务器和客户端在同一局域网
   - 防火墙可能阻止连接

5. **心跳机制**
   - 客户端应该定期发送心跳
   - 服务器会自动断开超时的客户端

## 故障排查

### 问题 1: 服务器启动失败

**可能原因：**
- 端口被占用
- 权限不足

**解决方案：**
```kotlin
// 检查端口是否被占用
fun isPortAvailable(port: Int): Boolean {
    return try {
        ServerSocket(port).use { true }
    } catch (e: IOException) {
        false
    }
}

// 使用不同的端口
serverKernel.connect("server://8444", connectionCallback)
```

### 问题 2: mDNS 服务无法发现

**可能原因：**
- WiFi 未连接
- 多播被禁用
- 防火墙阻止

**解决方案：**
```kotlin
// 检查 WiFi 连接
val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
if (!wifiManager.isWifiEnabled) {
    // 提示用户打开 WiFi
}

// 启用多播
val multicastLock = wifiManager.createMulticastLock("taplink")
multicastLock.acquire()
```

### 问题 3: 客户端心跳超时

**可能原因：**
- 网络不稳定
- 客户端未发送心跳
- 心跳间隔太长

**解决方案：**
```kotlin
// 客户端缩短心跳间隔
private const val CLIENT_HEARTBEAT_INTERVAL = 20_000L // 20秒

// 服务器增加超时时间
private const val HEARTBEAT_TIMEOUT = 90_000L // 90秒
```

---

**版本**: 1.0  
**更新日期**: 2025-12-11  
**作者**: TaPro Team
