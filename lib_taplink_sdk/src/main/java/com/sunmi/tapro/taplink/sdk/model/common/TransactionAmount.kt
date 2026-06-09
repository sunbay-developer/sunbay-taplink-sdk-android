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
     * Final transaction amount returned by Tapro (unit: base currency unit).
     *
     * This reflects the total after Tapro applies the submitted amount breakdown
     * and any network rules such as removing surcharge for Debit Card transactions.
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
     *
     * For Debit Card transactions, the returned amount may not include this surcharge
     * because Tapro removes it before completion.
     */
    val surchargeAmount: BigDecimal? = null,
    
    /**
     * Tip amount (unit: base currency unit).
     *
     * When tip is submitted separately from the order amount, this field reflects
     * the tip portion included in the final transaction total.
     */
    val tipAmount: BigDecimal? = null,
    
    /**
     * Cashback amount (unit: base currency unit).
     */
    val cashbackAmount: BigDecimal? = null
)

