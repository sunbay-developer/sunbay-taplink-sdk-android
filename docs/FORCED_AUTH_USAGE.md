# 强制授权（FORCED_AUTH）使用指南

## 概述

强制授权（FORCED_AUTH）是一种特殊的交易类型，用于在预授权交易因网络问题或其他原因失败，但银行已经返回授权码时，强制完成交易。

## 使用场景

FORCED_AUTH 通常在以下情况下使用：

1. **预授权交易超时但获得了授权码**
   - 网络延迟导致交易超时
   - 但银行已经处理并返回了授权码

2. **网络中断导致交易失败但银行已授权**
   - 交易过程中网络连接中断
   - 银行端已经完成授权并分配了授权码

3. **需要使用离线授权码完成交易**
   - 在离线环境下获得的授权码
   - 需要在联机状态下完成交易

## 重要说明

- **必须在预授权交易之后使用**：FORCED_AUTH 不能独立使用，必须基于已存在的预授权交易
- **需要提供银行返回的授权码**：必须有有效的授权码才能执行强制授权
- **通常用于异常情况的交易恢复**：这是一个异常处理机制，不建议作为常规流程

## 基本用法

### 1. 基本强制授权请求

```kotlin
val request = PaymentRequest()
    .setAction("FORCED_AUTH")
    .setOriginalTransactionId("TXN20231119002")  // 必需：原预授权交易ID
    .setTransactionRequestId("TXN_${System.currentTimeMillis()}")
    .setForcedAuthCode("123456")  // 必需：银行返回的授权码
    .setAmount(AmountInfo()
        .setOrderAmount(450.00)  // 必须与原预授权金额一致
        .setPricingCurrency("USD"))
    .setDescription("强制授权完成付款")
    .setReason("网络超时，使用授权码完成交易")

TaplinkSDK.execute(request, paymentCallback)
```

### 2. 完整的预授权 + 强制授权流程

```kotlin
class PaymentManager {
    private var originalAuthTransactionId: String? = null
    
    // 步骤1：执行预授权
    fun performPreAuthorization(amount: BigDecimal) {
        val authRequest = PaymentRequest()
            .setAction("AUTH")
            .setReferenceOrderId("ORDER_${System.currentTimeMillis()}")
            .setTransactionRequestId("AUTH_${System.currentTimeMillis()}")
            .setAmount(AmountInfo()
                .setOrderAmount(amount)
                .setPricingCurrency("USD"))
            .setDescription("酒店预订")

        TaplinkSDK.execute(authRequest, object : PaymentCallback {
            override fun onSuccess(result: PaymentResult) {
                if (result.isSuccess()) {
                    // 预授权成功，保存授权信息
                    originalAuthTransactionId = result.transactionId
                    saveAuthInfo(result.transactionId, result.authCode)
                    showMessage("预授权成功：${result.authCode}")
                } else {
                    showError("预授权失败：${result.message}")
                }
            }
            
            override fun onFailure(error: PaymentError) {
                // 预授权失败，但如果有授权码，可以使用强制授权
                if (!error.authCode.isNullOrEmpty()) {
                    showDialog("预授权超时但获得授权码", 
                        "是否使用授权码 ${error.authCode} 完成交易？") {
                        performForcedAuth(error.authCode!!, amount)
                    }
                } else {
                    showError("预授权失败：${error.message}")
                }
            }
            
            override fun onProgress(event: PaymentEvent) {
                updateProgress(event.message)
            }
        })
    }
    
    // 步骤2：执行强制授权
    private fun performForcedAuth(authCode: String, amount: BigDecimal) {
        val forcedAuthRequest = PaymentRequest()
            .setAction("FORCED_AUTH")
            .setOriginalTransactionId(originalAuthTransactionId!!)
            .setTransactionRequestId("FORCED_AUTH_${System.currentTimeMillis()}")
            .setForcedAuthCode(authCode)
            .setAmount(AmountInfo()
                .setOrderAmount(amount)
                .setPricingCurrency("USD"))
            .setDescription("强制授权完成")
            .setReason("预授权超时，使用授权码完成")
        
        TaplinkSDK.execute(forcedAuthRequest, object : PaymentCallback {
            override fun onSuccess(result: PaymentResult) {
                if (result.isSuccess()) {
                    showMessage("强制授权成功：${result.transactionId}")
                    printReceipt(result)
                } else {
                    showError("强制授权失败：${result.message}")
                }
            }
            
            override fun onFailure(error: PaymentError) {
                showError("强制授权失败：${error.message}")
            }
            
            override fun onProgress(event: PaymentEvent) {
                updateProgress(event.message)
            }
        })
    }
}
```

## 错误处理

### 常见错误码

| 错误码 | 说明 | 解决方案 |
|--------|------|----------|
| C12 | 找不到原始交易 | 检查 originalTransactionId 是否正确 |
| C38 | 不支持的交易类型 | 确认 SDK 版本支持 FORCED_AUTH |
| 051 | 余额不足 | 联系发卡行或更换支付方式 |
| 055 | 授权码无效 | 检查授权码是否正确或已过期 |

### 错误处理示例

```kotlin
override fun onFailure(error: PaymentError) {
    when (error.code) {
        "C12" -> {
            showError("找不到原始预授权交易，请检查交易ID")
        }
        "055" -> {
            showError("授权码无效或已过期，请重新获取")
        }
        "051" -> {
            showError("余额不足，请使用其他支付方式")
        }
        else -> {
            showError("强制授权失败：${error.message}")
        }
    }
}
```

## 最佳实践

### 1. 授权码验证

```kotlin
private fun validateAuthCode(authCode: String): Boolean {
    // 检查授权码格式（通常是6位数字）
    if (authCode.length != 6 || !authCode.all { it.isDigit() }) {
        return false
    }
    
    // 检查授权码是否已过期（通常有效期很短）
    val authTime = getAuthCodeTimestamp(authCode)
    val currentTime = System.currentTimeMillis()
    val maxAge = 5 * 60 * 1000 // 5分钟
    
    return (currentTime - authTime) <= maxAge
}
```

### 2. 交易状态管理

```kotlin
enum class TransactionState {
    PENDING,
    PRE_AUTH_SUCCESS,
    PRE_AUTH_FAILED,
    FORCED_AUTH_SUCCESS,
    FORCED_AUTH_FAILED
}

class TransactionManager {
    private var currentState = TransactionState.PENDING
    
    fun updateState(newState: TransactionState) {
        LogUtil.d("Transaction state: $currentState -> $newState")
        currentState = newState
        
        // 根据状态更新UI
        when (newState) {
            TransactionState.PRE_AUTH_SUCCESS -> enableForcedAuthOption(false)
            TransactionState.PRE_AUTH_FAILED -> enableForcedAuthOption(true)
            TransactionState.FORCED_AUTH_SUCCESS -> completeTransaction()
            TransactionState.FORCED_AUTH_FAILED -> showRetryOptions()
            else -> {}
        }
    }
}
```

### 3. 日志记录

```kotlin
private fun logForcedAuthAttempt(authCode: String, amount: BigDecimal) {
    LogUtil.i("FORCED_AUTH", """
        Attempting forced authorization:
        - Original Transaction ID: $originalAuthTransactionId
        - Auth Code: ${authCode.take(2)}****
        - Amount: $amount
        - Timestamp: ${System.currentTimeMillis()}
    """.trimIndent())
}
```

## 注意事项

1. **时效性**：授权码通常有很短的有效期（通常5-10分钟），建议及时使用

2. **金额一致性**：强制授权的金额必须与原预授权金额完全一致

3. **异常处理**：仅在异常情况下使用，不建议作为常规交易流程

4. **安全性**：授权码是敏感信息，应当安全存储和传输

5. **审计追踪**：建议记录所有强制授权操作，便于后续审计

## 相关交易类型

- **AUTH**：预授权 - FORCED_AUTH 的前置交易
- **POST_AUTH**：预授权完成 - 正常的预授权完成方式
- **INCREMENT_AUTH**：预授权追加 - 增加预授权金额

## 总结

FORCED_AUTH 是一个重要的异常处理机制，能够在预授权交易出现问题时，通过授权码强制完成交易，避免资金损失。但应当谨慎使用，确保在合适的场景下使用，并做好相应的错误处理和日志记录。