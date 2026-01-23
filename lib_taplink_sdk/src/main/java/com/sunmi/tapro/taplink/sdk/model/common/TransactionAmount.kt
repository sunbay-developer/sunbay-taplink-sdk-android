package com.sunmi.tapro.taplink.sdk.model.common

import java.math.BigDecimal

/**
 * Transaction amount class.
 * 
 * Contains detailed amount breakdown information.
 * 
 * @author TaPro Team
 * @since 2025-01-XX
 */
data class TransactionAmount(
    /**
     * Pricing currency (ISO 4217 standard, e.g., "USD", "EUR").
     */
    val priceCurrency: String,
    
    /**
     * Transaction amount (unit: base currency unit).
     */
    val transAmount: BigDecimal,
    
    /**
     * Order amount (unit: base currency unit).
     */
    val orderAmount: BigDecimal,
    
    /**
     * Tax amount (unit: base currency unit).
     */
    val taxAmount: BigDecimal? = null,
    
    /**
     * Service fee (unit: base currency unit).
     */
    val serviceFee: BigDecimal? = null,
    
    /**
     * Surcharge amount (unit: base currency unit).
     */
    val surchargeAmount: BigDecimal? = null,
    
    /**
     * Tip amount (unit: base currency unit).
     */
    val tipAmount: BigDecimal? = null,
    
    /**
     * Cashback amount (unit: base currency unit).
     */
    val cashbackAmount: BigDecimal? = null
)

