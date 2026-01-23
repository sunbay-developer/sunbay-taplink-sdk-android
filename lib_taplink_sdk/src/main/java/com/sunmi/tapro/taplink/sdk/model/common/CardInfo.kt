package com.sunmi.tapro.taplink.sdk.model.common

/**
 * Card information.
 * 
 * @author TaPro Team
 * @since 2025-01-XX
 */
data class CardInfo(
    /**
     * Masked card number (format: 411111******1111).
     */
    val maskedPan: String? = null,
    
    /**
     * Card network type.
     * Values: CREDIT, DEBIT, EBT, EGC, UNKNOWN
     */
    val cardNetworkType: String? = null,
    
    /**
     * Payment method ID.
     * Values: VISA, MASTERCARD, AMEX, DISCOVER, UNIONPAY, etc.
     */
    val paymentMethodId: String? = null,
    
    /**
     * Sub-payment method ID.
     * Values: SNAP, VOUCHER, WITHDRAW, BENEFIT, etc. (used for EBT transactions)
     */
    val subPaymentMethodId: String? = null,
    
    /**
     * Entry mode.
     * Values: MANUAL, SWIPE, FALLBACK_SWIPE, CONTACT, CONTACTLESS
     */
    val entryMode: String? = null,
    
    /**
     * Authentication method.
     * Values: NOT_AUTHENTICATED, PIN, OFFLINE_PIN, BY_PASS, SIGNATURE
     */
    val authenticationMethod: String? = null,
    
    /**
     * Cardholder name (optional).
     */
    val cardholderName: String? = null,
    
    /**
     * Expiry date (format: MM/YY, optional).
     */
    val expiryDate: String? = null,
    
    /**
     * Issuer bank (optional).
     */
    val issuerBank: String? = null,
    
    /**
     * Card brand (optional).
     */
    val cardBrand: String? = null
)



