package com.sunmi.tapro.taplink.sdk.model.request

/**
 * Query Request Class
 * 
 * Used to query transaction status
 * Can query by transaction ID or transaction request ID
 * 
 * Note: Either transactionId or transactionRequestId must be provided, but not both
 * 
 * @author TaPro Team
 * @since 2025-01-XX
 */
class QueryRequest {
    
    /**
     * Transaction ID (choose one with transactionRequestId)
     * Nexus (SUNBAY payment gateway) transaction ID
     */
    var transactionId: String? = null
        private set
    
    /**
     * Transaction request ID (choose one with transactionId)
     * Transaction request ID assigned by merchant system
     */
    var transactionRequestId: String? = null
        private set
    
    /**
     * Chained call: Set transaction ID
     * 
     * @param transactionId Transaction ID
     * @return QueryRequest Current object, supports chained calls
     */
    fun setTransactionId(transactionId: String): QueryRequest {
        this.transactionId = transactionId
        this.transactionRequestId = null
        return this
    }
    
    /**
     * Chained call: Set transaction request ID
     * 
     * @param transactionRequestId Transaction request ID
     * @return QueryRequest Current object, supports chained calls
     */
    fun setTransactionRequestId(transactionRequestId: String): QueryRequest {
        this.transactionId = null
        this.transactionRequestId = transactionRequestId
        return this
    }
    
    companion object {
        /**
         * Create request to query by transaction ID
         * 
         * @param transactionId Transaction ID
         * @return QueryRequest Query request object
         */
        fun byTransactionId(transactionId: String): QueryRequest {
            return QueryRequest().setTransactionId(transactionId)
        }
        
        /**
         * Create request to query by transaction request ID
         * 
         * @param transactionRequestId Transaction request ID
         * @return QueryRequest Query request object
         */
        fun byTransactionRequestId(transactionRequestId: String): QueryRequest {
            return QueryRequest().setTransactionRequestId(transactionRequestId)
        }
    }
}

