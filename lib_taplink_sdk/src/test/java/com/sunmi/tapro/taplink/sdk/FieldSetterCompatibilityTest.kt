package com.sunmi.tapro.taplink.sdk

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import com.sunmi.tapro.taplink.sdk.config.ConnectionConfig
import com.sunmi.tapro.taplink.sdk.config.TaplinkConfig
import com.sunmi.tapro.taplink.sdk.enums.*
import com.sunmi.tapro.taplink.sdk.impl.TaplinkApiImpl
import com.sunmi.tapro.taplink.sdk.impl.snapshotForExecution
import com.sunmi.tapro.taplink.sdk.model.common.*
import com.sunmi.tapro.taplink.sdk.model.request.PaymentRequest
import com.sunmi.tapro.taplink.sdk.model.request.QueryRequest
import com.sunmi.tapro.taplink.sdk.model.request.transaction.SaleRequest
import com.sunmi.tapro.taplink.sdk.protocol.ProtocolRequestBuilder
import com.sunmi.tapro.taplink.sdk.util.TaplinkJsonBridge
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal
import java.lang.reflect.Method
import java.lang.reflect.Proxy

class FieldSetterCompatibilityTest {
    /** SPEC-CONFIG-001 AC1: every changed field-setter overload must update its receiver. */
    @Test fun allChangedSettersUpdateOriginalAndReturnSameInstance() {
        val objects = listOf(
            TaplinkConfig.create("app", "key"), PaymentRequest("SALE"),
            AmountInfo(BigDecimal("1000"), "USD"), DeviceInfo(), GoodsDetail(),
            PaymentMethodInfo(_category = "CARD"), StaffInfo(), TipConfig(),
            TipSuggestions(FeeMode.RATE, listOf(10))
        )
        var exercised = 0
        for (receiver in objects) {
            val expectations = linkedMapOf<String, Any?>()
            for (setter in receiver.javaClass.methods.filter {
                it.name.startsWith("set") && it.returnType == receiver.javaClass
            }.sortedBy { it.name + it.parameterTypes.joinToString() }) {
                val argument = argumentFor(setter)
                assertSame(setter.toString(), receiver, setter.invoke(receiver, argument))
                val getter = when (setter.name) {
                    "setMerchantOrderNo" -> "getReferenceOrderId"
                    "setCategory" -> "getCategoryString"
                    "setId" -> "getIdString"
                    "setSubId" -> "getSubIdString"
                    else -> "get" + setter.name.removePrefix("set")
                }
                val expected = when {
                    setter.name in listOf("setCategory", "setId", "setSubId") && argument is Enum<*> ->
                        argument.javaClass.getMethod("toApiString").invoke(argument)
                    argument is TransactionAction -> argument.value
                    else -> argument
                }
                expectations[getter] = expected
                // Check accumulated state after each setter, not just its own field.
                expectations.forEach { (method, value) ->
                    assertEquals(setter.toString(), value, receiver.javaClass.getMethod(method).invoke(receiver))
                }
                exercised++
            }
        }
        assertEquals("Inventory of changed public setter overloads", 73, exercised)
    }

    /** SPEC-CONFIG-001 AC1: screenshot, repeated writes and unchanged fluent types. */
    @Test fun ignoredChainsAndExistingBuildersWork() {
        val config = TaplinkConfig.create("app", "key")
        config.setLogEnabled(true).setLogLevel(LogLevel.DEBUG)
        config.setTimeout(30)
        assertTrue(config.logEnabled)
        assertEquals(LogLevel.DEBUG, config.logLevel)
        assertEquals(30, config.timeout)
        config.setTimeout(60)
        assertEquals(60, config.timeout)
        assertEquals("app", config.appId)
        assertSame(config, config.clearMerchantId())

        val connection = ConnectionConfig()
        assertSame(connection, connection.setHost("127.0.0.1").setPort(8443).setRetryPolicy(2, 1000))
        assertEquals(8443, connection.port)
        assertEquals(2, connection.maxRetryCount)
        val query = QueryRequest()
        assertSame(query, query.setTransactionId("original").setTransactionRequestId("request"))
        assertEquals("original", query.transactionId)
        val builder = SaleRequest.builder()
        builder.setReferenceOrderId("ORDER001").setTransactionRequestId("new-request")
        builder.setAmount(AmountInfo(BigDecimal("1000"), "USD"))
        assertSame(builder, builder.setDescription("purchase"))
        assertEquals("purchase", builder.build().description)
    }

    /** SPEC-CONFIG-001 AC2: verify init itself stores one independent config for both managers. */
    @Test fun initializationFreezesCallerConfigAndNestedMetadata() {
        val staff = StaffInfo("operator")
        val device = DeviceInfo(deviceType = "POS")
        val config = TaplinkConfig.create("app", "key")
            .setTimeout(30).setDefaultStaffInfo(staff).setDefaultDeviceInfo(device)
        val api = TaplinkApiImpl()
        val context = object : ContextWrapper(null) {
            override fun getApplicationContext(): Context = this
            override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences =
                Proxy.newProxyInstance(javaClass.classLoader, arrayOf(SharedPreferences::class.java)) { _, method, args ->
                    when (method.name) {
                        "getAll" -> emptyMap<String, Any>()
                        "contains" -> false
                        "edit" -> Proxy.newProxyInstance(javaClass.classLoader,
                            arrayOf(SharedPreferences.Editor::class.java)) { editor, editMethod, _ ->
                            when (editMethod.name) {
                                "apply" -> null
                                "commit" -> true
                                else -> editor
                            }
                        }
                        else -> args?.getOrNull(1)
                    }
                } as SharedPreferences
        }
        api.init(context, config)
        val saved = field(api, "config") as TaplinkConfig
        val payment = field(api, "paymentManager")!!
        val connection = field(api, "connectionManager")!!
        assertNotSame(config, saved)
        assertSame(saved, field(payment, "config"))
        assertSame(saved, field(connection, "config"))
        config.setTimeout(90).setAppId("changed").setLogEnabled(true)
        staff.setOperatorId("changed")
        device.setDeviceType("TABLET")
        assertEquals(30, saved.timeout)
        assertEquals("app", saved.appId)
        assertFalse(saved.logEnabled)
        assertEquals("operator", saved.defaultStaffInfo?.operatorId)
        assertEquals("POS", saved.defaultDeviceInfo?.deviceType)
        api.init(context, config)
        assertEquals(90, (field(api, "config") as TaplinkConfig).timeout)
        api.disconnect()
        api.removeConnectionListener()
    }

    /** SPEC-CONFIG-001 AC3: preserve copy/component/value behavior, including boxed nullable floats. */
    @Test fun copyComponentsAndValueSemanticsRemainAvailable() {
        val original = TaplinkConfig.create("app", "key").setTimeout(30)
        val copy = original.copy(timeout = 60)
        assertNotSame(original, copy)
        copy.setAppId("copy")
        assertEquals("app", original.appId)
        assertEquals(30, original.timeout)
        val (app, merchant, key) = original
        assertEquals("app", app)
        assertNull(merchant)
        assertEquals("key", key)
        assertEquals(original, original.copy())
        assertEquals(original.hashCode(), original.copy().hashCode())
        assertNotEquals(original.copy(taproAppWidthRatio = 0.0f), original.copy(taproAppWidthRatio = -0.0f))
        assertEquals(original.copy(taproAppWidthRatio = Float.NaN), original.copy(taproAppWidthRatio = Float.NaN))
        assertEquals(30, original.component9())
    }

    /** SPEC-CONFIG-001 AC3: Kotlin data-class boolean hashes use 0/1, not Java's 1237/1231. */
    @Test fun hashCodesMatchLegacyDataClasses() {
        for (enabled in listOf(false, true)) for (autoOpen in listOf(false, true)) {
            val config = TaplinkConfig.create("app", "key").setLogEnabled(enabled)
                .setAutoOpenUsbScreenPlayer(autoOpen)
            val legacy = LegacyConfigValue(config.appId, config.merchantId, config.secretKey,
                enabled, config.logLevel, config.defaultStaffInfo, config.defaultDeviceInfo,
                config.version, config.timeout, config.taproAppWidthRatio, autoOpen)
            assertEquals(legacy.hashCode(), config.hashCode())
        }
        for (host in listOf(false, true)) for (screen in listOf(false, true)) for (tax in listOf(false, true)) {
            val tip = TipConfig(host, screen, TipMode.ON_SALE, tax)
            assertEquals(LegacyTipValue(host, screen, TipMode.ON_SALE, tax, null).hashCode(), tip.hashCode())
        }
    }

    /** SPEC-CONFIG-001 AC4: enum getters refresh after prior reads; signing sees receiver mutations. */
    @Test fun enumGettersSerializationAndMerchantValidationStayConsistent() {
        val method = PaymentMethodInfo(_category = "CARD")
        assertEquals(PaymentCategory.CARD, method.category)
        method.setCategory(PaymentCategory.EBT).setId(PaymentMethodId.EBT).setSubId(PaymentMethodSubId.SNAP)
        assertEquals(PaymentCategory.EBT, method.category)
        assertEquals(PaymentMethodId.EBT, method.id)
        assertEquals(PaymentMethodSubId.SNAP, method.subId)
        method.setCategory("CARD")
        assertEquals(PaymentCategory.CARD, method.category)
        val request = PaymentRequest("SALE")
        request.setDescription("changed").setAmount(AmountInfo(BigDecimal("1000"), "USD"))
        val basic = ProtocolRequestBuilder.convertToBasicRequest(request, "1.1.0", "app", "key")
        val json = JSONObject(basic.bizData)
        assertEquals("changed", json.getString("description"))
        assertEquals("1000", json.getJSONObject("amount").get("orderAmount").toString())
        assertEquals("CARD", JSONObject(TaplinkJsonBridge.toJsonString(method)).getString("category"))
        val config = TaplinkConfig.create("app", "key").setMerchantId("valid")
        assertThrows(IllegalArgumentException::class.java) { config.setMerchantId("invalid space") }
        assertEquals("valid", config.merchantId)
    }

    /** SPEC-CONFIG-001 AC5: snapshot isolates all newly mutable nested payload fields. */
    @Test fun submittedSnapshotDoesNotTrackLaterSettersOrCollectionChanges() {
        val values = mutableListOf(10, 15)
        val names = mutableListOf("first", "second")
        val goods = mutableListOf(GoodsDetail(goodsId = "item"))
        val request = PaymentRequest("SALE", transactionRequestId = "original",
            amount = AmountInfo(BigDecimal("1000"), "USD"),
            paymentMethod = PaymentMethodInfo(_category = "CARD"),
            staffInfo = StaffInfo("operator"), deviceInfo = DeviceInfo(deviceType = "POS"),
            goodsDetail = goods, tipConfig = TipConfig(suggestions = TipSuggestions(FeeMode.RATE, values, names)))
        val saved = request.snapshotForExecution()
        val payload = TaplinkJsonBridge.toJsonString(saved)
        request.setTransactionRequestId("new").setRequestTimeout(5000)
        request.amount!!.setOrderAmount(BigDecimal("2000"))
        request.paymentMethod!!.setCategory("EBT")
        request.staffInfo!!.setOperatorId("new")
        request.deviceInfo!!.setDeviceType("new")
        goods[0].setGoodsId("new")
        goods.clear()
        values[0] = 99
        names.clear()
        request.tipConfig!!.setOnScreenTip(true)
        assertEquals(payload, TaplinkJsonBridge.toJsonString(saved))
        assertEquals("original", saved.transactionRequestId)
    }

    private fun field(receiver: Any, name: String): Any? =
        receiver.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(receiver)

    private data class LegacyConfigValue(
        val appId: String, val merchantId: String?, val secretKey: String,
        val logEnabled: Boolean, val logLevel: LogLevel, val staff: StaffInfo?, val device: DeviceInfo?,
        val version: String, val timeout: Int, val width: Float?, val autoOpen: Boolean
    )

    private data class LegacyTipValue(
        val host: Boolean, val screen: Boolean, val mode: TipMode,
        val tax: Boolean, val suggestions: TipSuggestions?
    )

    private fun argumentFor(method: Method): Any {
        val type = method.parameterTypes.single()
        return when {
            type == String::class.java -> if (method.name == "setPricingCurrency") "USD" else "changed"
            type == Int::class.javaPrimitiveType -> 30
            type == Long::class.javaPrimitiveType -> 5000L
            type == Boolean::class.javaPrimitiveType -> true
            type == java.lang.Float::class.java -> 0.5f
            type == BigDecimal::class.java -> BigDecimal("1000")
            type.isEnum -> type.enumConstants.last()
            type == StaffInfo::class.java -> StaffInfo("changed")
            type == DeviceInfo::class.java -> DeviceInfo(deviceType = "POS")
            type == AmountInfo::class.java -> AmountInfo(BigDecimal("1000"), "USD")
            type == PaymentMethodInfo::class.java -> PaymentMethodInfo(_category = "CARD")
            type == SignatureConfig::class.java -> SignatureConfig.none()
            type == TipConfig::class.java -> TipConfig(onScreenTip = true)
            type == TipSuggestions::class.java -> TipSuggestions(FeeMode.RATE, listOf(10))
            type == List::class.java -> when (method.name) {
                "setGoodsDetail" -> listOf(GoodsDetail(goodsId = "item"))
                "setNames" -> listOf("changed")
                else -> listOf(10, 15)
            }
            else -> error("No fixture for $method")
        }
    }
}
