package com.sunmi.tapro.taplink.sdk.model.common

import java.math.BigDecimal

/**
 * Batch amount class.
 * 
 * Used to return batch total amount information during batch close.
 * 
 * @author TaPro Team
 * @since 2025-01-XX
 */
data class BatchAmount(
    /**
     * Pricing currency (ISO 4217 standard, e.g., "USD", "EUR").
     */
    val priceCurrency: String,
    
    /**
     * Total amount (unit: base currency unit).
     */
    val amount: BigDecimal
)

