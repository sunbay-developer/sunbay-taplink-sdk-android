# WebSocketServerKernel 快速参考

## 🚀 快速开始

### 1. 创建服务器

```kotlin
val serverKernel = WebSocketServerKernel(
    appId = "your_app_id",
    appSecretKey = "your_secret_key",
    context = applicationContext
)
```

### 2. 启动服务器

```kotlin
// 默认端口 8443
serverKernel.connect("server://", connectionCallback)

// 指定端口
serverKernel.connect("server://8444", connectionCallback)
```

### 3. 接收数据

```kotlin
serverKernel.registerDataReceiver { data ->
    val message = String(data, Charsets.UTF_8)
    handleMessage(message)
}
```

### 4. 发送数据

```kotlin
// 广播到所有客户端
serverKernel.sendData("trace_001", data, callback)

// 发送到指定客户端
serverKernel.sendDataToClient(clientId, "trace_002", data, callback)
```

### 5. 停止服务器

```kotlin
serverKernel.disconnect()
```

## ⚙️ 配置参数

| 参数 | 默认值 | 说明 |
|------|--------|------|
| 默认端口 | 8443 | 服务器监听端口 |
| 端口范围 | 8443-8453 | 自动选择范围 |
| 心跳间隔 | 30秒 | 检查间隔 |
| 心跳超时 | 60秒 | 超时时间 |
| 广播间隔 | 30秒 | mDNS 广播间隔 |

## 📋 核心功能

### ✅ 多客户端支持
- 同时支持多个客户端连接
- 自动管理客户端连接和断开
- 跟踪每个客户端的状态

### ✅ 心跳检测
- 自动检测客户端连接状态
- 超时自动断开僵尸连接
- 收到 ping 自动回复 pong

### ✅ mDNS 广播
- 自动在局域网中广播服务
- 客户端可以自动发现服务器
- 定期重新广播保持可见性

### ✅ 数据发送
- 广播到所有客户端
- 发送到指定客户端
- 线程安全的并发发送

## 🔄 连接回调

```kotlin
object : ConnectionCallback {
    override fun onConnected(extraInfoMap: Map<String, String?>?) {
        // 服务器启动成功
        val serverIp = extraInfoMap?.get("serverIp")
        val serverPort = extraInfoMap?.get("serverPort")
    }
    
    override fun onWaitingConnect() {
        // 正在启动服务器
    }
    
    override fun onDisconnected(code: String, msg: String) {
        // 服务器已停止
    }
}
```

## 📊 客户端管理

```kotlin
// 获取客户端数量
val count = serverKernel.getClientCount()

// 获取所有客户端
val clients = serverKernel.getClients()
clients.forEach { client ->
    println("${client.id} - ${client.remoteAddress}")
}
```

## 💡 常见用法

### 启动前台服务

```kotlin
class ServerService : Service() {
    private lateinit var serverKernel: WebSocketServerKernel
    
    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, createNotification())
        
        serverKernel = WebSocketServerKernel(
            appId = "server_app",
            appSecretKey = "secret",
            context = applicationContext
        )
        
        serverKernel.connect("server://", connectionCallback)
    }
    
    override fun onDestroy() {
        super.onDestroy()
        serverKernel.disconnect()
    }
}
```

### 处理客户端消息

```kotlin
serverKernel.registerDataReceiver { data ->
    try {
        val json = JSONObject(String(data, Charsets.UTF_8))
        val type = json.optString("type")
        
        when (type) {
            "ping" -> handlePing()
            "payment_request" -> handlePayment(json)
            "query_request" -> handleQuery(json)
            else -> Log.w(TAG, "Unknown type: $type")
        }
    } catch (e: Exception) {
        Log.e(TAG, "Parse error: ${e.message}")
    }
}
```

### 广播消息

```kotlin
fun broadcastUpdate(update: String) {
    val data = update.toByteArray()
    serverKernel.sendData("broadcast", data, object : InnerCallback {
        override fun onResponse(responseData: String) {
            Log.d(TAG, "Broadcast success: $responseData")
        }
        
        override fun onError(code: String, msg: String) {
            Log.e(TAG, "Broadcast failed: $msg")
        }
    })
}
```

## ⚠️ 注意事项

1. **权限要求**
   ```xml
   <uses-permission android:name="android.permission.INTERNET" />
   <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
   <uses-permission android:name="android.permission.CHANGE_WIFI_MULTICAST_STATE" />
   ```

2. **线程安全**
   - 所有回调在后台线程执行
   - UI 更新需要切换到主线程

3. **资源管理**
   - 及时调用 `disconnect()` 释放资源
   - 避免内存泄漏

4. **网络环境**
   - 确保在同一局域网
   - 检查防火墙设置

## 🔍 故障排查

### 服务器启动失败

```kotlin
// 检查端口是否被占用
fun isPortAvailable(port: Int): Boolean {
    return try {
        ServerSocket(port).use { true }
    } catch (e: IOException) {
        false
    }
}
```

### mDNS 无法发现

```kotlin
// 启用多播
val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
val multicastLock = wifiManager.createMulticastLock("taplink")
multicastLock.acquire()
```

### 客户端心跳超时

```kotlin
// 客户端缩短心跳间隔
lifecycleScope.launch {
    while (isActive) {
        delay(20_000) // 20秒
        client.send("{\"type\":\"ping\"}")
    }
}
```

## 📖 完整文档

- [详细使用指南](WEBSOCKET_SERVER_USAGE.md)
- [实现总结](WEBSOCKET_SERVER_SUMMARY.md)
- [客户端实现](WebSocketClientKernel.kt)

---

**版本**: 1.0.0  
**更新日期**: 2025-12-11
