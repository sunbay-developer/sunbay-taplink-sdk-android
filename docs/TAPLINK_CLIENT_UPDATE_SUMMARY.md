# TaplinkClient 更新总结

## 概述

成功更新了TaplinkClient，使其支持新的类型安全的交易请求对象，同时保持与现有PaymentRequest系统的兼容性。

## 主要更改

### 1. 新增类型安全的交易方法

TaplinkClient现在提供了11个新的交易方法，每个方法接受对应的具体请求对象：

```kotlin
// 销售交易
fun sale(request: SaleRequest, callback: PaymentCallback)

// 预授权交易
fun auth(request: AuthRequest, callback: PaymentCallback)

// 强制预授权交易
fun forcedAuth(request: ForcedAuthRequest, callback: PaymentCallback)

// 退款交易
fun refund(request: RefundRequest, callback: PaymentCallback)

// 撤销交易
fun void(request: VoidRequest, callback: PaymentCallback)

// 预授权完成交易
fun postAuth(request: PostAuthRequest, callback: PaymentCallback)

// 增量预授权交易
fun incrementalAuth(request: IncrementalAuthRequest, callback: PaymentCallback)

// 中止交易
fun abort(request: AbortRequest, callback: PaymentCallback)

// 小费调整交易
fun tipAdjust(request: TipAdjustRequest, callback: PaymentCallback)

// 批次查询
fun batchQuery(request: BatchQueryRequest, callback: PaymentCallback)

// 批次关闭
fun batchClose(request: BatchCloseRequest, callback: PaymentCallback)
```

### 2. 请求对象转换机制

实现了完整的转换机制，将具体的请求对象转换为PaymentRequest：

- **SaleRequest** → PaymentRequest (SALE)
- **AuthRequest** → PaymentRequest (AUTH) 
- **ForcedAuthRequest** → PaymentRequest (FORCED_AUTH)
- **RefundRequest** → PaymentRequest (REFUND)
- **VoidRequest** → PaymentRequest (VOID)
- **PostAuthRequest** → PaymentRequest (POST_AUTH)
- **IncrementalAuthRequest** → PaymentRequest (INCREMENT_AUTH)
- **AbortRequest** → PaymentRequest (ABORT)
- **TipAdjustRequest** → PaymentRequest (TIP_ADJUST)
- **BatchQueryRequest** → PaymentRequest (QUERY)
- **BatchCloseRequest** → PaymentRequest (BATCH_CLOSE)

### 3. 字段统一化

将所有请求对象中的`timeExpire`字段统一为`requestTimeout`，与PaymentRequest保持一致：

- **类型**: `String?` → `Long?`
- **单位**: 从字符串时间格式改为秒数
- **语义**: 从"过期时间"改为"请求超时时间"

### 4. 向后兼容性

- 保留了所有原有的交易方法（sale、refund、void等）
- 原有的便捷方法（quickSale、quickRefund等）继续可用
- executeRawRequest方法仍然支持直接使用PaymentRequest

## 使用示例

### 基本用法

```kotlin
// 创建销售请求
val saleRequest = SaleRequest.builder()
    .setReferenceOrderId("ORDER_001")
    .setTransactionRequestId("TXN_001")
    .setAmount(AmountInfo(
        orderAmount = BigDecimal("25.99"),
        pricingCurrency = "USD"
    ))
    .setDescription("Coffee purchase")
    .setRequestTimeout(30L)
    .build()

// 执行交易
client.sale(saleRequest, paymentCallback)
```

### 退款示例

```kotlin
// 引用退款
val refundRequest = RefundRequest.referencedBuilder()
    .setTransactionRequestId("TXN_REFUND_001")
    .setAmount(AmountInfo(
        orderAmount = BigDecimal("10.00"),
        pricingCurrency = "USD"
    ))
    .setDescription("Partial refund")
    .setOriginalTransactionId("ORIGINAL_TXN_ID")
    .build()

client.refund(refundRequest, paymentCallback)
```

## 技术优势

### 1. 类型安全
- 编译时验证字段类型和必需性
- 避免运行时错误
- 更好的IDE支持和自动完成

### 2. 清晰的API
- 每种交易类型有专门的请求类
- 字段明确，避免混淆
- 更好的文档和示例

### 3. 验证增强
- 内置字段验证
- 业务规则检查
- 详细的错误信息

### 4. 维护性
- 代码结构清晰
- 易于扩展新的交易类型
- 统一的转换机制

## 迁移指南

### 从传统方法迁移

**之前:**
```kotlin
client.sale(amountInfo, orderId, description, callback)
```

**现在:**
```kotlin
val request = SaleRequest.builder()
    .setReferenceOrderId(orderId)
    .setTransactionRequestId(generateTxnId())
    .setAmount(amountInfo)
    .setDescription(description)
    .build()
    
client.sale(request, callback)
```

### 字段映射

| 旧字段 | 新字段 | 说明 |
|--------|--------|------|
| timeExpire (String?) | requestTimeout (Long?) | 超时时间，单位改为秒 |
| - | 所有字段类型安全 | 编译时验证 |

## 测试

- 创建了集成测试验证新API
- 提供了完整的使用示例
- 所有转换方法都经过验证

## 总结

此次更新成功实现了：
1. ✅ 类型安全的交易请求对象
2. ✅ 完整的转换机制
3. ✅ 向后兼容性
4. ✅ 统一的字段命名
5. ✅ 完整的文档和示例

TaplinkClient现在提供了更安全、更清晰、更易用的API，同时保持了与现有代码的完全兼容性。