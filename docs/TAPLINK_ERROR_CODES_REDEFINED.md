# Taplink SDK 错误码重新定义

## 概述

本文档重新定义了 Taplink SDK 的错误码体系，采用三位数字格式，便于分类管理和快速识别错误类型。

## 错误码分类体系

### 错误码范围定义

| 范围 | 错误类型 | 说明 |
|------|----------|------|
| 101-110 | 初始化和配置错误 | SDK初始化、配置相关错误 |
| 111-120 | 连接错误 | 各种连接模式的连接建立错误 |
| 121-140 | 交易错误 | 交易处理、支付相关错误 |
| 141-150 | 参数验证错误 | 请求参数格式、内容验证错误 |
| 151-160 | 数据传输错误 | 数据发送、接收相关错误 |
| 161-170 | 设备硬件错误 | USB、串口等硬件设备错误 |
| 171-180 | 网络通信错误 | 网络连接、通信协议错误 |
| 181-190 | 权限认证错误 | 权限、认证、安全相关错误 |
| 191-199 | 系统状态错误 | 系统状态、资源相关错误 |

## 详细错误码定义

### 101-110: 初始化和配置错误

| 新错误码 | 原错误码 | 错误名称 | 说明 | 解决方案 | 重试策略 |
|----------|----------|----------|------|----------|----------|
| 101 | T01 | SDK_NOT_INITIALIZED | SDK未初始化 | 调用TaplinkSDK.init()初始化SDK | 修复后可重试 |
| 102 | T02 | TAPRO_APP_NOT_INSTALLED | Tapro应用未安装 | 安装Tapro应用到设备 | 修复后可重试 |
| 103 | T14 | VSP_CLIENT_NOT_READY | VSP客户端未就绪 | 重新初始化VSP客户端 | 修复后可重试 |
| 104 | T20 | NO_SERVICE_KERNEL | 无可用服务内核 | 重新初始化服务内核 | 修复后可重试 |
| 105 | T25 | TRANSACTION_INTERFACE_NULL | 交易接口为空 | 重新绑定交易服务接口 | 修复后可重试 |
| 106 | T41 | CONNECTION_AUTH_FAILED | 连接认证失败 | 检查认证参数（appId、secretKey等） | 修复后可重试 |
| 107 | - | INVALID_SDK_CONFIG | SDK配置无效 | 检查TaplinkConfig配置参数 | 修复后可重试 |
| 108 | - | MISSING_REQUIRED_CONFIG | 缺少必需配置 | 提供所有必需的配置参数 | 修复后可重试 |
| 109 | - | CONFIG_VALIDATION_FAILED | 配置验证失败 | 检查配置参数格式和值 | 修复后可重试 |
| 110 | - | INITIALIZATION_TIMEOUT | 初始化超时 | 增加初始化超时时间或检查系统状态 | 修复后可重试 |

### 111-120: 连接错误

| 新错误码 | 原错误码 | 错误名称 | 说明 | 解决方案 | 重试策略 |
|----------|----------|----------|------|----------|----------|
| 111 | T03 | IPC_SERVICE_BIND_FAILED | IPC服务绑定失败 | 重启Tapro应用或重启设备 | 可使用相同ID重试 |
| 112 | T04 | WEBSOCKET_CONNECTION_FAILED | WebSocket连接失败 | 检查防火墙，确保端口8443-8453开放 | 可使用相同ID重试 |
| 113 | T05 | MDNS_DISCOVERY_FAILED | mDNS发现失败 | 手动输入IP地址或检查网络mDNS支持 | 可使用相同ID重试 |
| 114 | T06 | CONNECTION_DISCONNECTED | 连接已断开 | 调用connect()重新建立连接 | 可使用相同ID重试 |
| 115 | T07 | NO_CONNECTION_ESTABLISHED | 未建立连接 | 先调用connect()建立连接再发起交易 | 可使用相同ID重试 |
| 116 | T08 | MANUAL_DISCONNECT | 手动断开连接 | 正常断开，无需处理 | 无需重试 |
| 117 | T09 | CONNECTION_ALREADY_EXISTS | 连接已建立 | 连接已存在，无需重复建立 | 无需重试 |
| 118 | T37 | CONNECTION_NOT_ESTABLISHED | 未建立连接 | 调用connect()建立连接 | 可使用相同ID重试 |
| 119 | - | CONNECTION_TIMEOUT | 连接超时 | 增加连接超时时间或检查网络状态 | 可使用相同ID重试 |
| 120 | - | CONNECTION_REFUSED | 连接被拒绝 | 检查设备状态和网络配置 | 可使用相同ID重试 |

### 121-140: 交易错误

| 新错误码 | 原错误码 | 错误名称 | 说明 | 解决方案 | 重试策略 |
|----------|----------|----------|------|----------|----------|
| 121 | T27 | TRANSACTION_LOCKED | 交易锁定(防并发操作) | 等待当前操作完成后重试 | 可使用相同ID重试 |
| 122 | T30 | SYSTEM_PROCESSING_OTHER_TRANSACTION | 系统正在处理其他交易 | 等待当前交易完成后重试 | 可使用相同ID重试 |
| 123 | T31 | INIT_FAILED_SYSTEM_BUSY | 初始化失败：系统正在处理其他交易 | 等待交易完成后重新初始化 | 可使用相同ID重试 |
| 124 | T34 | TAPRO_RESPONSE_TIMEOUT | Tapro响应超时 | 查询交易状态后重试 | **特殊处理**：先查询状态 |
| 125 | T38 | HOST_TRANSACTION_REJECTED | host交易拒绝 | 交易被Host拒绝，检查交易参数 | **必须使用新ID** |
| 126 | T39 | HOST_TRANSACTION_PROCESSING | Host交易处理中 | Host正在处理交易，请查询交易状态 | **必须使用新ID** |
| 127 | T29 | TRANSACTION_TERMINATED | 交易已终止 | 交易已终止，无需重试 | 无需重试 |
| 128 | T40 | TERMINATION_FAILED | 终止失败 | 交易终止操作失败，检查交易状态 | 可使用相同ID重试 |
| 129 | - | TRANSACTION_CANCELLED | 交易已取消 | 用户取消交易 | 无需重试 |
| 130 | - | TRANSACTION_DUPLICATE | 重复交易 | 检测到重复的交易请求ID | 使用新的交易请求ID |
| 131 | - | TRANSACTION_AMOUNT_INVALID | 交易金额无效 | 检查交易金额格式和范围 | 修复后可使用相同ID重试 |
| 132 | - | TRANSACTION_TYPE_UNSUPPORTED | 不支持的交易类型 | 检查action参数是否正确 | 修复后可使用相同ID重试 |
| 133 | - | ORIGINAL_TRANSACTION_NOT_FOUND | 原始交易未找到 | 检查原始交易ID是否正确 | 修复后可使用相同ID重试 |
| 134 | - | REFUND_AMOUNT_EXCEEDS | 退款金额超限 | 退款金额不能超过原交易金额 | 修复后可使用相同ID重试 |
| 135 | - | TRANSACTION_EXPIRED | 交易已过期 | 交易超过有效期 | 创建新交易 |
| 136 | - | INSUFFICIENT_FUNDS | 余额不足 | 卡片或账户余额不足 | 使用其他支付方式 |
| 137 | - | CARD_DECLINED | 卡片被拒绝 | 银行或发卡方拒绝交易 | 使用其他卡片 |
| 138 | - | PAYMENT_METHOD_NOT_SUPPORTED | 支付方式不支持 | 当前支付方式不被支持 | 使用支持的支付方式 |
| 139 | - | TRANSACTION_LIMIT_EXCEEDED | 交易限额超限 | 交易金额超过限额 | 调整交易金额 |
| 140 | - | BATCH_CLOSE_FAILED | 批次关闭失败 | 批次关闭操作失败 | 重试批次关闭 |

### 141-150: 参数验证错误

| 新错误码 | 原错误码 | 错误名称 | 说明 | 解决方案 | 重试策略 |
|----------|----------|----------|------|----------|----------|
| 141 | T15 | MISSING_REQUEST_PARAMETERS | 缺少请求参数 | 检查并提供所有必需参数 | 修复后可使用相同ID重试 |
| 142 | T16 | INVALID_JSON_FORMAT | 请求JSON格式错误 | 检查JSON格式、括号匹配和数据类型 | 修复后可使用相同ID重试 |
| 143 | T17 | PROTOCOL_DATA_INVALID | 协议数据无效或解析失败 | 检查协议数据格式是否正确 | 修复后可使用相同ID重试 |
| 144 | T18 | INVALID_AMOUNT_PARAMETER | 无效的金额参数 | 检查金额格式和范围是否有效 | 修复后可使用相同ID重试 |
| 145 | T19 | PROTOCOL_TYPE_MISMATCH | 协议类型不匹配 | 确认协议类型匹配预期值 | 修复后可使用相同ID重试 |
| 146 | T28 | UNSUPPORTED_ACTION_TYPE | 不支持的交易类型 | 检查action参数是否正确且被支持 | 修复后可使用相同ID重试 |
| 147 | - | MISSING_TRANSACTION_ID | 缺少交易ID | 提供transactionRequestId或referenceOrderId | 修复后可使用相同ID重试 |
| 148 | - | INVALID_CURRENCY_CODE | 无效的货币代码 | 使用有效的ISO 4217货币代码 | 修复后可使用相同ID重试 |
| 149 | - | PARAMETER_LENGTH_INVALID | 参数长度无效 | 检查参数长度是否符合要求 | 修复后可使用相同ID重试 |
| 150 | - | PARAMETER_FORMAT_INVALID | 参数格式无效 | 检查参数格式是否符合规范 | 修复后可使用相同ID重试 |

### 151-160: 数据传输错误

| 新错误码 | 原错误码 | 错误名称 | 说明 | 解决方案 | 重试策略 |
|----------|----------|----------|------|----------|----------|
| 151 | T21 | DATA_SEND_FAILED | 发送数据失败 | 检查网络连接，重试数据发送 | 可使用相同ID重试 |
| 152 | T22 | CANNOT_SEND_DATA_NOT_CONNECTED | 无法发送数据，未连接 | 先建立连接再发送数据 | 可使用相同ID重试 |
| 153 | T23 | DATA_TRANSMISSION_FAILED | 发送数据失败 | 检查网络/硬件状态，重试发送 | 可使用相同ID重试 |
| 154 | T24 | DATA_SEND_UNEXPECTED_ERROR | 发送数据时发生意外错误 | 记录错误日志，重启服务或重试 | 可使用相同ID重试 |
| 155 | - | DATA_RECEIVE_FAILED | 接收数据失败 | 检查连接状态和数据完整性 | 可使用相同ID重试 |
| 156 | - | DATA_CORRUPTION | 数据损坏 | 数据传输过程中发生损坏 | 可使用相同ID重试 |
| 157 | - | DATA_SIZE_EXCEEDED | 数据大小超限 | 数据包大小超过限制 | 分割数据或减少数据量 |
| 158 | - | DATA_ENCODING_ERROR | 数据编码错误 | 数据编码格式错误 | 检查数据编码格式 |
| 159 | - | DATA_CHECKSUM_FAILED | 数据校验失败 | 数据完整性校验失败 | 可使用相同ID重试 |
| 160 | - | DATA_TIMEOUT | 数据传输超时 | 数据传输超时 | 增加超时时间或重试 |

### 161-170: 设备硬件错误

| 新错误码 | 原错误码 | 错误名称 | 说明 | 解决方案 | 重试策略 |
|----------|----------|----------|------|----------|----------|
| 161 | T10 | INVALID_USB_PROTOCOL | 无效的USB协议 | 使用支持的USB协议或启用自动检测 | 可使用相同ID重试 |
| 162 | T11 | USB_DEVICE_ACCESS_DENIED | USB设备访问被拒绝 | 重新授权USB设备权限 | 可使用相同ID重试 |
| 163 | T12 | USB_DEVICE_CONNECTION_TIMEOUT | USB设备连接超时 | 检查USB连接，重新插拔设备 | 可使用相同ID重试 |
| 164 | T13 | SERIAL_PORT_NOT_READY | 串口设备未就绪 | 重新初始化串口连接 | 可使用相同ID重试 |
| 165 | T35 | CABLE_NOT_CONNECTED | 线缆未连接 | 请连接 USB 或串口线缆 | 可使用相同ID重试 |
| 166 | - | USB_DEVICE_NOT_FOUND | USB设备未找到 | 检查USB设备连接 | 可使用相同ID重试 |
| 167 | - | SERIAL_PORT_BUSY | 串口端口忙 | 串口被其他程序占用 | 关闭占用程序或重试 |
| 168 | - | DEVICE_FIRMWARE_ERROR | 设备固件错误 | 设备固件异常 | 更新设备固件 |
| 169 | - | HARDWARE_MALFUNCTION | 硬件故障 | 硬件设备故障 | 更换硬件设备 |
| 170 | - | DEVICE_POWER_INSUFFICIENT | 设备供电不足 | USB供电不足 | 使用带电源的USB集线器 |

### 171-180: 网络通信错误

| 新错误码 | 原错误码 | 错误名称 | 说明 | 解决方案 | 重试策略 |
|----------|----------|----------|------|----------|----------|
| 171 | - | NETWORK_UNREACHABLE | 网络不可达 | 网络连接不可用 | 检查网络连接 |
| 172 | - | DNS_RESOLUTION_FAILED | DNS解析失败 | 域名解析失败 | 使用IP地址或检查DNS设置 |
| 173 | - | SSL_HANDSHAKE_FAILED | SSL握手失败 | SSL/TLS握手失败 | 检查SSL证书和配置 |
| 174 | - | WEBSOCKET_PROTOCOL_ERROR | WebSocket协议错误 | WebSocket协议异常 | 检查WebSocket实现 |
| 175 | - | NETWORK_TIMEOUT | 网络超时 | 网络操作超时 | 增加超时时间或检查网络 |
| 176 | - | PROXY_CONNECTION_FAILED | 代理连接失败 | 代理服务器连接失败 | 检查代理设置 |
| 177 | - | FIREWALL_BLOCKED | 防火墙阻止 | 防火墙阻止连接 | 配置防火墙规则 |
| 178 | - | PORT_UNAVAILABLE | 端口不可用 | 网络端口不可用 | 更换端口或检查端口占用 |
| 179 | - | PROTOCOL_VERSION_UNSUPPORTED | 协议版本不支持 | 通信协议版本不匹配 | 更新协议版本 |
| 180 | - | NETWORK_INTERFACE_ERROR | 网络接口错误 | 网络接口异常 | 检查网络接口配置 |

### 181-190: 权限认证错误

| 新错误码 | 原错误码 | 错误名称 | 说明 | 解决方案 | 重试策略 |
|----------|----------|----------|------|----------|----------|
| 181 | - | INVALID_APP_ID | 无效的应用ID | AppId格式或值无效 | 检查AppId配置 |
| 182 | - | INVALID_SECRET_KEY | 无效的密钥 | SecretKey格式或值无效 | 检查SecretKey配置 |
| 183 | - | SIGNATURE_VERIFICATION_FAILED | 签名验证失败 | 请求签名验证失败 | 检查签名算法和密钥 |
| 184 | - | ACCESS_TOKEN_EXPIRED | 访问令牌过期 | 访问令牌已过期 | 重新获取访问令牌 |
| 185 | - | INSUFFICIENT_PERMISSIONS | 权限不足 | 应用权限不足 | 申请相应权限 |
| 186 | - | ACCOUNT_SUSPENDED | 账户被暂停 | 商户账户被暂停 | 联系客服恢复账户 |
| 187 | - | RATE_LIMIT_EXCEEDED | 请求频率超限 | 请求频率超过限制 | 降低请求频率 |
| 188 | - | IP_ADDRESS_BLOCKED | IP地址被阻止 | IP地址被加入黑名单 | 更换IP或联系客服 |
| 189 | - | CERTIFICATE_INVALID | 证书无效 | 客户端证书无效 | 更新或重新安装证书 |
| 190 | - | AUTHENTICATION_REQUIRED | 需要认证 | 操作需要身份认证 | 完成身份认证 |

### 191-199: 系统状态错误

| 新错误码 | 原错误码 | 错误名称 | 说明 | 解决方案 | 重试策略 |
|----------|----------|----------|------|----------|----------|
| 191 | - | SYSTEM_MAINTENANCE | 系统维护中 | 系统正在维护 | 等待维护完成 |
| 192 | - | SERVICE_UNAVAILABLE | 服务不可用 | 服务暂时不可用 | 稍后重试 |
| 193 | - | RESOURCE_EXHAUSTED | 资源耗尽 | 系统资源不足 | 等待资源释放或联系客服 |
| 194 | - | INTERNAL_SERVER_ERROR | 内部服务器错误 | 服务器内部错误 | 联系技术支持 |
| 195 | - | DATABASE_CONNECTION_FAILED | 数据库连接失败 | 数据库连接异常 | 检查数据库状态 |
| 196 | - | CACHE_SERVICE_ERROR | 缓存服务错误 | 缓存服务异常 | 重启缓存服务 |
| 197 | - | QUEUE_SERVICE_ERROR | 队列服务错误 | 消息队列服务异常 | 检查队列服务状态 |
| 198 | - | STORAGE_SERVICE_ERROR | 存储服务错误 | 存储服务异常 | 检查存储服务状态 |
| 199 | - | UNKNOWN_ERROR | 未知错误 | 未知的系统错误 | 联系技术支持 |

## 成功响应码

| 错误码 | 说明 | 备注 |
|--------|------|------|
| 000 | 交易成功 | 操作成功完成 |

## 幂等性机制与重试策略

### 核心原则

1. **特殊错误码**（124、125、126、000）重试时，必须使用**新的** `transactionRequestId`
2. **其他所有错误码**重试时，可以使用**相同的** `transactionRequestId`
3. 修改金额或订单后，必须使用新的 `transactionRequestId`

### 重试策略分类

#### 必须使用新ID的错误码
- **125** (HOST_TRANSACTION_REJECTED): host交易拒绝
- **126** (HOST_TRANSACTION_PROCESSING): Host交易处理中
- **000** (SUCCESS): 交易成功，无需重试

#### 特殊处理的错误码
- **124** (TAPRO_RESPONSE_TIMEOUT): 先查询交易状态，确认结果后再决定重试策略

#### 可使用相同ID重试的错误码
- **101-123**: 初始化、配置、连接、部分交易错误
- **127-140**: 其他交易错误
- **141-199**: 参数、传输、硬件、网络、权限、系统错误

## 错误处理代码示例

### 1. 错误码判断逻辑

```kotlin
/**
 * 判断是否可以使用相同的 transactionRequestId 重试
 */
private fun canRetryWithSameId(code: String): Boolean {
    val errorCode = code.toIntOrNull() ?: return false
    
    // 特殊错误码：124、125、126、000 不能使用相同ID
    return errorCode !in listOf(124, 125, 126, 0)
}

/**
 * 获取错误类型
 */
private fun getErrorCategory(code: String): ErrorCategory {
    val errorCode = code.toIntOrNull() ?: return ErrorCategory.UNKNOWN
    
    return when (errorCode) {
        in 101..110 -> ErrorCategory.INITIALIZATION
        in 111..120 -> ErrorCategory.CONNECTION
        in 121..140 -> ErrorCategory.TRANSACTION
        in 141..150 -> ErrorCategory.PARAMETER_VALIDATION
        in 151..160 -> ErrorCategory.DATA_TRANSMISSION
        in 161..170 -> ErrorCategory.DEVICE_HARDWARE
        in 171..180 -> ErrorCategory.NETWORK_COMMUNICATION
        in 181..190 -> ErrorCategory.PERMISSION_AUTH
        in 191..199 -> ErrorCategory.SYSTEM_STATUS
        0 -> ErrorCategory.SUCCESS
        else -> ErrorCategory.UNKNOWN
    }
}
```

### 2. 错误处理策略

```kotlin
fun handlePaymentError(error: PaymentError) {
    val errorCode = error.code.toIntOrNull() ?: return
    
    when (errorCode) {
        // 初始化和配置错误 (101-110)
        in 101..110 -> {
            showError("初始化错误：${error.message}")
            // 需要修复配置后重试
        }
        
        // 连接错误 (111-120)
        in 111..120 -> {
            showError("连接错误：${error.message}")
            // 可使用相同ID重试
            scheduleRetryWithSameId()
        }
        
        // 交易错误 (121-140)
        124 -> {
            // 响应超时：先查询状态
            queryTransactionStatus()
        }
        125 -> {
            // host交易拒绝：必须使用新ID
            showError("交易被拒绝，请使用新的交易请求ID重试")
        }
        126 -> {
            // Host交易处理中：必须使用新ID
            showError("交易处理中，请查询状态或使用新ID重试")
        }
        in 121..123, in 127..140 -> {
            // 其他交易错误：可使用相同ID重试
            scheduleRetryWithSameId()
        }
        
        // 参数验证错误 (141-150)
        in 141..150 -> {
            showError("参数错误：${error.message}")
            // 修复参数后可使用相同ID重试
        }
        
        // 数据传输错误 (151-160)
        in 151..160 -> {
            showError("数据传输错误：${error.message}")
            // 可使用相同ID重试
            scheduleRetryWithSameId()
        }
        
        // 设备硬件错误 (161-170)
        in 161..170 -> {
            showError("设备错误：${error.message}")
            // 修复硬件问题后可使用相同ID重试
        }
        
        // 网络通信错误 (171-180)
        in 171..180 -> {
            showError("网络错误：${error.message}")
            // 可使用相同ID重试
            scheduleRetryWithSameId()
        }
        
        // 权限认证错误 (181-190)
        in 181..190 -> {
            showError("认证错误：${error.message}")
            // 修复认证问题后可重试
        }
        
        // 系统状态错误 (191-199)
        in 191..199 -> {
            showError("系统错误：${error.message}")
            // 等待系统恢复后重试
        }
        
        // 成功
        0 -> {
            // 交易成功，无需处理
        }
        
        else -> {
            showError("未知错误：${error.message}")
        }
    }
}
```

### 3. 错误分类枚举

```kotlin
enum class ErrorCategory(val range: IntRange, val description: String) {
    SUCCESS(0..0, "成功"),
    INITIALIZATION(101..110, "初始化和配置错误"),
    CONNECTION(111..120, "连接错误"),
    TRANSACTION(121..140, "交易错误"),
    PARAMETER_VALIDATION(141..150, "参数验证错误"),
    DATA_TRANSMISSION(151..160, "数据传输错误"),
    DEVICE_HARDWARE(161..170, "设备硬件错误"),
    NETWORK_COMMUNICATION(171..180, "网络通信错误"),
    PERMISSION_AUTH(181..190, "权限认证错误"),
    SYSTEM_STATUS(191..199, "系统状态错误"),
    UNKNOWN(-1..-1, "未知错误");
    
    companion object {
        fun fromCode(code: Int): ErrorCategory {
            return values().find { code in it.range } ?: UNKNOWN
        }
    }
}
```

## 错误码映射表

### 原错误码到新错误码映射

| 原错误码 | 新错误码 | 错误名称 |
|----------|----------|----------|
| T01 | 101 | SDK_NOT_INITIALIZED |
| T02 | 102 | TAPRO_APP_NOT_INSTALLED |
| T03 | 111 | IPC_SERVICE_BIND_FAILED |
| T04 | 112 | WEBSOCKET_CONNECTION_FAILED |
| T05 | 113 | MDNS_DISCOVERY_FAILED |
| T06 | 114 | CONNECTION_DISCONNECTED |
| T07 | 115 | NO_CONNECTION_ESTABLISHED |
| T08 | 116 | MANUAL_DISCONNECT |
| T09 | 117 | CONNECTION_ALREADY_EXISTS |
| T10 | 161 | INVALID_USB_PROTOCOL |
| T11 | 162 | USB_DEVICE_ACCESS_DENIED |
| T12 | 163 | USB_DEVICE_CONNECTION_TIMEOUT |
| T13 | 164 | SERIAL_PORT_NOT_READY |
| T14 | 103 | VSP_CLIENT_NOT_READY |
| T15 | 141 | MISSING_REQUEST_PARAMETERS |
| T16 | 142 | INVALID_JSON_FORMAT |
| T17 | 143 | PROTOCOL_DATA_INVALID |
| T18 | 144 | INVALID_AMOUNT_PARAMETER |
| T19 | 145 | PROTOCOL_TYPE_MISMATCH |
| T20 | 104 | NO_SERVICE_KERNEL |
| T21 | 151 | DATA_SEND_FAILED |
| T22 | 152 | CANNOT_SEND_DATA_NOT_CONNECTED |
| T23 | 153 | DATA_TRANSMISSION_FAILED |
| T24 | 154 | DATA_SEND_UNEXPECTED_ERROR |
| T25 | 105 | TRANSACTION_INTERFACE_NULL |
| T27 | 121 | TRANSACTION_LOCKED |
| T28 | 146 | UNSUPPORTED_ACTION_TYPE |
| T29 | 127 | TRANSACTION_TERMINATED |
| T30 | 122 | SYSTEM_PROCESSING_OTHER_TRANSACTION |
| T31 | 123 | INIT_FAILED_SYSTEM_BUSY |
| T34 | 124 | TAPRO_RESPONSE_TIMEOUT |
| T35 | 165 | CABLE_NOT_CONNECTED |
| T37 | 118 | CONNECTION_NOT_ESTABLISHED |
| T38 | 125 | HOST_TRANSACTION_REJECTED |
| T39 | 126 | HOST_TRANSACTION_PROCESSING |
| T40 | 128 | TERMINATION_FAILED |
| T41 | 106 | CONNECTION_AUTH_FAILED |
| 000 | 000 | SUCCESS |

## 优势与特点

### 1. 清晰的分类体系
- 三位数字格式便于识别和分类
- 每个范围代表特定的错误类型
- 便于扩展和维护

### 2. 向后兼容
- 提供原错误码到新错误码的映射
- 支持渐进式迁移
- 保持现有逻辑不变

### 3. 易于理解
- 数字范围直观表示错误类型
- 统一的命名规范
- 详细的错误描述和解决方案

### 4. 便于维护
- 预留了扩展空间
- 结构化的错误定义
- 便于自动化处理和分析

## 迁移指南

### 1. 代码迁移

```kotlin
// 旧代码
when (error.code) {
    "T01" -> handleInitError()
    "T38" -> handleHostRejection()
}

// 新代码
when (error.code.toIntOrNull()) {
    101 -> handleInitError()
    125 -> handleHostRejection()
}

// 或使用映射函数
when (mapLegacyErrorCode(error.code)) {
    101 -> handleInitError()
    125 -> handleHostRejection()
}
```

### 2. 配置更新

```kotlin
// 错误码映射配置
class ErrorCodeMapper {
    private val legacyToNewMapping = mapOf(
        "T01" to 101,
        "T02" to 102,
        "T38" to 125,
        "T39" to 126,
        // ... 其他映射
    )
    
    fun mapLegacyCode(legacyCode: String): Int? {
        return legacyToNewMapping[legacyCode]
    }
}
```

## 总结

新的三位数字错误码体系具有以下优势：

1. **结构清晰**：按功能模块分类，便于快速定位问题
2. **易于扩展**：每个范围预留了扩展空间
3. **便于维护**：统一的格式和命名规范
4. **向后兼容**：提供完整的映射关系
5. **便于自动化**：数字格式便于程序处理和分析

这套新的错误码体系将为 Taplink SDK 提供更好的错误处理和问题诊断能力。