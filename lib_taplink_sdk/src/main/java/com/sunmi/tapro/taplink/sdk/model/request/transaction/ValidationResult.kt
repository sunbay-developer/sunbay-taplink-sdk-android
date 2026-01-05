package com.sunmi.tapro.taplink.sdk.model.request.transaction

/**
 * 验证结果类
 *
 * 包含验证是否成功和具体的错误信息
 *
 * @param isValid 是否验证通过
 * @param errors 错误列表，验证失败时包含具体错误信息
 *
 * @author TaPro Team
 * @since 2025-01-XX
 */
data class ValidationResult(
    val isValid: Boolean,
    val errors: List<ValidationError> = emptyList()
) {
    companion object {
        /**
         * 创建成功的验证结果
         */
        fun success(): ValidationResult = ValidationResult(true)
        
        /**
         * 创建失败的验证结果
         * 
         * @param errors 错误列表
         */
        fun failure(vararg errors: ValidationError): ValidationResult = 
            ValidationResult(false, errors.toList())
        
        /**
         * 创建失败的验证结果
         * 
         * @param errors 错误列表
         */
        fun failure(errors: List<ValidationError>): ValidationResult = 
            ValidationResult(false, errors)
    }
    
    /**
     * 获取错误信息字符串
     */
    fun getErrorMessage(): String = errors.joinToString(", ") { it.message }
}