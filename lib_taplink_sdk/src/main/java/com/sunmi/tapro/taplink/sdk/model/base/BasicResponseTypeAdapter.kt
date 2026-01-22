package com.sunmi.tapro.taplink.sdk.model.base

import com.google.gson.*
import com.google.gson.reflect.TypeToken
import com.sunmi.tapro.taplink.sdk.model.common.PaymentEvent
import java.lang.reflect.Type

/**
 * BasicResponse 的自定义 JSON 序列化/反序列化适配器
 * 
 * 处理 eventCode (Int) 和 eventMsg (String) 到 PaymentEvent 的转换
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
        
        // 解析基本字段
        val appSign = jsonObject.get("appSign")?.asString ?: throw JsonParseException("Missing appSign")
        val version = jsonObject.get("version")?.asString ?: throw JsonParseException("Missing version")
        val timeStamp = jsonObject.get("timeStamp")?.asString ?: throw JsonParseException("Missing timeStamp")
        val action = jsonObject.get("action")?.asString ?: throw JsonParseException("Missing action")
        val traceId = jsonObject.get("traceId")?.asString ?: throw JsonParseException("Missing traceId")
        val bizData = jsonObject.get("bizData")?.asJsonObject
        
        // 解析 eventCode 和 eventMsg，转换为 PaymentEvent
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
        
        // eventMsg 不再使用，直接根据 eventCode 创建对应的 PaymentEvent
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
        
        // 将 PaymentEvent 转换为 eventCode 和 eventMsg
        // eventCode 在 JSON 中可以是 Int 或 String 类型
        // 如果 eventCode 是数字字符串（如 "4003"），转换为 Int；否则保持为 String
        val eventCodeElement = when {
            src.event.eventCode.toIntOrNull() != null -> {
                // 是数字字符串，转换为 Int
                JsonPrimitive(src.event.eventCode.toInt())
            }
            else -> {
                // 非数字字符串，保持为 String
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

