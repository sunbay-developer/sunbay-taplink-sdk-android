package com.sunmi.tapro.taplink.sdk.protocol

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.sunmi.tapro.taplink.sdk.model.base.BasicRequest
import com.sunmi.tapro.taplink.sdk.model.request.PaymentRequest
import com.sunmi.tapro.taplink.sdk.util.SignUtil
import com.sunmi.tapro.taplink.communication.util.LogUtil
import java.text.SimpleDateFormat
import java.util.*

/**
 * Protocol Request Builder
 *
 * Converts any request object to the underlying transport protocol format (BasicRequest).
 * Uses Jackson for all JSON serialisation — no Gson dependency.
 *
 * @author TaPro Team
 * @since 2025-01-XX
 */
object ProtocolRequestBuilder {

    private const val TAG = "ProtocolRequestBuilder"

    private val TIMESTAMP_FORMAT = SimpleDateFormat("yyyyMMddHHmmssSSS", Locale.US)

    private val mapper: ObjectMapper = ObjectMapper()
        .registerKotlinModule()
        .setSerializationInclusion(JsonInclude.Include.NON_NULL)

    /**
     * Convert a specific request object to [BasicRequest].
     *
     * @param request  The business request (e.g. PaymentRequest)
     * @param version  SDK version string
     * @param appid    App identifier — injected into bizData JSON
     * @param secretKey HMAC-SHA256 signing key
     */
    fun convertToBasicRequest(
        request: Any,
        version: String,
        appid: String,
        secretKey: String,
        appToAppMode: String? = null
    ): BasicRequest {
        try {
            val action = getActionByRequest(request)

            // Serialise the business request to a mutable map so we can inject appId
            @Suppress("UNCHECKED_CAST")
            val bizMap = mapper.convertValue(request, MutableMap::class.java) as MutableMap<String, Any?>
            bizMap["appId"] = appid
            // App-to-App mode is carried in bizData so Tapro can decide per-connection behavior.
            if (!appToAppMode.isNullOrBlank()) {
                bizMap["appToAppMode"] = appToAppMode
            }

            val bizDataStr = mapper.writeValueAsString(bizMap)

            val timestamp = getCurrentTimestamp()
            val traceId = generateTraceId()

            val signData = buildSignData(version, timestamp, action, bizDataStr, traceId)
            val appSign = SignUtil.generateHMACSHA256(signData, secretKey)

            return BasicRequest(
                appSign = appSign,
                version = version,
                timeStamp = timestamp,
                action = action,
                bizData = bizDataStr,
                traceId = traceId
            )
        } catch (e: Exception) {
            LogUtil.e(TAG, "Failed to convert request to BasicRequest: ${e.message}")
            throw RequestConvertException("Failed to convert request", e)
        }
    }

    fun getActionByRequest(request: Any): String {
        return when (request) {
            is PaymentRequest -> request.action.uppercase()
            else -> {
                LogUtil.i(TAG, "Unknown request type: ${request.javaClass.simpleName}, using class name as action")
                request.javaClass.simpleName.replace("Request", "").lowercase()
            }
        }
    }

    private fun buildSignData(
        version: String,
        timestamp: String,
        action: String,
        bizDataStr: String,
        traceId: String
    ): String =
        "action=$action&bizData=$bizDataStr&timeStamp=$timestamp&traceId=$traceId&version=$version"

    private fun getCurrentTimestamp(): String = TIMESTAMP_FORMAT.format(Date())

    private fun generateTraceId(): String = UUID.randomUUID().toString().replace("-", "")

    class RequestConvertException(message: String, cause: Throwable? = null) : Exception(message, cause)
}
