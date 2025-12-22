# TransactionAction Enum Usage Guide

## 📋 Overview

The `TransactionAction` enum defines all supported transaction types, replacing the previously used magic strings, providing type safety and better code completion.

## 🎯 Why Use Enums?

### Problems with Using Strings ❌

```kotlin
// ❌ Easy to make spelling errors
val request = PaymentRequest(
    action = "SAEL",  // Spelling error! Should be SALE
    ...
)

// ❌ No code completion
val request = PaymentRequest(
    action = "???",  // Don't know what values are available
    ...
)

// ❌ No compile-time checking
val request = PaymentRequest(
    action = "INVALID_ACTION",  // Compiles but fails at runtime
    ...
)
```

### Advantages of Using Enums ✅

```kotlin
// ✅ Type safe, compile-time checking
val request = PaymentRequest(
    action = TransactionAction.SALE.value,
    ...
)

// ✅ IDE auto-completion for all available values
val request = PaymentRequest(
    action = TransactionAction.  // IDE shows all options
    ...
)

// ✅ Refactoring friendly
// If enum names are changed, IDE can automatically update all references
```

## 📚 Supported Transaction Types

| Enum Value | String Value | Description | Use Case |
|------------|--------------|-------------|----------|
| `INIT` | "INIT" | Initialize connection | Initialize TaPro app after establishing connection |
| `SALE` | "SALE" | Sale transaction | Standard payment transaction |
| `REFUND` | "REFUND" | Refund transaction | Refund a completed transaction |
| `VOID` | "VOID" | Void transaction | Cancel a same-day transaction |
| `AUTH` | "AUTH" | Pre-authorization | Freeze funds without charging |
| `POST_AUTH` | "POST_AUTH" | Complete pre-authorization | Complete pre-auth and charge |
| `INCREMENT_AUTH` | "INCREMENT_AUTH" | Increment pre-authorization | Increase pre-auth amount |
| `FORCED_AUTH` | "FORCED_AUTH" | Forced authorization | Force complete transaction using auth code |
| `TIP_ADJUST` | "TIP_ADJUST" | Tip adjustment | Adjust tip amount for completed transaction |
| `BATCH_CLOSE` | "BATCH_CLOSE" | Batch settlement | Settle all transactions in current batch |
| `QUERY` | "QUERY" | Query transaction | Query transaction status |

## 🔧 Usage Methods

### Method 1: Direct Enum Usage (Recommended) ✅

```kotlin
import com.sunmi.tapro.taplink.sdk.enums.TransactionAction

// Kotlin
val request = PaymentRequest(
    action = TransactionAction.SALE.value,
    referenceOrderId = "ORDER001",
    transactionRequestId = "TXN001",
    amount = amountInfo,
    description = "Product purchase"
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
    .setDescription("Product purchase")
    .build();
```

### Method 2: Using Builder Pattern

```kotlin
// Kotlin - Using enum
val request = PaymentRequest.builder()
    .setAction(TransactionAction.SALE)
    .setReferenceOrderId("ORDER001")
    .setTransactionRequestId("TXN001")
    .setAmount(amountInfo)
    .setDescription("Product purchase")
    .build()
```

### Method 3: Backward Compatibility (Still supports strings)

```kotlin
// Still supports strings, but not recommended
val request = PaymentRequest(
    action = "SALE",  // Backward compatible
    referenceOrderId = "ORDER001",
    ...
)
```

## 📖 Complete Examples

### Example 1: Sale Transaction

```kotlin
import com.sunmi.tapro.taplink.sdk.enums.TransactionAction
import com.sunmi.tapro.taplink.sdk.model.request.PaymentRequest
import com.sunmi.tapro.taplink.sdk.model.common.AmountInfo
import java.math.BigDecimal

// Create sale request
val saleRequest = PaymentRequest(
    action = TransactionAction.SALE.value,
    referenceOrderId = "ORDER${System.currentTimeMillis()}",
    transactionRequestId = "TXN${System.currentTimeMillis()}",
    amount = AmountInfo(
        totalAmount = BigDecimal("100.00"),
        currency = "USD"
    ),
    description = "Purchase product"
)

// Execute transaction
TaplinkSDK.execute(saleRequest, object : PaymentCallback {
    override fun onSuccess(result: PaymentResult) {
        println("Transaction successful: ${result.transactionId}")
    }
    
    override fun onFailure(error: PaymentError) {
        println("Transaction failed: ${error.message}")
    }
    
    override fun onProgress(event: PaymentEvent) {
        println("Transaction in progress: ${event.eventMsg}")
    }
})
```

### Example 2: Refund Transaction

```kotlin
// Create refund request
val refundRequest = PaymentRequest(
    action = TransactionAction.REFUND.value,
    referenceOrderId = "ORDER${System.currentTimeMillis()}",
    transactionRequestId = "REFUND${System.currentTimeMillis()}",
    originalTransactionId = "Original transaction ID",
    amount = AmountInfo(
        totalAmount = BigDecimal("50.00"),
        currency = "USD"
    ),
    description = "Refund",
    reason = "Customer requested refund"
)

TaplinkSDK.execute(refundRequest, callback)
```

### Example 3: Query Transaction

```kotlin
// Method 1: Using QueryRequest (Recommended)
val queryRequest = QueryRequest.byTransactionId("Transaction ID")
TaplinkSDK.query(queryRequest, callback)

// Method 2: Using PaymentRequest
val queryRequest = PaymentRequest(
    action = TransactionAction.QUERY.value,
    transactionRequestId = "QUERY${System.currentTimeMillis()}",
    originalTransactionId = "Transaction ID to query",
    description = "Query transaction status"
)
TaplinkSDK.execute(queryRequest, callback)
```

### Example 4: Pre-authorization Flow

```kotlin
// 1. Pre-authorization
val authRequest = PaymentRequest(
    action = TransactionAction.AUTH.value,
    referenceOrderId = "ORDER001",
    transactionRequestId = "AUTH001",
    amount = AmountInfo(
        totalAmount = BigDecimal("200.00"),
        currency = "USD"
    ),
    description = "Pre-authorization"
)

// 2. Complete pre-authorization
val postAuthRequest = PaymentRequest(
    action = TransactionAction.POST_AUTH.value,
    referenceOrderId = "ORDER001",
    transactionRequestId = "POST_AUTH001",
    originalTransactionId = "Pre-auth transaction ID",
    amount = AmountInfo(
        totalAmount = BigDecimal("180.00"),  // Can be less than pre-auth amount
        currency = "USD"
    ),
    description = "Complete pre-authorization"
)
```

### Example 5: Tip Adjustment

```kotlin
val tipAdjustRequest = PaymentRequest(
    action = TransactionAction.TIP_ADJUST.value,
    referenceOrderId = "ORDER001",
    transactionRequestId = "TIP001",
    originalTransactionId = "Original transaction ID",
    tipAmount = BigDecimal("10.00"),
    description = "Add tip"
)
```

## 🔍 Enum Utility Methods

### Convert from String to Enum

```kotlin
// Method 1: Safe conversion (returns null if not found)
val action = TransactionAction.fromValue("SALE")
if (action != null) {
    println("Found transaction type: ${action.value}")
}

// Method 2: Force conversion (throws exception if not found)
try {
    val action = TransactionAction.valueOf("SALE")
    println("Transaction type: ${action.value}")
} catch (e: IllegalArgumentException) {
    println("Unknown transaction type")
}
```

### Get All Supported Transaction Types

```kotlin
val allActions = TransactionAction.getAllValues()
println("Supported transaction types: $allActions")
// Output: [INIT, SALE, REFUND, VOID, AUTH, POST_AUTH, ...]
```

### Get Enum from PaymentRequest

```kotlin
val request = PaymentRequest(
    action = TransactionAction.SALE.value,
    ...
)

// Get enum
val actionEnum = request.getActionEnum()
when (actionEnum) {
    TransactionAction.SALE -> println("This is a sale transaction")
    TransactionAction.REFUND -> println("This is a refund transaction")
    else -> println("Other transaction type")
}
```

## 🎨 Best Practices

### ✅ Recommended Practices

```kotlin
// 1. Use enum constants
val request = PaymentRequest(
    action = TransactionAction.SALE.value,
    ...
)

// 2. Use when expression to handle different types
when (request.getActionEnum()) {
    TransactionAction.SALE -> handleSale()
    TransactionAction.REFUND -> handleRefund()
    TransactionAction.VOID -> handleVoid()
    else -> handleOther()
}

// 3. Use enum in logging
LogUtil.d(TAG, "Processing ${TransactionAction.SALE} transaction")
```

### ❌ Practices to Avoid

```kotlin
// 1. Avoid hardcoded strings
val request = PaymentRequest(
    action = "SALE",  // ❌ Not recommended
    ...
)

// 2. Avoid string comparison
if (request.action == "SALE") {  // ❌ Not recommended
    handleSale()
}

// Recommend using enum comparison
if (request.getActionEnum() == TransactionAction.SALE) {  // ✅ Recommended
    handleSale()
}
```

## 🔄 Migration Guide

### Migrating from Strings to Enums

**Step 1: Import enum**

```kotlin
import com.sunmi.tapro.taplink.sdk.enums.TransactionAction
```

**Step 2: Replace strings**

```kotlin
// Before
val request = PaymentRequest(
    action = "SALE",
    ...
)

// After
val request = PaymentRequest(
    action = TransactionAction.SALE.value,
    ...
)
```

**Step 3: Update conditional checks**

```kotlin
// Before
if (request.action == "SALE") {
    ...
}

// After
if (request.getActionEnum() == TransactionAction.SALE) {
    ...
}
```

## 📊 Comparison Summary

| Feature | String Approach | Enum Approach |
|---------|----------------|---------------|
| Type Safety | ❌ No | ✅ Yes |
| Compile-time Check | ❌ No | ✅ Yes |
| IDE Completion | ❌ No | ✅ Yes |
| Spelling Errors | ❌ Found at runtime | ✅ Found at compile time |
| Refactoring Support | ❌ Difficult | ✅ Easy |
| Code Readability | ⚠️ Average | ✅ Good |
| Backward Compatibility | ✅ Yes | ✅ Yes |

## ❓ FAQ

### Q1: Is using enums mandatory?

**A:** No, it's not mandatory. For backward compatibility, string approach is still supported. However, using enums is strongly recommended for type safety and better development experience.

### Q2: How to use in Java?

**A:** Usage in Java is simpler:

```java
PaymentRequest request = PaymentRequest.builder()
    .setAction(TransactionAction.SALE)  // Pass enum directly
    .setReferenceOrderId("ORDER001")
    .build();
```

### Q3: Will old code be affected?

**A:** No. All old code will continue to work because we maintain support for string parameters.

### Q4: How to add new transaction types?

**A:** Simply add new enum values to the `TransactionAction` enum:

```kotlin
enum class TransactionAction(val value: String) {
    // Existing ones...
    SALE("SALE"),
    
    // Add new ones
    NEW_ACTION("NEW_ACTION");
}
```

## 🎉 Summary

Advantages of using `TransactionAction` enum:

1. ✅ **Type Safety** - Compile-time checking, avoid spelling errors
2. ✅ **Code Completion** - IDE auto-suggests all available values
3. ✅ **Easy Maintenance** - Centralized management of all transaction types
4. ✅ **Refactoring Friendly** - IDE can automatically update all references
5. ✅ **Backward Compatible** - Doesn't affect existing code
6. ✅ **Clear Documentation** - Each enum value has detailed description

**Recommended to use enum approach in all new code!**

---

**Created**: 2025-12-12  
**Author**: TaPro Team  
**Version**: 1.0.0
