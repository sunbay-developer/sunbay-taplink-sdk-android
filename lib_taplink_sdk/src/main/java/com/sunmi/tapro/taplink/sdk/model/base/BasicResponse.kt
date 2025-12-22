package com.sunmi.tapro.taplink.sdk.model.base

import com.google.gson.JsonObject
import com.google.gson.annotations.JsonAdapter
import com.google.gson.annotations.SerializedName
import com.sunmi.tapro.taplink.sdk.model.common.PaymentEvent

/**
 * Taplink basic response parameters
 *
 * All responses use this structure
 * Contains common fields: appSign, version, timeStamp, action, event, bizData
 *
 * @author TaPro Team
 * @since 2025-01-XX
 */
@JsonAdapter(BasicResponseTypeAdapter::class)
data class BasicResponse(
    /**
     * Message signature, generated using HMAC-SHA256 algorithm
     * Required
     */
    @SerializedName("appSign")
    val appSign: String,
    
    /**
     * SDK version number, format: x.y.z
     * Required
     */
    @SerializedName("version")
    val version: String,
    
    /**
     * Response timestamp, format: yyyyMMddHHmmssSSS
     * Required
     */
    @SerializedName("timeStamp")
    val timeStamp: String,
    
    /**
     * Operation type identifier
     * Required
     */
    @SerializedName("action")
    val action: String,
    
    /**
     * Payment event, contains event code and event information
     * Required
     * When JSON serialized, maps to eventCode and eventMsg fields
     */
    val event: PaymentEvent,
    
    /**
     * Business data, varies based on action type
     * Optional
     */
    @SerializedName("bizData")
    val bizData: JsonObject? = null,
    
    /**
     * Trace ID, used to track concurrent requests, ensures response returns to corresponding callback
     * Required, must match traceId in request
     */
    @SerializedName("traceId")
    val traceId: String
)


