package com.sunmi.tapro.taplink.sdk.model.request

import com.sunmi.tapro.taplink.sdk.enums.PrintReceipt
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
 * Provides comprehensive field support for various transaction types
 *
 * **Recommended usage with enum:**
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
 * - transactionRequestId is used for idempotency control; requests with the same value are treated as the same transaction
 * - For temporary errors (network timeout, system busy), you can retry with the same transactionRequestId
 * - After a clear failure or parameter modification, you must use a new transactionRequestId
 *
 * @author TaPro Team
 * @since 2025-01-XX
 */
data class PaymentRequest(
    // ========== Basic transaction fields ==========

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
     * - QUERY: Transaction query
     */
    val action: String,

    /**
     * Merchant order ID / Reference order ID (required, 6-32 characters)
     * Unique identifier for the order in the merchant system
     *
     * Notes:
     * - Used to identify orders or business records in the merchant business system
     * - One order can be associated with multiple transactions (e.g., original transaction, refund, tip adjustment, etc.)
     * - Multiple transactions for the same order use the same referenceOrderId
     * - Can use the same value as transactionRequestId (simplified scenario)
     */
    val referenceOrderId: String? = null,

    /**
     * Transaction request ID (required, for idempotency)
     * Unique identifier for this specific transaction request
     *
     * Idempotency notes:
     * - Requests with the same transactionRequestId are treated as the same transaction
     * - For temporary errors (network timeout, system busy), you can retry with the same transactionRequestId
     * - After a clear failure or parameter modification, you must use a new transactionRequestId
     * - Different transaction types for the same order must use different transactionRequestId
     *
     * Can use the same value as referenceOrderId (simplified scenario)
     */
    val transactionRequestId: String? = null,


    /**
     * Transaction description (required)
     * Transaction description or product description
     */
    val description: String? = null,

    // ========== Amount information ==========

    /**
     * Amount information (required)
     * Structured object containing detailed amount breakdown
     */
    val amount: AmountInfo? = null,

    // ========== Payment method ==========

    /**
     * Payment method information (optional)
     * Specifies preferred payment method category and ID
     */
    val paymentMethod: PaymentMethodInfo? = null,

    // ========== Staff information ==========

    /**
     * Staff information (optional)
     * Information about the operator and tip recipient
     */
    val staffInfo: StaffInfo? = null,

    // ========== Device information ==========

    /**
     * Device information (optional)
     * Details of the device initiating the transaction
     */
    val deviceInfo: DeviceInfo? = null,

    // ========== Goods details ==========

    /**
     * Goods detail list (optional)
     * Detailed list of goods/services in the transaction
     */
    val goodsDetail: List<GoodsDetail>? = null,

    // ========== Reference transaction fields ==========

    /**
     * Original transaction ID (required for refund/void/authorization completion)
     * References the original transaction
     */
    val originalTransactionId: String? = null,

    /**
     * Original transaction request ID (alternative to originalTransactionId)
     * Uses the original transaction request ID for reference
     */
    val originalTransactionRequestId: String? = null,

    // ========== Tip adjustment ==========

    /**
     * Tip amount (required for tip adjustment, unit: base currency unit)
     */
    val tipAmount: BigDecimal? = null,

    // ========== Forced authorization specific fields ==========

    /**
     * Forced authorization code (required for forced authorization)
     * Authorization code used for forced authorization transactions
     */
    val forcedAuthCode: String? = null,

    // ========== Additional fields ==========

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
     * Request timeout (unit: seconds)
     */
    val requestTimeout: Long? = null,

    // ========== Receipt configuration ==========

    /**
     * Receipt type (optional, default: NONE)
     * Specifies which receipt copies should be printed for the transaction
     *
     * Supported types:
     * - NONE: No receipt will be printed (default)
     * - MERCHANT: Only the merchant copy will be printed
     * - CUSTOMER: Only the customer copy will be printed
     * - BOTH: Both merchant and customer copies will be printed
     */
    val printReceipt: PrintReceipt = PrintReceipt.NONE,

    // ========== Tip configuration ==========
) {
    // ========== Chain call methods for basic transaction fields ==========

    /**
     * Chain call: Set transaction type (using enum)
     * Recommended to use this method for type safety
     */
    fun setAction(action: TransactionAction): PaymentRequest = copy(action = action.value)

    /**
     * Chain call: Set transaction type (using string)
     * Preserved for backward compatibility, recommended to use TransactionAction enum
     */
    fun setAction(action: String): PaymentRequest = copy(action = action)

    /**
     * Chain call: Set merchant order ID / reference order ID
     */
    fun setReferenceOrderId(referenceOrderId: String): PaymentRequest = copy(referenceOrderId = referenceOrderId)

    /**
     * Chain call: Set merchant order ID (compatible with old method name)
     * @deprecated Use setReferenceOrderId instead
     */
    @Deprecated("Use setReferenceOrderId instead", ReplaceWith("setReferenceOrderId(merchantOrderNo)"))
    fun setMerchantOrderNo(merchantOrderNo: String): PaymentRequest = copy(referenceOrderId = merchantOrderNo)

    /**
     * Chain call: Set transaction request ID
     */
    fun setTransactionRequestId(transactionRequestId: String): PaymentRequest = copy(transactionRequestId = transactionRequestId)

    /**
     * Chain call: Set transaction description
     */
    fun setDescription(description: String): PaymentRequest = copy(description = description)

    // ========== Chain call methods for amount information ==========

    /**
     * Chain call: Set amount information
     */
    fun setAmount(amount: AmountInfo): PaymentRequest = copy(amount = amount)

    // ========== Chain call methods for payment method ==========

    /**
     * Chain call: Set payment method information
     */
    fun setPaymentMethod(paymentMethod: PaymentMethodInfo): PaymentRequest = copy(paymentMethod = paymentMethod)

    // ========== Chain call methods for staff information ==========

    /**
     * Chain call: Set staff information
     */
    fun setStaffInfo(staffInfo: StaffInfo): PaymentRequest = copy(staffInfo = staffInfo)

    // ========== Chain call methods for device information ==========

    /**
     * Chain call: Set device information
     */
    fun setDeviceInfo(deviceInfo: DeviceInfo): PaymentRequest = copy(deviceInfo = deviceInfo)

    // ========== Chain call methods for goods details ==========

    /**
     * Chain call: Set goods detail list
     */
    fun setGoodsDetail(goodsDetail: List<GoodsDetail>): PaymentRequest = copy(goodsDetail = goodsDetail)

    // ========== Chain call methods for reference transaction fields ==========

    /**
     * Chain call: Set original transaction ID
     */
    fun setOriginalTransactionId(originalTransactionId: String): PaymentRequest =
        copy(originalTransactionId = originalTransactionId)

    /**
     * Chain call: Set original transaction request ID
     */
    fun setOriginalTransactionRequestId(originalTransactionRequestId: String): PaymentRequest =
        copy(originalTransactionRequestId = originalTransactionRequestId)

    // ========== Chain call methods for tip adjustment ==========

    /**
     * Chain call: Set tip amount
     */
    fun setTipAmount(tipAmount: BigDecimal): PaymentRequest = copy(tipAmount = tipAmount)

    // ========== Chain call methods for forced authorization ==========

    /**
     * Chain call: Set forced authorization code
     */
    fun setForcedAuthCode(forcedAuthCode: String): PaymentRequest = copy(forcedAuthCode = forcedAuthCode)

    // ========== Chain call methods for additional fields ==========

    /**
     * Chain call: Set reason
     */
    fun setReason(reason: String): PaymentRequest = copy(reason = reason)

    /**
     * Chain call: Set additional data
     */
    fun setAttach(attach: String): PaymentRequest = copy(attach = attach)

    /**
     * Chain call: Set notification URL
     */
    fun setNotifyUrl(notifyUrl: String): PaymentRequest = copy(notifyUrl = notifyUrl)

    /**
     * Chain call: Set transaction timeout
     */
    fun setRequestTimeout(requestTimeout: Long): PaymentRequest = copy(requestTimeout = requestTimeout)

    // ========== Chain call methods for receipt configuration ==========

    /**
     * Chain call: Set print receipt
     */
    fun setPrintReceipt(printReceipt: PrintReceipt): PaymentRequest = copy(printReceipt = printReceipt)

    // ========== Chain call methods for tip configuration ==========

    /**
     * Get transaction type enum
     *
     * @return TransactionAction? Corresponding enum, returns null if unrecognized
     */
    fun getActionEnum(): TransactionAction? = TransactionAction.fromValue(action)
    

    companion object {
        /**
         * Create a new PaymentRequest builder
         *
         * Usage example (recommended with enum):
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
     * Provides a more flexible construction method, especially suitable for Java calls
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
        private var requestTimeout: Long? = null
        private var printReceipt: PrintReceipt = PrintReceipt.NONE

        /**
         * Set transaction type (using enum, recommended)
         */
        fun setAction(action: TransactionAction) = apply { this.action = action.value }

        /**
         * Set transaction type (using string, backward compatibility)
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
        fun setRequestTimeout(requestTimeout: Long) = apply { this.requestTimeout = requestTimeout }
        fun setPrintReceipt(printReceipt: PrintReceipt) = apply { this.printReceipt = printReceipt }

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
                requestTimeout = requestTimeout,
                printReceipt = printReceipt,
            )
        }
    }
}

