package com.sunmi.tapro.taplink.sdk.impl

import com.sunmi.tapro.taplink.communication.enums.InnerErrorCode
import com.sunmi.tapro.taplink.communication.util.ErrorStringHelper
import com.sunmi.tapro.taplink.communication.util.LocalCallbackManager
import com.sunmi.tapro.taplink.communication.interfaces.InnerCallback
import com.sunmi.tapro.taplink.sdk.callback.PaymentCallback
import com.sunmi.tapro.taplink.sdk.error.PaymentError
import com.sunmi.tapro.taplink.sdk.model.common.PaymentEvent
import com.sunmi.tapro.taplink.sdk.model.request.PaymentRequest
import com.sunmi.tapro.taplink.sdk.model.response.PaymentResult
import com.sunmi.tapro.taplink.sdk.callback.TerminalInfoCallback
import com.sunmi.tapro.taplink.sdk.model.response.TerminalInfo
import com.sunmi.tapro.taplink.communication.util.LogUtil
import com.sunmi.tapro.taplink.sdk.util.TaplinkJsonBridge

/**
 * Response processing class using Android's built-in JSON implementation.
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

    /**
     * Listener for device info extracted from transaction responses.
     * Called when a response contains taproVersion/deviceId (typically from the first
     * transaction after INIT on the TaPro side).
     */
    var onDeviceInfoReceived: ((deviceId: String, taproVersion: String) -> Unit)? = null

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
            val node = TaplinkJsonBridge.parseObject(responseJson)
            val traceId = TaplinkJsonBridge.stringOrNull(node, "traceId")
            val eventCode = TaplinkJsonBridge.stringOrNull(node, "eventCode")?.uppercase()

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
                val errorCode = TaplinkJsonBridge.stringOrNull(node, "errorCode") ?: "UNKNOWN_ERROR"
                val errorMsg = TaplinkJsonBridge.stringOrNull(node, "eventMsg") ?: "Unknown error"
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

            val basicResponse = TaplinkJsonBridge.parseBasicResponse(result)
            LogUtil.d(
                TAG,
                "Received BasicResponse: eventCode=${basicResponse.event.eventCode}, " +
                        "eventMsg=${basicResponse.event.eventMsg}"
            )

            val paymentResult = if (!basicResponse.bizData.isNullOrBlank()) {
                TaplinkJsonBridge.parsePaymentResult(basicResponse.bizData)
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
                    notifyDeviceInfoIfPresent(paymentResult)
                    callback.onSuccess(paymentResult)
                }
                is PaymentEvent.Completed -> {
                    LogUtil.d(
                        TAG,
                        "[TAPLINK-TX] TraceId=${paymentResult.traceId} | Completed: " +
                                "code=${paymentResult.code}, status=${paymentResult.transactionStatus}"
                    )
                    notifyDeviceInfoIfPresent(paymentResult)
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
     * Deserialize a GET_TERMINAL_INFO response using the standard response-handler contract.
     *
     * Its `bizData` is a [TerminalInfo] payload rather than a [PaymentResult], so completed
     * and cancelled events dispatch through [TerminalInfoCallback]; non-final events are
     * surfaced as technical failures because this control action has no progress callback.
     */
    fun handleResponse(
        result: String,
        callback: TerminalInfoCallback,
        request: PaymentRequest
    ) {
        try {
            val basicResponse = TaplinkJsonBridge.parseBasicResponse(result)
            LogUtil.d(TAG, "Received BasicResponse: eventCode=${basicResponse.event.eventCode}, eventMsg=${basicResponse.event.eventMsg}")

            val bizData = basicResponse.bizData
            if (bizData.isNullOrBlank()) {
                throw IllegalArgumentException(
                    "GET_TERMINAL_INFO response missing terminal info data" +
                        (basicResponse.event.eventMsg?.let { ": $it" } ?: "")
                )
            }

            // A control action failure (e.g. device not registered / sign-in failed on the
            // TaPro side) is delivered by TaPro as a transaction-style failure payload
            // (a PaymentResult with a business error code such as 307/C20 and no `terminal`
            // field) rather than a TerminalInfo. Detect it BEFORE attempting to deserialize
            // TerminalInfo, otherwise the missing non-null `terminal` field turns the real
            // business error into a misleading 302 "Request data format error".
            val terminalFailure = extractTerminalInfoFailure(bizData)
            if (terminalFailure != null) {
                LogUtil.e(
                    TAG,
                    "GET_TERMINAL_INFO business failure: code=${terminalFailure.code}, " +
                        "message=${terminalFailure.message}, traceId=${basicResponse.traceId}"
                )
                callback.onFailure(
                    PaymentError.create(
                        code = terminalFailure.code,
                        message = terminalFailure.message,
                        suggestion = ErrorStringHelper.getSolution(terminalFailure.code) ?: "",
                        traceId = basicResponse.traceId
                    )
                )
                return
            }

            val terminalInfo = TaplinkJsonBridge.parseTerminalInfo(bizData)
            when (basicResponse.event) {
                is PaymentEvent.Cancel,
                is PaymentEvent.Completed -> callback.onSuccess(terminalInfo)
                else -> {
                    val errorCode = InnerErrorCode.E302
                    callback.onFailure(
                        PaymentError.create(
                            code = errorCode.code,
                            message = basicResponse.event.eventMsg ?: errorCode.description,
                            suggestion = ErrorStringHelper.getSolution(errorCode.code) ?: "",
                            traceId = basicResponse.traceId
                        )
                    )
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
     * Business failure carried by a GET_TERMINAL_INFO `bizData`.
     *
     * @property code business error code (e.g. "307"); never the success code "100".
     * @property message human-readable error message.
     */
    private data class TerminalInfoFailure(val code: String, val message: String)

    /**
     * Inspect a GET_TERMINAL_INFO `bizData` and, if it actually represents a control-action
     * failure (a transaction-style failure payload) rather than a [TerminalInfo], extract the
     * real business error code and message.
     *
     * TaPro emits such a payload when the control action fails before it can read terminal
     * info (e.g. device not registered / sign-in failed): the `bizData` is a serialized
     * `PaymentResult` — it has a `code`/`transactionResultCode` and `transactionStatus=FAILED`,
     * but no `terminal` field. Returning the real code here lets the SDK surface, for example,
     * a `307` business error instead of a misleading `302` "Request data format error" produced
     * by a failed `TerminalInfo` deserialization.
     *
     * @return the extracted [TerminalInfoFailure] when the payload is a failure result, or
     *   `null` when it looks like a normal [TerminalInfo] payload (which must be deserialized
     *   by the caller).
     */
    private fun extractTerminalInfoFailure(bizData: String): TerminalInfoFailure? {
        return try {
            val node = TaplinkJsonBridge.parseObject(bizData)
            // A genuine TerminalInfo payload always carries the `terminal` object. If it is
            // present, this is not a failure payload — let the caller deserialize normally.
            if (TaplinkJsonBridge.objectOrNull(node, "terminal") != null) {
                return null
            }

            val status = TaplinkJsonBridge.stringOrNull(node, "transactionStatus")
                ?.takeIf { it.isNotBlank() }
            val code = TaplinkJsonBridge.stringOrNull(node, "code")?.takeIf { it.isNotBlank() }
            val transactionResultCode = TaplinkJsonBridge.stringOrNull(node, "transactionResultCode")
                ?.takeIf { it.isNotBlank() }

            val isFailure = status.equals("FAILED", ignoreCase = true) ||
                (code != null && code != SUCCESS_CODE && code != "0")

            if (!isFailure) {
                // No `terminal`, but also no failure signal — leave it to the normal
                // deserialization path, which will raise a clear parse error if invalid.
                return null
            }

            val message = TaplinkJsonBridge.stringOrNull(node, "transactionResultMsg")
                ?.takeIf { it.isNotBlank() }
                ?: TaplinkJsonBridge.stringOrNull(node, "message")?.takeIf { it.isNotBlank() }
                ?: "GET_TERMINAL_INFO failed"

            // Prefer the transaction-mapped `code`; fall back to the raw transactionResultCode.
            val resolvedCode = code ?: transactionResultCode ?: InnerErrorCode.E307.code
            TerminalInfoFailure(code = resolvedCode, message = message)
        } catch (e: Exception) {
            LogUtil.d(TAG, "Not a terminal-info failure payload: ${e.message}")
            null
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

            val jsonObject = TaplinkJsonBridge.parseObject(json)


            // Check for Headless event signature: has "eventCode" + "eventId" but no "appSign"
            val hasEventId = jsonObject.has("eventId")
            val hasEventCode = jsonObject.has("eventCode")
            val hasAppSign = jsonObject.has("appSign")

            if (hasEventId && hasEventCode && !hasAppSign) {
                val eventCode = TaplinkJsonBridge.stringOrNull(jsonObject, "eventCode") ?: return null
                val eventId = TaplinkJsonBridge.stringOrNull(jsonObject, "eventId")
                val cancelable = TaplinkJsonBridge.booleanOrNull(jsonObject, "cancelable") ?: false
                val transactionRequestId =
                    TaplinkJsonBridge.stringOrNull(jsonObject, "transactionRequestId")
                val referenceOrderId = TaplinkJsonBridge.stringOrNull(jsonObject, "referenceOrderId")
                val timestamp = TaplinkJsonBridge.longOrNull(jsonObject, "timestamp")
                    ?: System.currentTimeMillis()

                // Parse optional data map
                val dataMap = TaplinkJsonBridge.objectOrNull(jsonObject, "data")?.let { dataObj ->
                    dataObj.keys().asSequence().associateWith { key -> dataObj.get(key).toString() }
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
            val jsonObject = TaplinkJsonBridge.parseObject(responseJson)

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
            val node = TaplinkJsonBridge.parseObject(responseJson)
            TaplinkJsonBridge.stringOrNull(node, "eventCode")
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

    /**
     * If the payment result contains TaPro device info (taproVersion / deviceId),
     * notify the registered listener. This typically happens on the first transaction
     * response after INIT is performed by TaPro.
     */
    private fun notifyDeviceInfoIfPresent(result: PaymentResult) {
        val taproVersion = result.taproVersion
        val deviceId = result.deviceId
        if (!taproVersion.isNullOrBlank() || !deviceId.isNullOrBlank()) {
            LogUtil.d(TAG, "Device info found in response: taproVersion=$taproVersion, deviceId=$deviceId")
            onDeviceInfoReceived?.invoke(
                deviceId ?: "unknown",
                taproVersion ?: "unknown"
            )
        }
    }
}
