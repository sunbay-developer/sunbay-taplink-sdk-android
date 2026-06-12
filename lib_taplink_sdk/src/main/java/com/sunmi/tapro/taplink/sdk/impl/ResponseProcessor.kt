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

    // Track processed terminal events (COMPLETED/CANCEL) by traceId to prevent duplicate processing
    // Terminal events should only be processed once even if they arrive multiple times
    private val processedTerminalEvents = mutableSetOf<String>()

    init {
        LogUtil.d(TAG, "ResponseProcessor initialized")
    }

    /**
     * Route low-level response JSON to the matching callback.
     * 
     * Implements deduplication for terminal events (COMPLETED/CANCEL) to prevent
     * duplicate processing if the same event arrives multiple times from the terminal.
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

            // Deduplicate terminal events (COMPLETED/CANCEL)
            // If this is a terminal event and we've already processed it, skip to prevent duplicate handling
            if (shouldRemove) {
                val isTerminalEventAlreadyProcessed = synchronized(processedTerminalEvents) {
                    val terminalEventKey = "$traceId:$eventCode"
                    if (terminalEventKey in processedTerminalEvents) {
                        LogUtil.d(TAG, "Duplicate terminal event detected: traceId=$traceId, eventCode=$eventCode, skipping")
                        true
                    } else {
                        processedTerminalEvents.add(terminalEventKey)
                        false
                    }
                }
                
                if (isTerminalEventAlreadyProcessed) {
                    return
                }
            }

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

            // 先尝试识别是否为 Headless 中间事件（简化 JSON 格式，无 appSign/version）
            val headlessEvent = tryParseAsHeadlessIntermediateEvent(result)
            if (headlessEvent != null) {
                LogUtil.d(TAG, "Headless intermediate event: eventCode=${headlessEvent.eventCode}")
                callback.onProgress(headlessEvent)
                return
            }

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
    /**
     * Try to parse response as a Headless intermediate event.
     *
     * Headless mode intermediate events use a simplified JSON format:
     * {
     *   "eventId": "EVT_TXN20250101001_007",
     *   "eventCode": "CardDetected",
     *   "cancelable": false,
     *   "timestamp": 1735689600000,
     *   "transactionRequestId": "TXN20250101001",
     *   "data": { "entryMode": "CONTACT", "cardBrand": "VISA" }
     * }
     *
     * These events do NOT have appSign/version/bizData/action fields.
     *
     * @return PaymentEvent.HeadlessEvent if parsing succeeds, null otherwise
     */
    private fun tryParseAsHeadlessIntermediateEvent(json: String): PaymentEvent? {
        return try {

            val jsonObject = mapper.readTree(json)


            // Check for Headless event signature: has "eventCode" + "eventId" but no "appSign"
            val hasEventId = jsonObject.has("eventId")
            val hasEventCode = jsonObject.has("eventCode")
            val hasAppSign = jsonObject.has("appSign")

            if (hasEventId && hasEventCode && !hasAppSign) {
                val eventCode = jsonObject.get("eventCode")?.asText() ?: return null
                val eventId = jsonObject.get("eventId")?.takeUnless { it.isNull }?.asText()
                val cancelable = jsonObject.get("cancelable")?.takeUnless { it.isNull }?.asBoolean() ?: false
                val transactionRequestId = jsonObject.get("transactionRequestId")
                    ?.takeUnless { it.isNull }
                    ?.asText()
                val referenceOrderId = jsonObject.get("referenceOrderId")
                    ?.takeUnless { it.isNull }
                    ?.asText()
                val timestamp = jsonObject.get("timestamp")
                    ?.takeUnless { it.isNull }
                    ?.asLong()
                    ?: System.currentTimeMillis()

                // Parse optional data map
                val dataMap = if (jsonObject.has("data") && jsonObject.get("data").isObject) {
                    val dataObj = jsonObject.get("data")
                    dataObj.fields().asSequence().associate { (key, value) ->
                        val normalizedValue = when {
                            value.isValueNode -> value.asText()
                            else -> mapper.writeValueAsString(value)
                        }
                        key to normalizedValue
                    }
                } else {
                    null
                }

                // 归一化 eventCode/eventMsg，兼容现有业务按 WAITING_CARD 等常量判断。
                val predefined = PaymentEvent.fromEventCode(eventCode)
                val normalizedEventCode = if (predefined is PaymentEvent.HeadlessEvent) {
                    eventCode
                } else {
                    predefined.eventCode
                }
                val normalizedEventMsg = if (predefined is PaymentEvent.HeadlessEvent) {
                    "Headless: $eventCode"
                } else {
                    predefined.eventMsg
                }

                // Return as HeadlessEvent with full metadata
                PaymentEvent.HeadlessEvent(
                    eventCode = normalizedEventCode,
                    eventMsg = normalizedEventMsg,
                    cancelable = cancelable,
                    eventId = eventId,
                    transactionRequestId = transactionRequestId,
                    referenceOrderId = referenceOrderId,
                    data = dataMap,
                    timestamp = timestamp,
                    rawEventCode = eventCode
                )
            } else {
                null // Not a Headless intermediate event
            }
        } catch (e: Exception) {
            null // Parse failed, not a Headless event
        }
    }



    private fun shouldRemoveCallback(responseJson: String): Boolean {
        return try {

            // Headless 中间事件（eventId 存在且无 appSign）只用于进度透传，不能释放回调。
            val jsonObject = mapper.readTree(responseJson)

            val hasEventId = jsonObject.has("eventId")
            val hasAppSign = jsonObject.has("appSign")
            if (hasEventId && !hasAppSign) {
                return false
            }


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

    /**
     * Clear processed terminal events tracking.
     * Should be called when connection is reset or transaction context changes.
     */
    fun clearProcessedTerminalEvents() {
        synchronized(processedTerminalEvents) {
            processedTerminalEvents.clear()
            LogUtil.d(TAG, "Cleared processed terminal events tracking")
        }
    }
}
