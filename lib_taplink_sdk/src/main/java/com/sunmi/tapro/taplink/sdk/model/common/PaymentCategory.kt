package com.sunmi.tapro.taplink.sdk.model.common

/**
 * 支付方式类别枚举
 * 
 * 定义所有支持的支付方式类别
 * 
 * @author TaPro Team
 * @since 2025-01-XX
 */
enum class PaymentCategory {
    /**
     * 银行卡（通用）
     */
    CARD,
    
    /**
     * 信用卡
     */
    CARD_CREDIT,
    
    /**
     * 借记卡
     */
    CARD_DEBIT,
    
    /**
     * 二维码主扫模式（商户扫客户）
     */
    QR_MPM,
    
    /**
     * 二维码被扫模式（客户扫商户）
     */
    QR_CPM,
    
    /**
     * 电子福利转账
     */
    EBT,
    
    /**
     * 现金
     */
    CASH;
    
    companion object {
        /**
         * 从字符串转换为枚举
         * 
         * @param value 字符串值
         * @return PaymentCategory? 对应的枚举值，如果无法识别则返回 null
         */
        fun fromString(value: String?): PaymentCategory? {
            if (value.isNullOrBlank()) return null
            
            return when (value.uppercase()) {
                "CARD" -> CARD
                "CARD-CREDIT" -> CARD_CREDIT
                "CARD-DEBIT" -> CARD_DEBIT
                "QR-MPM" -> QR_MPM
                "QR-CPM" -> QR_CPM
                "EBT" -> EBT
                "CASH" -> CASH
                else -> null
            }
        }
    }
    
    /**
     * 转换为字符串（用于API传输）
     */
    fun toApiString(): String {
        return when (this) {
            CARD -> "CARD"
            CARD_CREDIT -> "CARD-CREDIT"
            CARD_DEBIT -> "CARD-DEBIT"
            QR_MPM -> "QR-MPM"
            QR_CPM -> "QR-CPM"
            EBT -> "EBT"
            CASH -> "CASH"
        }
    }
}


