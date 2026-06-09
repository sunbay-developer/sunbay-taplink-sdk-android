package com.sunmi.tapro.taplink.sdk.impl

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.sunmi.tapro.taplink.communication.enums.InnerErrorCode
import com.sunmi.tapro.taplink.communication.util.ErrorStringHelper
import com.sunmi.tapro.taplink.communication.util.LocalCallbackManager
import com.sunmi.tapro.taplink.communication.interfaces.InnerCallback
import com.sunmi.tapro.taplink.sdk.callback.PaymentCallback
import com.sunmi.tapro.taplink.sdk.error.PaymentError
import com.sunmi.tapro.taplink.sdk.model.base.BasicResponse
import com.sunmi.tapro.taplink.sdk.model.base.BasicResponseJacksonDeserializer
import com.sunmi.tapro.taplink.sdk.model.common.PaymentEvent
import com.sunmi.tapro.taplink.sdk.model.request.PaymentRequest
import com.sunmi.tapro.taplink.sdk.model.response.PaymentResult
import com.sunmi.tapro.taplink.communication.util.LogUtil

/**
 * Response processing class — Gson-free, uses Jackson.
 *
 * @author TaPro Team
 * @since 2025-01-XX
 */
class ResponseProcessor {
    private val TAG = "ResponseProcessor"

    companion object {
        private const val ERROR_EVENT_CODE = "ERROR"
        const val SUCCESS_CODE = "100"
    }

    private val mapper: ObjectMapper = ObjectMapper()
        .registerKotlinModule()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

    // Mapper that knows how to deserialize BasicResponse (custom deserializer for PaymentEvent)
    private val responseMapper: ObjectMapper = ObjectMapper()
        .registerKotlinModule()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        .also { om ->
            val module = com.fasterxml.jackson.databind.module.SimpleModule()
            module.addDeserializer(BasicResponse::class.java, BasicResponseJacksonDeserializer())
            om.registerModule(module)
        }

    init {
        LogUtil.d(TAG, "ResponseProcessor initialized")
    }

    /**
     * Route low-level response JSON to the matching callback.
     */
    fun processResponse(responseJson: String, callbackManager: LocalCallbackManager<InnerCallback?>) {
        try {
            val node = mapper.readTree(responseJson)
            val traceId = node.get("traceId")?.asText()
            val eventCode = node.get("eventCode")?.asText()?.uppercase()

            if (traceId.isNullOrBlank()) {
                LogUtil.e(TAG, "Received data without traceId, ignoring: $responseJson")
                return
            }

            val isError = eventCode == ERROR_EVENT_CODE
            val shouldRemove = shouldRemoveCallback(responseJson) || isError

            val callback = if (shouldRemove) {
                callbackManager.getAndRemoveCallbackByTraceId(traceId)
            } else {
                callbackManager.getCallbackByTraceId(traceId)
            }

            if (isError) {
                val errorCode = node.get("errorCode")?.asText() ?: "UNKNOWN_ERROR"
                val errorMsg = node.get("eventMsg")?.asText() ?: "Unknown error"
                LogUtil.d(TAG, "[TAPLINK-TX] TraceId=$traceId | ECR Error received: code=$errorCode, msg=$errorMsg")
                callback?.onError(errorCode, errorMsg)
            } else {
                callback?.onResponse(responseJson)
            }
        } catch (e: Exception) {
            LogUtil.e(TAG, "Failed to parse response as JSON: $responseJson, error=${e.message}")
        }
    }

    /**
     * Deserialize a full response JSON and dispatch to the payment callback.
     */
    fun handleResponse(result: String, callback: PaymentCallback, request: PaymentRequest) {
        try {
            val basicResponse = responseMapper.readValue(result, BasicResponse::class.java)
            LogUtil.d(
                TAG,
                "Received BasicResponse: eventCode=${basicResponse.event.eventCode}, " +
                        "eventMsg=${basicResponse.event.eventMsg}"
            )

            val paymentResult = if (!basicResponse.bizData.isNullOrBlank()) {
                mapper.readValue(basicResponse.bizData, PaymentResult::class.java)
            } else {
                PaymentResult(code = "", message = basicResponse.event.eventMsg)
            }

            when (val event = basicResponse.event) {
                is PaymentEvent.Cancel -> {
                    LogUtil.d(
                        TAG,
                        "[TAPLINK-TX] TraceId=${paymentResult.traceId} | Cancel: " +
                                "code=${paymentResult.code}, status=${paymentResult.transactionStatus}"
                    )
                    callback.onSuccess(paymentResult)
                }
                is PaymentEvent.Completed -> {
                    LogUtil.d(
                        TAG,
                        "[TAPLINK-TX] TraceId=${paymentResult.traceId} | Completed: " +
                                "code=${paymentResult.code}, status=${paymentResult.transactionStatus}"
                    )
                    callback.onSuccess(paymentResult)
                }
                else -> {
                    if (paymentResult.code != SUCCESS_CODE) {
                        LogUtil.e(TAG, "Payment error during progress: code=${paymentResult.code}")
                        val errorCode = InnerErrorCode.fromCode(paymentResult.code, paymentResult.message)
                        callback.onFailure(
                            PaymentError.create(
                                code = paymentResult.code,
                                message = if (paymentResult.message.isNullOrEmpty())
                                    errorCode.description else paymentResult.message,
                                suggestion = ErrorStringHelper.getSolution(paymentResult.code) ?: "",
                                traceId = paymentResult.traceId,
                                transactionId = paymentResult.transactionId,
                                transactionRequestId = paymentResult.transactionRequestId
                            )
                        )
                    } else {
                        LogUtil.d(
                            TAG,
                            "Payment in progress: eventCode=${event.eventCode}, progress=${event.progress}"
                        )
                        callback.onProgress(event)
                    }
                }
            }
        } catch (e: Exception) {
            LogUtil.e(TAG, "Failed to parse response: action=${request.action}, error=${e.message}")
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

    private fun shouldRemoveCallback(responseJson: String): Boolean {
        return try {
            val eventCode = extractEventCode(responseJson) ?: return false
            when (PaymentEvent.fromEventCode(eventCode)) {
                is PaymentEvent.Completed, is PaymentEvent.Cancel -> true
                else -> false
            }
        } catch (e: Exception) {
            LogUtil.e(TAG, "Failed to parse responseJson for shouldRemoveCallback: ${e.message}")
            true
        }
    }

    private fun extractEventCode(responseJson: String): String? {
        return try {
            val node = mapper.readTree(responseJson)
            node.get("eventCode")?.asText()
        } catch (e: Exception) {
            null
        }
    }
}
