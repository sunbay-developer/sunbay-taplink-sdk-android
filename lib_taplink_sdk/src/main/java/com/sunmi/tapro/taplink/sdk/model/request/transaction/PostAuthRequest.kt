package com.sunmi.tapro.taplink.sdk.model.request.transaction

import com.sunmi.tapro.taplink.sdk.model.common.AmountInfo
import com.sunmi.tapro.taplink.sdk.model.common.StaffInfo

/**
 * 预授权完成交易请求
 *
 * 用于完成预授权交易，将预授权金额转为实际扣款
 *
 * @param originalTransactionId 原始预授权交易ID（与originalTransactionRequestId二选一）
 * @param originalTransactionRequestId 原始预授权交易请求ID（与originalTransactionId二选一）
 * @param transactionRequestId 交易请求ID（必需）
 * @param amount 完成金额信息（必需，可以小于等于预授权金额）
 * @param description 交易描述（可选，最多128字符）
 * @param attach 附加信息（可选）
 * @param notifyUrl 通知URL（可选）
 * @param requestTimeout 请求超时时间（可选，单位：秒）
 * @param staffInfo 员工信息（可选）
 *
 * @author TaPro Team
 * @since 2025-01-XX
 */
data class PostAuthRequest(
    val originalTransactionId: String? = null,
    val originalTransactionRequestId: String? = null,
    val transactionRequestId: String,
    val amount: AmountInfo,
    val description: String? = null,
    val attach: String? = null,
    val notifyUrl: String? = null,
    val requestTimeout: Long? = null,
    val staffInfo: StaffInfo? = null
) : BaseTransactionRequest() {

    init {
        require(originalTransactionId != null || originalTransactionRequestId != null) {
            "Either originalTransactionId or originalTransactionRequestId must be provided"
        }
    }

    override fun validate(): ValidationResult {
        return TransactionRequestValidator.combineResults(
            TransactionRequestValidator.validateOriginalTransactionReference(
                originalTransactionId, 
                originalTransactionRequestId
            ),
            TransactionRequestValidator.validateTransactionRequestId(transactionRequestId),
            TransactionRequestValidator.validateAmount(amount)
        )
    }

    companion object {
        /**
         * 创建PostAuthRequest构建器
         */
        fun builder(): Builder = Builder()
    }

    /**
     * PostAuthRequest构建器
     */
    class Builder {
        private var originalTransactionId: String? = null
        private var originalTransactionRequestId: String? = null
        private var transactionRequestId: String? = null
        private var amount: AmountInfo? = null
        private var description: String? = null
        private var attach: String? = null
        private var notifyUrl: String? = null
        private var requestTimeout: Long? = null
        private var staffInfo: StaffInfo? = null

        /**
         * 设置原始预授权交易ID
         */
        fun setOriginalTransactionId(originalTransactionId: String): Builder {
            this.originalTransactionId = originalTransactionId
            return this
        }

        /**
         * 设置原始预授权交易请求ID
         */
        fun setOriginalTransactionRequestId(originalTransactionRequestId: String): Builder {
            this.originalTransactionRequestId = originalTransactionRequestId
            return this
        }

        /**
         * 设置交易请求ID
         */
        fun setTransactionRequestId(transactionRequestId: String): Builder {
            this.transactionRequestId = transactionRequestId
            return this
        }

        /**
         * 设置完成金额信息
         */
        fun setAmount(amount: AmountInfo): Builder {
            this.amount = amount
            return this
        }

        /**
         * 设置交易描述
         */
        fun setDescription(description: String?): Builder {
            this.description = description
            return this
        }

        /**
         * 设置附加信息
         */
        fun setAttach(attach: String): Builder {
            this.attach = attach
            return this
        }

        /**
         * 设置通知URL
         */
        fun setNotifyUrl(notifyUrl: String): Builder {
            this.notifyUrl = notifyUrl
            return this
        }

        /**
         * 设置请求超时时间
         */
        fun setRequestTimeout(requestTimeout: Long): Builder {
            this.requestTimeout = requestTimeout
            return this
        }

        /**
         * 设置员工信息
         */
        fun setStaffInfo(staffInfo: StaffInfo): Builder {
            this.staffInfo = staffInfo
            return this
        }

        /**
         * 构建PostAuthRequest实例
         * 
         * @throws TransactionRequestValidationException 如果验证失败
         */
        fun build(): PostAuthRequest {
            val request = PostAuthRequest(
                originalTransactionId = originalTransactionId,
                originalTransactionRequestId = originalTransactionRequestId,
                transactionRequestId = requireNotNull(transactionRequestId) { "transactionRequestId is required" },
                amount = requireNotNull(amount) { "amount is required" },
                description = description,
                attach = attach,
                notifyUrl = notifyUrl,
                requestTimeout = requestTimeout,
                staffInfo = staffInfo
            )

            val validationResult = request.validate()
            if (!validationResult.isValid) {
                throw TransactionRequestValidationException(validationResult.errors)
            }

            return request
        }
    }
}