package com.sunmi.tapro.taplink.sdk.adapter

import com.sunmi.tapro.taplink.sdk.enums.TransactionAction
import com.sunmi.tapro.taplink.sdk.model.common.AmountInfo
import com.sunmi.tapro.taplink.sdk.model.request.PaymentRequest
import com.sunmi.tapro.taplink.sdk.model.request.transaction.*
import com.sunmi.tapro.taplink.sdk.model.request.transaction.settlement.BatchCloseRequest

/**
 * Payment request adapter
 *
 * Adapt the specific transaction request types to a unified PaymentRequest format
 * Be responsible for data conversion and field mapping at the business layer
 *
 * @author TaPro Team
 * @since 2025-01-XX
 */
object PaymentRequestAdapter {

    /**
     * Convert SaleRequest to PaymentRequest
     */
    fun convertSaleRequest(request: SaleRequest): PaymentRequest {
        return PaymentRequest(
            action = TransactionAction.SALE.value,
            referenceOrderId = request.referenceOrderId,
            transactionRequestId = request.transactionRequestId,
            description = request.description,
            amount = request.amount,
            paymentMethod = request.paymentMethod,
            attach = request.attach,
            notifyUrl = request.notifyUrl,
            requestTimeout = request.requestTimeout,
            printReceipt = request.printReceipt,
        )
    }

    /**
     * Convert AuthRequest to PaymentRequest
     */
    fun convertAuthRequest(request: AuthRequest): PaymentRequest {
        // Convert AuthAmountInfo to AmountInfo
        val amountInfo = AmountInfo(
            orderAmount = request.amount.orderAmount,
            pricingCurrency = request.amount.pricingCurrency
        )

        return PaymentRequest(
            action = TransactionAction.AUTH.value,
            referenceOrderId = request.referenceOrderId,
            transactionRequestId = request.transactionRequestId,
            description = request.description,
            amount = amountInfo,
            paymentMethod = request.paymentMethod,
            attach = request.attach,
            notifyUrl = request.notifyUrl,
            requestTimeout = request.requestTimeout,
            printReceipt = request.printReceipt
        )
    }

    /**
     * Convert ForcedAuthRequest to PaymentRequest
     */
    fun convertForcedAuthRequest(request: ForcedAuthRequest): PaymentRequest {
        // Convert AuthAmountInfo to AmountInfo
        val amountInfo = AmountInfo(
            orderAmount = request.amount.orderAmount,
            pricingCurrency = request.amount.pricingCurrency
        )

        return PaymentRequest(
            action = TransactionAction.FORCED_AUTH.value,
            referenceOrderId = request.referenceOrderId,
            transactionRequestId = request.transactionRequestId,
            description = request.description,
            amount = amountInfo,
            paymentMethod = request.paymentMethod,
            attach = request.attach,
            notifyUrl = request.notifyUrl,
            requestTimeout = request.requestTimeout,
            printReceipt = request.printReceipt
        )
    }

    /**
     * Convert RefundRequest to PaymentRequest
     */
    fun convertRefundRequest(request: RefundRequest): PaymentRequest {
        return PaymentRequest(
            action = TransactionAction.REFUND.value,
            referenceOrderId = request.referenceOrderId,
            transactionRequestId = request.transactionRequestId,
            description = request.description,
            amount = request.amount,
            originalTransactionId = request.originalTransactionId,
            originalTransactionRequestId = request.originalTransactionRequestId,
            paymentMethod = request.paymentMethod,
            attach = request.attach,
            notifyUrl = request.notifyUrl,
            requestTimeout = request.requestTimeout,
            printReceipt = request.printReceipt
        )
    }

    /**
     * Convert VoidRequest to PaymentRequest
     */
    fun convertVoidRequest(request: VoidRequest): PaymentRequest {
        return PaymentRequest(
            action = TransactionAction.VOID.value,
            transactionRequestId = request.transactionRequestId,
            description = request.description,
            originalTransactionId = request.originalTransactionId,
            originalTransactionRequestId = request.originalTransactionRequestId,
            attach = request.attach,
            notifyUrl = request.notifyUrl,
            printReceipt = request.printReceipt
        )
    }

    /**
     * Convert PostAuthRequest to PaymentRequest
     */
    fun convertPostAuthRequest(request: PostAuthRequest): PaymentRequest {
        return PaymentRequest(
            action = TransactionAction.POST_AUTH.value,
            transactionRequestId = request.transactionRequestId,
            description = request.description,
            amount = request.amount,
            originalTransactionId = request.originalTransactionId,
            originalTransactionRequestId = request.originalTransactionRequestId,
            attach = request.attach,
            notifyUrl = request.notifyUrl,
            requestTimeout = request.requestTimeout,
            printReceipt = request.printReceipt,
        )
    }

    /**
     * Convert IncrementalAuthRequest to PaymentRequest
     */
    fun convertIncrementalAuthRequest(request: IncrementalAuthRequest): PaymentRequest {
        // Convert AuthAmountInfo to AmountInfo
        val amountInfo = AmountInfo(
            orderAmount = request.amount.orderAmount,
            pricingCurrency = request.amount.pricingCurrency
        )

        return PaymentRequest(
            action = TransactionAction.INCREMENT_AUTH.value,
            transactionRequestId = request.transactionRequestId,
            description = request.description,
            amount = amountInfo,
            originalTransactionId = request.originalTransactionId,
            originalTransactionRequestId = request.originalTransactionRequestId,
            attach = request.attach,
            notifyUrl = request.notifyUrl,
            requestTimeout = request.requestTimeout,
            printReceipt = request.printReceipt
        )
    }

    /**
     * Convert AbortRequest to PaymentRequest
     */
    fun convertAbortRequest(request: AbortRequest): PaymentRequest {
        return PaymentRequest(
            action = TransactionAction.ABORT.value,
            description = request.description,
            originalTransactionRequestId = request.originalTransactionRequestId,
            attach = request.attach,
            requestTimeout = request.requestTimeout
        )
    }

    /**
     * Convert TipAdjustRequest to PaymentRequest
     */
    fun convertTipAdjustRequest(request: TipAdjustRequest): PaymentRequest {
        return PaymentRequest(
            action = TransactionAction.TIP_ADJUST.value,
            transactionRequestId = request.transactionRequestId,
            originalTransactionId = request.originalTransactionId,
            originalTransactionRequestId = request.originalTransactionRequestId,
            tipAmount = request.tipAmount,
            attach = request.attach,
            requestTimeout = request.requestTimeout
        )
    }

    /**
     * Convert BatchCloseRequest to PaymentRequest
     */
    fun convertBatchCloseRequest(request: BatchCloseRequest): PaymentRequest {
        return PaymentRequest(
            action = TransactionAction.BATCH_CLOSE.value,
            transactionRequestId = request.transactionRequestId,
            description = request.description ?: "Batch Close",
            requestTimeout = request.requestTimeout
        )
    }
}