# 错误码优化分析报告

## 概述

本文档分析了半集成模式错误码映射表（247-286行），提出了专业术语优化和错误合并建议。

## 一、可以合并的错误码

### 1. 连接相关错误合并

**问题**：T07和T37都映射到212，但错误名称不同
- T07 → 212: `NO_CONNECTION_ESTABLISHED` (未建立连接)
- T37 → 212: `CONNECTION_NOT_ESTABLISHED` (未建立连接)

**建议**：统一为一个错误名称
- **合并为**：`NO_CONNECTION_ESTABLISHED` (未建立连接)
- **理由**：两个错误码含义相同，都是表示连接未建立，应该使用统一的错误名称

### 2. 数据传输错误合并

**问题**：T21和T23都表示"发送数据失败"，但描述略有不同
- T21 → 341: `DATA_SEND_FAILED` (发送数据失败)
- T23 → 343: `DATA_TRANSMISSION_FAILED` (发送数据失败)

**建议**：根据实际使用场景区分或合并
- **方案1（合并）**：如果T21和T23在实际使用中没有明显区别，可以合并为一个错误码
- **方案2（区分）**：如果T21是通用发送失败，T23是传输层失败，可以保留但优化命名：
  - T21 → `DATA_SEND_FAILED` (数据发送失败)
  - T23 → `DATA_TRANSMISSION_ERROR` (数据传输错误)

**当前代码分析**：
- T21: "Failed to send data" - 通用发送失败
- T23: "Failed to send data" - 也是通用发送失败
- **结论**：可以合并，或根据实际错误原因区分（网络失败 vs 硬件失败）

## 二、专业术语优化

### 1. 应用名称统一

**问题**：文档中混用了"Tapro"和"TaPro"
- T02 → 231: `TAPRO_APP_NOT_INSTALLED`
- T34 → 314: `TAPRO_RESPONSE_TIMEOUT`

**建议**：统一使用"TaPro"（根据官方命名）
- `TAPRO_APP_NOT_INSTALLED` → `TAPRO_APP_NOT_INSTALLED` (保持大写，但说明中统一为TaPro)
- `TAPRO_RESPONSE_TIMEOUT` → `TAPRO_RESPONSE_TIMEOUT`

### 2. Host大小写统一

**问题**：文档中混用了"host"和"Host"
- T38 → 315: `HOST_TRANSACTION_REJECTED` (host交易拒绝)
- T39 → 316: `HOST_TRANSACTION_PROCESSING` (Host交易处理中)

**建议**：统一使用"Host"（首字母大写，表示支付主机系统）
- `HOST_TRANSACTION_REJECTED` → `HOST_TRANSACTION_REJECTED` (Host交易拒绝)
- `HOST_TRANSACTION_PROCESSING` → `HOST_TRANSACTION_PROCESSING` (Host交易处理中)

### 3. 技术术语优化

**问题1**：VSP术语
- T14 → 202: `VSP_CLIENT_NOT_READY` (VSP客户端未就绪)

**建议**：更专业的描述
- `VSP_CLIENT_NOT_READY` → `VSP_CLIENT_NOT_READY` (虚拟串口客户端未就绪)
- 或保持简洁：`VSP_CLIENT_NOT_READY` (VSP客户端未就绪) - 当前已足够专业

**问题2**：IPC术语
- T03 → 232: `IPC_SERVICE_BIND_FAILED` (IPC服务绑定失败)

**建议**：更专业的描述
- `IPC_SERVICE_BIND_FAILED` → `IPC_SERVICE_BIND_FAILED` (进程间通信服务绑定失败)
- 或保持简洁：`IPC_SERVICE_BIND_FAILED` (IPC服务绑定失败) - 当前已足够专业

**问题3**：mDNS术语
- T05 → 271: `MDNS_DISCOVERY_FAILED` (mDNS发现失败)

**建议**：更专业的描述
- `MDNS_DISCOVERY_FAILED` → `MDNS_DISCOVERY_FAILED` (多播DNS服务发现失败)
- 或保持简洁：`MDNS_DISCOVERY_FAILED` (mDNS服务发现失败) - 更专业

### 4. 错误名称优化

**问题1**：交易错误描述
- T27 → 311: `TRANSACTION_LOCKED` (交易锁定(防并发操作))

**建议**：简化描述
- `TRANSACTION_LOCKED` → `TRANSACTION_LOCKED` (交易锁定)
- 括号中的说明可以在详细描述中体现

**问题2**：系统错误描述
- T31 → 313: `INIT_FAILED_SYSTEM_BUSY` (初始化失败：系统正在处理其他交易)

**建议**：简化错误名称
- `INIT_FAILED_SYSTEM_BUSY` → `INIT_FAILED_SYSTEM_BUSY` (初始化失败：系统繁忙)
- 或：`SYSTEM_BUSY_INIT_FAILED` (系统繁忙导致初始化失败)

## 三、具体优化建议

### 优化后的错误码映射表

| 原错误码 | 新错误码 | 优化后的错误名称 | 分类 | 重试策略 | 优化说明 |
|----------|----------|------------------|------|----------|----------|
| 000 | 000 | SUCCESS | 成功 | 无需重试 | - |
| T01 | 201 | SDK_NOT_INITIALIZED | 通用系统错误 | 修复后可重试 | - |
| T14 | 202 | VSP_CLIENT_NOT_READY | 通用系统错误 | 修复后可重试 | 保持，VSP是标准术语 |
| T20 | 203 | NO_SERVICE_KERNEL | 通用系统错误 | 修复后可重试 | - |
| T25 | 204 | TRANSACTION_INTERFACE_NULL | 通用系统错误 | 修复后可重试 | - |
| T09 | 211 | CONNECTION_ALREADY_EXISTS | 通用连接管理错误 | 无需重试 | - |
| T07 | 212 | NO_CONNECTION_ESTABLISHED | 通用连接管理错误 | 可使用相同ID重试 | **合并**：T07和T37统一 |
| T37 | 212 | NO_CONNECTION_ESTABLISHED | 通用连接管理错误 | 可使用相同ID重试 | **合并**：与T07统一 |
| T06 | 213 | CONNECTION_DISCONNECTED | 通用连接管理错误 | 可使用相同ID重试 | - |
| T08 | 214 | MANUAL_DISCONNECT | 通用连接管理错误 | 无需重试 | - |
| T41 | 221 | CONNECTION_AUTH_FAILED | 通用认证错误 | 修复后可重试 | - |
| T02 | 231 | TAPRO_APP_NOT_INSTALLED | APP_TO_APP连接错误 | 修复后可重试 | 统一为TaPro（说明中） |
| T03 | 232 | IPC_SERVICE_BIND_FAILED | APP_TO_APP连接错误 | 可使用相同ID重试 | 保持，IPC是标准术语 |
| T04 | 251 | WEBSOCKET_CONNECTION_FAILED | LAN连接错误 | 可使用相同ID重试 | - |
| T05 | 271 | MDNS_DISCOVERY_FAILED | LAN配置错误 | 可使用相同ID重试 | 优化：mDNS服务发现失败 |
| T35 | 281 | CABLE_NOT_CONNECTED | CABLE连接错误 | 修复后可重试 | - |
| T11 | 283 | USB_PERMISSION_DENIED | CABLE连接错误 | 修复后可重试 | - |
| T12 | 286 | USB_DEVICE_CONNECTION_TIMEOUT | CABLE连接错误 | 可使用相同ID重试 | - |
| T10 | 291 | INVALID_USB_PROTOCOL | CABLE协议错误 | 可使用相同ID重试 | - |
| T13 | 294 | SERIAL_PORT_NOT_READY | CABLE协议错误 | 可使用相同ID重试 | - |
| T27 | 311 | TRANSACTION_LOCKED | 交易处理错误 | 可使用相同ID重试 | 简化：移除括号说明 |
| T30 | 312 | SYSTEM_PROCESSING_OTHER_TRANSACTION | 交易处理错误 | 可使用相同ID重试 | - |
| T31 | 313 | INIT_FAILED_SYSTEM_BUSY | 交易处理错误 | 可使用相同ID重试 | 优化：系统繁忙导致初始化失败 |
| T34 | 314 | TAPRO_RESPONSE_TIMEOUT | 交易处理错误 | **特殊处理** | 统一为TaPro（说明中） |
| T38 | 315 | HOST_TRANSACTION_REJECTED | 交易处理错误 | **必须使用新ID** | 统一为Host（首字母大写） |
| T39 | 316 | HOST_TRANSACTION_PROCESSING | 交易处理错误 | **必须使用新ID** | 统一为Host（首字母大写） |
| T29 | 317 | TRANSACTION_TERMINATED | 交易处理错误 | 无需重试 | - |
| T40 | 318 | TERMINATION_FAILED | 交易处理错误 | 可使用相同ID重试 | - |
| T15 | 331 | MISSING_REQUEST_PARAMETERS | 参数验证错误 | 修复后可使用相同ID重试 | - |
| T16 | 332 | INVALID_JSON_FORMAT | 参数验证错误 | 修复后可使用相同ID重试 | - |
| T17 | 333 | PROTOCOL_DATA_INVALID | 参数验证错误 | 修复后可使用相同ID重试 | - |
| T18 | 334 | INVALID_AMOUNT_PARAMETER | 参数验证错误 | 修复后可使用相同ID重试 | - |
| T19 | 335 | PROTOCOL_TYPE_MISMATCH | 参数验证错误 | 修复后可使用相同ID重试 | - |
| T28 | 336 | UNSUPPORTED_ACTION_TYPE | 参数验证错误 | 修复后可使用相同ID重试 | - |
| T21 | 341 | DATA_SEND_FAILED | 数据传输错误 | 可使用相同ID重试 | **建议合并**：与T23合并或区分 |
| T22 | 342 | CANNOT_SEND_DATA_NOT_CONNECTED | 数据传输错误 | 可使用相同ID重试 | - |
| T23 | 343 | DATA_TRANSMISSION_FAILED | 数据传输错误 | 可使用相同ID重试 | **建议合并**：与T21合并或区分 |
| T24 | 344 | DATA_SEND_UNEXPECTED_ERROR | 数据传输错误 | 可使用相同ID重试 | - |

## 四、推荐优化方案

### 方案1：保守优化（推荐）

**只合并明显重复的错误，优化术语一致性**

1. **合并T07和T37**：统一为`NO_CONNECTION_ESTABLISHED`
2. **统一术语**：
   - "Tapro" → "TaPro"（在说明中统一）
   - "host" → "Host"（首字母大写）
3. **优化描述**：
   - `MDNS_DISCOVERY_FAILED` → 说明中改为"mDNS服务发现失败"
   - `TRANSACTION_LOCKED` → 移除括号，在详细描述中说明

### 方案2：激进优化

**合并更多相似错误，简化错误码体系**

1. **合并T07和T37**：统一为`NO_CONNECTION_ESTABLISHED`
2. **合并T21和T23**：统一为`DATA_SEND_FAILED`，在详细描述中区分网络失败和硬件失败
3. **统一所有术语**：按方案1执行

## 五、实施建议

### 优先级1（必须优化）
- ✅ 合并T07和T37的错误名称
- ✅ 统一Host大小写（Host交易拒绝/Host交易处理中）
- ✅ 统一TaPro命名（在说明中）

### 优先级2（建议优化）
- ⚠️ 优化mDNS描述为"mDNS服务发现失败"
- ⚠️ 简化TRANSACTION_LOCKED描述

### 优先级3（可选优化）
- 💡 考虑合并T21和T23（需要确认实际使用场景）
- 💡 优化INIT_FAILED_SYSTEM_BUSY命名

## 六、优化后的错误码映射表（推荐版本）

| 原错误码 | 新错误码 | 错误名称 | 分类 | 重试策略 |
|----------|----------|----------|------|----------|
| 000 | 000 | SUCCESS | 成功 | 无需重试 |
| T01 | 201 | SDK_NOT_INITIALIZED | 通用系统错误 | 修复后可重试 |
| T14 | 202 | VSP_CLIENT_NOT_READY | 通用系统错误 | 修复后可重试 |
| T20 | 203 | NO_SERVICE_KERNEL | 通用系统错误 | 修复后可重试 |
| T25 | 204 | TRANSACTION_INTERFACE_NULL | 通用系统错误 | 修复后可重试 |
| T09 | 211 | CONNECTION_ALREADY_EXISTS | 通用连接管理错误 | 无需重试 |
| T07 | 212 | NO_CONNECTION_ESTABLISHED | 通用连接管理错误 | 可使用相同ID重试 |
| T37 | 212 | NO_CONNECTION_ESTABLISHED | 通用连接管理错误 | 可使用相同ID重试 |
| T06 | 213 | CONNECTION_DISCONNECTED | 通用连接管理错误 | 可使用相同ID重试 |
| T08 | 214 | MANUAL_DISCONNECT | 通用连接管理错误 | 无需重试 |
| T41 | 221 | CONNECTION_AUTH_FAILED | 通用认证错误 | 修复后可重试 |
| T02 | 231 | TAPRO_APP_NOT_INSTALLED | APP_TO_APP连接错误 | 修复后可重试 |
| T03 | 232 | IPC_SERVICE_BIND_FAILED | APP_TO_APP连接错误 | 可使用相同ID重试 |
| T04 | 251 | WEBSOCKET_CONNECTION_FAILED | LAN连接错误 | 可使用相同ID重试 |
| T05 | 271 | MDNS_DISCOVERY_FAILED | LAN配置错误 | 可使用相同ID重试 |
| T35 | 281 | CABLE_NOT_CONNECTED | CABLE连接错误 | 修复后可重试 |
| T11 | 283 | USB_PERMISSION_DENIED | CABLE连接错误 | 修复后可重试 |
| T12 | 286 | USB_DEVICE_CONNECTION_TIMEOUT | CABLE连接错误 | 可使用相同ID重试 |
| T10 | 291 | INVALID_USB_PROTOCOL | CABLE协议错误 | 可使用相同ID重试 |
| T13 | 294 | SERIAL_PORT_NOT_READY | CABLE协议错误 | 可使用相同ID重试 |
| T27 | 311 | TRANSACTION_LOCKED | 交易处理错误 | 可使用相同ID重试 |
| T30 | 312 | SYSTEM_PROCESSING_OTHER_TRANSACTION | 交易处理错误 | 可使用相同ID重试 |
| T31 | 313 | INIT_FAILED_SYSTEM_BUSY | 交易处理错误 | 可使用相同ID重试 |
| T34 | 314 | TAPRO_RESPONSE_TIMEOUT | 交易处理错误 | **特殊处理**：先查询状态，确认后可使用相同ID重试 |
| T38 | 315 | HOST_TRANSACTION_REJECTED | 交易处理错误 | **必须使用新ID** |
| T39 | 316 | HOST_TRANSACTION_PROCESSING | 交易处理错误 | **必须使用新ID** |
| T29 | 317 | TRANSACTION_TERMINATED | 交易处理错误 | 无需重试 |
| T40 | 318 | TERMINATION_FAILED | 交易处理错误 | 可使用相同ID重试 |
| T15 | 331 | MISSING_REQUEST_PARAMETERS | 参数验证错误 | 修复后可使用相同ID重试 |
| T16 | 332 | INVALID_JSON_FORMAT | 参数验证错误 | 修复后可使用相同ID重试 |
| T17 | 333 | PROTOCOL_DATA_INVALID | 参数验证错误 | 修复后可使用相同ID重试 |
| T18 | 334 | INVALID_AMOUNT_PARAMETER | 参数验证错误 | 修复后可使用相同ID重试 |
| T19 | 335 | PROTOCOL_TYPE_MISMATCH | 参数验证错误 | 修复后可使用相同ID重试 |
| T28 | 336 | UNSUPPORTED_ACTION_TYPE | 参数验证错误 | 修复后可使用相同ID重试 |
| T21 | 341 | DATA_SEND_FAILED | 数据传输错误 | 可使用相同ID重试 |
| T22 | 342 | CANNOT_SEND_DATA_NOT_CONNECTED | 数据传输错误 | 可使用相同ID重试 |
| T23 | 343 | DATA_TRANSMISSION_FAILED | 数据传输错误 | 可使用相同ID重试 |
| T24 | 344 | DATA_SEND_UNEXPECTED_ERROR | 数据传输错误 | 可使用相同ID重试 |

## 七、总结

### 必须优化的项目
1. **合并T07和T37**：统一错误名称为`NO_CONNECTION_ESTABLISHED`
2. **统一Host大小写**：所有Host相关错误统一为首字母大写
3. **统一TaPro命名**：在说明中统一使用TaPro

### 建议优化的项目
1. **优化mDNS描述**：改为"mDNS服务发现失败"
2. **简化TRANSACTION_LOCKED**：移除括号说明

### 可选优化的项目
1. **考虑合并T21和T23**：需要确认实际使用场景是否有区别

---

**版本**: 1.0.0  
**更新日期**: 2025-01-12  
**作者**: TaPro Team
