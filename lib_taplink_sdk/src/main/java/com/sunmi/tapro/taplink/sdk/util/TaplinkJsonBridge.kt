package com.sunmi.tapro.taplink.sdk.util

import com.sunmi.tapro.taplink.sdk.model.base.BasicResponse
import com.sunmi.tapro.taplink.sdk.model.common.BatchCloseInfo
import com.sunmi.tapro.taplink.sdk.model.common.CardInfo
import com.sunmi.tapro.taplink.sdk.model.common.PaymentEvent
import com.sunmi.tapro.taplink.sdk.model.common.PaymentMethodInfo
import com.sunmi.tapro.taplink.sdk.model.common.TransactionAmount
import com.sunmi.tapro.taplink.sdk.model.response.MerchantMid
import com.sunmi.tapro.taplink.sdk.model.response.PaymentResult
import com.sunmi.tapro.taplink.sdk.model.response.Terminal
import com.sunmi.tapro.taplink.sdk.model.response.TerminalInfo
import com.sunmi.tapro.taplink.sdk.model.response.TerminalTid
import java.lang.reflect.Modifier
import java.math.BigDecimal
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * JSON compatibility bridge shared by the TapLink protocol implementation.
 *
 * It follows Tapro's bridge semantics while relying only on Android's built-in JSON classes:
 * null object properties are omitted, enums use their protocol names, and readers accept both
 * object-valued `bizData` from SDK <= 1.0.7 and string-valued `bizData` from newer SDKs.
 */
internal object TaplinkJsonBridge {

    fun toJsonObject(value: Any): JSONObject = when (val converted = toJsonValue(value)) {
        is JSONObject -> converted
        else -> throw JSONException("Expected JSON object, got ${converted.javaClass.name}")
    }

    fun toJsonString(value: Any): String = when (val converted = toJsonValue(value)) {
        is JSONObject -> converted.toString()
        is JSONArray -> converted.toString()
        is String -> JSONObject.quote(converted)
        else -> converted.toString()
    }

    fun parseObject(json: String): JSONObject = JSONObject(json)

    fun parseBasicResponse(json: String): BasicResponse {
        val root = parseObject(json)
        return BasicResponse(
            appSign = requiredString(root, "appSign"),
            version = requiredString(root, "version"),
            timeStamp = requiredString(root, "timeStamp"),
            action = requiredString(root, "action"),
            event = PaymentEvent.fromEventCode(requiredString(root, "eventCode")),
            bizData = jsonStringOrNull(root, "bizData"),
            traceId = requiredString(root, "traceId")
        )
    }

    fun parsePaymentResult(json: String): PaymentResult {
        val root = parseObject(json)
        return PaymentResult(
            code = requiredString(root, "code"),
            message = stringOrNull(root, "message"),
            traceId = stringOrNull(root, "traceId"),
            transactionId = stringOrNull(root, "transactionId"),
            referenceOrderId = stringOrNull(root, "referenceOrderId"),
            transactionRequestId = stringOrNull(root, "transactionRequestId"),
            transactionStatus = stringOrNull(root, "transactionStatus"),
            transactionBatchStatus = stringOrNull(root, "transactionBatchStatus"),
            relatedTransactionStatus = stringOrNull(root, "relatedTransactionStatus"),
            transactionType = stringOrNull(root, "transactionType"),
            amount = objectOrNull(root, "amount")?.let(::parseTransactionAmount),
            createTime = stringOrNull(root, "createTime"),
            completeTime = stringOrNull(root, "completeTime"),
            cardInfo = objectOrNull(root, "cardInfo")?.let(::parseCardInfo),
            batchNo = intOrNull(root, "batchNo"),
            voucherNo = stringOrNull(root, "voucherNo"),
            stan = stringOrNull(root, "stan"),
            rrn = stringOrNull(root, "rrn"),
            authCode = stringOrNull(root, "authCode"),
            transactionResultCode = stringOrNull(root, "transactionResultCode"),
            transactionResultMsg = stringOrNull(root, "transactionResultMsg"),
            description = stringOrNull(root, "description"),
            attach = stringOrNull(root, "attach"),
            receiptJson = stringOrNull(root, "receiptJson"),
            batchCloseInfo = objectOrNull(root, "batchCloseInfo")?.let(::parseBatchCloseInfo),
            tipAmount = decimalOrNull(root, "tipAmount"),
            incrementalAmount = decimalOrNull(root, "incrementalAmount"),
            totalAuthorizedAmount = decimalOrNull(root, "totalAuthorizedAmount"),
            merchantRefundNo = stringOrNull(root, "merchantRefundNo"),
            originalTransactionId = stringOrNull(root, "originalTransactionId"),
            originalTransactionRequestId = stringOrNull(root, "originalTransactionRequestId"),
            taproVersion = stringOrNull(root, "taproVersion"),
            deviceId = stringOrNull(root, "deviceId")
        )
    }

    fun parseTerminalInfo(json: String): TerminalInfo {
        val root = parseObject(json)
        val terminal = objectOrNull(root, "terminal") ?: throw JSONException("Missing terminal")
        return TerminalInfo(
            merchantId = stringOrNull(root, "merchantId"),
            dbaName = stringOrNull(root, "dbaName"),
            mcc = stringOrNull(root, "mcc"),
            country = stringOrNull(root, "country"),
            stateName = stringOrNull(root, "stateName"),
            cityName = stringOrNull(root, "cityName"),
            street = stringOrNull(root, "street"),
            detailAddress = stringOrNull(root, "detailAddress"),
            zipCode = stringOrNull(root, "zipCode"),
            midList = parseObjectList(root.optJSONArray("midList")) { item ->
                MerchantMid(
                    channelCode = requiredString(item, "channelCode"),
                    channelName = requiredString(item, "channelName"),
                    mid = requiredString(item, "mid")
                )
            },
            terminal = Terminal(
                sn = requiredString(terminal, "sn"),
                vendor = stringOrNull(terminal, "vendor"),
                model = stringOrNull(terminal, "model"),
                tidList = parseObjectList(terminal.optJSONArray("tidList")) { item ->
                    TerminalTid(
                        channelCode = requiredString(item, "channelCode"),
                        channelName = requiredString(item, "channelName"),
                        tid = requiredString(item, "tid")
                    )
                }
            )
        )
    }

    fun stringOrNull(source: JSONObject, key: String): String? {
        if (!source.has(key) || source.isNull(key)) return null
        return source.get(key).toString()
    }

    fun longOrNull(source: JSONObject, key: String): Long? {
        if (!source.has(key) || source.isNull(key)) return null
        return runCatching { source.getLong(key) }.getOrNull()
    }

    fun booleanOrNull(source: JSONObject, key: String): Boolean? {
        if (!source.has(key) || source.isNull(key)) return null
        return runCatching { source.getBoolean(key) }.getOrNull()
    }

    fun objectOrNull(source: JSONObject, key: String): JSONObject? {
        if (!source.has(key) || source.isNull(key)) return null
        return when (val value = source.get(key)) {
            is JSONObject -> value
            is String -> runCatching { JSONObject(value) }.getOrNull()
            else -> null
        }
    }

    private fun requiredString(source: JSONObject, key: String): String =
        stringOrNull(source, key) ?: throw JSONException("Missing $key")

    private fun jsonStringOrNull(source: JSONObject, key: String): String? {
        if (!source.has(key) || source.isNull(key)) return null
        return when (val value = source.get(key)) {
            is String -> value
            is JSONObject, is JSONArray -> value.toString()
            else -> value.toString()
        }
    }

    private fun intOrNull(source: JSONObject, key: String): Int? {
        if (!source.has(key) || source.isNull(key)) return null
        return runCatching { source.getInt(key) }.getOrNull()
    }

    private fun decimalOrNull(source: JSONObject, key: String): BigDecimal? =
        stringOrNull(source, key)?.let(::BigDecimal)

    private fun parseTransactionAmount(source: JSONObject) = TransactionAmount(
        priceCurrency = requiredString(source, "priceCurrency"),
        transAmount = decimalOrNull(source, "transAmount")
            ?: throw JSONException("Missing transAmount"),
        orderAmount = decimalOrNull(source, "orderAmount")
            ?: throw JSONException("Missing orderAmount"),
        taxAmount = decimalOrNull(source, "taxAmount"),
        serviceFee = decimalOrNull(source, "serviceFee"),
        surchargeAmount = decimalOrNull(source, "surchargeAmount"),
        tipAmount = decimalOrNull(source, "tipAmount"),
        cashbackAmount = decimalOrNull(source, "cashbackAmount")
    )

    private fun parseCardInfo(source: JSONObject) = CardInfo(
        maskedPan = stringOrNull(source, "maskedPan"),
        cardNetworkType = stringOrNull(source, "cardNetworkType"),
        paymentMethodId = stringOrNull(source, "paymentMethodId"),
        subPaymentMethodId = stringOrNull(source, "subPaymentMethodId"),
        entryMode = stringOrNull(source, "entryMode"),
        authenticationMethod = stringOrNull(source, "authenticationMethod"),
        cardholderName = stringOrNull(source, "cardholderName"),
        expiryDate = stringOrNull(source, "expiryDate"),
        issuerBank = stringOrNull(source, "issuerBank"),
        cardBrand = stringOrNull(source, "cardBrand")
    )

    private fun parseBatchCloseInfo(source: JSONObject) = BatchCloseInfo(
        totalCount = intOrNull(source, "totalCount"),
        totalAmount = decimalOrNull(source, "totalAmount"),
        totalTip = decimalOrNull(source, "totalTip"),
        totalTax = decimalOrNull(source, "totalTax"),
        totalSurchargeAmount = decimalOrNull(source, "totalSurchargeAmount"),
        totalServiceFee = decimalOrNull(source, "totalServiceFee"),
        cashDiscount = decimalOrNull(source, "cashDiscount"),
        closeTime = stringOrNull(source, "closeTime")
    )

    private fun <T> parseObjectList(source: JSONArray?, convert: (JSONObject) -> T): List<T> {
        if (source == null) return emptyList()
        return (0 until source.length()).map { index -> convert(source.getJSONObject(index)) }
    }

    private fun toJsonValue(value: Any?): Any = when (value) {
        null -> JSONObject.NULL
        is JSONObject, is JSONArray, is String, is Number, is Boolean -> value
        is Char -> value.toString()
        is Enum<*> -> value.name
        is Map<*, *> -> mapToJson(value)
        is Iterable<*> -> iterableToJson(value)
        is Array<*> -> iterableToJson(value.asIterable())
        is PaymentMethodInfo -> paymentMethodToJson(value)
        is BasicResponse -> basicResponseToJson(value)
        else -> reflectToJson(value)
    }

    private fun mapToJson(source: Map<*, *>): JSONObject = JSONObject().also { target ->
        source.forEach { (key, value) ->
            if (key != null && value != null) target.put(key.toString(), toJsonValue(value))
        }
    }

    private fun iterableToJson(source: Iterable<*>): JSONArray = JSONArray().also { target ->
        source.forEach { target.put(toJsonValue(it)) }
    }

    private fun paymentMethodToJson(source: PaymentMethodInfo): JSONObject = JSONObject().also {
        source.getCategoryString()?.let { value -> it.put("category", value) }
        source.getIdString()?.let { value -> it.put("id", value) }
        source.getSubIdString()?.let { value -> it.put("subId", value) }
    }

    private fun basicResponseToJson(source: BasicResponse): JSONObject = JSONObject().also {
        it.put("appSign", source.appSign)
        it.put("version", source.version)
        it.put("timeStamp", source.timeStamp)
        it.put("action", source.action)
        it.put("eventCode", source.event.eventCode)
        it.put("eventMsg", source.event.eventMsg)
        it.put("bizData", source.bizData ?: JSONObject.NULL)
        it.put("traceId", source.traceId)
    }

    private fun reflectToJson(source: Any): JSONObject {
        val target = JSONObject()
        val hierarchy = generateSequence(source.javaClass as Class<*>?) { it.superclass }
            .takeWhile { it != Object::class.java }
            .toList()
            .asReversed()
        hierarchy.forEach { type ->
            type.declaredFields.forEach fieldLoop@{ field ->
                val modifiers = field.modifiers
                if (Modifier.isStatic(modifiers) || Modifier.isTransient(modifiers) || field.isSynthetic) {
                    return@fieldLoop
                }
                field.isAccessible = true
                val value = field.get(source) ?: return@fieldLoop
                target.put(field.name, toJsonValue(value))
            }
        }
        return target
    }
}
