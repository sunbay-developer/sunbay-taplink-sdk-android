package com.sunmi.tapro.taplink.sdk.model.request

import com.sunmi.tapro.taplink.sdk.enums.TransactionAction
import com.sunmi.tapro.taplink.sdk.model.common.AmountInfo
import com.sunmi.tapro.taplink.sdk.model.common.DeviceInfo
import com.sunmi.tapro.taplink.sdk.model.common.GoodsDetail
import com.sunmi.tapro.taplink.sdk.model.common.PaymentMethodInfo
import com.sunmi.tapro.taplink.sdk.model.common.StaffInfo
import java.math.BigDecimal

/**
 * Payment Request Class
 *
 * Comprehensive field support for various transaction types
 *
 * **Recommended using enums:**
 * ```kotlin
 * val request = PaymentRequest(
 *     action = TransactionAction.SALE,
 *     referenceOrderId = "ORDER001",
 *     transactionRequestId = "TXN001",
 *     amount = amountInfo,
 *     description = "Product purchase"
 * )
 * ```
 *
 * **Java usage instructions:**
 * ```java
 * PaymentRequest request = PaymentRequest.builder()
 *     .setAction(TransactionAction.SALE)
 *     .setReferenceOrderId("ORDER001")
 *     .setTransactionRequestId("TXN001")
 *     .setAmount(amountInfo)
 *     .setDescription("Product purchase")
 *     .build();
 * ```
 *
 * **Important notes:**
 * - referenceOrderId and transactionRequestId can use the same value
 * - transactionRequestId is used for idempotency control, requests with the same value are treated as the same transaction
 * - For temporary errors (network timeout, system busy), you can retry with the same transactionRequestId
 * - After explicit failure or parameter modification, you must use a new transactionRequestId
 *
 * @author TaPro Team
 * @since 2025-01-XX
 */
data class PaymentRequest(
    // ========== Basic Transaction Fields ==========

    /**
     * Transaction type (required)
     * Recommended to use TransactionAction enum, also supports string (backward compatibility)
     * 
     * Supported types:
     * - SALE: Sale transaction
     * - REFUND: Refund transaction
     * - VOID: Void transaction
     * - AUTH: Pre-authorization
     * - POST_AUTH: Pre-authorization completion
     * - INCREMENT_AUTH: Pre-authorization increment
     * - FORCED_AUTH: Forced authorization
     * - TIP_ADJUST: Tip adjustment
     * - BATCH_CLOSE: Batch settlement
     * - QUERY: Query transaction
     */
    val action: String,

    /**
     * Merchant order number/reference order number (required, 6-32 characters)
     * Unique identifier for orders in merchant system
     *
     * Description:
     * - Used to identify orders or business records in merchant business system
     * - One order can be associated with multiple transactions (such as original transaction, refund, tip adjustment, etc.)
     * - Multiple transactions for the same order use the same referenceOrderId
     * - Can use the same value as transactionRequestId (simplified scenario)
     */
    val referenceOrderId: String? = null,

    /**
     * Transaction request ID (required, for idempotency)
     * Unique identifier for this specific transaction request
     *
     * Idempotency description:
     * - Requests with the same transactionRequestId are treated as the same transaction
     * - For temporary errors (network timeout, system busy), you can retry with the same transactionRequestId
     * - After explicit failure or parameter modification, you must use a new transactionRequestId
     * - Different transaction types for the same order must use different transactionRequestIds
     *
     * Can use the same value as referenceOrderId (simplified scenario)
     */
    val transactionRequestId: String? = null,


    /**
     * Transaction description (required)
     * Transaction description or product description
     */
    val description: String? = null,

    // ========== Amount Information ==========

    /**
     * Amount information (required)
     * Structured object containing detailed amount breakdown
     */
    val amount: AmountInfo? = null,

    // ========== Payment Method ==========

    /**
     * Payment method information (optional)
     * Specify preferred payment method category and ID
     */
    val paymentMethod: PaymentMethodInfo? = null,

    // ========== Staff Information ==========

    /**
     * Staff information (optional)
     * Information about operator and tip recipient
     */
    val staffInfo: StaffInfo? = null,

    // ========== Device Information ==========

    /**
     * Device information (optional)
     * Details of the device initiating the transaction
     */
    val deviceInfo: DeviceInfo? = null,

    // ========== Product Details ==========

    /**
     * Product details list (optional)
     * Detailed list of products/services in the transaction
     */
    val goodsDetail: List<GoodsDetail>? = null,

    // ========== Reference Transaction Fields ==========

    /**
     * Original transaction ID (required for refund/void/authorization completion)
     * Reference to original transaction
     */
    val originalTransactionId: String? = null,

    /**
     * Original transaction request ID (alternative to originalTransactionId)
     * Use original transaction request ID for reference
     */
    val originalTransactionRequestId: String? = null,

    // ========== Tip Adjustment ==========

    /**
     * Tip amount (required for tip adjustment, unit: base currency unit)
     */
    val tipAmount: BigDecimal? = null,

    // ========== Forced Authorization Specific Fields ==========

    /**
     * Forced authorization code (required for forced authorization)
     * Authorization code used for forced authorization transactions
     */
    val forcedAuthCode: String? = null,

    // ========== Additional Fields ==========

    /**
     * Reason (optional for refund/void)
     * Reason for refund or void
     */
    val reason: String? = null,

    /**
     * Additional data (optional)
     * Custom data attached to the transaction (JSON format recommended)
     */
    val attach: String? = null,

    /**
     * Notification URL (optional)
     * URL to receive transaction result notifications
     */
    val notifyUrl: String? = null,

    /**
     * Transaction expiration time (optional, ISO 8601 format)
     * Transaction expiration time (e.g., "2024-11-24T10:30:00+08:00")
     */
    val timeExpire: String? = null
) {
    // ========== Chained Method Calls for Basic Transaction Fields ==========

    /**
     * Chained call: Set transaction type (using enum)
     * Recommended method, type-safe
     */
    fun setAction(action: TransactionAction): PaymentRequest = copy(action = action.value)

    /**
     * Chained call: Set transaction type (using string)
     * Kept for backward compatibility, TransactionAction enum recommended
     */
    fun setAction(action: String): PaymentRequest = copy(action = action)

    /**
     * Chained call: Set merchant order number/reference order ID
     */
    fun setReferenceOrderId(referenceOrderId: String): PaymentRequest = copy(referenceOrderId = referenceOrderId)

    /**
     * Chained call: Set merchant order number (compatible with old method name)
     * @deprecated Use setReferenceOrderId instead
     */
    @Deprecated("Use setReferenceOrderId instead", ReplaceWith("setReferenceOrderId(merchantOrderNo)"))
    fun setMerchantOrderNo(merchantOrderNo: String): PaymentRequest = copy(referenceOrderId = merchantOrderNo)

    /**
     * Chained call: Set transaction request ID
     */
    fun setTransactionRequestId(transactionRequestId: String): PaymentRequest = copy(transactionRequestId = transactionRequestId)

    /**
     * Chained call: Set transaction description
     */
    fun setDescription(description: String): PaymentRequest = copy(description = description)

    // ========== Chained Method Calls for Amount Information ==========

    /**
     * Chained call: Set amount information
     */
    fun setAmount(amount: AmountInfo): PaymentRequest = copy(amount = amount)

    // ========== Chained Method Calls for Payment Method ==========

    /**
     * Chained call: Set payment method information
     */
    fun setPaymentMethod(paymentMethod: PaymentMethodInfo): PaymentRequest = copy(paymentMethod = paymentMethod)

    // ========== Chained Method Calls for Staff Information ==========

    /**
     * Chained call: Set staff information
     */
    fun setStaffInfo(staffInfo: StaffInfo): PaymentRequest = copy(staffInfo = staffInfo)

    // ========== Chained Method Calls for Device Information ==========

    /**
     * Chained call: Set device information
     */
    fun setDeviceInfo(deviceInfo: DeviceInfo): PaymentRequest = copy(deviceInfo = deviceInfo)

    // ========== Chained Method Calls for Goods Details ==========

    /**
     * Chained call: Set goods details list
     */
    fun setGoodsDetail(goodsDetail: List<GoodsDetail>): PaymentRequest = copy(goodsDetail = goodsDetail)

    // ========== Chained Method Calls for Reference Transaction Fields ==========

    /**
     * Chained call: Set original transaction ID
     */
    fun setOriginalTransactionId(originalTransactionId: String): PaymentRequest =
        copy(originalTransactionId = originalTransactionId)

    /**
     * Chained call: Set original transaction request ID
     */
    fun setOriginalTransactionRequestId(originalTransactionRequestId: String): PaymentRequest =
        copy(originalTransactionRequestId = originalTransactionRequestId)

    // ========== Chained Method Calls for Tip Adjustment ==========

    /**
     * Chained call: Set tip amount
     */
    fun setTipAmount(tipAmount: BigDecimal): PaymentRequest = copy(tipAmount = tipAmount)

    // ========== Chained Method Calls for Forced Authorization ==========

    /**
     * Chained call: Set forced authorization code
     */
    fun setForcedAuthCode(forcedAuthCode: String): PaymentRequest = copy(forcedAuthCode = forcedAuthCode)

    // ========== Chained Method Calls for Additional Fields ==========

    /**
     * Chained call: Set reason
     */
    fun setReason(reason: String): PaymentRequest = copy(reason = reason)

    /**
     * Chained call: Set additional data
     */
    fun setAttach(attach: String): PaymentRequest = copy(attach = attach)

    /**
     * Chained call: Set notification URL
     */
    fun setNotifyUrl(notifyUrl: String): PaymentRequest = copy(notifyUrl = notifyUrl)

    /**
     * Chained call: Set transaction expiration time
     */
    fun setTimeExpire(timeExpire: String): PaymentRequest = copy(timeExpire = timeExpire)

    /**
     * Get transaction type enum
     * 
     * @return TransactionAction? Corresponding enum, returns null if not recognized
     */
    fun getActionEnum(): TransactionAction? = TransactionAction.fromValue(action)

    companion object {
        /**
         * Create a new PaymentRequest builder
         *
         * Usage example (enum recommended):
         * ```kotlin
         * val request = PaymentRequest.builder()
         *     .setAction(TransactionAction.SALE)
         *     .setReferenceOrderId("ORDER001")
         *     .setTransactionRequestId("TXN001")
         *     .setAmount(amountInfo)
         *     .setDescription("Product purchase")
         *     .build()
         * ```
         */
        @JvmStatic
        fun builder(): Builder = Builder()
    }

    /**
     * PaymentRequest Builder
     *
     * Provides more flexible construction method, especially suitable for Java calls
     */
    class Builder {
        private var action: String = ""
        private var referenceOrderId: String = ""
        private var transactionRequestId: String = ""
        private var description: String? = null
        private var amount: AmountInfo? = null
        private var paymentMethod: PaymentMethodInfo? = null
        private var staffInfo: StaffInfo? = null
        private var deviceInfo: DeviceInfo? = null
        private var goodsDetail: List<GoodsDetail>? = null
        private var originalTransactionId: String? = null
        private var originalTransactionRequestId: String? = null
        private var tipAmount: BigDecimal? = null
        private var forcedAuthCode: String? = null
        private var reason: String? = null
        private var attach: String? = null
        private var notifyUrl: String? = null
        private var timeExpire: String? = null

        /**
         * Set transaction type (using enum, recommended)
         */
        fun setAction(action: TransactionAction) = apply { this.action = action.value }

        /**
         * Set transaction type (using string, backward compatible)
         */
        fun setAction(action: String) = apply { this.action = action }
        fun setReferenceOrderId(referenceOrderId: String) = apply { this.referenceOrderId = referenceOrderId }
        fun setTransactionRequestId(transactionRequestId: String) = apply { this.transactionRequestId = transactionRequestId }
        fun setDescription(description: String) = apply { this.description = description }
        fun setAmount(amount: AmountInfo) = apply { this.amount = amount }
        fun setPaymentMethod(paymentMethod: PaymentMethodInfo) = apply { this.paymentMethod = paymentMethod }
        fun setStaffInfo(staffInfo: StaffInfo) = apply { this.staffInfo = staffInfo }
        fun setDeviceInfo(deviceInfo: DeviceInfo) = apply { this.deviceInfo = deviceInfo }
        fun setGoodsDetail(goodsDetail: List<GoodsDetail>) = apply { this.goodsDetail = goodsDetail }
        fun setOriginalTransactionId(originalTransactionId: String) = apply { this.originalTransactionId = originalTransactionId }
        fun setOriginalTransactionRequestId(originalTransactionRequestId: String) =
            apply { this.originalTransactionRequestId = originalTransactionRequestId }

        fun setTipAmount(tipAmount: BigDecimal) = apply { this.tipAmount = tipAmount }
        fun setForcedAuthCode(forcedAuthCode: String) = apply { this.forcedAuthCode = forcedAuthCode }
        fun setReason(reason: String) = apply { this.reason = reason }
        fun setAttach(attach: String) = apply { this.attach = attach }
        fun setNotifyUrl(notifyUrl: String) = apply { this.notifyUrl = notifyUrl }
        fun setTimeExpire(timeExpire: String) = apply { this.timeExpire = timeExpire }

        /**
         * Build PaymentRequest object
         */
        fun build(): PaymentRequest {
            require(action.isNotEmpty()) { "action is required" }
            require(referenceOrderId.isNotEmpty()) { "referenceOrderId is required" }
            require(transactionRequestId.isNotEmpty()) { "transactionRequestId is required" }

            return PaymentRequest(
                action = action,
                referenceOrderId = referenceOrderId,
                transactionRequestId = transactionRequestId,
                description = description,
                amount = amount,
                paymentMethod = paymentMethod,
                staffInfo = staffInfo,
                deviceInfo = deviceInfo,
                goodsDetail = goodsDetail,
                originalTransactionId = originalTransactionId,
                originalTransactionRequestId = originalTransactionRequestId,
                tipAmount = tipAmount,
                forcedAuthCode = forcedAuthCode,
                reason = reason,
                attach = attach,
                notifyUrl = notifyUrl,
                timeExpire = timeExpire
            )
        }
    }
}

