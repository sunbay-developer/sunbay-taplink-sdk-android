package com.sunmi.tapro.taplink.sdk.model.request

/**
 * 查询请求类
 * 
 * 用于查询交易状态
 * 可以通过交易ID或交易请求ID查询
 * 
 * 注意：transactionId 和 transactionRequestId 必须提供其中一个，且不能同时提供
 * 
 * @author TaPro Team
 * @since 2025-01-XX
 */
class QueryRequest {
    
    /**
     * 交易ID（与 transactionRequestId 二选一）
     * Nexus（SUNBAY支付网关）交易ID
     */
    var transactionId: String? = null
        private set
    
    /**
     * 交易请求ID（与 transactionId 二选一）
     * 商户系统分配的交易请求ID
     */
    var transactionRequestId: String? = null
        private set
    
    /**
     * 链式调用：设置交易ID
     * 
     * @param transactionId 交易ID
     * @return QueryRequest 当前对象，支持链式调用
     */
    fun setTransactionId(transactionId: String): QueryRequest {
        this.transactionId = transactionId
        this.transactionRequestId = null
        return this
    }
    
    /**
     * 链式调用：设置交易请求ID
     * 
     * @param transactionRequestId 交易请求ID
     * @return QueryRequest 当前对象，支持链式调用
     */
    fun setTransactionRequestId(transactionRequestId: String): QueryRequest {
        this.transactionId = null
        this.transactionRequestId = transactionRequestId
        return this
    }
    
    companion object {
        /**
         * 创建通过交易ID查询的请求
         * 
         * @param transactionId 交易ID
         * @return QueryRequest 查询请求对象
         */
        fun byTransactionId(transactionId: String): QueryRequest {
            return QueryRequest().setTransactionId(transactionId)
        }
        
        /**
         * 创建通过交易请求ID查询的请求
         * 
         * @param transactionRequestId 交易请求ID
         * @return QueryRequest 查询请求对象
         */
        fun byTransactionRequestId(transactionRequestId: String): QueryRequest {
            return QueryRequest().setTransactionRequestId(transactionRequestId)
        }
    }
}

