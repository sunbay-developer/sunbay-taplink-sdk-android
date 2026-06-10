# WebSocketClientKernel 正确重构分析

## 当前问题重新分析

### 继承关系错误
当前WebSocketClientKernel的继承关系：
```kotlin
class WebSocketClientKernel : BaseServiceKernel()
```

**正确的继承关系应该是**：
```kotlin
class WebSocketClientKernel : AsyncServiceKernel(appId, appSecretKey)
```

### AsyncServiceKernel提供的基础功能

通过分析AsyncServiceKernel，它已经提供了：

1. **协程管理**
   ```kotlin
   protected val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
   ```

2. **并发控制**
   ```kotlin
   protected val sendMutex = Mutex()
   ```

3. **连接回调管理**
   ```kotlin
   protected var currentConnectionCallback: ConnectionCallback? = null
   ```

4. **通用连接流程**
   ```kotlin
   override fun connect(protocol: String, connectionCallback: ConnectionCallback)
   override fun sendData(traceId: String, data: ByteArray, callback: InnerCallback?)
   override fun disconnect()
   ```

5. **协议解析**
   ```kotlin
   val parseResult = ProtocolManager.parseProtocol(protocol)
   ```

6. **抽象方法定义**
   ```kotlin
   protected abstract fun getServiceType(): String
   protected abstract fun getExpectedProtocolType(): String
   protected abstract fun isValidProtocolType(parseResult: ProtocolParseResult): Boolean
   protected abstract fun performConnect(parseResult: ProtocolParseResult, connectionCallback: ConnectionCallback)
   protected abstract fun performSendData(traceId: String, data: ByteArray, callback: InnerCallback?)
   protected abstract fun performDisconnect()
   ```

## 重构策略调整

### 策略1：修正继承关系 + 管理器模式
1. 让WebSocketClientKernel正确继承AsyncServiceKernel
2. 实现AsyncServiceKernel要求的抽象方法
3. 将复杂逻辑分解到管理器中
4. 在抽象方法实现中使用管理器

### 策略2：保持现有结构 + 内部重构
1. 保持当前的继承关系不变
2. 在类内部使用管理器模式重构
3. 逐步迁移功能到管理器

## 推荐方案：策略1

### 优势
1. **符合架构设计**：正确使用AsyncServiceKernel的设计意图
2. **减少重复代码**：利用AsyncServiceKernel已有的功能
3. **统一架构风格**：与其他ServiceKernel保持一致
4. **更好的扩展性**：基于正确的基础架构

### 重构后的结构

```kotlin
class WebSocketClientKernel(
    appId: String,
    appSecretKey: String,
    private val context: Context
) : AsyncServiceKernel(appId, appSecretKey) {

    // 管理器组件
    private val connectionManager: WebSocketConnectionManager
    private val serviceDiscoveryManager: ServiceDiscoveryManager
    private val heartbeatManager: HeartbeatManager
    
    // 实现AsyncServiceKernel的抽象方法
    override fun getServiceType(): String = "WebSocket"
    override fun getExpectedProtocolType(): String = "websocket or lan protocol"
    override fun isValidProtocolType(parseResult: ProtocolParseResult): Boolean
    override fun performConnect(parseResult: ProtocolParseResult, connectionCallback: ConnectionCallback)
    override fun performSendData(traceId: String, data: ByteArray, callback: InnerCallback?)
    override fun performDisconnect()
}
```

## 具体实施步骤

### 第1步：修正继承关系
```kotlin
// 修改类声明
class WebSocketClientKernel(
    appId: String,
    appSecretKey: String,
    private val context: Context
) : AsyncServiceKernel(appId, appSecretKey) // 正确继承
```

### 第2步：移除重复代码
删除AsyncServiceKernel已提供的功能：
- ❌ 删除：`scope`（AsyncServiceKernel已提供）
- ❌ 删除：`sendMutex`（AsyncServiceKernel已提供）
- ❌ 删除：`currentConnectionCallback`（AsyncServiceKernel已提供）
- ❌ 删除：`connect()`方法（AsyncServiceKernel已实现）
- ❌ 删除：`sendData()`方法（AsyncServiceKernel已实现）
- ❌ 删除：`disconnect()`方法（AsyncServiceKernel已实现）

### 第3步：实现抽象方法
```kotlin
override fun getServiceType(): String = "WebSocket"

override fun getExpectedProtocolType(): String = "websocket or lan protocol"

override fun isValidProtocolType(parseResult: ProtocolParseResult): Boolean {
    return parseResult is ProtocolParseResult.LanProtocol
}

override fun performConnect(parseResult: ProtocolParseResult, connectionCallback: ConnectionCallback) {
    // 使用管理器实现连接逻辑
}

override fun performSendData(traceId: String, data: ByteArray, callback: InnerCallback?) {
    // 使用管理器实现发送逻辑
}

override fun performDisconnect() {
    // 使用管理器实现断开逻辑
}
```

### 第4步：集成管理器
```kotlin
private val connectionManager = DefaultWebSocketConnectionManager()
private val serviceDiscoveryManager = DefaultServiceDiscoveryManager(context)
private val heartbeatManager = DefaultHeartbeatManager()

override fun performConnect(parseResult: ProtocolParseResult, connectionCallback: ConnectionCallback) {
    launchInScope {
        val lanResult = parseResult as ProtocolParseResult.LanProtocol
        val uri = ProtocolManager.lanProtocolToWebSocketUri(lanResult.originalProtocol)
        
        if (uri != null) {
            val result = connectionManager.connect(uri)
            when (result) {
                is WebSocketConnectionManager.ConnectionResult.Success -> {
                    notifyConnectionSuccess()
                }
                is WebSocketConnectionManager.ConnectionResult.Failure -> {
                    // 尝试服务发现
                    tryServiceDiscovery(connectionCallback)
                }
            }
        } else {
            notifyConnectionError("Invalid URI")
        }
    }
}
```

## 预期收益

### 1. 代码减少
- 原来：1000+ 行
- 重构后：约400-500行（移除AsyncServiceKernel重复功能）

### 2. 架构一致性
- 与LocalServiceKernel等其他内核保持一致的架构
- 正确利用AsyncServiceKernel的设计

### 3. 维护性提升
- 基础功能由AsyncServiceKernel统一管理
- 特定功能由管理器分别处理
- 职责更加清晰

### 4. 扩展性增强
- 可以利用AsyncServiceKernel的所有辅助方法
- 管理器可以独立扩展和优化

## 风险评估

### 1. 兼容性风险
- **风险**：修改继承关系可能影响现有调用
- **缓解**：保持公共接口不变，内部重构

### 2. 行为变化风险
- **风险**：AsyncServiceKernel的行为可能与原实现不同
- **缓解**：详细测试，确保行为一致性

### 3. 依赖风险
- **风险**：依赖AsyncServiceKernel的稳定性
- **缓解**：AsyncServiceKernel是成熟的基础类

## 总结

正确的重构应该：
1. **修正继承关系**：继承AsyncServiceKernel而不是BaseServiceKernel
2. **利用基础功能**：充分利用AsyncServiceKernel提供的通用功能
3. **管理器模式**：将复杂逻辑分解到专门的管理器中
4. **实现抽象方法**：在抽象方法中使用管理器完成具体功能

这样的重构既符合架构设计，又能显著降低代码复杂度，是更加合理的方案。