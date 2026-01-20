# 半集成模式错误码（三位数字格式）

## 概述

本文档定义了 Taplink SDK 半集成模式的错误码体系，采用三位数字格式，按照错误类型和连接模式进行分类。

## 错误码分类体系

### 错误码范围定义

| 范围 | 错误类型 | 说明 | 连接模式 |
|------|----------|------|----------|
| 201-209 | 通用系统错误 | SDK初始化、配置、权限等通用错误 | 所有模式 |
| 211-220 | 通用连接管理错误 | 连接状态、连接限制等通用连接错误 | 所有模式 |
| 221-230 | 通用认证错误 | 认证、凭证、会话等通用认证错误 | 所有模式 |
| 231-250 | APP_TO_APP模式错误 | APP_TO_APP连接和协议错误 | APP_TO_APP |
| 251-280 | LAN模式错误 | LAN连接、网络、配置错误 | LAN |
| 281-310 | CABLE模式错误 | CABLE连接、协议、设备、权限错误 | CABLE |
| 311-350 | 交易处理错误 | 交易处理相关错误（包括参数验证、数据传输、交易业务逻辑） | 所有模式 |

## 详细错误码定义

### 201-209: 通用系统错误

| 错误码 | 原错误码 | 错误名称 | 说明 | ISV消息 | ISO消息 | 解决方案 | 重试策略 |
|--------|----------|----------|------|---------|---------|----------|----------|
| 201 | T01, G0001 | SDK_NOT_INITIALIZED | SDK未初始化 | SDK not initialized, please call init() first | System not ready, please restart application | 调用TaplinkSDK.init()初始化SDK | 修复后可重试 |
| 202 | T14, G0002, C1002 | VSP_CLIENT_NOT_READY | 设备未检测到 | VSP client not ready, device not detected | System not ready, device not found | 检查线缆和设备，重新初始化VSP客户端 | 修复后可重试 |
| 203 | T20, G0002 | NO_SERVICE_KERNEL | 无可用服务内核 | No service kernel available | System not ready | 重新初始化服务内核 | 修复后可重试 |
| 204 | T25, G0002 | TRANSACTION_INTERFACE_NULL | 交易接口为空 | Transaction interface is null | System not ready | 重新绑定交易服务接口 | 修复后可重试 |
| 205 | G0002 | INVALID_CONFIGURATION | SDK配置无效 | Invalid SDK configuration provided | Configuration error, contact support | 检查TaplinkConfig配置参数 | 修复后可重试 |
| 206 | G0003 | INSUFFICIENT_PERMISSIONS | 权限不足 | App lacks required permissions | Permission denied, check app settings | 申请相应权限 | 修复后可重试 |
| 207 | G0004 | UNSUPPORTED_OPERATION | 不支持的操作 | Operation not supported in current mode | Feature not available | 使用支持的操作 | 无需重试 |
| 208 | - | CONFIG_VALIDATION_FAILED | 配置验证失败 | SDK configuration validation failed | Configuration error | 检查配置参数格式和值 | 修复后可重试 |
| 209 | T31 | INIT_FAILED_SYSTEM_BUSY | 初始化失败：系统繁忙 | Init failed: system busy | System busy | 等待系统空闲后重新初始化 | 可使用相同ID重试 |

### 211-220: 通用连接管理错误

| 错误码 | 原错误码 | 错误名称 | 说明 | ISV消息 | ISO消息 | 解决方案 | 重试策略 |
|--------|----------|----------|------|---------|---------|----------|----------|
| 211 | T09, G1001 | CONNECTION_ALREADY_EXISTS | 连接已存在 | Connection already established | Already connected to device | 断开现有连接或使用现有连接 | 无需重试 |
| 212 | T07, T37, G1002 | NO_CONNECTION_ESTABLISHED | 未建立连接 | No active connection found | Not connected to payment device | 先调用connect()建立连接 | 可使用相同ID重试 |
| 213 | T06, G1005 | CONNECTION_DISCONNECTED | 连接已断开 | Connection disconnected | Connection lost | 调用connect()重新建立连接 | 可使用相同ID重试 |
| 214 | T08 | MANUAL_DISCONNECT | 手动断开连接 | Manual disconnect | Connection closed | 正常断开，无需处理 | 无需重试 |
| 215 | G1005, G1003, G1004 | CONNECTION_FAILED | 连接失败 | Connection failed | Connection failed | 检查网络/线缆/设备状态并重新连接 | 可使用相同ID重试 |
| 219 | - | CONNECTION_INTERRUPTED | 连接中断 | Connection interrupted unexpectedly | Connection lost | 检查连接状态并重试 | 可使用相同ID重试 |

### 221-230: 通用认证错误

| 错误码 | 原错误码 | 错误名称 | 说明 | ISV消息 | ISO消息 | 解决方案 | 重试策略 |
|--------|----------|----------|------|---------|---------|----------|----------|
| 221 | T41, G2001 | CONNECTION_AUTH_FAILED | 连接认证失败 | Connection authentication failed | Authentication failed | 检查认证参数（appId、secretKey等），重新建立连接 | 修复后可重试 |

### 231-250: APP_TO_APP模式错误

#### 231-240: APP_TO_APP连接错误

| 错误码 | 原错误码 | 错误名称 | 说明 | ISV消息 | ISO消息 | 解决方案 | 重试策略 |
|--------|----------|----------|------|---------|---------|----------|----------|
| 231 | T02, A1001 | TAPRO_APP_NOT_INSTALLED | TaPro应用未安装 | TaPro application not found on device | Payment app not installed | 安装TaPro应用到设备 | 修复后可重试 |
| 232 | T03, A1003 | IPC_SERVICE_BIND_FAILED | IPC服务绑定失败 | IPC service bind failed | Communication error with payment app | 重启TaPro应用或重启设备 | 可使用相同ID重试 |
| 233 | A1003 | INTENT_FAILED | Intent通信失败 | Failed to communicate with TaPro app | Communication error with payment app | 重启两个应用 | 可使用相同ID重试 |
| 234 | A1002 | TAPRO_APP_OUTDATED | TaPro应用版本过旧 | TaPro app version not supported | Payment app needs update | 更新TaPro应用 | 修复后可重试 |
| 235 | A1004 | TAPRO_APP_BUSY | TaPro应用忙碌 | TaPro app is processing another request | Payment app is busy | 等待并重试 | 可使用相同ID重试 |
| 236 | A1005 | PACKAGE_VERIFICATION_FAILED | 包验证失败 | TaPro app package verification failed | Security verification failed | 重新安装TaPro应用 | 修复后可重试 |
| 237 | - | INTENT_PERMISSION_DENIED | Intent权限被拒绝 | Intent permission denied | Permission denied | 检查应用权限设置 | 修复后可重试 |
| 238 | - | APP_NOT_RESPONDING | 应用无响应 | TaPro app not responding | Payment app not responding | 重启TaPro应用 | 修复后可重试 |
| 239 | - | APP_CRASHED | 应用崩溃 | TaPro app crashed | Payment app crashed | 重启TaPro应用 | 修复后可重试 |
| 240 | - | APP_VERSION_MISMATCH | 应用版本不匹配 | TaPro app version incompatible with SDK | Version mismatch | 更新应用或SDK版本 | 修复后可重试 |

### 251-280: LAN模式错误

#### 251-260: LAN连接错误

| 错误码 | 原错误码 | 错误名称 | 说明 | ISV消息 | ISO消息 | 解决方案 | 重试策略 |
|--------|----------|----------|------|---------|---------|----------|----------|
| 251 | T04, L1001 | WEBSOCKET_CONNECTION_FAILED | WebSocket连接失败 | WebSocket connection failed | Network connection failed | 检查防火墙，确保端口8443-8453开放 | 可使用相同ID重试 |
| 252 | L1001 | DEVICE_NOT_FOUND | 设备未找到 | Payment device not found on network | Device not detected | 检查设备网络连接 | 可使用相同ID重试 |
| 253 | L1002 | CONNECTION_REFUSED | 连接被拒绝 | Device refused connection | Connection denied by device | 检查设备设置 | 可使用相同ID重试 |
| 254 | L1003 | NETWORK_UNREACHABLE | 网络不可达 | Cannot reach payment device | Network connection failed | 检查网络连接 | 可使用相同ID重试 |
| 255 | L1004 | INVALID_IP_ADDRESS | IP地址无效 | Invalid device IP address | Invalid network address | 验证设备IP地址 | 修复后可使用相同ID重试 |
| 256 | L1005 | PORT_UNAVAILABLE | 端口不可用 | Cannot connect to device port | Network port blocked | 检查防火墙设置 | 修复后可使用相同ID重试 |
| 257 | - | DEVICE_DISCONNECTED | 设备断开连接 | Device disconnected from network | Device disconnected | 检查设备网络状态 | 可使用相同ID重试 |
| 258 | - | CONNECTION_RESET | 连接重置 | Connection reset by device | Connection reset | 重新建立连接 | 可使用相同ID重试 |
| 259 | - | HOST_UNREACHABLE | 主机不可达 | Host unreachable | Host not reachable | 检查网络路由 | 可使用相同ID重试 |
| 260 | - | CONNECTION_ABORTED | 连接中止 | Connection aborted | Connection aborted | 重新建立连接 | 可使用相同ID重试 |

#### 261-270: LAN网络错误

| 错误码 | 原错误码 | 错误名称 | 说明 | ISV消息 | ISO消息 | 解决方案 | 重试策略 |
|--------|----------|----------|------|---------|---------|----------|----------|
| 261 | L5001 | WIFI_DISCONNECTED | WiFi断开 | WiFi connection lost | Network disconnected | 重新连接WiFi | 修复后可重试 |
| 262 | L5002 | NETWORK_TIMEOUT | 网络超时 | Network operation timeout | Network response timeout | 检查网络稳定性 | 可使用相同ID重试 |
| 263 | L5003 | DNS_RESOLUTION_FAILED | DNS解析失败 | Cannot resolve device hostname | Name resolution failed | 使用IP地址代替 | 修复后可使用相同ID重试 |
| 264 | L5004 | SSL_HANDSHAKE_FAILED | SSL握手失败 | SSL/TLS handshake failed | Secure connection failed | 检查SSL配置 | 修复后可重试 |
| 265 | L5005 | WEBSOCKET_ERROR | WebSocket错误 | WebSocket connection error | Real-time connection failed | 检查WebSocket支持 | 修复后可重试 |
| 266 | - | NETWORK_INTERFACE_DOWN | 网络接口关闭 | Network interface down | Network interface unavailable | 检查网络接口状态 | 修复后可重试 |
| 267 | - | ROUTING_ERROR | 路由错误 | Network routing error | Routing failed | 检查网络路由配置 | 修复后可重试 |
| 268 | - | MTU_SIZE_EXCEEDED | MTU大小超限 | Packet size exceeds MTU | Packet too large | 减少数据包大小 | 修复后可使用相同ID重试 |
| 269 | - | NETWORK_CONGESTION | 网络拥塞 | Network congestion detected | Network busy | 稍后重试 | 稍后重试 |
| 270 | - | PROXY_AUTHENTICATION_FAILED | 代理认证失败 | Proxy authentication failed | Proxy authentication error | 检查代理认证信息 | 修复后可重试 |

#### 271-280: LAN配置错误

| 错误码 | 原错误码 | 错误名称 | 说明 | ISV消息 | ISO消息 | 解决方案 | 重试策略 |
|--------|----------|----------|------|---------|---------|----------|----------|
| 271 | T05, L6005 | MDNS_DISCOVERY_FAILED | mDNS服务发现失败 | mDNS service discovery failed | Automatic detection failed | 手动输入IP地址或检查网络mDNS支持 | 可使用相同ID重试 |
| 272 | L6001 | MISSING_HOST_CONFIG | 缺少主机配置 | Device host not configured | Network address not set | 配置设备IP地址 | 修复后可使用相同ID重试 |
| 273 | L6002 | MISSING_PORT_CONFIG | 缺少端口配置 | Device port not configured | Network port not set | 配置设备端口 | 修复后可使用相同ID重试 |
| 274 | L6003 | INVALID_HOST_FORMAT | 主机格式无效 | Invalid host address format | Invalid network address format | 使用有效的IP或主机名 | 修复后可使用相同ID重试 |
| 275 | L6004 | INVALID_PORT_RANGE | 端口范围无效 | Port number out of valid range | Invalid port number | 使用端口8443-8453 | 修复后可使用相同ID重试 |
| 276 | - | NETWORK_CONFIG_INVALID | 网络配置无效 | Network configuration invalid | Network settings invalid | 检查网络配置 | 修复后可使用相同ID重试 |
| 277 | - | FIREWALL_BLOCKED | 防火墙阻止 | Firewall blocking connection | Firewall blocking | 配置防火墙规则 | 修复后可使用相同ID重试 |
| 278 | - | PORT_ALREADY_IN_USE | 端口已被占用 | Port already in use | Port unavailable | 更换端口或关闭占用程序 | 修复后可使用相同ID重试 |
| 279 | - | NETWORK_SUBNET_MISMATCH | 子网不匹配 | Device not in same subnet | Network subnet mismatch | 确保设备在同一子网 | 修复后可使用相同ID重试 |
| 280 | - | BONJOUR_SERVICE_ERROR | Bonjour服务错误 | Bonjour/mDNS service error | Service discovery error | 检查mDNS服务状态 | 修复后可重试 |

### 281-310: CABLE模式错误

#### 281-290: CABLE连接错误

| 错误码 | 原错误码 | 错误名称 | 说明 | ISV消息 | ISO消息 | 解决方案 | 重试策略 |
|--------|----------|----------|------|---------|---------|----------|----------|
| 281 | T35, C1001 | CABLE_NOT_CONNECTED | 线缆未连接 | USB cable not connected | Cable connection required | 请连接 USB 或串口线缆 | 修复后可重试 |
| 283 | T11, C1003 | USB_PERMISSION_DENIED | USB权限被拒绝 | USB device access denied | Permission required | 重新授权USB设备权限 | 修复后可重试 |
| 284 | C1004 | INCOMPATIBLE_DEVICE | 设备不兼容 | Connected device not supported | Device not compatible | 使用支持的设备 | 修复后可重试 |
| 285 | C1005 | CABLE_DISCONNECTED | 线缆断开 | USB cable disconnected during operation | Cable unplugged | 重新连接线缆 | 可使用相同ID重试 |
| 286 | T12 | USB_DEVICE_CONNECTION_TIMEOUT | USB设备连接超时 | USB device connection timeout | Device connection timeout | 检查USB连接，重新插拔设备 | 可使用相同ID重试 |
| 287 | - | USB_DEVICE_BUSY | USB设备忙碌 | USB device busy | Device busy | 等待设备空闲 | 可使用相同ID重试 |
| 288 | - | USB_DEVICE_ERROR | USB设备错误 | USB device error | Device error | 检查USB设备状态 | 修复后可重试 |
| 289 | - | CABLE_DAMAGED | 线缆损坏 | USB cable damaged | Cable damaged | 更换USB线缆 | 修复后可重试 |
| 290 | - | USB_HUB_ERROR | USB集线器错误 | USB hub error | USB hub error | 检查USB集线器 | 修复后可重试 |

#### 291-300: CABLE协议错误

| 错误码 | 原错误码 | 错误名称 | 说明 | ISV消息 | ISO消息 | 解决方案 | 重试策略 |
|--------|----------|----------|------|---------|---------|----------|----------|
| 291 | T10, C3001 | INVALID_USB_PROTOCOL | 无效的USB协议 | Invalid USB protocol | Communication protocol error | 使用支持的USB协议或启用自动检测 | 可使用相同ID重试 |
| 292 | C3002 | AOA_INITIALIZATION_FAILED | AOA初始化失败 | USB AOA mode initialization failed | USB connection setup failed | 检查USB AOA支持 | 修复后可重试 |
| 293 | C3003 | VSP_COMMUNICATION_ERROR | VSP通信错误 | Virtual Serial Port error | Serial communication error | 检查VSP驱动 | 修复后可重试 |
| 294 | T13, C3004 | SERIAL_PORT_NOT_READY | 串口设备未就绪 | Serial port not ready | Serial port unavailable | 重新初始化串口连接 | 可使用相同ID重试 |
| 295 | C3005 | PROTOCOL_SWITCH_FAILED | 协议切换失败 | Failed to switch cable protocol | Protocol change failed | 使用特定协议重新连接 | 修复后可重试 |
| 296 | C3001 | PROTOCOL_DETECTION_FAILED | 协议检测失败 | Cannot detect cable protocol | Communication protocol error | 尝试手动选择协议 | 修复后可重试 |
| 297 | - | PROTOCOL_NEGOTIATION_FAILED | 协议协商失败 | Protocol negotiation failed | Protocol negotiation error | 检查协议兼容性 | 修复后可重试 |
| 298 | - | AOA_PROTOCOL_ERROR | AOA协议错误 | AOA protocol error | AOA protocol error | 检查AOA协议实现 | 修复后可重试 |
| 299 | - | VSP_DRIVER_ERROR | VSP驱动错误 | VSP driver error | Driver error | 更新VSP驱动 | 修复后可重试 |
| 300 | - | PROTOCOL_TIMEOUT | 协议超时 | Protocol operation timeout | Protocol timeout | 增加协议超时时间 | 可使用相同ID重试 |

#### 301-310: CABLE设备和权限错误

| 错误码 | 原错误码 | 错误名称 | 说明 | ISV消息 | ISO消息 | 解决方案 | 重试策略 |
|--------|----------|----------|------|---------|---------|----------|----------|
| 301 | C4001 | DEVICE_BUSY | 设备忙碌 | Payment device is busy | Device processing other request | 等待并重试 | 可使用相同ID重试 |
| 302 | C4002 | DEVICE_ERROR | 设备错误 | Payment device reported error | Device malfunction | 检查设备状态 | 修复后可重试 |
| 303 | C4003 | FIRMWARE_INCOMPATIBLE | 固件不兼容 | Device firmware not supported | Device software incompatible | 更新设备固件 | 修复后可重试 |
| 304 | C4004 | DEVICE_LOCKED | 设备锁定 | Payment device is locked | Device access restricted | 解锁设备 | 修复后可重试 |
| 305 | C4005 | POWER_INSUFFICIENT | 供电不足 | USB power insufficient for device | Power supply issue | 使用带电源的USB集线器 | 修复后可重试 |
| 306 | C7001 | USB_PERMISSION_REQUIRED | USB权限必需 | USB device permission required | Device access permission needed | 授予USB权限 | 修复后可重试 |
| 307 | C7002 | SERIAL_PORT_PERMISSION | 串口权限 | Serial port access permission required | Serial access permission needed | 授予串口权限 | 修复后可重试 |
| 308 | C7003 | DEVICE_ACCESS_DENIED | 设备访问被拒绝 | Cannot access USB device | Device access blocked | 检查设备权限 | 修复后可重试 |
| 309 | C7004 | DRIVER_PERMISSION_ERROR | 驱动权限错误 | USB driver permission error | Driver access denied | 安装/更新USB驱动 | 修复后可重试 |
| 310 | C7005 | SYSTEM_PERMISSION_DENIED | 系统权限被拒绝 | System-level permission denied | System access restricted | 使用提升的权限运行 | 修复后可重试 |

### 311-350: 交易处理错误

#### 311-320: 参数验证错误（交易处理）

| 错误码 | 原错误码 | 错误名称 | 说明 | ISV消息 | ISO消息 | 解决方案 | 重试策略 |
|--------|----------|----------|------|---------|---------|----------|----------|
| 311 | T15 | MISSING_REQUEST_PARAMETERS | 缺少请求参数 | Missing request parameters | Invalid request | 检查并提供所有必需参数 | 修复后可使用相同ID重试 |
| 312 | T16 | INVALID_JSON_FORMAT | 请求JSON格式错误 | Invalid JSON format | Invalid request format | 检查JSON格式、括号匹配和数据类型 | 修复后可使用相同ID重试 |
| 313 | T17 | PROTOCOL_DATA_INVALID | 协议数据无效或解析失败 | Protocol data invalid or parse failed | Invalid data format | 检查协议数据格式是否正确 | 修复后可使用相同ID重试 |
| 314 | T18 | INVALID_AMOUNT_PARAMETER | 无效的金额参数 | Invalid amount parameter | Invalid amount | 检查金额格式和范围是否有效 | 修复后可使用相同ID重试 |
| 315 | T19 | PROTOCOL_TYPE_MISMATCH | 协议类型不匹配 | Protocol type mismatch | Protocol error | 确认协议类型匹配预期值 | 修复后可使用相同ID重试 |
| 316 | T28 | UNSUPPORTED_ACTION_TYPE | 不支持的交易类型 | Unsupported action type | Transaction type not supported | 检查action参数是否正确且被支持 | 修复后可使用相同ID重试 |
| 317 | - | MISSING_TRANSACTION_ID | 缺少交易ID | Missing transaction ID | Transaction ID required | 提供transactionRequestId或referenceOrderId | 修复后可使用相同ID重试 |
| 318 | - | INVALID_CURRENCY_CODE | 无效的货币代码 | Invalid currency code | Invalid currency | 使用有效的ISO 4217货币代码 | 修复后可使用相同ID重试 |
| 319 | - | PARAMETER_LENGTH_INVALID | 参数长度无效 | Parameter length invalid | Parameter too long | 检查参数长度是否符合要求 | 修复后可使用相同ID重试 |
| 320 | - | PARAMETER_FORMAT_INVALID | 参数格式无效 | Parameter format invalid | Invalid format | 检查参数格式是否符合规范 | 修复后可使用相同ID重试 |

#### 321-330: 数据传输错误（交易处理）

| 错误码 | 原错误码 | 错误名称 | 说明 | ISV消息 | ISO消息 | 解决方案 | 重试策略 |
|--------|----------|----------|------|---------|---------|----------|----------|
| 321 | T21, T23 | DATA_SEND_FAILED | 发送数据失败 | Data send failed | Data transmission failed | 检查网络/硬件状态，重试数据发送 | 可使用相同ID重试 |
| 322 | T22 | CANNOT_SEND_DATA_NOT_CONNECTED | 无法发送数据，未连接 | Cannot send data, not connected | Not connected | 先建立连接再发送数据 | 可使用相同ID重试 |
| 323 | T24 | DATA_SEND_UNEXPECTED_ERROR | 发送数据时发生意外错误 | Unexpected error while sending data | Data send error | 记录错误日志，重启服务或重试 | 可使用相同ID重试 |
| 324 | - | DATA_RECEIVE_FAILED | 接收数据失败 | Data receive failed | Data receive error | 检查连接状态和数据完整性 | 可使用相同ID重试 |
| 325 | - | DATA_CORRUPTION | 数据损坏 | Data corruption | Data corrupted | 数据传输过程中发生损坏 | 可使用相同ID重试 |
| 326 | - | DATA_SIZE_EXCEEDED | 数据大小超限 | Data size exceeded | Data too large | 数据包大小超过限制 | 分割数据或减少数据量 |
| 327 | - | DATA_ENCODING_ERROR | 数据编码错误 | Data encoding error | Encoding error | 数据编码格式错误 | 检查数据编码格式 |
| 328 | - | DATA_CHECKSUM_FAILED | 数据校验失败 | Data checksum failed | Checksum error | 数据完整性校验失败 | 可使用相同ID重试 |
| 329 | - | DATA_TIMEOUT | 数据传输超时 | Data transmission timeout | Transmission timeout | 数据传输超时 | 增加超时时间或重试 |
| 330 | - | (预留) | (预留) | (预留) | (预留) | (预留) | (预留) |

#### 331-350: 交易业务逻辑错误

| 错误码 | 原错误码 | 错误名称 | 说明 | ISV消息 | ISO消息 | 解决方案 | 重试策略 |
|--------|----------|----------|------|---------|---------|----------|----------|
| 331 | T27, T30 | TRANSACTION_LOCKED | 交易锁定（系统正在处理其他交易） | Transaction locked, system processing other transaction | Transaction in progress | 等待当前交易完成后重试 | 可使用相同ID重试 |
| 332 | T34 | TAPRO_RESPONSE_TIMEOUT | TaPro响应超时 | TaPro response timeout | Payment timeout | 查询交易状态后重试 | **特殊处理**：先查询状态，确认后可使用相同ID重试 |
| 333 | T38 | HOST_TRANSACTION_REJECTED | Host交易拒绝 | Host transaction rejected | Transaction rejected | 交易被Host拒绝，检查交易参数 | **必须使用新ID** |
| 334 | T39 | HOST_TRANSACTION_PROCESSING | Host交易处理中 | Host transaction processing | Transaction in progress | Host正在处理交易，请查询交易状态 | **必须使用新ID** |
| 335 | T29 | TRANSACTION_TERMINATED | 交易已终止 | Transaction terminated | Transaction terminated | 交易已终止，无需重试 | 无需重试 |
| 336 | T40 | TERMINATION_FAILED | 终止失败 | Termination failed | Termination failed | 交易终止操作失败，检查交易状态 | 可使用相同ID重试 |
| 337 | T1001 | TRANSACTION_FAILED | 交易失败 | Payment transaction failed | Transaction unsuccessful | 重试交易 | **必须使用新ID** |
| 338 | T1002 | TRANSACTION_CANCELLED | 交易已取消 | Transaction cancelled by user | Payment cancelled | 开始新交易 | 无需重试 |
| 339 | - | (预留) | (预留) | (预留) | (预留) | (预留) | (预留) |
| 340 | - | (预留) | (预留) | (预留) | (预留) | (预留) | (预留) |
| 341 | - | (预留) | (预留) | (预留) | (预留) | (预留) | (预留) |
| 342 | - | (预留) | (预留) | (预留) | (预留) | (预留) | (预留) |
| 343 | - | (预留) | (预留) | (预留) | (预留) | (预留) | (预留) |
| 344 | - | (预留) | (预留) | (预留) | (预留) | (预留) | (预留) |
| 345 | - | (预留) | (预留) | (预留) | (预留) | (预留) | (预留) |
| 346 | - | (预留) | (预留) | (预留) | (预留) | (预留) | (预留) |
| 347 | - | (预留) | (预留) | (预留) | (预留) | (预留) | (预留) |
| 348 | - | (预留) | (预留) | (预留) | (预留) | (预留) | (预留) |
| 349 | - | (预留) | (预留) | (预留) | (预留) | (预留) | (预留) |
| 350 | - | (预留) | (预留) | (预留) | (预留) | (预留) | (预留) |

## 错误码分类映射表

### 原错误码（字母+数字）到新错误码（三位数字）映射

#### 集成指南T系列错误码映射（按新错误码排序）

| 原错误码 | 新错误码 | 错误名称 | 分类 | 重试策略 |
|----------|----------|----------|------|----------|
| 000 | 000 | SUCCESS | 成功 | 无需重试 |
| T01 | 201 | SDK_NOT_INITIALIZED | 通用系统错误 | 修复后可重试 |
| T14 | 202 | VSP_CLIENT_NOT_READY | 通用系统错误 | 修复后可重试 |
| T20 | 203 | NO_SERVICE_KERNEL | 通用系统错误 | 修复后可重试 |
| T25 | 204 | TRANSACTION_INTERFACE_NULL | 通用系统错误 | 修复后可重试 |
| T31 | 209 | INIT_FAILED_SYSTEM_BUSY | 通用系统错误 | 可使用相同ID重试 |
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
| T15 | 311 | MISSING_REQUEST_PARAMETERS | 参数验证错误 | 修复后可使用相同ID重试 |
| T16 | 312 | INVALID_JSON_FORMAT | 参数验证错误 | 修复后可使用相同ID重试 |
| T17 | 313 | PROTOCOL_DATA_INVALID | 参数验证错误 | 修复后可使用相同ID重试 |
| T18 | 314 | INVALID_AMOUNT_PARAMETER | 参数验证错误 | 修复后可使用相同ID重试 |
| T19 | 315 | PROTOCOL_TYPE_MISMATCH | 参数验证错误 | 修复后可使用相同ID重试 |
| T28 | 316 | UNSUPPORTED_ACTION_TYPE | 参数验证错误 | 修复后可使用相同ID重试 |
| T21 | 321 | DATA_SEND_FAILED | 数据传输错误 | 可使用相同ID重试 |
| T23 | 321 | DATA_SEND_FAILED | 数据传输错误 | 可使用相同ID重试 |
| T22 | 322 | CANNOT_SEND_DATA_NOT_CONNECTED | 数据传输错误 | 可使用相同ID重试 |
| T24 | 323 | DATA_SEND_UNEXPECTED_ERROR | 数据传输错误 | 可使用相同ID重试 |
| T27 | 331 | TRANSACTION_LOCKED | 交易业务逻辑错误 | 可使用相同ID重试 |
| T30 | 331 | TRANSACTION_LOCKED | 交易业务逻辑错误 | 可使用相同ID重试 |
| T34 | 332 | TAPRO_RESPONSE_TIMEOUT | 交易业务逻辑错误 | **特殊处理**：先查询状态，确认后可使用相同ID重试 |
| T38 | 333 | HOST_TRANSACTION_REJECTED | 交易业务逻辑错误 | **必须使用新ID** |
| T39 | 334 | HOST_TRANSACTION_PROCESSING | 交易业务逻辑错误 | **必须使用新ID** |
| T29 | 335 | TRANSACTION_TERMINATED | 交易业务逻辑错误 | 无需重试 |
| T40 | 336 | TERMINATION_FAILED | 交易业务逻辑错误 | 可使用相同ID重试 |

#### 半集成模式错误码映射（G/A/L/C/T系列，按新错误码排序）

| 原错误码 | 新错误码 | 错误名称 | 分类 |
|----------|----------|----------|------|
| G0001 | 201 | SDK_NOT_INITIALIZED | 通用系统错误 |
| C1002 | 202 | VSP_CLIENT_NOT_READY | 通用系统错误 |
| G0002 | 205 | INVALID_CONFIGURATION | 通用系统错误 |
| G0003 | 206 | INSUFFICIENT_PERMISSIONS | 通用系统错误 |
| G0004 | 207 | UNSUPPORTED_OPERATION | 通用系统错误 |
| G1001 | 211 | CONNECTION_ALREADY_EXISTS | 通用连接管理错误 |
| G1002 | 212 | NO_CONNECTION_AVAILABLE | 通用连接管理错误 |
| G1005 | 215 | CONNECTION_FAILED | 通用连接管理错误 |
| G1003 | 215 | CONNECTION_FAILED | 通用连接管理错误 |
| G1004 | 215 | CONNECTION_FAILED | 通用连接管理错误 |
| A1001 | 231 | TAPRO_APP_NOT_FOUND | APP_TO_APP连接错误 |
| A1003 | 233 | INTENT_FAILED | APP_TO_APP连接错误 |
| A1002 | 234 | TAPRO_APP_OUTDATED | APP_TO_APP连接错误 |
| A1004 | 235 | TAPRO_APP_BUSY | APP_TO_APP连接错误 |
| A1005 | 236 | PACKAGE_VERIFICATION_FAILED | APP_TO_APP连接错误 |
| A3001 | 241 | INVALID_INTENT_DATA | APP_TO_APP协议错误 |
| A3002 | 242 | INTENT_TIMEOUT | APP_TO_APP协议错误 |
| A3003 | 243 | RESPONSE_FORMAT_ERROR | APP_TO_APP协议错误 |
| A3004 | 244 | PROTOCOL_VERSION_MISMATCH | APP_TO_APP协议错误 |
| A3005 | 245 | DATA_SERIALIZATION_ERROR | APP_TO_APP协议错误 |
| L1001 | 252 | DEVICE_NOT_FOUND | LAN连接错误 |
| L1002 | 253 | CONNECTION_REFUSED | LAN连接错误 |
| L1003 | 254 | NETWORK_UNREACHABLE | LAN连接错误 |
| L1004 | 255 | INVALID_IP_ADDRESS | LAN连接错误 |
| L1005 | 256 | PORT_UNAVAILABLE | LAN连接错误 |
| L5001 | 261 | WIFI_DISCONNECTED | LAN网络错误 |
| L5002 | 262 | NETWORK_TIMEOUT | LAN网络错误 |
| L5003 | 263 | DNS_RESOLUTION_FAILED | LAN网络错误 |
| L5004 | 264 | SSL_HANDSHAKE_FAILED | LAN网络错误 |
| L5005 | 265 | WEBSOCKET_ERROR | LAN网络错误 |
| L6001 | 272 | MISSING_HOST_CONFIG | LAN配置错误 |
| L6002 | 273 | MISSING_PORT_CONFIG | LAN配置错误 |
| L6003 | 274 | INVALID_HOST_FORMAT | LAN配置错误 |
| L6004 | 275 | INVALID_PORT_RANGE | LAN配置错误 |
| L6005 | 275 | MDNS_DISCOVERY_FAILED | LAN配置错误 |
| C1001 | 281 | CABLE_NOT_CONNECTED | CABLE连接错误 |
| C1003 | 283 | USB_PERMISSION_DENIED | CABLE连接错误 |
| C1004 | 284 | INCOMPATIBLE_DEVICE | CABLE连接错误 |
| C1005 | 285 | CABLE_DISCONNECTED | CABLE连接错误 |
| C3001 | 291 | PROTOCOL_DETECTION_FAILED | CABLE协议错误 |
| C3002 | 292 | AOA_INITIALIZATION_FAILED | CABLE协议错误 |
| C3003 | 293 | VSP_COMMUNICATION_ERROR | CABLE协议错误 |
| C3004 | 294 | RS232_PORT_ERROR | CABLE协议错误 |
| C3005 | 295 | PROTOCOL_SWITCH_FAILED | CABLE协议错误 |
| C4001 | 301 | DEVICE_BUSY | CABLE设备错误 |
| C4002 | 302 | DEVICE_ERROR | CABLE设备错误 |
| C4003 | 303 | FIRMWARE_INCOMPATIBLE | CABLE设备错误 |
| C4004 | 304 | DEVICE_LOCKED | CABLE设备错误 |
| C4005 | 305 | POWER_INSUFFICIENT | CABLE设备错误 |
| C7001 | 306 | USB_PERMISSION_REQUIRED | CABLE权限错误 |
| C7002 | 307 | SERIAL_PORT_PERMISSION | CABLE权限错误 |
| C7003 | 308 | DEVICE_ACCESS_DENIED | CABLE权限错误 |
| C7004 | 309 | DRIVER_PERMISSION_ERROR | CABLE权限错误 |
| C7005 | 310 | SYSTEM_PERMISSION_DENIED | CABLE权限错误 |
| T1003 | 332 | TRANSACTION_TIMEOUT | 交易业务逻辑错误 |
| T1004 | 332 | INVALID_AMOUNT | 交易业务逻辑错误 |
| T1005 | 333 | DUPLICATE_TRANSACTION | 交易业务逻辑错误 |
| T1001 | 337 | TRANSACTION_FAILED | 交易业务逻辑错误 |
| T1002 | 338 | TRANSACTION_CANCELLED | 交易业务逻辑错误 |

## 错误码分类枚举

```kotlin
enum class SemiIntegrationErrorCategory(val range: IntRange, val description: String) {
    // 通用错误
    GENERAL_SYSTEM(201..209, "通用系统错误"),
    GENERAL_CONNECTION(211..220, "通用连接管理错误"),
    GENERAL_AUTH(221..221, "通用认证错误"),
    
    // APP_TO_APP模式错误
    APP_TO_APP_CONNECTION(231..240, "APP_TO_APP连接错误"),
    APP_TO_APP_PROTOCOL(241..250, "APP_TO_APP协议错误"),
    
    // LAN模式错误
    LAN_CONNECTION(251..260, "LAN连接错误"),
    LAN_NETWORK(261..270, "LAN网络错误"),
    LAN_CONFIG(271..280, "LAN配置错误"),
    
    // CABLE模式错误
    CABLE_CONNECTION(281..290, "CABLE连接错误"),
    CABLE_PROTOCOL(291..300, "CABLE协议错误"),
    CABLE_DEVICE_PERMISSION(301..310, "CABLE设备和权限错误"),
    
    // 交易错误
    PARAMETER_VALIDATION(311..320, "参数验证错误"),
    DATA_TRANSMISSION(321..330, "数据传输错误"),
    TRANSACTION_PROCESSING(331..350, "交易业务逻辑错误"),
    
    UNKNOWN(-1..-1, "未知错误");
    
    companion object {
        fun fromCode(code: Int): SemiIntegrationErrorCategory {
            return values().find { code in it.range } ?: UNKNOWN
        }
        
        fun getConnectionMode(code: Int): ConnectionMode? {
            return when (code) {
                in 231..250 -> ConnectionMode.APP_TO_APP
                in 251..280 -> ConnectionMode.LAN
                in 281..310 -> ConnectionMode.CABLE
                else -> null
            }
        }
    }
}
```

## 错误处理代码示例

### 1. 错误码判断和分类

```kotlin
/**
 * 判断错误码所属的分类
 */
fun getErrorCategory(code: Int): SemiIntegrationErrorCategory {
    return SemiIntegrationErrorCategory.fromCode(code)
}

/**
 * 判断是否可以使用相同的 transactionRequestId 重试
 * 
 * 根据集成指南：
 * - T34（332）：先查询交易状态，确认后可使用相同ID重试
 * - T38（333）、T39（334）：必须使用新ID
 * - 其他所有错误码：可使用相同ID重试
 */
fun canRetryWithSameId(code: Int): Boolean {
    // 特殊错误码：T38（333）、T39（334）必须使用新ID
    return code !in listOf(333, 334)
}

/**
 * 判断是否需要先查询状态
 * 
 * 根据集成指南：T34（332）需要先查询交易状态
 */
fun shouldQueryStatusFirst(code: Int): Boolean {
    return code == 332  // T34: TAPRO_RESPONSE_TIMEOUT
}
```

### 2. 错误处理策略

```kotlin
fun handleSemiIntegrationError(error: SemiIntegrationError) {
    val errorCode = error.code.toIntOrNull() ?: return
    
    when (errorCode) {
        // 通用系统错误 (201-209)
        in 201..209 -> {
            handleGeneralSystemError(error)
        }
        
        // 通用连接管理错误 (211-220)
        in 211..220 -> {
            handleGeneralConnectionError(error)
        }
        
        // 通用认证错误 (221)
        221 -> {
            handleGeneralAuthError(error)
        }
        
        // APP_TO_APP模式错误 (231-250)
        in 231..250 -> {
            handleAppToAppError(error)
        }
        
        // LAN模式错误 (251-280)
        in 251..280 -> {
            handleLanError(error)
        }
        
        // CABLE模式错误 (281-310)
        in 281..310 -> {
            handleCableError(error)
        }
        
        // 参数验证错误 (311-320)
        in 311..320 -> {
            handleParameterValidationError(error)
        }
        
        // 数据传输错误 (321-330)
        in 321..330 -> {
            handleDataTransmissionError(error)
        }
        
        // 交易业务逻辑错误 (331-350)
        332 -> {
            // T34: Tapro响应超时 - 先查询交易状态，确认后可使用相同ID重试
            queryTransactionStatus()
        }
        333 -> {
            // T38: Host交易拒绝 - 必须使用新ID
            showError("交易被拒绝，请使用新的交易请求ID重试")
        }
        334 -> {
            // T39: Host交易处理中 - 必须使用新ID
            showError("交易处理中，请查询状态或使用新ID重试")
        }
        in 331..350 -> {
            handleTransactionError(error)
        }
        
        // 参数验证错误 (331-340)
        in 331..340 -> {
            handleParameterValidationError(error)
        }
        
        // 数据传输错误 (341-350)
        in 341..350 -> {
            handleDataTransmissionError(error)
        }
        
        else -> {
            showError("未知错误：${error.message}")
        }
    }
}
```

### 3. 错误码映射工具

```kotlin
class SemiIntegrationErrorCodeMapper {
    private val legacyToNewMapping = mapOf(
        // 按新错误码从小到大排序
        // 000: 成功
        "000" to 0,
        // 201-209: 通用系统错误
        "T01" to 201, "G0001" to 201,
        "T14" to 202, "C1002" to 202,
        "T20" to 203,
        "T25" to 204,
        "G0002" to 205,
        "G0003" to 206,
        "G0004" to 207,
        "T31" to 209,
        // 211-220: 通用连接管理错误
        "T09" to 211, "G1001" to 211,
        "T07" to 212, "T37" to 212, "G1002" to 212,
        "T06" to 213,
        "T08" to 214,
        "G1005" to 215, "G1003" to 215, "G1004" to 215,
        // 221: 通用认证错误
        "T41" to 221, "G2001" to 221,
        // 231-250: APP_TO_APP模式错误
        "T02" to 231, "A1001" to 231,
        "T03" to 232, "A1003" to 232,
        "A1003" to 233,
        "A1002" to 234,
        "A1004" to 235,
        "A1005" to 236,
        "A3001" to 241,
        "A3002" to 242,
        "A3003" to 243,
        "A3004" to 244,
        "A3005" to 245,
        // 251-280: LAN模式错误
        "T04" to 251, "L1001" to 251,
        "L1001" to 252,
        "L1002" to 253,
        "L1003" to 254,
        "L1004" to 255,
        "L1005" to 256,
        "L5001" to 261,
        "L5002" to 262,
        "L5003" to 263,
        "L5004" to 264,
        "L5005" to 265,
        "T05" to 271, "L6005" to 271,
        "L6001" to 272,
        "L6002" to 273,
        "L6003" to 274,
        "L6004" to 275, "L6005" to 275,
        // 281-310: CABLE模式错误
        "T35" to 281, "C1001" to 281,
        "T11" to 283, "C1003" to 283,
        "C1004" to 284,
        "C1005" to 285,
        "T12" to 286,
        "T10" to 291, "C3001" to 291,
        "C3002" to 292,
        "C3003" to 293,
        "T13" to 294, "C3004" to 294,
        "C3005" to 295,
        "C4001" to 301,
        "C4002" to 302,
        "C4003" to 303,
        "C4004" to 304,
        "C4005" to 305,
        "C7001" to 306,
        "C7002" to 307,
        "C7003" to 308,
        "C7004" to 309,
        "C7005" to 310,
        // 311-320: 参数验证错误
        "T15" to 311,
        "T16" to 312,
        "T17" to 313,
        "T18" to 314,
        "T19" to 315,
        "T28" to 316,
        // 321-330: 数据传输错误
        "T21" to 321,
        "T22" to 322,
        "T23" to 321,
        "T24" to 323,
        // 331-350: 交易业务逻辑错误
        "T27" to 331,
        "T30" to 331,
        "T34" to 332, "T1003" to 332, "T1004" to 332,
        "T38" to 333, "T1005" to 333,
        "T39" to 334,
        "T29" to 335,
        "T40" to 336,
        "T1001" to 337,
        "T1002" to 338
    )
    
    /**
     * 将原错误码（字母+数字）映射到新错误码（三位数字）
     */
    fun mapLegacyCode(legacyCode: String): Int? {
        return legacyToNewMapping[legacyCode]
    }
    
    /**
     * 获取错误码的连接模式
     */
    fun getConnectionMode(code: Int): ConnectionMode? {
        return SemiIntegrationErrorCategory.getConnectionMode(code)
    }
}
```

## 优势与特点

### 1. 统一的数字格式
- 所有错误码采用三位数字格式，便于程序处理
- 数字范围清晰表示错误分类
- 便于扩展和维护

### 2. 清晰的分类体系
- 按错误类型和连接模式分类
- 每个范围代表特定的错误类型
- 便于快速定位问题

### 3. 向后兼容
- 提供原错误码到新错误码的完整映射
- 支持渐进式迁移
- 保持现有逻辑不变

### 4. 便于维护
- 预留了扩展空间
- 结构化的错误定义
- 便于自动化处理和分析

## 与集成指南的对应关系

本文档整合了《Sunbay Taplink SDK for Android - 集成指南》中定义的所有T系列错误码（T01-T41），并按照半集成模式的三位数字格式进行了重新分类和映射。

### 集成指南T系列错误码完整映射（按新错误码排序）

| 集成指南错误码 | 三位数字错误码 | 错误名称 | 分类 | 重试策略说明 |
|---------------|---------------|----------|------|-------------|
| 000 | 000 | SUCCESS | 成功 | 无需重试 |
| T01 | 201 | SDK_NOT_INITIALIZED | 通用系统错误 | 修复后可重试 |
| T14 | 202 | VSP_CLIENT_NOT_READY | 通用系统错误 | 修复后可重试 |
| T20 | 203 | NO_SERVICE_KERNEL | 通用系统错误 | 修复后可重试 |
| T25 | 204 | TRANSACTION_INTERFACE_NULL | 通用系统错误 | 修复后可重试 |
| T31 | 209 | INIT_FAILED_SYSTEM_BUSY | 通用系统错误 | 可使用相同ID重试 |
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
| T15 | 311 | MISSING_REQUEST_PARAMETERS | 参数验证错误 | 修复后可使用相同ID重试 |
| T16 | 312 | INVALID_JSON_FORMAT | 参数验证错误 | 修复后可使用相同ID重试 |
| T17 | 313 | PROTOCOL_DATA_INVALID | 参数验证错误 | 修复后可使用相同ID重试 |
| T18 | 314 | INVALID_AMOUNT_PARAMETER | 参数验证错误 | 修复后可使用相同ID重试 |
| T19 | 315 | PROTOCOL_TYPE_MISMATCH | 参数验证错误 | 修复后可使用相同ID重试 |
| T28 | 316 | UNSUPPORTED_ACTION_TYPE | 参数验证错误 | 修复后可使用相同ID重试 |
| T21 | 321 | DATA_SEND_FAILED | 数据传输错误 | 可使用相同ID重试 |
| T23 | 321 | DATA_SEND_FAILED | 数据传输错误 | 可使用相同ID重试 |
| T22 | 322 | CANNOT_SEND_DATA_NOT_CONNECTED | 数据传输错误 | 可使用相同ID重试 |
| T24 | 323 | DATA_SEND_UNEXPECTED_ERROR | 数据传输错误 | 可使用相同ID重试 |
| T27 | 331 | TRANSACTION_LOCKED | 交易业务逻辑错误 | 可使用相同ID重试 |
| T30 | 331 | TRANSACTION_LOCKED | 交易业务逻辑错误 | 可使用相同ID重试 |
| T34 | 332 | TAPRO_RESPONSE_TIMEOUT | 交易业务逻辑错误 | **特殊处理**：先查询状态，确认后可使用相同ID重试 |
| T38 | 333 | HOST_TRANSACTION_REJECTED | 交易业务逻辑错误 | **必须使用新ID** |
| T39 | 334 | HOST_TRANSACTION_PROCESSING | 交易业务逻辑错误 | **必须使用新ID** |
| T29 | 335 | TRANSACTION_TERMINATED | 交易业务逻辑错误 | 无需重试 |
| T40 | 336 | TERMINATION_FAILED | 交易业务逻辑错误 | 可使用相同ID重试 |

### 重试策略说明

根据集成指南的幂等性机制：

1. **必须使用新ID的错误码**：
   - **315 (T38)**: Host交易拒绝
   - **316 (T39)**: Host交易处理中

2. **特殊处理：先查询状态**：
   - **314 (T34)**: Tapro响应超时 - 先查询交易状态，确认结果后再使用**相同** `transactionRequestId` 重试

3. **可使用相同ID重试**：
   - 除315、316外的所有其他错误码都可以使用**相同的** `transactionRequestId` 重试

## 错误码类型归类

虽然错误码按连接模式和功能进行了分类，但某些错误码在语义上可以归类为同一类型，便于统一理解和处理。

### 1. 超时错误（6个）

所有超时相关的错误码，统一使用 `*_TIMEOUT` 命名规范：

| 错误码 | 原错误码 | 错误名称 | 分类 | 说明 |
|--------|----------|----------|------|------|
| 242 | A3002 | INTENT_TIMEOUT | APP_TO_APP协议错误 | Intent通信超时 |
| 262 | L5002 | NETWORK_TIMEOUT | LAN网络错误 | 网络操作超时 |
| 286 | T12 | USB_DEVICE_CONNECTION_TIMEOUT | CABLE连接错误 | USB设备连接超时 |
| 300 | - | PROTOCOL_TIMEOUT | CABLE协议错误 | 协议操作超时 |
| 314 | T34 | TAPRO_RESPONSE_TIMEOUT | 交易处理错误 | TaPro响应超时（需特殊处理） |
| 350 | - | DATA_TIMEOUT | 数据传输错误 | 数据传输超时 |

**通用处理方式**：增加超时时间或重试（T34需要先查询状态）

### 2. 权限错误（8个）

所有权限相关的错误码，统一使用 `*_PERMISSION_*` 或 `*_ACCESS_DENIED` 命名规范：

| 错误码 | 原错误码 | 错误名称 | 分类 | 说明 |
|--------|----------|----------|------|------|
| 206 | G0003 | INSUFFICIENT_PERMISSIONS | 通用系统错误 | 应用权限不足 |
| 237 | - | INTENT_PERMISSION_DENIED | APP_TO_APP连接错误 | Intent权限被拒绝 |
| 283 | T11, C1003 | USB_PERMISSION_DENIED | CABLE连接错误 | USB权限被拒绝 |
| 306 | C7001 | USB_PERMISSION_REQUIRED | CABLE权限错误 | USB权限必需 |
| 307 | C7002 | SERIAL_PORT_PERMISSION | CABLE权限错误 | 串口权限 |
| 308 | C7003 | DEVICE_ACCESS_DENIED | CABLE权限错误 | 设备访问被拒绝 |
| 309 | C7004 | DRIVER_PERMISSION_ERROR | CABLE权限错误 | 驱动权限错误 |
| 310 | C7005 | SYSTEM_PERMISSION_DENIED | CABLE权限错误 | 系统权限被拒绝 |

**通用处理方式**：申请/授予相应权限后重试

### 3. 初始化错误（6个）

所有初始化相关的错误码，统一使用 `*_NOT_READY`、`*_NOT_INITIALIZED`、`*_INITIALIZATION_FAILED` 命名规范：

| 错误码 | 原错误码 | 错误名称 | 分类 | 说明 |
|--------|----------|----------|------|------|
| 201 | T01, G0001 | SDK_NOT_INITIALIZED | 通用系统错误 | SDK未初始化 |
| 202 | T14, G0002, C1002 | VSP_CLIENT_NOT_READY | 通用系统错误 | VSP客户端未就绪（设备未检测到） |
| 203 | T20, G0002 | NO_SERVICE_KERNEL | 通用系统错误 | 无可用服务内核 |
| 204 | T25, G0002 | TRANSACTION_INTERFACE_NULL | 通用系统错误 | 交易接口为空 |
| 210 | T31 | INIT_FAILED_SYSTEM_BUSY | 通用系统错误 | 初始化失败：系统繁忙 |
| 292 | C3002 | AOA_INITIALIZATION_FAILED | CABLE协议错误 | AOA初始化失败 |

**通用处理方式**：重新初始化或等待系统空闲后重试

### 4. 连接断开错误（5个）

所有连接断开相关的错误码，统一使用 `*_DISCONNECTED`、`CONNECTION_INTERRUPTED` 命名规范：

| 错误码 | 原错误码 | 错误名称 | 分类 | 说明 |
|--------|----------|----------|------|------|
| 213 | T06, G1005 | CONNECTION_DISCONNECTED | 通用连接管理错误 | 连接已断开 |
| 219 | - | CONNECTION_INTERRUPTED | 通用连接管理错误 | 连接中断 |
| 257 | - | DEVICE_DISCONNECTED | LAN连接错误 | 设备断开连接 |
| 261 | L5001 | WIFI_DISCONNECTED | LAN网络错误 | WiFi断开 |
| 285 | C1005 | CABLE_DISCONNECTED | CABLE连接错误 | 线缆断开 |

**通用处理方式**：检查连接状态，重新建立连接

### 5. 连接失败错误（1个）

所有连接建立失败相关的错误码，统一使用 `CONNECTION_FAILED` 命名规范：

| 错误码 | 原错误码 | 错误名称 | 分类 | 说明 |
|--------|----------|----------|------|------|
| 215 | G1005, G1003, G1004 | CONNECTION_FAILED | 通用连接管理错误 | 连接失败 |

**通用处理方式**：检查连接状态和配置，重新建立连接或修复连接问题

**注意**：
- 原错误码 G1003（连接数超限）、G1004（连接被拒绝）已合并到此错误码
- 219 (CONNECTION_INTERRUPTED) 属于"连接断开错误"，因为它表示已建立的连接被中断

#### 连接失败错误码详细说明

##### 215 - CONNECTION_FAILED（连接失败）

**触发场景**（包含原216/G1003、217/G1004、218、220的所有场景）：
- 连接丢失：连接在操作过程中丢失（原215/G1005）
- 连接数超限：设备端已达到最大连接数限制（原216/G1003）
- 连接被拒绝：设备端主动拒绝连接请求（原217/G1004）
- 连接超时：连接建立超时（原218）
- 连接状态无效：SDK内部连接状态不一致（原220）

**典型场景示例**：
```kotlin
// 场景1：连接失败（所有原因）
try {
    taplinkSDK.connect(deviceInfo)
} catch (e: TaplinkException) {
    if (e.errorCode == 215) {
        // 错误码 215: 连接失败
        // 可能原因：连接丢失、连接数超限、连接被拒绝、连接超时、状态无效
        // 解决方案：检查连接状态和配置，重新建立连接
    }
}
```

**处理建议**：
1. **检查连接状态**：确认当前连接状态，清理无效连接
2. **检查设备状态**：确认设备是否正常运行、是否忙碌
3. **验证配置**：检查连接参数（IP地址、端口、appId、secretKey等）
4. **检查网络/线缆**：LAN模式检查网络，CABLE模式检查线缆
5. **重试策略**：可使用相同ID重试，建议根据具体情况调整重试延迟

**错误处理代码示例**：
```kotlin
fun handleConnectionFailed(
    error: TaplinkException,
    deviceInfo: DeviceInfo,
    retryConfig: RetryConfig = RetryConfig.default()
): Result<Connection> {
    LogUtil.w(TAG, "Connection failed: ${error.message}")
    
    // 1. 检查并清理现有连接
    val activeConnections = taplinkSDK.getActiveConnections()
    if (activeConnections.isNotEmpty()) {
        LogUtil.d(TAG, "Found ${activeConnections.size} active connections, closing...")
        activeConnections.forEach { conn ->
            if (conn.deviceId != targetDeviceId) {
                conn.disconnect()
            }
        }
    }
    
    // 2. 检查设备状态
    val deviceStatus = checkDeviceStatus(deviceInfo)
    if (!deviceStatus.isAvailable) {
        LogUtil.w(TAG, "Device not available: ${deviceStatus.reason}")
        return Result.failure(Exception("Device not available: ${deviceStatus.reason}"))
    }
    
    // 3. 检查网络/线缆（根据连接模式）
    when (deviceInfo.connectionMode) {
        ConnectionMode.LAN -> {
            val networkStatus = checkNetworkStatus()
            if (!networkStatus.isConnected) {
                return Result.failure(Exception("Network not connected"))
            }
        }
        ConnectionMode.CABLE -> {
            // 检查USB线缆连接
        }
        else -> {}
    }
    
    // 4. 重置连接状态（如果需要）
    try {
        taplinkSDK.resetConnectionState()
    } catch (e: Exception) {
        LogUtil.w(TAG, "Failed to reset connection state: ${e.message}")
    }
    
    // 5. 等待后重试
    delay(retryConfig.retryDelay)
    
    // 6. 重试连接
    try {
        val connection = taplinkSDK.connect(deviceInfo)
        LogUtil.d(TAG, "Connection successful after retry")
        return Result.success(connection)
    } catch (e: TaplinkException) {
        return Result.failure(e)
    }
}
```

---

#### 连接失败错误码统一处理示例

```kotlin
/**
 * 统一的连接失败错误处理
 */
fun handleConnectionFailureError(
    error: TaplinkException,
    deviceInfo: DeviceInfo,
    retryConfig: RetryConfig = RetryConfig.default()
): Result<Connection> {
    return when (error.errorCode) {
        215 -> {
            // CONNECTION_FAILED: 统一处理所有连接失败情况
            handleConnectionFailed(error, deviceInfo, retryConfig)
        }
        
        else -> {
            Result.failure(error)
        }
    }
}

/**
 * 重试配置
 */
data class RetryConfig(
    val maxRetries: Int = 3,
    val initialTimeout: Long = 30000L,
    val retryDelay: Long = 1000L,
    val useExponentialBackoff: Boolean = true
) {
    companion object {
        fun default() = RetryConfig()
    }
}

/**
 * 连接失败错误码判断工具
 */
object ConnectionFailureErrorHelper {
    /**
     * 判断是否为连接失败错误码
     */
    fun isConnectionFailureError(code: Int): Boolean {
        return code == 215
    }
    
    /**
     * 判断是否可以使用相同ID重试
     */
    fun canRetryWithSameId(code: Int): Boolean {
        // 连接失败错误码可以使用相同ID重试
        return isConnectionFailureError(code)
    }
    
    /**
     * 获取错误码的建议重试延迟（毫秒）
     */
    fun getSuggestedRetryDelay(code: Int, attempt: Int): Long {
        return when (code) {
            215 -> 1000L * (1 shl attempt.coerceAtMost(3))  // 连接失败：指数退避
            else -> 1000L
        }
    }
}
```

### 6. 设备错误（5个）

所有设备相关的错误码，统一使用 `DEVICE_*` 或 `USB_DEVICE_*` 命名规范：

| 错误码 | 原错误码 | 错误名称 | 分类 | 说明 |
|--------|----------|----------|------|------|
| 287 | - | USB_DEVICE_BUSY | CABLE连接错误 | USB设备忙碌 |
| 288 | - | USB_DEVICE_ERROR | CABLE连接错误 | USB设备错误 |
| 301 | C4001 | DEVICE_BUSY | CABLE设备错误 | 设备忙碌 |
| 302 | C4002 | DEVICE_ERROR | CABLE设备错误 | 设备错误 |
| 304 | C4004 | DEVICE_LOCKED | CABLE设备错误 | 设备锁定 |

**通用处理方式**：检查设备状态，等待设备空闲或修复设备问题

### 6. 协议错误（11个）

所有协议相关的错误码，统一使用 `PROTOCOL_*`、`*_PROTOCOL_*`、`*_VERSION_MISMATCH` 命名规范：

| 错误码 | 原错误码 | 错误名称 | 分类 | 说明 |
|--------|----------|----------|------|------|
| 241 | A3001 | INVALID_INTENT_DATA | APP_TO_APP协议错误 | Intent数据无效 |
| 243 | A3003 | RESPONSE_FORMAT_ERROR | APP_TO_APP协议错误 | 响应格式错误 |
| 244 | A3004 | PROTOCOL_VERSION_MISMATCH | APP_TO_APP协议错误 | 协议版本不匹配 |
| 245 | A3005 | DATA_SERIALIZATION_ERROR | APP_TO_APP协议错误 | 数据序列化错误 |
| 291 | T10, C3001 | INVALID_USB_PROTOCOL | CABLE协议错误 | 无效的USB协议 |
| 293 | C3003 | VSP_COMMUNICATION_ERROR | CABLE协议错误 | VSP通信错误 |
| 295 | C3005 | PROTOCOL_SWITCH_FAILED | CABLE协议错误 | 协议切换失败 |
| 296 | C3001 | PROTOCOL_DETECTION_FAILED | CABLE协议错误 | 协议检测失败 |
| 297 | - | PROTOCOL_NEGOTIATION_FAILED | CABLE协议错误 | 协议协商失败 |
| 298 | - | AOA_PROTOCOL_ERROR | CABLE协议错误 | AOA协议错误 |
| 335 | T19 | PROTOCOL_TYPE_MISMATCH | 参数验证错误 | 协议类型不匹配 |

**通用处理方式**：检查协议配置，更新协议版本或修复协议数据格式

## 总结

半集成模式的三位数字错误码体系具有以下特点：

1. **结构清晰**：按连接模式和错误类型分类，便于快速定位问题
2. **易于扩展**：每个范围预留了扩展空间
3. **便于维护**：统一的格式和命名规范
4. **向后兼容**：提供完整的映射关系，包括集成指南中的所有T系列错误码
5. **便于自动化**：数字格式便于程序处理和分析
6. **与集成指南一致**：完全整合了集成指南中的错误码定义和重试策略

这套错误码体系将为 Taplink SDK 半集成模式提供更好的错误处理和问题诊断能力，并与集成指南保持完全一致。

---

**版本**: 1.0.0  
**更新日期**: 2025-01-12  
**作者**: TaPro Team
