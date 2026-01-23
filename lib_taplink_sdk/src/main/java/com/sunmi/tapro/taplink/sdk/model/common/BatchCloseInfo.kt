package com.sunmi.tapro.taplink.sdk.model.common

import java.math.BigDecimal

/**
 * Batch close information class.
 * 
 * Used to return batch summary information during batch close.
 * 
 * @author TaPro Team
 * @since 2025-01-XX
 */
data class BatchCloseInfo(
    /**
     * Transaction count (batch close only).
     */
    val totalCount: Int? = null,
    
    /**
     * Batch total amount (batch close only).
     */
    val totalAmount: BigDecimal? = null,

    /**
     * Total tip amount.
     * Optional.
     */
    val totalTip: BigDecimal? = null,

    /**
     * Total tax amount.
     * Optional.
     */
    val totalTax: BigDecimal? = null,

    /**
     * Total surcharge amount.
     * Optional.
     */
    val totalSurchargeAmount: BigDecimal? = null,

    /**
     * Total service fee amount.
     * Optional.
     */
    val totalServiceFee: BigDecimal? = null,

    /**
     * Total cash discount amount.
     * Optional.
     */
    val cashDiscount: BigDecimal? = null,
    
    /**
     * Batch close time (batch close only, ISO 8601 format).
     */
    val closeTime: String? = null
)


