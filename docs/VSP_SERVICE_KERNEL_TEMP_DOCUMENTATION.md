# VSPServiceKernelTemp 文档

## 概述

`VSPServiceKernelTemp` 是参考 `VSPPosApiTask` 功能创建的现代化串口通信服务内核，继承自 `AsyncServiceKernel`，提供异步的UART串口通信功能。

## 主要特性

### 1. 现代化架构
- **继承自AsyncServiceKernel**: 利用现代化的异步服务架构
- **Kotlin协程支持**: 使用协程进行异步操作，避免阻塞主线程
- **生命周期管理**: 完整的连接生命周期管理

### 2. 兼容性设计
- **参考VSPPosApiTask**: 保持与原有VSPPosApiTask相似的功能
- **SUNMI UART API**: 使用SUNMI设备的原生UART API
- **Bundle参数支持**: 支持Bundle配置参数，便于迁移

### 3. 功能特性
- **异步数据收发**: 非阻塞的数据传输
- **错误处理**: 完善的错误处理和状态管理
- **配置灵活**: 支持多种串口参数配置
- **统一重连**: 重连机制由TaplinkSDK统一管理

## 类结构

```kotlin
class VSPServiceKernelTemp(
    appId: String,
    appSecretKey: String,
    private val context: Context
) : AsyncServiceKernel(appId, appSecretKey)
```

### 核心方法

#### 连接管理
- `performConnect()`: 执行连接逻辑
- `performDisconnect()`: 执行断开连接逻辑

#### 数据传输
- `performSendData()`: 发送数据到串口
- `startDataReceive()`: 启动数据接收循环

#### 状态查询
- `isUartConnected()`: 检查UART连接状态
- `getCurrentConfig()`: 获取当前配置信息

## 协议格式

VSP协议格式：`vsp://baudRate/dataBits/parity/stopBits`

### 参数说明
- **baudRate**: 波特率 (如: 115200, 9600, 38400)
- **dataBits**: 数据位 (通常为8)
- **parity**: 校验位
  - `n` 或 `none`: 无校验
  - `e` 或 `even`: 偶校验
  - `o` 或 `odd`: 奇校验
- **stopBits**: 停止位 (1或2)

### 示例
```
vsp://115200/8/n/1    # 115200波特率, 8数据位, 无校验, 1停止位
vsp://9600/8/e/2      # 9600波特率, 8数据位, 偶校验, 2停止位
```

## 使用方法

### 1. 基本使用

```kotlin
// 创建实例
val vspKernel = VSPServiceKernelTemp(
    appId = "your_app_id",
    appSecretKey = "your_secret",
    context = context
)

// 设置数据接收器
vspKernel.setDataReceiver { data ->
    val receivedString = String(data)
    Log.d("VSP", "Received: $receivedString")
}

// 连接
vspKernel.connect("vsp://115200/8/n/1", object : ConnectionCallback {
    override fun onConnected(extraInfoMap: Map<String, String?>?) {
        Log.d("VSP", "Connected successfully")
    }
    
    override fun onError(code: String, message: String) {
        Log.e("VSP", "Connection failed: $message")
    }
    
    // ... 其他回调方法
})
```

### 2. 发送数据

```kotlin
val data = "Hello Device!".toByteArray()
vspKernel.sendData("trace_id", data, object : InnerCallback {
    override fun onResponse(response: String) {
        Log.d("VSP", "Data sent successfully")
    }
    
    override fun onError(code: String, message: String) {
        Log.e("VSP", "Send failed: $message")
    }
})
```

### 3. 使用Bundle配置（兼容VSPPosApiTask）

```kotlin
// 创建配置Bundle
val configBundle = VSPServiceKernelTemp.createConfigBundle(
    baudRate = 115200,
    dataBits = 8,
    parity = "n",
    stopBits = 1
)

// 转换为协议字符串
val protocol = VSPServiceKernelTemp.bundleToVspProtocol(configBundle)

// 连接
vspKernel.connect(protocol, connectionCallback)
```

## 与VSPPosApiTask的对比

| 特性 | VSPPosApiTask | VSPServiceKernelTemp |
|------|---------------|---------------------|
| 架构 | 传统Thread + Runnable | 现代Kotlin协程 |
| 异步支持 | 手动线程管理 | 内置协程支持 |
| 错误处理 | 基础异常处理 | 完善的错误分类和处理 |
| 重连机制 | 需要手动实现 | 由TaplinkSDK统一管理 |
| 生命周期 | 手动管理 | AsyncServiceKernel管理 |
| 配置方式 | Bundle参数 | 协议字符串 + Bundle兼容 |
| 数据接收 | 轮询方式 | 协程异步接收 |

## 错误处理

### 错误类型
1. **连接错误**: UART设备无法打开
2. **发送错误**: 数据发送失败
3. **接收错误**: 数据接收异常
4. **超时错误**: 操作超时 (`UartException.PORT_ERROR_TIMEOUT`)
5. **端口错误**: 端口未打开或已关闭 (`UartException.PORT_ERROR_NOT_OPEN`)

### UART错误码处理
根据VSPPosApiTask的实现，SUNMI UART API主要使用以下错误码：

- **PORT_ERROR_TIMEOUT**: 超时错误，通常可以忽略，继续重试
- **PORT_ERROR_NOT_OPEN**: 端口未打开或已关闭，需要重新连接
- **其他错误码**: 未知错误，更新状态为ERROR让上层处理

### 错误恢复
- **状态更新**: 错误发生时更新连接状态
- **上层重连**: 重连由TaplinkSDK的ReconnectManager统一处理
- **状态同步**: 错误状态与连接状态保持同步

## 配置选项

### 串口参数
```kotlin
// 支持的波特率
val supportedBaudRates = listOf(9600, 19200, 38400, 57600, 115200)

// 支持的数据位
val supportedDataBits = listOf(7, 8)

// 支持的校验位
val supportedParity = listOf("n", "e", "o")  // none, even, odd

// 支持的停止位
val supportedStopBits = listOf(1, 2)
```

## 最佳实践

### 1. 资源管理
```kotlin
// 在Activity/Fragment的onDestroy中清理资源
override fun onDestroy() {
    super.onDestroy()
    vspKernel?.disconnect()
}
```

### 2. 错误处理
```kotlin
// 实现完整的错误处理
vspKernel.connect(protocol, object : ConnectionCallback {
    override fun onError(code: String, message: String) {
        when (code) {
            "NOT_CONNECTED" -> {
                // 处理未连接错误
            }
            "UART_NOT_OPEN" -> {
                // 处理端口未打开错误，TaplinkSDK会自动重连
            }
            else -> {
                // 处理其他错误
            }
        }
    }
})
```

### 3. 数据处理
```kotlin
// 设置数据接收器时进行异常处理
vspKernel.setDataReceiver { data ->
    try {
        val message = String(data)
        processMessage(message)
    } catch (e: Exception) {
        Log.e("VSP", "Error processing data: ${e.message}")
    }
}
```

## 重连机制说明

VSPServiceKernelTemp **不包含内置重连机制**，重连由TaplinkSDK的ReconnectManager统一管理：

### 重连流程
1. VSP内核检测到连接错误时，更新状态为`ERROR`或`DISCONNECTED`
2. TaplinkSDK的ReconnectManager监听状态变化
3. ReconnectManager根据配置策略自动触发重连
4. 重连时会重新调用VSP内核的`performConnect()`方法

### 优势
- **统一管理**: 所有连接类型使用相同的重连策略
- **配置灵活**: 重连参数可在SDK级别统一配置
- **状态一致**: 重连状态与整体连接状态保持一致
- **简化实现**: VSP内核专注于串口通信，不需要处理重连逻辑

## 调试和日志

### 日志级别
- **DEBUG**: 详细的操作日志
- **INFO**: 重要状态变更
- **WARN**: 警告信息
- **ERROR**: 错误信息

### 关键日志点
1. 连接建立和断开
2. 数据发送和接收
3. 错误发生和状态更新
4. UART参数配置

## 性能考虑

### 1. 内存使用
- 合理的接收缓冲区大小
- 及时释放不需要的资源
- 避免内存泄漏

### 2. CPU使用
- 使用协程避免阻塞主线程
- 合理的轮询间隔（50ms）
- 错误时适当延迟重试

### 3. 串口效率
- 批量数据传输
- 合理的超时设置
- 避免频繁的连接断开

## 扩展和定制

### 1. 自定义错误处理
```kotlin
class CustomVSPKernel(appId: String, appSecretKey: String, context: Context) 
    : VSPServiceKernelTemp(appId, appSecretKey, context) {
    
    override fun handleUartError(e: UartException) {
        // 自定义错误处理逻辑
        super.handleUartError(e)
    }
}
```

### 2. 扩展协议支持
```kotlin
// 可以扩展支持更多的串口参数
// 或者添加自定义的协议解析逻辑
```

## 相关文件

- `VSPServiceKernelTemp.kt`: 主要实现文件
- `VSP_SERVICE_KERNEL_TEMP_USAGE_EXAMPLE.kt`: 使用示例
- `AsyncServiceKernel.kt`: 基类实现
- `ProtocolParseResult.kt`: 协议解析结果
- `ReconnectManager.kt`: TaplinkSDK重连管理器