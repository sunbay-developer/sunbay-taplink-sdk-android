package com.sunmi.tapro.taplink.sdk.model.request

/**
 * Query Request Class
 *
 * Used to query transaction status
 * Can query by transaction ID, transaction request ID, or order ID
 *
 * @author TaPro Team
 * @since 2025-01-XX
 */
class QueryRequest {

    /**
     * Transaction ID
     * Nexus (SUNBAY payment gateway) transaction ID
     */
    var transactionId: String? = null
        private set

    /**
     * Transaction request ID
     * Transaction request ID assigned by the merchant system
     */
    var transactionRequestId: String? = null
        private set

    /**
     * Chain call: Set transaction ID
     *
     * @param transactionId Transaction ID
     * @return QueryRequest Current object, supporting chain calls
     */
    fun setTransactionId(transactionId: String): QueryRequest {
        this.transactionId = transactionId
        return this
    }

    /**
     * Chain call: Set transaction request ID
     *
     * @param transactionRequestId Transaction request ID
     * @return QueryRequest Current object, supporting chain calls
     */
    fun setTransactionRequestId(transactionRequestId: String): QueryRequest {
        this.transactionRequestId = transactionRequestId
        return this
    }

    companion object {
        /**
         * Create query request builder
         *
         * @return Builder Builder object
         */
        fun builder(): Builder = Builder()

        /**
         * Create request for querying by transaction ID
         *
         * @param transactionId Transaction ID
         * @return QueryRequest Query request object
         */
        fun byTransactionId(transactionId: String): QueryRequest {
            return QueryRequest().setTransactionId(transactionId)
        }

        /**
         * Create request for querying by transaction request ID
         *
         * @param transactionRequestId Transaction request ID
         * @return QueryRequest Query request object
         */
        fun byTransactionRequestId(transactionRequestId: String): QueryRequest {
            return QueryRequest().setTransactionRequestId(transactionRequestId)
        }
    }

    /**
     * QueryRequest Builder
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

