package com.sunmi.tapro.taplink.sdk.model.common

/**
 * Payment Method ID Enum
 * 
 * Defines all supported specific payment method IDs
 * 
 * @author TaPro Team
 * @since 2025-01-XX
 */
enum class PaymentMethodId {
    // ========== QR Code Payment ==========
    /**
     * WeChat Pay
     */
    WECHAT,
    
    /**
     * Alipay
     */
    ALIPAY,
    
    // ========== EBT Sub-payment Methods ==========
    /**
     * SNAP (Supplemental Nutrition Assistance Program)
     */
    SNAP,
    
    /**
     * Voucher
     */
    VOUCHER,
    
    /**
     * Withdraw
     */
    WITHDRAW,
    
    /**
     * Benefit
     */
    BENEFIT;
    
    companion object {
        /**
         * Convert from string to enum
         * 
         * @param value String value
         * @return PaymentMethodId? Corresponding enum value, returns null if not recognized
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
     * Convert to string (for API transmission)
     */
    fun toApiString(): String {
        return name
    }
}

