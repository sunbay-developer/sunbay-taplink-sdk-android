package com.sunmi.tapro.taplink.sdk.model.request

/**
 * 查询请求类
 *
 * 用于查询交易状态
 * 可以通过交易ID、交易请求ID或订单ID查询
 *
 * @author TaPro Team
 * @since 2025-01-XX
 */
class QueryRequest {

    /**
     * 交易ID
     * Nexus（SUNBAY支付网关）交易ID
     */
    var transactionId: String? = null
        private set

    /**
     * 交易请求ID
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
        return this
    }

    /**
     * 链式调用：设置交易请求ID
     *
     * @param transactionRequestId 交易请求ID
     * @return QueryRequest 当前对象，支持链式调用
     */
    fun setTransactionRequestId(transactionRequestId: String): QueryRequest {
        this.transactionRequestId = transactionRequestId
        return this
    }

    companion object {
        /**
         * 创建查询请求构建器
         *
         * @return Builder 构建器对象
         */
        fun builder(): Builder = Builder()

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

    /**
     * QueryRequest 构建器
     */
    class Builder {
        private var transactionId: String? = null
        private var transactionRequestId: String? = null

        fun setTransactionId(transactionId: String) = apply { this.transactionId = transactionId }
        fun setTransactionRequestId(transactionRequestId: String) = apply { this.transactionRequestId = transactionRequestId }

        fun build(): QueryRequest {
            val request = QueryRequest()
            transactionId?.let { request.setTransactionId(it) }
            transactionRequestId?.let { request.setTransactionRequestId(it) }
            return request
        }
    }
}

