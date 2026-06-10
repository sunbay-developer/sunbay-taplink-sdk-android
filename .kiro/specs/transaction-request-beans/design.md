# Design Document

## Overview

This design document outlines the implementation of specific transaction request beans for the SUNBAY Nexus semi-integrated cloud mode API. The design replaces the generic PaymentRequest approach with type-safe, transaction-specific request classes that provide better developer experience, compile-time validation, and clearer API contracts.

## Architecture

### Package Structure

```
lib_taplink_sdk/src/main/java/com/sunmi/tapro/taplink/sdk/model/request/
├── transaction/
│   ├── SaleRequest.kt
│   ├── AuthRequest.kt
│   ├── ForcedAuthRequest.kt
│   ├── IncrementalAuthRequest.kt
│   ├── PostAuthRequest.kt
│   ├── RefundRequest.kt
│   ├── VoidRequest.kt
│   ├── AbortRequest.kt
│   ├── TipAdjustRequest.kt
│   └── settlement/
│       ├── BatchQueryRequest.kt
│       └── BatchCloseRequest.kt
└── common/
    ├── BaseTransactionRequest.kt
    └── TransactionRequestValidator.kt
```

### Design Principles

1. **Type Safety**: Each transaction type has its own dedicated request class with appropriate fields
2. **Validation**: Built-in validation for required fields and business rules
3. **Builder Pattern**: Consistent builder pattern for easy construction
4. **Immutability**: Data classes are immutable by default
5. **Documentation**: Comprehensive KDoc with usage examples
6. **Backward Compatibility**: Maintain compatibility with existing PaymentRequest where possible

## Components and Interfaces

### Base Transaction Request

```kotlin
abstract class BaseTransactionRequest {
    abstract fun validate(): ValidationResult
}
```

### Transaction Request Validator

```kotlin
object TransactionRequestValidator {
    fun validateTransactionRequestId(transactionRequestId: String?): ValidationResult
    fun validateReferenceOrderId(referenceOrderId: String?): ValidationResult
    fun validateAmount(amount: AmountInfo?): ValidationResult
}
```

### Amount Information Structure

```kotlin
data class AmountInfo(
    val orderAmount: Long,
    val pricingCurrency: String,
    val tipAmount: Long? = null,
    val taxAmount: Long? = null,
    val surchargeAmount: Long? = null,
    val cashbackAmount: Long? = null
)
```

## Data Models

### 1. Sale Request

```kotlin
data class SaleRequest(
    val referenceOrderId: String,
    val transactionRequestId: String,
    val amount: AmountInfo,
    val description: String,
    val paymentMethod: PaymentMethodInfo? = null,
    val attach: String? = null,
    val notifyUrl: String? = null,
    val timeExpire: String? = null
) : BaseTransactionRequest() {
    
    companion object {
        fun builder(): Builder = Builder()
    }
    
    class Builder {
        // Builder implementation
    }
}
```

### 2. Auth Request

```kotlin
data class AuthRequest(
    val referenceOrderId: String,
    val transactionRequestId: String,
    val amount: AuthAmountInfo, // Simplified amount for auth
    val description: String,
    val paymentMethod: PaymentMethodInfo? = null,
    val attach: String? = null,
    val notifyUrl: String? = null,
    val timeExpire: String? = null
) : BaseTransactionRequest()

data class AuthAmountInfo(
    val orderAmount: Long,
    val pricingCurrency: String
)
```

### 3. Refund Request

```kotlin
data class RefundRequest(
    val transactionRequestId: String,
    val amount: AmountInfo,
    val description: String,
    // Referenced refund fields
    val originalTransactionId: String? = null,
    val originalTransactionRequestId: String? = null,
    // Non-referenced refund fields
    val referenceOrderId: String? = null,
    val paymentMethod: PaymentMethodInfo? = null,
    // Common optional fields
    val attach: String? = null,
    val notifyUrl: String? = null,
    val timeExpire: String? = null
) : BaseTransactionRequest() {
    
    init {
        require(isReferencedRefund() || isNonReferencedRefund()) {
            "Refund must be either referenced (with originalTransactionId/originalTransactionRequestId) or non-referenced (with referenceOrderId)"
        }
    }
    
    fun isReferencedRefund(): Boolean = 
        originalTransactionId != null || originalTransactionRequestId != null
    
    fun isNonReferencedRefund(): Boolean = 
        referenceOrderId != null
}
```

### 4. Void Request

```kotlin
data class VoidRequest(
    val originalTransactionId: String? = null,
    val originalTransactionRequestId: String? = null,
    val transactionRequestId: String,
    val description: String,
    val attach: String? = null,
    val notifyUrl: String? = null
) : BaseTransactionRequest() {
    
    init {
        require(originalTransactionId != null || originalTransactionRequestId != null) {
            "Either originalTransactionId or originalTransactionRequestId must be provided"
        }
    }
}
```

### 5. Post Auth Request

```kotlin
data class PostAuthRequest(
    val originalTransactionId: String? = null,
    val originalTransactionRequestId: String? = null,
    val transactionRequestId: String,
    val amount: AmountInfo,
    val description: String,
    val attach: String? = null,
    val notifyUrl: String? = null
) : BaseTransactionRequest() {
    
    init {
        require(originalTransactionId != null || originalTransactionRequestId != null) {
            "Either originalTransactionId or originalTransactionRequestId must be provided"
        }
    }
}
```

### 6. Incremental Auth Request

```kotlin
data class IncrementalAuthRequest(
    val originalTransactionId: String? = null,
    val originalTransactionRequestId: String? = null,
    val transactionRequestId: String,
    val amount: AuthAmountInfo, // Only orderAmount and currency
    val description: String,
    val attach: String? = null,
    val notifyUrl: String? = null
) : BaseTransactionRequest() {
    
    init {
        require(originalTransactionId != null || originalTransactionRequestId != null) {
            "Either originalTransactionId or originalTransactionRequestId must be provided"
        }
        require(amount.orderAmount > 0) {
            "Increment amount must be positive"
        }
    }
}
```

### 7. Forced Auth Request

```kotlin
data class ForcedAuthRequest(
    val referenceOrderId: String,
    val transactionRequestId: String,
    val amount: AuthAmountInfo,
    val description: String,
    val paymentMethod: PaymentMethodInfo? = null,
    val attach: String? = null,
    val notifyUrl: String? = null,
    val timeExpire: String? = null
) : BaseTransactionRequest()
```

### 8. Abort Request

```kotlin
data class AbortRequest(
    val originalTransactionId: String? = null,
    val originalTransactionRequestId: String? = null,
    val description: String? = null,
    val attach: String? = null
) : BaseTransactionRequest() {
    
    init {
        require(originalTransactionId != null || originalTransactionRequestId != null) {
            "Either originalTransactionId or originalTransactionRequestId must be provided"
        }
    }
}
```

### 9. Tip Adjust Request

```kotlin
data class TipAdjustRequest(
    val originalTransactionId: String? = null,
    val originalTransactionRequestId: String? = null,
    val tipAmount: Long,
    val attach: String? = null
) : BaseTransactionRequest() {
    
    init {
        require(originalTransactionId != null || originalTransactionRequestId != null) {
            "Either originalTransactionId or originalTransactionRequestId must be provided"
        }
        require(tipAmount >= 0) {
            "Tip amount must be non-negative"
        }
    }
}
```

### 10. Batch Query Request

```kotlin
data class BatchQueryRequest(
    // No fields needed - batch query is context-based
) : BaseTransactionRequest() {
    
    companion object {
        fun create(): BatchQueryRequest = BatchQueryRequest()
    }
}
```

### 11. Batch Close Request

```kotlin
data class BatchCloseRequest(
    val transactionRequestId: String,
    val channelCode: String,
    val description: String? = null
) : BaseTransactionRequest() {
    
    init {
        require(channelCode.isNotEmpty()) {
            "channelCode is required"
        }
    }
    
    companion object {
        fun builder(): Builder = Builder()
    }
    
    class Builder {
        // Builder implementation
    }
}
```

## Correctness Properties

*A property is a characteristic or behavior that should hold true across all valid executions of a system-essentially, a formal statement about what the system should do. Properties serve as the bridge between human-readable specifications and machine-verifiable correctness guarantees.*

Let me analyze the acceptance criteria to determine testable properties:

Based on the prework analysis, I've identified the following consolidated correctness properties:

### Property 1: Required Field Validation
*For any* transaction request bean, all fields marked as required in the API documentation should be validated as non-null and non-empty during construction
**Validates: Requirements 1.1, 1.5, 2.1, 4.2, 5.2, 6.2, 7.1, 7.5, 8.2, 9.2, 10.1, 10.4, 11.1**

### Property 2: Optional Field Support
*For any* transaction request bean, all optional fields should be settable and retrievable without affecting required field validation
**Validates: Requirements 1.2, 2.2, 4.3, 5.4, 6.4, 7.2, 8.3, 9.3, 11.2**

### Property 3: Builder Pattern Consistency
*For any* transaction request bean that provides a builder, the builder should successfully construct valid instances when all required fields are provided
**Validates: Requirements 1.4, 2.4, 7.4, 10.3, 11.4, 12.1**

### Property 4: Amount Structure Validation by Transaction Type
*For any* transaction request that includes amount information, the amount structure should contain only the fields appropriate for that transaction type
**Validates: Requirements 1.3, 2.3, 2.5, 3.4, 5.3, 6.3, 7.3**

### Property 5: Original Transaction Reference Validation
*For any* transaction request that references an original transaction, exactly one of originalTransactionId or originalTransactionRequestId must be provided
**Validates: Requirements 4.1, 5.1, 6.1, 8.1, 8.5, 9.1**

### Property 6: Refund Mode Mutual Exclusivity
*For any* refund request, it should be either referenced (with original transaction identifiers) or non-referenced (with referenceOrderId), but never both
**Validates: Requirements 3.1, 3.2, 3.3, 3.5**

### Property 7: Field Exclusion Validation
*For any* transaction request bean, fields that are explicitly not supported for that transaction type should not be present in the class structure
**Validates: Requirements 4.4, 4.5, 8.4, 9.5, 10.2**

### Property 8: Positive Amount Validation
*For any* transaction request with amount fields that must be positive (like increment amounts), validation should reject non-positive values
**Validates: Requirements 6.5, 9.4**

### Property 9: API Compatibility
*For any* existing PaymentRequest usage pattern, the new request bean system should provide equivalent functionality through appropriate request beans
**Validates: Requirements 12.3, 12.5**

### Property 10: Channel Code Format Validation
*For any* batch close request, the channelCode should be validated for proper format and non-empty value
**Validates: Requirements 11.3**

## Error Handling

### Validation Errors

```kotlin
sealed class ValidationError(val message: String) {
    object MissingTransactionRequestId : ValidationError("transactionRequestId is required")
    object MissingReferenceOrderId : ValidationError("referenceOrderId is required")
    object MissingAmount : ValidationError("amount is required")
    object MissingDescription : ValidationError("description is required")
    object InvalidAmount : ValidationError("amount must be positive")
    object InvalidTipAmount : ValidationError("tipAmount must be non-negative")
    object MissingOriginalTransaction : ValidationError("Either originalTransactionId or originalTransactionRequestId is required")
    object InvalidRefundMode : ValidationError("Refund must be either referenced or non-referenced, not both")
    object InvalidChannelCode : ValidationError("channelCode is required and must not be empty")
}

data class ValidationResult(
    val isValid: Boolean,
    val errors: List<ValidationError> = emptyList()
)
```

### Exception Handling

```kotlin
class TransactionRequestValidationException(
    val errors: List<ValidationError>
) : IllegalArgumentException("Validation failed: ${errors.joinToString(", ") { it.message }}")
```

## Testing Strategy

### Unit Testing Approach

**Unit Tests Focus:**
- Specific examples and edge cases for each request bean
- Builder pattern functionality
- Validation error scenarios
- Field presence/absence verification

**Property Tests Focus:**
- Universal validation rules across all request types
- Builder pattern consistency
- Amount structure validation
- Field requirement compliance

### Property-Based Testing Configuration

- **Minimum 100 iterations** per property test
- **Test Library:** Use Kotest Property Testing for Kotlin
- **Generators:** Custom generators for each request type with valid/invalid data combinations
- **Tag Format:** **Feature: transaction-request-beans, Property {number}: {property_text}**

### Test Data Generators

```kotlin
object RequestGenerators {
    fun validSaleRequest(): Arb<SaleRequest>
    fun invalidSaleRequest(): Arb<SaleRequest>
    fun validAuthRequest(): Arb<AuthRequest>
    fun validRefundRequest(): Arb<RefundRequest>
    // ... other generators
}
```

### Integration Testing

- Test compatibility with existing PaymentRequest usage
- Test serialization/deserialization with API endpoints
- Test builder pattern usage from Java code
- Test validation integration with SDK components

## Implementation Notes

### Kotlin Data Class Benefits

1. **Immutability**: All request objects are immutable by default
2. **Copy Function**: Easy creation of modified copies
3. **Destructuring**: Support for destructuring declarations
4. **Equals/HashCode**: Automatic implementation based on properties

### Java Interoperability

1. **Builder Pattern**: Provides familiar Java construction pattern
2. **Static Factory Methods**: Easy creation methods for common scenarios
3. **Getter Methods**: Automatic generation for Java access
4. **Validation**: Consistent validation across Kotlin and Java usage

### Backward Compatibility Strategy

1. **Deprecation Path**: Mark PaymentRequest methods as deprecated with migration guidance
2. **Adapter Pattern**: Provide adapters to convert between old and new request types
3. **Documentation**: Clear migration examples in KDoc
4. **Gradual Migration**: Support both patterns during transition period

## Migration Guide

### From PaymentRequest to Specific Request Beans

**Before:**
```kotlin
val request = PaymentRequest(
    action = TransactionAction.SALE.value,
    referenceOrderId = "ORDER123",
    transactionRequestId = "TXN123",
    amount = amountInfo,
    description = "Purchase"
)
```

**After:**
```kotlin
val request = SaleRequest(
    referenceOrderId = "ORDER123",
    transactionRequestId = "TXN123",
    amount = amountInfo,
    description = "Purchase"
)
```

### Builder Pattern Migration

**Before:**
```kotlin
val request = PaymentRequest.builder()
    .setAction(TransactionAction.SALE)
    .setReferenceOrderId("ORDER123")
    .build()
```

**After:**
```kotlin
val request = SaleRequest.builder()
    .setReferenceOrderId("ORDER123")
    .setTransactionRequestId("TXN123")
    .setAmount(amountInfo)
    .setDescription("Purchase")
    .build()
```