# TransactionAction 枚举使用指南

## 📋 概述

`TransactionAction` 枚举定义了所有支持的交易类型，替代了之前使用的魔法字符串，提供了类型安全和更好的代码提示。

## 🎯 为什么使用枚举？

### 使用字符串的问题 ❌

```kotlin
// ❌ 容易拼写错误
val request = PaymentRequest(
    action = "SAEL",  // 拼写错误！应该是 SALE
    ...
)

// ❌ 没有代码提示
val request = PaymentRequest(
    action = "???",  // 不知道有哪些可用的值
    ...
)

// ❌ 编译时无法检查
val request = PaymentRequest(
    action = "INVALID_ACTION",  // 编译通过，运行时才发现错误
    ...
)
```

### 使用枚举的优势 ✅

```kotlin
// ✅ 类型安全，编译时检查
val request = PaymentRequest(
    action = TransactionAction.SALE.value,
    ...
)

// ✅ IDE 自动提示所有可用值
val request = PaymentRequest(
    action = TransactionAction.  // IDE 会显示所有选项
    ...
)

// ✅ 重构友好
// 如果修改枚举名称，IDE 可以自动更新所有引用
```

## 📚 支持的交易类型

| 枚举值 | 字符串值 | 说明 | 使用场景 |
|--------|---------|------|---------|
| `INIT` | "INIT" | 初始化连接 | 建立连接后初始化 TaPro 应用 |
| `SALE` | "SALE" | 销售交易 | 标准的支付交易 |
| `REFUND` | "REFUND" | 退款交易 | 对已完成的交易进行退款 |
| `VOID` | "VOID" | 撤销交易 | 撤销当天的交易（当日退款） |
| `AUTH` | "AUTH" | 预授权 | 冻结资金但不扣款 |
| `POST_AUTH` | "POST_AUTH" | 预授权完成 | 完成预授权并扣款 |
| `INCREMENT_AUTH` | "INCREMENT_AUTH" | 预授权追加 | 增加预授权金额 |
| `FORCED_AUTH` | "FORCED_AUTH" | 强制授权 | 使用授权码强制完成交易 |
| `TIP_ADJUST` | "TIP_ADJUST" | 小费调整 | 调整已完成交易的小费金额 |
| `BATCH_CLOSE` | "BATCH_CLOSE" | 批次结算 | 结算当前批次的所有交易 |
| `QUERY` | "QUERY" | 查询交易 | 查询交易状态 |

## 🔧 使用方法

### 方法 1: 直接使用枚举值（推荐）✅

```kotlin
import com.sunmi.tapro.taplink.sdk.enums.TransactionAction

// Kotlin
val request = PaymentRequest(
    action = TransactionAction.SALE.value,
    referenceOrderId = "ORDER001",
    transactionRequestId = "TXN001",
    amount = amountInfo,
    description = "商品购买"
)
```

```java
// Java
import com.sunmi.tapro.taplink.sdk.enums.TransactionAction;

PaymentRequest request = PaymentRequest.builder()
    .setAction(TransactionAction.SALE)
    .setReferenceOrderId("ORDER001")
    .setTransactionRequestId("TXN001")
    .setAmount(amountInfo)
    .setDescription("商品购买")
    .build();
```

### 方法 2: 使用 Builder 模式

```kotlin
// Kotlin - 使用枚举
val request = PaymentRequest.builder()
    .setAction(TransactionAction.SALE)
    .setReferenceOrderId("ORDER001")
    .setTransactionRequestId("TXN001")
    .setAmount(amountInfo)
    .setDescription("商品购买")
    .build()
```

### 方法 3: 向后兼容（仍然支持字符串）

```kotlin
// 仍然支持字符串，但不推荐
val request = PaymentRequest(
    action = "SALE",  // 向后兼容
    referenceOrderId = "ORDER001",
    ...
)
```

## 📖 完整示例

### 示例 1: 销售交易

```kotlin
import com.sunmi.tapro.taplink.sdk.enums.TransactionAction
import com.sunmi.tapro.taplink.sdk.model.request.PaymentRequest
import com.sunmi.tapro.taplink.sdk.model.common.AmountInfo
import java.math.BigDecimal

// 创建销售请求
val saleRequest = PaymentRequest(
    action = TransactionAction.SALE.value,
    referenceOrderId = "ORDER${System.currentTimeMillis()}",
    transactionRequestId = "TXN${System.currentTimeMillis()}",
    amount = AmountInfo(
        totalAmount = BigDecimal("100.00"),
        currency = "USD"
    ),
    description = "购买商品"
)

// 执行交易
TaplinkSDK.execute(saleRequest, object : PaymentCallback {
    override fun onSuccess(result: PaymentResult) {
        println("交易成功: ${result.transactionId}")
    }
    
    override fun onFailure(error: PaymentError) {
        println("交易失败: ${error.message}")
    }
    
    override fun onProgress(event: PaymentEvent) {
        println("交易进行中: ${event.eventMsg}")
    }
})
```

### 示例 2: 退款交易

```kotlin
// 创建退款请求
val refundRequest = PaymentRequest(
    action = TransactionAction.REFUND.value,
    referenceOrderId = "ORDER${System.currentTimeMillis()}",
    transactionRequestId = "REFUND${System.currentTimeMillis()}",
    originalTransactionId = "原始交易ID",
    amount = AmountInfo(
        totalAmount = BigDecimal("50.00"),
        currency = "USD"
    ),
    description = "退款",
    reason = "客户要求退款"
)

TaplinkSDK.execute(refundRequest, callback)
```

### 示例 3: 查询交易

```kotlin
// 方式 1: 使用 QueryRequest（推荐）
val queryRequest = QueryRequest.byTransactionId("交易ID")
TaplinkSDK.query(queryRequest, callback)

// 方式 2: 使用 PaymentRequest
val queryRequest = PaymentRequest(
    action = TransactionAction.QUERY.value,
    transactionRequestId = "QUERY${System.currentTimeMillis()}",
    originalTransactionId = "要查询的交易ID",
    description = "查询交易状态"
)
TaplinkSDK.execute(queryRequest, callback)
```

### 示例 4: 预授权流程

```kotlin
// 1. 预授权
val authRequest = PaymentRequest(
    action = TransactionAction.AUTH.value,
    referenceOrderId = "ORDER001",
    transactionRequestId = "AUTH001",
    amount = AmountInfo(
        totalAmount = BigDecimal("200.00"),
        currency = "USD"
    ),
    description = "预授权"
)

// 2. 预授权完成
val postAuthRequest = PaymentRequest(
    action = TransactionAction.POST_AUTH.value,
    referenceOrderId = "ORDER001",
    transactionRequestId = "POST_AUTH001",
    originalTransactionId = "预授权交易ID",
    amount = AmountInfo(
        totalAmount = BigDecimal("180.00"),  // 可以小于预授权金额
        currency = "USD"
    ),
    description = "预授权完成"
)
```

### 示例 5: 小费调整

```kotlin
val tipAdjustRequest = PaymentRequest(
    action = TransactionAction.TIP_ADJUST.value,
    referenceOrderId = "ORDER001",
    transactionRequestId = "TIP001",
    originalTransactionId = "原始交易ID",
    tipAmount = BigDecimal("10.00"),
    description = "添加小费"
)
```

## 🔍 枚举工具方法

### 从字符串转换为枚举

```kotlin
// 方法 1: 安全转换（返回 null 如果不存在）
val action = TransactionAction.fromValue("SALE")
if (action != null) {
    println("找到交易类型: ${action.value}")
}

// 方法 2: 强制转换（抛出异常如果不存在）
try {
    val action = TransactionAction.valueOf("SALE")
    println("交易类型: ${action.value}")
} catch (e: IllegalArgumentException) {
    println("未知的交易类型")
}
```

### 获取所有支持的交易类型

```kotlin
val allActions = TransactionAction.getAllValues()
println("支持的交易类型: $allActions")
// 输出: [INIT, SALE, REFUND, VOID, AUTH, POST_AUTH, ...]
```

### 从 PaymentRequest 获取枚举

```kotlin
val request = PaymentRequest(
    action = TransactionAction.SALE.value,
    ...
)

// 获取枚举
val actionEnum = request.getActionEnum()
when (actionEnum) {
    TransactionAction.SALE -> println("这是销售交易")
    TransactionAction.REFUND -> println("这是退款交易")
    else -> println("其他交易类型")
}
```

## 🎨 最佳实践

### ✅ 推荐做法

```kotlin
// 1. 使用枚举常量
val request = PaymentRequest(
    action = TransactionAction.SALE.value,
    ...
)

// 2. 使用 when 表达式处理不同类型
when (request.getActionEnum()) {
    TransactionAction.SALE -> handleSale()
    TransactionAction.REFUND -> handleRefund()
    TransactionAction.VOID -> handleVoid()
    else -> handleOther()
}

// 3. 在日志中使用枚举
LogUtil.d(TAG, "Processing ${TransactionAction.SALE} transaction")
```

### ❌ 避免的做法

```kotlin
// 1. 避免硬编码字符串
val request = PaymentRequest(
    action = "SALE",  // ❌ 不推荐
    ...
)

// 2. 避免字符串比较
if (request.action == "SALE") {  // ❌ 不推荐
    handleSale()
}

// 推荐使用枚举比较
if (request.getActionEnum() == TransactionAction.SALE) {  // ✅ 推荐
    handleSale()
}
```

## 🔄 迁移指南

### 从字符串迁移到枚举

**步骤 1: 导入枚举**

```kotlin
import com.sunmi.tapro.taplink.sdk.enums.TransactionAction
```

**步骤 2: 替换字符串**

```kotlin
// 之前
val request = PaymentRequest(
    action = "SALE",
    ...
)

// 之后
val request = PaymentRequest(
    action = TransactionAction.SALE.value,
    ...
)
```

**步骤 3: 更新条件判断**

```kotlin
// 之前
if (request.action == "SALE") {
    ...
}

// 之后
if (request.getActionEnum() == TransactionAction.SALE) {
    ...
}
```

## 📊 对比总结

| 特性 | 字符串方式 | 枚举方式 |
|------|-----------|---------|
| 类型安全 | ❌ 无 | ✅ 有 |
| 编译时检查 | ❌ 无 | ✅ 有 |
| IDE 提示 | ❌ 无 | ✅ 有 |
| 拼写错误 | ❌ 运行时才发现 | ✅ 编译时发现 |
| 重构支持 | ❌ 困难 | ✅ 容易 |
| 代码可读性 | ⚠️ 一般 | ✅ 好 |
| 向后兼容 | ✅ 是 | ✅ 是 |

## ❓ 常见问题

### Q1: 是否必须使用枚举？

**A:** 不是必须的。为了向后兼容，仍然支持字符串方式。但强烈推荐使用枚举，因为它提供了类型安全和更好的开发体验。

### Q2: 如何在 Java 中使用？

**A:** Java 中使用更简单：

```java
PaymentRequest request = PaymentRequest.builder()
    .setAction(TransactionAction.SALE)  // 直接传枚举
    .setReferenceOrderId("ORDER001")
    .build();
```

### Q3: 旧代码会受影响吗？

**A:** 不会。所有旧代码仍然可以正常工作，因为我们保留了字符串参数的支持。

### Q4: 如何添加新的交易类型？

**A:** 在 `TransactionAction` 枚举中添加新的枚举值即可：

```kotlin
enum class TransactionAction(val value: String) {
    // 现有的...
    SALE("SALE"),
    
    // 添加新的
    NEW_ACTION("NEW_ACTION");
}
```

## 🎉 总结

使用 `TransactionAction` 枚举的优势：

1. ✅ **类型安全** - 编译时检查，避免拼写错误
2. ✅ **代码提示** - IDE 自动提示所有可用值
3. ✅ **易于维护** - 集中管理所有交易类型
4. ✅ **重构友好** - IDE 可以自动更新所有引用
5. ✅ **向后兼容** - 不影响现有代码
6. ✅ **文档清晰** - 每个枚举值都有详细说明

**推荐在所有新代码中使用枚举方式！**

---

**创建日期**: 2025-12-12  
**作者**: TaPro Team  
**版本**: 1.0.0
