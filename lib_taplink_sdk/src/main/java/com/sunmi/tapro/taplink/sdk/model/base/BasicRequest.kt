package com.sunmi.tapro.taplink.sdk.model.base

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
    val appSign: String,

    val version: String,

    val timeStamp: String,

    val action: String,

    /** Business data as a JSON string. */
    val bizData: String,

    val traceId: String
)
