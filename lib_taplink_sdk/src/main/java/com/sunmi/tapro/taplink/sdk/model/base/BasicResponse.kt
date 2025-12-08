package com.sunmi.tapro.taplink.sdk.model.base

import com.google.gson.JsonObject
import com.google.gson.annotations.JsonAdapter
import com.google.gson.annotations.SerializedName
import com.sunmi.tapro.taplink.sdk.model.common.PaymentEvent

/**
 * Taplink基础响应参数
 *
 * 所有响应都使用此结构
 * 包含通用字段：appSign, version, timeStamp, action, event, bizData
 *
 * @author TaPro Team
 * @since 2025-01-XX
 */
@JsonAdapter(BasicResponseTypeAdapter::class)
data class BasicResponse(
    /**
     * 消息签名，使用HMAC-SHA256算法生成
     * 必填
     */
    @SerializedName("appSign")
    val appSign: String,
    
    /**
     * SDK版本号，格式：x.y.z
     * 必填
     */
    @SerializedName("version")
    val version: String,
    
    /**
     * 响应时间戳，格式：yyyyMMddHHmmssSSS
     * 必填
     */
    @SerializedName("timeStamp")
    val timeStamp: String,
    
    /**
     * 操作类型标识符
     * 必填
     */
    @SerializedName("action")
    val action: String,
    
    /**
     * 支付事件，包含事件码和事件信息
     * 必填
     * JSON 序列化时会映射为 eventCode 和 eventMsg 字段
     */
    val event: PaymentEvent,
    
    /**
     * 业务数据，根据action类型变化
     * 可选
     */
    @SerializedName("bizData")
    val bizData: JsonObject? = null,
    
    /**
     * 追踪ID，用于跟踪并发请求，确保响应能返回给对应的callback
     * 必填，与请求中的traceId保持一致
     */
    @SerializedName("traceId")
    val traceId: String
)


