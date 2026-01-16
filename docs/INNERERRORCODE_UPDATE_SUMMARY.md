# InnerErrorCode 错误码更新总结

## 更新日期
2025-01-14

## 更新目的
根据《Sunbay Taplink SDK for Android - 集成指南 (NEW).md》中定义的完整错误码体系，更新项目中的 `InnerErrorCode` 类，使其与最新的集成指南保持一致。

## 错误码体系变更

### 新错误码范围说明

| 错误码范围 | 错误类型 | 说明 |
|-----------|---------|------|
| **100** | 成功响应 | 操作成功（非错误码） |
| **20x** | 初始化错误 | SDK初始化相关问题 |
| **21x** | 连接状态/失败 | 连接状态管理和连接失败 |
| **23x** | 本地模式连接失败 | 同设备APP_TO_APP模式连接问题 |
| **24x** | 局域网模式连接失败 | LAN模式网络连接问题 |
| **25x** | USB模式连接失败 | CABLE模式线缆连接问题 |
| **30x + 399** | 交易处理错误 | 交易参数、发送、状态等问题 |

### 新增错误码对象

#### 成功响应
- `Success` (100) - 操作成功

#### 初始化错误 (20x)
- `E201` (201) - SDK未初始化
- `E202` (202) - SDK服务异常
- `E203` (203) - Tapro初始化失败

#### 连接状态/失败 (21x)
- `E211` (211) - 连接已存在，无需重复连接
- `E212` (212) - 设备未连接
- `E213` (213) - 连接已断开
- `E214` (214) - 无法建立连接
- `E221` (221) - 认证失败

#### 本地模式连接失败 (23x)
- `E231` (231) - Tapro应用未安装
- `E232` (232) - 无法连接到Tapro

#### 局域网模式连接失败 (24x)
- `E241` (241) - 无法连接到服务端
- `E242` (242) - 无法发现服务端

#### USB模式连接失败 (25x)
- `E251` (251) - 线缆未连接
- `E252` (252) - USB权限被拒绝
- `E253` (253) - USB连接超时
- `E254` (254) - USB协议不支持
- `E255` (255) - 串口设备未就绪

#### 交易处理错误 (30x + 399)
- `E301` (301) - 缺少必填参数
- `E302` (302) - 请求数据格式错误
- `E303` (303) - 不支持的交易类型
- `E304` (304) - 请求发送失败
- `E305` (305) - 交易正在进行
- `E306` (306) - 响应超时（需先查询交易状态）
- `E307` (307) - 交易被拒绝（必须使用新ID重试）
- `E308` (308) - 交易处理中（需先查询交易状态）
- `E309` (309) - 交易已终止（不要重试）
- `E310` (310) - 余额不足（必须使用新ID重试）
- `E311` (311) - 密码错误（必须使用新ID重试）
- `E399` (399) - 交易失败（必须使用新ID重试）

## 向后兼容性

### 旧错误码保留
所有旧的 T 系列和 C 系列错误码对象都被保留，并标记为 `@Deprecated`，以确保现有代码不会中断。

### 错误码映射关系

#### T 系列到新错误码的映射
- T01 → 201 (SDK未初始化)
- T02 → 231 (Tapro应用未安装)
- T03 → 232 (IPC服务绑定失败)
- T04 → 241 (WebSocket连接失败)
- T05 → 242 (mDNS发现失败)
- T06, T08 → 213 (连接断开)
- T07, T37 → 212 (未建立连接)
- T09 → 211 (连接已存在)
- T10 → 254 (USB协议无效)
- T11 → 252 (USB设备访问被拒绝)
- T12 → 253 (USB连接超时)
- T13, T14 → 255 (串口设备未就绪)
- T15, T18 → 301 (缺少必填参数)
- T16, T17, T19 → 302 (请求数据格式错误)
- T20-T25 → 304 (请求发送失败)
- T27, T30, T31 → 305 (交易正在进行)
- T28 → 303 (不支持的交易类型)
- T29, T40 → 309 (交易已终止)
- T34 → 306 (响应超时)
- T35 → 251 (线缆未连接)
- T38 → 307 (交易被拒绝)
- T39 → 308 (交易处理中)
- T41 → 221 (连接认证失败)

#### C 系列到新错误码的映射
所有 C 系列错误码都映射到相应的新错误码，具体映射关系与 T 系列类似。

## 新增辅助方法

### 错误分类方法
- `isInitializationError(code)` - 判断是否为初始化错误 (20x)
- `isConnectionStateError(code)` - 判断是否为连接状态/失败错误 (21x)
- `isLocalModeConnectionError(code)` - 判断是否为本地模式连接失败 (23x)
- `isLanModeConnectionError(code)` - 判断是否为局域网模式连接失败 (24x)
- `isUsbModeConnectionError(code)` - 判断是否为USB模式连接失败 (25x)
- `isTransactionProcessingError(code)` - 判断是否为交易处理错误 (30x + 399)

### 重试策略方法
- `needsQueryBeforeRetry(code)` - 判断是否需要先查询交易状态再重试（306, 308）
- `mustUseNewTransactionId(code)` - 判断是否必须使用新的 transactionRequestId 重试（307, 310, 311, 399）
- `canRetryWithSameId(code)` - 判断是否可以使用相同的 transactionRequestId 重试（301-305）
- `shouldNotRetry(code)` - 判断是否不应该重试（309）

### 废弃的方法
以下方法已标记为 `@Deprecated`，建议使用新的分类方法：
- `isTSeriesError(code)`
- `isConnectionError(code)`
- `isParameterValidationError(code)`
- `isDataTransmissionError(code)`
- `isSystemTimeoutError(code)`

## transactionRequestId 重试规则

根据集成指南，不同错误码有不同的重试规则：

| 重试规则 | 错误码 | 说明 |
|---------|--------|------|
| **可用相同ID** | 301-305 | 参数/发送错误，可以使用相同ID重试 |
| **先查询再决定** | 306, 308 | 响应超时/交易处理中，需要先查询状态 |
| **必须用新ID** | 307, 310, 311, 399 | 交易失败，必须使用新ID重试 |
| **不要重试** | 309 | 交易已终止，不应该重试 |

## 使用示例

### 使用新错误码
```kotlin
// 检查错误类型
when {
    InnerErrorCode.isInitializationError(errorCode) -> {
        // 处理初始化错误
    }
    InnerErrorCode.isConnectionStateError(errorCode) -> {
        // 处理连接错误
    }
    InnerErrorCode.isTransactionProcessingError(errorCode) -> {
        // 处理交易错误
    }
}

// 判断重试策略
when {
    InnerErrorCode.needsQueryBeforeRetry(errorCode) -> {
        // 先查询交易状态
        queryTransactionStatus()
    }
    InnerErrorCode.mustUseNewTransactionId(errorCode) -> {
        // 必须使用新ID重试
        retryWithNewId()
    }
    InnerErrorCode.canRetryWithSameId(errorCode) -> {
        // 可以使用相同ID重试
        retryWithSameId()
    }
    InnerErrorCode.shouldNotRetry(errorCode) -> {
        // 不要重试
        showError()
    }
}
```

### 向后兼容
```kotlin
// 旧代码仍然可以工作，但会收到废弃警告
val errorCode = InnerErrorCode.fromCode("T01")  // 返回 T01 对象
val newErrorCode = InnerErrorCode.fromCode("201")  // 返回 E201 对象

// 两者的 code 属性不同
println(errorCode.code)  // 输出: T01
println(newErrorCode.code)  // 输出: 201
```

## 迁移建议

### 对于新代码
- 使用新的错误码对象（E201-E399）
- 使用新的分类方法和重试策略方法
- 参考集成指南中的错误处理示例

### 对于现有代码
- 现有代码无需立即修改，旧错误码仍然可用
- 建议逐步迁移到新错误码
- 使用 IDE 的废弃警告来识别需要更新的代码

## 文件变更
- `lib_taplink_communication/src/main/java/com/sunmi/tapro/taplink/communication/enums/InnerErrorCode.kt`

## 参考文档
- `docs/Sunbay Taplink SDK for Android - 集成指南 (NEW).md` - 第7章：错误码参考
