# TransactionAction 快速参考

## 🚀 快速开始

### Kotlin

```kotlin
import com.sunmi.tapro.taplink.sdk.enums.TransactionAction

// 创建请求
val request = PaymentRequest(
    action = TransactionAction.SALE.value,
    referenceOrderId = "ORDER001",
    transactionRequestId = "TXN001",
    amount = amountInfo,
    description = "商品购买"
)
```

### Java

```java
import com.sunmi.tapro.taplink.sdk.enums.TransactionAction;

// 创建请求
PaymentRequest request = PaymentRequest.builder()
    .setAction(TransactionAction.SALE)
    .setReferenceOrderId("ORDER001")
    .setTransactionRequestId("TXN001")
    .setAmount(amountInfo)
    .setDescription("商品购买")
    .build();
```

## 📋 所有交易类型

| 枚举 | 值 | 说明 |
|------|-----|------|
| `INIT` | "INIT" | 初始化连接 |
| `SALE` | "SALE" | 销售交易 |
| `REFUND` | "REFUND" | 退款交易 |
| `VOID` | "VOID" | 撤销交易 |
| `AUTH` | "AUTH" | 预授权 |
| `POST_AUTH` | "POST_AUTH" | 预授权完成 |
| `INCREMENT_AUTH` | "INCREMENT_AUTH" | 预授权追加 |
| `FORCED_AUTH` | "FORCED_AUTH" | 强制授权 |
| `TIP_ADJUST` | "TIP_ADJUST" | 小费调整 |
| `BATCH_CLOSE` | "BATCH_CLOSE" | 批次结算 |
| `QUERY` | "QUERY" | 查询交易 |

## 🔧 常用方法

```kotlin
// 从字符串转换
val action = TransactionAction.fromValue("SALE")

// 获取所有值
val all = TransactionAction.getAllValues()

// 从请求获取枚举
val actionEnum = request.getActionEnum()
```

## 💡 最佳实践

✅ **推荐**
```kotlin
action = TransactionAction.SALE.value
```

⚠️ **不推荐**
```kotlin
action = "SALE"
```

---

📖 [完整文档](TRANSACTION_ACTION_USAGE.md)
