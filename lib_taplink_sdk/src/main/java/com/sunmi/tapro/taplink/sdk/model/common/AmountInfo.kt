package com.sunmi.tapro.taplink.sdk.model.common

import java.math.BigDecimal

/**
 * 金额信息类
 * 
 * 包含详细的金额明细信息
 * 
 * @author TaPro Team
 * @since 2025-01-XX
 */
data class AmountInfo(
    /**
     * 订单金额（必需，单位：基本货币单位，如美元）
     */
    val orderAmount: BigDecimal,
    
    /**
     * 定价货币（必需，ISO 4217 标准，如 "USD", "EUR"）
     */
    val pricingCurrency: String,
    
    /**
     * 小费金额（可选，单位：基本货币单位）
     */
    val tipAmount: BigDecimal? = null,
    
    /**
     * 税额（可选，单位：基本货币单位）
     */
    val taxAmount: BigDecimal? = null,
    
    /**
     * 附加费金额（可选，单位：基本货币单位）
     */
    val surchargeAmount: BigDecimal? = null,
    
    /**
     * 现金返还金额（可选，单位：基本货币单位）
     */
    val cashbackAmount: BigDecimal? = null,
    
    /**
     * 服务费（可选，单位：基本货币单位）
     */
    val serviceFee: BigDecimal? = null
) {
    /**
     * 链式调用：设置订单金额
     */
    fun setOrderAmount(orderAmount: BigDecimal): AmountInfo = copy(orderAmount = orderAmount)
    
    /**
     * 链式调用：设置定价货币
     */
    fun setPricingCurrency(pricingCurrency: String): AmountInfo = copy(pricingCurrency = pricingCurrency)
    
    /**
     * 链式调用：设置小费金额
     */
    fun setTipAmount(tipAmount: BigDecimal): AmountInfo = copy(tipAmount = tipAmount)
    
    /**
     * 链式调用：设置税额
     */
    fun setTaxAmount(taxAmount: BigDecimal): AmountInfo = copy(taxAmount = taxAmount)
    
    /**
     * 链式调用：设置附加费金额
     */
    fun setSurchargeAmount(surchargeAmount: BigDecimal): AmountInfo = copy(surchargeAmount = surchargeAmount)
    
    /**
     * 链式调用：设置现金返还金额
     */
    fun setCashbackAmount(cashbackAmount: BigDecimal): AmountInfo = copy(cashbackAmount = cashbackAmount)
    
    /**
     * 链式调用：设置服务费
     */
    fun setServiceFee(serviceFee: BigDecimal): AmountInfo = copy(serviceFee = serviceFee)
}

