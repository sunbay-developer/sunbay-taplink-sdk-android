package com.sunmi.tapro.taplink.sdk.model.common

/**
 * Payment method ID enumeration.
 * 
 * Defines all supported specific payment method IDs.
 * 
 * @author TaPro Team
 * @since 2025-01-XX
 */
enum class PaymentMethodId {
    // ========== QR Code Payment ==========
    /**
     * WeChat Pay.
     */
    WECHAT,
    
    /**
     * Alipay.
     */
    ALIPAY,
    
    // ========== EBT Sub-payment Methods ==========
    /**
     * SNAP (Supplemental Nutrition Assistance Program).
     */
    SNAP,
    
    /**
     * Voucher.
     */
    VOUCHER,
    
    /**
     * Withdraw.
     */
    WITHDRAW,
    
    /**
     * Benefit.
     */
    BENEFIT;
    
    companion object {
        /**
         * Converts from string to enum.
         * 
         * @param value the string value
         * @return the corresponding enum value, or null if not recognized
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
     * Converts to string (for API transmission).
     */
    fun toApiString(): String {
        return name
    }
}

