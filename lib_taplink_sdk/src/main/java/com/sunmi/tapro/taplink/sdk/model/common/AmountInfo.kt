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
 * Field setters update this instance and return it for chaining. Use [copy] for
 * independent top-level values, and finish updates before request submission.
 * Serialize writes and do not mutate instances used as hash collection keys.
 *
 * @author Tapro Team
 * @since 2025-01-XX
 */
class AmountInfo(
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
    orderAmount: BigDecimal,
    
    /**
     * Pricing currency (**required**).
     * ISO 4217 currency code, e.g., `"USD"`, `"EUR"`.
     */
    pricingCurrency: String,
    
    /**
     * Tip amount (optional).
     * Must be in the **smallest currency unit** (cents).
     *
     * When the tip is known upfront, set it separately from [orderAmount].
     * Tapro uses the amount breakdown to calculate and return the final transaction total.
     */
    tipAmount: BigDecimal? = null,
    
    /**
     * Tax amount (optional).
     * Must be in the **smallest currency unit** (cents).
     */
    taxAmount: BigDecimal? = null,
    
    /**
     * Surcharge amount (optional).
     * Must be in the **smallest currency unit** (cents).
     *
     * If the customer pays with a Debit Card, Tapro removes this surcharge amount
     * before the transaction is completed.
     */
    surchargeAmount: BigDecimal? = null,
    
    /**
     * Cashback amount (optional).
     * Must be in the **smallest currency unit** (cents).
     */
    cashbackAmount: BigDecimal? = null,
    
    /**
     * Service fee (optional).
     * Must be in the **smallest currency unit** (cents).
     */
    serviceFee: BigDecimal? = null
) {
    var orderAmount: BigDecimal = orderAmount
        private set

    var pricingCurrency: String = pricingCurrency
        private set

    var tipAmount: BigDecimal? = tipAmount
        private set

    var taxAmount: BigDecimal? = taxAmount
        private set

    var surchargeAmount: BigDecimal? = surchargeAmount
        private set

    var cashbackAmount: BigDecimal? = cashbackAmount
        private set

    var serviceFee: BigDecimal? = serviceFee
        private set

    /**
     * Sets the order amount.
     *
     * @param orderAmount the order amount
     * @return this AmountInfo instance for method chaining
     */
    fun setOrderAmount(orderAmount: BigDecimal): AmountInfo = apply { this.orderAmount = orderAmount }
    
    /**
     * Sets the pricing currency.
     *
     * @param pricingCurrency the pricing currency code
     * @return this AmountInfo instance for method chaining
     */
    fun setPricingCurrency(pricingCurrency: String): AmountInfo = apply { this.pricingCurrency = pricingCurrency }
    
    /**
     * Sets the tip amount.
     *
     * @param tipAmount the tip amount
     * @return this AmountInfo instance for method chaining
     */
    fun setTipAmount(tipAmount: BigDecimal): AmountInfo = apply { this.tipAmount = tipAmount }
    
    /**
     * Sets the tax amount.
     *
     * @param taxAmount the tax amount
     * @return this AmountInfo instance for method chaining
     */
    fun setTaxAmount(taxAmount: BigDecimal): AmountInfo = apply { this.taxAmount = taxAmount }
    
    /**
     * Sets the surcharge amount.
     *
     * @param surchargeAmount the surcharge amount
     * @return this AmountInfo instance for method chaining
     */
    fun setSurchargeAmount(surchargeAmount: BigDecimal): AmountInfo = apply { this.surchargeAmount = surchargeAmount }
    
    /**
     * Sets the cashback amount.
     *
     * @param cashbackAmount the cashback amount
     * @return this AmountInfo instance for method chaining
     */
    fun setCashbackAmount(cashbackAmount: BigDecimal): AmountInfo = apply { this.cashbackAmount = cashbackAmount }
    
    /**
     * Sets the service fee.
     *
     * @param serviceFee the service fee
     * @return this AmountInfo instance for method chaining
     */
    fun setServiceFee(serviceFee: BigDecimal): AmountInfo = apply { this.serviceFee = serviceFee }


    /** Creates an independent shallow copy; setters mutate this instance. */
    fun copy(
        orderAmount: BigDecimal = this.orderAmount,
        pricingCurrency: String = this.pricingCurrency,
        tipAmount: BigDecimal? = this.tipAmount,
        taxAmount: BigDecimal? = this.taxAmount,
        surchargeAmount: BigDecimal? = this.surchargeAmount,
        cashbackAmount: BigDecimal? = this.cashbackAmount,
        serviceFee: BigDecimal? = this.serviceFee
    ): AmountInfo = AmountInfo(
        orderAmount = orderAmount,
        pricingCurrency = pricingCurrency,
        tipAmount = tipAmount,
        taxAmount = taxAmount,
        surchargeAmount = surchargeAmount,
        cashbackAmount = cashbackAmount,
        serviceFee = serviceFee
    )

    operator fun component1(): BigDecimal = orderAmount
    operator fun component2(): String = pricingCurrency
    operator fun component3(): BigDecimal? = tipAmount
    operator fun component4(): BigDecimal? = taxAmount
    operator fun component5(): BigDecimal? = surchargeAmount
    operator fun component6(): BigDecimal? = cashbackAmount
    operator fun component7(): BigDecimal? = serviceFee

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AmountInfo) return false
        return orderAmount == other.orderAmount &&
            pricingCurrency == other.pricingCurrency &&
            tipAmount == other.tipAmount &&
            taxAmount == other.taxAmount &&
            surchargeAmount == other.surchargeAmount &&
            cashbackAmount == other.cashbackAmount &&
            serviceFee == other.serviceFee
    }

    override fun hashCode(): Int {
        var result = orderAmount.hashCode()
        result = 31 * result + pricingCurrency.hashCode()
        result = 31 * result + (tipAmount?.hashCode() ?: 0)
        result = 31 * result + (taxAmount?.hashCode() ?: 0)
        result = 31 * result + (surchargeAmount?.hashCode() ?: 0)
        result = 31 * result + (cashbackAmount?.hashCode() ?: 0)
        result = 31 * result + (serviceFee?.hashCode() ?: 0)
        return result
    }

    override fun toString(): String =
        "AmountInfo(orderAmount=$orderAmount, pricingCurrency=$pricingCurrency, tipAmount=$tipAmount, taxAmount=$taxAmount, surchargeAmount=$surchargeAmount, cashbackAmount=$cashbackAmount, serviceFee=$serviceFee)"

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

