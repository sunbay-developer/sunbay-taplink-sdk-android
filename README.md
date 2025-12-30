# TaplinkDemo

SUNBAY Taplink SDK 支付集成演示应用，展示如何集成和使用 SUNBAY Taplink SDK 进行支付交易处理。

> 🚀 **快速体验**: 下载 [APK文件](app/release/TaplinkDemo-release-1.0.0.apk) 直接安装，或观看演示视频了解功能

## 项目介绍

TaplinkDemo 是由商米提供的支付 SDK 集成示例应用，演示如何使用 Taplink SDK 在 Android 应用中实现各种支付交易功能。该应用采用原生 Android 开发方式，使用 XML 布局和基于 Activity 的架构。

### 📦 演示资源说明

本项目提供了完整的演示资源，方便开发者快速了解和体验：

- **[TaplinkDemo-debug-1.0.0.apk](app/debug/TaplinkDemo-debug-1.0.0.apk)** - 预编译的演示应用
  - 可直接安装在 Android 7.1+ 设备上
  - 包含完整的支付功能演示
  - 支持 App-to-App 模式连接

- **[Tapro [standalone] - preview_uat_v1.0.0.85(develop).apk](Tapro%20%5Bstandalone%5D%20-%20preview_uat_v1.0.0.85%28develop%29.apk)** - Tapro 支付终端应用
  - 版本: v1.0.0.85 (develop)
  - **必须安装**: App-to-App 模式需要Tapro应用处理支付
  - 需要将设备 SN 绑定到 SUNBAY 平台才能正常使用
  - 与 TaplinkDemo 配合使用完成支付交易

- **功能演示**: 

![功能演示GIF](./taplinkdemo.gif)

> 📹 **演示说明**: 
> - 上方GIF展示了完整的应用操作流程
> - 如需查看高清版本，可下载 [完整视频文件](./taplinkdemo.mp4)

  - 展示完整的支付流程操作
  - App-to-App 模式连接演示
  - 交易历史和详情查看

## 功能特性

### 支付交易功能
- **销售交易 (SALE)** - 标准支付交易，支持附加金额
- **预授权交易 (AUTH)** - 预授权交易
- **强制授权 (FORCED_AUTH)** - 强制授权交易
- **退款交易 (REFUND)** - 退款操作
- **撤销交易 (VOID)** - 交易撤销
- **预授权完成 (POST_AUTH)** - 预授权完成
- **增量授权 (INCREMENTAL_AUTH)** - 增量授权
- **小费调整 (TIP_ADJUST)** - 小费金额调整
- **查询交易 (QUERY)** - 根据请求ID查询交易状态
- **批次结算 (BATCH_CLOSE)** - 日终批次结算

### 连接模式
- **App-to-App 模式** - 同设备集成（已实现）
- **Cable 模式** - USB线缆连接，支持多种协议（AUTO、USB_AOA、USB_VSP、RS232）
- **LAN 模式** - 局域网连接（已实现）

### 技术特性
- **原生 Android UI** - 使用 XML 布局和 Material Design
- **基于 Activity 的架构** - 清晰的UI层次结构
- **交易历史管理** - 完整的交易记录和状态跟踪
- **连接配置管理** - 支持多种连接模式的配置和切换
- **实时状态更新** - 支付进度和连接状态实时显示
- **简单错误处理** - 直接显示SDK错误信息，用户友好的对话框
- **附加金额支持** - 支持附加费、小费、税费、返现、服务费
- **Cable协议配置** - 支持AUTO、USB_AOA、USB_VSP、RS232协议选择
- **网络连接检测** - LAN模式的网络状态监控

## 技术栈

### 开发环境
- **Kotlin**: 2.2.21
- **Android Gradle Plugin**: 8.13.1
- **Compile SDK**: 35
- **Min SDK**: 25
- **Target SDK**: 35
- **JVM Target**: 11

### 核心依赖
- **Taplink SDK**: 1.0.1
- **AndroidX Core KTX**: 1.16.0
- **AndroidX AppCompat**: 1.7.1
- **Material Components**: 1.12.0
- **ConstraintLayout**: 2.1.4
- **Kotlin Coroutines**: 1.8.1
- **Lifecycle Runtime KTX**: 2.9.2
- **Gson**: 2.13.1
- **Java-WebSocket**: 1.5.3

## 项目结构

```
app/src/main/java/com/sunmi/tapro/taplink/demo/
├── TaplinkDemoApplication.kt          # 应用程序类
├── activity/                          # Activity 实现
│   ├── MainActivity.kt               # 主页面 - 支付交易
│   ├── ConnectionActivity.kt         # 连接配置
│   ├── TransactionListActivity.kt    # 交易历史列表
│   └── TransactionDetailActivity.kt  # 交易详情页面
├── adapter/                          # 列表适配器
│   └── TransactionAdapter.kt         # 交易列表适配器
├── model/                            # 数据模型
│   ├── Transaction.kt                # 交易记录模型
│   ├── TransactionType.kt            # 交易类型枚举
│   └── TransactionStatus.kt          # 交易状态枚举
├── repository/                       # 数据仓库
│   └── TransactionRepository.kt      # 交易数据管理
├── service/                          # SDK 集成层
│   ├── PaymentService.kt             # 支付服务接口
│   └── TaplinkPaymentService.kt     # 统一支付服务实现
└── util/                             # 工具类
    ├── ConnectionPreferences.kt      # 连接配置管理
    └── NetworkUtils.kt               # 网络工具类
```

### 资源目录结构

```
app/src/main/res/
├── layout/                           # XML 布局文件
│   ├── activity_main.xml             # 主页面布局
│   ├── activity_connection.xml       # 连接配置布局
│   ├── activity_transaction_list.xml # 交易列表布局
│   ├── activity_transaction_detail.xml # 交易详情布局
│   ├── item_transaction.xml          # 交易列表项布局
│   └── dialog_additional_amounts.xml # 附加金额对话框布局
├── values/                           # 资源值
│   ├── strings.xml                   # 字符串资源
│   ├── colors.xml                    # 颜色资源
│   ├── themes.xml                    # 主题配置
│   ├── arrays.xml                    # 数组资源
│   └── config.xml                    # SDK 配置
└── drawable/                         # 图像资源
```

## 演示资源

### 📱 APK 下载

#### TaplinkDemo 演示应用
- **演示应用APK**: [TaplinkDemo-debug-1.0.0.apk](app/debug/TaplinkDemo-debug-1.0.0.apk)
  - 版本: 1.0.0 Debug
  - 最小SDK: 25 (Android 7.1)
  - 目标SDK: 35 (Android 15)
  - 直接安装即可体验完整功能

#### Tapro 支付终端应用
- **Tapro应用APK**: [Tapro [standalone] - preview_uat_v1.0.0.97(develop).apk](Tapro%20%5Bstandalone%5D%20-%20preview_uat_v1.0.0.97%28develop%29.apk)
  - 版本: v1.0.0.97 (develop)
  - **重要**: 使用 App-to-App 模式必须安装此应用
  - 安装后需要将设备 SN 绑定到 SUNBAY 平台
  - 负责处理实际的支付交易操作

## 快速开始

### 环境要求
- Android Studio Ladybug | 2024.2.1 或更高版本
- JDK 11 或更高版本
- Android SDK 35
- Gradle 8.x

### 构建步骤

1. **克隆项目**
```bash
git clone <repository-url>
cd TaplinkDemo
```

2. **配置本地环境**
确保 `local.properties` 文件包含正确的 SDK 路径：
```properties
sdk.dir=/path/to/your/Android/sdk
```

3. **同步依赖**
在 Android Studio 中打开项目，等待 Gradle 同步完成。

4. **构建项目**
```bash
./gradlew build
```

5. **运行应用**
```bash
./gradlew installDebug
```
或在 Android Studio 中直接运行。

## 连接模式详解

本演示应用实现了多种连接模式，支持不同的集成场景：

### App-to-App 模式

**同设备集成解决方案**，适用于单台设备运行商户应用和支付终端应用的场景。

#### 工作原理
1. **Demo App**（本应用）- 发起支付请求的商户应用
2. **Tapro App** - 处理支付的终端应用
3. 两个应用运行在同一台 Android 设备上
4. 通过 Android Intent 机制进行通信

#### 支付流程
1. 用户在 Demo App 中选择金额和交易类型
2. Demo App 通过 Intent 向 Tapro App 发送支付请求
3. Tapro App 启动并处理支付
4. 用户在 Tapro App 中完成支付
5. Tapro App 将结果返回给 Demo App
6. Demo App 显示交易结果

#### 要求
- Demo App 和 Tapro App 必须安装在同一设备上
- 应用必须使用兼容的证书签名
- 有效的 Taplink SDK 凭据（appId、merchantId、secretKey）

### Cable 模式

**USB线缆连接模式**，适用于商户设备与独立支付终端通过USB线缆连接的场景。

#### 支持的协议
- **USB AOA** (Android Open Accessory 2.0) - Android设备作为配件模式
- **USB VSP** (Virtual Serial Port/CDC-ACM) - USB虚拟串口通信
- **RS232** - 标准串行通信协议
- **自动检测** - SDK自动识别并选择最佳协议

#### 工作原理
1. 商户设备通过USB线缆连接到支付终端
2. SDK自动检测连接类型和协议
3. 建立串行通信通道
4. 通过协议栈进行支付数据交换

#### 配置要求
- USB线缆物理连接
- 支持的USB协议（AOA/VSP/RS232）
- 正确的设备权限配置

### LAN 模式

**局域网连接模式**，适用于商户设备与支付终端在同一网络环境下通过IP网络通信的场景。

#### 工作原理
1. 商户设备和支付终端连接到同一局域网
2. 通过IP地址和端口建立TCP连接
3. 使用WebSocket或HTTP协议进行通信
4. 支持设备自动发现和手动配置

#### 配置参数
- **IP地址** - 支付终端的局域网IP地址
- **端口** - 通信端口（默认8443）
- **网络类型** - WiFi、以太网或移动网络

#### 网络要求
- 设备必须连接到网络（WiFi/以太网/移动网络）
- 商户设备和支付终端在同一网络段
- 网络防火墙允许相应端口通信
- 稳定的网络连接以确保交易可靠性

#### 自动连接功能
- **设备发现** - 自动扫描网络中的支付终端
- **配置缓存** - 保存成功连接的设备信息
- **智能重连** - 网络恢复后自动重新连接
- **连接监控** - 实时监控网络状态和设备可达性

## 权限要求

应用需要以下权限以支持不同的连接模式：

### 基础权限
- `INTERNET` - 网络访问（LAN模式必需）
- `ACCESS_NETWORK_STATE` - 网络状态检测（LAN模式必需）
- `ACCESS_WIFI_STATE` - WiFi状态检测（LAN模式推荐）
- `CHANGE_WIFI_STATE` - WiFi状态修改（LAN模式可选）

### USB连接权限（Cable模式）
- `USB_PERMISSION` - USB设备访问
- `HARDWARE_USB_HOST` - USB主机模式支持
- `HARDWARE_USB_ACCESSORY` - USB配件模式支持

### 蓝牙权限（未来扩展）
- `BLUETOOTH` - 蓝牙访问
- `BLUETOOTH_ADMIN` - 蓝牙管理

### 权限说明
- **网络权限** - LAN模式需要访问网络进行TCP通信
- **USB权限** - Cable模式需要访问USB设备进行串行通信
- **WiFi权限** - 用于网络状态监控和连接管理
- **蓝牙权限** - 为未来的蓝牙连接模式预留

## 使用说明

### 0. 快速体验（推荐）

如果您想快速体验应用功能，可以：

1. **安装必要的APK文件**:
   - 下载并安装 [TaplinkDemo-debug-1.0.0.apk](app/debug/TaplinkDemo-debug-1.0.0.apk) - 演示应用
   - 下载并安装 [Tapro [standalone] - preview_uat_v1.0.0.97(develop).apk](Tapro%20%5Bstandalone%5D%20-%20preview_uat_v1.0.0.97%28develop%29.apk) - Tapro支付终端应用

2. **设备绑定**: 将您的设备 SN 绑定到 SUNBAY 平台（联系技术支持获取绑定方法）

3. **配置SDK凭据**: 按照下面的步骤配置您自己的SDK凭据

### 1. 配置 SDK 凭据

编辑 `app/src/main/res/values/config.xml` 并填入您的 Taplink SDK 凭据：

```xml
<resources>
    <!-- Application identifier - Assigned by Taplink platform -->
    <string name="taplink_app_id">your_app_id</string>

    <!-- Merchant identifier - Assigned by Taplink platform -->
    <string name="taplink_merchant_id">your_merchant_id</string>

    <!-- Secret key - Used for signature verification, assigned by Taplink platform -->
    <string name="taplink_secret_key">your_secret_key</string>
</resources>
```

### 2. SDK 初始化

SDK 在应用启动时在 `TaplinkDemoApplication` 中自动初始化：

```kotlin
class TaplinkDemoApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        initializeTaplinkSDK()
    }
    
    private fun initializeTaplinkSDK() {
        val config = TaplinkConfig(
            appId = getString(R.string.taplink_app_id),
            merchantId = getString(R.string.taplink_merchant_id),
            secretKey = getString(R.string.taplink_secret_key)
        ).setLogEnabled(true)
         .setLogLevel(LogLevel.DEBUG)
         .setConnectionMode(ConnectionMode.APP_TO_APP)
        
        TaplinkSDK.init(this, config)
    }
}
```

### 3. 执行交易

1. **启动应用** - 应用将自动以 App-to-App 模式连接到 Tapro
2. **选择金额** - 使用预设金额按钮或输入自定义金额
3. **选择交易类型** - 点击 Sale 或 Auth 按钮
4. **完成支付** - 应用将启动 Tapro 应用进行支付处理
5. **查看结果** - 返回演示应用查看交易结果

### 4. 查看交易历史

- 点击主页面的"交易历史"按钮
- 查看所有交易记录及状态
- 点击任意交易查看详情
- 使用"查询交易"检查交易状态
- 使用"批次结算"进行日终结算

### 5. 连接设置

- 点击主页面的"设置"按钮
- 选择连接模式：
  - **App-to-App** - 同设备集成（默认）
  - **Cable** - USB线缆连接
  - **LAN** - 局域网连接
- 根据选择的模式配置相应参数：
  - **LAN模式** - 输入支付终端IP地址和端口
  - **Cable模式** - 选择USB协议类型（自动检测/AOA/VSP/RS232）
- 保存配置并重新连接

### 6. 高级功能

#### 附加金额支持
- **销售交易**支持附加金额：附加费、小费、税费、返现、服务费
- **预授权完成**支持小费和税费

#### 后续交易操作
- **退款**：对成功的销售交易进行退款
- **撤销**：撤销当日交易
- **预授权完成**：完成预授权交易
- **增量授权**：增加预授权金额
- **小费调整**：调整交易小费金额

#### 智能重试机制
- 自动识别临时性错误和明确失败错误
- 临时性错误使用相同 transactionRequestId 重试
- 明确失败错误生成新 transactionRequestId 重试
- 支持最多 3 次重试，带指数退避延迟



## 连接模式配置指南

### App-to-App 模式配置

**适用场景**：单台设备运行商户应用和支付终端应用

#### 配置步骤
1. 确保已安装 Tapro 支付终端应用
2. 在连接设置中选择"App-to-App"模式
3. 点击"确认"保存配置
4. 应用将自动尝试连接到 Tapro 应用

#### 故障排除
- 确保 Tapro 应用已正确安装
- 检查应用签名是否兼容
- 验证 SDK 凭据配置

### Cable 模式配置

**适用场景**：商户设备通过USB线缆连接到独立支付终端

#### 配置步骤
1. 使用USB线缆连接商户设备和支付终端
2. 在连接设置中选择"Cable"模式
3. 选择Cable协议类型：
   - **AUTO**（推荐）- SDK自动选择最佳协议
   - **USB_AOA** - USB Android Open Accessory 2.0
   - **USB_VSP** - USB Virtual Serial Port
   - **RS232** - 标准RS232串行通信
4. 点击"确认"保存配置并连接

#### 支持的连接协议
- **AUTO** - 自动检测最佳协议（默认推荐）
- **USB_AOA** - Android Open Accessory 2.0协议
- **USB_VSP** - Virtual Serial Port协议
- **RS232** - 标准串行通信协议

#### 故障排除
- 检查USB线缆连接状态
- 确认设备USB权限
- 尝试不同的协议类型
- 重新插拔USB连接

### LAN 模式配置

**适用场景**：商户设备和支付终端在同一局域网环境

#### 首次配置
1. 确保商户设备已连接到网络
2. 在连接设置中选择"LAN"模式
3. 输入支付终端配置：
   - **IP地址** - 支付终端的局域网IP（如：192.168.1.100）
   - **端口** - 通信端口（默认：8443）
4. 点击"确认"保存配置并连接

#### 自动连接
- 应用会记住成功连接的设备信息
- 下次启动时自动尝试连接到已保存的设备
- 支持网络恢复后自动重连

#### 网络要求
- **网络连接** - WiFi、以太网或移动网络
- **网络段** - 设备必须在同一网络段
- **端口开放** - 防火墙允许指定端口通信
- **网络稳定性** - 建议使用稳定的网络连接

#### 故障排除
- 检查网络连接状态
- 验证IP地址和端口配置
- 测试设备网络可达性
- 检查防火墙设置
- 尝试重新配置网络参数

### 连接模式切换

#### 切换步骤
1. 进入"连接设置"页面
2. 选择新的连接模式
3. 配置相应的连接参数
4. 点击"确认"应用新配置
5. 应用将断开当前连接并使用新模式重新连接

#### 注意事项
- 切换连接模式会断开当前连接
- 确保新模式的硬件和网络环境已准备就绪
- 建议在无交易进行时切换连接模式

### 开发指南

### 架构概述

应用采用基于 Activity 的架构：

- **Activity 层**：处理 UI 和用户交互
- **Service 层**：封装 Taplink SDK 功能
- **Repository 层**：管理内存中的交易数据
- **Model 层**：定义数据结构
- **Util 层**：提供基础工具类（连接配置、网络检测）

### 添加新的交易类型

1. **在 `TransactionType.kt` 中添加交易类型**：
```kotlin
enum class TransactionType {
    SALE,
    AUTH,
    YOUR_NEW_TYPE  // 在此添加
}
```

2. **在 PaymentService 中实现**：
```kotlin
fun executeYourNewType(
    referenceOrderId: String,
    transactionRequestId: String,
    amount: Double,
    currency: String,
    description: String,
    callback: PaymentCallback
)
```

3. **在 `activity_main.xml` 中添加 UI 按钮**并在 `MainActivity.kt` 中处理点击事件

### 自定义主题

修改 `app/src/main/res/values/` 中的主题文件：
- `colors.xml` - 颜色定义
- `themes.xml` - 主题样式
- `strings.xml` - 文本资源

### 错误处理

应用采用简单直接的错误处理策略：

#### 错误处理方法
- **直接显示SDK错误**：使用SDK提供的错误信息
- **用户友好对话框**：使用标准AlertDialog显示错误信息
- **简单重试机制**：通过"重试"按钮进行用户发起的重试
- **Toast提示**：用于简单的状态提示

#### 错误显示原则
1. **直接SDK消息**：显示SDK错误消息而不修改
2. **清晰标题**：使用明确的对话框标题（如"连接失败"、"支付错误"）
3. **最少操作**：仅提供必要操作（确定、重试）
4. **错误代码显示**：包含错误代码以便调试

#### 实现示例
```kotlin
// 简单错误显示
private fun showError(title: String, message: String) {
    AlertDialog.Builder(this)
        .setTitle(title)
        .setMessage(message)
        .setPositiveButton("OK", null)
        .show()
}

// 连接错误处理
private fun showConnectionFailure(message: String) {
    AlertDialog.Builder(this)
        .setTitle("Connection Failed")
        .setMessage(message)
        .setPositiveButton("Retry") { _, _ -> attemptConnection() }
        .setNegativeButton("Cancel", null)
        .show()
}
```



## 常见问题

### Q: SDK 初始化失败？
A: 
- 确保在 `build.gradle.kts` 中正确添加了 Taplink SDK 依赖
- 验证 `config.xml` 中的凭据是否正确
- 检查 logcat 获取详细错误信息

### Q: 连接失败，错误代码 "C22"？
A: 
- 错误 C22 表示 Tapro 应用未安装
- 下载并安装 [Tapro [standalone] - preview_uat_v1.0.0.97(develop).apk](Tapro%20%5Bstandalone%5D%20-%20preview_uat_v1.0.0.97%28develop%29.apk)
- 确保设备 SN 已绑定到 SUNBAY 平台
- 确保两个应用使用兼容的证书签名

### Q: 连接失败，错误代码 "S03"？
A: 
- 错误 S03 表示签名验证失败
- 验证 `config.xml` 中的 `appId`、`merchantId` 和 `secretKey`
- 联系商米支持验证您的凭据

### Q: 交易失败？
A: 
- 检查设备是否已连接（主页面显示连接状态）
- 确保金额大于 0
- 检查 logcat 获取详细错误代码和消息
- 验证 Tapro 应用配置正确

### Q: 交易卡在 PROCESSING 状态？
A: 
- 使用"查询交易"功能检查实际状态
- 交易可能在 Tapro 中已完成但回调失败
- 检查网络连接

### Q: 编译错误？
A: 
- 确保使用 JDK 11 或更高版本
- 清理并重新构建项目：
```bash
./gradlew clean build
```
- 在 Android Studio 中同步 Gradle 文件

### Q: 如何处理错误？
A:
- 应用直接显示SDK提供的错误信息
- 使用AlertDialog和Toast进行错误提示
- 连接失败时提供"重试"按钮
- 错误信息包含错误代码便于调试

### Q: 如何处理附加金额？
A:
- 销售交易支持附加费、小费、税费、返现、服务费
- 在交易对话框中输入相应金额
- 系统会自动计算总金额并在交易记录中保存详情

### Q: 后续交易操作失败？
A:
- 确保原始交易状态为成功
- 检查原始交易 ID 是否正确
- 验证操作权限和金额限制
- 查看错误日志获取具体原因

### Q: LAN模式连接失败？
A: 
- 确保设备已连接到网络（WiFi/以太网）
- 验证商户设备和支付终端在同一网络段
- 检查IP地址和端口配置是否正确
- 确认网络防火墙允许相应端口通信
- 使用网络工具测试设备可达性

### Q: Cable模式无法识别设备？
A: 
- 检查USB线缆连接是否牢固
- 确认设备支持所选的USB协议
- 尝试使用自动检测模式
- 检查设备USB权限设置
- 重新插拔USB线缆并重试连接

### Q: 网络连接不稳定？
A: 
- 检查WiFi信号强度和网络质量
- 尝试切换到以太网连接
- 确认路由器和网络设备工作正常
- 检查网络延迟和丢包情况
- 考虑使用有线连接提高稳定性
