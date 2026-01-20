package com.sunmi.tapro.taplink.sdk.adapter

import com.sunmi.tapro.taplink.sdk.enums.TransactionAction
import com.sunmi.tapro.taplink.sdk.model.common.AmountInfo
import com.sunmi.tapro.taplink.sdk.model.request.PaymentRequest
import com.sunmi.tapro.taplink.sdk.model.request.transaction.*
import com.sunmi.tapro.taplink.sdk.model.request.transaction.settlement.BatchCloseRequest

/**
 * 支付请求适配器
 *
 * 将具体的交易请求类型适配为统一的 PaymentRequest 格式
 * 负责业务层的数据转换和字段映射
 *
 * @author TaPro Team
 * @since 2025-01-XX
 */
object PaymentRequestAdapter {

    /**
     * 将SaleRequest转换为PaymentRequest
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
            requestTimeout = request.requestTimeout
        )
    }

    /**
     * 将AuthRequest转换为PaymentRequest
     */
    fun convertAuthRequest(request: AuthRequest): PaymentRequest {
        // 将AuthAmountInfo转换为AmountInfo
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
            requestTimeout = request.requestTimeout
        )
    }

    /**
     * 将ForcedAuthRequest转换为PaymentRequest
     */
    fun convertForcedAuthRequest(request: ForcedAuthRequest): PaymentRequest {
        // 将AuthAmountInfo转换为AmountInfo
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
            requestTimeout = request.requestTimeout
        )
    }

    /**
     * 将RefundRequest转换为PaymentRequest
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
            requestTimeout = request.requestTimeout
        )
    }

    /**
     * 将VoidRequest转换为PaymentRequest
     */
    fun convertVoidRequest(request: VoidRequest): PaymentRequest {
        return PaymentRequest(
            action = TransactionAction.VOID.value,
            transactionRequestId = request.transactionRequestId,
            description = request.description,
            originalTransactionId = request.originalTransactionId,
            originalTransactionRequestId = request.originalTransactionRequestId,
            attach = request.attach,
            notifyUrl = request.notifyUrl
        )
    }

    /**
     * 将PostAuthRequest转换为PaymentRequest
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
            requestTimeout = request.requestTimeout
        )
    }

    /**
     * 将IncrementalAuthRequest转换为PaymentRequest
     */
    fun convertIncrementalAuthRequest(request: IncrementalAuthRequest): PaymentRequest {
        // 将AuthAmountInfo转换为AmountInfo
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
            requestTimeout = request.requestTimeout
        )
    }

    /**
     * 将AbortRequest转换为PaymentRequest
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
     * 将TipAdjustRequest转换为PaymentRequest
     */
    fun convertTipAdjustRequest(request: TipAdjustRequest): PaymentRequest {
        return PaymentRequest(
            action = TransactionAction.TIP_ADJUST.value,
            originalTransactionId = request.originalTransactionId,
            originalTransactionRequestId = request.originalTransactionRequestId,
            tipAmount = request.tipAmount,
            attach = request.attach,
            requestTimeout = request.requestTimeout
        )
    }

    /**
     * 将BatchCloseRequest转换为PaymentRequest
     */
    fun convertBatchCloseRequest(request: BatchCloseRequest): PaymentRequest {
        return PaymentRequest(
            action = TransactionAction.BATCH_CLOSE.value,
            transactionRequestId = request.transactionRequestId,
            description = request.description ?: "批次关闭",
            requestTimeout = request.requestTimeout
        )
    }
}