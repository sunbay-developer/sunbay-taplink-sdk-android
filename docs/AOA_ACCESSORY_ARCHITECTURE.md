# Android AOA Accessory 模式架构文档

## 概述

本文档详细介绍 Taplink SDK 中 Android Open Accessory (AOA) Accessory 模式的实现架构。Accessory 模式允许 Android 设备作为 USB 配件，被其他 Android 设备（作为 Host）连接和控制。

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

### AOA Accessory 模式简介

在 AOA 协议中，Accessory 模式是被动角色：

- **Accessory 设备**：作为 USB 配件的 Android 设备，被动等待连接
- **Host 设备**：作为 USB 主机的 Android 设备，主动发起连接
- **自动启动**：当 Host 发送 AOA 协议后，Accessory 设备会自动切换到 Accessory 模式

### 关键特性

1. **被动等待连接**：Accessory 等待 Host 发起连接
2. **自动模式切换**：接收到 AOA 协议后自动切换
3. **权限管理**：处理 USB Accessory 权限请求
4. **区分主动/被动断开**：
   - 用户主动断开 → DISCONNECTED（停止等待）
   - Host 断开/拔线 → WAITING_CONNECT（继续等待）
5. **FileInputStream/FileOutputStream**：使用文件流进行数据传输
6. **状态机驱动**：简化的状态机管理连接生命周期

---

## 架构设计

### 整体架构

```
┌─────────────────────────────────────────────────────────────┐
│                     Application Layer                        │
│              (UsbAccessoryKernelTestActivity)                │
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
│                AOA Accessory Kernel Layer                    │
│                (CableAoaAccessoryKernel)                     │
│  ┌──────────────────────────────────────────────────────┐   │
│  │  • 状态机管理                                         │   │
│  │  • 被动等待连接                                       │   │
│  │  • 权限请求处理                                       │   │
│  │  • 区分主动/被动断开                                  │   │
│  │  • 数据收发（FileInputStream/FileOutputStream）      │   │
│  └──────────────────────────────────────────────────────┘   │
└────────────────────────┬────────────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────────┐
│                   Android USB Framework                      │
│         (UsbManager, UsbAccessory, ParcelFileDescriptor)     │
└─────────────────────────────────────────────────────────────┘
```

### 模块职责

#### 1. CableAoaAccessoryKernel（核心内核）

**位置**：`lib_taplink_communication/src/main/java/com/sunmi/tapro/taplink/communication/cable/aoa/accessory/CableAoaAccessoryKernel.kt`

**职责**：
- 实现简化的 AOA Accessory 连接逻辑
- 管理连接状态机
- 处理 Accessory 查找、权限请求
- 管理数据收发（FileInputStream/FileOutputStream）
- 处理 Accessory 插拔事件
- 区分主动/被动断开

**核心特性**：
- 单一连接入口（`connect()`）
- 幂等操作（重复调用安全）
- 状态即真相（状态变量直接反映资源状态）
- 移除 session/token 机制，使用简单状态机
- 被动断开后自动进入等待状态

#### 2. AsyncServiceKernel（基类）

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
    └── CableAoaAccessoryKernel (Accessory 模式实现)
        │
        ├── 状态机：ConnectionState
        │   ├── IDLE（空闲）
        │   ├── REQUESTING_PERMISSION（请求权限）
        │   ├── CONNECTING（连接中）
        │   ├── CONNECTED（已连接）
        │   ├── DISCONNECTING（断开中）
        │   └── ERROR（错误）
        │
        ├── 资源管理：
        │   ├── currentAccessory: UsbAccessory?
        │   ├── fileDescriptor: ParcelFileDescriptor?
        │   ├── inputStream: FileInputStream?
        │   └── outputStream: FileOutputStream?
        │
        └── 广播接收器：
            ├── permissionReceiver（权限响应）
            └── accessoryReceiver（Accessory 插拔）
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
│  1. 设置状态为 WAITING_CONNECT                           │
│     - 通知应用层正在等待                                 │
└────────┬────────────────────────────────────────────────┘
         │
         ▼
┌─────────────────────────────────────────────────────────┐
│  2. 持续查找 Accessory（循环）                           │
│     - 每 500ms 检查一次                                  │
│     - 直到找到 Accessory 或用户取消                      │
│     - 也可通过 ATTACHED 广播触发                         │
└────────┬────────────────────────────────────────────────┘
         │
         ▼
    ┌───┴───┐
    │ 找到? │
    └───┬───┘
        │
    ┌───┴───────────────────────────────────────┐
    │                                           │
   是                                          否
    │                                           │
    ▼                                           ▼
┌─────────────────┐                  ┌─────────────────────┐
│  3. 检查权限     │                  │  继续等待            │
│     - 有权限：   │                  │  （返回步骤 2）      │
│       直接连接   │                  └─────────────────────┘
│     - 无权限：   │
│       请求权限   │
└────────┬────────┘
         │
         ▼
┌─────────────────────────────────────────────────────────┐
│  4. 连接到 Accessory                                     │
│     - 打开 Accessory                                     │
│     - 创建 FileInputStream/FileOutputStream              │
│     - 保存资源                                           │
└────────┬────────────────────────────────────────────────┘
         │
         ▼
┌─────────────────────────────────────────────────────────┐
│  5. 启动数据接收                                         │
│     - 启动接收协程（FileInputStream.read）               │
│     - 回调成功                                           │
└─────────────────────────────────────────────────────────┘
```

### 详细步骤说明

#### 步骤 1：设置等待状态

```kotlin
private suspend fun startConnection(callback: ConnectionCallback) {
    try {
        // 设置为等待连接状态
        updateStatus(InnerConnectionStatus.WAITING_CONNECT)
        
        // 通知应用层正在等待连接
        callback.onWaitingConnect()
        
        // 持续查找 Accessory，直到找到或被取消
        val accessory = waitForAccessory()
        if (accessory == null) {
            // 被取消或超时
            LogUtil.i(TAG, "Accessory wait cancelled or timed out")
            return
        }
        
        // ...
    } catch (e: Exception) {
        handleConnectionError("Connection failed: ${e.message}", callback, InnerErrorCode.E212.code)
    }
}
```

#### 步骤 2：持续查找 Accessory

```kotlin
/**
 * 等待 Accessory 出现
 * 
 * 持续查找 Accessory，直到找到或状态改变或用户主动断开
 * 
 * @return 找到的 Accessory，如果被取消则返回 null
 */
private suspend fun waitForAccessory(): UsbAccessory? {
    val checkInterval = 500L  // 每 500ms 检查一次
    
    LogUtil.i(TAG, "Waiting for USB accessory to appear...")
    
    while (state == ConnectionState.IDLE && shouldKeepWaiting) {
        // 检查是否有 Accessory
        val accessory = findAccessory()
        if (accessory != null) {
            LogUtil.i(TAG, "Accessory found: ${getAccessoryDisplayKey(accessory)}")
            return accessory
        }
        
        // 等待一段时间后再次检查
        delay(checkInterval)
    }
    
    // 状态改变或用户主动断开，返回 null
    if (!shouldKeepWaiting) {
        LogUtil.i(TAG, "Accessory wait cancelled by user")
    }
    return null
}
```

**重要特性**：
- **持续等待**：每 500ms 检查一次，直到找到 Accessory
- **可取消**：用户调用 `disconnect()` 会设置 `shouldKeepWaiting=false`，循环自动退出
- **双重触发**：既可以通过轮询发现，也可以通过 `ATTACHED` 广播触发
- **智能控制**：只有在 `shouldKeepWaiting=true` 时才会轮询

```kotlin
private fun findAccessory(): UsbAccessory? {
    val accessories = usbManager.accessoryList
    
    if (accessories == null || accessories.isEmpty()) {
        LogUtil.w(TAG, "No USB accessories found")
        return null
    }
    
    LogUtil.d(TAG, "Found ${accessories.size} USB accessory(ies)")
    
    // 返回第一个 Accessory
    return accessories.firstOrNull()
}
```

#### 步骤 3：权限请求

```kotlin
private suspend fun requestPermissionAndWait(accessory: UsbAccessory) {
    permissionLatch = CountDownLatch(1)
    permissionDenied = false
    pendingPermissionAccessory = accessory
    
    val intent = createPermissionIntent(accessory)
    usbManager.requestPermission(accessory, intent)
    
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

#### 步骤 4：连接到 Accessory

```kotlin
private suspend fun connectToAccessory(accessory: UsbAccessory, callback: ConnectionCallback) {
    synchronized(stateLock) {
        if (state == ConnectionState.CONNECTED) {
            handleConnectionSuccess(callback, null)
            return
        }
        state = ConnectionState.CONNECTING
    }
    
    try {
        // 1. 打开 Accessory
        val fd = usbManager.openAccessory(accessory)
            ?: throw Exception("Failed to open accessory")
        
        // 2. 创建输入输出流
        val input = FileInputStream(fd.fileDescriptor)
        val output = FileOutputStream(fd.fileDescriptor)
        
        // 3. 保存资源
        synchronized(stateLock) {
            currentAccessory = accessory
            fileDescriptor = fd
            inputStream = input
            outputStream = output
            state = ConnectionState.CONNECTED
            updateStatus(InnerConnectionStatus.CONNECTED)
        }
        
        // 4. 启动接收
        startReceive()
        
        // 5. 回调成功
        handleConnectionSuccess(callback, null)
        
    } catch (e: Exception) {
        cleanupResources()
        handleConnectionError("Failed to connect: ${e.message}", callback, InnerErrorCode.E212.code)
    }
}
```

---

## 状态管理

### 状态定义

```kotlin
private enum class ConnectionState {
    IDLE,                    // 空闲，未连接
    REQUESTING_PERMISSION,   // 正在请求权限
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
  ├─> REQUESTING_PERMISSION ──> CONNECTING ──> CONNECTED   │
  │                                              │          │
  └──────────────────────────────────────────────┘          │
                    (disconnect)                            │
                                                            │
  ┌─────────────────────────────────────────────────────────┘
  │  (被动断开 → WAITING_CONNECT)
  │  (主动断开 → DISCONNECTED)
  └─> IDLE
```

### 主动 vs 被动断开

这是 Accessory 模式的核心特性，通过 `shouldKeepWaiting` 标志控制：

```kotlin
// 是否应该继续等待连接（用于区分主动/被动断开）
@Volatile
private var shouldKeepWaiting: Boolean = false

/**
 * 断开连接（内部方法，支持区分主动/被动断开）
 * 
 * @param isUserInitiated true=用户主动断开，false=Host断开/拔线
 */
private fun performDisconnect(isUserInitiated: Boolean) {
    // 清理资源
    cleanupResources()
    
    synchronized(stateLock) {
        state = ConnectionState.IDLE
        
        if (isUserInitiated) {
            // 用户主动断开：停止等待
            shouldKeepWaiting = false
            updateStatus(InnerConnectionStatus.DISCONNECTED)
            LogUtil.i(TAG, "User initiated disconnect, status set to DISCONNECTED")
        } else {
            // Host 断开/拔线：继续等待（如果 shouldKeepWaiting=true）
            if (shouldKeepWaiting) {
                updateStatus(InnerConnectionStatus.WAITING_CONNECT)
                LogUtil.i(TAG, "Host disconnected, will continue waiting")
                
                // 重新启动等待流程
                scope.launch {
                    startConnection(currentCallback)
                }
            } else {
                updateStatus(InnerConnectionStatus.DISCONNECTED)
            }
        }
    }
}
```

**使用场景**：

| 场景 | 调用方式 | shouldKeepWaiting | 结果状态 | 说明 |
|------|---------|-------------------|---------|------|
| 用户点击"连接" | `connect()` | true | WAITING_CONNECT | 开始等待并持续轮询 |
| 用户点击"断开" | `disconnect()` | false | DISCONNECTED | 停止等待，停止轮询 |
| Host 拔线 | `performDisconnect(false)` | 保持 true | WAITING_CONNECT | 继续等待，继续轮询 |
| Accessory detach | `performDisconnect(false)` | 保持 true | WAITING_CONNECT | 继续等待，继续轮询 |
| release() | `release()` | false | IDLE | 完全停止 |

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
    
    // 2. 获取输出流
    val output = outputStream
    
    if (output == null) {
        callback?.onError(InnerErrorCode.E304.code, "Output stream not available")
        return
    }
    
    // 3. 发送数据
    try {
        output.write(data)
        output.flush()
        LogUtil.d(TAG, "✅ Data sent successfully: ${data.size} bytes")
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
            val input = inputStream
            
            if (input == null) {
                break
            }
            
            try {
                // 使用带超时的阻塞读取
                val bytesRead = withTimeoutOrNull(500) {
                    input.read(buffer)
                }
                
                when {
                    bytesRead == null -> {
                        // 超时，继续下一次循环
                        delay(10)
                    }
                    bytesRead > 0 -> {
                        val data = buffer.copyOf(bytesRead)
                        dataReceiver?.invoke(data)
                    }
                    else -> {
                        // bytesRead < 0: 流结束
                        LogUtil.w(TAG, "Input stream ended, stopping receive")
                        break
                    }
                }
            } catch (e: java.io.InterruptedIOException) {
                if (!isActive || state != ConnectionState.CONNECTED) {
                    break
                }
            } catch (e: java.io.IOException) {
                if (isActive && state == ConnectionState.CONNECTED) {
                    LogUtil.e(TAG, "IO error during receive: ${e.message}")
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

### 断开检测

Accessory 模式通过以下方式检测断开：

#### 1. 系统广播检测

通过 `ACTION_USB_ACCESSORY_DETACHED` 广播检测：

```kotlin
private val accessoryReceiver = object : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            UsbManager.ACTION_USB_ACCESSORY_DETACHED -> {
                val accessory = intent.getParcelableExtra<UsbAccessory>(UsbManager.EXTRA_ACCESSORY)
                
                if (isSameAccessory(accessory, currentAccessory) && 
                    state == ConnectionState.CONNECTED) {
                    // Host 断开，触发被动断开
                    scope.launch {
                        performDisconnect(isUserInitiated = false)
                    }
                }
            }
        }
    }
}
```

#### 2. 流结束检测

通过 `FileInputStream.read()` 返回值检测：

```kotlin
val bytesRead = input.read(buffer)

when {
    bytesRead < 0 -> {
        // 流结束，Host 已断开
        LogUtil.w(TAG, "Input stream ended, stopping receive")
        break
    }
}
```

#### 3. IO 异常检测

捕获读写过程中的 IO 异常：

```kotlin
try {
    val bytesRead = input.read(buffer)
    // ...
} catch (e: java.io.IOException) {
    if (isActive && state == ConnectionState.CONNECTED) {
        LogUtil.e(TAG, "IO error during receive: ${e.message}")
        // 可能是临时错误，延迟后继续
        delay(100)
    } else {
        break
    }
}
```

### 错误码

| 错误码 | 描述 | 场景 |
|--------|------|------|
| E212 | Accessory 连接失败 | 无法打开 Accessory |
| E252 | 权限被拒绝 | 用户拒绝 USB 权限请求 |
| E253 | Accessory 断开 | Accessory 在连接过程中断开 |
| E304 | 数据发送失败 | 写入流失败 |

---

## 使用示例

### 基本使用

```kotlin
class UsbAccessoryKernelTestActivity : AppCompatActivity() {
    
    private var kernel: AsyncServiceKernel? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 创建 Kernel
        kernel = CableAoaAccessoryKernel(
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
    
    // 连接（等待 Host）
    private fun connectToAccessory() {
        val protocol = "usb://"
        
        val callback = object : ConnectionCallback {
            override fun onWaitingConnect() {
                log("等待 Host 连接...")
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
    private fun disconnectFromAccessory() {
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
        val testData = "Hello from Accessory"
        
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
    <uses-feature android:name="android.hardware.usb.accessory" android:required="true" />
    
    <application>
        <activity
            android:name=".UsbAccessoryKernelTestActivity"
            android:exported="true"
            android:launchMode="singleTask">
            <!-- 重要：监听 USB_ACCESSORY_ATTACHED 事件 -->
            <intent-filter>
                <action android:name="android.hardware.usb.action.USB_ACCESSORY_ATTACHED" />
            </intent-filter>
            
            <!-- 重要：指定 Accessory 过滤器 -->
            <meta-data
                android:name="android.hardware.usb.action.USB_ACCESSORY_ATTACHED"
                android:resource="@xml/accessory_filter" />
        </activity>
    </application>
</manifest>
```

### Accessory 过滤器配置

**文件位置**：`res/xml/accessory_filter.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <!-- USB Accessory 过滤器配置 -->
    <!-- 必须与 UsbStandardProtocol.UsbStandardInfo 的默认值一致 -->
    <usb-accessory
        manufacturer="SUNMI"
        model="TAPLINK_SDK"
        version="1.0" />
</resources>
```

**重要**：这些值必须与 Host 发送的 AOA 协议中的值完全一致！

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

### 2. 状态同步

根据不同状态更新 UI：

```kotlin
private fun updateButtonStatesByStatus(status: InnerConnectionStatus) {
    when (status) {
        InnerConnectionStatus.DISCONNECTED -> {
            btnConnect.isEnabled = true
            btnConnect.text = "连接"
            btnDisconnect.isEnabled = false
            btnSendData.isEnabled = false
        }
        InnerConnectionStatus.WAITING_CONNECT -> {
            btnConnect.isEnabled = false
            btnConnect.text = "等待中..."
            btnDisconnect.isEnabled = true  // 可以取消等待
            btnDisconnect.text = "取消等待"
            btnSendData.isEnabled = false
        }
        InnerConnectionStatus.CONNECTED -> {
            btnConnect.isEnabled = false
            btnConnect.text = "已连接"
            btnDisconnect.isEnabled = true
            btnDisconnect.text = "断开"
            btnSendData.isEnabled = true
        }
        else -> {
            // 连接中...
        }
    }
}
```

### 3. 区分断开类型

提供不同的用户提示：

```kotlin
val callback = object : ConnectionCallback {
    override fun onDisconnected(code: String, msg: String) {
        when {
            msg.contains("User disconnected") -> {
                showToast("已断开连接")
            }
            msg.contains("Host disconnected") -> {
                showToast("Host 已断开，等待下次连接")
            }
            else -> {
                showToast("连接断开: $msg")
            }
        }
    }
}
```

### 4. 自动启动

配置 Activity 在 Accessory 连接时自动启动：

```xml
<activity
    android:name=".UsbAccessoryKernelTestActivity"
    android:exported="true"
    android:launchMode="singleTask">
    <intent-filter>
        <action android:name="android.hardware.usb.action.USB_ACCESSORY_ATTACHED" />
    </intent-filter>
    <meta-data
        android:name="android.hardware.usb.action.USB_ACCESSORY_ATTACHED"
        android:resource="@xml/accessory_filter" />
</activity>
```

---

## 常见问题

### Q1: 为什么需要配置 accessory_filter.xml？

A: Accessory 过滤器用于：
1. 告诉系统哪些 Accessory 是你的应用支持的
2. 当匹配的 Accessory 连接时，自动启动你的应用
3. 过滤器的值必须与 Host 发送的 AOA 协议信息完全一致

### Q2: 主动断开和被动断开有什么区别？

A: 

**主动断开**（用户操作）：
- 状态变为 `DISCONNECTED`
- 停止等待，完全断开
- 需要用户再次点击"连接"才会重新等待

**被动断开**（Host 断开/拔线）：
- 状态变为 `WAITING_CONNECT`
- 继续等待下次连接
- Host 重新连接时自动建立连接

### Q3: 如何处理 Host 临时断开？

A: SDK 内置了延迟检查机制（300ms），避免因临时 USB 信号问题导致误断开：

```kotlin
// 延迟检查，避免误断开
scope.launch {
    delay(300)
    
    // 再次检查设备是否真的断开
    val stillExists = checkAccessoryStillExists()
    
    if (!stillExists) {
        // 确认断开
        performDisconnect(isUserInitiated = false)
    } else {
        // 临时信号问题，忽略
        LogUtil.d(TAG, "Accessory still exists, ignoring detach event")
    }
}
```

### Q4: 为什么使用 FileInputStream/FileOutputStream？

A: Accessory 模式使用文件流是因为：
1. Android 系统为 Accessory 提供的是 `ParcelFileDescriptor`
2. 文件流提供了简单的阻塞式 I/O 接口
3. 相比 bulkTransfer，文件流更适合 Accessory 的被动角色

### Q5: 如何调试 Accessory 连接问题？

A: 检查以下几点：

1. **Manifest 配置**：
   - 是否声明了 `usb.accessory` feature
   - 是否配置了 `USB_ACCESSORY_ATTACHED` intent-filter
   - 是否指定了 `accessory_filter` meta-data

2. **过滤器匹配**：
   - `accessory_filter.xml` 的值是否与 Host 的 AOA 协议一致
   - manufacturer、model、version 必须完全匹配

3. **权限**：
   - 是否授予了 USB 权限
   - 检查权限请求是否成功

4. **日志**：
   - 查看 `SimplifiedAoaAccessoryKernel` 的日志
   - 检查状态转换是否正常

---

## 技术细节

### Accessory 识别

Accessory 通过以下信息识别：

```kotlin
data class AccessoryInfo(
    val manufacturer: String,  // 制造商
    val model: String,         // 型号
    val version: String,       // 版本
    val serial: String?        // 序列号（可选）
)
```

**注意**：`serial` 在某些情况下可能无法访问（权限问题），因此不应作为主要识别依据。

### 文件流特性

FileInputStream/FileOutputStream 的特点：

1. **阻塞式读取**：`read()` 会阻塞直到有数据或流结束
2. **流结束标志**：返回 -1 表示流结束（Host 断开）
3. **缓冲区大小**：建议使用 16KB 缓冲区
4. **超时处理**：使用 `withTimeoutOrNull` 避免永久阻塞

### 性能优化

1. **IO 调度器**：接收使用 `Dispatchers.IO`，避免阻塞主线程
2. **缓冲区复用**：使用固定大小的缓冲区，减少内存分配
3. **批量写入**：使用 `flush()` 确保数据及时发送
4. **错误重试**：发送失败时自动重试（最多 3 次）

---

## Host vs Accessory 对比

| 特性 | Host 模式 | Accessory 模式 |
|------|----------|---------------|
| 角色 | 主动发起连接 | 被动等待连接 |
| 设备查找 | 主动查找设备 | 等待 Host 连接 |
| AOA 协议 | 发送 AOA 协议 | 接收 AOA 协议（自动） |
| 数据传输 | bulkTransfer | FileInputStream/FileOutputStream |
| 断开行为 | 断开后停止 | 被动断开后继续等待 |
| 权限请求 | 需要两次（原始设备 + AOA 设备） | 需要一次 |
| 自动启动 | 不支持 | 支持（通过 intent-filter） |
| 适用场景 | POS 主机、控制端 | POS 外设、被控端 |

---

## 参考资料

- [Android Open Accessory Protocol](https://source.android.com/devices/accessories/aoa)
- [USB Accessory](https://developer.android.com/guide/topics/connectivity/usb/accessory)
- [ParcelFileDescriptor](https://developer.android.com/reference/android/os/ParcelFileDescriptor)

---

## 更新日志

| 日期 | 版本 | 说明 |
|------|------|------|
| 2025-01-19 | 1.0.0 | 初始版本，基于 CableAoaAccessoryKernel 实现 |

---

**文档维护者**：Tapro Team  
**最后更新**：2025-01-19
