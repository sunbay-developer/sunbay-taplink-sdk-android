package com.sunmi.tapro.taplink.sdk.model.common

import java.math.BigDecimal

/**
 * Amount information class.
 *
 * All monetary values in this class use the **smallest currency unit** (e.g., cents for USD/EUR).
 * Do NOT pass dollar/euro amounts — pass cents instead:
 * - `BigDecimal("1000")` = **$10.00 USD**
 * - `BigDecimal("500")`  = **$5.00 USD** or **€5.00 EUR**
 *
 * Use the factory method [AmountInfo.of] for the simplest construction:
 * ```kotlin
 * val amount = AmountInfo.of(1000L, "USD")   // $10.00
 * val amount = AmountInfo.of(500L, "EUR")    // €5.00
 * ```
 *
 * @author TaPro Team
 * @since 2025-01-XX
 */
data class AmountInfo(
    /**
     * Order amount (**required**).
     *
     * Must be in the **smallest currency unit** (cents). Integer values only — no decimals.
     * Example: `BigDecimal("1000")` = $10.00 USD.
     *
     * This should represent the base order amount before any optional tip.
     * When a tip is known upfront, keep it out of `orderAmount` and set [tipAmount] separately.
     * If tip is folded into `orderAmount`, tax may also be calculated on the tip portion.
     */
    val orderAmount: BigDecimal,
    
    /**
     * Pricing currency (**required**).
     * ISO 4217 currency code, e.g., `"USD"`, `"EUR"`.
     */
    val pricingCurrency: String,
    
    /**
     * Tip amount (optional).
     * Must be in the **smallest currency unit** (cents).
     *
     * When the tip is known upfront, set it separately from [orderAmount].
     * Tapro uses the amount breakdown to calculate and return the final transaction total.
     */
    val tipAmount: BigDecimal? = null,
    
    /**
     * Tax amount (optional).
     * Must be in the **smallest currency unit** (cents).
     */
    val taxAmount: BigDecimal? = null,
    
    /**
     * Surcharge amount (optional).
     * Must be in the **smallest currency unit** (cents).
     *
     * If the customer pays with a Debit Card, Tapro removes this surcharge amount
     * before the transaction is completed.
     */
    val surchargeAmount: BigDecimal? = null,
    
    /**
     * Cashback amount (optional).
     * Must be in the **smallest currency unit** (cents).
     */
    val cashbackAmount: BigDecimal? = null,
    
    /**
     * Service fee (optional).
     * Must be in the **smallest currency unit** (cents).
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

    companion object {
        /**
         * Creates an [AmountInfo] for the most common case: a flat order amount with no tip or extras.
         *
         * All amounts in this SDK use the **smallest currency unit** (e.g., cents for USD/EUR).
         * Example: `AmountInfo.of(1000L, "USD")` represents **$10.00 USD**.
         *
         * @param cents Order amount in smallest currency unit (e.g., cents)
         * @param currency ISO 4217 currency code (e.g., "USD", "EUR")
         */
        @JvmStatic
        fun of(cents: Long, currency: String): AmountInfo =
            AmountInfo(orderAmount = java.math.BigDecimal(cents), pricingCurrency = currency)

        /**
         * Creates an [AmountInfo] from a [java.math.BigDecimal] amount.
         *
         * The value must represent the **smallest currency unit** (no decimals for USD/EUR).
         * Example: `AmountInfo.of(BigDecimal("1000"), "USD")` represents **$10.00 USD**.
         *
         * @param amount Order amount as BigDecimal in smallest currency unit
         * @param currency ISO 4217 currency code (e.g., "USD", "EUR")
         */
        @JvmStatic
        fun of(amount: java.math.BigDecimal, currency: String): AmountInfo =
            AmountInfo(orderAmount = amount, pricingCurrency = currency)
    }
}

