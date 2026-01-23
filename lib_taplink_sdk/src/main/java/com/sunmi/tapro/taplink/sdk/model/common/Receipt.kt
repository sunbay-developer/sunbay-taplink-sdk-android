package com.sunmi.tapro.taplink.sdk.model.common

/**
 * Receipt information.
 * 
 * @author TaPro Team
 * @since 2025-01-XX
 */
data class Receipt(
    /**
     * Merchant copy receipt content (text format).
     */
    val merchantCopy: String? = null,
    
    /**
     * Customer copy receipt content (text format).
     */
    val customerCopy: String? = null,
    
    /**
     * Whether signature is required: true/false.
     */
    val signatureRequired: Boolean = false,
    
    /**
     * Signature image (Base64 encoded).
     */
    val signatureImage: ByteArray? = null
)



