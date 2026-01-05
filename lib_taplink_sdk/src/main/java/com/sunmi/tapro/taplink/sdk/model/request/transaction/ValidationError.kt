package com.sunmi.tapro.taplink.sdk.model.request.transaction

/**
 * 验证错误类
 *
 * 定义所有可能的验证错误类型
 *
 * @param message 错误消息
 *
 * @author TaPro Team
 * @since 2025-01-XX
 */
sealed class ValidationError(val message: String) {
    
    // 基础字段验证错误
    object MissingTransactionRequestId : ValidationError("transactionRequestId is required")
    object MissingReferenceOrderId : ValidationError("referenceOrderId is required")
    object MissingAmount : ValidationError("amount is required")
    object MissingDescription : ValidationError("description is required")
    
    // 金额验证错误
    object InvalidAmount : ValidationError("amount must be positive")
    object InvalidTipAmount : ValidationError("tipAmount must be non-negative")
    
    // 原始交易引用验证错误
    object MissingOriginalTransaction : ValidationError("Either originalTransactionId or originalTransactionRequestId is required")
    
    // 退款模式验证错误
    object InvalidRefundMode : ValidationError("Refund must be either referenced or non-referenced, not both")
    
    // 自定义验证错误
    data class CustomError(val customMessage: String) : ValidationError(customMessage)
}