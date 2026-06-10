package com.sunmi.tapro.taplink.sdk.model.base

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.sunmi.tapro.taplink.sdk.model.common.PaymentEvent

/**
 * Taplink basic response parameters
 *
 * All responses use this structure.
 * Deserialized by [BasicResponseJacksonDeserializer] which maps eventCode/eventMsg → PaymentEvent.
 * Serialized by [BasicResponseJacksonSerializer] which flattens event → eventCode + eventMsg.
 *
 * @author TaPro Team
 * @since 2025-01-XX
 */
@JsonSerialize(using = BasicResponseJacksonSerializer::class)
@JsonDeserialize(using = BasicResponseJacksonDeserializer::class)
data class BasicResponse(
    @JsonProperty("appSign")
    val appSign: String,

    @JsonProperty("version")
    val version: String,

    @JsonProperty("timeStamp")
    val timeStamp: String,

    @JsonProperty("action")
    val action: String,

    /** Payment event derived from eventCode / eventMsg fields in the JSON. */
    val event: PaymentEvent,

    /** Business data as a JSON string (may be null). */
    @JsonProperty("bizData")
    val bizData: String? = null,

    @JsonProperty("traceId")
    val traceId: String
)
