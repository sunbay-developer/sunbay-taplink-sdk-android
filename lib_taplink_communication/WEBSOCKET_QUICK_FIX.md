# WebSocket ECONNREFUSED 快速解决方案

## 问题

```
WebSocket error: failed to connect to /10.14.78.11 (port 8443): ECONNREFUSED (Connection refused)
```

## 快速诊断

运行诊断脚本：

```bash
cd lib_taplink_communication
./diagnose_websocket.sh 10.14.78.11 8443
```

## 最可能的原因和解决方案

### 1. 服务端未启动 ⭐ 最常见

**检查：**
```bash
# 检查端口是否开放
nc -zv 10.14.78.11 8443
# 或者
telnet 10.14.78.11 8443
```

**解决：**
- 启动服务端应用
- 确认服务端 WebSocket 服务正在运行

---

### 2. 端口不正确

**检查：**
```bash
# 扫描常用端口
nmap -p 8080,8443,9090,3000 10.14.78.11
```

**解决：**
如果发现服务在其他端口（如 8080），修改客户端连接：

```kotlin
// 原来
val protocol = "lan://10.14.78.11:8443"

// 修改为实际端口
val protocol = "lan://10.14.78.11:8080"
```

---

### 3. IP 地址错误

**检查：**
```bash
# 确认目标设备的实际 IP
ping 10.14.78.11
```

**解决：**
使用正确的 IP 地址，或者使用 mDNS 自动发现：

```kotlin
// 使用自动发现
val protocol = "lan://auto"
```

---

## 立即可尝试的解决方案

### 方案 1: 使用 mDNS 自动发现

```kotlin
// 不指定具体地址，让系统自动发现
val protocol = "lan://auto"
TaplinkSDK.connect(protocol, listener)
```

**优势：**
- 不需要硬编码 IP 和端口
- 自动发现局域网内的服务
- WebSocketClientKernel 已支持此功能

---

### 方案 2: 尝试常用端口

```kotlin
val commonPorts = listOf(8080, 8443, 9090, 3000)
val targetIp = "10.14.78.11"

for (port in commonPorts) {
    val protocol = "lan://$targetIp:$port"
    // 尝试连接
}
```

---

### 方案 3: 检查服务端状态

在服务端设备上：

```bash
# 检查进程
ps aux | grep websocket
ps aux | grep taplink

# 检查端口监听
netstat -tlnp | grep 8443
ss -tlnp | grep 8443

# 查看日志
adb logcat | grep -E "WebSocket|Server"
```

---

## 服务端检查清单

如果你能访问服务端，请确认：

### 1. WebSocket 服务已启动

```kotlin
// 服务端应该有类似代码
val server = WebSocketServer(InetSocketAddress("0.0.0.0", 8443))
server.start()
Log.d(TAG, "WebSocket server started on port 8443")
```

### 2. 监听所有接口

```kotlin
// ✅ 正确：监听所有接口
InetSocketAddress("0.0.0.0", 8443)

// ❌ 错误：只监听本地
InetSocketAddress("127.0.0.1", 8443)
```

### 3. 检查日志

查找以下日志：
- ✅ `WebSocket server started on port 8443`
- ❌ `Failed to bind port 8443`
- ❌ `Address already in use`

---

## 客户端代码优化建议

### 添加端口扫描

```kotlin
private suspend fun findAvailablePort(host: String): Int? {
    val commonPorts = listOf(8080, 8443, 9090, 3000, 8000)
    
    for (port in commonPorts) {
        try {
            val socket = Socket()
            socket.connect(InetSocketAddress(host, port), 3000)
            socket.close()
            return port
        } catch (e: Exception) {
            // 端口不可用
        }
    }
    return null
}
```

### 改进错误提示

```kotlin
override fun onError(ex: Exception) {
    val errorMsg = when {
        ex.message?.contains("ECONNREFUSED") == true -> {
            "服务端未启动或端口错误 - ${currentUri?.host}:${currentUri?.port}"
        }
        else -> "WebSocket error: ${ex.message}"
    }
    
    LogUtil.e(TAG, errorMsg)
    // 自动尝试 mDNS 发现
    if (currentInnerConnectionStatus == InnerConnectionStatus.CONNECTING) {
        LogUtil.d(TAG, "尝试 mDNS 自动发现...")
        discoverAndConnect(connectionCallback)
    }
}
```

---

## 总结

**ECONNREFUSED 错误 = 服务端没有在指定地址监听**

**解决优先级：**

1. **使用 mDNS 自动发现** - 最简单的解决方案
2. **确认服务端已启动** - 检查目标设备
3. **扫描端口** - 找到实际监听的端口
4. **检查网络** - 确认 IP 地址正确

**推荐做法：**
- 优先使用 `lan://auto` 自动发现
- 添加端口扫描功能
- 改进错误提示信息

---

**快速测试命令：**

```bash
# 1. 运行诊断
./diagnose_websocket.sh 10.14.78.11 8443

# 2. 手动检查
ping 10.14.78.11
nc -zv 10.14.78.11 8443

# 3. 扫描端口
nmap -p 8080,8443,9090,3000 10.14.78.11
```

---

**文档版本**: 1.0.0  
**更新日期**: 2025-12-19  
**作者**: TaPro Team