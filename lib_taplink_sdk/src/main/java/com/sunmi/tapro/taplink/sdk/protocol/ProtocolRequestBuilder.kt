package com.sunmi.tapro.taplink.sdk.protocol

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.sunmi.tapro.taplink.sdk.model.base.BasicRequest
import com.sunmi.tapro.taplink.sdk.model.request.PaymentRequest
import com.sunmi.tapro.taplink.sdk.util.SignUtil
import com.sunmi.tapro.taplink.communication.util.LogUtil
import java.text.SimpleDateFormat
import java.util.*

/**
 * Protocol Request Builder
 *
 * Converts any request object to the underlying transport protocol format (BasicRequest)
 * Responsible for protocol layer data encapsulation, signature generation, timestamp processing, etc.
 *
 * @author TaPro Team
 * @since 2025-01-XX
 */
object ProtocolRequestBuilder {

    private const val TAG = "ProtocolRequestBuilder"

    /** Timestamp format: yyyyMMddHHmmssSSS */
    private val TIMESTAMP_FORMAT = SimpleDateFormat("yyyyMMddHHmmssSSS", Locale.US)

    /** Gson instance */
    private val gson = Gson()

    /**
     * Convert specific Request to BasicRequest
     *
     * @param request Specific Request object (e.g., PaymentRequest)
     * @param version SDK version number
     * @param secretKey Signature secret key
     * @return BasicRequest Converted BasicRequest object
     */
    fun convertToBasicRequest(
        request: Any,
        version: String,
        appid: String,
        secretKey: String
    ): BasicRequest {
        try {
            // 1. Get action (extract from PaymentRequest or use default value)
            val action = getActionByRequest(request)

            // 2. Convert request to JsonObject as bizData
            val bizDataJson = gson.toJsonTree(request).asJsonObject

            bizDataJson.addProperty("appId", appid)

            // 3. Generate timestamp
            val timestamp = getCurrentTimestamp()

            // 4. Generate traceId (for tracking concurrent requests)
            val traceId = generateTraceId()

            // 5. Build signature data (exclude appSign field, sort by field name, include traceId)
            val signData = buildSignData(version, timestamp, action, bizDataJson, traceId)

            // 6. Generate signature
            val appSign = SignUtil.generateHMACSHA256(signData, secretKey)

            // 7. Create BasicRequest object
            return BasicRequest(
                appSign = appSign,
                version = version,
                timeStamp = timestamp,
                action = action,
                bizData = bizDataJson,
                traceId = traceId
            )
        } catch (e: Exception) {
            LogUtil.e(TAG, "Failed to convert request to BasicRequest: ${e.message}")
            throw RequestConvertException("Failed to convert request", e)
        }
    }

    /**
     * Automatically determine action based on Request type
     *
     * @param request Request object
     * @return String action string
     */
    fun getActionByRequest(request: Any): String {
        return when (request) {
            is PaymentRequest -> {
                // PaymentRequest contains action field, return directly
                request.action.uppercase()
            }

            else -> {
                LogUtil.i(TAG, "Unknown request type: ${request.javaClass.simpleName}, using class name as action")
                request.javaClass.simpleName.replace("Request", "").lowercase()
            }
        }
    }

    /**
     * Build signature data
     *
     * Sort by field name, concatenate into string for signature
     * Format: action={action}&bizData={bizDataJson}&timeStamp={timeStamp}&traceId={traceId}&version={version}
     *
     * @param version SDK version number
     * @param timestamp Timestamp
     * @param action Operation type
     * @param bizData Business data JSON object
     * @param traceId Trace ID
     * @return String String for signature
     */
    private fun buildSignData(
        version: String,
        timestamp: String,
        action: String,
        bizData: JsonObject,
        traceId: String
    ): String {
        // Sort by field name: action, bizData, timeStamp, traceId, version
        val bizDataStr = gson.toJson(bizData)
        return "action=$action&bizData=$bizDataStr&timeStamp=$timestamp&traceId=$traceId&version=$version"
    }

    /**
     * Get current timestamp
     *
     * @return String Format: yyyyMMddHHmmssSSS
     */
    private fun getCurrentTimestamp(): String {
        return TIMESTAMP_FORMAT.format(Date())
    }

    /**
     * Generate trace ID
     *
     * Use UUID to generate unique trace ID for tracking concurrent requests, ensuring responses return to corresponding callback
     *
     * @return String Trace ID (UUID format, without hyphens)
     */
    private fun generateTraceId(): String {
        return UUID.randomUUID().toString().replace("-", "")
    }

    /**
     * Request conversion exception
     */
    class RequestConvertException(message: String, cause: Throwable? = null) : Exception(message, cause)
}

