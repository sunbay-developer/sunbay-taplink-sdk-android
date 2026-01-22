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
 * Taplink 交易客户端类
 *
 * 专注于交易操作的客户端，通过 TaplinkSDK.getClient() 获取实例
 * 连接管理由 TaplinkSDK 负责，此类只提供交易功能
 *
 * 主要职责：
 * - 交易操作（sale、refund、void、auth等）
 * - 查询功能（交易查询、状态查询）
 * - 便捷交易方法
 *
 * 使用方式：
 * ```kotlin
 * // 1. 初始化SDK并建立连接
 * TaplinkSDK.init(context, config)
 * TaplinkSDK.connect(connectionConfig) { connected ->
 *     if (connected) {
 *         // 2. 获取客户端并执行交易
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
 * 支持的交易类型：
 * - sale: 销售交易
 * - refund: 退款交易
 * - void: 撤销交易
 * - auth: 预授权
 * - postAuth: 预授权完成
 * - incrementalAuth: 预授权追加
 * - tipAdjust: 小费调整
 * - batchClose: 批次结算
 * - query: 查询交易
 *
 * @author TaPro Team
 * @since 2025-01-XX
 */
class TaplinkClient internal constructor(
    /**
     * 内部使用的 API 实现
     */
    private val apiImpl: TaplinkApiImpl
) {

    // ==================== 新的类型安全交易方法 ====================

    /**
     * 销售交易（使用SaleRequest）
     *
     * @param request 销售请求对象
     * @param callback 交易回调
     */
    fun sale(request: SaleRequest, callback: PaymentCallback) {
        val paymentRequest = PaymentRequestAdapter.convertSaleRequest(request)
        apiImpl.execute(paymentRequest, callback)
    }

    /**
     * 预授权交易（使用AuthRequest）
     *
     * @param request 预授权请求对象
     * @param callback 交易回调
     */
    fun auth(request: AuthRequest, callback: PaymentCallback) {
        val paymentRequest = PaymentRequestAdapter.convertAuthRequest(request)
        apiImpl.execute(paymentRequest, callback)
    }

    /**
     * 强制预授权交易（使用ForcedAuthRequest）
     *
     * @param request 强制预授权请求对象
     * @param callback 交易回调
     */
    fun forcedAuth(request: ForcedAuthRequest, callback: PaymentCallback) {
        val paymentRequest = PaymentRequestAdapter.convertForcedAuthRequest(request)
        apiImpl.execute(paymentRequest, callback)
    }

    /**
     * 退款交易（使用RefundRequest）
     *
     * @param request 退款请求对象
     * @param callback 交易回调
     */
    fun refund(request: RefundRequest, callback: PaymentCallback) {
        val paymentRequest = PaymentRequestAdapter.convertRefundRequest(request)
        apiImpl.execute(paymentRequest, callback)
    }

    /**
     * 撤销交易（使用VoidRequest）
     *
     * @param request 撤销请求对象
     * @param callback 交易回调
     */
    fun void(request: VoidRequest, callback: PaymentCallback) {
        val paymentRequest = PaymentRequestAdapter.convertVoidRequest(request)
        apiImpl.execute(paymentRequest, callback)
    }

    /**
     * 预授权完成交易（使用PostAuthRequest）
     *
     * @param request 预授权完成请求对象
     * @param callback 交易回调
     */
    fun postAuth(request: PostAuthRequest, callback: PaymentCallback) {
        val paymentRequest = PaymentRequestAdapter.convertPostAuthRequest(request)
        apiImpl.execute(paymentRequest, callback)
    }

    /**
     * 增量预授权交易（使用IncrementalAuthRequest）
     *
     * @param request 增量预授权请求对象
     * @param callback 交易回调
     */
    fun incrementalAuth(request: IncrementalAuthRequest, callback: PaymentCallback) {
        val paymentRequest = PaymentRequestAdapter.convertIncrementalAuthRequest(request)
        apiImpl.execute(paymentRequest, callback)
    }

    /**
     * 中止交易（使用AbortRequest）
     *
     * @param request 中止请求对象
     * @param callback 交易回调
     */
    fun abort(request: AbortRequest, callback: PaymentCallback) {
        val paymentRequest = PaymentRequestAdapter.convertAbortRequest(request)
        apiImpl.execute(paymentRequest, callback)
    }

    /**
     * 小费调整交易（使用TipAdjustRequest）
     *
     * @param request 小费调整请求对象
     * @param callback 交易回调
     */
    fun tipAdjust(request: TipAdjustRequest, callback: PaymentCallback) {
        val paymentRequest = PaymentRequestAdapter.convertTipAdjustRequest(request)
        apiImpl.execute(paymentRequest, callback)
    }

    /**
     * 查询交易
     *
     * @param queryRequest 查询请求
     * @param callback 查询回调
     */
    fun query(
        queryRequest: QueryRequest,
        callback: PaymentCallback
    ) {
        apiImpl.query(queryRequest, callback)
    }

    /**
     * 批次关闭（使用BatchCloseRequest）
     *
     * @param request 批次关闭请求对象
     * @param callback 交易回调
     */
    fun batchClose(request: BatchCloseRequest, callback: PaymentCallback) {
        val paymentRequest = PaymentRequestAdapter.convertBatchCloseRequest(request)
        apiImpl.execute(paymentRequest, callback)
    }
}