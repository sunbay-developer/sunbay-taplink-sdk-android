package com.sunmi.tapro.taplink.sdk.impl

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.sunmi.tapro.taplink.communication.enums.InnerErrorCode
import org.json.JSONObject
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
        // First attempt strict Gson parsing; fall back to lenient org.json parsing
        // when the server returns a malformed payload (e.g. un-escaped JSON inside
        // a string field such as eventMsg).
        val parsed = parseResponseSafely(responseJson)
        if (parsed == null) {
            LogUtil.e(TAG, "Failed to parse response as JSON: $responseJson")
            return
        }

        val (traceId, eventCode, errorCode, eventMsg) = parsed

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
            LogUtil.d(TAG, "[TAPLINK-TX] TraceId=$traceId | ECR Error received: code=$errorCode, msg=$eventMsg")
            callback?.onError(errorCode ?: "UNKNOWN_ERROR", eventMsg ?: "Unknown error")
        } else {
            callback?.onResponse(responseJson)
        }
    }

    /**
     * Parsed fields extracted from a raw response string.
     */
    private data class ParsedResponse(
        val traceId: String?,
        val eventCode: String?,
        val errorCode: String?,
        val eventMsg: String?
    )

    /**
     * Try to parse the response JSON with Gson first; if that fails (e.g. the server
     * embeds an un-escaped JSON object inside a string field), fall back to
     * [org.json.JSONObject] which is more lenient, and finally to a regex-based
     * extraction so we can still route the error to the correct callback.
     */
    private fun parseResponseSafely(responseJson: String): ParsedResponse? {
        // --- attempt 1: strict Gson ---
        try {
            val obj = JsonParser.parseString(responseJson).asJsonObject
            return ParsedResponse(
                traceId = obj.get("traceId")?.asString,
                eventCode = obj.get("eventCode")?.asString?.uppercase(),
                errorCode = obj.get("errorCode")?.asString,
                eventMsg = obj.get("eventMsg")?.asString
            )
        } catch (_: Exception) {
            // fall through to lenient parse
        }

        // --- attempt 2: org.json (lenient) ---
        try {
            val obj = JSONObject(responseJson)
            return ParsedResponse(
                traceId = obj.optString("traceId").ifEmpty { null },
                eventCode = obj.optString("eventCode").uppercase().ifEmpty { null },
                errorCode = obj.optString("errorCode").ifEmpty { null },
                eventMsg = obj.optString("eventMsg").ifEmpty { null }
            )
        } catch (_: Exception) {
            // fall through to regex extraction
        }

        // --- attempt 3: regex extraction for badly-formed JSON ---
        LogUtil.w(TAG, "Both JSON parsers failed, attempting regex extraction")
        return extractFieldsViaRegex(responseJson)
    }

    /**
     * Last-resort field extraction using simple regex patterns.
     * Handles cases where the JSON is structurally invalid (e.g. un-escaped
     * nested JSON objects in string values).
     */
    private fun extractFieldsViaRegex(responseJson: String): ParsedResponse? {
        return try {
            fun extractField(key: String): String? =
                Regex(""""$key"\s*:\s*"([^"]*?)"""").find(responseJson)?.groupValues?.get(1)
                    ?.ifEmpty { null }

            val traceId = extractField("traceId")
            val eventCode = extractField("eventCode")?.uppercase()
            val errorCode = extractField("errorCode")
            // eventMsg may itself be a JSON object — grab everything between the first
            // occurrence of `"eventMsg":` and the outer closing brace of the root object.
            val eventMsg = Regex(""""eventMsg"\s*:\s*(.+)$""")
                .find(responseJson.trimEnd().trimEnd('}').trimEnd())
                ?.groupValues?.get(1)
                ?.trim()
                ?.trimEnd(',')
                ?.let { raw ->
                    // Strip surrounding quotes if it was a plain string value
                    if (raw.startsWith('"') && raw.endsWith('"')) raw.drop(1).dropLast(1) else raw
                }

            if (traceId == null && eventCode == null) null
            else ParsedResponse(traceId, eventCode, errorCode, eventMsg)
        } catch (e: Exception) {
            LogUtil.e(TAG, "Regex extraction also failed: ${e.message}")
            null
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

            // First check event type, then code within each branch
            when (val event = basicResponse.event) {
                is PaymentEvent.Cancel -> {
                    // Terminal or user cancelled — route to onSuccess with the
                    // transactionStatus from Tapro (FAILED). This is still a final
                    // response, not a communication error.
                    LogUtil.d(
                        TAG,
                        "[TAPLINK-TX] TraceId=${paymentResult.traceId} | Cancel: code=${paymentResult.code}, " +
                                "transactionResultCode=${paymentResult.transactionResultCode}, message=${paymentResult.message}"
                    )
                    callback.onSuccess(paymentResult)
                }

                is PaymentEvent.Completed -> {
                    // Tapro processed the request and returned a final result.
                    // ALWAYS route to onSuccess — the caller inspects
                    // result.isSuccess() / isFailed() / isProcessing()
                    // to determine the actual transaction outcome.
                    // onFailure is reserved for communication errors only.
                    LogUtil.d(
                        TAG,
                        "[TAPLINK-TX] TraceId=${paymentResult.traceId} | Completed: code=${paymentResult.code}, " +
                                "transactionResultCode=${paymentResult.transactionResultCode}, message=${paymentResult.message}, " +
                                "status=${paymentResult.transactionStatus}"
                    )
                    callback.onSuccess(paymentResult)
                }

                else -> {
                    // Non-final event types (progress, PIN entry, card insertion, etc.).
                    // Check code first — if the device reported an error mid-flow, surface
                    // it as onFailure rather than silently emitting a progress event.
                    if (paymentResult.code != SUCCESS_CODE) {
                        LogUtil.e(TAG, "Payment error during progress: code=${paymentResult.code}, message=${paymentResult.message}")
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
                        LogUtil.d(
                            TAG,
                            "Payment in progress: eventCode=${event.eventCode}, eventMsg=${event.eventMsg}, progress=${event.progress}"
                        )
                        callback.onProgress(event)
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