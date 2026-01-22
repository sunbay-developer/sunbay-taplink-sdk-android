package com.sunmi.tapro.taplink.sdk.callback

import com.sunmi.tapro.taplink.communication.enums.InnerErrorCode
import com.sunmi.tapro.taplink.communication.util.ErrorStringHelper
import com.sunmi.tapro.taplink.communication.util.LogUtil
import com.sunmi.tapro.taplink.sdk.error.PaymentError

/**
 * PaymentCallback 扩展函数
 *
 * 提供便捷的错误处理方法来减少重复代码
 *
 * @author TaPro Team
 * @since 2025-01-XX
 */

/**
 * 使用 InnerErrorCode 对象创建并调用 onFailure
 *
 * @param errorCode 错误码对象
 * @param additionalMessage 额外的错误消息（可选，会追加到错误描述后）
 * @param traceId 追踪 ID（可选）
 * @param referenceOrderId 商户订单号（可选）
 * @param transactionId 交易 ID（可选）
 * @param transactionRequestId 交易请求 ID（可选）
 */
fun PaymentCallback.onFailure(
    errorCode: InnerErrorCode,
    errorMessage: String? = null,
    traceId: String? = null,
    referenceOrderId: String? = null,
    transactionId: String? = null,
    transactionRequestId: String? = null
) {
    val message = if (errorMessage != null) {
        "${errorCode.code}:$errorMessage"
    } else {
        "${errorCode.code}:${errorCode.description}"
    }

    val paymentError = PaymentError.create(
        code = errorCode.code,
        message = message,
        suggestion = ErrorStringHelper.getSolution(errorCode.code) ?: "",
        traceId = traceId,
        referenceOrderId = referenceOrderId,
        transactionId = transactionId,
        transactionRequestId = transactionRequestId
    )

    onFailure(paymentError)
}

/**
 * 使用错误码字符串创建并调用 onFailure
 *
 * @param code 错误码字符串
 * @param additionalMessage 额外的错误消息（可选，会追加到错误描述后）
 * @param traceId 追踪 ID（可选）
 * @param referenceOrderId 商户订单号（可选）
 * @param transactionId 交易 ID（可选）
 * @param transactionRequestId 交易请求 ID（可选）
 */
fun PaymentCallback.onFailure(
    code: String,
    errorMsg: String? = null,
    traceId: String? = null,
    referenceOrderId: String? = null,
    transactionId: String? = null,
    transactionRequestId: String? = null
) {
    val errorCode = InnerErrorCode.fromCode(code, errorMsg)
    val message = "${errorCode.code}:${errorCode.description}"

    val paymentError = PaymentError.create(
        code = code,
        message = message,
        suggestion = ErrorStringHelper.getSolution(code) ?: "",
        traceId = traceId,
        referenceOrderId = referenceOrderId,
        transactionId = transactionId,
        transactionRequestId = transactionRequestId
    )

    onFailure(paymentError)
}
