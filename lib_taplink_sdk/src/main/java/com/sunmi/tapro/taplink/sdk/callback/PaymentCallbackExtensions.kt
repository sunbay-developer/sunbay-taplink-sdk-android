package com.sunmi.tapro.taplink.sdk.callback

import com.google.gson.JsonParser
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
        errorMessage
    } else {
        errorCode.description
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

    // Try to parse structured JSON msg from Tapro (format: {"message":"...","suggestion":"...","traceId":"..."})
    val parsed = parseStructuredErrorMsg(errorMsg)
    val message = parsed?.message ?: errorMsg ?: errorCode.description
    val suggestion = parsed?.suggestion
        ?: ErrorStringHelper.getSolution(code) ?: ""
    val resolvedTraceId = parsed?.traceId ?: traceId

    val paymentError = PaymentError.create(
        code = code,
        message = message,
        suggestion = suggestion,
        traceId = resolvedTraceId,
        referenceOrderId = referenceOrderId,
        transactionId = transactionId,
        transactionRequestId = transactionRequestId
    )

    onFailure(paymentError)
}

/**
 * Parse structured JSON error message from Tapro service.
 * Returns null if msg is not valid JSON or doesn't have the expected structure.
 * This ensures backward compatibility — plain string messages are used as-is.
 */
private fun parseStructuredErrorMsg(msg: String?): ParsedErrorMsg? {
    if (msg.isNullOrBlank()) return null
    return try {
        val json = JsonParser.parseString(msg).asJsonObject
        ParsedErrorMsg(
            message = json.get("message")?.asString,
            suggestion = json.get("suggestion")?.asString,
            traceId = json.get("traceId")?.asString
        )
    } catch (e: Exception) {
        // Not a JSON string — this is fine, just use as plain text
        null
    }
}

private data class ParsedErrorMsg(
    val message: String?,
    val suggestion: String?,
    val traceId: String?
)
