package com.sunmi.tapro.taplink.sdk.model.request.transaction.settlement

import com.sunmi.tapro.taplink.sdk.model.request.transaction.BaseTransactionRequest
import com.sunmi.tapro.taplink.sdk.model.request.transaction.ValidationResult

/**
 * 批次查询交易请求
 *
 * 用于查询当前批次的交易信息，不需要任何参数
 * 查询结果基于当前终端的上下文
 *
 * @author TaPro Team
 * @since 2025-01-XX
 */
data class BatchQueryRequest(
    // 批次查询不需要任何字段，基于上下文进行查询
    private val placeholder: Unit = Unit
) : BaseTransactionRequest() {

    override fun validate(): ValidationResult {
        // 批次查询不需要验证任何字段
        return ValidationResult.success()
    }

    companion object {
        /**
         * 创建BatchQueryRequest实例
         */
        fun create(): BatchQueryRequest = BatchQueryRequest()

        /**
         * 创建BatchQueryRequest构建器
         */
        fun builder(): Builder = Builder()
    }

    /**
     * BatchQueryRequest构建器
     */
    class Builder {
        /**
         * 构建BatchQueryRequest实例
         */
        fun build(): BatchQueryRequest {
            return BatchQueryRequest()
        }
    }
}