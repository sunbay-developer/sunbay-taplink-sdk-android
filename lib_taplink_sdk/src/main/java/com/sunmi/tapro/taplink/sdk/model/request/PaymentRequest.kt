package com.sunmi.tapro.taplink.sdk.model.request

import com.sunmi.tapro.taplink.sdk.enums.CardNetworkType
import com.sunmi.tapro.taplink.sdk.enums.PrintReceipt
import com.sunmi.tapro.taplink.sdk.enums.TransactionAction
import com.sunmi.tapro.taplink.sdk.model.common.AmountInfo
import com.sunmi.tapro.taplink.sdk.model.common.DeviceInfo
import com.sunmi.tapro.taplink.sdk.model.common.GoodsDetail
import com.sunmi.tapro.taplink.sdk.model.common.PaymentMethodInfo
import com.sunmi.tapro.taplink.sdk.model.common.SignatureConfig
import com.sunmi.tapro.taplink.sdk.model.common.StaffInfo
import com.sunmi.tapro.taplink.sdk.model.common.TipConfig
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
 * Field setters update this instance and return it for chaining. Use [copy] for
 * independent top-level values, and finish updates before request submission.
 * Serialize writes and do not mutate instances used as hash collection keys.
 *
 * @author Tapro Team
 * @since 2025-01-XX
 */
class PaymentRequest(
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
    action: String,

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
    referenceOrderId: String? = null,

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
    transactionRequestId: String? = null,


    /**
     * Transaction description (required)
     * Transaction description or product description
     */
    description: String? = null,

    // ========== Amount information ==========

    /**
     * Amount information (required)
     * Structured object containing detailed amount breakdown
     */
    amount: AmountInfo? = null,

    // ========== Payment method ==========

    /**
     * Payment method information (optional)
     * Specifies preferred payment method category and ID
     */
    paymentMethod: PaymentMethodInfo? = null,

    /**
     * Card network type (optional)
     * Only takes effect when paymentMethod.category is CARD; when not specified, the system will automatically identify.
     * Values: CREDIT, DEBIT
     */
    cardNetworkType: CardNetworkType? = null,

    // ========== Staff information ==========

    /**
     * Staff information (optional)
     * Information about the operator and tip recipient
     */
    staffInfo: StaffInfo? = null,

    // ========== Device information ==========

    /**
     * Device information (optional)
     * Details of the device initiating the transaction
     */
    deviceInfo: DeviceInfo? = null,

    // ========== Goods details ==========

    /**
     * Goods detail list (optional)
     * Detailed list of goods/services in the transaction
     */
    goodsDetail: List<GoodsDetail>? = null,

    // ========== Reference transaction fields ==========

    /**
     * Original transaction ID (required for refund/void/authorization completion)
     * References the original transaction
     */
    originalTransactionId: String? = null,

    /**
     * Original transaction request ID (alternative to originalTransactionId)
     * Uses the original transaction request ID for reference
     */
    originalTransactionRequestId: String? = null,

    // ========== Tip adjustment ==========

    /**
     * Tip amount (required for tip adjustment, unit: base currency unit)
     */
    tipAmount: BigDecimal? = null,

    // ========== Forced authorization specific fields ==========

    /**
     * Forced authorization code (required for forced authorization)
     * Authorization code used for forced authorization transactions
     */
    forcedAuthCode: String? = null,

    // ========== Additional fields ==========

    /**
     * Reason (optional for refund/void)
     * Reason for refund or void
     */
    reason: String? = null,

    /**
     * Additional data (optional)
     * Custom data attached to the transaction (JSON format recommended)
     */
    attach: String? = null,

    /**
     * Notification URL (optional)
     * URL to receive transaction result notifications
     */
    notifyUrl: String? = null,

    /**
     * Request timeout in milliseconds. When omitted or non-positive, TaplinkConfig.timeout is used.
     */
    requestTimeout: Long? = null,

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
     * - TOTAL: Only the batch total report will be printed (batch close only)
     * - DETAIL: Only the batch detail report will be printed (batch close only)
     */
    printReceipt: PrintReceipt? = PrintReceipt.AUTO,

    /**
     * Per-transaction signature configuration for SALE and AUTH.
     * If not set, Tapro uses its terminal-side default signature policy.
     */
    signatureConfig: SignatureConfig? = null,

    // ========== Tip configuration ==========

    /**
     * Tip configuration (optional).
     * Used by SALE and POST_AUTH to configure on-screen tip behavior.
     * Mutually exclusive with amount.tipAmount.
     */
    tipConfig: TipConfig? = null,
) {
    var action: String = action
        private set

    var referenceOrderId: String? = referenceOrderId
        private set

    var transactionRequestId: String? = transactionRequestId
        private set

    var description: String? = description
        private set

    var amount: AmountInfo? = amount
        private set

    var paymentMethod: PaymentMethodInfo? = paymentMethod
        private set

    var cardNetworkType: CardNetworkType? = cardNetworkType
        private set

    var staffInfo: StaffInfo? = staffInfo
        private set

    var deviceInfo: DeviceInfo? = deviceInfo
        private set

    var goodsDetail: List<GoodsDetail>? = goodsDetail
        private set

    var originalTransactionId: String? = originalTransactionId
        private set

    var originalTransactionRequestId: String? = originalTransactionRequestId
        private set

    var tipAmount: BigDecimal? = tipAmount
        private set

    var forcedAuthCode: String? = forcedAuthCode
        private set

    var reason: String? = reason
        private set

    var attach: String? = attach
        private set

    var notifyUrl: String? = notifyUrl
        private set

    var requestTimeout: Long? = requestTimeout
        private set

    var printReceipt: PrintReceipt? = printReceipt
        private set

    var signatureConfig: SignatureConfig? = signatureConfig
        private set

    var tipConfig: TipConfig? = tipConfig
        private set

    // ========== Chain call methods for basic transaction fields ==========

    /**
     * Chain call: Set transaction type (using enum)
     * Recommended to use this method for type safety
     */
    fun setAction(action: TransactionAction): PaymentRequest = apply { this.action = action.value }

    /**
     * Chain call: Set transaction type (using string)
     * Preserved for backward compatibility, recommended to use TransactionAction enum
     */
    fun setAction(action: String): PaymentRequest = apply { this.action = action }

    /**
     * Chain call: Set merchant order ID / reference order ID
     */
    fun setReferenceOrderId(referenceOrderId: String): PaymentRequest = apply { this.referenceOrderId = referenceOrderId }

    /**
     * Chain call: Set merchant order ID (compatible with old method name)
     * @deprecated Use setReferenceOrderId instead
     */
    @Deprecated("Use setReferenceOrderId instead", ReplaceWith("setReferenceOrderId(merchantOrderNo)"))
    fun setMerchantOrderNo(merchantOrderNo: String): PaymentRequest = apply { this.referenceOrderId = merchantOrderNo }

    /**
     * Chain call: Set transaction request ID
     */
    fun setTransactionRequestId(transactionRequestId: String): PaymentRequest = apply { this.transactionRequestId = transactionRequestId }

    /**
     * Chain call: Set transaction description
     */
    fun setDescription(description: String): PaymentRequest = apply { this.description = description }

    // ========== Chain call methods for amount information ==========

    /**
     * Chain call: Set amount information
     */
    fun setAmount(amount: AmountInfo): PaymentRequest = apply { this.amount = amount }

    // ========== Chain call methods for payment method ==========

    /**
     * Chain call: Set payment method information
     */
    fun setPaymentMethod(paymentMethod: PaymentMethodInfo): PaymentRequest = apply { this.paymentMethod = paymentMethod }

    /**
     * Chain call: Set card network type.
     * Only takes effect when paymentMethod.category is CARD; when not specified, the system will automatically identify.
     */
    fun setCardNetworkType(cardNetworkType: CardNetworkType): PaymentRequest = apply { this.cardNetworkType = cardNetworkType }

    // ========== Chain call methods for staff information ==========

    /**
     * Chain call: Set staff information
     */
    fun setStaffInfo(staffInfo: StaffInfo): PaymentRequest = apply { this.staffInfo = staffInfo }

    // ========== Chain call methods for device information ==========

    /**
     * Chain call: Set device information
     */
    fun setDeviceInfo(deviceInfo: DeviceInfo): PaymentRequest = apply { this.deviceInfo = deviceInfo }

    // ========== Chain call methods for goods details ==========

    /**
     * Chain call: Set goods detail list
     */
    fun setGoodsDetail(goodsDetail: List<GoodsDetail>): PaymentRequest = apply { this.goodsDetail = goodsDetail }

    // ========== Chain call methods for reference transaction fields ==========

    /**
     * Chain call: Set original transaction ID
     */
    fun setOriginalTransactionId(originalTransactionId: String): PaymentRequest =
        apply { this.originalTransactionId = originalTransactionId }

    /**
     * Chain call: Set original transaction request ID
     */
    fun setOriginalTransactionRequestId(originalTransactionRequestId: String): PaymentRequest =
        apply { this.originalTransactionRequestId = originalTransactionRequestId }

    // ========== Chain call methods for tip adjustment ==========

    /**
     * Chain call: Set tip amount
     */
    fun setTipAmount(tipAmount: BigDecimal): PaymentRequest = apply { this.tipAmount = tipAmount }

    // ========== Chain call methods for forced authorization ==========

    /**
     * Chain call: Set forced authorization code
     */
    fun setForcedAuthCode(forcedAuthCode: String): PaymentRequest = apply { this.forcedAuthCode = forcedAuthCode }

    // ========== Chain call methods for additional fields ==========

    /**
     * Chain call: Set reason
     */
    fun setReason(reason: String): PaymentRequest = apply { this.reason = reason }

    /**
     * Chain call: Set additional data
     */
    fun setAttach(attach: String): PaymentRequest = apply { this.attach = attach }

    /**
     * Chain call: Set notification URL
     */
    fun setNotifyUrl(notifyUrl: String): PaymentRequest = apply { this.notifyUrl = notifyUrl }

    /**
     * Chain call: Set transaction timeout in milliseconds
     */
    fun setRequestTimeout(requestTimeout: Long): PaymentRequest = apply { this.requestTimeout = requestTimeout }

    // ========== Chain call methods for receipt configuration ==========

    /**
     * Chain call: Set print receipt
     */
    fun setPrintReceipt(printReceipt: PrintReceipt): PaymentRequest = apply { this.printReceipt = printReceipt }

    /**
     * Chain call: Set per-transaction signature configuration.
     */
    fun setSignatureConfig(signatureConfig: SignatureConfig): PaymentRequest =
        apply { this.signatureConfig = signatureConfig }

    // ========== Chain call methods for tip configuration ==========

    /**
     * Chain call: Set tip configuration
     */
    fun setTipConfig(tipConfig: TipConfig): PaymentRequest = apply { this.tipConfig = tipConfig }

    /**
     * Get transaction type enum
     *
     * @return TransactionAction? Corresponding enum, returns null if unrecognized
     */
    fun getActionEnum(): TransactionAction? = TransactionAction.fromValue(action)



    /** Creates an independent shallow copy; setters mutate this instance. */
    fun copy(
        action: String = this.action,
        referenceOrderId: String? = this.referenceOrderId,
        transactionRequestId: String? = this.transactionRequestId,
        description: String? = this.description,
        amount: AmountInfo? = this.amount,
        paymentMethod: PaymentMethodInfo? = this.paymentMethod,
        cardNetworkType: CardNetworkType? = this.cardNetworkType,
        staffInfo: StaffInfo? = this.staffInfo,
        deviceInfo: DeviceInfo? = this.deviceInfo,
        goodsDetail: List<GoodsDetail>? = this.goodsDetail,
        originalTransactionId: String? = this.originalTransactionId,
        originalTransactionRequestId: String? = this.originalTransactionRequestId,
        tipAmount: BigDecimal? = this.tipAmount,
        forcedAuthCode: String? = this.forcedAuthCode,
        reason: String? = this.reason,
        attach: String? = this.attach,
        notifyUrl: String? = this.notifyUrl,
        requestTimeout: Long? = this.requestTimeout,
        printReceipt: PrintReceipt? = this.printReceipt,
        signatureConfig: SignatureConfig? = this.signatureConfig,
        tipConfig: TipConfig? = this.tipConfig
    ): PaymentRequest = PaymentRequest(
        action = action,
        referenceOrderId = referenceOrderId,
        transactionRequestId = transactionRequestId,
        description = description,
        amount = amount,
        paymentMethod = paymentMethod,
        cardNetworkType = cardNetworkType,
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
        signatureConfig = signatureConfig,
        tipConfig = tipConfig
    )

    operator fun component1(): String = action
    operator fun component2(): String? = referenceOrderId
    operator fun component3(): String? = transactionRequestId
    operator fun component4(): String? = description
    operator fun component5(): AmountInfo? = amount
    operator fun component6(): PaymentMethodInfo? = paymentMethod
    operator fun component7(): CardNetworkType? = cardNetworkType
    operator fun component8(): StaffInfo? = staffInfo
    operator fun component9(): DeviceInfo? = deviceInfo
    operator fun component10(): List<GoodsDetail>? = goodsDetail
    operator fun component11(): String? = originalTransactionId
    operator fun component12(): String? = originalTransactionRequestId
    operator fun component13(): BigDecimal? = tipAmount
    operator fun component14(): String? = forcedAuthCode
    operator fun component15(): String? = reason
    operator fun component16(): String? = attach
    operator fun component17(): String? = notifyUrl
    operator fun component18(): Long? = requestTimeout
    operator fun component19(): PrintReceipt? = printReceipt
    operator fun component20(): SignatureConfig? = signatureConfig
    operator fun component21(): TipConfig? = tipConfig

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PaymentRequest) return false
        return action == other.action &&
            referenceOrderId == other.referenceOrderId &&
            transactionRequestId == other.transactionRequestId &&
            description == other.description &&
            amount == other.amount &&
            paymentMethod == other.paymentMethod &&
            cardNetworkType == other.cardNetworkType &&
            staffInfo == other.staffInfo &&
            deviceInfo == other.deviceInfo &&
            goodsDetail == other.goodsDetail &&
            originalTransactionId == other.originalTransactionId &&
            originalTransactionRequestId == other.originalTransactionRequestId &&
            tipAmount == other.tipAmount &&
            forcedAuthCode == other.forcedAuthCode &&
            reason == other.reason &&
            attach == other.attach &&
            notifyUrl == other.notifyUrl &&
            requestTimeout == other.requestTimeout &&
            printReceipt == other.printReceipt &&
            signatureConfig == other.signatureConfig &&
            tipConfig == other.tipConfig
    }

    override fun hashCode(): Int {
        var result = action.hashCode()
        result = 31 * result + (referenceOrderId?.hashCode() ?: 0)
        result = 31 * result + (transactionRequestId?.hashCode() ?: 0)
        result = 31 * result + (description?.hashCode() ?: 0)
        result = 31 * result + (amount?.hashCode() ?: 0)
        result = 31 * result + (paymentMethod?.hashCode() ?: 0)
        result = 31 * result + (cardNetworkType?.hashCode() ?: 0)
        result = 31 * result + (staffInfo?.hashCode() ?: 0)
        result = 31 * result + (deviceInfo?.hashCode() ?: 0)
        result = 31 * result + (goodsDetail?.hashCode() ?: 0)
        result = 31 * result + (originalTransactionId?.hashCode() ?: 0)
        result = 31 * result + (originalTransactionRequestId?.hashCode() ?: 0)
        result = 31 * result + (tipAmount?.hashCode() ?: 0)
        result = 31 * result + (forcedAuthCode?.hashCode() ?: 0)
        result = 31 * result + (reason?.hashCode() ?: 0)
        result = 31 * result + (attach?.hashCode() ?: 0)
        result = 31 * result + (notifyUrl?.hashCode() ?: 0)
        result = 31 * result + (requestTimeout?.hashCode() ?: 0)
        result = 31 * result + (printReceipt?.hashCode() ?: 0)
        result = 31 * result + (signatureConfig?.hashCode() ?: 0)
        result = 31 * result + (tipConfig?.hashCode() ?: 0)
        return result
    }

    override fun toString(): String =
        "PaymentRequest(action=$action, referenceOrderId=$referenceOrderId, transactionRequestId=$transactionRequestId, description=$description, amount=$amount, paymentMethod=$paymentMethod, cardNetworkType=$cardNetworkType, staffInfo=$staffInfo, deviceInfo=$deviceInfo, goodsDetail=$goodsDetail, originalTransactionId=$originalTransactionId, originalTransactionRequestId=$originalTransactionRequestId, tipAmount=$tipAmount, forcedAuthCode=$forcedAuthCode, reason=$reason, attach=$attach, notifyUrl=$notifyUrl, requestTimeout=$requestTimeout, printReceipt=$printReceipt, signatureConfig=$signatureConfig, tipConfig=$tipConfig)"

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
        private var cardNetworkType: CardNetworkType? = null
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
        private var printReceipt: PrintReceipt? = PrintReceipt.AUTO
        private var signatureConfig: SignatureConfig? = null
        private var tipConfig: TipConfig? = null

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
        /**
         * Set card network type.
         * Only takes effect when paymentMethod.category is CARD; when not specified, the system will automatically identify.
         */
        fun setCardNetworkType(cardNetworkType: CardNetworkType) = apply { this.cardNetworkType = cardNetworkType }
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
        fun setSignatureConfig(signatureConfig: SignatureConfig) =
            apply { this.signatureConfig = signatureConfig }
        fun setTipConfig(tipConfig: TipConfig) = apply { this.tipConfig = tipConfig }

        /**
         * Build PaymentRequest object
         */
        fun build(): PaymentRequest {
            require(action.isNotEmpty()) { "action is required" }

            // referenceOrderId is only required for sale/auth/refund transactions
            val actionsRequiringOrderId = setOf(
                TransactionAction.SALE.value,
                TransactionAction.AUTH.value,
                TransactionAction.FORCED_AUTH.value,
                TransactionAction.REFUND.value
            )
            if (action in actionsRequiringOrderId) {
                require(referenceOrderId.isNotEmpty()) { "referenceOrderId is required for action: $action" }
            }

            require(transactionRequestId.isNotEmpty()) { "transactionRequestId is required" }

            return PaymentRequest(
                action = action,
                referenceOrderId = referenceOrderId,
                transactionRequestId = transactionRequestId,
                description = description,
                amount = amount,
                paymentMethod = paymentMethod,
                cardNetworkType = cardNetworkType,
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
                signatureConfig = signatureConfig,
                tipConfig = tipConfig,
            )
        }
    }
}
