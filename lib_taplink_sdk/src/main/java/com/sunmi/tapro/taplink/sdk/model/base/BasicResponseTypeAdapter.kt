package com.sunmi.tapro.taplink.sdk.model.base

import com.google.gson.*
import com.google.gson.reflect.TypeToken
import com.sunmi.tapro.taplink.sdk.model.common.PaymentEvent
import java.lang.reflect.Type

/**
 * Custom JSON serialization/deserialization adapter for BasicResponse.
 * 
 * Handles conversion between eventCode (Int) and eventMsg (String) to PaymentEvent.
 * 
 * @author TaPro Team
 * @since 2025-01-XX
 */
class BasicResponseTypeAdapter : JsonDeserializer<BasicResponse>, JsonSerializer<BasicResponse> {
    
    override fun deserialize(
        json: JsonElement?,
        typeOfT: Type?,
        context: JsonDeserializationContext?
    ): BasicResponse {
        if (json == null || !json.isJsonObject) {
            throw JsonParseException("Expected JSON object")
        }
        
        val jsonObject = json.asJsonObject
        
        // Parse basic fields
        val appSign = jsonObject.get("appSign")?.asString ?: throw JsonParseException("Missing appSign")
        val version = jsonObject.get("version")?.asString ?: throw JsonParseException("Missing version")
        val timeStamp = jsonObject.get("timeStamp")?.asString ?: throw JsonParseException("Missing timeStamp")
        val action = jsonObject.get("action")?.asString ?: throw JsonParseException("Missing action")
        val traceId = jsonObject.get("traceId")?.asString ?: throw JsonParseException("Missing traceId")
        val bizData = jsonObject.get("bizData")?.asJsonObject
        
        // Parse eventCode and eventMsg, convert to PaymentEvent
        val eventCodeElement = jsonObject.get("eventCode")
        val eventMsgElement = jsonObject.get("eventMsg")
        
        val eventCode = when {
            eventCodeElement?.isJsonPrimitive == true -> {
                val primitive = eventCodeElement.asJsonPrimitive
                if (primitive.isNumber) {
                    primitive.asInt.toString()
                } else {
                    primitive.asString
                }
            }
            else -> throw JsonParseException("Missing or invalid eventCode")
        }
        
        // eventMsg is no longer used, create PaymentEvent directly from eventCode
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
    
    override fun serialize(
        src: BasicResponse?,
        typeOfSrc: Type?,
        context: JsonSerializationContext?
    ): JsonElement {
        if (src == null) {
            return JsonNull.INSTANCE
        }
        
        val jsonObject = JsonObject()
        jsonObject.addProperty("appSign", src.appSign)
        jsonObject.addProperty("version", src.version)
        jsonObject.addProperty("timeStamp", src.timeStamp)
        jsonObject.addProperty("action", src.action)
        
        // Convert PaymentEvent to eventCode and eventMsg
        // eventCode can be Int or String type in JSON
        // If eventCode is a numeric string (like "4003"), convert to Int; otherwise keep as String
        val eventCodeElement = when {
            src.event.eventCode.toIntOrNull() != null -> {
                // Is numeric string, convert to Int
                JsonPrimitive(src.event.eventCode.toInt())
            }
            else -> {
                // Non-numeric string, keep as String
                JsonPrimitive(src.event.eventCode)
            }
        }
        jsonObject.add("eventCode", eventCodeElement)
        jsonObject.addProperty("eventMsg", src.event.eventMsg)
        
        if (src.bizData != null) {
            jsonObject.add("bizData", src.bizData)
        }
        
        jsonObject.addProperty("traceId", src.traceId)
        
        return jsonObject
    }
}

