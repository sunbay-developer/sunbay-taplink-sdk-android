package com.sunmi.tapro.taplink.sdk.model.request.transaction

import java.math.BigDecimal

/**
 * 预授权金额信息
 *
 * 用于预授权相关交易的简化金额结构，只包含基本金额和币种
 *
 * @param orderAmount 交易金额，使用基本货币单位（如美元）
 * @param pricingCurrency 标价币种，遵循ISO 4217标准
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
         * 创建AuthAmountInfo的构建器
         */
        fun builder(): Builder = Builder()
    }
    
    /**
     * AuthAmountInfo构建器
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