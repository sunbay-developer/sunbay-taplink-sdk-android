package com.sunmi.tapro.taplink.sdk.model.request.transaction

import com.sunmi.tapro.taplink.sdk.model.common.AmountInfo
import java.math.BigDecimal

/**
 * 交易请求验证器
 *
 * 提供通用的验证方法，用于验证交易请求的各个字段
 *
 * @author TaPro Team
 * @since 2025-01-XX
 */
object TransactionRequestValidator {

    /**
     * 验证交易请求ID
     *
     * @param transactionRequestId 交易请求ID
     * @return ValidationResult 验证结果
     */
    fun validateTransactionRequestId(transactionRequestId: String?): ValidationResult {
        return if (transactionRequestId.isNullOrBlank()) {
            ValidationResult.failure(ValidationError.MissingTransactionRequestId)
        } else {
            ValidationResult.success()
        }
    }

    /**
     * 验证参考订单号
     *
     * @param referenceOrderId 参考订单号
     * @return ValidationResult 验证结果
     */
    fun validateReferenceOrderId(referenceOrderId: String?): ValidationResult {
        return if (referenceOrderId.isNullOrBlank()) {
            ValidationResult.failure(ValidationError.MissingReferenceOrderId)
        } else if (referenceOrderId.length < 6 || referenceOrderId.length > 32) {
            ValidationResult.failure(ValidationError.CustomError("referenceOrderId must be 6-32 characters"))
        } else if (!referenceOrderId.matches(Regex("^[a-zA-Z0-9_\\-|*]+$"))) {
            ValidationResult.failure(ValidationError.CustomError("referenceOrderId can only contain letters, numbers, _, -, |, *"))
        } else {
            ValidationResult.success()
        }
    }

    /**
     * 验证金额信息
     *
     * @param amount 金额信息
     * @return ValidationResult 验证结果
     */
    fun validateAmount(amount: AmountInfo?): ValidationResult {
        return if (amount == null) {
            ValidationResult.failure(ValidationError.MissingAmount)
        } else if (amount.orderAmount <= BigDecimal.ZERO) {
            ValidationResult.failure(ValidationError.InvalidAmount)
        } else {
            ValidationResult.success()
        }
    }

    /**
     * 验证描述信息
     *
     * @param description 描述信息
     * @return ValidationResult 验证结果
     */
    fun validateDescription(description: String?): ValidationResult {
        return if (description.isNullOrBlank()) {
            ValidationResult.failure(ValidationError.MissingDescription)
        } else if (description.length > 128) {
            ValidationResult.failure(ValidationError.CustomError("description must not exceed 128 characters"))
        } else {
            ValidationResult.success()
        }
    }

    /**
     * 验证原始交易引用
     *
     * @param originalTransactionId 原始交易ID
     * @param originalTransactionRequestId 原始交易请求ID
     * @return ValidationResult 验证结果
     */
    fun validateOriginalTransactionReference(
        originalTransactionId: String?,
        originalTransactionRequestId: String?
    ): ValidationResult {
        return if (originalTransactionId.isNullOrBlank() && originalTransactionRequestId.isNullOrBlank()) {
            ValidationResult.failure(ValidationError.MissingOriginalTransaction)
        } else {
            ValidationResult.success()
        }
    }

    /**
     * 验证原始交易引用
     *
     * @param originalTransactionRequestId 原始交易请求ID
     * @return ValidationResult 验证结果
     */
    fun validateOriginalTransactionReference(
        originalTransactionRequestId: String?
    ): ValidationResult {
        return if (originalTransactionRequestId.isNullOrBlank()) {
            ValidationResult.failure(ValidationError.MissingOriginalTransaction)
        } else {
            ValidationResult.success()
        }
    }

    /**
     * 验证小费金额
     *
     * @param tipAmount 小费金额
     * @return ValidationResult 验证结果
     */
    fun validateTipAmount(tipAmount: Long): ValidationResult {
        return if (tipAmount < 0) {
            ValidationResult.failure(ValidationError.InvalidTipAmount)
        } else {
            ValidationResult.success()
        }
    }

    /**
     * 合并多个验证结果
     *
     * @param results 验证结果列表
     * @return ValidationResult 合并后的验证结果
     */
    fun combineResults(vararg results: ValidationResult): ValidationResult {
        val allErrors = results.flatMap { it.errors }
        return if (allErrors.isEmpty()) {
            ValidationResult.success()
        } else {
            ValidationResult.failure(allErrors)
        }
    }
}