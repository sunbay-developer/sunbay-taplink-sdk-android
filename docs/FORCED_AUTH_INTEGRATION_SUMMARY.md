# FORCED_AUTH 集成总结

## 完成状态

✅ **FORCED_AUTH（强制授权）交易类型已成功集成到 Taplink SDK 文档中**

## 已完成的工作

### 1. 代码层面
- ✅ `TransactionAction` 枚举中已包含 `FORCED_AUTH("FORCED_AUTH")` 定义
- ✅ 位置：`lib_taplink_sdk/src/main/java/com/sunmi/tapro/taplink/sdk/enums/TransactionAction.kt`
- ✅ 包含完整的注释说明

### 2. 文档层面

#### 2.1 创建了专门的使用指南
- ✅ 文件：`docs/FORCED_AUTH_USAGE.md`
- ✅ 内容包括：
  - 概述和使用场景
  - 重要说明
  - 基本用法示例
  - 完整的预授权 + 强制授权流程
  - 错误处理
  - 最佳实践
  - 注意事项

#### 2.2 已在现有文档中引用
- ✅ `lib_taplink_sdk/TRANSACTION_ACTION_USAGE.md` - 包含 FORCED_AUTH 的完整说明
- ✅ `lib_taplink_sdk/TRANSACTION_ACTION_QUICK_REF.md` - 快速参考表中包含 FORCED_AUTH
- ✅ `docs/Sunbay Taplink SDK for Android - 集成指南.md` - API 参考部分提到 FORCED_AUTH

## FORCED_AUTH 在预授权流程中的位置

```
预授权流程：
1. AUTH（预授权）- 冻结资金
   ↓
2a. POST_AUTH（预授权完成）- 正常完成流程
   或
2b. FORCED_AUTH（强制授权）- 异常情况下使用授权码完成
   或
2c. INCREMENT_AUTH（预授权追加）- 增加预授权金额
```

## 使用场景

FORCED_AUTH 主要用于以下异常情况：

1. **预授权交易超时但获得了授权码**
   - 网络延迟导致交易超时
   - 但银行已经处理并返回了授权码

2. **网络中断导致交易失败但银行已授权**
   - 交易过程中网络连接中断
   - 银行端已经完成授权并分配了授权码

3. **需要使用离线授权码完成交易**
   - 在离线环境下获得的授权码
   - 需要在联机状态下完成交易

## 关键特性

### 必需参数
- `action`: "FORCED_AUTH"
- `originalTransactionId`: 原预授权交易ID
- `transactionRequestId`: 新的交易请求ID（唯一）
- `forcedAuthCode`: 银行返回的授权码
- `amount`: 金额（必须与原预授权金额一致）

### 重要限制
- ✅ 必须在预授权交易之后使用
- ✅ 需要提供有效的授权码
- ✅ 金额必须与原预授权金额一致
- ✅ 授权码通常有时效性（5-10分钟）
- ⚠️ 仅用于异常情况，不建议作为常规流程

## 代码示例

### 基本用法

```kotlin
val request = PaymentRequest()
    .setAction("FORCED_AUTH")
    .setOriginalTransactionId("TXN20231119002")
    .setTransactionRequestId("TXN_${System.currentTimeMillis()}")
    .setForcedAuthCode("123456")
    .setAmount(AmountInfo()
        .setOrderAmount(450.00)
        .setPricingCurrency("USD"))
    .setDescription("强制授权完成付款")
    .setReason("网络超时，使用授权码完成交易")

TaplinkSDK.execute(request, paymentCallback)
```

### 完整流程

```kotlin
// 1. 预授权
val authRequest = PaymentRequest()
    .setAction("AUTH")
    .setAmount(AmountInfo().setOrderAmount(500.00).setPricingCurrency("USD"))
    // ...

TaplinkSDK.execute(authRequest, object : PaymentCallback {
    override fun onSuccess(result: PaymentResult) {
        // 保存授权信息
        saveAuthInfo(result.transactionId, result.authCode)
    }
    
    override fun onFailure(error: PaymentError) {
        // 如果有授权码，可以使用强制授权
        if (error.authCode != null) {
            performForcedAuth(error.authCode)
        }
    }
})

// 2. 强制授权
private fun performForcedAuth(authCode: String) {
    val forcedAuthRequest = PaymentRequest()
        .setAction("FORCED_AUTH")
        .setOriginalTransactionId(originalAuthTransactionId)
        .setForcedAuthCode(authCode)
        .setAmount(AmountInfo().setOrderAmount(500.00).setPricingCurrency("USD"))
        // ...
    
    TaplinkSDK.execute(forcedAuthRequest, paymentCallback)
}
```

## 文档位置

### 主要文档
1. **专门使用指南**：`docs/FORCED_AUTH_USAGE.md`
   - 完整的使用说明
   - 代码示例
   - 最佳实践

2. **集成指南**：`docs/Sunbay Taplink SDK for Android - 集成指南.md`
   - 第5章：核心功能
   - 第6章：API 参考（PaymentRequest 部分）

3. **交易类型文档**：`lib_taplink_sdk/TRANSACTION_ACTION_USAGE.md`
   - 所有交易类型的完整说明
   - 包含 FORCED_AUTH 的详细用法

4. **快速参考**：`lib_taplink_sdk/TRANSACTION_ACTION_QUICK_REF.md`
   - 快速查找表
   - 包含 FORCED_AUTH 的简要说明

## 建议的文档更新（可选）

如果需要在集成指南中添加独立的 FORCED_AUTH 章节，建议在以下位置插入：

```markdown
### 5.6 预授权完成（POST_AUTH）
...

### 5.7 强制授权（FORCED_AUTH）  ← 新增章节
...

### 5.8 小费调整（TIP_ADJUST）
...

### 5.9 批次关闭（BATCH_CLOSE）
```

## 验证清单

- [x] TransactionAction 枚举中包含 FORCED_AUTH
- [x] 创建了专门的使用指南文档
- [x] 在交易类型文档中包含 FORCED_AUTH
- [x] 提供了完整的代码示例
- [x] 说明了使用场景和限制
- [x] 提供了错误处理指南
- [x] 包含了最佳实践建议

## 总结

FORCED_AUTH 交易类型已经完全集成到 Taplink SDK 中，包括：
- ✅ 代码实现（枚举定义）
- ✅ 完整的文档说明
- ✅ 使用示例和最佳实践
- ✅ 错误处理指南

开发者可以通过以下方式使用 FORCED_AUTH：
1. 查阅 `docs/FORCED_AUTH_USAGE.md` 了解详细用法
2. 参考 `lib_taplink_sdk/TRANSACTION_ACTION_USAGE.md` 了解所有交易类型
3. 在预授权流程中，当出现异常但获得授权码时，使用 FORCED_AUTH 完成交易

---

**创建日期**：2025-12-22  
**作者**：TaPro Team  
**版本**：1.0.0
