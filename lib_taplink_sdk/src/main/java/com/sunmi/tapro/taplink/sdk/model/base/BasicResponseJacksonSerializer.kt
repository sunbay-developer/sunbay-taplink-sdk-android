package com.sunmi.tapro.taplink.sdk.model.base

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.SerializerProvider

/**
 * Jackson serializer for [BasicResponse].
 *
 * Flattens the [BasicResponse.event] (PaymentEvent) into top-level "eventCode" and "eventMsg"
 * fields in the output JSON, matching the wire format expected by the SDK deserializer.
 *
 * Wire format:
 * {
 *   "appSign": "...",
 *   "version": "...",
 *   "timeStamp": "...",
 *   "action": "...",
 *   "eventCode": "COMPLETED",
 *   "eventMsg": "Transaction completed",
 *   "bizData": "...",
 *   "traceId": "..."
 * }
 */
class BasicResponseJacksonSerializer : JsonSerializer<BasicResponse>() {

    override fun serialize(value: BasicResponse, gen: JsonGenerator, serializers: SerializerProvider) {
        gen.writeStartObject()
        gen.writeStringField("appSign", value.appSign)
        gen.writeStringField("version", value.version)
        gen.writeStringField("timeStamp", value.timeStamp)
        gen.writeStringField("action", value.action)
        gen.writeStringField("eventCode", value.event.eventCode)
        gen.writeStringField("eventMsg", value.event.eventMsg)
        if (value.bizData != null) {
            gen.writeStringField("bizData", value.bizData)
        } else {
            gen.writeNullField("bizData")
        }
        gen.writeStringField("traceId", value.traceId)
        gen.writeEndObject()
    }
}
