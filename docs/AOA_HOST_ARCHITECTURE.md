# Android AOA Host 模式架构文档

## 概述

本文档详细介绍 Taplink SDK 中 Android Open Accessory (AOA) Host 模式的实现架构。Host 模式允许 Android 设备作为 USB 主机，连接并控制其他 Android 设备（作为 Accessory）。

## 目录

1. [核心概念](#核心概念)
2. [架构设计](#架构设计)
3. [类层次结构](#类层次结构)
4. [连接流程](#连接流程)
5. [状态管理](#状态管理)
6. [数据传输](#数据传输)
7. [错误处理](#错误处理)
8. [使用示例](#使用示例)

---

## 核心概念

### AOA 协议简介

Android Open Accessory (AOA) 协议允许外部 USB 硬件（配件）与 Android 设备通信。在 Host 模式下：

- **Host 设备**：作为 USB 主机的 Android 设备，主动发起连接
- **Accessory 设备**：作为 USB 配件的 Android 设备，被动接受连接
- **协议切换**：Host 通过发送 AOA 协议命令，将普通 Android 设备切换到 Accessory 模式

### 关键特性

1. **自动设备检测**：自动识别连接的 USB 设备
2. **AOA 协议切换**：自动将设备切换到 Accessory 模式
3. **权限管理**：处理 USB 设备权限请求
4. **三层断开检测**：
   - 系统广播检测（< 100ms）
   - 传输错误检测（< 500ms）
   - 异常捕获检测（< 500ms）
5. **状态机驱动**：简化的状态机管理连接生命周期

---

## 架构设计

### 整体架构

```
┌─────────────────────────────────────────────────────────────┐
│                     Application Layer                        │
│                  (UsbHostKernelTestActivity)                 │
└────────────────────────┬────────────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────────┐
│                   SDK Public API Layer                       │
│                    (AsyncServiceKernel)                      │
└────────────────────────┬────────────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────────┐
│                  AOA Host Kernel Layer                       │
│                  (CableAoaHostKernel)                        │
│  ┌──────────────────────────────────────────────────────┐   │
│  │  • 状态机管理                                         │   │
│  │  • 设备查找与连接                                     │   │
│  │  • AOA 协议切换                                       │   │
│  │  • 权限请求处理                                       │   │
│  │  • 数据收发                                           │   │
│  └──────────────────────────────────────────────────────┘   │
└────────────────────────┬────────────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────────┐
│                   Protocol Layer                             │
│              (UsbStandardProtocol)                           │
│  ┌──────────────────────────────────────────────────────┐   │
│  │  • AOA 协议命令封装                                   │   │
│  │  • GET_PROTOCOL / SEND_STRING / START                │   │
│  │  • 设备兼容性检查                                     │   │
│  └──────────────────────────────────────────────────────┘   │
└────────────────────────┬────────────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────────┐
│                  Android USB Framework                       │
│         (UsbManager, UsbDevice, UsbDeviceConnection)         │
└─────────────────────────────────────────────────────────────┘
```

### 模块职责

#### 1. CableAoaHostKernel（核心内核）

**位置**：`lib_taplink_communication/src/main/java/com/sunmi/tapro/taplink/communication/cable/aoa/host/CableAoaHostKernel.kt`

**职责**：
- 实现简化的 AOA Host 连接逻辑
- 管理连接状态机
- 处理设备查找、权限请求、AOA 切换
- 管理数据收发
- 处理设备插拔事件

**核心特性**：
- 单一连接入口（`connect()`）
- 幂等操作（重复调用安全）
- 状态即真相（状态变量直接反映资源状态）
- 移除 session/token 机制，使用简单状态机

#### 2. UsbStandardProtocol（协议层）

**位置**：`lib_taplink_communication/src/main/java/com/sunmi/tapro/taplink/communication/protocol/UsbStandardProtocol.kt`

**职责**：
- 封装 AOA 协议命令
- 提供设备兼容性检查
- 管理 AOA 协议常量

**核心方法**：
```kotlin
// 发送完整的 AOA 协议序列
fun sendAoaProtocolSequence(
    connection: UsbDeviceConnection, 
    deviceInfo: UsbStandardInfo
): Boolean

// 检查设备是否支持 AOA
fun isAoaCompatible(vendorId: Int, productId: Int): Boolean
```

#### 3. AsyncServiceKernel（基类）

**位置**：`lib_taplink_communication/src/main/java/com/sunmi/tapro/taplink/communication/interfaces/AsyncServiceKernel.kt`

**职责**：
- 提供异步连接/断开接口
- 管理连接回调
- 提供数据发送互斥锁
- 管理连接状态

---

## 类层次结构

```
AsyncServiceKernel (抽象基类)
    │
    ├── 提供：
    │   ├── connect() / disconnect() 公共接口
    │   ├── sendData() 数据发送（带互斥锁）
    │   ├── 连接状态管理
    │   └── 协程作用域管理
    │
    └── CableAoaHostKernel (Host 模式实现)
        │
        ├── 状态机：ConnectionState
        │   ├── IDLE（空闲）
        │   ├── REQUESTING_PERMISSION（请求权限）
        │   ├── SWITCHING_AOA（切换 AOA）
        │   ├── CONNECTING（连接中）
        │   ├── CONNECTED（已连接）
        │   ├── DISCONNECTING（断开中）
        │   └── ERROR（错误）
        │
        ├── 资源管理：
        │   ├── currentDevice: UsbDevice?
        │   ├── connection: UsbDeviceConnection?
        │   ├── interface_: UsbInterface?
        │   ├── endpointIn: UsbEndpoint?
        │   └── endpointOut: UsbEndpoint?
        │
        └── 广播接收器：
            ├── permissionReceiver（权限响应）
            └── deviceReceiver（设备插拔）
```

---

## 连接流程

### 完整连接流程图

```
┌─────────────────┐
│  应用调用        │
│  connect()      │
└────────┬────────┘
         │
         ▼
┌─────────────────────────────────────────────────────────┐
│  1. 查找目标设备                                         │
│     - 优先查找 AOA 设备                                  │
│     - 否则返回第一个设备                                 │
└────────┬────────────────────────────────────────────────┘
         │
         ▼
    ┌───┴───┐
    │ 是否  │
    │ AOA?  │
    └───┬───┘
        │
    ┌───┴───────────────────────────────────────┐
    │                                           │
   是                                          否
    │                                           │
    ▼                                           ▼
┌─────────────────┐                  ┌─────────────────────┐
│  直接连接        │                  │  2. 检查权限         │
│  AOA 设备        │                  │     - 有权限：发送   │
│                 │                  │       AOA 协议       │
└────────┬────────┘                  │     - 无权限：请求   │
         │                           │       权限           │
         │                           └────────┬────────────┘
         │                                    │
         │                                    ▼
         │                           ┌─────────────────────┐
         │                           │  3. 发送 AOA 协议    │
         │                           │     - GET_PROTOCOL  │
         │                           │     - SEND_STRING   │
         │                           │     - START         │
         │                           └────────┬────────────┘
         │                                    │
         │                                    ▼
         │                           ┌─────────────────────┐
         │                           │  4. 等待 AOA 设备    │
         │                           │     出现（15秒）     │
         │                           └────────┬────────────┘
         │                                    │
         └────────────────┬───────────────────┘
                          │
                          ▼
                 ┌─────────────────────┐
                 │  5. 连接 AOA 设备    │
                 │     - 请求权限       │
                 │     - 打开设备       │
                 │     - Claim 接口     │
                 │     - 查找端点       │
                 └────────┬────────────┘
                          │
                          ▼
                 ┌─────────────────────┐
                 │  6. 启动数据接收     │
                 │     - 启动接收协程   │
                 │     - 回调成功       │
                 └─────────────────────┘
```

### 详细步骤说明

#### 步骤 1：查找目标设备

```kotlin
private fun findTargetDevice(): UsbDevice? {
    val devices = usbManager.deviceList
    
    // 优先查找 AOA 设备
    val aoaDevice = findAoaDevice()
    if (aoaDevice != null) {
        return aoaDevice
    }
    
    // 否则返回第一个设备
    return devices.values.firstOrNull()
}
```

#### 步骤 2：权限请求

```kotlin
private suspend fun requestPermissionAndWait(device: UsbDevice) {
    permissionLatch = CountDownLatch(1)
    permissionDenied = false
    pendingPermissionDevice = device
    
    val intent = createPermissionIntent(device)
    usbManager.requestPermission(device, intent)
    
    // 等待权限响应（最多 30 秒）
    val granted = withTimeoutOrNull(30000) {
        permissionLatch?.await()
        !permissionDenied
    }
    
    if (granted != true) {
        throw Exception("Permission denied or timeout")
    }
}
```

#### 步骤 3：发送 AOA 协议

```kotlin
private fun sendAoaProtocol(device: UsbDevice) {
    val conn = usbManager.openDevice(device)
    
    try {
        val aoaDeviceInfo = UsbStandardProtocol.createDefaultAoaDeviceInfo()
        
        if (!UsbStandardProtocol.sendAoaProtocolSequence(conn, aoaDeviceInfo)) {
            throw Exception("Failed to send AOA protocol sequence")
        }
    } finally {
        conn.close()
    }
}
```

#### 步骤 4：等待 AOA 设备出现

```kotlin
private suspend fun waitForAoaDevice(timeout: Long): UsbDevice? {
    aoaDeviceLatch = CountDownLatch(1)
    
    // 先检查是否已经存在
    val existing = findAoaDevice()
    if (existing != null) {
        return existing
    }
    
    // 等待设备出现
    val appeared = withTimeoutOrNull(timeout) {
        aoaDeviceLatch?.await()
        true
    }
    
    return if (appeared == true) findAoaDevice() else null
}
```

#### 步骤 5：连接 AOA 设备

```kotlin
private suspend fun connectToAoaDevice(device: UsbDevice, callback: ConnectionCallback) {
    // 1. 请求权限（如果需要）
    if (!usbManager.hasPermission(device)) {
        requestPermissionAndWait(device)
    }
    
    // 2. 打开设备
    val conn = usbManager.openDevice(device)
    
    // 3. 查找接口和端点
    val (iface, epIn, epOut) = findAoaInterface(device)
    
    // 4. Claim 接口
    conn.claimInterface(iface, true)
    
    // 5. 保存资源
    synchronized(stateLock) {
        currentDevice = device
        connection = conn
        interface_ = iface
        endpointIn = epIn
        endpointOut = epOut
        state = ConnectionState.CONNECTED
    }
    
    // 6. 启动接收
    startReceive()
    
    // 7. 回调成功
    handleConnectionSuccess(callback, null)
}
```

---

## 状态管理

### 状态定义

```kotlin
private enum class ConnectionState {
    IDLE,                    // 空闲，未连接
    REQUESTING_PERMISSION,   // 正在请求权限
    SWITCHING_AOA,          // 正在发送 AOA 协议，等待设备切换
    CONNECTING,             // 正在建立连接
    CONNECTED,              // 已连接
    DISCONNECTING,          // 正在断开
    ERROR                   // 错误状态
}
```

### 状态转换规则

```
IDLE ──────────────────────────────────────────────────────┐
  │                                                         │
  ├─> REQUESTING_PERMISSION ──> SWITCHING_AOA ──> CONNECTING ──> CONNECTED
  │                                                         │
  └─────────────────────────────────────────────────────────┘
                          (disconnect)
```

### 状态转换示例

```kotlin
// 开始连接
synchronized(stateLock) {
    when (state) {
        ConnectionState.CONNECTED -> {
            // 已连接，直接返回成功
            handleConnectionSuccess(connectionCallback, null)
            return
        }
        ConnectionState.IDLE -> {
            // 空闲，开始连接
            state = ConnectionState.CONNECTING
        }
        else -> {
            // 其他状态，忽略
            return
        }
    }
}
```

### 状态保护

所有状态转换都在 `stateLock` 保护下进行，确保线程安全：

```kotlin
private val stateLock = Any()

synchronized(stateLock) {
    state = ConnectionState.CONNECTING
    updateStatus(InnerConnectionStatus.CONNECTING)
}
```

---

## 数据传输

### 数据发送

```kotlin
override suspend fun performSendData(
    traceId: String, 
    data: ByteArray, 
    callback: InnerCallback?
) {
    // 1. 检查状态
    if (state != ConnectionState.CONNECTED) {
        callback?.onError(InnerErrorCode.E304.code, "Not connected")
        return
    }
    
    // 2. 获取连接和端点
    val conn = connection
    val epOut = endpointOut
    
    if (conn == null || epOut == null) {
        callback?.onError(InnerErrorCode.E304.code, "Connection not available")
        return
    }
    
    // 3. 发送数据
    try {
        val result = conn.bulkTransfer(epOut, data, data.size, 3000)
        if (result < 0) {
            callback?.onError(InnerErrorCode.E304.code, "Bulk transfer failed")
        }
    } catch (e: Exception) {
        callback?.onError(InnerErrorCode.E304.code, e.message ?: "Send failed")
    }
}
```

### 数据接收

```kotlin
private fun startReceive() {
    receiveJob = scope.launch(Dispatchers.IO) {
        val buffer = ByteArray(16384)
        
        while (isActive && state == ConnectionState.CONNECTED) {
            val conn = connection
            val epIn = endpointIn
            
            if (conn == null || epIn == null) {
                break
            }
            
            try {
                val bytesRead = conn.bulkTransfer(epIn, buffer, buffer.size, 500)
                
                when {
                    bytesRead > 0 -> {
                        val data = buffer.copyOf(bytesRead)
                        dataReceiver?.invoke(data)
                    }
                    bytesRead < 0 -> {
                        // 传输错误，检查设备是否还在
                        if (!isDeviceStillConnected()) {
                            scope.launch { disconnect() }
                            break
                        }
                        delay(100)
                    }
                    else -> {
                        // 超时，继续
                        delay(10)
                    }
                }
            } catch (e: Exception) {
                if (isActive && state == ConnectionState.CONNECTED) {
                    if (!isDeviceStillConnected()) {
                        scope.launch { disconnect() }
                        break
                    }
                    delay(100)
                } else {
                    break
                }
            }
        }
    }
}
```

---

## 错误处理

### 三层断开检测

#### 1. 系统广播检测（< 100ms）

最快的检测方式，通过系统广播立即感知设备断开：

```kotlin
private val deviceReceiver = object : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                val device = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
                if (isSameDevice(device, currentDevice) && state == ConnectionState.CONNECTED) {
                    scope.launch { disconnect() }
                }
            }
        }
    }
}
```

#### 2. 传输错误检测（< 500ms）

通过 bulkTransfer 返回值检测：

```kotlin
val bytesRead = conn.bulkTransfer(epIn, buffer, buffer.size, 500)

if (bytesRead < 0) {
    // 传输错误
    if (!isDeviceStillConnected()) {
        scope.launch { disconnect() }
        break
    }
}
```

#### 3. 异常捕获检测（< 500ms）

捕获传输过程中的异常：

```kotlin
try {
    val bytesRead = conn.bulkTransfer(epIn, buffer, buffer.size, 500)
    // ...
} catch (e: Exception) {
    if (isActive && state == ConnectionState.CONNECTED) {
        if (!isDeviceStillConnected()) {
            scope.launch { disconnect() }
            break
        }
    }
}
```

### 错误码

| 错误码 | 描述 | 场景 |
|--------|------|------|
| E212 | 设备连接失败 | 无法打开设备、无法 claim 接口 |
| E251 | 未找到 USB 设备 | 系统中没有 USB 设备 |
| E252 | 权限被拒绝 | 用户拒绝 USB 权限请求 |
| E253 | 设备断开 | 设备在连接过程中断开 |
| E254 | AOA 协议失败 | 发送 AOA 协议失败 |
| E304 | 数据发送失败 | bulkTransfer 失败 |

---

## 使用示例

### 基本使用

```kotlin
class UsbHostKernelTestActivity : AppCompatActivity() {
    
    private var kernel: AsyncServiceKernel? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 创建 Kernel
        kernel = CableAoaHostKernel(
            appId = "demo_app_id",
            appSecretKey = "demo_secret_key",
            context = this
        ).apply {
            // 设置数据接收回调
            registerDataReceiver { data ->
                data?.let {
                    log("收到数据: ${it.size} bytes - ${String(it, Charsets.UTF_8)}")
                }
            }
            
            // 设置状态变化监听器
            setStatusChangeListener { status ->
                runOnUiThread {
                    updateButtonStatesByStatus(status)
                    log("状态变化: $status")
                }
            }
        }
    }
    
    // 连接设备
    private fun connectToDevice() {
        val protocol = "usb://"
        
        val callback = object : ConnectionCallback {
            override fun onWaitingConnect() {
                log("等待连接...")
            }
            
            override fun onConnected(extraInfoMap: Map<String, String?>?) {
                log("连接成功")
            }
            
            override fun onDisconnected(code: String, msg: String) {
                log("断开通知: [$code] $msg")
            }
        }
        
        scope.launch {
            try {
                kernel?.connect(protocol, callback)
            } catch (e: Exception) {
                log("连接异常: ${e.message}")
            }
        }
    }
    
    // 断开连接
    private fun disconnectFromDevice() {
        scope.launch {
            try {
                kernel?.disconnect()
                log("断开成功")
            } catch (e: Exception) {
                log("断开异常: ${e.message}")
            }
        }
    }
    
    // 发送数据
    private fun sendTestData() {
        val testData = "Hello from Host"
        
        kernel?.sendData(
            traceId = "test_${System.currentTimeMillis()}",
            data = testData.toByteArray(),
            callback = null
        )
    }
    
    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        scope.launch {
            kernel?.disconnect()
        }
    }
}
```

### AndroidManifest 配置

```xml
<manifest>
    <!-- USB 权限 -->
    <uses-permission android:name="android.permission.USB_PERMISSION" />
    <uses-feature android:name="android.hardware.usb.host" android:required="true" />
    
    <application>
        <activity
            android:name=".UsbHostKernelTestActivity"
            android:exported="false">
        </activity>
    </application>
</manifest>
```

---

## 最佳实践

### 1. 资源管理

始终在 Activity/Fragment 销毁时释放资源：

```kotlin
override fun onDestroy() {
    super.onDestroy()
    scope.cancel()
    scope.launch {
        kernel?.disconnect()
    }
}
```

### 2. 错误处理

提供友好的错误提示：

```kotlin
val callback = object : ConnectionCallback {
    override fun onDisconnected(code: String, msg: String) {
        when (code) {
            InnerErrorCode.E252.code -> {
                showToast("USB 权限被拒绝，请允许权限后重试")
            }
            InnerErrorCode.E251.code -> {
                showToast("未找到 USB 设备，请连接设备后重试")
            }
            else -> {
                showToast("连接失败: $msg")
            }
        }
    }
}
```

### 3. 状态同步

使用状态监听器同步 UI：

```kotlin
kernel?.setStatusChangeListener { status ->
    runOnUiThread {
        when (status) {
            InnerConnectionStatus.CONNECTED -> {
                btnConnect.isEnabled = false
                btnDisconnect.isEnabled = true
                btnSendData.isEnabled = true
            }
            InnerConnectionStatus.DISCONNECTED -> {
                btnConnect.isEnabled = true
                btnDisconnect.isEnabled = false
                btnSendData.isEnabled = false
            }
            else -> {
                // 连接中...
            }
        }
    }
}
```

### 4. 日志记录

使用结构化日志便于调试：

```kotlin
private fun log(message: String) {
    val timestamp = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date())
    val logMessage = "[$timestamp] $message\n"
    
    runOnUiThread {
        logTextView.append(logMessage)
        logScrollView.post {
            logScrollView.fullScroll(ScrollView.FOCUS_DOWN)
        }
    }
}
```

---

## 常见问题

### Q1: 为什么需要两次权限请求？

A: AOA 协议需要两次权限：
1. 第一次：请求原始设备权限，用于发送 AOA 协议
2. 第二次：请求 AOA 设备权限，用于数据通信

这是 AOA 协议的标准流程，因为设备在切换到 AOA 模式后，VID/PID 会改变，系统会将其视为新设备。

### Q2: 如何判断设备是否支持 AOA？

A: 使用 `UsbStandardProtocol.isAoaCompatible()` 检查：

```kotlin
val isAoa = UsbStandardProtocol.isAoaCompatible(device.vendorId, device.productId)
```

AOA 设备的特征：
- VID: 0x18D1 (Google)
- PID: 0x2D00 ~ 0x2D05

### Q3: 如何处理设备意外断开？

A: SDK 提供三层断开检测，会自动触发 `onDisconnected` 回调。应用层只需：

```kotlin
override fun onDisconnected(code: String, msg: String) {
    // 1. 更新 UI 状态
    updateUIToDisconnected()
    
    // 2. 提示用户
    showToast("设备已断开")
    
    // 3. 可选：自动重连
    if (autoReconnect) {
        scheduleReconnect()
    }
}
```

### Q4: 数据发送失败怎么办？

A: 检查以下几点：
1. 确认设备已连接（`state == CONNECTED`）
2. 检查数据大小（建议 < 16KB）
3. 检查超时设置（默认 3000ms）
4. 查看错误回调中的详细信息

```kotlin
kernel?.sendData(
    traceId = "test",
    data = data,
    callback = object : InnerCallback {
        override fun onError(code: String, msg: String) {
            log("发送失败: [$code] $msg")
        }
    }
)
```

---

## 技术细节

### AOA 协议命令序列

```
1. GET_PROTOCOL (51)
   ├─ 请求类型: USB_DIR_IN | USB_TYPE_VENDOR
   ├─ 返回: 2 字节协议版本
   └─ 用途: 查询设备是否支持 AOA

2. SEND_STRING (52) × 6
   ├─ 请求类型: USB_TYPE_VENDOR
   ├─ 索引 0: Manufacturer
   ├─ 索引 1: Model
   ├─ 索引 2: Description
   ├─ 索引 3: Version
   ├─ 索引 4: URI
   └─ 索引 5: Serial

3. START (53)
   ├─ 请求类型: USB_TYPE_VENDOR
   └─ 用途: 启动 Accessory 模式
```

### 端点配置

AOA 设备通常使用以下端点配置：

```
Interface:
  ├─ Class: 255 (Vendor Specific)
  ├─ Subclass: 66
  ├─ Protocol: 1
  └─ Endpoints:
      ├─ IN (可选): Bulk, 用于接收数据
      └─ OUT (必需): Bulk, 用于发送数据
```

### 性能优化

1. **批量传输**：使用 bulkTransfer 而非 controlTransfer
2. **缓冲区大小**：16KB 缓冲区平衡性能和内存
3. **超时设置**：接收 500ms，发送 3000ms
4. **协程调度**：接收使用 IO 调度器，避免阻塞主线程

---

## 参考资料

- [Android Open Accessory Protocol](https://source.android.com/devices/accessories/aoa)
- [USB Bulk Transfer](https://developer.android.com/reference/android/hardware/usb/UsbDeviceConnection#bulkTransfer)
- [Android USB Host](https://developer.android.com/guide/topics/connectivity/usb/host)

---

## 更新日志

| 日期 | 版本 | 说明 |
|------|------|------|
| 2025-01-19 | 1.0.0 | 初始版本，基于 CableAoaHostKernel 实现 |

---

**文档维护者**：TaPro Team  
**最后更新**：2025-01-19
