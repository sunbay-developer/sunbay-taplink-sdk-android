# Taplink SDK for Android

[![Version](https://img.shields.io/badge/version-1.0.1-blue.svg)](https://github.com/sunmi/taplink-sdk)
[![Min SDK](https://img.shields.io/badge/minSdk-25-green.svg)](https://developer.android.com/about/versions/android-7.1)
[![Kotlin](https://img.shields.io/badge/kotlin-2.0.21-purple.svg)](https://kotlinlang.org/)
[![Maven Central](https://img.shields.io/badge/Maven%20Central-com.sunmi%3Ataplink--sdk-blue.svg)](https://search.maven.org/artifact/com.sunmi/taplink-sdk)

Taplink SDK 是 SUNBAY 提供的 Android 支付集成 SDK，帮助 POS 应用开发者快速集成支付功能。支持多种连接模式，提供完整的支付交易 API。

## 📦 快速集成

在 `build.gradle.kts` 中添加依赖：

```kotlin
dependencies {
    implementation("com.sunmi:taplink-sdk:1.0.1")
}
```

## 📋 目录

- [功能特性](#功能特性)
- [技术栈](#技术栈)
- [项目结构](#项目结构)
- [快速开始](#快速开始)
- [构建说明](#构建说明)
- [版本信息](#版本信息)
- [开发指南](#开发指南)
- [相关文档](#相关文档)

## ✨ 功能特性

- 🔌 **多种连接模式**
  - 应用间模式：同设备应用间通信
  - 线缆模式：USB/串口线缆连接
  - 局域网模式：网络连接

- 💳 **完整支付功能**
  - 支持多种交易类型（消费、撤销、退款等）
  - 同步和异步两种调用方式
  - 完整的交易查询功能

- 🛠️ **开发友好**
  - 简洁的 API 设计
  - 完善的错误处理
  - 详细的集成文档

## 🛠️ 技术栈

- **语言**: Kotlin 2.0.21
- **构建工具**: Gradle with Kotlin DSL
- **Android Gradle Plugin**: 8.13.1
- **最低支持**: Android 7.1 (API 25)
- **编译目标**: Android API 35
- **Java 版本**: Java 11

## 📁 项目结构

```
Taplink_SDK/
├── lib_taplink_sdk/          # SDK 主模块
│   ├── build.gradle.kts      # SDK 构建配置
│   ├── sdk_publish.gradle    # 发布配置
│   └── src/                  # 源代码
├── lib_taplink_service/       # 服务层模块
├── lib_log/                  # 日志模块
├── app/                      # 示例应用
├── docs/                     # 文档目录
│   └── Taplink SDK for Android - 集成指南.md
├── build.gradle.kts          # 根构建配置
├── settings.gradle.kts       # 项目设置
└── README.md                 # 本文件
```

## 🚀 快速开始

### 环境要求

- Android Studio Hedgehog 或更高版本
- JDK 11 或更高版本
- Android SDK (API 25+)

### 使用 SDK（推荐方式）

**使用 Maven 远程依赖（推荐）：**

在项目的 `build.gradle.kts` 中添加：

```kotlin
dependencies {
    implementation("com.sunmi:taplink-sdk:1.0.1")
}
```

### 从源码构建

如果需要从源码构建 SDK：

**克隆项目：**

```bash
git clone http://code.sunmi.com/sunbay/App/taplinksdk.git
cd taplinksdk
```

**构建项目：**

```bash
# 构建 Debug 版本
./gradlew :lib_taplink_sdk:assembleDebug

# 构建 Release 版本
./gradlew :lib_taplink_sdk:assembleRelease
```

构建完成后，AAR 文件会自动复制到 `build/` 目录：
- Debug: `build/taplink-sdk-1.0.1-debug.aar`
- Release: `build/taplink-sdk-1.0.1-release.aar`

## 🔨 构建说明

### 构建任务

SDK 模块提供了以下 Gradle 任务：

| 任务 | 说明 |
|------|------|
| `assembleDebug` | 构建 Debug 版本 AAR |
| `assembleRelease` | 构建 Release 版本 AAR |
| `copyDebugAar` | 拷贝并重命名 Debug AAR 到 build 目录 |
| `copyReleaseAar` | 拷贝并重命名 Release AAR 到 build 目录 |

### 自动构建流程

执行 `assembleDebug` 或 `assembleRelease` 时，会自动执行对应的拷贝任务，将生成的 AAR 文件重命名并复制到 `../build/` 目录。

### 构建配置

SDK 版本信息在 `lib_taplink_sdk/build.gradle.kts` 中配置：

```kotlin
object SdkVersion {
    const val CODE = 2
    const val NAME = "1.0.1"
}
```

修改版本号后，重新构建即可生成对应版本的 AAR 文件。

### 发布配置

SDK 支持发布到 Maven Central 仓库，配置在 `lib_taplink_sdk/sdk_publish.gradle` 中。

**Maven 坐标：**
- Group ID: `com.sunmi`
- Artifact ID: `taplink-sdk`
- 版本: `1.0.1` (Release) 或 `1.0.1-SNAPSHOT` (Snapshot)

**仓库地址：**
- Release: Maven Central (https://repo1.maven.org/maven2/)
- Snapshot: Sonatype Snapshots (https://central.sonatype.com/repository/maven-snapshots/)

发布命令：
```bash
# 发布 Release 版本
./gradlew :lib_taplink_sdk:publishReleasePublicationToReleaseUrlRepository

# 发布 Snapshot 版本
./gradlew :lib_taplink_sdk:publishDebugPublicationToSnapshotUrlRepository
```

## 📦 版本信息

### 当前版本

- **版本号**: 1.0.1
- **版本代码**: 2
- **发布日期**: 2025-01

### 版本历史

| 版本 | 日期 | 说明 |
|------|------|------|
| 1.0.1 | 2025-01 | 当前版本 |

## 💻 开发指南

### 集成 SDK

#### 方式一：使用 Maven 远程依赖（推荐）

1. 在项目根目录的 `build.gradle` 或 `settings.gradle.kts` 中添加 Maven 仓库：

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        // 如果需要使用 Snapshot 版本，添加以下仓库
        maven {
            url = uri("https://central.sonatype.com/repository/maven-snapshots/")
        }
    }
}
```

2. 在模块的 `build.gradle` 或 `build.gradle.kts` 中添加依赖：

**使用 Release 版本（稳定版）：**

```kotlin
dependencies {
    implementation("com.sunmi:taplink-sdk:1.0.1")
}
```

**使用 Snapshot 版本（开发版）：**

```kotlin
dependencies {
    implementation("com.sunmi:taplink-sdk:1.0.1-SNAPSHOT")
}
```

#### 方式二：使用本地 AAR 文件

1. 将 AAR 文件添加到项目的 `libs` 目录
2. 在 `build.gradle` 中添加依赖：

```gradle
dependencies {
    implementation files('libs/taplink-sdk-1.0.1-release.aar')
}
```

### 初始化 SDK

```kotlin
import com.sunmi.tapro.taplink.sdk.TaplinkSDK
import com.sunmi.tapro.taplink.sdk.config.TaplinkConfig

// 创建配置
val config = TaplinkConfig.Builder()
    .setAppId("your_app_id")
    .setAppName("Your App Name")
    .build()

// 初始化 SDK
TaplinkSDK.init(context, config)
```

### 建立连接

```kotlin
import com.sunmi.tapro.taplink.sdk.callback.ConnectionListener
import com.sunmi.tapro.taplink.sdk.config.ConnectionConfig

// 连接配置（可选，使用 null 表示自动检测）
val connectionConfig = ConnectionConfig.Builder()
    .setConnectionMode(ConnectionMode.APP_TO_APP)
    .build()

// 建立连接
TaplinkSDK.connect(connectionConfig, object : ConnectionListener {
    override fun onConnected() {
        // 连接成功
    }
    
    override fun onDisconnected() {
        // 连接断开
    }
    
    override fun onError(error: TaplinkException) {
        // 连接错误
    }
})
```

### 执行交易

```kotlin
import com.sunmi.tapro.taplink.sdk.model.request.PaymentRequest
import com.sunmi.tapro.taplink.sdk.callback.PaymentCallback

val request = PaymentRequest.Builder()
    .setAction("SALE")  // 交易类型
    .setAmount(100)     // 金额（分）
    .setOrderNo("ORDER123")
    .build()

TaplinkSDK.executePayment(request, object : PaymentCallback {
    override fun onSuccess(result: PaymentResult) {
        // 交易成功
    }
    
    override fun onError(error: TaplinkException) {
        // 交易失败
    }
})
```

更多使用示例和 API 文档，请参考 [集成指南](./docs/Taplink%20SDK%20for%20Android%20-%20集成指南.md)。

## 📚 相关文档

- [集成指南](./docs/Taplink%20SDK%20for%20Android%20-%20集成指南.md) - 详细的集成和使用文档
- [API 文档](./docs/) - API 参考文档

## 🤝 贡献

欢迎提交 Issue 和 Pull Request。

## 📄 许可证

Copyright © SUNBAY. All rights reserved.

## 📞 支持

如有问题或建议，请联系：
- 项目地址: http://code.sunmi.com/sunbay/App/taplinksdk
- 技术支持: 请联系 SUNBAY 技术支持团队

---

**注意**: 本 SDK 仅限本地模式集成。如需服务端云端模式集成，请使用 Nexus SDK。
