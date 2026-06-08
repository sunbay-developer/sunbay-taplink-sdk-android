package com.sunmi.tapro.taplink.sdk.model.base

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Taplink basic request parameters
 *
 * All requests use this structure.
 * bizData is a JSON string containing the serialised business payload.
 *
 * @author TaPro Team
 * @since 2025-01-XX
 */
data class BasicRequest(
    @JsonProperty("appSign")
    val appSign: String,

    @JsonProperty("version")
    val version: String,

    @JsonProperty("timeStamp")
    val timeStamp: String,

    @JsonProperty("action")
    val action: String,

    /** Business data as a JSON string. */
    @JsonProperty("bizData")
    val bizData: String,

    @JsonProperty("traceId")
    val traceId: String
)
