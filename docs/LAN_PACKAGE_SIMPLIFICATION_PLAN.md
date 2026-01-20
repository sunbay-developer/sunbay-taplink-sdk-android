# LAN包简化方案

## 当前问题

基于实际需求分析，当前的接口设计存在过度工程化问题：

1. **ConnectionManager接口** - 目前没有使用不同WebSocket库的需求
2. **ServiceDiscoveryManager接口** - 目前没有多种服务发现实现的需求  
3. **HeartbeatManager接口** - 心跳逻辑简单，不需要抽象

## 简化原则

1. **YAGNI**: 你不会需要它 - 不要为未来可能的需求过度设计
2. **KISS**: 保持简单愚蠢 - 简单的设计更容易维护
3. **实用主义**: 解决当前的实际问题，而不是假想的问题

## 简化方案

### 方案：直接使用具体类

```
lan/
├── client/
│   └── WebSocketClientKernel.kt          # 主要内核类
├── connection/
│   └── WebSocketConnectionManager.kt     # 具体的连接管理类
├── discovery/
│   └── ServiceDiscoveryManager.kt        # 具体的服务发现类
├── heartbeat/
│   └── HeartbeatManager.kt               # 具体的心跳管理类
└── model/
    └── ServiceInfo.kt                     # 数据模型
```

### 简化后的类设计

#### 1. WebSocketConnectionManager (具体类)
```kotlin
class WebSocketConnectionManager {
    private var client: WebSocketClient? = null
    private var messageListener: MessageListener? = null
    private var connectionListener: ConnectionListener? = null
    
    suspend fun connect(uri: URI): ConnectionResult { ... }
    fun disconnect() { ... }
    fun send(data: ByteArray): Boolean { ... }
    fun setMessageListener(listener: MessageListener?) { ... }
    fun setConnectionListener(listener: ConnectionListener?) { ... }
    
    // 内部类和接口
    sealed class ConnectionResult { ... }
    interface MessageListener { ... }
    interface ConnectionListener { ... }
}
```

#### 2. ServiceDiscoveryManager (具体类)
```kotlin
class ServiceDiscoveryManager(private val context: Context) {
    private var nsdManager: NsdManager? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    
    suspend fun discoverServices(serviceType: String): List<ServiceInfo> { ... }
    fun startServiceMonitoring(serviceType: String, listener: ServiceChangeListener) { ... }
    fun stopServiceMonitoring() { ... }
    
    interface ServiceChangeListener { ... }
}
```

#### 3. HeartbeatManager (具体类)
```kotlin
class HeartbeatManager {
    private var config: HeartbeatConfig = HeartbeatConfig()
    private var heartbeatJob: Job? = null
    private var sender: HeartbeatSender? = null
    
    fun startHeartbeat(config: HeartbeatConfig, sender: HeartbeatSender) { ... }
    fun stopHeartbeat() { ... }
    fun handleHeartbeatResponse() { ... }
    
    data class HeartbeatConfig(...)
    interface HeartbeatSender { ... }
}
```

#### 4. WebSocketClientKernel (简化)
```kotlin
class WebSocketClientKernel : AsyncServiceKernel {
    // 直接使用具体类
    private val connectionManager = WebSocketConnectionManager()
    private val serviceDiscovery = ServiceDiscoveryManager(context)
    private val heartbeatManager = HeartbeatManager()
    
    // 实现AsyncServiceKernel的抽象方法
    override fun performConnect(...) { ... }
    override fun performSendData(...) { ... }
    override fun performDisconnect() { ... }
}
```

## 简化优势

### 1. 代码更简洁
- 移除不必要的接口定义
- 减少抽象层次
- 代码更直观易懂

### 2. 维护成本更低
- 没有接口-实现的映射关系
- 修改功能时只需要改一个地方
- 减少了文件数量

### 3. 测试仍然可行
```kotlin
// 可以通过构造函数注入进行测试
class WebSocketClientKernel(
    private val connectionManager: WebSocketConnectionManager = WebSocketConnectionManager(),
    private val serviceDiscovery: ServiceDiscoveryManager = ServiceDiscoveryManager(context),
    private val heartbeatManager: HeartbeatManager = HeartbeatManager()
) : AsyncServiceKernel {
    // ...
}

// 测试时注入Mock对象
@Test
fun testConnect() {
    val mockConnectionManager = mockk<WebSocketConnectionManager>()
    val kernel = WebSocketClientKernel(connectionManager = mockConnectionManager)
    // ...
}
```

### 4. 扩展性保留
如果未来真的需要多实现，可以：
1. 提取接口
2. 重构现有代码
3. 保持向后兼容

## 实施步骤

### 第1步：重构目录结构
```bash
# 移动文件到新的结构
mv client/connection/impl/DefaultConnectionManager.kt connection/WebSocketConnectionManager.kt
mv client/discovery/ServiceDiscoveryManager.kt discovery/ServiceDiscoveryManager.kt  
mv client/heartbeat/HeartbeatManager.kt heartbeat/HeartbeatManager.kt
```

### 第2步：移除接口定义
- 删除ConnectionManager接口
- 删除ServiceDiscoveryManager接口
- 删除HeartbeatManager接口

### 第3步：更新实现类
- 移除接口实现声明
- 调整包名和引用
- 简化类结构

### 第4步：更新WebSocketClientKernel
- 直接使用具体类
- 移除接口引用
- 简化构造和初始化

### 第5步：测试验证
- 确保功能正常
- 验证测试用例
- 检查编译错误

## 预期结果

### 文件结构
```
lan/
├── client/
│   └── WebSocketClientKernel.kt
├── connection/
│   └── WebSocketConnectionManager.kt
├── discovery/
│   └── ServiceDiscoveryManager.kt
├── heartbeat/
│   └── HeartbeatManager.kt
└── model/
    └── ServiceInfo.kt
```

### 代码量对比
- **简化前**: 接口文件 + 实现文件 = 约800行
- **简化后**: 只有实现文件 = 约500行
- **减少**: 约40%的代码量

### 复杂度对比
- **简化前**: 3个接口 + 3个实现 + 1个内核 = 7个概念
- **简化后**: 3个管理器 + 1个内核 = 4个概念
- **减少**: 约43%的概念复杂度

## 总结

这个简化方案遵循了YAGNI和KISS原则，移除了不必要的抽象层，使代码更加简洁和易维护。同时保留了模块化的结构，如果未来真的需要多实现，可以很容易地重构为接口模式。

这是一个更加实用和务实的设计。