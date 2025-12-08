package com.sunmi.tapro.taplink.sdk.model.response

/**
 * 批次关闭结果
 * 
 * @author TaPro Team
 * @since 2025-01-XX
 */
data class BatchCloseResult(
    /** 批次号 */
    val batchNo: String,
    
    /** 总笔数 */
    val totalCount: Int,
    
    /** 总金额（分/美分等最小单位） */
    val totalAmount: Int,
    
    /** 销售笔数 */
    val saleCount: Int,
    
    /** 销售金额（分/美分等最小单位） */
    val saleAmount: Int,
    
    /** 退款笔数 */
    val refundCount: Int,
    
    /** 退款金额（分/美分等最小单位） */
    val refundAmount: Int,
    
    /** 货币代码（ISO 4217） */
    val currency: String? = null,
    
    /** 批次关闭时间（ISO 8601 格式） */
    val closeTime: String? = null,
    
    /** 结果描述 */
    val message: String? = null
) {
    /**
     * 是否成功
     */
    fun isSuccess(): Boolean = batchNo.isNotEmpty() && totalCount >= 0
}



