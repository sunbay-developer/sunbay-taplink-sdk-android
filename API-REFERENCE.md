# Taplink Android SDK API Reference

> This document is the complete API reference for the Taplink Android SDK. For integration steps and usage patterns, see [README.md](./README.md).

## Contents

- [Callback Semantics](#callback-semantics)
- [Request Models](#request-models)
- [Response Models](#response-models)
- [Error Models](#error-models)
- [Data Models](#data-models)
- [Enums](#enums)

---

## Callback Semantics

### PaymentCallback

All transaction APIs share the same callback interface:

```kotlin
interface PaymentCallback {
    fun onSuccess(result: PaymentResult)
    fun onProgress(event: String, message: String)
    fun onFailure(error: PaymentError)
}
```

| Callback | When it fires | Meaning |
|----------|---------------|---------|
| `onSuccess` | Tapro received and processed the request | Returns all transaction outcomes. Use `result.isSuccess()`, `result.isFailed()`, and `result.isProcessing()` to inspect the final state |
| `onProgress` | During transaction execution | Progress updates such as card insertion, PIN entry, or authorization waiting |
| `onFailure` | SDK cannot communicate with Tapro | Communication/technical errors only, such as disconnects, timeouts, or invalid configuration |

### Key rule

- Approved transaction → `onSuccess`, `result.isSuccess() == true`
- Declined / cancelled / aborted transaction → `onSuccess`, `result.isFailed() == true`
- Processing transaction → `onSuccess`, `result.isProcessing() == true`
- Connection timeout or delivery failure → `onFailure`

---

## Request Models

### AmountInfo

All amounts use the **smallest currency unit** (for example, cents for USD).

| Field | Type | Required | Description |
|------|------|----------|-------------|
| `orderAmount` | BigDecimal | Yes | Order amount in the smallest currency unit |
| `pricingCurrency` | String | Yes | ISO 4217 currency code, such as `USD` or `EUR` |
| `tipAmount` | BigDecimal | No | Tip amount |
| `taxAmount` | BigDecimal | No | Tax amount |
| `surchargeAmount` | BigDecimal | No | Surcharge amount; Tapro removes it automatically for debit card transactions |
| `cashbackAmount` | BigDecimal | No | Cashback amount |
| `serviceFee` | BigDecimal | No | Service fee |

```kotlin
val amount = AmountInfo.of(1000L, "USD") // $10.00
```

---

### SaleRequest

| Field | Type | Required | Description |
|------|------|----------|-------------|
| `referenceOrderId` | String | Yes | Merchant order ID, 6-32 characters |
| `transactionRequestId` | String | Yes | Unique request ID for this transaction |
| `amount` | AmountInfo | Yes | Amount details |
| `description` | String | No | Transaction description, max 128 characters |
| `paymentMethod` | PaymentMethodInfo | No | Payment method information |
| `cardNetworkType` | CardNetworkType | No | Card network type, such as CREDIT or DEBIT |
| `attach` | String | No | Additional data, returned as-is |
| `notifyUrl` | String | No | Notification callback URL |
| `requestTimeout` | Long | No | Request timeout in seconds |
| `staffInfo` | StaffInfo | No | Staff information |
| `tipConfig` | TipConfig | No | Tip configuration, mutually exclusive with `amount.tipAmount` |
| `printReceipt` | PrintReceipt | No | Receipt print mode, defaults to AUTO |

```kotlin
val request = SaleRequest.builder()
    .setReferenceOrderId("ORDER-001")
    .setTransactionRequestId(UUID.randomUUID().toString())
    .setAmount(AmountInfo.of(1000L, "USD"))
    .setDescription("Coffee")
    .build()
```

---

### AuthRequest

| Field | Type | Required | Description |
|------|------|----------|-------------|
| `referenceOrderId` | String | Yes | Merchant order ID, 6-32 characters |
| `transactionRequestId` | String | Yes | Transaction request ID |
| `amount` | AuthAmountInfo | Yes | Authorization amount; amount plus currency only |
| `description` | String | No | Transaction description |
| `paymentMethod` | PaymentMethodInfo | No | Payment method |
| `attach` | String | No | Additional data |
| `notifyUrl` | String | No | Notification URL |
| `requestTimeout` | Long | No | Timeout in seconds |
| `staffInfo` | StaffInfo | No | Staff information |
| `printReceipt` | PrintReceipt | No | Receipt print mode |

---

### ForcedAuthRequest

Used for offline or special authorization scenarios.

| Field | Type | Required | Description |
|------|------|----------|-------------|
| `referenceOrderId` | String | Yes | Merchant order ID |
| `transactionRequestId` | String | Yes | Transaction request ID |
| `amount` | AuthAmountInfo | Yes | Authorization amount |
| `description` | String | No | Transaction description |
| `paymentMethod` | PaymentMethodInfo | No | Payment method |
| `attach` | String | No | Additional data |
| `notifyUrl` | String | No | Notification URL |
| `requestTimeout` | Long | No | Timeout in seconds |
| `staffInfo` | StaffInfo | No | Staff information |
| `printReceipt` | PrintReceipt | No | Receipt print mode |

---

### PostAuthRequest

Completes a pre-authorization transaction and converts the held amount into a final capture.

| Field | Type | Required | Description |
|------|------|----------|-------------|
| `originalTransactionId` | String | One of two | Original pre-authorization transaction ID |
| `originalTransactionRequestId` | String | One of two | Original pre-authorization request ID |
| `transactionRequestId` | String | Yes | Current request ID |
| `amount` | AmountInfo | Yes | Completion amount, can be less than or equal to the authorized amount |
| `description` | String | No | Transaction description |
| `attach` | String | No | Additional data |
| `notifyUrl` | String | No | Notification URL |
| `requestTimeout` | Long | No | Timeout in seconds |
| `staffInfo` | StaffInfo | No | Staff information |
| `tipConfig` | TipConfig | No | Tip configuration |
| `printReceipt` | PrintReceipt | No | Receipt print mode |

---

### IncrementalAuthRequest

Increases the authorization amount on an existing pre-authorization.

| Field | Type | Required | Description |
|------|------|----------|-------------|
| `originalTransactionId` | String | One of two | Original transaction ID |
| `originalTransactionRequestId` | String | One of two | Original transaction request ID |
| `transactionRequestId` | String | Yes | Current request ID |
| `amount` | AuthAmountInfo | Yes | Incremental authorization amount |
| `description` | String | No | Transaction description |
| `attach` | String | No | Additional data |
| `notifyUrl` | String | No | Notification URL |
| `requestTimeout` | Long | No | Timeout in seconds |
| `staffInfo` | StaffInfo | No | Staff information |
| `printReceipt` | PrintReceipt | No | Receipt print mode |

---

### RefundRequest

Supports two refund modes:

1. Referenced refund: based on an original transaction ID or request ID
2. Non-referenced refund: independent refund based on merchant order ID

| Field | Type | Required | Description |
|------|------|----------|-------------|
| `transactionRequestId` | String | Yes | Current refund request ID |
| `amount` | AmountInfo | Yes | Refund amount |
| `description` | String | No | Refund description |
| `originalTransactionId` | String | One of two | Original transaction ID for referenced refunds |
| `originalTransactionRequestId` | String | One of two | Original request ID for referenced refunds |
| `referenceOrderId` | String | One of two | Merchant order ID for non-referenced refunds, 6-32 characters |
| `paymentMethod` | PaymentMethodInfo | No | Payment method |
| `cardNetworkType` | CardNetworkType | No | Card network type |
| `attach` | String | No | Additional data |
| `notifyUrl` | String | No | Notification URL |
| `requestTimeout` | Long | No | Timeout in seconds |
| `staffInfo` | StaffInfo | No | Staff information |
| `printReceipt` | PrintReceipt | No | Receipt print mode |

```kotlin
val request = RefundRequest.referencedBuilder()
    .setTransactionRequestId(UUID.randomUUID().toString())
    .setOriginalTransactionId("txn_123456")
    .setAmount(AmountInfo.of(500L, "USD"))
    .build()
```

---

### VoidRequest

| Field | Type | Required | Description |
|------|------|----------|-------------|
| `originalTransactionId` | String | One of two | Original transaction ID |
| `originalTransactionRequestId` | String | One of two | Original transaction request ID |
| `transactionRequestId` | String | Yes | Current request ID |
| `description` | String | No | Void description |
| `attach` | String | No | Additional data |
| `notifyUrl` | String | No | Notification URL |
| `printReceipt` | PrintReceipt | No | Receipt print mode |

---

### TipAdjustRequest

| Field | Type | Required | Description |
|------|------|----------|-------------|
| `transactionRequestId` | String | Yes | Current request ID |
| `originalTransactionId` | String | One of two | Original transaction ID |
| `originalTransactionRequestId` | String | One of two | Original transaction request ID |
| `tipAmount` | BigDecimal | Yes | Tip amount in the smallest currency unit; must be non-negative |
| `attach` | String | No | Additional data |
| `requestTimeout` | Long | No | Timeout in seconds |

---

### AbortRequest

| Field | Type | Required | Description |
|------|------|----------|-------------|
| `originalTransactionRequestId` | String | Yes | Request ID of the transaction to abort |
| `description` | String | No | Abort reason |
| `attach` | String | No | Additional data |
| `requestTimeout` | Long | No | Timeout in seconds |

---

### QueryRequest

| Field | Type | Required | Description |
|------|------|----------|-------------|
| `transactionId` | String | One of two | Transaction ID |
| `transactionRequestId` | String | One of two | Transaction request ID |

```kotlin
val queryByTxnId = QueryRequest.byTransactionId("txn_123456")
val queryByRequestId = QueryRequest.byTransactionRequestId("req_abcdef")
```

---

### BatchCloseRequest

| Field | Type | Required | Description |
|------|------|----------|-------------|
| `transactionRequestId` | String | Yes | Request ID |
| `description` | String | No | Batch close description, max 128 characters |
| `requestTimeout` | Long | No | Timeout in seconds |

---

## Response Models

### PaymentResult

All transaction operations return this object through `onSuccess`.

#### Basic fields

| Field | Type | Description |
|------|------|-------------|
| `code` | String | Response code; `"100"` indicates gateway success |
| `message` | String? | Response message |
| `traceId` | String? | Trace ID for troubleshooting |

#### Transaction identifiers

| Field | Type | Description |
|------|------|-------------|
| `transactionId` | String? | Nexus transaction ID |
| `referenceOrderId` | String? | Merchant order ID |
| `transactionRequestId` | String? | Transaction request ID |

#### Transaction status

| Field | Type | Description |
|------|------|-------------|
| `transactionStatus` | String? | `SUCCESS`, `PROCESSING`, or `FAILED` |
| `transactionType` | String? | Transaction type, such as SALE, AUTH, REFUND, VOID, etc. |

#### Amount and time

| Field | Type | Description |
|------|------|-------------|
| `amount` | TransactionAmount? | Transaction amount details |
| `createTime` | String? | Creation time in ISO 8601 format |
| `completeTime` | String? | Completion time in ISO 8601 format |

#### Card and receipt data

| Field | Type | Description |
|------|------|-------------|
| `cardInfo` | CardInfo? | Card details |
| `receiptJson` | String? | Receipt JSON data |

#### Voucher and trace data

| Field | Type | Description |
|------|------|-------------|
| `batchNo` | Int? | Batch number |
| `voucherNo` | String? | Voucher number |
| `stan` | String? | STAN |
| `rrn` | String? | RRN |
| `authCode` | String? | Authorization code |

#### Result details

| Field | Type | Description |
|------|------|-------------|
| `transactionResultCode` | String? | Transaction result code |
| `transactionResultMsg` | String? | Transaction result message |
| `description` | String? | Transaction description |
| `attach` | String? | Additional data |

#### Batch close / tip / refund fields

| Field | Type | Description |
|------|------|-------------|
| `batchCloseInfo` | BatchCloseInfo? | Batch close summary |
| `tipAmount` | BigDecimal? | Tip amount in the smallest currency unit |
| `incrementalAmount` | BigDecimal? | Incremental amount |
| `totalAuthorizedAmount` | BigDecimal? | Total authorized amount |
| `merchantRefundNo` | String? | Merchant refund number |
| `originalTransactionId` | String? | Original transaction ID |
| `originalTransactionRequestId` | String? | Original transaction request ID |

### Status helper methods

| Method | Return type | Description |
|------|------|-------------|
| `isSuccess()` | Boolean | Returns `true` when `code == "100"` and `transactionStatus == "SUCCESS"` |
| `isFailed()` | Boolean | Returns `true` when `transactionStatus == "FAILED"` |
| `isProcessing()` | Boolean | Returns `true` when `transactionStatus == "PROCESSING"` |

```kotlin
override fun onSuccess(result: PaymentResult) {
    when {
        result.isSuccess() -> handleApproved(result)
        result.isFailed() -> handleDeclined(result)
        result.isProcessing() -> pollForFinalStatus(result)
    }
}
```

---

### TransactionAmount

| Field | Type | Description |
|------|------|-------------|
| `priceCurrency` | String | ISO 4217 currency code |
| `transAmount` | BigDecimal | Final transaction amount returned by Tapro |
| `orderAmount` | BigDecimal | Order amount |
| `taxAmount` | BigDecimal? | Tax amount |
| `serviceFee` | BigDecimal? | Service fee |
| `surchargeAmount` | BigDecimal? | Surcharge amount |
| `tipAmount` | BigDecimal? | Tip amount |
| `cashbackAmount` | BigDecimal? | Cashback amount |

---

### CardInfo

| Field | Type | Description |
|------|------|-------------|
| `maskedPan` | String? | Masked card number, for example `411111******1111` |
| `cardNetworkType` | String? | Card network type |
| `paymentMethodId` | String? | Payment method ID |
| `subPaymentMethodId` | String? | Sub-payment method ID |
| `entryMode` | String? | Entry mode |
| `authenticationMethod` | String? | Authentication method |
| `cardholderName` | String? | Cardholder name |
| `expiryDate` | String? | Expiry date |
| `issuerBank` | String? | Issuing bank |
| `cardBrand` | String? | Card brand |

---

### BatchCloseInfo

| Field | Type | Description |
|------|------|-------------|
| `totalCount` | Int? | Transaction count |
| `totalAmount` | BigDecimal? | Total amount |
| `totalTip` | BigDecimal? | Total tip |
| `totalTax` | BigDecimal? | Total tax |
| `totalSurchargeAmount` | BigDecimal? | Total surcharge |
| `totalServiceFee` | BigDecimal? | Total service fee |
| `cashDiscount` | BigDecimal? | Cash discount |
| `closeTime` | String? | Batch close time in ISO 8601 format |

---

## Error Models

### PaymentError

Returned only from `onFailure`, which represents communication or technical errors.

| Property | Type | Description |
|------|------|-------------|
| `code` | String | Error code |
| `message` | String | Error message |
| `suggestion` | String | Suggested resolution |
| `category` | ErrorCategory | Error category |
| `canRetryWithSameId` | Boolean | Whether the same `transactionRequestId` can be reused |
| `traceId` | String? | Trace ID |
| `referenceOrderId` | String? | Merchant order ID |
| `transactionId` | String? | Transaction ID |
| `transactionRequestId` | String? | Transaction request ID |

### PaymentError.Detail

| Field | Type | Description |
|------|------|-------------|
| `code` | String | Error code |
| `message` | String | Error message |
| `suggestion` | String | Suggested resolution |
| `errorCategory` | ErrorCategory | Error category |
| `canRetryWithSameId` | Boolean | Whether the same ID can be reused |

### Common retry guidance

| Code | Meaning | Can retry with same ID | Notes |
|------|---------|------------------------|------|
| `306` | Response timeout | No | Query the transaction status first |
| `307` | Transaction already exists | No | Use a new `transactionRequestId` |
| `308` | Transaction is processing | No | Wait or query status |
| Other | Communication/configuration issue | Usually yes | Depends on the error category |

---

## Enums

### ErrorCategory

| Value | Meaning |
|------|---------|
| `INITIALIZATION` | Initialization error |
| `COMMUNICATION` | Communication error |
| `TRANSACTION` | Transaction-level error |
| `UNKNOWN` | Unknown error |

### CardNetworkType

| Value | Meaning |
|------|---------|
| `CREDIT` | Credit card |
| `DEBIT` | Debit card |

### PrintReceipt

| Value | Meaning |
|------|---------|
| `AUTO` | Automatic printing (default) |
| `NONE` | Do not print |
| `MERCHANT` | Print merchant copy only |
| `CUSTOMER` | Print customer copy only |
| `BOTH` | Print both copies |

### Transaction status values

| Value | Meaning |
|------|---------|
| `SUCCESS` | Transaction approved |
| `PROCESSING` | Transaction still in progress |
| `FAILED` | Transaction declined, cancelled, or aborted |

---

## Related Documents

- [README.md](./README.md) — Integration guide and usage examples
- [sunbay-open-docs](https://docs.sunbay.dev/en/sdk/client-side/android) — Online documentation

---

*SDK version: 1.0.6.7-SNAPSHOT*
