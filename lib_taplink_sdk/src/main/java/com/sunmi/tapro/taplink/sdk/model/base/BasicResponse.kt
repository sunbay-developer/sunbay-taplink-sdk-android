package com.sunmi.tapro.taplink.sdk.model.base

import com.sunmi.tapro.taplink.sdk.model.common.PaymentEvent

/**
 * Taplink basic response parameters
 *
 * All responses use this structure.
 * The internal JSON bridge maps flat eventCode/eventMsg fields to [PaymentEvent].
 *
 * @author Tapro Team
 * @since 2025-01-XX
 */
data class BasicResponse(
    val appSign: String,

    val version: String,

    val timeStamp: String,

    val action: String,

    /** Payment event derived from eventCode / eventMsg fields in the JSON. */
    val event: PaymentEvent,

    /** Business data as a JSON string (may be null). */
    val bizData: String? = null,

    val traceId: String
)
