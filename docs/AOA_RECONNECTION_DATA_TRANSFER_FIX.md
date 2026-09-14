# AOA 重连后数据传输失败问题修复

## 问题描述

在 AOA Host 和 Accessory 断开后重新连接时，虽然两端都显示"已连接"状态，但数据传输出现严重问题：

### 问题 1：首次连接时接收循环过早停止 ⭐ **核心问题**

**现象**：
- Host 发送数据后，接收循环持续收到 `-1` 返回值
- 连续 5 次 `-1` 后，接收循环认为连接断开，停止接收
- 此时 Accessory 还没有发送数据，导致 Host 无法接收任何数据

**根本原因**：
- `bulkTransfer` 返回 `-1` 不一定表示错误，可能只是暂时没有数据（超时）
- 错误地将"超时无数据"当作"连接错误"处理
- 连续错误计数机制过于敏感，导致正常的等待被误判为错误

**详细分析**：参见 `docs/AOA_RECEIVE_LOOP_FIX_SUMMARY.md`

### 问题 2：重连后收到旧数据

**现象**：
- 第二次连接时，Host 收到的是第一次连接时 Accessory 发送的旧数据
- Accessory 收到的数据也有延迟
- 数据顺序混乱，无法正常通信

**根本原因**：
- USB 端点缓冲区在断开连接时没有被清空，导致旧数据残留

### 问题 3：清空 OUT 端点导致垃圾数据

**现象**：
- Accessory 收到大量全零垃圾数据（5156 字节）
- 这些垃圾数据干扰了正常通信

**根本原因**：
- 错误地清空了 OUT 端点（发送端点）
- 对 OUT 端点执行读取操作会产生垃圾数据
- 这些垃圾数据被发送到对端

## 根本原因分析

### 问题根源

**USB 端点缓冲区在断开连接时没有被清空，导致旧数据残留！**

### 详细时间线（从日志分析）

**第一次连接（13:55:19-13:55:30）**:
1. 13:55:21.836 - Host 发送数据："Hello from Host Test at 13:55:21.836"
2. 13:55:22.857 - Accessory 成功接收 ✅
3. 13:55:24.783 - Accessory 发送数据："Hello from Accessory Test at 21:55:24.783"
4. Host 接收循环持续报错 `Bulk transfer error: -1`
5. **关键**：虽然报错，但数据已经在 USB 端点缓冲区中
6. 13:55:25/30 - 双方断开连接

**第二次连接（13:55:31-）**:
1. 13:55:31 - Accessory 连接成功
2. 13:55:32 - Host 连接成功
3. 13:55:32.148 - **Host 立即收到第一次连接时的旧数据**："Hello from Accessory Test at 21:55:24.783"
4. 13:55:34.625 - Host 发送新数据："Hello from Host Test at 13:55:34.625"
5. 13:55:38.553 - Accessory 发送新数据："Hello from Accessory Test at 21:55:38.553"
6. 13:55:42.296 - Accessory 收到的是 Host 在 13:55:41.264 发送的数据，不是 13:55:34.625 的数据！

### 日志证据

**Host 端（docs/host.txt）**:
```
# 第一次连接
13:55:21.853 - 📤 [Host] Sending 36 bytes: "Hello from Host Test at 13:55:21.836"
13:55:20.253 - Bulk transfer error: -1 (consecutive errors: 1)
13:55:20.861 - Bulk transfer error: -1 (consecutive errors: 2)
...

# 第二次连接
13:55:32.148 - 📥 [Host] Received 41 bytes: "Hello from Accessory Test at 21:55:24.783"
                                              ^^^^^^^^^^^^^^^^^^^^^^^^
                                              这是第一次连接时的数据！
```

**Accessory 端（docs/accessory.txt）**:
```
# 第一次连接
13:55:22.860 - 📥 [Accessory] Received: "Hello from Host Test at 13:55:21.836" ✅
13:55:24.798 - 📤 [Accessory] Sending: "Hello from Accessory Test at 21:55:24.783"

# 第二次连接
13:55:38.590 - 📤 [Accessory] Sending: "Hello from Accessory Test at 21:55:38.553"
13:55:42.297 - 📥 [Accessory] Received: "Hello from Host Test at 13:55:41.264"
                                         ^^^^^^^^^^^^^^^^^^^^^^^^
                                         不是 13:55:34.625 发送的数据！
```

### 为什么会这样？

1. **USB 端点有硬件缓冲区**：
   - 发送的数据先进入端点缓冲区
   - 接收端从缓冲区读取数据
   - 断开连接时，缓冲区中的数据不会自动清空

2. **Android USB API 的行为**：
   - `UsbDeviceConnection.close()` 不会清空端点缓冲区
   - `UsbDeviceConnection.releaseInterface()` 也不会清空
   - 重新打开设备时，缓冲区中的旧数据仍然存在

3. **导致的问题**：
   - 第一次连接时发送的数据残留在缓冲区
   - 第二次连接时，首先读到的是旧数据
   - 新数据被延迟或丢失
   - 数据顺序完全混乱

## 修复方案

### 修复 1：改进接收循环的错误处理

**问题**：将 `bulkTransfer` 返回 `-1` 当作错误，导致接收循环过早停止。

**根本原因**：
- `bulkTransfer` 返回 `-1` 不一定表示错误，通常只是超时（没有数据可读）
- 原代码使用连续错误计数，连续 5 次 `-1` 后就停止接收循环
- 这导致 Host 在 Accessory 发送数据之前就停止了接收

**解决方案**：只有在设备真的断开时才停止接收循环。

```kotlin
when {
    bytesRead > 0 -> {
        // 成功接收数据
        val data = buffer.copyOf(bytesRead)
        dataReceiver?.invoke(data)
    }
    
    bytesRead < 0 -> {
        // 传输错误或超时
        // 注意：bulkTransfer 超时也会返回 -1，不一定是真正的错误
        
        // 检查设备是否真的断开
        if (!isDeviceStillConnected()) {
            LogUtil.w(TAG, "❌ Device disconnected, stopping receive loop")
            disconnect()
            break
        }
        
        // 设备还在，可能只是超时或暂时没有数据
        // 继续接收，不停止循环
        delay(10)
    }
    
    else -> {
        // bytesRead == 0: 超时，没有数据
        delay(10)
    }
}
```

**关键改进**：
1. **移除连续错误计数**：不再统计连续 `-1` 的次数
2. **只检查设备状态**：通过 `isDeviceStillConnected()` 判断设备是否真的断开
3. **继续接收**：如果设备还在，继续等待数据，不停止接收循环
4. **减少日志**：只在收到数据或真正断开时打印日志，避免日志过多

**为什么这样有效**：
- `bulkTransfer` 的 `-1` 返回值通常表示超时，不是错误
- 只有当设备真的从 USB 设备列表中消失时，才认为是断开
- 这样接收循环可以一直运行，等待对端发送数据

### 修复 2：清空 IN 端点缓冲区

**问题**：USB 端点缓冲区残留旧数据。

**解决方案**：在连接建立后立即清空 IN 端点缓冲区。

### 修复 3：只清空 IN 端点，不清空 OUT 端点

**问题**：清空 OUT 端点会产生垃圾数据发送到对端。

**解决方案**：只清空 IN 端点（接收端点），不清空 OUT 端点（发送端点）。

添加 `flushEndpoint()` 方法，在连接建立后清空端点缓冲区：

```kotlin
/**
 * 清空 USB 端点缓冲区
 * 
 * 重要：防止读取上一次连接残留的旧数据
 */
private fun flushEndpoint(connection: UsbDeviceConnection, endpoint: UsbEndpoint) {
    try {
        val buffer = ByteArray(endpoint.maxPacketSize)
        var flushedBytes = 0
        var iterations = 0
        val maxIterations = 10  // 最多尝试 10 次
        
        LogUtil.d(TAG, "Flushing endpoint 0x${Integer.toHexString(endpoint.address.toInt() and 0xFF)}...")
        
        // 持续读取直到没有数据或达到最大次数
        while (iterations < maxIterations) {
            val bytesRead = connection.bulkTransfer(endpoint, buffer, buffer.size, 100)  // 100ms 超时
            if (bytesRead > 0) {
                flushedBytes += bytesRead
                iterations++
            } else {
                break
            }
        }
        
        if (flushedBytes > 0) {
            LogUtil.i(TAG, "Flushed $flushedBytes bytes from endpoint 0x${Integer.toHexString(endpoint.address.toInt() and 0xFF)} in $iterations iterations")
        } else {
            LogUtil.d(TAG, "Endpoint 0x${Integer.toHexString(endpoint.address.toInt() and 0xFF)} is clean (no data to flush)")
        }
    } catch (e: Exception) {
        LogUtil.w(TAG, "Failed to flush endpoint: ${e.message}")
    }
}
```

### 调用时机

在 `connectToAoaDevice()` 方法中，claim 接口后、启动接收循环前调用：

```kotlin
// 4. Claim 接口
if (!conn.claimInterface(iface, true)) {
    conn.close()
    throw Exception("Failed to claim interface")
}

// 5. 清空端点缓冲区（重要！防止读取旧数据）
// 注意：只清空 IN 端点（接收端点），不清空 OUT 端点（发送端点）
if (epIn != null) {
    flushEndpoint(conn, epIn)
}

// 6. 保存资源并启动接收
// ...
```

### 重要注意事项

**只清空 IN 端点（接收端点），不要清空 OUT 端点（发送端点）！**

原因：
1. **IN 端点**（接收端点）：
   - 用于从对端接收数据
   - 可能残留上一次连接的旧数据
   - 需要清空以防止读到旧数据

2. **OUT 端点**（发送端点）：
   - 用于向对端发送数据
   - 对 OUT 端点执行 `bulkTransfer` 读取操作会产生垃圾数据
   - 这些垃圾数据会被发送到对端，导致对端收到错误数据
   - **绝对不要清空 OUT 端点！**

### 错误示例（不要这样做）

```kotlin
// ❌ 错误：清空 OUT 端点会导致对端收到垃圾数据
flushEndpoint(conn, epOut)  // 会产生 5120 字节的全零数据发送到对端
```

实际测试中发现：
- Host 清空 OUT 端点时，读取了 5120 字节
- Accessory 收到了 5156 字节的全零数据
- 这些垃圾数据干扰了正常通信

### 为什么这样有效？

1. **只清空接收端点**：
   - 清空 IN 端点（接收端点）中的旧数据
   - 确保接收循环读到的是新连接后的数据
   - **不清空 OUT 端点**，避免产生垃圾数据

2. **短超时**：
   - 使用 100ms 超时，快速检测是否有数据
   - 如果没有数据，立即返回，不会阻塞

3. **限制次数**：
   - 最多尝试 10 次，防止无限循环
   - 正常情况下，1-2 次就能清空所有旧数据

4. **避免干扰对端**：
   - 不对 OUT 端点执行读取操作
   - 防止产生垃圾数据发送到对端
   - 确保对端只收到有效数据

### Host 端修改

#### 1. `startReceive()` 方法

**修改前**：
```kotlin
private fun startReceive() {
    receiveJob?.cancel()
    
    receiveJob = scope.launch(Dispatchers.IO) {
        val buffer = ByteArray(16384)
        
        var loopCount = 0
        while (isActive && state == ConnectionState.CONNECTED) {
            loopCount++
            val conn = connection  // ⚠️ 每次从共享变量读取
            val epIn = endpointIn  // ⚠️ 可能是过期的资源
            
            // ...
        }
    }
}
```

**修改后**：
```kotlin
private fun startReceive() {
    // 1. 取消旧协程
    val oldJob = receiveJob
    receiveJob = null
    oldJob?.cancel()
    
    // 2. 获取资源快照（在启动新协程前）
    val currentConn = connection
    val currentEpIn = endpointIn
    val currentDev = currentDevice
    
    // 3. 验证资源有效性
    if (currentConn == null || currentEpIn == null || currentDev == null) {
        LogUtil.w(TAG, "Cannot start receive: resources not ready")
        return
    }
    
    // 4. 生成连接 ID
    val connectionId = System.currentTimeMillis()
    
    receiveJob = scope.launch(Dispatchers.IO) {
        val buffer = ByteArray(16384)
        var loopCount = 0
        var consecutiveErrors = 0
        
        while (isActive && state == ConnectionState.CONNECTED) {
            loopCount++
            
            // 5. 验证资源是否仍然有效
            if (connection != currentConn || endpointIn != currentEpIn || currentDevice != currentDev) {
                LogUtil.w(TAG, "⚠️ Connection resources changed, stopping stale receive loop")
                break
            }
            
            // 6. 使用快照资源
            val bytesRead = currentConn.bulkTransfer(currentEpIn, buffer, buffer.size, 500)
            
            when {
                bytesRead > 0 -> {
                    consecutiveErrors = 0  // 重置错误计数
                    // 处理数据...
                }
                bytesRead < 0 -> {
                    consecutiveErrors++
                    LogUtil.w(TAG, "Bulk transfer error: $bytesRead (consecutive errors: $consecutiveErrors)")
                    
                    // 7. 连续错误检测
                    if (consecutiveErrors >= 5) {
                        LogUtil.w(TAG, "❌ Too many consecutive errors, stopping receive")
                        break
                    }
                    delay(100)
                }
                else -> {
                    consecutiveErrors = 0  // 超时不算错误
                    delay(10)
                }
            }
        }
    }
}
```

#### 2. `cleanupResources()` 方法

**修改前**：
```kotlin
private fun cleanupResources() {
    // 停止接收
    receiveJob?.cancel()
    receiveJob = null
    
    // 释放接口
    interface_?.let { iface ->
        connection?.releaseInterface(iface)
    }
    
    // 关闭连接
    connection?.close()
    
    // 清空引用
    currentDevice = null
    connection = null
    interface_ = null
    endpointIn = null
    endpointOut = null
}
```

**修改后**：
```kotlin
private fun cleanupResources() {
    // 1. 先清空引用（防止新操作使用旧资源）
    val oldConnection = connection
    val oldInterface = interface_
    val oldReceiveJob = receiveJob
    
    currentDevice = null
    connection = null
    interface_ = null
    endpointIn = null
    endpointOut = null
    
    // 2. 停止接收协程（使用本地引用）
    oldReceiveJob?.cancel()
    receiveJob = null
    
    // 3. 等待协程完全停止（最多 500ms）
    val startTime = System.currentTimeMillis()
    while (oldReceiveJob?.isActive == true && System.currentTimeMillis() - startTime < 500) {
        Thread.sleep(10)
    }
    
    // 4. 释放接口（使用本地引用）
    oldInterface?.let { iface ->
        oldConnection?.releaseInterface(iface)
    }
    
    // 5. 关闭连接（使用本地引用）
    oldConnection?.close()
}
```

### Accessory 端修改

Accessory 端应用了相同的修复策略：
- 资源快照机制
- 连接 ID 追踪
- 改进的资源清理
- 连续错误检测

## 修复效果

### 预期改进

1. **消除旧数据残留**：
   - 重连后不会读到上一次连接的数据
   - 数据顺序正确，按发送顺序接收

2. **数据传输正常**：
   - Host 发送的数据能被 Accessory 正确接收
   - Accessory 发送的数据能被 Host 正确接收
   - 双向通信完全正常

3. **稳定的重连**：
   - 多次重连后数据传输仍然正常
   - 不会出现数据混乱或延迟

### 测试建议

1. **基本重连测试**：
   - 首次连接：验证数据双向传输正常
   - 断开重连：验证不会收到旧数据
   - 多次重连：验证每次重连后数据传输都正常

2. **数据顺序测试**：
   - 发送带时间戳的数据
   - 验证接收到的数据时间戳是正确的
   - 确保不会收到旧的时间戳

3. **压力测试**：
   - 快速重连：连续断开重连 10 次
   - 并发数据：重连后立即发送大量数据
   - 长时间运行：保持连接 1 小时，定期发送数据

4. **异常场景**：
   - 拔线重连：物理断开后重新连接
   - 主动断开：用户主动断开后重连
   - 被动断开：Host 断开后 Accessory 自动重连

### 预期日志

**第一次连接**:
```
13:55:19 - Flushing endpoint 0x81...
13:55:19 - Endpoint 0x81 is clean (no data to flush)
13:55:19 - Flushing endpoint 0x1...
13:55:19 - Endpoint 0x1 is clean (no data to flush)
13:55:21 - Host 发送: "Hello at 13:55:21"
13:55:22 - Accessory 接收: "Hello at 13:55:21" ✅
13:55:24 - Accessory 发送: "Hello at 13:55:24"
13:55:24 - Host 接收: "Hello at 13:55:24" ✅
```

**第二次连接**:
```
13:55:32 - Flushing endpoint 0x81...
13:55:32 - Flushed 41 bytes from endpoint 0x81 in 1 iterations  ← 清空了旧数据！
13:55:32 - Flushing endpoint 0x1...
13:55:32 - Endpoint 0x1 is clean (no data to flush)
13:55:34 - Host 发送: "Hello at 13:55:34"
13:55:35 - Accessory 接收: "Hello at 13:55:34" ✅ 正确的数据！
13:55:38 - Accessory 发送: "Hello at 13:55:38"
13:55:38 - Host 接收: "Hello at 13:55:38" ✅ 正确的数据！
```

## 相关文件

- `lib_taplink_communication/src/main/java/com/sunmi/tapro/taplink/communication/cable/aoa/host/CableAoaHostKernel.kt`
- `lib_taplink_communication/src/main/java/com/sunmi/tapro/taplink/communication/cable/aoa/accessory/CableAoaAccessoryKernel.kt`
- `docs/host.txt` - Host 端日志
- `docs/accessory.txt` - Accessory 端日志

## 总结

这次修复解决了 AOA 首次连接和重连后数据传输失败的核心问题：

### 问题 1：接收循环过早停止（首次连接）
**根本原因**：错误地将 `bulkTransfer` 返回 `-1`（超时）当作连接错误，导致接收循环在对端发送数据之前就停止了。

**解决方案**：
- 移除连续错误计数机制
- 只有在设备真的从 USB 设备列表中消失时才停止接收循环
- 对于 `-1` 返回值（超时），继续等待数据

### 问题 2：重连后收到旧数据
**根本原因**：USB 端点缓冲区在断开连接时没有被清空，导致旧数据残留。

**解决方案**：
- 在连接建立后立即清空 IN 端点（接收端点）缓冲区
- **重要**：只清空 IN 端点，不清空 OUT 端点（发送端点）
- 使用短超时（100ms）快速检测并清空残留数据

### 关键改进点

1. ✅ **改进错误处理** - 不再将超时当作错误，只有设备真的断开才停止接收
2. ✅ **端点清空** - 连接建立后清空 IN 端点缓冲区，防止读取旧数据
3. ✅ **只清空 IN 端点** - 避免清空 OUT 端点产生垃圾数据
4. ✅ **快速检测** - 使用短超时（100ms）快速检测是否有数据
5. ✅ **限制次数** - 最多尝试 10 次，防止无限循环
6. ✅ **减少日志** - 只在关键事件时打印日志，避免日志过多

### 修复效果

**首次连接**：
- Host 发送数据后，接收循环继续运行
- Accessory 发送数据时，Host 能够正确接收
- 双向通信正常

**重连**：
- 连接建立后清空旧数据
- 不会收到上一次连接的残留数据
- 数据顺序正确，按发送顺序接收

### 为什么之前的修复不够？

之前的修复（资源快照、连接 ID 追踪）虽然改进了资源管理，但没有解决两个根本问题：
1. **接收循环过早停止** - 需要改进错误处理逻辑
2. **USB 硬件缓冲区中的旧数据** - 需要清空端点缓冲区

这些改进仍然有价值，因为它们：
- 提高了代码的健壮性
- 改进了资源管理和日志
- 防止了资源竞争

但要真正解决数据传输问题，必须同时修复错误处理逻辑和清空 USB 端点缓冲区。

---

**修复日期**: 2026-01-19  
**修复人员**: Tapro Team  
**问题类型**: USB 端点缓冲区管理  
**影响范围**: AOA Host 模式重连
