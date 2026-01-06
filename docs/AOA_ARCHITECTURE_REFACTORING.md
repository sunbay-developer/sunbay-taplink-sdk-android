# AOA架构重构 - 分离Host和Accessory功能

## 重构目标

将原来混合的`BaseUsbStandardKernel`重构为更清晰的层次结构，分离Host和Accessory模式的专用功能。

## 原有架构问题

### 1. 功能混合
`BaseUsbStandardKernel`包含了Host和Accessory模式的所有功能：
- Accessory专用：`accessoryInputStream`、`accessoryOutputStream`、`accessoryPermissionReceiver`
- Host专用：USB设备权限管理、bulkTransfer数据传输
- 共同功能：USB管理器、基础连接管理

### 2. 权限冲突
Host模式继承了Accessory权限接收器，导致双重授权问题。

### 3. 代码复杂性
通过`isHostMode`标志来控制行为，增加了代码复杂性。

## 新架构设计

```
AsyncServiceKernel
├── BaseUsbAoaKernel (共同的AOA基础功能)
│   ├── BaseUsbHostKernel (Host模式专用)
│   │   └── UsbStandardHostKernel
│   └── BaseUsbAccessoryKernel (Accessory模式专用)
│       └── UsbStandardAccessoryKernel
```

## 重构实现

### 1. BaseUsbAoaKernel (共同基类)

**职责**：
- USB管理器初始化
- 协议验证
- 基础连接管理
- 数据接收任务管理

**关键代码**：
```kotlin
abstract class BaseUsbAoaKernel(
    appId: String,
    appSecretKey: String,
    protected val context: Context,
    protected val usbStandardInfo: UsbStandardProtocol.UsbStandardInfo,
    protected val permissionAction: String,
    protected val permissionPendingIntent: PendingIntent,
) : AsyncServiceKernel(appId, appSecretKey) {

    protected val usbManager: UsbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    protected var dataReceiveJob: Job? = null

    // 抽象方法由子类实现
    protected abstract suspend fun performUsbConnect(parseResult: ProtocolParseResult.UsbProtocol, connectionCallback: ConnectionCallback)
    protected abstract fun startDataReceive()
    protected abstract fun disconnectInternal()
}
```

### 2. BaseUsbAccessoryKernel (Accessory专用基类)

**职责**：
- Accessory权限管理
- Accessory连接管理
- FileInputStream/FileOutputStream数据传输
- Accessory广播接收器

**关键功能**：
```kotlin
abstract class BaseUsbAccessoryKernel : BaseUsbAoaKernel {
    // Accessory连接相关
    protected var connectedAccessory: UsbAccessory? = null
    protected var accessoryFileDescriptor: ParcelFileDescriptor? = null
    protected var accessoryInputStream: FileInputStream? = null
    protected var accessoryOutputStream: FileOutputStream? = null

    // Accessory权限请求相关
    protected var pendingAccessory: UsbAccessory? = null
    protected val accessoryPermissionReceiver: BroadcastReceiver
    protected val accessoryReceiver: BroadcastReceiver

    // Accessory模式数据传输
    override fun startDataReceive() {
        // 使用FileInputStream接收数据
    }

    override fun performSendData() {
        // 使用FileOutputStream发送数据
    }
}
```

### 3. BaseUsbHostKernel (Host专用基类)

**职责**：
- USB设备操作
- 设备权限请求处理
- USB设备广播接收器
- bulkTransfer数据传输

**关键功能**：
```kotlin
abstract class BaseUsbHostKernel : BaseUsbAoaKernel {
    // USB设备连接相关
    protected var connectedDevice: UsbDevice? = null
    protected var usbDeviceConnection: UsbDeviceConnection? = null
    protected var usbInterface: UsbInterface? = null
    protected var usbEndpointIn: UsbEndpoint? = null
    protected var usbEndpointOut: UsbEndpoint? = null

    // 设备权限请求相关
    protected var pendingDevice: UsbDevice? = null
    protected val permissionReceiver: BroadcastReceiver
    protected val deviceReceiver: BroadcastReceiver

    // Host模式数据传输
    override fun performSendData() {
        // 使用bulkTransfer发送数据
    }

    protected abstract fun sendDataToDevice(data: ByteArray): Boolean
}
```

### 4. 具体实现类更新

**UsbStandardAccessoryKernel**：
```kotlin
class UsbStandardAccessoryKernel : BaseUsbAccessoryKernel {
    // 只需实现Accessory特有的连接逻辑
    override fun onAccessoryAttached(accessory: UsbAccessory) {
        // Accessory连接处理
    }
}
```

**UsbStandardHostKernel**：
```kotlin
class UsbStandardHostKernel : BaseUsbHostKernel {
    // 只需实现Host特有的连接逻辑和数据传输
    override fun sendDataToDevice(data: ByteArray): Boolean {
        return sendDataToAoaDevice(data)
    }

    override fun startDataReceive() {
        // 使用bulkTransfer接收数据
    }
}
```

## 重构优势

### 1. 职责分离
- **BaseUsbAoaKernel**：共同功能
- **BaseUsbAccessoryKernel**：Accessory专用功能
- **BaseUsbHostKernel**：Host专用功能

### 2. 消除权限冲突
- Host模式只注册设备权限接收器
- Accessory模式只注册Accessory权限接收器
- 不再需要`isHostMode`标志

### 3. 代码清晰性
- 每个类职责单一明确
- 减少条件判断逻辑
- 更容易维护和扩展

### 4. 数据传输方式分离
- **Accessory模式**：FileInputStream/FileOutputStream
- **Host模式**：bulkTransfer
- 各自使用最适合的传输方式

## 迁移影响

### 1. 删除旧文件
- 可以删除原来的`BaseUsbStandardKernel.kt`（如果不再需要）

### 2. 更新导入
- 更新相关类的import语句
- 确保所有引用都指向新的类

### 3. 测试验证
- 验证Host模式连接和数据传输
- 验证Accessory模式连接和数据传输
- 确保权限处理正确

## 总结

通过这次重构：

1. **解决了权限冲突问题**：Host和Accessory模式使用各自的权限接收器
2. **提高了代码质量**：职责分离，代码更清晰
3. **改善了可维护性**：每个类功能单一，易于理解和修改
4. **优化了性能**：减少不必要的条件判断

新架构更符合单一职责原则，为后续功能扩展提供了更好的基础。