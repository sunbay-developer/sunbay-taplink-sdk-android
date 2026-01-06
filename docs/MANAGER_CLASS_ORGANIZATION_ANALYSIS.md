# Manager 类组织结构分析与建议

## 当前状况分析

### 现有 Manager 类分布

当前 lib_taplink_sdk 中有 3 个 Manager 类，分布在不同的包中：

1. **ConnectionManager** (`impl/`)
   - 位置: `com.sunmi.tapro.taplink.sdk.impl.ConnectionManager`
   - 职责: 连接管理、状态跟踪、INIT 命令处理
   - 依赖: ReconnectManager, PaymentManager, TaplinkServiceKernel
   - 大小: ~814 行代码

2. **PaymentManager** (`impl/`)
   - 位置: `com.sunmi.tapro.taplink.sdk.impl.PaymentManager`
   - 职责: 支付请求处理、交易查询、回调管理
   - 依赖: ConnectionManager, ResponseProcessor, LocalCallbackManager
   - 大小: ~500+ 行代码

3. **ReconnectManager** (`reconnect/`)
   - 位置: `com.sunmi.tapro.taplink.sdk.reconnect.ReconnectManager`
   - 职责: 自动重连逻辑、重试管理、连接持久化
   - 依赖: ConnectionPersistence, ConnectionConfig
   - 大小: ~400+ 行代码

### 当前包结构分析

```
sdk/
├── api/           # 公共 API 接口
├── callback/      # 回调接口
├── config/        # 配置类
├── converter/     # 转换器
├── enums/         # 枚举类
├── error/         # 错误类
├── impl/          # 实现类 (包含 ConnectionManager, PaymentManager)
├── model/         # 数据模型
├── persistence/   # 持久化
├── reconnect/     # 重连逻辑 (包含 ReconnectManager)
└── util/          # 工具类
```

## 问题分析

### 1. 包结构不一致
- ConnectionManager 和 PaymentManager 在 `impl/` 包
- ReconnectManager 在 `reconnect/` 包
- 缺乏统一的组织原则

### 2. 职责边界模糊
- ConnectionManager 职责过重（连接 + 状态 + INIT 命令）
- PaymentManager 与 ConnectionManager 耦合度高
- ReconnectManager 独立性好，但位置不合理

### 3. 依赖关系复杂
```
ConnectionManager ←→ PaymentManager (双向依赖)
ConnectionManager → ReconnectManager
PaymentManager → ConnectionManager
```

## 优化方案

### 方案一：统一 Manager 包 (推荐)

创建专门的 `manager/` 包，将所有 Manager 类集中管理：

```
sdk/
├── manager/       # 管理器类 (新增)
│   ├── ConnectionManager.kt
│   ├── PaymentManager.kt
│   └── ReconnectManager.kt
├── api/           # 公共 API 接口
├── callback/      # 回调接口
├── config/        # 配置类
├── impl/          # 其他实现类 (ResponseProcessor, TaplinkApiImpl)
└── ...
```

**优势:**
- 所有管理器类集中在一个包中，便于维护
- 包名清晰表达功能职责
- 符合常见的架构模式

### 方案二：按功能域分包

根据业务功能将 Manager 类分散到相应的功能包：

```
sdk/
├── connection/    # 连接相关
│   ├── ConnectionManager.kt
│   └── ReconnectManager.kt
├── payment/       # 支付相关
│   └── PaymentManager.kt
├── impl/          # 其他实现类
└── ...
```

**优势:**
- 按业务功能分组，逻辑清晰
- 相关类聚集在一起

**劣势:**
- ConnectionManager 和 PaymentManager 有交互，分离可能不利于维护

### 方案三：保持现状优化

保持当前结构，但进行小幅调整：

```
sdk/
├── impl/          # 核心实现类
│   ├── ConnectionManager.kt
│   ├── PaymentManager.kt
│   └── ResponseProcessor.kt
├── reconnect/     # 重连相关 (保持不变)
│   └── ReconnectManager.kt
└── ...
```

## 推荐方案详细说明

### 选择方案一：统一 Manager 包

#### 1. 迁移步骤

1. 创建 `manager/` 包
2. 移动 Manager 类到新包
3. 更新所有引用
4. 更新文档

#### 2. 新的包结构

```
com.sunmi.tapro.taplink.sdk.manager/
├── ConnectionManager.kt    # 从 impl/ 移动
├── PaymentManager.kt       # 从 impl/ 移动
└── ReconnectManager.kt     # 从 reconnect/ 移动
```

#### 3. 优化建议

**ConnectionManager 职责优化:**
- 保持连接管理核心职责
- 考虑将 INIT 命令处理提取到单独的类
- 简化与 PaymentManager 的双向依赖

**PaymentManager 职责优化:**
- 专注于支付和查询逻辑
- 减少对 ConnectionManager 的直接依赖
- 通过事件或回调机制与连接状态交互

**ReconnectManager 职责优化:**
- 保持当前良好的设计
- 考虑是否需要与其他 Manager 更紧密集成

#### 4. 依赖关系优化

建议的依赖关系：
```
ConnectionManager → ReconnectManager (保持)
PaymentManager → ConnectionManager (简化为事件驱动)
ReconnectManager ← ConnectionManager (保持)
```

## 实施建议

### 阶段一：创建 manager 包
1. 创建新的 `manager/` 包
2. 移动 Manager 类
3. 更新 import 语句

### 阶段二：优化职责分离
1. 分析 ConnectionManager 的职责，考虑拆分
2. 简化 Manager 类之间的依赖关系
3. 引入事件驱动模式减少耦合

### 阶段三：文档更新
1. 更新架构文档
2. 更新 API 文档
3. 更新示例代码

## 总结

推荐采用**方案一：统一 Manager 包**，理由如下：

1. **一致性**: 所有管理器类在同一个包中，便于查找和维护
2. **清晰性**: 包名明确表达了类的职责
3. **可扩展性**: 未来添加新的 Manager 类有明确的位置
4. **最小影响**: 只需要移动文件和更新 import，不需要重构逻辑

这种组织方式符合常见的软件架构模式，有利于项目的长期维护和团队协作。