package com.sunmi.tapro.taplink.sdk.model.response

/**
 * Batch Close Result
 * 
 * @author TaPro Team
 * @since 2025-01-XX
 */
data class BatchCloseResult(
    /** Batch number */
    val batchNo: String,
    
    /** Total count */
    val totalCount: Int,
    
    /** Total amount (cents/smallest currency unit) */
    val totalAmount: Int,
    
    /** Sale count */
    val saleCount: Int,
    
    /** Sale amount (cents/smallest currency unit) */
    val saleAmount: Int,
    
    /** Refund count */
    val refundCount: Int,
    
    /** Refund amount (cents/smallest currency unit) */
    val refundAmount: Int,
    
    /** Currency code (ISO 4217) */
    val currency: String? = null,
    
    /** Batch close time (ISO 8601 format) */
    val closeTime: String? = null,
    
    /** Result description */
    val message: String? = null
) {
    /**
     * Whether successful
     */
    fun isSuccess(): Boolean = batchNo.isNotEmpty() && totalCount >= 0
}



