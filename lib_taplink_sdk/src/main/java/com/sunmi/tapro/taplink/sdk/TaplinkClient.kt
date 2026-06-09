package com.sunmi.tapro.taplink.sdk

import com.sunmi.tapro.taplink.sdk.callback.PaymentCallback
import com.sunmi.tapro.taplink.sdk.adapter.PaymentRequestAdapter
import com.sunmi.tapro.taplink.sdk.enums.TransactionAction
import com.sunmi.tapro.taplink.sdk.impl.TaplinkApiImpl
import com.sunmi.tapro.taplink.sdk.model.common.AmountInfo
import com.sunmi.tapro.taplink.sdk.model.request.PaymentRequest
import com.sunmi.tapro.taplink.sdk.model.request.QueryRequest
import com.sunmi.tapro.taplink.sdk.model.request.transaction.*
import com.sunmi.tapro.taplink.sdk.model.request.transaction.settlement.BatchCloseRequest
import java.math.BigDecimal

/**
 * Taplink Transaction Client Class
 *
 * Client focused on transaction operations, obtained via TaplinkSDK.getClient()
 * Connection management is handled by TaplinkSDK, this class only provides transaction functionality
 *
 * Main responsibilities:
 * - Transaction operations (sale, refund, void, auth, etc.)
 * - Query functionality (transaction query, status query)
 * - Convenient transaction methods
 *
 * Usage:
 * ```kotlin
 * // 1. Initialize SDK and establish connection
 * TaplinkSDK.init(context, config)
 * TaplinkSDK.connect(connectionConfig) { connected ->
 *     if (connected) {
 *         // 2. Get client and execute transaction
 *         val client = TaplinkSDK.getClient()
 *         client.quickSale(
 *             totalAmount = BigDecimal("10.00"),
 *             orderId = "ORDER001",
 *             callback = paymentCallback
 *         )
 *     }
 * }
 * ```
 *
 * Supported transaction types:
 * - sale: Sales transaction
 * - refund: Refund transaction
 * - void: Void transaction
 * - auth: Pre-authorization
 * - postAuth: Pre-authorization completion
 * - incrementalAuth: Incremental pre-authorization
 * - tipAdjust: Tip adjustment
 * - batchClose: Batch settlement
 * - query: Transaction query
 *
 * @author TaPro Team
 * @since 2025-01-XX
 */
class TaplinkClient internal constructor(
    /**
     * Internal API implementation
     */
    private val apiImpl: TaplinkApiImpl
) {

    // ==================== New type-safe transaction methods ====================

    /**
     * Sales transaction (using SaleRequest)
     *
     * @param request Sales request object
     * @param callback Transaction callback
     */
    fun sale(request: SaleRequest, callback: PaymentCallback) {
        val paymentRequest = PaymentRequestAdapter.convertSaleRequest(request)
        apiImpl.execute(paymentRequest, callback)
    }

    /**
     * Pre-authorization transaction (using AuthRequest)
     *
     * @param request Pre-authorization request object
     * @param callback Transaction callback
     */
    fun auth(request: AuthRequest, callback: PaymentCallback) {
        val paymentRequest = PaymentRequestAdapter.convertAuthRequest(request)
        apiImpl.execute(paymentRequest, callback)
    }

    /**
     * Forced pre-authorization transaction (using ForcedAuthRequest)
     *
     * @param request Forced pre-authorization request object
     * @param callback Transaction callback
     */
    fun forcedAuth(request: ForcedAuthRequest, callback: PaymentCallback) {
        val paymentRequest = PaymentRequestAdapter.convertForcedAuthRequest(request)
        apiImpl.execute(paymentRequest, callback)
    }

    /**
     * Refund transaction (using RefundRequest)
     *
     * @param request Refund request object
     * @param callback Transaction callback
     */
    fun refund(request: RefundRequest, callback: PaymentCallback) {
        val paymentRequest = PaymentRequestAdapter.convertRefundRequest(request)
        apiImpl.execute(paymentRequest, callback)
    }

    /**
     * Void transaction (using VoidRequest)
     *
     * @param request Void request object
     * @param callback Transaction callback
     */
    fun void(request: VoidRequest, callback: PaymentCallback) {
        val paymentRequest = PaymentRequestAdapter.convertVoidRequest(request)
        apiImpl.execute(paymentRequest, callback)
    }

    /**
     * Pre-authorization completion transaction (using PostAuthRequest)
     *
     * @param request Pre-authorization completion request object
     * @param callback Transaction callback
     */
    fun postAuth(request: PostAuthRequest, callback: PaymentCallback) {
        val paymentRequest = PaymentRequestAdapter.convertPostAuthRequest(request)
        apiImpl.execute(paymentRequest, callback)
    }

    /**
     * Incremental pre-authorization transaction (using IncrementalAuthRequest)
     *
     * @param request Incremental pre-authorization request object
     * @param callback Transaction callback
     */
    fun incrementalAuth(request: IncrementalAuthRequest, callback: PaymentCallback) {
        val paymentRequest = PaymentRequestAdapter.convertIncrementalAuthRequest(request)
        apiImpl.execute(paymentRequest, callback)
    }

    /**
     * Abort transaction (using AbortRequest)
     *
     * @param request Abort request object
     * @param callback Transaction callback
     */
    fun abort(request: AbortRequest, callback: PaymentCallback) {
        val paymentRequest = PaymentRequestAdapter.convertAbortRequest(request)
        apiImpl.execute(paymentRequest, callback)
    }

    /**
     * Tip adjustment transaction (using TipAdjustRequest)
     *
     * @param request Tip adjustment request object
     * @param callback Transaction callback
     */
    fun tipAdjust(request: TipAdjustRequest, callback: PaymentCallback) {
        val paymentRequest = PaymentRequestAdapter.convertTipAdjustRequest(request)
        apiImpl.execute(paymentRequest, callback)
    }

    /**
     * Transaction query
     *
     * @param queryRequest Query request
     * @param callback Query callback
     */
    fun query(
        queryRequest: QueryRequest,
        callback: PaymentCallback
    ) {
        apiImpl.query(queryRequest, callback)
    }

    /**
     * Batch close (using BatchCloseRequest)
     *
     * @param request Batch close request object
     * @param callback Transaction callback
     */
    fun batchClose(request: BatchCloseRequest, callback: PaymentCallback) {
        val paymentRequest = PaymentRequestAdapter.convertBatchCloseRequest(request)
        apiImpl.execute(paymentRequest, callback)
    }
}