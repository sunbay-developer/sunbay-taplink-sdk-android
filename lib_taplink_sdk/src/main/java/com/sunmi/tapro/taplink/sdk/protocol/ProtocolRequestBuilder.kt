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
 * 协议请求构建器
 *
 * 将任何请求对象转换为底层传输协议格式 (BasicRequest)
 * 负责协议层的数据封装、签名生成、时间戳处理等
 *
 * @author TaPro Team
 * @since 2025-01-XX
 */
object ProtocolRequestBuilder {

    private const val TAG = "ProtocolRequestBuilder"

    /** 时间戳格式：yyyyMMddHHmmssSSS */
    private val TIMESTAMP_FORMAT = SimpleDateFormat("yyyyMMddHHmmssSSS", Locale.US)

    /** Gson实例 */
    private val gson = Gson()

    /**
     * 将具体的Request转换为BasicRequest
     *
     * @param request 具体的Request对象（如PaymentRequest）
     * @param version SDK版本号
     * @param secretKey 签名密钥
     * @return BasicRequest 转换后的BasicRequest对象
     */
    fun convertToBasicRequest(
        request: Any,
        version: String,
        appid: String,
        secretKey: String
    ): BasicRequest {
        try {
            // 1. 获取 action（从 PaymentRequest 中提取或使用默认值）
            val action = getActionByRequest(request)

            // 2. 将request转换为JsonObject作为bizData
            val bizDataJson = gson.toJsonTree(request).asJsonObject

            bizDataJson.addProperty("appId", appid)

            // 3. 生成时间戳
            val timestamp = getCurrentTimestamp()

            // 4. 生成traceId（用于跟踪并发请求）
            val traceId = generateTraceId()

            // 5. 构建签名数据（排除appSign字段，按字段名排序，包含traceId）
            val signData = buildSignData(version, timestamp, action, bizDataJson, traceId)

            // 6. 生成签名
            val appSign = SignUtil.generateHMACSHA256(signData, secretKey)

            // 7. 创建BasicRequest对象
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
     * 根据Request类型自动确定action
     *
     * @param request Request对象
     * @return String action字符串
     */
    fun getActionByRequest(request: Any): String {
        return when (request) {
            is PaymentRequest -> {
                // PaymentRequest 包含 action 字段，直接返回
                request.action.uppercase()
            }

            else -> {
                LogUtil.i(TAG, "Unknown request type: ${request.javaClass.simpleName}, using class name as action")
                request.javaClass.simpleName.replace("Request", "").lowercase()
            }
        }
    }

    /**
     * 构建签名数据
     *
     * 按照字段名排序，拼接成字符串用于签名
     * 格式：action={action}&bizData={bizDataJson}&timeStamp={timeStamp}&traceId={traceId}&version={version}
     *
     * @param version SDK版本号
     * @param timestamp 时间戳
     * @param action 操作类型
     * @param bizData 业务数据JSON对象
     * @param traceId 追踪ID
     * @return String 用于签名的字符串
     */
    private fun buildSignData(
        version: String,
        timestamp: String,
        action: String,
        bizData: JsonObject,
        traceId: String
    ): String {
        // 按字段名排序：action, bizData, timeStamp, traceId, version
        val bizDataStr = gson.toJson(bizData)
        return "action=$action&bizData=$bizDataStr&timeStamp=$timestamp&traceId=$traceId&version=$version"
    }

    /**
     * 获取当前时间戳
     *
     * @return String 格式：yyyyMMddHHmmssSSS
     */
    private fun getCurrentTimestamp(): String {
        return TIMESTAMP_FORMAT.format(Date())
    }

    /**
     * 生成追踪ID
     *
     * 使用UUID生成唯一的追踪ID，用于跟踪并发请求，确保响应能返回给对应的callback
     *
     * @return String 追踪ID（UUID格式，去除连字符）
     */
    private fun generateTraceId(): String {
        return UUID.randomUUID().toString().replace("-", "")
    }

    /**
     * 请求转换异常
     */
    class RequestConvertException(message: String, cause: Throwable? = null) : Exception(message, cause)
}

