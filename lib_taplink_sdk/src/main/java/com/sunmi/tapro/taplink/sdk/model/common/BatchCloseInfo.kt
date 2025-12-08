package com.sunmi.tapro.taplink.sdk.model.common

import java.math.BigDecimal

/**
 * 批次关闭信息类
 * 
 * 用于批次关闭时返回批次汇总信息
 * 
 * @author TaPro Team
 * @since 2025-01-XX
 */
data class BatchCloseInfo(
    /**
     * 交易数量（仅批次关闭）
     */
    val totalCount: Int? = null,
    
    /**
     * 批次总金额（仅批次关闭）
     */
    val totalAmount: BigDecimal? = null,

    /**
     * 总小费金额
     * 可选
     */
    val totalTip: BigDecimal? = null,

    /**
     * 总税金额
     * 可选
     */
    val totalTax: BigDecimal? = null,

    /**
     * 总附加费金额
     * 可选
     */
    val totalSurchargeAmount: BigDecimal? = null,

    /**
     * 总服务费金额
     * 可选
     */
    val totalServiceFee: BigDecimal? = null,

    /**
     * 总现金优惠金额
     * 可选
     */
    val cashDiscount: BigDecimal? = null,
    
    /**
     * 批次关闭时间（仅批次关闭，ISO 8601 格式）
     */
    val closeTime: String? = null
)


