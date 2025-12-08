package com.sunmi.tapro.taplink.sdk.model.common

/**
 * 小票信息
 * 
 * @author TaPro Team
 * @since 2025-01-XX
 */
data class Receipt(
    /** 商户联小票内容（文本格式） */
    val merchantCopy: String? = null,
    
    /** 客户联小票内容（文本格式） */
    val customerCopy: String? = null,
    
    /** 是否需要签名：true/false */
    val signatureRequired: Boolean = false,
    
    /** 签名图片（Base64 编码） */
    val signatureImage: ByteArray? = null
)



