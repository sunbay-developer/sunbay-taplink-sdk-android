package com.sunmi.tapro.taplink.sdk

import com.sunmi.tapro.taplink.sdk.callback.PaymentCallback
import com.sunmi.tapro.taplink.sdk.enums.TransactionAction
import com.sunmi.tapro.taplink.sdk.impl.TaplinkApiImpl
import com.sunmi.tapro.taplink.sdk.model.common.AmountInfo
import com.sunmi.tapro.taplink.sdk.model.request.PaymentRequest
import com.sunmi.tapro.taplink.sdk.model.request.QueryRequest
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
    
    // ==================== 交易方法 ====================
    
    /**
     * 销售交易
     * 
     * @param amount 交易金额信息
     * @param orderId 订单ID
     * @param description 交易描述（可选）
     * @param callback 交易回调
     */
    fun sale(
        amount: AmountInfo,
        orderId: String,
        description: String? = null,
        callback: PaymentCallback
    ) {
        val request = PaymentRequest.builder()
            .setAction(TransactionAction.SALE)
            .setReferenceOrderId(orderId)
            .setTransactionRequestId(generateTransactionRequestId(orderId, "SALE"))
            .setAmount(amount)
            .setDescription(description ?: "销售交易")
            .build()
        
        apiImpl.execute(request, callback)
    }
    
    /**
     * 退款交易
     * 
     * @param amount 退款金额信息
     * @param orderId 订单ID
     * @param originalTransactionId 原始交易ID
     * @param reason 退款原因（可选）
     * @param callback 交易回调
     */
    fun refund(
        amount: AmountInfo,
        orderId: String,
        originalTransactionId: String,
        reason: String? = null,
        callback: PaymentCallback
    ) {
        val request = PaymentRequest.builder()
            .setAction(TransactionAction.REFUND)
            .setReferenceOrderId(orderId)
            .setTransactionRequestId(generateTransactionRequestId(orderId, "REFUND"))
            .setAmount(amount)
            .setOriginalTransactionId(originalTransactionId)
            .setReason(reason ?: "退款")
            .setDescription("退款交易")
            .build()
        
        apiImpl.execute(request, callback)
    }
    
    /**
     * 撤销交易
     * 
     * @param orderId 订单ID
     * @param originalTransactionId 原始交易ID
     * @param reason 撤销原因（可选）
     * @param callback 交易回调
     */
    fun void(
        orderId: String,
        originalTransactionId: String,
        reason: String? = null,
        callback: PaymentCallback
    ) {
        val request = PaymentRequest.builder()
            .setAction(TransactionAction.VOID)
            .setReferenceOrderId(orderId)
            .setTransactionRequestId(generateTransactionRequestId(orderId, "VOID"))
            .setOriginalTransactionId(originalTransactionId)
            .setReason(reason ?: "撤销")
            .setDescription("撤销交易")
            .build()
        
        apiImpl.execute(request, callback)
    }
    
    /**
     * 预授权
     * 
     * @param amount 预授权金额信息
     * @param orderId 订单ID
     * @param description 交易描述（可选）
     * @param callback 交易回调
     */
    fun auth(
        amount: AmountInfo,
        orderId: String,
        description: String? = null,
        callback: PaymentCallback
    ) {
        val request = PaymentRequest.builder()
            .setAction(TransactionAction.AUTH)
            .setReferenceOrderId(orderId)
            .setTransactionRequestId(generateTransactionRequestId(orderId, "AUTH"))
            .setAmount(amount)
            .setDescription(description ?: "预授权")
            .build()
        
        apiImpl.execute(request, callback)
    }
    
    /**
     * 预授权完成
     * 
     * @param amount 完成金额信息
     * @param orderId 订单ID
     * @param originalTransactionId 原始预授权交易ID
     * @param description 交易描述（可选）
     * @param callback 交易回调
     */
    fun postAuth(
        amount: AmountInfo,
        orderId: String,
        originalTransactionId: String,
        description: String? = null,
        callback: PaymentCallback
    ) {
        val request = PaymentRequest.builder()
            .setAction(TransactionAction.POST_AUTH)
            .setReferenceOrderId(orderId)
            .setTransactionRequestId(generateTransactionRequestId(orderId, "POST_AUTH"))
            .setAmount(amount)
            .setOriginalTransactionId(originalTransactionId)
            .setDescription(description ?: "预授权完成")
            .build()
        
        apiImpl.execute(request, callback)
    }
    
    /**
     * 预授权追加
     * 
     * @param amount 追加金额信息
     * @param orderId 订单ID
     * @param originalTransactionId 原始预授权交易ID
     * @param description 交易描述（可选）
     * @param callback 交易回调
     */
    fun incrementalAuth(
        amount: AmountInfo,
        orderId: String,
        originalTransactionId: String,
        description: String? = null,
        callback: PaymentCallback
    ) {
        val request = PaymentRequest.builder()
            .setAction(TransactionAction.INCREMENT_AUTH)
            .setReferenceOrderId(orderId)
            .setTransactionRequestId(generateTransactionRequestId(orderId, "INCREMENT_AUTH"))
            .setAmount(amount)
            .setOriginalTransactionId(originalTransactionId)
            .setDescription(description ?: "预授权追加")
            .build()
        
        apiImpl.execute(request, callback)
    }
    
    /**
     * 小费调整
     * 
     * @param tipAmount 小费金额
     * @param orderId 订单ID
     * @param originalTransactionId 原始交易ID
     * @param callback 交易回调
     */
    fun tipAdjust(
        tipAmount: BigDecimal,
        orderId: String,
        originalTransactionId: String,
        callback: PaymentCallback
    ) {
        val request = PaymentRequest.builder()
            .setAction(TransactionAction.TIP_ADJUST)
            .setReferenceOrderId(orderId)
            .setTransactionRequestId(generateTransactionRequestId(orderId, "TIP_ADJUST"))
            .setOriginalTransactionId(originalTransactionId)
            .setTipAmount(tipAmount)
            .setDescription("小费调整")
            .build()
        
        apiImpl.execute(request, callback)
    }
    
    /**
     * 批次结算
     * 
     * @param orderId 订单ID
     * @param callback 交易回调
     */
    fun batchClose(
        orderId: String,
        callback: PaymentCallback
    ) {
        val request = PaymentRequest.builder()
            .setAction(TransactionAction.BATCH_CLOSE)
            .setReferenceOrderId(orderId)
            .setTransactionRequestId(generateTransactionRequestId(orderId, "BATCH_CLOSE"))
            .setDescription("批次结算")
            .build()
        
        apiImpl.execute(request, callback)
    }
    
    // ==================== 查询方法 ====================
    
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
     * 查询交易（通过交易ID）
     * 
     * @param transactionId 交易ID
     * @param callback 查询回调
     */
    fun queryByTransactionId(
        transactionId: String,
        callback: PaymentCallback
    ) {
        val queryRequest = QueryRequest.builder()
            .setTransactionId(transactionId)
            .build()
        
        apiImpl.query(queryRequest, callback)
    }
    
    /**
     * 查询交易（通过订单ID）
     * 
     * @param orderId 订单ID
     * @param callback 查询回调
     */
    fun queryByOrderId(
        orderId: String,
        callback: PaymentCallback
    ) {
        val queryRequest = QueryRequest.builder()
            .setReferenceOrderId(orderId)
            .build()
        
        apiImpl.query(queryRequest, callback)
    }
    
    // ==================== 便捷方法 ====================
    
    /**
     * 快速销售（使用基本金额）
     * 
     * @param totalAmount 总金额
     * @param currency 货币代码（默认USD）
     * @param orderId 订单ID
     * @param description 交易描述（可选）
     * @param callback 交易回调
     */
    fun quickSale(
        totalAmount: BigDecimal,
        currency: String = "USD",
        orderId: String,
        description: String? = null,
        callback: PaymentCallback
    ) {
        val amountInfo = AmountInfo(
            orderAmount = totalAmount,
            pricingCurrency = currency
        )
        
        sale(amountInfo, orderId, description, callback)
    }
    
    /**
     * 快速退款（使用基本金额）
     * 
     * @param refundAmount 退款金额
     * @param currency 货币代码（默认USD）
     * @param orderId 订单ID
     * @param originalTransactionId 原始交易ID
     * @param reason 退款原因（可选）
     * @param callback 交易回调
     */
    fun quickRefund(
        refundAmount: BigDecimal,
        currency: String = "USD",
        orderId: String,
        originalTransactionId: String,
        reason: String? = null,
        callback: PaymentCallback
    ) {
        val amountInfo = AmountInfo(
            orderAmount = refundAmount,
            pricingCurrency = currency
        )
        
        refund(amountInfo, orderId, originalTransactionId, reason, callback)
    }
    
    /**
     * 快速预授权（使用基本金额）
     * 
     * @param authAmount 预授权金额
     * @param currency 货币代码（默认USD）
     * @param orderId 订单ID
     * @param description 交易描述（可选）
     * @param callback 交易回调
     */
    fun quickAuth(
        authAmount: BigDecimal,
        currency: String = "USD",
        orderId: String,
        description: String? = null,
        callback: PaymentCallback
    ) {
        val amountInfo = AmountInfo(
            orderAmount = authAmount,
            pricingCurrency = currency
        )
        
        auth(amountInfo, orderId, description, callback)
    }
    
    // ==================== 工具方法 ====================
    
    /**
     * 执行原始交易请求（高级用法）
     * 
     * @param request 支付请求对象
     * @param callback 支付回调
     */
    fun executeRawRequest(request: PaymentRequest, callback: PaymentCallback) {
        apiImpl.execute(request, callback)
    }
    
    /**
     * 生成交易请求ID
     * 
     * @param orderId 订单ID
     * @param action 交易类型
     * @return 生成的交易请求ID
     */
    private fun generateTransactionRequestId(orderId: String, action: String): String {
        val timestamp = System.currentTimeMillis()
        return "${orderId}_${action}_${timestamp}"
    }
}