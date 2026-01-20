package com.sunmi.tapro.taplink.sdk.impl

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.sunmi.tapro.taplink.communication.enums.InnerErrorCode
import com.sunmi.tapro.taplink.sdk.callback.PaymentCallback
import com.sunmi.tapro.taplink.sdk.error.PaymentError
import com.sunmi.tapro.taplink.sdk.model.base.BasicResponse
import com.sunmi.tapro.taplink.sdk.model.common.PaymentEvent
import com.sunmi.tapro.taplink.sdk.model.request.PaymentRequest
import com.sunmi.tapro.taplink.sdk.model.response.PaymentResult
import com.sunmi.tapro.taplink.communication.interfaces.InnerCallback
import com.sunmi.tapro.taplink.communication.util.ErrorStringHelper
import com.sunmi.tapro.taplink.communication.util.LocalCallbackManager
import com.sunmi.tapro.taplink.communication.util.LogUtil

/**
 * Response processing class
 *
 * Handles all response-related operations including:
 * - Response data parsing
 * - Event code extraction
 * - Callback routing
 * - Error handling
 *
 * @author TaPro Team
 * @since 2025-12-22
 */
class ResponseProcessor {
    private val TAG = "ResponseProcessor"

    companion object {
        private const val ERROR_EVENT_CODE = "ERROR"
        const val SUCCESS_CODE = "100"
    }

    /**
     * Gson instance for JSON parsing
     */
    private val gson = Gson()

    init {
        LogUtil.d(TAG, "ResponseProcessor initialized")
    }

    /**
     * Process received response and route to appropriate callback
     */
    fun processResponse(responseJson: String, callbackManager: LocalCallbackManager<InnerCallback?>) {
        try {
            val jsonObject = JsonParser.parseString(responseJson).asJsonObject
            val traceId = jsonObject.get("traceId")?.asString
            val eventCode = jsonObject.get("eventCode")?.asString?.uppercase()

            if (traceId.isNullOrBlank()) {
                LogUtil.e(TAG, "Received data without traceId, ignoring: $responseJson")
                return
            }

            // Determine if this is an error response
            val isError = eventCode == ERROR_EVENT_CODE

            // Determine if callback should be removed
            val shouldRemove = shouldRemoveCallback(responseJson) || isError

            val callback = if (shouldRemove) {
                callbackManager.getAndRemoveCallbackByTraceId(traceId)
            } else {
                callbackManager.getCallbackByTraceId(traceId)
            }

            // Call appropriate callback method based on response type
            if (isError) {
                val errorCode = jsonObject.get("errorCode")?.asString ?: "UNKNOWN_ERROR"
                val errorMsg = jsonObject.get("eventMsg")?.asString ?: "Unknown error"
                callback?.onError(errorCode, errorMsg)
            } else {
                callback?.onResponse(responseJson)
            }
        } catch (e: Exception) {
            // If unable to parse as JSON, might be old format error response
            LogUtil.e(TAG, "Failed to parse response as JSON: $responseJson, error=${e.message}")
        }
    }

    /**
     * Handle response data for specific payment request
     */
    fun handleResponse(result: String, callback: PaymentCallback, request: PaymentRequest) {
        try {
            val basicResponse = gson.fromJson(result, BasicResponse::class.java)
            LogUtil.d(
                TAG,
                "Received BasicResponse: eventCode=${basicResponse.event.eventCode}, eventMsg=${basicResponse.event.eventMsg}"
            )

            // Parse PaymentResult from bizData
            val paymentResult = if (basicResponse.bizData != null) {
                gson.fromJson(basicResponse.bizData, PaymentResult::class.java)
            } else {
                // If bizData is empty, create a default PaymentResult
                PaymentResult(
                    code = "",
                    message = basicResponse.event.eventMsg
                )
            }

            // First check code
            if (paymentResult.code != SUCCESS_CODE) {
                // Code is not 0, call onFailure
                LogUtil.e(TAG, "Payment failed: code=${paymentResult.code}, message=${paymentResult.message}")
                val errorCode = InnerErrorCode.fromCode(paymentResult.code, paymentResult.message)
                callback.onFailure(
                    PaymentError.create(
                        code = paymentResult.code,
                        message = if (paymentResult.message.isNullOrEmpty()) errorCode.description else paymentResult.message,
                        suggestion = ErrorStringHelper.getSolution(paymentResult.code) ?: "",
                        traceId = paymentResult.traceId,
                        transactionId = paymentResult.transactionId,
                        transactionRequestId = paymentResult.transactionRequestId
                    )
                )
            } else {
                // Code is 0, determine based on PaymentEvent type
                when (basicResponse.event) {
                    is PaymentEvent.Completed -> {
                        // Event is Completed, call onSuccess
                        LogUtil.d(
                            TAG,
                            "Payment completed successfully: eventCode=${basicResponse.event.eventCode}"
                        )
                        callback.onSuccess(paymentResult)
                    }

                    is PaymentEvent.Cancel -> {
                        // Event is Cancel, call onFailure
                        LogUtil.e(
                            TAG,
                            "Payment cancelled: eventCode=${basicResponse.event.eventCode}, eventMsg=${basicResponse.event.eventMsg}"
                        )
                        val errorCode = InnerErrorCode.fromCode(paymentResult.code)
                        callback.onFailure(
                            PaymentError.create(
                                code = paymentResult.code,
                                message = errorCode.description,
                                suggestion = ErrorStringHelper.getSolution(paymentResult.code) ?: "",
                                traceId = paymentResult.traceId,
                                transactionId = paymentResult.transactionId,
                                transactionRequestId = paymentResult.transactionRequestId
                            )
                        )
                    }

                    else -> {
                        // Other event types, call onProgress
                        LogUtil.d(
                            TAG,
                            "Payment in progress: eventCode=${basicResponse.event.eventCode}, eventMsg=${basicResponse.event.eventMsg}, progress=${basicResponse.event.progress}"
                        )
                        callback.onProgress(basicResponse.event)
                    }
                }
            }
        } catch (e: Exception) {
            // Parse failure, treat as send failure
            LogUtil.e(TAG, "Failed to parse response: action=${request.action}, result=$result, error=${e.message}")
            val errorCode = InnerErrorCode.E302
            callback.onFailure(
                PaymentError.create(
                    code = errorCode.code,
                    message = "${errorCode.description}(${e.message})",
                    suggestion = ErrorStringHelper.getSolution(errorCode.code) ?: ""
                )
            )
        }
    }

    /**
     * Determine if callback should be removed
     */
    private fun shouldRemoveCallback(responseJson: String): Boolean {
        return try {
            val eventCode = extractEventCode(responseJson)
            if (eventCode == null) {
                return false
            }

            // Use PaymentEvent.fromEventCode to determine if it's Completed or Cancel
            val paymentEvent = PaymentEvent.fromEventCode(eventCode)
            when (paymentEvent) {
                is PaymentEvent.Completed -> true
                is PaymentEvent.Cancel -> true
                else -> false
            }
        } catch (e: Exception) {
            LogUtil.e(TAG, "Failed to parse responseJson for shouldRemoveCallback: ${e.message}")
            true // Default to remove on parse failure
        }
    }

    /**
     * Extract eventCode from response JSON
     */
    private fun extractEventCode(responseJson: String): String? {
        return try {
            val jsonObject = JsonParser.parseString(responseJson).asJsonObject
            val eventCodeElement = jsonObject.get("eventCode")
            when {
                eventCodeElement?.isJsonPrimitive == true -> {
                    val primitive = eventCodeElement.asJsonPrimitive
                    when {
                        primitive.isString -> primitive.asString
                        primitive.isNumber -> primitive.asString
                        else -> null
                    }
                }

                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }
}