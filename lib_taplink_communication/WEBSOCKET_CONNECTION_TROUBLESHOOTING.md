# WebSocket 连接问题排查指南

## 问题现象

```
WebSocket error: failed to connect to /10.14.78.11 (port 8443) from /:: (port 42846): connect failed: ECONNREFUSED (Connection refused)
```

## 错误分析

### ECONNREFUSED 的含义

- **Connection refused** 表示目标地址 `10.14.78.11:8443` 没有服务在监听
- 这不是网络不通的问题，而是目标端口没有服务

### 可能的原因

1. **服务端未启动** - 目标设备上的 WebSocket 服务没有运行
2. **端口不正确** - 服务端监听的端口不是 8443
3. **IP 地址错误** - 目标设备的 IP 不是 10.14.78.11
4. **防火墙阻止** - 目标设备防火墙阻止了连接
5. **服务端崩溃** - 服务端启动后崩溃了

---

## 诊断步骤

### 1. 检查目标设备网络连通性

```bash
# 检查网络是否通
ping 10.14.78.11

# 检查端口是否开放
telnet 10.14.78.11 8443
# 或者使用 nc (netcat)
nc -zv 10.14.78.11 8443
```

**预期结果：**
- `ping` 成功 → 网络通
- `telnet` 或 `nc` 成功 → 端口开放
- `telnet` 失败 → 端口未监听（ECONNREFUSED）

---

### 2. 检查服务端状态

在目标设备（10.14.78.11）上执行：

```bash
# 检查端口监听状态
netstat -tlnp | grep 8443
# 或者使用 ss
ss -tlnp | grep 8443

# 检查进程状态
ps aux | grep taplink
ps aux | grep websocket
```

**预期结果：**
- 如果有输出 → 服务正在监听
- 如果没有输出 → 服务未启动或端口不对

---

### 3. 检查服务端日志

```bash
# 查看服务端日志
adb logcat | grep -E "WebSocket|Server|taplink"

# 或者查看系统日志
journalctl -f | grep taplink
```

**查找关键信息：**
- ✅ `WebSocket server started on port 8443`
- ✅ `Server listening on 0.0.0.0:8443`
- ❌ `Failed to bind port 8443`
- ❌ `Address already in use`

---

### 4. 使用 nmap 扫描端口

```bash
# 扫描目标设备的常用端口
nmap -p 8080,8443,9090,3000 10.14.78.11

# 扫描所有开放端口
nmap -p- 10.14.78.11
```

**查找 WebSocket 服务可能使用的端口：**
- 8080 (HTTP)
- 8443 (HTTPS)
- 9090 (常用 WebSocket 端口)
- 3000 (开发服务器常用端口)

---

## 解决方案

### 方案 1: 确认服务端配置

**检查服务端配置文件或代码：**

```kotlin
// 服务端应该有类似的代码
val server = WebSocketServer(InetSocketAddress("0.0.0.0", 8443))
server.start()
Log.d(TAG, "WebSocket server started on port 8443")
```

**确认：**
- [ ] 服务端确实在监听 8443 端口
- [ ] 服务端绑定的是 `0.0.0.0`（所有接口）而不是 `127.0.0.1`（仅本地）
- [ ] 服务端没有崩溃

---

### 方案 2: 修改客户端连接地址

如果服务端在不同的端口，修改客户端连接协议：

```kotlin
// 原来的协议
val protocol = "lan://10.14.78.11:8443"

// 尝试其他端口
val protocol = "lan://10.14.78.11:8080"  // HTTP 端口
val protocol = "lan://10.14.78.11:9090"  // 其他端口
```

---

### 方案 3: 使用 mDNS 自动发现

让客户端自动发现服务端，而不是硬编码 IP 和端口：

```kotlin
// 使用 mDNS 发现
val protocol = "lan://auto"  // 自动发现

// 或者只指定 IP，让系统扫描端口
val protocol = "lan://10.14.78.11"  // 自动扫描端口
```

**WebSocketClientKernel 已经支持 mDNS 发现：**

```kotlin
// 直接连接失败时，会自动启动 NSD 发现
LogUtil.d(TAG, "Direct connection failed, starting NSD discovery")
discoverAndConnect(connectionCallback)
```

---

### 方案 4: 启动服务端

如果服务端未启动，需要启动服务端应用：

**在目标设备上：**

```bash
# 启动服务端应用
am start -n com.sunmi.tapro/.MainActivity

# 或者发送广播启动服务
am broadcast -a com.sunmi.tapro.START_WEBSOCKET_SERVER
```

**或者在服务端应用中添加自动启动：**

```kotlin
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // 开机自动启动 WebSocket 服务
            val serviceIntent = Intent(context, WebSocketService::class.java)
            context.startService(serviceIntent)
        }
    }
}
```

---

### 方案 5: 检查防火墙设置

**Android 设备：**
- 通常没有防火墙问题
- 检查是否有安全软件阻止

**Linux/Windows 服务端：**

```bash
# Linux - 检查 iptables
iptables -L -n | grep 8443

# 开放端口
iptables -A INPUT -p tcp --dport 8443 -j ACCEPT

# Ubuntu/Debian - 使用 ufw
ufw allow 8443

# CentOS/RHEL - 使用 firewalld
firewall-cmd --add-port=8443/tcp --permanent
firewall-cmd --reload
```

---

## 客户端代码优化

### 添加更详细的错误处理

在 `WebSocketClientKernel` 中添加更具体的错误信息：

```kotlin
override fun onError(ex: Exception) {
    val errorMsg = when {
        ex.message?.contains("ECONNREFUSED") == true -> {
            "Connection refused - server not running on ${currentUri?.host}:${currentUri?.port}"
        }
        ex.message?.contains("EHOSTUNREACH") == true -> {
            "Host unreachable - check network connection to ${currentUri?.host}"
        }
        ex.message?.contains("ETIMEDOUT") == true -> {
            "Connection timeout - server may be slow or firewall blocking"
        }
        else -> {
            "WebSocket error: ${ex.message}"
        }
    }
    
    LogUtil.e(TAG, errorMsg)
    
    if (currentInnerConnectionStatus == InnerConnectionStatus.CONNECTING) {
        handleConnectionError(errorMsg, connectionCallback, InnerErrorCode.C33.code)
    }
}
```

---

### 添加端口扫描功能

```kotlin
/**
 * 扫描常用端口
 */
private suspend fun scanCommonPorts(host: String): Int? {
    val commonPorts = listOf(8080, 8443, 9090, 3000, 8000)
    
    for (port in commonPorts) {
        try {
            val socket = Socket()
            socket.connect(InetSocketAddress(host, port), 3000) // 3秒超时
            socket.close()
            LogUtil.d(TAG, "Found open port: $port on $host")
            return port
        } catch (e: Exception) {
            LogUtil.d(TAG, "Port $port closed on $host")
        }
    }
    
    return null
}
```

---

## 服务端检查清单

### 1. WebSocket 服务启动

```kotlin
class WebSocketService : Service() {
    
    private var server: WebSocketServer? = null
    
    override fun onCreate() {
        super.onCreate()
        startWebSocketServer()
    }
    
    private fun startWebSocketServer() {
        try {
            server = object : WebSocketServer(InetSocketAddress("0.0.0.0", 8443)) {
                override fun onOpen(conn: WebSocket, handshake: ClientHandshake) {
                    Log.d(TAG, "Client connected: ${conn.remoteSocketAddress}")
                }
                
                override fun onMessage(conn: WebSocket, message: String) {
                    Log.d(TAG, "Message received: $message")
                }
                
                override fun onError(conn: WebSocket?, ex: Exception) {
                    Log.e(TAG, "WebSocket error", ex)
                }
                
                override fun onStart() {
                    Log.d(TAG, "✅ WebSocket server started on port 8443")
                }
            }
            
            server?.start()
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to start WebSocket server", e)
        }
    }
}
```

### 2. 网络权限

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
```

### 3. 服务声明

```xml
<service
    android:name=".WebSocketService"
    android:enabled="true"
    android:exported="false" />
```

---

## 快速诊断脚本

创建一个快速诊断脚本：

```bash
#!/bin/bash

TARGET_IP="10.14.78.11"
TARGET_PORT="8443"

echo "========================================="
echo "WebSocket 连接诊断工具"
echo "========================================="
echo "目标地址: $TARGET_IP:$TARGET_PORT"
echo ""

# 1. 检查网络连通性
echo "1. 检查网络连通性..."
ping -c 3 $TARGET_IP
if [ $? -eq 0 ]; then
    echo "✅ 网络连通"
else
    echo "❌ 网络不通"
    exit 1
fi
echo ""

# 2. 检查端口
echo "2. 检查端口 $TARGET_PORT..."
nc -zv $TARGET_IP $TARGET_PORT
if [ $? -eq 0 ]; then
    echo "✅ 端口开放"
else
    echo "❌ 端口未开放 (ECONNREFUSED)"
fi
echo ""

# 3. 扫描常用端口
echo "3. 扫描常用端口..."
for port in 8080 8443 9090 3000 8000; do
    nc -zv $TARGET_IP $port 2>/dev/null
    if [ $? -eq 0 ]; then
        echo "✅ 端口 $port 开放"
    fi
done
echo ""

# 4. 尝试 HTTP 请求
echo "4. 尝试 HTTP 请求..."
curl -I http://$TARGET_IP:8080 2>/dev/null
if [ $? -eq 0 ]; then
    echo "✅ HTTP 服务响应"
else
    echo "❌ HTTP 服务无响应"
fi
```

---

## 总结

**ECONNREFUSED 错误的解决步骤：**

1. ✅ **确认服务端运行** - 最重要的步骤
2. ✅ **检查端口和 IP** - 确认地址正确
3. ✅ **测试网络连通性** - 使用 ping 和 telnet
4. ✅ **查看服务端日志** - 找到具体错误原因
5. ✅ **使用 mDNS 发现** - 让客户端自动发现服务端

**最可能的原因：**
- 服务端没有启动
- 服务端监听的端口不是 8443
- 服务端崩溃了

**建议：**
- 先确认服务端正常运行
- 使用 mDNS 自动发现而不是硬编码地址
- 添加端口扫描功能

---

**文档版本**: 1.0.0  
**更新日期**: 2025-12-19  
**作者**: TaPro Team