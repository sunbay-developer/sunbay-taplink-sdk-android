# Requirements Document

## Introduction

This document defines the requirements for creating specific transaction request beans based on the SUNBAY Nexus semi-integrated cloud mode API documentation. The system needs to provide type-safe, well-structured request classes for each transaction type, replacing the current generic PaymentRequest approach with specific, focused request beans.

## Glossary

- **Transaction_Request_Bean**: A data class representing the request parameters for a specific transaction type
- **Semi_Integrated_API**: The SUNBAY Nexus cloud-based payment processing API
- **Transaction_Type**: A specific kind of payment operation (Sale, Auth, Refund, etc.)
- **Request_Parameters**: The data fields required or optional for each transaction type
- **API_Documentation**: The official SUNBAY Nexus semi-integrated cloud mode interface documentation

## Requirements

### Requirement 1: Sale Transaction Request Bean

**User Story:** As a developer, I want a dedicated SaleRequest class, so that I can create sale transactions with type-safe, validated parameters.

#### Acceptance Criteria

1. THE Sale_Request_Bean SHALL include all required fields: referenceOrderId, transactionRequestId, amount, description
2. THE Sale_Request_Bean SHALL include all optional fields: paymentMethod, attach, notifyUrl, timeExpire
3. THE Sale_Request_Bean SHALL support complex amount structure with orderAmount, tipAmount, taxAmount, surchargeAmount, cashbackAmount, pricingCurrency
4. THE Sale_Request_Bean SHALL provide builder pattern for easy construction
5. THE Sale_Request_Bean SHALL validate required fields are not null or empty

### Requirement 2: Auth Transaction Request Bean

**User Story:** As a developer, I want a dedicated AuthRequest class, so that I can create pre-authorization transactions with appropriate parameters.

#### Acceptance Criteria

1. THE Auth_Request_Bean SHALL include required fields: referenceOrderId, transactionRequestId, amount, description
2. THE Auth_Request_Bean SHALL include optional fields: paymentMethod, attach, notifyUrl, timeExpire
3. THE Auth_Request_Bean SHALL support amount structure with orderAmount and pricingCurrency only (no tip/tax/surcharge for auth)
4. THE Auth_Request_Bean SHALL provide builder pattern for construction
5. THE Auth_Request_Bean SHALL validate that amount contains only valid auth fields

### Requirement 3: Refund Transaction Request Bean

**User Story:** As a developer, I want a dedicated RefundRequest class, so that I can create refund transactions with proper reference handling.

#### Acceptance Criteria

1. THE Refund_Request_Bean SHALL support both referenced and non-referenced refunds
2. WHEN creating referenced refund, THE Refund_Request_Bean SHALL require originalTransactionId or originalTransactionRequestId
3. WHEN creating non-referenced refund, THE Refund_Request_Bean SHALL require referenceOrderId and paymentMethod
4. THE Refund_Request_Bean SHALL include all amount fields: orderAmount, tipAmount, taxAmount, surchargeAmount, cashbackAmount
5. THE Refund_Request_Bean SHALL validate mutual exclusivity between referenced and non-referenced modes

### Requirement 4: Void Transaction Request Bean

**User Story:** As a developer, I want a dedicated VoidRequest class, so that I can cancel same-day transactions with minimal parameters.

#### Acceptance Criteria

1. THE Void_Request_Bean SHALL require originalTransactionId or originalTransactionRequestId
2. THE Void_Request_Bean SHALL require transactionRequestId, description
3. THE Void_Request_Bean SHALL include optional fields: attach, notifyUrl
4. THE Void_Request_Bean SHALL NOT include amount fields (void is always full amount)
5. THE Void_Request_Bean SHALL NOT include referenceOrderId (void operates on transactions, not orders)

### Requirement 5: Post Auth Transaction Request Bean

**User Story:** As a developer, I want a dedicated PostAuthRequest class, so that I can complete pre-authorization transactions.

#### Acceptance Criteria

1. THE Post_Auth_Request_Bean SHALL require originalTransactionId or originalTransactionRequestId
2. THE Post_Auth_Request_Bean SHALL require transactionRequestId, amount, description
3. THE Post_Auth_Request_Bean SHALL support amount fields: orderAmount, tipAmount, taxAmount, surchargeAmount, pricingCurrency
4. THE Post_Auth_Request_Bean SHALL include optional fields: attach, notifyUrl
5. THE Post_Auth_Request_Bean SHALL validate that completion amount does not exceed original auth amount

### Requirement 6: Incremental Auth Transaction Request Bean

**User Story:** As a developer, I want a dedicated IncrementalAuthRequest class, so that I can increase pre-authorization amounts.

#### Acceptance Criteria

1. THE Incremental_Auth_Request_Bean SHALL require originalTransactionId or originalTransactionRequestId
2. THE Incremental_Auth_Request_Bean SHALL require transactionRequestId, amount, description
3. THE Incremental_Auth_Request_Bean SHALL support amount with orderAmount and pricingCurrency only
4. THE Incremental_Auth_Request_Bean SHALL include optional fields: attach, notifyUrl
5. THE Incremental_Auth_Request_Bean SHALL validate that increment amount is positive

### Requirement 7: Forced Auth Transaction Request Bean

**User Story:** As a developer, I want a dedicated ForcedAuthRequest class, so that I can process manual card entry transactions.

#### Acceptance Criteria

1. THE Forced_Auth_Request_Bean SHALL include required fields: referenceOrderId, transactionRequestId, amount, description
2. THE Forced_Auth_Request_Bean SHALL include optional fields: paymentMethod, attach, notifyUrl, timeExpire
3. THE Forced_Auth_Request_Bean SHALL support amount with orderAmount and pricingCurrency only
4. THE Forced_Auth_Request_Bean SHALL provide builder pattern for construction
5. THE Forced_Auth_Request_Bean SHALL validate required fields are present

### Requirement 8: Abort Transaction Request Bean

**User Story:** As a developer, I want a dedicated AbortRequest class, so that I can cancel pending transactions.

#### Acceptance Criteria

1. THE Abort_Request_Bean SHALL require originalTransactionId or originalTransactionRequestId
2. THE Abort_Request_Bean SHALL NOT require terminalSn (context-based operation)
3. THE Abort_Request_Bean SHALL include optional fields: description, attach
4. THE Abort_Request_Bean SHALL NOT include amount or referenceOrderId fields
5. THE Abort_Request_Bean SHALL validate that at least one original transaction identifier is provided

### Requirement 9: Tip Adjust Transaction Request Bean

**User Story:** As a developer, I want a dedicated TipAdjustRequest class, so that I can modify tip amounts on completed transactions.

#### Acceptance Criteria

1. THE Tip_Adjust_Request_Bean SHALL require originalTransactionId or originalTransactionRequestId
2. THE Tip_Adjust_Request_Bean SHALL require tipAmount only
3. THE Tip_Adjust_Request_Bean SHALL include optional attach field
4. THE Tip_Adjust_Request_Bean SHALL validate that tipAmount is non-negative
5. THE Tip_Adjust_Request_Bean SHALL NOT require appId, merchantId, terminalSn (context-based backend operation)

### Requirement 10: Batch Query Request Bean

**User Story:** As a developer, I want a dedicated BatchQueryRequest class, so that I can query batch settlement information.

#### Acceptance Criteria

1. THE Batch_Query_Request_Bean SHALL require appId, merchantId, terminalSn
2. THE Batch_Query_Request_Bean SHALL NOT include transaction-specific fields
3. THE Batch_Query_Request_Bean SHALL provide simple constructor and builder pattern
4. THE Batch_Query_Request_Bean SHALL validate required fields are present
5. THE Batch_Query_Request_Bean SHALL be lightweight with minimal fields

### Requirement 11: Batch Close Request Bean

**User Story:** As a developer, I want a dedicated BatchCloseRequest class, so that I can close batches for settlement.

#### Acceptance Criteria

1. THE Batch_Close_Request_Bean SHALL require appId, merchantId, transactionRequestId, terminalSn, channelCode
2. THE Batch_Close_Request_Bean SHALL include optional description field
3. THE Batch_Close_Request_Bean SHALL validate channelCode format
4. THE Batch_Close_Request_Bean SHALL provide builder pattern for construction
5. THE Batch_Close_Request_Bean SHALL ensure transactionRequestId uniqueness for idempotency

### Requirement 12: Common Request Infrastructure

**User Story:** As a developer, I want consistent patterns across all request beans, so that I can use them predictably and efficiently.

#### Acceptance Criteria

1. THE Request_Bean_System SHALL provide consistent builder patterns across all request types
2. THE Request_Bean_System SHALL include proper validation for required fields
3. THE Request_Bean_System SHALL support both Kotlin data classes and Java builder patterns
4. THE Request_Bean_System SHALL include comprehensive documentation with usage examples
5. THE Request_Bean_System SHALL maintain backward compatibility with existing PaymentRequest where possible