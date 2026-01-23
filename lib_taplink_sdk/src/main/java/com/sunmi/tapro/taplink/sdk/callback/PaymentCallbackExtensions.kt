package com.sunmi.tapro.taplink.sdk.callback

import com.sunmi.tapro.taplink.communication.enums.InnerErrorCode
import com.sunmi.tapro.taplink.communication.util.ErrorStringHelper
import com.sunmi.tapro.taplink.communication.util.LogUtil
import com.sunmi.tapro.taplink.sdk.error.PaymentError

/**
 * PaymentCallback extension function
 *
 * Provide convenient error handling methods to reduce duplicate code
 *
 * @author TaPro Team
 * @since 2025-01-XX
 */

/**
 * Create and invoke onFailure using the InnerErrorCode object
 *
 * @param errorCode Error code object
 * @param additionalMessage Additional error messages (optional, will be appended after the error description)
 * @param traceId Tracking ID (optional)
 * @param referenceOrderId Merchant order Number (optional)
 * @param transactionId Transaction ID (optional)
 * @param transactionRequestId Transaction request ID(optional)
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
 * Create and call onFailure using the error code string
 *
 * @param errorCode Error code object
 * @param additionalMessage Additional error messages (optional, will be appended after the error description)
 * @param traceId Tracking ID (optional)
 * @param referenceOrderId Merchant order Number (optional)
 * @param transactionId Transaction ID (optional)
 * @param transactionRequestId Transaction request ID(optional)
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
