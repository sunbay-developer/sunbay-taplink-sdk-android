# 网络连通性检查器 - 单客户端服务器支持

## 问题背景

在使用 `NetworkConnectivityChecker` 进行网络连通性检查时，如果服务器端只支持单个客户端连接，TCP连接检查会导致现有的WebSocket连接被断开。

## 问题分析

### 原始实现问题
```kotlin
// 原始实现会创建新的TCP连接进行检查
val tcpReachable = checkTcpConnectivity(host, port, timeoutMs)
```

当 `checkTcpConnectivity` 方法执行时：
1. 创建新的 `Socket` 连接到服务器
2. 如果服务器只支持单客户端，会断开现有的WebSocket连接
3. 导致心跳检查反而破坏了正常的通信连接

### 影响场景
- 服务器端只支持一个客户端连接
- 心跳检查时触发TCP连通性检查
- 现有WebSocket连接被意外断开

## 解决方案

### 1. 默认使用Ping检查
修改 `isHostReachable` 方法，默认只使用ping检查：

```kotlin
suspend fun isHostReachable(
    host: String, 
    port: Int, 
    timeoutMs: Int = DEFAULT_TIMEOUT_MS
): Boolean {
    // 仅使用ping检查，避免TCP连接干扰现有连接
    val pingReachable = checkPingConnectivity(host, timeoutMs)
    return pingReachable
}
```

### 2. 提供TCP检查的可选方法
为需要TCP检查的场景提供专门的方法：

```kotlin
suspend fun isHostReachableWithTcp(
    host: String, 
    port: Int, 
    timeoutMs: Int = DEFAULT_TIMEOUT_MS
): Boolean {
    // 警告：此方法会创建TCP连接，可能会干扰现有连接！
    // 仅在确认服务器支持多客户端连接时使用
}
```

### 3. 更新详细检查方法
同样提供两个版本的详细检查：

```kotlin
// 默认版本：仅使用ping
suspend fun checkConnectivityDetailed(...)

// TCP版本：包含TCP检查（可能干扰现有连接）
suspend fun checkConnectivityDetailedWithTcp(...)
```

## 使用指南

### 推荐用法（单客户端服务器）
```kotlin
// 心跳检查时使用默认方法（仅ping）
val isReachable = connectivityChecker.isHostReachable(
    host = uri.host,
    port = uri.port,
    timeoutMs = 2000
)
```

### 多客户端服务器用法
```kotlin
// 如果确认服务器支持多客户端，可以使用TCP检查
val isReachable = connectivityChecker.isHostReachableWithTcp(
    host = uri.host,
    port = uri.port,
    timeoutMs = 2000
)
```

## 技术细节

### Ping检查的优势
1. **不会创建TCP连接**：避免干扰现有连接
2. **检查网络层连通性**：能够检测到网络断开
3. **轻量级**：资源消耗较少

### Ping检查的局限性
1. **无法检查端口状态**：只能检查主机是否可达
2. **可能被防火墙阻止**：某些网络环境可能禁用ping
3. **精度较低**：无法确认具体服务是否正常

### TCP检查的优势
1. **精确检查端口**：能够确认特定端口是否开放
2. **模拟真实连接**：更接近实际的连接场景
3. **可靠性高**：不受ping策略影响

### TCP检查的风险
1. **可能断开现有连接**：单客户端服务器会断开当前连接
2. **资源消耗较大**：需要建立完整的TCP连接
3. **可能触发服务器日志**：每次检查都会在服务器留下连接记录

## 最佳实践

### 1. 根据服务器类型选择方法
```kotlin
class ConnectivityCheckStrategy {
    fun chooseCheckMethod(serverSupportsMultipleClients: Boolean): CheckMethod {
        return if (serverSupportsMultipleClients) {
            CheckMethod.TCP_AND_PING
        } else {
            CheckMethod.PING_ONLY
        }
    }
}
```

### 2. 配置化检查策略
```kotlin
data class ConnectivityConfig(
    val useTcpCheck: Boolean = false,
    val pingTimeout: Int = 2000,
    val tcpTimeout: Int = 3000
)
```

### 3. 错误处理和降级
```kotlin
suspend fun smartConnectivityCheck(host: String, port: Int): Boolean {
    return try {
        // 首先尝试ping检查
        connectivityChecker.isHostReachable(host, port)
    } catch (e: Exception) {
        // ping失败时的降级策略
        LogUtil.w(TAG, "Ping check failed, assuming network is available: ${e.message}")
        true // 假设网络可用，让WebSocket自己检测
    }
}
```

## 修改记录

| 日期 | 修改内容 | 影响 |
|------|----------|------|
| 2025-01-08 | 默认方法改为仅使用ping检查 | 避免干扰单客户端服务器连接 |
| 2025-01-08 | 添加带TCP检查的可选方法 | 为多客户端服务器提供更精确的检查 |
| 2025-01-08 | 更新方法注释和文档 | 明确使用场景和风险 |

## 相关文件

- `NetworkConnectivityChecker.kt` - 网络连通性检查器实现
- `ConnectionManager.kt` - WebSocket连接管理器
- `HeartbeatManager.kt` - 心跳管理器（使用连通性检查）

## 注意事项

1. **向后兼容性**：默认方法行为已改变，从TCP+Ping改为仅Ping
2. **性能影响**：Ping检查通常比TCP检查更快
3. **准确性权衡**：为了避免连接干扰，牺牲了一定的检查精度
4. **配置建议**：建议根据实际的服务器环境选择合适的检查方法

## 总结

通过将默认的网络连通性检查改为仅使用ping，我们解决了单客户端服务器连接被意外断开的问题。同时保留了TCP检查的选项，为不同的使用场景提供了灵活性。这种设计在保证功能完整性的同时，最大程度地避免了对现有连接的干扰。