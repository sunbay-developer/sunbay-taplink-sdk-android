package com.sunmi.tapro.taplink.sdk.model.base

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonNode
import com.sunmi.tapro.taplink.sdk.model.common.PaymentEvent

/**
 * Jackson deserializer for [BasicResponse].
 *
 * Handles mapping of flat eventCode / eventMsg JSON fields → [PaymentEvent] sealed class.
 * bizData is kept as a raw JSON string so downstream code can use its own mapper.
 *
 * Supports both integer (e.g., 4003) and string (e.g., "COMPLETED") eventCode values.
 */
class BasicResponseJacksonDeserializer : JsonDeserializer<BasicResponse>() {

    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): BasicResponse {
        val node: JsonNode = p.codec.readTree(p)

        val appSign = node.get("appSign")?.asText()
            ?: throw IllegalArgumentException("Missing appSign")
        val version = node.get("version")?.asText()
            ?: throw IllegalArgumentException("Missing version")
        val timeStamp = node.get("timeStamp")?.asText()
            ?: throw IllegalArgumentException("Missing timeStamp")
        val action = node.get("action")?.asText()
            ?: throw IllegalArgumentException("Missing action")
        val traceId = node.get("traceId")?.asText()
            ?: throw IllegalArgumentException("Missing traceId")

        // bizData kept as JSON string
        val bizDataNode = node.get("bizData")
        val bizData: String? = when {
            bizDataNode == null || bizDataNode.isNull -> null
            bizDataNode.isTextual -> bizDataNode.asText()
            else -> bizDataNode.toString()
        }

        // eventCode may be an integer or a string in the JSON
        val eventCode = node.get("eventCode")?.let { ec ->
            if (ec.isNumber) ec.asInt().toString() else ec.asText()
        } ?: throw IllegalArgumentException("Missing eventCode")

        val event = PaymentEvent.fromEventCode(eventCode)

        return BasicResponse(
            appSign = appSign,
            version = version,
            timeStamp = timeStamp,
            action = action,
            event = event,
            bizData = bizData,
            traceId = traceId
        )
    }
}
