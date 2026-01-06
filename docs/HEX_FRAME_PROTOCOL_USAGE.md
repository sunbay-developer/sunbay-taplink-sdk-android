# 十六进制帧传输协议使用指南

## 概述

`HexFrameProtocol` 是一个通用的十六进制帧传输协议实现，可用于各种需要可靠数据传输的场景。

## 协议格式

```
FF + 长度(4位) + 数据(十六进制) + 校验和(2位) + FE
```

### 帧结构说明

- **帧头 (FF)**: 1字节，固定标记，用于识别帧的开始
- **数据长度**: 4位十六进制字符，表示原始数据的字节数
  - 例如：`0010` 表示16字节
  - 例如：`00FF` 表示255字节
- **数据内容**: 原始数据转换为十六进制字符串
  - 每个字节转换为2个十六进制字符
  - 例如：字节 `0x41` 转换为 `"41"`
- **校验和**: 2位十六进制字符，数据字节和的低8位
  - 用于验证数据完整性
- **帧尾 (FE)**: 1字节，固定标记，用于识别帧的结束

### 示例

原始数据: `"Hello"` (5字节: 0x48 0x65 0x6C 0x6C 0x6F)

编码后的帧:
```
FF                    # 帧头
0005                  # 长度 (5字节)
48656C6C6F            # 数据 (Hello的十六进制)
FC                    # 校验和 (0x48+0x65+0x6C+0x6C+0x6F = 0x1FC, 低8位 = 0xFC)
FE                    # 帧尾
```

完整帧: `FF000548656C6C6FFCFE`

## 使用方法

### 1. 基本使用 - 编码和解码

```kotlin
import com.sunmi.tapro.taplink.communication.protocol.HexFrameProtocol

// 编码数据
val originalData = "Hello World".toByteArray()
val encodedFrame = HexFrameProtocol.encode(originalData)

// 发送 encodedFrame...

// 解码数据
val buffer = "FF000548656C6C6FFCFE"  // 接收到的数据
val result = HexFrameProtocol.decode(buffer)

for (frame in result.frames) {
    val decodedString = String(frame)
    println("Decoded: $decodedString")
}
```

### 2. 使用 HexFrameBuffer 进行缓冲管理

`HexFrameBuffer` 提供了完整的缓冲区管理功能，包括：
- 自动帧提取
- 超时检测
- 顺序处理
- 线程安全

```kotlin
import com.sunmi.tapro.taplink.communication.protocol.HexFrameBuffer
import kotlinx.coroutines.CoroutineScope

class MyDataHandler(private val scope: CoroutineScope) {
    
    private val hexFrameBuffer = HexFrameBuffer(
        scope = scope,
        onFrameReceived = { frame ->
            // 处理接收到的完整帧
            val message = String(frame)
            println("Received: $message")
        },
        maxBufferSize = 128 * 1024,  // 128KB
        messageTimeoutMs = 5000L      // 5秒超时
    )
    
    // 当接收到数据时调用
    fun onDataReceived(data: ByteArray) {
        hexFrameBuffer.addData(data)
    }
    
    // 清空缓冲区
    fun clear() {
        hexFrameBuffer.clear()
    }
    
    // 停止处理
    fun stop() {
        hexFrameBuffer.stop()
    }
}
```

### 3. 在 USB AOA 中使用

```kotlin
class UsbAoaKernel : AsyncServiceKernel() {
    
    private var hexFrameBuffer: HexFrameBuffer? = null
    
    init {
        hexFrameBuffer = HexFrameBuffer(
            scope = scope,
            onFrameReceived = { frame ->
                // 发送给数据接收器
                dataReceiver?.invoke(frame)
            }
        )
    }
    
    // 发送数据
    override fun performSendData(traceId: String, data: ByteArray, callback: InnerCallback?) {
        try {
            // 使用HexFrameProtocol编码
            val frameData = HexFrameProtocol.encode(data)
            
            // 通过USB AOA发送
            usbConnection?.bulkTransfer(outEndpoint, frameData, frameData.size, 2000)
            
        } catch (e: Exception) {
            callback?.onError(InnerErrorCode.C62.code, e.message ?: "Send failed")
        }
    }
    
    // 接收数据
    private fun onUsbDataReceived(data: ByteArray) {
        hexFrameBuffer?.addData(data)
    }
    
    // 断开连接
    override fun performDisconnect() {
        hexFrameBuffer?.stop()
        // ... 其他清理工作
    }
}
```

### 4. 在 WebSocket 中使用

```kotlin
class WebSocketKernel : AsyncServiceKernel() {
    
    private var hexFrameBuffer: HexFrameBuffer? = null
    private var webSocket: WebSocket? = null
    
    init {
        hexFrameBuffer = HexFrameBuffer(
            scope = scope,
            onFrameReceived = { frame ->
                dataReceiver?.invoke(frame)
            }
        )
    }
    
    // 发送数据
    override fun performSendData(traceId: String, data: ByteArray, callback: InnerCallback?) {
        try {
            // 使用HexFrameProtocol编码
            val frameData = HexFrameProtocol.encode(data)
            
            // 通过WebSocket发送（作为文本）
            webSocket?.send(String(frameData))
            
        } catch (e: Exception) {
            callback?.onError(InnerErrorCode.C62.code, e.message ?: "Send failed")
        }
    }
    
    // WebSocket消息监听
    private val webSocketListener = object : WebSocketListener() {
        override fun onMessage(webSocket: WebSocket, text: String) {
            // 接收到文本消息
            hexFrameBuffer?.addData(text.toByteArray())
        }
    }
}
```

### 5. 在蓝牙中使用

```kotlin
class BluetoothKernel : AsyncServiceKernel() {
    
    private var hexFrameBuffer: HexFrameBuffer? = null
    private var bluetoothSocket: BluetoothSocket? = null
    
    init {
        hexFrameBuffer = HexFrameBuffer(
            scope = scope,
            onFrameReceived = { frame ->
                dataReceiver?.invoke(frame)
            }
        )
    }
    
    // 发送数据
    override fun performSendData(traceId: String, data: ByteArray, callback: InnerCallback?) {
        try {
            // 使用HexFrameProtocol编码
            val frameData = HexFrameProtocol.encode(data)
            
            // 通过蓝牙发送
            bluetoothSocket?.outputStream?.write(frameData)
            
        } catch (e: Exception) {
            callback?.onError(InnerErrorCode.C62.code, e.message ?: "Send failed")
        }
    }
    
    // 蓝牙数据接收线程
    private fun startReceiveThread() {
        Thread {
            val buffer = ByteArray(1024)
            while (true) {
                try {
                    val bytes = bluetoothSocket?.inputStream?.read(buffer) ?: break
                    if (bytes > 0) {
                        val data = buffer.copyOf(bytes)
                        hexFrameBuffer?.addData(data)
                    }
                } catch (e: Exception) {
                    break
                }
            }
        }.start()
    }
}
```

## 优势

1. **固定帧边界**: FF/FE标记使得帧识别简单可靠
2. **校验和验证**: 确保数据完整性，检测传输错误
3. **高效传输**: 避免JSON分片问题，支持二进制数据
4. **通用性强**: 可用于各种传输介质（串口、USB、网络、蓝牙等）
5. **易于调试**: 十六进制格式便于日志查看和问题排查

## 注意事项

1. **缓冲区大小**: 根据实际数据量调整 `maxBufferSize`
2. **超时设置**: 根据网络延迟调整 `messageTimeoutMs`
3. **线程安全**: `HexFrameBuffer` 内部已处理线程安全，无需额外同步
4. **资源清理**: 使用完毕后记得调用 `stop()` 释放资源
5. **错误处理**: 校验和不匹配的帧会被自动丢弃，需要上层实现重传机制

## 性能考虑

- **编码开销**: 数据大小会增加约2.4倍（十六进制 + 帧头尾 + 校验和）
- **解码效率**: 使用单线程顺序处理，避免并发导致的数据错乱
- **内存使用**: 缓冲区默认128KB，可根据需要调整
- **适用场景**: 适合中小数据量的可靠传输，不适合大文件传输

## 与其他协议对比

| 特性 | HexFrameProtocol | JSON | Protobuf |
|------|------------------|------|----------|
| 帧边界识别 | ✅ 固定标记 | ❌ 需要额外处理 | ✅ 长度前缀 |
| 数据校验 | ✅ 校验和 | ❌ 无 | ❌ 无 |
| 可读性 | ✅ 十六进制 | ✅ 文本 | ❌ 二进制 |
| 传输效率 | ⚠️ 中等 | ❌ 低 | ✅ 高 |
| 实现复杂度 | ✅ 简单 | ✅ 简单 | ⚠️ 中等 |
| 二进制支持 | ✅ 原生支持 | ❌ 需要编码 | ✅ 原生支持 |

## 总结

`HexFrameProtocol` 提供了一个简单、可靠、通用的数据传输协议，特别适合需要可靠传输的场景。通过配合 `HexFrameBuffer` 使用，可以轻松实现完整的数据收发功能。
