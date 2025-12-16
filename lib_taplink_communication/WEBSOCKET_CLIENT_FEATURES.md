# WebSocketClientKernel 功能说明

## 概述

WebSocketClientKernel 是 Taplink SDK 中用于 LAN 模式连接的核心组件，提供了完整的 WebSocket 客户端功能，支持心跳机制、服务发现和智能重连。

## 主要功能特性

### 1. WebSocket 客户端连接
- 支持 `ws://` 和 `wss://` 协议
- 支持 `lan://` 协议自动转换
- 线程安全的数据发送
- 异步连接和数据处理

### 2. 心跳机制
- **心跳间隔**: 30秒
- **心跳超时**: 10秒
- **心跳消息**: `{"type":"ping"}`
- 自动检测连接状态
- 超时自动触发重连

### 3. NSD 服务发现
- 使用 Android 原生 NSD (Network Service Discovery) API
- 服务类型: `_taplink._tcp`
- 发现超时: 10秒
- 自动解析服务信息（IP地址和端口）
- 连接失败时自动启动服务发现

### 4. 智能重连机制
- **区分断开类型**:
  - 主动断开：不进行重连
  - 异常断开：自动重连
- **重连策略**:
  - 最大重连次数: 5次
  - 指数退避算法: 2秒 → 4秒 → 8秒 → 16秒 → 30秒
  - 重连成功后重置计数器

### 5. 线程安全
- 使用协程和 Mutex 确保并发安全
- 独立的协程作用域管理
- 原子操作控制状态

## 使用方式

### 基本连接
```kotlin
// 直接 WebSocket 连接
val kernel = WebSocketClientKernel(appId, secretKey, context)
kernel.connect("ws://192.168.1.100:8443", connectionCallback)

// LAN 协议（自动转换为 WebSocket）
kernel.connect("lan://192.168.1.100:8443", connectionCallback)
```

### 自动服务发现
```kotlin
// 连接失败时自动启动 NSD 发现
kernel.connect("ws://invalid-host:8443", connectionCallback)
// 如果直接连接失败，会自动搜索局域网内的 Taplink 服务
```

### 发送数据
```kotlin
kernel.sendData(traceId, data) { result ->
    when (result) {
        is Success -> println("数据发送成功")
        is Error -> println("发送失败: ${result.message}")
    }
}
```

### 断开连接
```kotlin
// 主动断开（不会重连）
kernel.disconnect()
```

## 配置参数

### 心跳配置
```kotlin
companion object {
    private const val HEARTBEAT_INTERVAL = 30_000L // 30秒心跳间隔
    private const val HEARTBEAT_TIMEOUT = 10_000L // 10秒心跳超时
    private const val HEARTBEAT_MESSAGE = "{\"type\":\"ping\"}"
}
```

### 重连配置
```kotlin
companion object {
    private const val MAX_RECONNECT_ATTEMPTS = 5 // 最大重连次数
    private const val RECONNECT_DELAY_BASE = 2_000L // 重连基础延迟2秒
    private const val RECONNECT_DELAY_MAX = 30_000L // 最大重连延迟30秒
}
```

### NSD 配置
```kotlin
companion object {
    private const val NSD_SERVICE_TYPE = "_taplink._tcp" // Taplink服务类型
    private const val NSD_DISCOVERY_TIMEOUT = 10_000L // NSD发现超时10秒
}
```

## 状态管理

### 连接状态
- `DISCONNECTED`: 未连接
- `CONNECTING`: 连接中
- `CONNECTED`: 已连接
- `ERROR`: 连接错误

### 状态转换
```
DISCONNECTED → CONNECTING → CONNECTED
     ↑              ↓           ↓
     └── ERROR ←────┴───────────┘
```

## 错误处理

### 连接错误码
- `C33`: WebSocket 连接失败
- `C34`: 设备离线
- `C35`: NSD 发现失败

### 错误恢复
1. **连接失败**: 自动启动 NSD 服务发现
2. **心跳超时**: 触发重连机制
3. **异常断开**: 指数退避重连
4. **发送失败**: 立即返回错误，不影响连接状态

## 最佳实践

### 1. 网络权限
确保应用具有必要的网络权限：
```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
<uses-permission android:name="android.permission.CHANGE_WIFI_MULTICAST_STATE" />
```

### 2. 生命周期管理
```kotlin
class MainActivity : AppCompatActivity() {
    private lateinit var kernel: WebSocketClientKernel
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        kernel = WebSocketClientKernel(appId, secretKey, this)
    }
    
    override fun onDestroy() {
        super.onDestroy()
        kernel.disconnect() // 主动断开连接
    }
}
```

### 3. 连接监听
```kotlin
val connectionCallback = object : ConnectionCallback {
    override fun onConnected(extraInfoMap: Map<String, String?>?) {
        // 连接成功，可以开始发送数据
    }
    
    override fun onDisconnected(code: String, message: String) {
        // 连接断开，检查是否需要重连
    }
    
    override fun onWaitingConnect() {
        // 正在连接中
    }
}
```

### 4. 数据发送
```kotlin
// 确保在连接成功后发送数据
if (kernel.getConnectionStatus() == ConnectionStatus.CONNECTED) {
    kernel.sendData(traceId, data, callback)
} else {
    // 处理未连接状态
}
```

## 注意事项

1. **网络环境**: 确保设备在同一局域网内
2. **防火墙**: 确保端口 8443-8453 未被阻止
3. **服务发现**: NSD 依赖于网络的 mDNS 支持
4. **资源管理**: 及时调用 disconnect() 释放资源
5. **线程安全**: 所有方法都是线程安全的，可以在任意线程调用

## 性能优化

1. **连接复用**: 同一设备的多次连接会复用现有连接
2. **异步处理**: 所有网络操作都在后台线程执行
3. **内存管理**: 自动清理过期的回调和资源
4. **网络优化**: 使用心跳机制减少不必要的重连

## 故障排除

### 连接失败
1. 检查网络连接
2. 确认目标设备在线
3. 验证 IP 地址和端口
4. 检查防火墙设置

### 服务发现失败
1. 确认网络支持 mDNS
2. 检查 WiFi 连接状态
3. 验证服务类型配置
4. 尝试手动指定 IP 地址

### 重连失败
1. 检查网络稳定性
2. 确认设备未进入休眠
3. 验证心跳配置
4. 查看错误日志