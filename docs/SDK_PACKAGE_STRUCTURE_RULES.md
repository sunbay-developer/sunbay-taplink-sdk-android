# SDK 包结构规则

## lib_taplink_sdk 包结构标准

本文档定义了 `lib_taplink_sdk` 模块的标准包结构和各包的职责划分，所有开发者必须遵循此结构进行开发。

## 标准包结构

```
com.sunmi.tapro.taplink.sdk/
├── adapter/           # 业务适配层
├── api/               # 公共 API 接口
├── callback/          # 回调接口
├── config/            # 配置类
├── enums/             # 枚举类
├── error/             # 错误类
├── impl/              # 实现类
├── manager/           # 管理器类
├── model/             # 数据模型
├── persistence/       # 持久化
├── protocol/          # 协议处理
├── util/              # 工具类
├── TaplinkClient.kt   # 客户端入口
└── TaplinkSDK.kt      # SDK 入口
```

## 包职责定义

### adapter/ - 业务适配层
**职责**: 业务层的请求适配和转换
- 将不同的业务请求适配为统一格式
- 处理业务字段映射和转换
- 与具体业务领域相关的适配逻辑

**当前文件**:
- `PaymentRequestAdapter.kt` - 支付请求适配器

**规则**:
- ✅ 适配器类应以 `Adapter` 结尾
- ✅ 负责不同数据格式之间的转换
- ❌ 不应包含业务逻辑处理

### api/ - 公共 API 接口
**职责**: 定义对外公开的 API 接口
- SDK 的公共接口定义
- 抽象接口和契约定义

**当前文件**:
- `TaplinkApi.kt` - Taplink API 接口

**规则**:
- ✅ 接口类应以 `Api` 结尾
- ✅ 只包含接口定义，不包含实现
- ❌ 不应包含具体的业务逻辑

### callback/ - 回调接口
**职责**: 定义各种回调接口
- 异步操作的回调接口
- 事件监听接口

**当前文件**:
- `ConnectionListener.kt` - 连接监听器
- `PaymentCallback.kt` - 支付回调

**规则**:
- ✅ 回调接口应以 `Callback` 或 `Listener` 结尾
- ✅ 只包含回调方法定义
- ❌ 不应包含实现逻辑

### config/ - 配置类
**职责**: 各种配置相关的类
- SDK 配置参数
- 连接配置
- 策略配置

**当前文件**:
- `ConnectionConfig.kt` - 连接配置
- `ReconnectPolicy.kt` - 重连策略
- `TaplinkConfig.kt` - Taplink 配置

**规则**:
- ✅ 配置类应以 `Config` 或 `Policy` 结尾
- ✅ 主要包含配置参数和验证逻辑
- ❌ 不应包含业务处理逻辑

### enums/ - 枚举类
**职责**: 定义各种枚举常量
- 状态枚举
- 类型枚举
- 常量定义

**当前文件**:
- `CableProtocol.kt` - 线缆协议枚举
- `ConnectionMode.kt` - 连接模式枚举
- `ConnectionStatus.kt` - 连接状态枚举
- `LogLevel.kt` - 日志级别枚举
- `TransactionAction.kt` - 交易动作枚举

**规则**:
- ✅ 枚举类名应清晰表达其用途
- ✅ 枚举值应使用大写字母和下划线
- ❌ 不应包含复杂的业务逻辑

### error/ - 错误类
**职责**: 定义各种错误和异常类
- 自定义异常类
- 错误信息封装

**当前文件**:
- `ConnectionError.kt` - 连接错误
- `PaymentError.kt` - 支付错误
- `TaplinkException.kt` - Taplink 异常

**规则**:
- ✅ 错误类应以 `Error` 或 `Exception` 结尾
- ✅ 应包含错误码和错误信息
- ❌ 不应包含错误处理逻辑

### impl/ - 实现类
**职责**: 接口的具体实现类
- API 接口的实现
- 其他需要实现的功能类

**当前文件**:
- `ResponseProcessor.kt` - 响应处理器
- `TaplinkApiImpl.kt` - Taplink API 实现

**规则**:
- ✅ 实现类应以 `Impl` 结尾或使用描述性名称
- ✅ 包含具体的实现逻辑
- ❌ 不应包含管理器类（应放在 manager/ 包）

### manager/ - 管理器类
**职责**: 各种管理器类，负责特定功能的管理
- 连接管理
- 支付管理
- 重连管理

**当前文件**:
- `ConnectionManager.kt` - 连接管理器
- `PaymentManager.kt` - 支付管理器
- `ReconnectManager.kt` - 重连管理器

**规则**:
- ✅ 管理器类必须以 `Manager` 结尾
- ✅ 负责特定功能域的管理和协调
- ❌ 不应包含简单的工具方法

### model/ - 数据模型
**职责**: 定义各种数据模型和实体类
- 请求模型
- 响应模型
- 通用数据结构

**子包结构**:
- `base/` - 基础模型类
- `common/` - 通用模型类
- `request/` - 请求模型类
- `response/` - 响应模型类

**规则**:
- ✅ 模型类应使用清晰的命名
- ✅ 请求类以 `Request` 结尾，响应类以 `Response` 结尾
- ❌ 不应包含业务逻辑处理

### persistence/ - 持久化
**职责**: 数据持久化相关的类
- 数据存储
- 缓存管理
- 配置持久化

**当前文件**:
- `ConnectionPersistence.kt` - 连接持久化

**规则**:
- ✅ 持久化类应以 `Persistence` 结尾
- ✅ 负责数据的存储和读取
- ❌ 不应包含业务逻辑

### protocol/ - 协议处理
**职责**: 所有协议相关的处理
- 协议检测
- 协议配置解析
- 协议请求构建

**当前文件**:
- `CableProtocolDetector.kt` - 线缆协议检测器
- `ProtocolConfigResolver.kt` - 协议配置解析器
- `ProtocolRequestBuilder.kt` - 协议请求构建器

**规则**:
- ✅ 协议相关的所有类都应放在此包
- ✅ 检测器以 `Detector` 结尾，解析器以 `Resolver` 结尾，构建器以 `Builder` 结尾
- ❌ 不应包含非协议相关的功能

### util/ - 工具类
**职责**: 通用工具类和辅助方法
- 签名工具
- 加密工具
- 通用辅助方法

**当前文件**:
- `SignUtil.kt` - 签名工具

**规则**:
- ✅ 工具类应以 `Util` 或 `Helper` 结尾
- ✅ 包含静态方法和通用功能
- ❌ 不应包含状态管理或复杂业务逻辑

## 包使用规则

### 1. 依赖关系规则
- **manager/** 可以依赖 **protocol/**, **config/**, **model/**, **error/**, **callback/**
- **protocol/** 可以依赖 **config/**, **model/**, **enums/**, **util/**
- **adapter/** 可以依赖 **model/**, **enums/**
- **impl/** 可以依赖 **manager/**, **api/**, **callback/**, **config/**
- **util/** 不应依赖其他业务包，保持独立性

### 2. 命名规则
- 类名应清晰表达其职责和功能
- 使用标准的后缀命名：`Manager`, `Adapter`, `Builder`, `Resolver`, `Detector`, `Config`, `Error`, `Exception`
- 避免使用模糊或通用的名称

### 3. 文件放置规则
- 新增类必须放在正确的包中
- 如果功能不明确，优先考虑创建新的专门包
- 不允许在错误的包中放置类文件

### 4. 重构规则
- 当类的职责发生变化时，应考虑移动到合适的包
- 定期审查包结构，确保职责划分清晰
- 重构时必须更新所有相关引用

## 扩展指南

### 添加新包
当需要添加新包时，应考虑：
1. 新包的职责是否与现有包重复
2. 新包是否有足够的类来支撑独立存在
3. 新包的命名是否清晰表达其职责

### 常见扩展场景
- **security/** - 安全相关功能
- **cache/** - 缓存管理
- **network/** - 网络通信
- **validation/** - 数据验证

## 违规检查

### 自动检查脚本
项目应包含自动检查脚本来验证包结构的正确性：
- 检查类是否在正确的包中
- 检查命名是否符合规范
- 检查依赖关系是否合理

### 代码审查要点
- 新增类的包位置是否正确
- 类名是否符合命名规范
- 职责划分是否清晰
- 依赖关系是否合理

## 总结

遵循标准的包结构规则可以：
- 提高代码的可维护性和可读性
- 降低新开发者的学习成本
- 确保项目架构的一致性
- 便于功能的扩展和重构

所有开发者都应严格遵循此包结构规则，在代码审查时也应重点检查包结构的正确性。