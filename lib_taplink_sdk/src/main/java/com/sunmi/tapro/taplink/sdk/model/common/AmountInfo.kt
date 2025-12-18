package com.sunmi.tapro.taplink.sdk.model.common

import java.math.BigDecimal

/**
 * Amount Information Class
 * 
 * Contains detailed amount breakdown information
 * 
 * @author TaPro Team
 * @since 2025-01-XX
 */
data class AmountInfo(
    /**
     * Order amount (required, unit: base currency unit, e.g., USD)
     */
    val orderAmount: BigDecimal,
    
    /**
     * Pricing currency (required, ISO 4217 standard, e.g., "USD", "EUR")
     */
    val pricingCurrency: String,
    
    /**
     * Tip amount (optional, unit: base currency unit)
     */
    val tipAmount: BigDecimal? = null,
    
    /**
     * Tax amount (optional, unit: base currency unit)
     */
    val taxAmount: BigDecimal? = null,
    
    /**
     * Surcharge amount (optional, unit: base currency unit)
     */
    val surchargeAmount: BigDecimal? = null,
    
    /**
     * Cashback amount (optional, unit: base currency unit)
     */
    val cashbackAmount: BigDecimal? = null,
    
    /**
     * Service fee (optional, unit: base currency unit)
     */
    val serviceFee: BigDecimal? = null
) {
    /**
     * Chained call: Set order amount
     */
    fun setOrderAmount(orderAmount: BigDecimal): AmountInfo = copy(orderAmount = orderAmount)
    
    /**
     * Chained call: Set pricing currency
     */
    fun setPricingCurrency(pricingCurrency: String): AmountInfo = copy(pricingCurrency = pricingCurrency)
    
    /**
     * Chained call: Set tip amount
     */
    fun setTipAmount(tipAmount: BigDecimal): AmountInfo = copy(tipAmount = tipAmount)
    
    /**
     * Chained call: Set tax amount
     */
    fun setTaxAmount(taxAmount: BigDecimal): AmountInfo = copy(taxAmount = taxAmount)
    
    /**
     * Chained call: Set surcharge amount
     */
    fun setSurchargeAmount(surchargeAmount: BigDecimal): AmountInfo = copy(surchargeAmount = surchargeAmount)
    
    /**
     * Chained call: Set cashback amount
     */
    fun setCashbackAmount(cashbackAmount: BigDecimal): AmountInfo = copy(cashbackAmount = cashbackAmount)
    
    /**
     * Chained call: Set service fee
     */
    fun setServiceFee(serviceFee: BigDecimal): AmountInfo = copy(serviceFee = serviceFee)
}

