package com.sunmi.tapro.taplink.sdk.impl

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.sunmi.tapro.taplink.sdk.BuildConfig
import com.sunmi.tapro.taplink.sdk.api.TaplinkApi
import com.sunmi.tapro.taplink.sdk.callback.ConnectionListener
import com.sunmi.tapro.taplink.sdk.callback.PaymentCallback
import com.sunmi.tapro.taplink.sdk.config.ConnectionConfig
import com.sunmi.tapro.taplink.sdk.config.TaplinkConfig
import com.sunmi.tapro.taplink.sdk.enums.ConnectionStatus
import com.sunmi.tapro.taplink.sdk.enums.LogLevel
import com.sunmi.tapro.taplink.sdk.enums.TransactionAction
import com.sunmi.tapro.taplink.sdk.error.ConnectionError
import com.sunmi.tapro.taplink.sdk.error.PaymentError
import com.sunmi.tapro.taplink.sdk.model.base.BasicResponse
import com.sunmi.tapro.taplink.sdk.model.common.PaymentEvent
import com.sunmi.tapro.taplink.sdk.model.request.PaymentRequest
import com.sunmi.tapro.taplink.sdk.model.request.QueryRequest
import com.sunmi.tapro.taplink.sdk.model.response.PaymentResult
import com.google.gson.JsonObject
import com.sunmi.tapro.taplink.sdk.util.RequestConverter
import com.sunmi.tapro.taplink.sdk.reconnect.ReconnectManager
import com.sunmi.tapro.taplink.communication.TaplinkServiceKernel
import com.sunmi.tapro.taplink.communication.interfaces.InnerCallback
import com.sunmi.tapro.taplink.communication.util.LocalCallbackManager
import com.sunmi.tapro.taplink.communication.util.LogUtil
import com.sunmi.tapro.taplink.communication.protocol.ProtocolManager
import com.sunmi.tapro.taplink.communication.interfaces.ConnectionCallback as ServiceConnectionCallback
import com.sunmi.tapro.taplink.sdk.util.ProtocolBuilder

/**
 * Taplink API 实现类
 *
 * 具体实现TaplinkApi接口的所有方法
 * 这是代理类，负责具体的业务逻辑实现
 *
 * @author TaPro Team
 * @since 2025-01-XX
 */
class TaplinkApiImpl : TaplinkApi {

    private val TAG = "TaplinkApiImpl"

    companion object {
        private const val DEFAULT_TIMEOUT_SECONDS = 60
        private const val DEFAULT_CURRENCY = "USD"
        private const val ERROR_EVENT_CODE = "ERROR"
        private const val SUCCESS_CODE = "0"
    }

    /**
     * SDK配置
     */
    private var config: TaplinkConfig? = null

    /**
     * 连接状态
     */
    private var connectionStatus: ConnectionStatus = ConnectionStatus.DISCONNECTED

    /**
     * 连接监听器
     */
    private var connectionListener: ConnectionListener? = null

    /**
     * 当前连接模式
     */
    private var currentConnectionMode: String? = null


    /**
     * 已连接设备ID
     */
    private var connectedDeviceId: String? = null
    private var connectedTaproVersion: String? = null

    /**
     * 重连管理器（延迟初始化）
     */
    private var reconnectManager: ReconnectManager? = null

    /**
     * Gson实例，用于JSON序列化
     */
    private val gson = Gson()

    /**
     * 回调管理器
     * 用于管理多个异步请求的回调，支持根据traceId查找和移除对应的回调
     */
    private val callbackManager = LocalCallbackManager<InnerCallback?>(
        timeoutMillis = 60_000L, // 超时时间60秒
        tag = TAG
    )

    init {
        LogUtil.d(TAG, "TaplinkApiImpl initialized")
    }

    // ==================== 初始化 ====================

    override fun init(context: Context, config: TaplinkConfig) {
        LogUtil.d(TAG, "Initializing SDK with appId: ${config.appId}")
        this.config = config

        // 初始化重连管理器
        reconnectManager = ReconnectManager(config) { connectionConfig, listener ->
            connectInternal(connectionConfig, listener)
        }

        // 初始化 ServiceKernel
        TaplinkServiceKernel.getInstance(context)

        // 配置日志级别
        if (config.logEnabled) {
            when (config.logLevel) {
                LogLevel.INFO -> LogUtil.setLevel(Log.INFO)
                LogLevel.WARN -> LogUtil.setLevel(Log.WARN)
                LogLevel.DEBUG -> LogUtil.setLevel(Log.DEBUG)
                LogLevel.ERROR -> LogUtil.setLevel(Log.ERROR)
            }
        } else {
            LogUtil.setLevel(9999)
        }
    }

    // ==================== 连接管理 ====================

    override fun connect(config: ConnectionConfig?, listener: ConnectionListener) {
        // 如果没有传递 config，尝试从 ReconnectManager 获取上次的连接参数
        val actualConfig = config ?: reconnectManager?.getLastConnectionConfig()

        if (actualConfig == null) {
            LogUtil.e(TAG, "No connection config provided and no previous connection config available")
            listener.onError(
                ConnectionError(
                    "C21",
                    "No connection config provided and no previous connection config available"
                )
            )
            return
        }

        LogUtil.d(TAG, "Using connection config: ${if (config != null) "provided" else "previous"}")

        // 准备重连管理器
        reconnectManager?.prepareConnect(actualConfig, listener)

        // 执行实际连接
        connectInternal(actualConfig, listener)
    }

    /**
     * 内部连接方法
     *
     * 实际执行连接逻辑，供 connect() 和 ReconnectManager 调用
     */
    private fun connectInternal(config: ConnectionConfig?, listener: ConnectionListener) {
        LogUtil.d(
            TAG,
            "Connecting with config: cableProtocol=${config?.cableProtocol}, host=${config?.host}, port=${config?.port}"
        )

        if (this.config?.appId.isNullOrBlank() || this.config?.secretKey.isNullOrBlank()) {
            listener.onError(
                ConnectionError(
                    "C20",
                    "SDK not initialized, please call init() first"
                )
            )
            return
        }

        // 根据 ConnectionConfig 构建协议字符串
        val (protocol, connectionMode) = ProtocolBuilder.buildProtocol(config, this.config!!)
        currentConnectionMode = connectionMode
        LogUtil.d(TAG, "Built protocol string: $protocol, connectionMode: $connectionMode")

        // 验证协议格式
        if (!ProtocolManager.isValidProtocol(protocol)) {
            listener.onError(
                ConnectionError(
                    "C41",
                    "Invalid protocol format: $protocol"
                )
            )
            return
        }

        // 通过 ServiceKernel 连接
        TaplinkServiceKernel.getInstance()
            ?.connect(
                protocol,
                this.config!!.appId,
                this.config!!.secretKey,
                object : ServiceConnectionCallback {
                    override fun onConnected(extraInfoMap: Map<String, String?>?) {
                        LogUtil.d(TAG, "onConnected, extraInfoMap=${extraInfoMap.toString()}")

                        // 通知重连管理器连接成功
                        reconnectManager?.onConnected()

                        // 注册数据接收器，用于接收响应
                        registerDataReceiver()

                        // 发送 INIT 指令初始化 tapro 应用，等待结果后再通知连接成功
                        sendInitCommand { success, errorCode, errorMsg, deviceId, taproVersion ->
                            if (success) {
                                LogUtil.d(TAG, "INIT command completed successfully, notifying connection listeners")
                                updateConnectionStatus(ConnectionStatus.CONNECTED)

                                // 连接参数已经在 prepareConnect 时保存到 ReconnectManager 中
                                LogUtil.d(TAG, "Connection successful, config saved in ReconnectManager")

                                // 从 INIT 响应中获取设备信息
                                connectedDeviceId = deviceId ?: "unknown"
                                connectedTaproVersion = taproVersion ?: "unknown"

                                LogUtil.d(
                                    TAG,
                                    "Device info from INIT response: deviceId=$connectedDeviceId, taproVersion=${connectedTaproVersion}"
                                )

                                // 通知监听器连接成功
                                listener.onConnected(connectedDeviceId ?: "unknown", connectedTaproVersion ?: "unknown")
                                connectionListener?.onConnected(
                                    connectedDeviceId ?: "unknown",
                                    connectedTaproVersion ?: "unknown"
                                )
                            } else {
                                LogUtil.e(TAG, "INIT command failed: code=$errorCode, msg=$errorMsg")
                                updateConnectionStatus(ConnectionStatus.DISCONNECTED)

                                val error = ConnectionError(
                                    errorCode ?: "INIT_FAILED",
                                    errorMsg ?: "Failed to initialize tapro application"
                                )
                                listener.onError(error)
                                connectionListener?.onError(error)
                            }
                        }
                    }

                    override fun onWaitingConnect() {
                        updateConnectionStatus(ConnectionStatus.WAIT_CONNECTING)
                    }

                    override fun onDisconnected(code: String, msg: String) {
                        updateConnectionStatus(ConnectionStatus.DISCONNECTED)
                        val reason = "Code: $code, Message: $msg"

                        // 尝试自动重连
                        val reconnected = reconnectManager?.onDisconnected(listener) ?: false

                        if (!reconnected) {
                            // 不重连，通知应用层并清理资源
                            listener.onDisconnected(reason)
                            connectionListener?.onDisconnected(reason)

                            // 清理资源（只在不重连时清理）
                            connectedDeviceId = null
                            connectedTaproVersion = null
                            currentConnectionMode = null
                            callbackManager.clearAll()
                        }
                        // 如果正在重连，保留设备信息和连接模式，不清理回调管理器
                    }

                    override fun onServiceAddressChanged(newHost: String, newPort: Int, oldHost: String, oldPort: Int) {
                        LogUtil.i(TAG, "Service address changed detected: $oldHost:$oldPort -> $newHost:$newPort")

                        // 创建新的连接配置
                        val newConnectionConfig = ConnectionConfig().apply {
                            setHost(newHost)
                            setPort(newPort)
                        }

                        LogUtil.d(TAG, "Triggering reconnection with new address: $newHost:$newPort")

                        // 通知重连管理器地址变更，触发重连
                        reconnectManager?.onAddressChanged(newConnectionConfig, listener)
                    }
                })
    }

    override fun disconnect() {
        LogUtil.d(TAG, "Manual disconnect")

        // 通知重连管理器主动断开
        reconnectManager?.disconnect()

        updateConnectionStatus(ConnectionStatus.DISCONNECTED)
        TaplinkServiceKernel.getInstance()?.getCurrentServiceKernel()?.disconnect()
        connectedDeviceId = null
        connectedTaproVersion = null
        currentConnectionMode = null

        // ReconnectManager 会在 disconnect() 时清空保存的连接参数
        LogUtil.d(TAG, "Connection config will be cleared by ReconnectManager")

        // 清理所有待处理的请求回调
        callbackManager.clearAll()
    }

    override fun isConnected(): Boolean {
        return connectionStatus == ConnectionStatus.CONNECTED
    }

    override fun getConnectedDeviceId(): String? {
        return connectedDeviceId
    }

    override fun getConnectionMode(): String? {
        return currentConnectionMode
    }

    override fun getTaproVersion(): String? {
        return connectedTaproVersion
    }

    // ==================== 交易执行 ====================

    override fun execute(request: PaymentRequest, callback: PaymentCallback) {
        LogUtil.d(TAG, "Executing payment request: action=${request.action}, referenceOrderId=${request.referenceOrderId}")
        sendPaymentRequest(request, callback)
    }

    // ==================== 交易查询 ====================

    /**
     * 异步查询交易状态
     *
     * **实现方式：** 将 QueryRequest 转换为 PaymentRequest（action="QUERY"），然后调用 sendPaymentRequest
     * 这样可以复用相同的请求发送逻辑，确保代码一致性
     *
     * @param request 查询请求对象
     * @param callback 支付回调接口
     */
    override fun query(request: QueryRequest, callback: PaymentCallback) {
        LogUtil.d(
            TAG,
            "Querying transaction: transactionId=${request.transactionId}, transactionRequestId=${request.transactionRequestId}"
        )

        try {
            // 将 QueryRequest 转换为 PaymentRequest
            val paymentRequest = convertQueryRequestToPaymentRequest(request)

            // 调用 sendPaymentRequest 发送请求
            sendPaymentRequest(paymentRequest, callback)
        } catch (e: Exception) {
            LogUtil.e(TAG, "Error converting QueryRequest to PaymentRequest: ${e.message}")
            callback.onFailure(PaymentError("REQUEST_ERROR", e.message ?: "Failed to convert query request"))
        }
    }

    // ==================== 监听器管理 ====================

    override fun setConnectionListener(listener: ConnectionListener?) {
        LogUtil.d(TAG, "Setting connection listener")
        this.connectionListener = listener
    }

    override fun removeConnectionListener() {
        LogUtil.d(TAG, "Removing connection listener")
        this.connectionListener = null
    }

    // ==================== 实用方法 ====================

    override fun getVersion(): String {
        return config?.version ?: BuildConfig.VERSION_NAME
    }

    override fun clearDeviceCache() {
        LogUtil.d(TAG, "Clearing device cache")
        connectedDeviceId = null
        connectedTaproVersion = null
        currentConnectionMode = null
        // TODO: 清除本地缓存的设备信息
    }

    // ==================== 私有辅助方法 ====================


    /**
     * 将 QueryRequest 转换为 PaymentRequest
     *
     * 查询请求需要转换为 PaymentRequest 格式，action 设置为 "QUERY"
     * 其他必需字段使用默认值或从配置中获取
     *
     * @param queryRequest 查询请求对象
     * @return PaymentRequest 转换后的支付请求对象
     */
    private fun convertQueryRequestToPaymentRequest(queryRequest: QueryRequest): PaymentRequest {
        val currentConfig = config
        if (currentConfig == null) {
            throw IllegalStateException("SDK not initialized")
        }

        // 验证查询请求必须有一个查询条件
        if (queryRequest.transactionId == null && queryRequest.transactionRequestId == null) {
            throw IllegalArgumentException("Either transactionId or transactionRequestId must be provided")
        }

        val transactionRequestId = queryRequest.transactionRequestId

        // 构建查询描述
        val description = when {
            queryRequest.transactionId != null -> "Query transaction by transactionId: ${queryRequest.transactionId}"
            queryRequest.transactionRequestId != null -> "Query transaction by transactionRequestId: ${queryRequest.transactionRequestId}"
            else -> "Query transaction"
        }

        // 构建 PaymentRequest，action 为 QUERY
        // 查询时，将查询条件放入 originalTransactionId 或 originalTransactionRequestId 字段
        return PaymentRequest(
            action = TransactionAction.QUERY.value,
            transactionRequestId = transactionRequestId,
            description = description,
            // 查询条件：根据 QueryRequest 中的字段设置
            originalTransactionId = queryRequest.transactionId,
            originalTransactionRequestId = queryRequest.transactionRequestId,
            // 使用配置中的默认值
            staffInfo = currentConfig.defaultStaffInfo,
            deviceInfo = currentConfig.defaultDeviceInfo
        )
    }

    /**
     * 发送支付请求
     *
     * @param request 支付请求
     * @param callback 支付回调
     */
    private fun sendPaymentRequest(request: PaymentRequest, callback: PaymentCallback) {
        try {
            // 验证前置条件
            val currentConfig = validateAndGetConfig(callback) ?: return
            if (!validateConnection(callback)) return

            // 将 PaymentRequest 转换为 BasicRequest
            val basicRequest = RequestConverter.convertToBasicRequest(
                request = request,
                version = currentConfig.version,
                currentConfig.appId,
                secretKey = currentConfig.secretKey
            )

            // 将 BasicRequest 转换为 JSON 字符串
            val requestJson = gson.toJson(basicRequest)
            LogUtil.d(TAG, "Sending BasicRequest: action=${request.action}, requestSize=${requestJson.length} bytes")

            // 创建内部回调，用于接收原始响应
            val internalCallback = createInternalCallback(callback, request, basicRequest.traceId)

            // 在发送前添加回调到 LocalCallbackManager
            callbackManager.addCallbackByTraceId(basicRequest.traceId, internalCallback)
            LogUtil.d(TAG, "Callback registered with traceId: ${basicRequest.traceId}")

            // 通过 TaplinkServiceKernel 发送数据
            val serviceKernel = TaplinkServiceKernel.getInstance()
            if (serviceKernel == null) {
                LogUtil.e(TAG, "Service kernel not available")
                callbackManager.removeCallbackByTraceId(basicRequest.traceId)
                callback.onFailure(PaymentError("NOT_CONNECTED", "Service kernel not available"))
                return
            }

            // 发送数据
            try {
                serviceKernel.sendData(
                    basicRequest.traceId,
                    requestJson.toByteArray(Charsets.UTF_8),
                    internalCallback // 回调已由 LocalCallbackManager 管理
                )
            } catch (e: Exception) {
                // 发送失败时，移除回调并通知错误
                LogUtil.e(TAG, "Failed to send data: traceId=${basicRequest.traceId}, error=${e.message}")
                callbackManager.removeCallbackByTraceId(basicRequest.traceId)
                callback.onFailure(PaymentError("SEND_ERROR", "Failed to send data: ${e.message}"))
            }
        } catch (e: Exception) {
            LogUtil.e(TAG, "Error processing request: action=${request.action}, error=${e.message}")
            callback.onFailure(PaymentError("REQUEST_ERROR", e.message ?: "Unknown error"))
        }
    }

    /**
     * 发送 INIT 指令初始化 tapro 应用
     *
     * @param onComplete 完成回调，参数为 (success: Boolean, errorCode: String?, errorMsg: String?, deviceId: String?, taproVersion: String?)
     */
    private fun sendInitCommand(onComplete: (Boolean, String?, String?, String?, String?) -> Unit) {
        try {
            LogUtil.d(TAG, "Sending INIT command to initialize tapro application")

            val currentConfig = config
            if (currentConfig == null) {
                LogUtil.e(TAG, "Cannot send INIT command: SDK not initialized")
                onComplete(false, "SDK_NOT_INITIALIZED", "SDK not initialized", null, null)
                return
            }

            // 构建 INIT 请求
            val initRequest = PaymentRequest(
                action = TransactionAction.INIT.value
            )

            // 将 PaymentRequest 转换为 BasicRequest
            val basicRequest = RequestConverter.convertToBasicRequest(
                request = initRequest,
                version = currentConfig.version,
                currentConfig.appId,
                secretKey = currentConfig.secretKey
            )

            // 将 BasicRequest 转换为 JSON 字符串
            val requestJson = gson.toJson(basicRequest)
            LogUtil.d(TAG, "INIT command prepared: traceId=${basicRequest.traceId}")

            // 创建内部回调，用于接收 INIT 响应
            val initCallback = object : InnerCallback {
                override fun onError(code: String, msg: String) {
                    LogUtil.e(TAG, "INIT command failed: code=$code, msg=$msg")
                    onComplete(false, code, msg, null, null)
                }

                override fun onResponse(responseData: String) {
                    LogUtil.d(TAG, "INIT command response received: $responseData")
                    try {
                        // 解析响应，判断是否成功
                        val basicResponse = gson.fromJson(responseData, BasicResponse::class.java)

                        // 从 bizData 直接解析设备信息
                        var code = "ERROR"
                        var message: String? = basicResponse.event.eventMsg
                        var deviceId: String? = null
                        var taproVersion: String? = null

                        if (basicResponse.bizData != null) {
                            try {
                                val bizDataJson = gson.fromJson(basicResponse.bizData, JsonObject::class.java)
                                code = bizDataJson.get("code")?.asString ?: "ERROR"
                                message = bizDataJson.get("message")?.asString
                                deviceId = bizDataJson.get("deviceId")?.asString
                                taproVersion = bizDataJson.get("taproVersion")?.asString
                            } catch (e: Exception) {
                                LogUtil.w(TAG, "Failed to parse bizData as JSON: ${e.message}")
                            }
                        }

                        // 判断是否成功
                        if (code == SUCCESS_CODE && basicResponse.event is PaymentEvent.Completed) {
                            LogUtil.d(TAG, "INIT command completed successfully")
                            LogUtil.d(TAG, "Device info: deviceId=$deviceId, taproVersion=$taproVersion")
                            onComplete(true, null, null, deviceId, taproVersion)
                        } else {
                            LogUtil.e(TAG, "INIT command failed: code=$code, message=$message")
                            onComplete(false, code, message, null, null)
                        }
                    } catch (e: Exception) {
                        LogUtil.e(TAG, "Failed to parse INIT response: ${e.message}")
                        onComplete(false, "PARSE_ERROR", "Failed to parse INIT response: ${e.message}", null, null)
                    }
                }
            }

            // 注册回调
            callbackManager.addCallbackByTraceId(basicRequest.traceId, initCallback)

            // 发送 INIT 指令
            val serviceKernel = TaplinkServiceKernel.getInstance()
            if (serviceKernel == null) {
                LogUtil.e(TAG, "Service kernel not available")
                callbackManager.removeCallbackByTraceId(basicRequest.traceId)
                onComplete(false, "SERVICE_UNAVAILABLE", "Service kernel not available", null, null)
                return
            }

            try {
                serviceKernel.sendData(
                    basicRequest.traceId,
                    requestJson.toByteArray(Charsets.UTF_8),
                    initCallback
                )
                LogUtil.d(TAG, "INIT command sent successfully")
            } catch (e: Exception) {
                LogUtil.e(TAG, "Failed to send INIT command: ${e.message}")
                callbackManager.removeCallbackByTraceId(basicRequest.traceId)
                onComplete(false, "SEND_ERROR", "Failed to send INIT command: ${e.message}", null, null)
            }
        } catch (e: Exception) {
            LogUtil.e(TAG, "Failed to prepare INIT command: ${e.message}")
            onComplete(false, "PREPARE_ERROR", "Failed to prepare INIT command: ${e.message}", null, null)
        }
    }

    /**
     * 注册数据接收器，用于接收响应
     */
    private fun registerDataReceiver() {
        TaplinkServiceKernel.getInstance()?.getCurrentServiceKernel()?.registerDataReceiver { data ->
            try {
                if (data == null || data.isEmpty()) {
                    return@registerDataReceiver
                }

                val responseJson = String(data, Charsets.UTF_8)
                processReceivedResponse(responseJson)
            } catch (e: Exception) {
                LogUtil.e(TAG, "Failed to process received data: ${e.message}")
            }
        }
    }

    /**
     * 处理接收到的响应数据
     *
     * @param responseJson 响应JSON字符串
     */
    private fun processReceivedResponse(responseJson: String) {
        try {
            val jsonObject = JsonParser.parseString(responseJson).asJsonObject
            val traceId = jsonObject.get("traceId")?.asString
            val eventCode = jsonObject.get("eventCode")?.asString?.uppercase()

            if (traceId.isNullOrBlank()) {
                LogUtil.e(TAG, "Received data without traceId, ignoring: $responseJson")
                return
            }

            // 判断是否是错误响应
            val isError = eventCode == ERROR_EVENT_CODE

            // 判断是否需要移除回调
            val shouldRemove = shouldRemoveCallback(responseJson) || isError

            val callback = if (shouldRemove) {
                callbackManager.getAndRemoveCallbackByTraceId(traceId)
            } else {
                callbackManager.getCallbackByTraceId(traceId)
            }

            // 根据响应类型调用相应的回调方法
            if (isError) {
                val errorCode = jsonObject.get("errorCode")?.asString ?: "UNKNOWN_ERROR"
                val errorMsg = jsonObject.get("eventMsg")?.asString ?: "Unknown error"
                callback?.onError(errorCode, errorMsg)
            } else {
                callback?.onResponse(responseJson)
            }
        } catch (e: Exception) {
            // 如果无法解析为 JSON，可能是旧格式的错误响应
            LogUtil.e(TAG, "Failed to parse response as JSON: $responseJson, error=${e.message}")
        }
    }

    /**
     * 创建内部回调对象
     *
     * @param callback 支付回调
     * @param request 支付请求
     * @param traceId 追踪ID
     * @return InnerCallback 内部回调对象
     */
    private fun createInternalCallback(
        callback: PaymentCallback,
        request: PaymentRequest,
        traceId: String
    ): InnerCallback {
        return object : InnerCallback {
            override fun onError(code: String, msg: String) {
                LogUtil.e(TAG, "Received error: code=$code, msg=$msg, traceId=$traceId")
                callback.onFailure(PaymentError(code, msg))
            }

            override fun onResponse(responseData: String) {
                handleResponse(responseData, callback, request)
            }
        }
    }

    /**
     * 判断是否需要移除回调
     *
     * @param responseJson 响应JSON字符串
     * @return Boolean 是否需要移除回调
     */
    private fun shouldRemoveCallback(responseJson: String): Boolean {
        return try {
            val eventCode = extractEventCode(responseJson)
            if (eventCode == null) {
                return false
            }

            // 使用 PaymentEvent.fromEventCode 判断是否是 Completed 或 Cancel
            val paymentEvent = PaymentEvent.fromEventCode(eventCode)
            when (paymentEvent) {
                is PaymentEvent.Completed -> true
                is PaymentEvent.Cancel -> true
                else -> false
            }
        } catch (e: Exception) {
            LogUtil.e(TAG, "Failed to parse responseJson for shouldRemoveCallback: ${e.message}")
            true // 解析失败时默认移除
        }
    }

    /**
     * 从响应JSON中提取eventCode
     *
     * @param responseJson 响应JSON字符串
     * @return String? eventCode，如果解析失败则返回null
     */
    private fun extractEventCode(responseJson: String): String? {
        return try {
            val jsonObject = JsonParser.parseString(responseJson).asJsonObject
            val eventCodeElement = jsonObject.get("eventCode")
            when {
                eventCodeElement?.isJsonPrimitive == true -> {
                    val primitive = eventCodeElement.asJsonPrimitive
                    when {
                        primitive.isString -> primitive.asString
                        primitive.isNumber -> primitive.asString
                        else -> null
                    }
                }

                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 处理响应数据
     *
     * @param result 响应JSON字符串
     * @param callback 支付回调
     * @param request 原始支付请求
     */
    private fun handleResponse(result: String, callback: PaymentCallback, request: PaymentRequest) {
        try {
            val basicResponse = gson.fromJson(result, BasicResponse::class.java)
            LogUtil.d(
                TAG,
                "Received BasicResponse: eventCode=${basicResponse.event.eventCode}, eventMsg=${basicResponse.event.eventMsg}"
            )

            // 从 bizData 解析 PaymentResult
            val paymentResult = if (basicResponse.bizData != null) {
                gson.fromJson(basicResponse.bizData, PaymentResult::class.java)
            } else {
                // 如果 bizData 为空，创建一个默认的 PaymentResult
                PaymentResult(
                    code = "ERROR",
                    message = basicResponse.event.eventMsg
                )
            }

            // 先判断 code 码
            if (paymentResult.code != SUCCESS_CODE) {
                // code 不为 0，调用 onFailure
                LogUtil.e(TAG, "Payment failed: code=${paymentResult.code}, message=${paymentResult.message}")
                callback.onFailure(
                    PaymentError(
                        paymentResult.code,
                        paymentResult.message ?: "unknown error"
                    )
                )
            } else {
                // code 为 0，根据 PaymentEvent 类型判断
                when (basicResponse.event) {
                    is PaymentEvent.Completed -> {
                        // 事件是 Completed，调用 onSuccess
                        LogUtil.d(
                            TAG,
                            "Payment completed successfully: eventCode=${basicResponse.event.eventCode}"
                        )
                        callback.onSuccess(paymentResult)
                    }

                    is PaymentEvent.Cancel -> {
                        // 事件是 Cancel，调用 onFailure
                        LogUtil.e(
                            TAG,
                            "Payment cancelled: eventCode=${basicResponse.event.eventCode}, eventMsg=${basicResponse.event.eventMsg}"
                        )
                        callback.onFailure(
                            PaymentError(
                                "CANCELLED",
                                basicResponse.event.eventMsg
                            )
                        )
                    }

                    else -> {
                        // 其他事件类型，调用 onProgress
                        LogUtil.d(
                            TAG,
                            "Payment in progress: eventCode=${basicResponse.event.eventCode}, eventMsg=${basicResponse.event.eventMsg}, progress=${basicResponse.event.progress}"
                        )
                        callback.onProgress(basicResponse.event)
                    }
                }
            }
        } catch (e: Exception) {
            // 解析失败，作为发送失败处理
            LogUtil.e(TAG, "Failed to parse response: action=${request.action}, result=$result, error=${e.message}")
            callback.onFailure(PaymentError("PARSE_ERROR", "Failed to parse response: ${e.message}"))
        }
    }

    /**
     * 验证并获取配置
     *
     * @param callback 支付回调，用于通知错误
     * @return TaplinkConfig? 配置对象，如果验证失败则返回null
     */
    private fun validateAndGetConfig(callback: PaymentCallback): TaplinkConfig? {
        val currentConfig = config
        if (currentConfig == null) {
            LogUtil.e(TAG, "SDK not initialized, please call init() first")
            callback.onFailure(PaymentError("SDK_NOT_INITIALIZED", "SDK not initialized, please call init() first"))
            return null
        }
        return currentConfig
    }

    /**
     * 验证连接状态
     *
     * @param callback 支付回调，用于通知错误
     * @return Boolean 是否已连接
     */
    private fun validateConnection(callback: PaymentCallback): Boolean {
        if (!isConnected()) {
            LogUtil.e(TAG, "Not connected, please call connect() first")
            callback.onFailure(PaymentError("NOT_CONNECTED", "Not connected, please call connect() first"))
            return false
        }
        return true
    }

    /**
     * 更新连接状态
     *
     * @param newStatus 新状态
     */
    private fun updateConnectionStatus(newStatus: ConnectionStatus) {
        if (connectionStatus != newStatus) {
            val oldStatus = connectionStatus
            connectionStatus = newStatus
            LogUtil.d(TAG, "Connection status changed: $oldStatus -> $newStatus")
            // 通知监听器
            connectionListener?.let { listener ->
                when (newStatus) {
                    ConnectionStatus.CONNECTED -> {
                        // 连接成功时，使用实际获取的deviceId和taproVersion
                        listener.onConnected(connectedDeviceId ?: "unknown", connectedTaproVersion ?: "unknown")
                    }

                    ConnectionStatus.DISCONNECTED -> {
                        listener.onDisconnected("User disconnected")
                    }

                    ConnectionStatus.ERROR -> {
                        listener.onError(com.sunmi.tapro.taplink.sdk.error.ConnectionError("UNKNOWN", "Connection error"))
                    }

                    else -> {
                        // 其他状态
                    }
                }
            }
        }
    }
}
