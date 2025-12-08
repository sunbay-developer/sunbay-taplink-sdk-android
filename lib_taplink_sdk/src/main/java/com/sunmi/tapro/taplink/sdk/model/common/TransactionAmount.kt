package com.sunmi.tapro.taplink.sdk.model.common

import java.math.BigDecimal

/**
 * 交易金额类
 * 
 * 包含详细的金额明细信息
 * 
 * @author TaPro Team
 * @since 2025-01-XX
 */
data class TransactionAmount(
    /**
     * 定价货币（ISO 4217 标准，如 "USD", "EUR"）
     */
    val priceCurrency: String,
    
    /**
     * 交易金额（单位：基本货币单位）
     */
    val transAmount: BigDecimal,
    
    /**
     * 订单金额（单位：基本货币单位）
     */
    val orderAmount: BigDecimal,
    
    /**
     * 税额（单位：基本货币单位）
     */
    val taxAmount: BigDecimal? = null,
    
    /**
     * 服务费（单位：基本货币单位）
     */
    val serviceFee: BigDecimal? = null,
    
    /**
     * 附加费金额（单位：基本货币单位）
     */
    val surchargeAmount: BigDecimal? = null,
    
    /**
     * 小费金额（单位：基本货币单位）
     */
    val tipAmount: BigDecimal? = null,
    
    /**
     * 现金返还金额（单位：基本货币单位）
     */
    val cashbackAmount: BigDecimal? = null
)

