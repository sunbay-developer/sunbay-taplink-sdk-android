package com.sunmi.tapro.taplink.sdk.model.common

/**
 * 卡片信息
 * 
 * @author TaPro Team
 * @since 2025-01-XX
 */
data class CardInfo(
    /**
     * 掩码卡号（格式：411111******1111）
     */
    val maskedPan: String? = null,
    
    /**
     * 卡网络类型
     * 值：CREDIT, DEBIT, EBT, EGC, UNKNOWN
     */
    val cardNetworkType: String? = null,
    
    /**
     * 支付方式 ID
     * 值：VISA, MASTERCARD, AMEX, DISCOVER, UNIONPAY 等
     */
    val paymentMethodId: String? = null,
    
    /**
     * 子支付方式 ID
     * 值：SNAP, VOUCHER, WITHDRAW, BENEFIT 等（EBT 交易时使用）
     */
    val subPaymentMethodId: String? = null,
    
    /**
     * 输入模式
     * 值：MANUAL, SWIPE, FALLBACK_SWIPE, CONTACT, CONTACTLESS
     */
    val entryMode: String? = null,
    
    /**
     * 认证方式
     * 值：NOT_AUTHENTICATED, PIN, OFFLINE_PIN, BY_PASS, SIGNATURE
     */
    val authenticationMethod: String? = null,
    
    /** 持卡人姓名（可选） */
    val cardholderName: String? = null,
    
    /** 有效期（格式：MM/YY，可选） */
    val expiryDate: String? = null,
    
    /** 发卡行（可选） */
    val issuerBank: String? = null,
    
    /** 卡品牌（可选） */
    val cardBrand: String? = null
)



