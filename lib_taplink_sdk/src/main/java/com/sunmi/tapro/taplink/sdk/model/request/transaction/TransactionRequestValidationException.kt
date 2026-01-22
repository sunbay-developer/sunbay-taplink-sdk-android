package com.sunmi.tapro.taplink.sdk.model.request.transaction

/**
 * 交易请求验证异常
 *
 * 当交易请求验证失败时抛出的异常
 *
 * @param errors 验证错误列表
 *
 * @author TaPro Team
 * @since 2025-01-XX
 */
class TransactionRequestValidationException(
    val errors: List<ValidationError>
) : IllegalArgumentException("Validation failed: ${errors.joinToString(", ") { it.message }}") {
    
    /**
     * 构造函数，接受单个验证错误
     * 
     * @param error 验证错误
     */
    constructor(error: ValidationError) : this(listOf(error))
    
    /**
     * 构造函数，接受验证结果
     * 
     * @param validationResult 验证结果
     */
    constructor(validationResult: ValidationResult) : this(validationResult.errors) {
        require(!validationResult.isValid) { "Cannot create exception from successful validation result" }
    }
    
    /**
     * 获取第一个错误信息
     */
    fun getFirstErrorMessage(): String? = errors.firstOrNull()?.message
    
    /**
     * 获取所有错误信息
     */
    fun getAllErrorMessages(): List<String> = errors.map { it.message }
}