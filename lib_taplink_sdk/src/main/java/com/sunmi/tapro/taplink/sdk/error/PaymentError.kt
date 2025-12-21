package com.sunmi.tapro.taplink.sdk.error

/**
 * Payment error
 * 
 * @author TaPro Team
 * @since 2025-01-XX
 */
data class PaymentError(
    /** Error code (e.g.: E01, E10, E30) */
    val code: String,
    
    /** Error description */
    val message: String,
    
    /** Order number */
    val orderNo: String? = null,
    
    /** Transaction ID (if available) */
    val transactionId: String? = null,
    
    /** Processing suggestion */
    val suggestion: String? = null
)



