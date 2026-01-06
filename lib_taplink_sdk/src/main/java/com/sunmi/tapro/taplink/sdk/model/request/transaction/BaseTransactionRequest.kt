package com.sunmi.tapro.taplink.sdk.model.request.transaction

/**
 * 交易请求基类
 *
 * 所有交易请求类的抽象基类，提供通用的验证接口
 *
 * @author TaPro Team
 * @since 2025-01-XX
 */
abstract class BaseTransactionRequest {

    /**
     * 验证请求参数
     *
     * @return ValidationResult 验证结果，包含是否有效和错误列表
     */
    abstract fun validate(): ValidationResult
}