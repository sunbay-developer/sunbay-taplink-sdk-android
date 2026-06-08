package com.sunmi.tapro.taplink.sdk.model.base

import com.fasterxml.jackson.annotation.JsonProperty
import com.sunmi.tapro.taplink.sdk.model.common.PaymentEvent

/**
 * Taplink basic response parameters
 *
 * All responses use this structure.
 * Deserialized by [BasicResponseDeserializer] which maps eventCode/eventMsg → PaymentEvent.
 *
 * @author TaPro Team
 * @since 2025-01-XX
 */
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
