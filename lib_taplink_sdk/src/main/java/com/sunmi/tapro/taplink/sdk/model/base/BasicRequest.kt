package com.sunmi.tapro.taplink.sdk.model.base

import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName

/**
 * Taplink基础请求参数
 *
 * 所有请求都使用此结构
 * 包含通用字段：appSign, version, timeStamp, action, bizData, traceId
 *
 * @author TaPro Team
 * @since 2025-01-XX
 */
data class BasicRequest(
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
     * 请求时间戳，格式：yyyyMMddHHmmssSSS
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
     * 业务数据，根据action类型变化
     * 必填，*Request类型就是bizData的内容
     */
    @SerializedName("bizData")
    val bizData: JsonObject,
    
    /**
     * 追踪ID，用于跟踪并发请求，确保响应能返回给对应的callback
     * 必填，由系统自动生成
     */
    @SerializedName("traceId")
    val traceId: String
)












