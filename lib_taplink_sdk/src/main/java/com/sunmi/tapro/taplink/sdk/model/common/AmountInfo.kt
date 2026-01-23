package com.sunmi.tapro.taplink.sdk.model.common

import java.math.BigDecimal

/**
 * Amount information class.
 * 
 * Contains detailed amount breakdown information.
 * 
 * @author TaPro Team
 * @since 2025-01-XX
 */
data class AmountInfo(
    /**
     * Order amount (required, unit: base currency unit, e.g., USD).
     */
    val orderAmount: BigDecimal,
    
    /**
     * Pricing currency (required, ISO 4217 standard, e.g., "USD", "EUR").
     */
    val pricingCurrency: String,
    
    /**
     * Tip amount (optional, unit: base currency unit).
     */
    val tipAmount: BigDecimal? = null,
    
    /**
     * Tax amount (optional, unit: base currency unit).
     */
    val taxAmount: BigDecimal? = null,
    
    /**
     * Surcharge amount (optional, unit: base currency unit).
     */
    val surchargeAmount: BigDecimal? = null,
    
    /**
     * Cashback amount (optional, unit: base currency unit).
     */
    val cashbackAmount: BigDecimal? = null,
    
    /**
     * Service fee (optional, unit: base currency unit).
     */
    val serviceFee: BigDecimal? = null
) {
    /**
     * Sets the order amount.
     *
     * @param orderAmount the order amount
     * @return the updated AmountInfo instance for method chaining
     */
    fun setOrderAmount(orderAmount: BigDecimal): AmountInfo = copy(orderAmount = orderAmount)
    
    /**
     * Sets the pricing currency.
     *
     * @param pricingCurrency the pricing currency code
     * @return the updated AmountInfo instance for method chaining
     */
    fun setPricingCurrency(pricingCurrency: String): AmountInfo = copy(pricingCurrency = pricingCurrency)
    
    /**
     * Sets the tip amount.
     *
     * @param tipAmount the tip amount
     * @return the updated AmountInfo instance for method chaining
     */
    fun setTipAmount(tipAmount: BigDecimal): AmountInfo = copy(tipAmount = tipAmount)
    
    /**
     * Sets the tax amount.
     *
     * @param taxAmount the tax amount
     * @return the updated AmountInfo instance for method chaining
     */
    fun setTaxAmount(taxAmount: BigDecimal): AmountInfo = copy(taxAmount = taxAmount)
    
    /**
     * Sets the surcharge amount.
     *
     * @param surchargeAmount the surcharge amount
     * @return the updated AmountInfo instance for method chaining
     */
    fun setSurchargeAmount(surchargeAmount: BigDecimal): AmountInfo = copy(surchargeAmount = surchargeAmount)
    
    /**
     * Sets the cashback amount.
     *
     * @param cashbackAmount the cashback amount
     * @return the updated AmountInfo instance for method chaining
     */
    fun setCashbackAmount(cashbackAmount: BigDecimal): AmountInfo = copy(cashbackAmount = cashbackAmount)
    
    /**
     * Sets the service fee.
     *
     * @param serviceFee the service fee
     * @return the updated AmountInfo instance for method chaining
     */
    fun setServiceFee(serviceFee: BigDecimal): AmountInfo = copy(serviceFee = serviceFee)
}

