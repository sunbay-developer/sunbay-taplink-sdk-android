# 连接超时时间分析

## 问题：10秒超时是否只是连接建立的时间？

**答案：是的，10秒只用于 WebSocket 连接建立，不包括 INIT 命令响应。**

## 超时时间分解

### 1. WebSocket 连接建立超时：10秒
**位置**: `ConnectionManager.connect()` 第 27 行
```kotlin
private const val CONNECTION_TIMEOUT_MS = 10_000L
```

**用途**:
- 等待 WebSocket 连接建立（TCP 连接 + TLS 握手 + WebSocket 握手）
- 代码逻辑：
```kotlin
val connectionResult = withTimeoutOrNull(CONNECTION_TIMEOUT_MS) {
    while (!isConnected.get() && client?.isOpen != true) {
        delay(100)
        // 检查连接状态
    }
    true
}
```

**包含的步骤**:
1. TCP 连接建立
2. TLS 握手（如果使用 wss://）
3. WebSocket 握手
4. 等待 `onOpen()` 回调触发，设置 `isConnected = true`

**不包含**:
- ❌ 服务发现时间
- ❌ INIT 命令发送和响应时间
- ❌ 其他业务逻辑

### 2. INIT 命令超时：60秒（默认）
**位置**: `ConnectionManager.sendInitCommand()` 第 645 行
```kotlin
val added = callbackManager.addInitCallback(
    basicRequest.traceId, initCallback,
    app2app = getConnectionMode() == ConnectionMode.APP_TO_APP.name,
    initRequest.requestTimeout  // 如果为 null，使用默认 60 秒
)
```

**默认超时**: `defaultTimeoutMillis = 60 * 1000L` (60秒)

**用途**:
- 等待 INIT 命令的响应
- 从 WebSocket 连接建立成功后开始计时

### 3. 服务发现超时：30秒
**位置**: `ServiceDiscoveryManager.discoverServices()` 第 31 行
```kotlin
private const val DISCOVERY_TIMEOUT_MS = 30_000L
```

**用途**:
- 当直接连接失败时，尝试通过 mDNS 服务发现找到设备
- 只在直接连接失败时触发

## 完整连接流程时间线

### 场景 1: 直接连接成功（最佳情况）
```
时间轴：
0s  ──> 开始连接
     ├─> TCP 连接建立（~100-500ms）
     ├─> TLS 握手（wss://，~200-1000ms）
     ├─> WebSocket 握手（~100-300ms）
     └─> onOpen() 回调（~10ms）
     
1s  ──> WebSocket 连接建立成功 ✅
     └─> 发送 INIT 命令
     
1-2s ──> 等待 INIT 响应
     └─> 收到 INIT 响应 ✅
     
总计：~1-2秒（如果网络良好）
```

### 场景 2: 直接连接失败，触发服务发现
```
时间轴：
0s  ──> 开始连接
     └─> 尝试直接连接（10秒超时）
     
10s ──> 直接连接超时 ❌
     └─> 触发服务发现（30秒超时）
     
10-40s ──> 服务发现中...
     └─> 找到服务 ✅
     
40s ──> 尝试连接到发现的服务（10秒超时）
     └─> 连接成功 ✅
     └─> 发送 INIT 命令
     
40-100s ──> 等待 INIT 响应（60秒超时）
     └─> 收到 INIT 响应 ✅
     
总计：可能超过 100 秒（最坏情况）
```

### 场景 3: 网络延迟较高
```
时间轴：
0s  ──> 开始连接
     ├─> TCP 连接建立（~2-5秒，网络慢）
     ├─> TLS 握手（~3-8秒，网络慢）
     ├─> WebSocket 握手（~1-2秒）
     └─> onOpen() 回调
     
10s ──> 如果还没建立，超时 ❌
     └─> 触发服务发现
     
总计：可能超过 10 秒，导致连接失败
```

## 问题分析

### 10秒是否足够？

**对于 WebSocket 连接建立来说，10秒可能不够的情况：**

1. **网络延迟高**：
   - LAN 环境下，如果网络质量差，TCP 连接可能需要 2-5 秒
   - TLS 握手可能需要 3-8 秒（特别是首次连接）
   - 总计可能超过 10 秒

2. **设备性能低**：
   - 如果服务器端设备性能较低，处理 TLS 握手可能较慢
   - WebSocket 握手处理可能延迟

3. **网络拥塞**：
   - 如果网络拥塞，TCP 连接建立可能超时
   - 重传机制可能导致总时间超过 10 秒

### 建议

**选项 1: 增加 WebSocket 连接超时时间**
```kotlin
private const val CONNECTION_TIMEOUT_MS = 30_000L  // 增加到 30 秒
```

**优点**:
- 给网络延迟和设备处理留出足够时间
- 减少因超时导致的连接失败

**缺点**:
- 如果连接真的失败，需要等待更长时间才能知道结果

**选项 2: 根据连接类型动态调整超时时间**
```kotlin
private fun getConnectionTimeout(secure: Boolean): Long {
    return if (secure) {
        30_000L  // wss:// 需要 TLS 握手，给更多时间
    } else {
        15_000L  // ws:// 不需要 TLS，时间可以短一些
    }
}
```

**选项 3: 保持 10 秒，但优化错误处理**
- 如果直接连接超时，立即触发服务发现
- 服务发现成功后，使用更长的超时时间重试

## 总结

1. **10秒确实只是 WebSocket 连接建立的时间**，不包括 INIT 命令
2. **INIT 命令有单独的超时时间**（60秒默认）
3. **服务发现有单独的超时时间**（30秒）
4. **在 LAN 环境下，10秒可能不够**，特别是：
   - 使用 wss://（需要 TLS 握手）
   - 网络延迟较高
   - 设备性能较低
5. **建议增加到 30 秒**，或者根据连接类型动态调整
