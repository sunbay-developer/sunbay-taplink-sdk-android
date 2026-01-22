package com.sunmi.tapro.taplink.sdk.model.common

import java.math.BigDecimal

/**
 * 批次金额类
 * 
 * 用于批次关闭时返回批次总金额信息
 * 
 * @author TaPro Team
 * @since 2025-01-XX
 */
data class BatchAmount(
    /**
     * 定价货币（ISO 4217 标准，如 "USD", "EUR"）
     */
    val priceCurrency: String,
    
    /**
     * 总金额（单位：基本货币单位）
     */
    val amount: BigDecimal
)

