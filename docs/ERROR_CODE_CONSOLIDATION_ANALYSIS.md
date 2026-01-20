# 错误码合并与归类分析报告

## 概述

本文档分析了半集成模式错误码，识别出可以合并或归类为同一类型的错误码，以提高错误码体系的一致性和可维护性。

## 一、可以合并的错误码

### 1. 数据发送失败错误（已识别）

**问题**：T21和T23都表示"发送数据失败"
- 341 (T21): `DATA_SEND_FAILED` (发送数据失败)
- 343 (T23): `DATA_TRANSMISSION_FAILED` (发送数据失败)

**建议**：合并为一个错误码
- **合并为**：341 `DATA_SEND_FAILED` (数据发送失败)
- **理由**：两个错误码含义相同，都是表示数据发送失败

### 2. 连接断开相关错误（可考虑合并）

**问题**：多个错误码都表示连接断开，但场景略有不同
- 213: `CONNECTION_DISCONNECTED` (连接已断开) - 通用连接断开
- 215: `CONNECTION_LOST` (连接丢失) - 操作过程中连接丢失
- 219: `CONNECTION_INTERRUPTED` (连接中断) - 意外中断
- 257: `DEVICE_DISCONNECTED` (设备断开连接) - LAN模式设备断开
- 285: `CABLE_DISCONNECTED` (线缆断开) - CABLE模式线缆断开
- 261: `WIFI_DISCONNECTED` (WiFi断开) - WiFi连接断开

**建议**：
- **方案1（保守）**：保留但统一命名规范
  - 213: `CONNECTION_DISCONNECTED` (连接已断开) - 通用
  - 215: `CONNECTION_LOST` (连接丢失) - 操作中丢失
  - 219: `CONNECTION_INTERRUPTED` (连接中断) - 意外中断
  - 其他按模式区分（LAN/CABLE/WiFi）
  
- **方案2（激进）**：合并为通用错误码
  - 213: `CONNECTION_DISCONNECTED` (连接已断开) - 统一使用此错误码
  - 其他场景在详细描述中说明

**推荐**：采用方案1，因为不同场景的处理方式可能不同

## 二、可以归类为同一类型的错误码

### 1. 超时错误（可创建超时错误类别）

**当前分布**：
- 218: `CONNECTION_TIMEOUT` (连接超时) - 通用连接管理错误
- 229: `AUTHENTICATION_TIMEOUT` (认证超时) - 通用认证错误
- 242: `INTENT_TIMEOUT` (Intent超时) - APP_TO_APP协议错误
- 262: `NETWORK_TIMEOUT` (网络超时) - LAN网络错误
- 286: `USB_DEVICE_CONNECTION_TIMEOUT` (USB设备连接超时) - CABLE连接错误
- 300: `PROTOCOL_TIMEOUT` (协议超时) - CABLE协议错误
- 314: `TAPRO_RESPONSE_TIMEOUT` (TaPro响应超时) - 交易处理错误
- 350: `DATA_TIMEOUT` (数据传输超时) - 数据传输错误

**建议**：
- 保持当前分类，但统一命名规范：`*_TIMEOUT`
- 在文档中说明所有超时错误的通用处理方式

### 2. 权限错误（可创建权限错误类别）

**当前分布**：
- 206: `INSUFFICIENT_PERMISSIONS` (权限不足) - 通用系统错误
- 225: `UNAUTHORIZED_ACCESS` (未授权访问) - 通用认证错误
- 237: `INTENT_PERMISSION_DENIED` (Intent权限被拒绝) - APP_TO_APP连接错误
- 283: `USB_PERMISSION_DENIED` (USB权限被拒绝) - CABLE连接错误
- 306: `USB_PERMISSION_REQUIRED` (USB权限必需) - CABLE权限错误
- 307: `SERIAL_PORT_PERMISSION` (串口权限) - CABLE权限错误
- 308: `DEVICE_ACCESS_DENIED` (设备访问被拒绝) - CABLE权限错误
- 309: `DRIVER_PERMISSION_ERROR` (驱动权限错误) - CABLE权限错误
- 310: `SYSTEM_PERMISSION_DENIED` (系统权限被拒绝) - CABLE权限错误

**建议**：
- 保持当前分类（按连接模式），但统一命名规范：`*_PERMISSION_*` 或 `*_ACCESS_DENIED`
- 在文档中说明权限错误的通用处理方式

### 3. 初始化失败错误（可创建初始化错误类别）

**当前分布**：
- 201: `SDK_NOT_INITIALIZED` (SDK未初始化) - 通用系统错误
- 202: `VSP_CLIENT_NOT_READY` (VSP客户端未就绪) - 通用系统错误
- 203: `NO_SERVICE_KERNEL` (无可用服务内核) - 通用系统错误
- 204: `TRANSACTION_INTERFACE_NULL` (交易接口为空) - 通用系统错误
- 210: `INIT_FAILED_SYSTEM_BUSY` (初始化失败：系统繁忙) - 通用系统错误
- 292: `AOA_INITIALIZATION_FAILED` (AOA初始化失败) - CABLE协议错误

**建议**：
- 保持当前分类，但统一命名规范：`*_NOT_READY`、`*_NOT_INITIALIZED`、`*_INITIALIZATION_FAILED`
- 在文档中说明初始化错误的通用处理方式

### 4. 设备错误（可创建设备错误类别）

**当前分布**：
- 282: `DEVICE_NOT_DETECTED` (设备未检测到) - CABLE连接错误
- 301: `DEVICE_BUSY` (设备忙碌) - CABLE设备错误
- 302: `DEVICE_ERROR` (设备错误) - CABLE设备错误
- 304: `DEVICE_LOCKED` (设备锁定) - CABLE设备错误
- 288: `USB_DEVICE_ERROR` (USB设备错误) - CABLE连接错误
- 287: `USB_DEVICE_BUSY` (USB设备忙碌) - CABLE连接错误

**建议**：
- 保持当前分类（CABLE模式），但统一命名规范：`DEVICE_*` 或 `USB_DEVICE_*`
- 在文档中说明设备错误的通用处理方式

### 5. 协议错误（可创建协议错误类别）

**当前分布**：
- 241: `INVALID_INTENT_DATA` (Intent数据无效) - APP_TO_APP协议错误
- 243: `RESPONSE_FORMAT_ERROR` (响应格式错误) - APP_TO_APP协议错误
- 244: `PROTOCOL_VERSION_MISMATCH` (协议版本不匹配) - APP_TO_APP协议错误
- 245: `DATA_SERIALIZATION_ERROR` (数据序列化错误) - APP_TO_APP协议错误
- 291: `INVALID_USB_PROTOCOL` (无效的USB协议) - CABLE协议错误
- 293: `VSP_COMMUNICATION_ERROR` (VSP通信错误) - CABLE协议错误
- 295: `PROTOCOL_SWITCH_FAILED` (协议切换失败) - CABLE协议错误
- 296: `PROTOCOL_DETECTION_FAILED` (协议检测失败) - CABLE协议错误
- 297: `PROTOCOL_NEGOTIATION_FAILED` (协议协商失败) - CABLE协议错误
- 298: `AOA_PROTOCOL_ERROR` (AOA协议错误) - CABLE协议错误
- 335: `PROTOCOL_TYPE_MISMATCH` (协议类型不匹配) - 参数验证错误

**建议**：
- 保持当前分类（按连接模式），但统一命名规范：`PROTOCOL_*`、`*_PROTOCOL_*`
- 在文档中说明协议错误的通用处理方式

## 三、具体合并建议

### 优先级1（必须合并）

1. **T21和T23**：合并数据发送失败错误
   - 341 (T21): `DATA_SEND_FAILED` (数据发送失败)
   - 343 (T23): 合并到341

### 优先级2（建议合并）

2. **连接断开错误**：统一命名规范
   - 213: `CONNECTION_DISCONNECTED` (连接已断开) - 通用
   - 215: `CONNECTION_LOST` (连接丢失) - 操作中丢失
   - 219: `CONNECTION_INTERRUPTED` (连接中断) - 意外中断
   - 保持其他按模式区分的错误码

### 优先级3（可选优化）

3. **权限错误**：统一命名规范
   - 统一使用 `*_PERMISSION_*` 或 `*_ACCESS_DENIED` 命名

4. **超时错误**：统一命名规范
   - 统一使用 `*_TIMEOUT` 命名

5. **初始化错误**：统一命名规范
   - 统一使用 `*_NOT_READY`、`*_NOT_INITIALIZED`、`*_INITIALIZATION_FAILED` 命名

## 四、推荐实施方案

### 方案1：保守优化（推荐）

**只合并明显重复的错误，统一命名规范**

1. **合并T21和T23**：统一为341 `DATA_SEND_FAILED`
2. **统一命名规范**：
   - 超时错误：统一使用 `*_TIMEOUT`
   - 权限错误：统一使用 `*_PERMISSION_*` 或 `*_ACCESS_DENIED`
   - 初始化错误：统一使用 `*_NOT_READY`、`*_NOT_INITIALIZED`、`*_INITIALIZATION_FAILED`
   - 协议错误：统一使用 `PROTOCOL_*` 或 `*_PROTOCOL_*`

### 方案2：激进优化

**合并更多相似错误，简化错误码体系**

1. **合并T21和T23**：统一为341 `DATA_SEND_FAILED`
2. **合并连接断开错误**：统一为213 `CONNECTION_DISCONNECTED`，在详细描述中区分场景
3. **统一所有命名规范**：按方案1执行

## 五、错误码类型归类总结

### 超时错误（8个）
- 连接超时、认证超时、Intent超时、网络超时、USB连接超时、协议超时、TaPro响应超时、数据传输超时

### 权限错误（9个）
- 权限不足、未授权访问、Intent权限被拒绝、USB权限被拒绝、USB权限必需、串口权限、设备访问被拒绝、驱动权限错误、系统权限被拒绝

### 初始化错误（6个）
- SDK未初始化、VSP客户端未就绪、无可用服务内核、交易接口为空、初始化失败系统繁忙、AOA初始化失败

### 连接断开错误（6个）
- 连接已断开、连接丢失、连接中断、设备断开连接、线缆断开、WiFi断开

### 设备错误（6个）
- 设备未检测到、设备忙碌、设备错误、设备锁定、USB设备错误、USB设备忙碌

### 协议错误（11个）
- Intent数据无效、响应格式错误、协议版本不匹配、数据序列化错误、无效的USB协议、VSP通信错误、协议切换失败、协议检测失败、协议协商失败、AOA协议错误、协议类型不匹配

## 六、实施建议

### 立即执行
1. ✅ 合并T21和T23为341 `DATA_SEND_FAILED`

### 后续优化
2. ⚠️ 统一超时错误命名规范
3. ⚠️ 统一权限错误命名规范
4. ⚠️ 统一初始化错误命名规范
5. ⚠️ 统一协议错误命名规范

---

**版本**: 1.0.0  
**更新日期**: 2025-01-12  
**作者**: TaPro Team
