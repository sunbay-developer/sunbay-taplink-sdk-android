package com.sunmi.tapro.taplink.sdk.model.common

/**
 * 支付方式ID枚举
 * 
 * 定义所有支持的具体支付方式ID
 * 
 * @author TaPro Team
 * @since 2025-01-XX
 */
enum class PaymentMethodId {
    // ========== 二维码支付 ==========
    /**
     * 微信支付
     */
    WECHAT,
    
    /**
     * 支付宝
     */
    ALIPAY,
    
    // ========== EBT 子支付方式 ==========
    /**
     * SNAP（补充营养援助计划）
     */
    SNAP,
    
    /**
     * Voucher（代金券）
     */
    VOUCHER,
    
    /**
     * Withdraw（提现）
     */
    WITHDRAW,
    
    /**
     * Benefit（福利）
     */
    BENEFIT;
    
    companion object {
        /**
         * 从字符串转换为枚举
         * 
         * @param value 字符串值
         * @return PaymentMethodId? 对应的枚举值，如果无法识别则返回 null
         */
        fun fromString(value: String?): PaymentMethodId? {
            if (value.isNullOrBlank()) return null
            
            return try {
                valueOf(value.uppercase().replace("-", "_"))
            } catch (e: IllegalArgumentException) {
                null
            }
        }
    }
    
    /**
     * 转换为字符串（用于API传输）
     */
    fun toApiString(): String {
        return name
    }
}

