# LAN 模式服务监听功能

## 功能说明

在 LAN 模式下，WebSocketClientKernel 现在支持监听服务端 IP 或端口的变化，并自动重新连接到新地址。

## 实现原理

### 1. 服务信息保存

连接成功后，保存当前连接的服务信息：

```kotlin
private fun saveCurrentServiceInfo(uri: URI) {
    currentServiceInfo = ServiceInfo(
        name = "taplink-service",
        host = uri.host,
        port = uri.port
    )
}
```

### 2. 启动服务监听

连接成功后，启动 mDNS 服务监听：

```kotlin
private fun startServiceMonitoring() {
    // 使用 NsdManager 持续监听服务变化
    nsdManager.discoverServices(NSD_SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, monitoringListener)
}
```

### 3. 检测地址变化

当发现服务时，检查是否是当前连接的服务，并比较地址：

```kotlin
private fun checkAndHandleServiceChange(newHost: String, newPort: Int) {
    val currentService = currentServiceInfo
    
    // 检查 IP 或端口是否发生变化
    if (currentService.host != newHost || currentService.port != newPort) {
        LogUtil.i(TAG, "Service address changed: ${currentService.host}:${currentService.port} -> $newHost:$newPort")
        
        // 重新连接到新地址
        reconnectToNewAddress(newHost, newPort)
    }
}
```

### 4. 自动重新连接

发现地址变化后，自动重新连接：

```kotlin
private fun reconnectToNewAddress(newHost: String, newPort: Int) {
    // 1. 断开当前连接
    client?.close()
    
    // 2. 构建新的 URI
    val newUri = URI("ws://$newHost:$newPort")
    
    // 3. 连接到新地址
    connectToUri(newUri, currentConnectionCallback)
}
```

## 使用场景

### 1. 服务端 IP 变化

**场景：** 服务端设备的 IP 地址发生变化（如 DHCP 重新分配）

**处理：**
- 检测到新的 IP 地址
- 自动断开旧连接
- 连接到新的 IP 地址

**日志示例：**
```
I/WebSocketClientKernel: Service address changed: 192.168.1.100:8080 -> 192.168.1.101:8080
I/WebSocketClientKernel: Reconnecting to new address: 192.168.1.101:8080
D/WebSocketClientKernel: Attempting to connect to new address: ws://192.168.1.101:8080
```

### 2. 服务端端口变化

**场景：** 服务端更改了监听端口

**处理：**
- 检测到新的端口号
- 自动重新连接到新端口

**日志示例：**
```
I/WebSocketClientKernel: Service address changed: 192.168.1.100:8080 -> 192.168.1.100:9090
I/WebSocketClientKernel: Reconnecting to new address: 192.168.1.100:9090
```

### 3. 服务端重启

**场景：** 服务端重启后可能使用不同的地址

**处理：**
- 通过 mDNS 重新发现服务
- 自动连接到新地址

## 配置选项

### 启用/禁用监听

监听功能在连接成功后自动启用，断开连接时自动停止。

### 监听间隔

使用 mDNS 的实时通知机制，无需轮询。

## 生命周期管理

### 启动时机

```kotlin
override fun onOpen(handshakedata: ServerHandshake) {
    // 保存当前连接的服务信息
    saveCurrentServiceInfo(uri)
    
    // 启动服务监听
    startServiceMonitoring()
    
    // 其他初始化...
}
```

### 停止时机

```kotlin
override fun onClose(code: Int, reason: String, remote: Boolean) {
    // 停止服务监听
    stopServiceMonitoring()
    
    // 其他清理...
}

override fun disconnect() {
    // 清理资源时也会停止监听
    cleanupResources()
}
```

## 错误处理

### 1. 监听启动失败

```kotlin
override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
    LogUtil.e(TAG, "Service monitoring start failed: $serviceType, errorCode: $errorCode")
    isServiceMonitoringEnabled = false
}
```

### 2. 重连失败

如果重连到新地址失败，会通过正常的错误处理机制通知上层，由 SDK 层的 ReconnectManager 处理重连。

### 3. 服务解析失败

```kotlin
override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
    LogUtil.e(TAG, "Service monitoring resolve failed: ${serviceInfo.serviceName}")
    // 继续监听其他服务
}
```

## 性能考虑

### 1. 资源使用

- mDNS 监听使用系统级服务，资源消耗较低
- 只在连接状态下启用监听
- 断开连接时立即停止监听

### 2. 网络开销

- mDNS 使用多播，网络开销很小
- 只有在检测到变化时才进行重连

### 3. 电池影响

- 使用系统的 mDNS 服务，对电池影响最小
- 避免了轮询检测的电池消耗

## 调试信息

### 关键日志

```
D/WebSocketClientKernel: Saved current service info: ServiceInfo(name=taplink-service, host=192.168.1.100, port=8080)
D/WebSocketClientKernel: Starting service monitoring for IP/port changes
D/WebSocketClientKernel: Service monitoring started: _taplink._tcp
D/WebSocketClientKernel: Service monitoring found: TaplinkService
D/WebSocketClientKernel: Service monitoring resolved: TaplinkService, 192.168.1.101:8080
I/WebSocketClientKernel: Service address changed: 192.168.1.100:8080 -> 192.168.1.101:8080
I/WebSocketClientKernel: Reconnecting to new address: 192.168.1.101:8080
```

### 状态检查

可以通过日志查看当前状态：

- `isServiceMonitoringEnabled` - 监听是否启用
- `currentServiceInfo` - 当前连接的服务信息
- `discoveredServices` - 发现的服务列表

## 兼容性

### Android 版本

- 支持 Android 4.1+ (API 16+)
- 使用标准的 NsdManager API

### 网络环境

- 需要支持 mDNS 的网络环境
- 局域网内的设备发现

## 测试建议

### 1. IP 变化测试

```bash
# 在服务端设备上更改 IP 地址
sudo ifconfig eth0 192.168.1.101

# 观察客户端日志，应该看到自动重连
```

### 2. 端口变化测试

```bash
# 重启服务端应用，使用不同端口
./taplink-server --port 9090

# 观察客户端是否自动连接到新端口
```

### 3. 服务重启测试

```bash
# 重启服务端应用
sudo systemctl restart taplink-service

# 观察客户端的重连行为
```

## 相关文件

- `lib_taplink_communication/src/main/java/com/sunmi/tapro/taplink/communication/lan/WebSocketClientKernel.kt` - 主要实现

---

**文档版本**: 1.0.0  
**更新日期**: 2025-12-19  
**作者**: TaPro Team