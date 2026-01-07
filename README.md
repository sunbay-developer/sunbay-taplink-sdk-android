# TaplinkDemo

> **SUNBAY Taplink SDK 支付集成演示应用** - 展示如何集成和使用 SUNBAY Taplink SDK 进行支付交易处理的完整示例项目

[![Android](https://img.shields.io/badge/Platform-Android-green.svg)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Language-Kotlin-blue.svg)](https://kotlinlang.org)
[![API](https://img.shields.io/badge/API-25%2B-brightgreen.svg)](https://android-arsenal.com/api?level=25)
[![License](https://img.shields.io/badge/License-Commercial-orange.svg)](LICENSE)

## 📖 项目简介

TaplinkDemo 是由 **SUNMI（商米）** 提供的专业支付 SDK 集成示例应用，为 Android 开发者提供完整的 Taplink SDK 集成参考实现。本项目展示了如何在 Android 应用中实现各种支付交易功能，采用原生 Android 开发方式，使用 XML 布局和基于 Activity 的架构。

### 🎯 项目价值

- **完整的集成示例** - 提供从 SDK 初始化到交易处理的完整实现
- **多种连接模式** - 支持 App-to-App、Cable、LAN 等多种集成方案
- **生产级代码质量** - 遵循 Android 开发最佳实践和编码规范
- **详细的文档说明** - 包含完整的使用指南和故障排除方案
- **开箱即用** - 提供预编译 APK 文件，可直接体验功能

### 🚀 快速体验

**方式一：直接安装 APK**
- 下载 [TaplinkDemo APK](app/debug/TaplinkDemo-debug-1.0.0.apk) 和 [Tapro APK](Tapro%20%5Bstandalone%5D%20-%20preview_uat_v1.0.0.125t%28develop%29.apk)
- 安装到 Android 7.1+ 设备
- 配置 SDK 凭据即可开始体验

**方式二：观看演示视频**

![功能演示](./taplinkdemo.gif)

> 📹 **演示说明**: 上方 GIF 展示了完整的应用操作流程，如需查看高清版本，可下载 [完整视频文件](./taplinkdemo.mp4)

## ✨ 功能特性

### 💳 支付交易功能
- **销售交易 (SALE)** - 标准支付交易，支持附加金额
- **预授权交易 (AUTH)** - 预授权交易
- **强制授权 (FORCED_AUTH)** - 强制授权交易
- **退款交易 (REFUND)** - 退款操作
- **撤销交易 (VOID)** - 交易撤销
- **预授权完成 (POST_AUTH)** - 预授权完成
- **小费调整 (TIP_ADJUST)** - 小费金额调整
- **查询交易 (QUERY)** - 根据请求ID查询交易状态
- **批次结算 (BATCH_CLOSE)** - 日终批次结算

### 🔗 连接模式
- **App-to-App 模式** - 同设备集成（已实现）
- **Cable 模式** - USB线缆连接，支持多种协议（AUTO、USB_AOA、USB_VSP、RS232）
- **LAN 模式** - 局域网连接（已实现）

### 🛠️ 技术特性
- **原生 Android UI** - 使用 XML 布局和 Material Design
- **基于 Activity 的架构** - 清晰的UI层次结构
- **交易历史管理** - 完整的交易记录和状态跟踪
- **连接配置管理** - 支持多种连接模式的配置和切换
- **实时状态更新** - 支付进度和连接状态实时显示
- **智能错误处理** - 直接显示SDK错误信息，用户友好的对话框
- **附加金额支持** - 支持附加费、小费、税费、返现、服务费
- **Cable协议配置** - 支持AUTO、USB_AOA、USB_VSP、RS232协议选择
- **网络连接检测** - LAN模式的网络状态监控

## 🚀 快速开始

### 📋 环境要求
- **Android Studio**: Ladybug | 2024.2.1 或更高版本
- **JDK**: 11 或更高版本
- **Android SDK**: 35
- **Gradle**: 8.x
- **最小 Android 版本**: 7.1 (API 25)
- **目标 Android 版本**: 15 (API 35)

### ⚡ 快速体验（推荐新用户）

如果您想快速体验应用功能，可以：

1. **安装必要的 APK 文件**:
   - 下载并安装 [TaplinkDemo APK](app/debug/TaplinkDemo-debug-1.0.0.apk) - 演示应用
   - 下载并安装 [Tapro APK](Tapro%20%5Bstandalone%5D%20-%20preview_uat_v1.0.0.125t%28develop%29.apk) - Tapro支付终端应用

2. **设备绑定**: 将您的设备 SN 绑定到 SUNBAY 平台（联系技术支持获取绑定方法）

3. **配置 SDK 凭据**: 按照下面的步骤配置您自己的 SDK 凭据

### 🔧 从源码构建

#### 1. 克隆项目
```bash
git clone <repository-url>
cd TaplinkDemo
```

#### 2. 配置本地环境
确保 `local.properties` 文件包含正确的 SDK 路径：
```properties
sdk.dir=/path/to/your/Android/sdk
```

#### 3. 同步依赖
在 Android Studio 中打开项目，等待 Gradle 同步完成。

#### 4. 构建项目
```bash
./gradlew build
```

#### 5. 运行应用
```bash
./gradlew installDebug
```
或在 Android Studio 中直接运行。

### ⚙️ 配置 SDK 凭据

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

### 🎯 第一个支付交易

1. **启动应用** - 应用将自动以 App-to-App 模式连接到 Tapro
2. **选择金额** - 使用预设金额按钮或输入自定义金额
3. **选择交易类型** - 点击 Sale 或 Auth 按钮
4. **完成支付** - 应用将启动 Tapro 应用进行支付处理
5. **查看结果** - 返回演示应用查看交易结果

### ✅ 验证安装

- 应用启动后应显示连接状态
- 主界面应显示金额输入和交易按钮
- 点击"设置"可以查看和修改连接配置
- 点击"交易历史"可以查看交易记录

## 📚 SDK 集成指南

### 🔧 SDK 初始化

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

### 💳 执行支付交易

```kotlin
// 创建支付请求
val paymentRequest = PaymentRequest.builder()
    .setReferenceOrderId("ORDER_${System.currentTimeMillis()}")
    .setTransactionRequestId(UUID.randomUUID().toString())
    .setAmount(BigDecimal("10.00"))
    .setCurrency("USD")
    .setDescription("Sample payment")
    .build()

// 执行支付
paymentService.executeSale(paymentRequest, object : PaymentCallback {
    override fun onSuccess(result: PaymentResult) {
        // 处理支付成功
        Log.d(TAG, "Payment successful: ${result.transactionId}")
    }
    
    override fun onError(error: PaymentError) {
        // 处理支付失败
        Log.e(TAG, "Payment failed: ${error.message}")
    }
})
```

### 🔄 查询交易状态

```kotlin
paymentService.queryTransaction(
    transactionRequestId = "your_transaction_request_id",
    callback = object : PaymentCallback {
        override fun onSuccess(result: PaymentResult) {
            // 处理查询结果
            when (result.status) {
                TransactionStatus.SUCCESS -> {
                    // 交易成功
                }
                TransactionStatus.FAILED -> {
                    // 交易失败
                }
                TransactionStatus.PROCESSING -> {
                    // 交易处理中
                }
            }
        }
        
        override fun onError(error: PaymentError) {
            // 处理查询失败
        }
    }
)
```

### 🔙 退款操作

```kotlin
paymentService.executeRefund(
    referenceOrderId = "REFUND_${System.currentTimeMillis()}",
    transactionRequestId = UUID.randomUUID().toString(),
    originalTransactionId = "original_transaction_id",
    amount = BigDecimal("5.00"),
    currency = "USD",
    description = "Partial refund",
    callback = object : PaymentCallback {
        override fun onSuccess(result: PaymentResult) {
            // 退款成功
        }
        
        override fun onError(error: PaymentError) {
            // 退款失败
        }
    }
)
```



## 🔗 连接模式配置

### 📱 App-to-App 模式

**同设备集成解决方案** - 适用于单台设备运行商户应用和支付终端应用的场景

#### 工作原理
1. **Demo App**（本应用）- 发起支付请求的商户应用
2. **Tapro App** - 处理支付的终端应用
3. 两个应用运行在同一台 Android 设备上
4. 通过 Android Intent 机制进行通信

#### 配置步骤
1. 确保已安装 Tapro 支付终端应用
2. 在连接设置中选择"App-to-App"模式
3. 点击"确认"保存配置
4. 应用将自动尝试连接到 Tapro 应用

#### 故障排除
- 确保 Tapro 应用已正确安装
- 检查应用签名是否兼容
- 验证 SDK 凭据配置

### 🔌 Cable 模式

**USB线缆连接模式** - 适用于商户设备通过USB线缆连接到独立支付终端

#### 支持的协议
- **AUTO**（推荐）- SDK自动选择最佳协议
- **USB_AOA** - USB Android Open Accessory 2.0
- **USB_VSP** - USB Virtual Serial Port
- **RS232** - 标准RS232串行通信

#### 配置步骤
1. 使用USB线缆连接商户设备和支付终端
2. 在连接设置中选择"Cable"模式
3. 选择Cable协议类型（推荐使用AUTO）
4. 点击"确认"保存配置并连接

#### 故障排除
- 检查USB线缆连接状态
- 确认设备USB权限
- 尝试不同的协议类型
- 重新插拔USB连接

### 🌐 LAN 模式

**局域网连接模式** - 适用于商户设备和支付终端在同一局域网环境

#### 网络要求
- **网络连接** - WiFi、以太网或移动网络
- **网络段** - 设备必须在同一网络段
- **端口开放** - 防火墙允许指定端口通信
- **网络稳定性** - 建议使用稳定的网络连接

#### 配置步骤
1. 确保商户设备已连接到网络
2. 在连接设置中选择"LAN"模式
3. 输入支付终端配置：
   - **IP地址** - 支付终端的局域网IP（如：192.168.1.100）
   - **端口** - 通信端口（默认：8443）
4. 点击"确认"保存配置并连接

#### 故障排除
- 检查网络连接状态
- 验证IP地址和端口配置
- 测试设备网络可达性
- 检查防火墙设置

### 🔄 连接模式切换

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

## 🏗️ 项目架构

### 📁 项目结构

```
app/src/main/java/com/sunmi/tapro/taplink/demo/
├── TaplinkDemoApplication.kt          # Application class - SDK initialization
├── activity/                          # UI Activities
│   ├── MainActivity.kt               # Main payment interface
│   ├── ConnectionActivity.kt         # Connection settings
│   ├── TransactionListActivity.kt    # Transaction history
│   └── TransactionDetailActivity.kt  # Transaction details
├── adapter/                          # RecyclerView adapters
│   └── TransactionAdapter.kt         # Transaction list adapter
├── model/                            # Data models
│   ├── Transaction.kt                # Transaction data model
│   ├── TransactionType.kt            # Transaction type enum
│   └── TransactionStatus.kt          # Transaction status enum
├── repository/                       # Data access layer
│   └── TransactionRepository.kt      # In-memory transaction storage
├── service/                          # Business logic layer
│   ├── PaymentService.kt             # Payment service interface
│   └── TaplinkPaymentService.kt     # Taplink SDK implementation
└── util/                             # Utility classes
    ├── ConnectionPreferences.kt      # Connection settings storage
    ├── Constants.kt                  # Application constants
    ├── DialogUtils.kt                # Dialog utilities
    ├── FormatUtils.kt                # Formatting utilities
    ├── NetworkUtils.kt               # Network utilities
    └── PaymentCallbackUtils.kt       # Payment callback utilities
```

### 🎨 资源结构

```
app/src/main/res/
├── layout/                           # XML layout files
│   ├── activity_main.xml             # Main screen layout
│   ├── activity_connection.xml       # Connection settings layout
│   ├── activity_transaction_list.xml # Transaction list layout
│   ├── activity_transaction_detail.xml # Transaction detail layout
│   ├── item_transaction.xml          # Transaction list item layout
│   └── dialog_additional_amounts.xml # Additional amounts dialog
├── values/                           # Resource values
│   ├── strings.xml                   # String resources (English)
│   ├── colors.xml                    # Color definitions
│   ├── themes.xml                    # Material Design themes
│   ├── arrays.xml                    # Array resources
│   └── config.xml                    # SDK configuration values
├── drawable/                         # Vector drawables and images
└── mipmap-*/                         # App icons for different densities
```

### ⚙️ 技术栈

#### 开发环境
- **Kotlin**: 2.2.21
- **Android Gradle Plugin**: 8.13.1
- **Compile SDK**: 35 (Android 15)
- **Min SDK**: 25 (Android 7.1)
- **Target SDK**: 35 (Android 15)
- **JVM Target**: 11

#### 核心依赖
- **Taplink SDK**: 1.0.3
- **AndroidX Core KTX**: 1.16.0
- **AndroidX AppCompat**: 1.7.1
- **Material Components**: 1.12.0
- **ConstraintLayout**: 2.1.4
- **Kotlin Coroutines**: 1.8.1
- **Lifecycle Runtime KTX**: 2.9.2
- **Gson**: 2.13.1

#### 架构模式
- **Activity-based Architecture** - Traditional Android UI pattern
- **Repository Pattern** - Data management abstraction
- **Service Layer Pattern** - Business logic encapsulation
- **Observer Pattern** - Event-driven communication
- **Strategy Pattern** - Flexible algorithm selection



## 🛠️ 开发指南

### 🏗️ 架构概述

应用采用基于 Activity 的分层架构：

- **Presentation Layer** - Activities 处理 UI 和用户交互
- **Business Layer** - Service 层封装 Taplink SDK 功能
- **Data Layer** - Repository 层管理内存中的交易数据
- **Model Layer** - 定义数据结构和业务实体
- **Utility Layer** - 提供基础工具类（连接配置、网络检测、格式化等）

### 🔧 添加新的交易类型

1. **在 `TransactionType.kt` 中添加交易类型**：
```kotlin
enum class TransactionType {
    SALE,
    AUTH,
    YOUR_NEW_TYPE  // Add your new transaction type here
}
```

2. **在 `PaymentService.kt` 中定义接口**：
```kotlin
fun executeYourNewType(
    referenceOrderId: String,
    transactionRequestId: String,
    amount: BigDecimal,
    currency: String,
    description: String,
    callback: PaymentCallback
)
```

3. **在 `TaplinkPaymentService.kt` 中实现**：
```kotlin
override fun executeYourNewType(
    referenceOrderId: String,
    transactionRequestId: String,
    amount: BigDecimal,
    currency: String,
    description: String,
    callback: PaymentCallback
) {
    // Implementation details
}
```

4. **在 UI 中添加按钮**：
   - 在 `activity_main.xml` 中添加 UI 按钮
   - 在 `MainActivity.kt` 中处理点击事件

### 🎨 自定义主题

修改 `app/src/main/res/values/` 中的主题文件：

```xml
<!-- colors.xml - 颜色定义 -->
<color name="primary_color">#6200EE</color>
<color name="primary_variant">#3700B3</color>

<!-- themes.xml - 主题样式 -->
<style name="Theme.TaplinkDemo" parent="Theme.MaterialComponents.DayNight">
    <item name="colorPrimary">@color/primary_color</item>
</style>

<!-- strings.xml - 文本资源 -->
<string name="app_name">TaplinkDemo</string>
```

### ⚠️ 错误处理策略

应用采用简单直接的错误处理策略：

#### 错误处理原则
- **直接显示 SDK 错误** - 使用 SDK 提供的原始错误信息
- **用户友好对话框** - 使用标准 AlertDialog 显示错误信息
- **简单重试机制** - 通过"重试"按钮进行用户发起的重试
- **Toast 提示** - 用于简单的状态提示和确认消息

#### 实现示例
```kotlin
// Simple error display
private fun showError(title: String, message: String) {
    AlertDialog.Builder(this)
        .setTitle(title)
        .setMessage(message)
        .setPositiveButton("OK", null)
        .show()
}

// Connection error with retry option
private fun showConnectionFailure(message: String) {
    AlertDialog.Builder(this)
        .setTitle("Connection Failed")
        .setMessage(message)
        .setPositiveButton("Retry") { _, _ -> attemptConnection() }
        .setNegativeButton("Cancel", null)
        .show()
}

// Payment error handling
private fun handlePaymentError(error: PaymentError) {
    val title = when (error.type) {
        PaymentErrorType.CONNECTION -> "Connection Error"
        PaymentErrorType.VALIDATION -> "Validation Error"
        PaymentErrorType.PROCESSING -> "Processing Error"
        else -> "Payment Error"
    }
    showError(title, "${error.message} (Code: ${error.code})")
}
```

### 📱 权限管理

应用需要以下权限以支持不同的连接模式：

#### 基础权限
```xml
<!-- Network access for LAN mode -->
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
```

#### USB 连接权限（Cable 模式）
```xml
<!-- USB device access -->
<uses-permission android:name="android.permission.USB_PERMISSION" />
<uses-feature android:name="android.hardware.usb.host" />
<uses-feature android:name="android.hardware.usb.accessory" />
```

### 🔍 调试和日志

#### 启用 SDK 日志
```kotlin
val config = TaplinkConfig(appId, merchantId, secretKey)
    .setLogEnabled(true)
    .setLogLevel(LogLevel.DEBUG)
```

#### 应用日志标签
```kotlin
companion object {
    private const val TAG = "TaplinkDemo"
    private const val PAYMENT_TAG = "Payment"
    private const val CONNECTION_TAG = "Connection"
}

// Usage
Log.d(TAG, "Application started")
Log.i(PAYMENT_TAG, "Payment initiated: $amount")
Log.w(CONNECTION_TAG, "Connection retry attempt: $retryCount")
```

## ❓ 常见问题

### 🔧 SDK 相关问题

**Q: SDK 初始化失败？**
A: 
- 确保在 `build.gradle.kts` 中正确添加了 Taplink SDK 依赖
- 验证 `config.xml` 中的凭据是否正确
- 检查 logcat 获取详细错误信息

**Q: 连接失败，错误代码 "C22"？**
A: 
- 错误 C22 表示 Tapro 应用未安装
- 下载并安装 [Tapro APK](Tapro%20%5Bstandalone%5D%20-%20preview_uat_v1.0.0.125t%28develop%29.apk)
- 确保设备 SN 已绑定到 SUNBAY 平台
- 确保两个应用使用兼容的证书签名

**Q: 连接失败，错误代码 "S03"？**
A: 
- 错误 S03 表示签名验证失败
- 验证 `config.xml` 中的 `appId`、`merchantId` 和 `secretKey`
- 联系商米支持验证您的凭据

### 💳 交易相关问题

**Q: 交易失败？**
A: 
- 检查设备是否已连接（主页面显示连接状态）
- 确保金额大于 0
- 检查 logcat 获取详细错误代码和消息
- 验证 Tapro 应用配置正确

**Q: 交易卡在 PROCESSING 状态？**
A: 
- 使用"查询交易"功能检查实际状态
- 交易可能在 Tapro 中已完成但回调失败
- 检查网络连接

**Q: 后续交易操作失败？**
A:
- 确保原始交易状态为成功
- 检查原始交易 ID 是否正确
- 验证操作权限和金额限制
- 查看错误日志获取具体原因

### 🔗 连接相关问题

**Q: LAN模式连接失败？**
A: 
- 确保设备已连接到网络（WiFi/以太网）
- 验证商户设备和支付终端在同一网络段
- 检查IP地址和端口配置是否正确
- 确认网络防火墙允许相应端口通信
- 使用网络工具测试设备可达性

**Q: Cable模式无法识别设备？**
A: 
- 检查USB线缆连接是否牢固
- 确认设备支持所选的USB协议
- 尝试使用自动检测模式
- 检查设备USB权限设置
- 重新插拔USB线缆并重试连接

**Q: 网络连接不稳定？**
A: 
- 检查WiFi信号强度和网络质量
- 尝试切换到以太网连接
- 确认路由器和网络设备工作正常
- 检查网络延迟和丢包情况
- 考虑使用有线连接提高稳定性

### 🛠️ 开发相关问题

**Q: 编译错误？**
A: 
- 确保使用 JDK 11 或更高版本
- 清理并重新构建项目：
```bash
./gradlew clean build
```
- 在 Android Studio 中同步 Gradle 文件

**Q: 如何处理附加金额？**
A:
- 销售交易支持附加费、小费、税费、返现、服务费
- 在交易对话框中输入相应金额
- 系统会自动计算总金额并在交易记录中保存详情

**Q: 如何自定义错误处理？**
A:
- 应用直接显示SDK提供的错误信息
- 使用AlertDialog和Toast进行错误提示
- 连接失败时提供"重试"按钮
- 错误信息包含错误代码便于调试

## 📄 许可证

本项目为商业软件，版权归 SUNMI（商米）所有。未经授权不得用于商业用途。

## 🤝 技术支持

如需技术支持或有任何问题，请联系：

- **官方网站**: [https://www.sunmi.com](https://www.sunmi.com)
- **开发者文档**: 请联系商米技术支持获取完整的开发者文档
- **技术支持**: 请通过官方渠道联系商米技术支持团队

---

**© 2024 SUNMI Technology Co., Ltd. All rights reserved.**
