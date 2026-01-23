package com.sunmi.tapro.taplink.sdk.model.request.transaction

import java.math.BigDecimal

/**
 * Authorization Amount Information
 *
 * Simplified amount structure for authorization-related transactions, containing only basic amount and currency
 *
 * @param orderAmount Transaction amount, using basic currency unit (e.g., USD)
 * @param pricingCurrency Pricing currency, following ISO 4217 standard
 *
 * @author TaPro Team
 * @since 2025-01-XX
 */
data class AuthAmountInfo(
    val orderAmount: BigDecimal,
    val pricingCurrency: String
) {
    init {
        require(orderAmount > BigDecimal.ZERO) { "orderAmount must be positive" }
        require(pricingCurrency.isNotBlank()) { "pricingCurrency is required" }
        require(pricingCurrency.length == 3) { "pricingCurrency must be 3 characters (ISO 4217)" }
    }
    
    companion object {
        /**
         * Create AuthAmountInfo builder
         */
        fun builder(): Builder = Builder()
    }
    
    /**
     * AuthAmountInfo Builder
     */
    class Builder {
        private var orderAmount: BigDecimal = BigDecimal.ZERO
        private var pricingCurrency: String = ""
        
        fun setOrderAmount(orderAmount: BigDecimal) = apply { this.orderAmount = orderAmount }
        fun setPricingCurrency(pricingCurrency: String) = apply { this.pricingCurrency = pricingCurrency }
        
        fun build(): AuthAmountInfo {
            return AuthAmountInfo(orderAmount, pricingCurrency)
        }
    }
}