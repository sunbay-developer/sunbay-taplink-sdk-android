package com.sunmi.tapro.taplink.service.local.kernel

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import com.sunmi.tapro.taplink.service.TaplinkServiceKernel
import com.sunmi.tapro.taplink.service.enums.ConnectionStatus
import com.sunmi.tapro.taplink.service.interfaces.BaseServiceKernel
import com.sunmi.tapro.taplink.service.interfaces.ConnectionCallback
import com.sunmi.tapro.taplink.service.interfaces.InnerCallback
import com.sunmi.tapro.taplink.service.local.ITransaction
import com.sunmi.tapro.taplink.service.local.ITransactionCallback
import com.google.gson.Gson
import com.sunmi.tapro.taplink.service.enums.InnerErrorCode
import com.sunmi.tapro.taplink.service.util.LogUtil
import com.sunmi.tapro.taplink.service.util.ProtocolParser
import com.sunmi.tapro.taplink.service.util.ProtocolParseResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

import kotlinx.coroutines.cancelChildren

/**
 * 本地服务内核类
 *
 * 提供本地通信的功能，实现IServiceKernel接口
 * 支持通过action和packageName启动和绑定服务
 *
 * @author TaPro Team
 * @since 2025-01-XX
 */
class LocalServiceKernel constructor(
    val appId: String,
    val appSecretKey: String
) : BaseServiceKernel() {
    private val TAG = "LocalServiceKernel"

    /**
     * 协程作用域
     */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * 并发控制互斥锁
     * 用于保证onTransaction调用的线程安全
     */
    private val transactionMutex = Mutex()

    /**
     * Gson实例，用于JSON解析
     */
    private val gson = Gson()

    /**
     * 当前连接的服务Intent
     */
    private var currentIntent: Intent? = null

    /**
     * 服务连接对象
     */
    private var serviceConnection: ServiceConnection? = null

    /**
     * 当前连接回调
     */
    private var currentConnectionCallback: ConnectionCallback? = null

    /**
     * 服务绑定状态
     */
    private var isServiceBound: Boolean = false

    /**
     * ITransaction接口实例
     */
    private var iTransaction: ITransaction? = null

    /**
     * 实现IServiceKernel接口：连接本地服务
     *
     * @param protocol 协议字符串，格式：packageName:action（如 "com.example.app:ACTION_CONNECT"）
     * @param connectionCallback 连接回调
     */
    override fun connect(protocol: String, connectionCallback: ConnectionCallback) {
        if (!canConnect()) {
            handleConnectionError(
                "Service is already connected or connecting. Current status: $currentConnectionStatus",
                connectionCallback,
                InnerErrorCode.C25.code
            )
            return
        }

        try {
            updateStatus(ConnectionStatus.CONNECTING)
            connectionCallback.onWaitingConnect()
            // 解析协议字符串
            val parseResult = ProtocolParser.parse(protocol)
            if (parseResult is ProtocolParseResult.Error) {
                handleConnectionError(
                    parseResult.message, connectionCallback,
                    InnerErrorCode.C41.code
                )
                return
            }

            if (parseResult !is ProtocolParseResult.LocalProtocol) {
                handleConnectionError(
                    "Protocol type mismatch, expected local protocol", connectionCallback,
                    InnerErrorCode.C41.code
                )
                return
            }

            val packageName = parseResult.packageName
            val action = parseResult.action

            // 本地服务连接逻辑
            LogUtil.d(TAG, "Connecting to local service: packageName=$packageName, action=$action")

            val context = TaplinkServiceKernel.getInstance()?.getContext()
            if (context == null) {
                handleConnectionError(
                    "Application context is null", connectionCallback,
                    InnerErrorCode.C20.code
                )
                return
            }

            // 创建Intent
            val intent = Intent(action)
            intent.setPackage(packageName)
            intent.putExtra("app_id", appId)
            intent.putExtra("secret_key", appSecretKey)
            // 检查服务是否存在
            val resolveInfoList = context.packageManager.queryIntentServices(intent, 0)
            LogUtil.d(TAG, "Query intent services result: ${resolveInfoList.size}")

            val matchService = resolveInfoList.find {
                it.serviceInfo.packageName == packageName
            }

            if (matchService == null) {
                handleConnectionError(
                    "Service not found: packageName=$packageName, action=$action", connectionCallback,
                    InnerErrorCode.C22.code
                )
                return
            }

            // 保存当前Intent和回调
            currentIntent = intent
            currentConnectionCallback = connectionCallback

            // 创建服务连接
            serviceConnection = createServiceConnection(connectionCallback)

            // 绑定服务
            try {
                val bindResult = context.bindService(
                    intent,
                    serviceConnection!!,
                    Context.BIND_AUTO_CREATE // 移除 BIND_NOT_FOREGROUND，因为我们不手动启动前台服务，且不需要限制后台绑定
                )
                LogUtil.d(TAG, "Bind service result: $bindResult")

                if (!bindResult) {
                    handleConnectionError(
                        "Failed to bind service", connectionCallback,
                        InnerErrorCode.C25.code
                    )
                }
                // 绑定成功会在ServiceConnection的onServiceConnected中处理
            } catch (e: Exception) {
                LogUtil.e(TAG, "Failed to bind service: ${e.message}")
                handleConnectionError("Failed to bind service: ${e.message}", connectionCallback, InnerErrorCode.C25.code)
            }

        } catch (e: Exception) {
            LogUtil.e(TAG, "Failed to connect local service\n${e.stackTraceToString()}")
            handleConnectionError(e.message ?: "Unknown error", connectionCallback, InnerErrorCode.C25.code)
        }
    }

    /**
     * 实现IServiceKernel接口：发送数据
     *
     * 使用并发控制确保onTransaction异步调用的回调正确对应
     *
     * @param data 要发送的数据
     * @param sendCallback 发送结果回调
     */
    override fun sendData(traceId: String, data: ByteArray, callback: InnerCallback?) {
        if (!canSendData()) {
            LogUtil.d(TAG, "Cannot send data, not connected. Status: $currentConnectionStatus")
            callback?.onError("NOT_CONNECTED", "Service not connected")
            return
        }

        if (iTransaction == null) {
            LogUtil.e(TAG, "ITransaction is null, cannot send data")
            callback?.onError("ITRANSACTION_NULL", "ITransaction interface is null")
            return
        }

        // 在协程中执行，使用Mutex保证并发安全
        scope.launch {
            transactionMutex.withLock {
                // 将ByteArray转换为String（假设是UTF-8编码）
                val requestData = String(data, Charsets.UTF_8)

                try {
                    LogUtil.d(TAG, "Sending data via ITransaction: traceId=$traceId, dataSize=${data.size} bytes")

                    // 创建回调对象
                    // 注意：不直接调用传入的 callback，而是通过 dataReceiver 传递响应
                    // 这样可以在 TaplinkApiImpl 中统一管理回调的调用和移除
                    val transactionCallback = object : ITransactionCallback.Stub() {
                        override fun callback(responseData: String?) {
                            val response = responseData ?: "SUCCESS"

                            // 通过 dataReceiver 传递响应（供 TaplinkApiImpl 使用）
                            // 这样可以通过 processReceivedResponse 统一管理回调的调用和移除
                            dataReceiver?.invoke(response.toByteArray(Charsets.UTF_8))
                        }

                        override fun onError(code: String?, msg: String?) {
                            // 构建包含 traceId 的错误响应 JSON，以便 TaplinkApiImpl 能够匹配回调
                            val errorCode = code ?: "UNKNOWN_ERROR"
                            val errorMsg = msg ?: "Unknown error"
                            val errorResponse = try {
                                """{"traceId":"$traceId","eventCode":"ERROR","errorCode":"$errorCode","eventMsg":"$errorMsg"}"""
                            } catch (e: Exception) {
                                "ERROR: $errorCode - $errorMsg"
                            }

                            // 错误信息也通过 dataReceiver 传递
                            dataReceiver?.invoke(errorResponse.toByteArray(Charsets.UTF_8))
                        }
                    }

                    // 调用ITransaction的onTransaction方法（异步）
                    iTransaction?.onTransaction(requestData, transactionCallback)
                    LogUtil.d(TAG, "Data sent via ITransaction successfully: traceId=$traceId")
                } catch (e: Exception) {
                    LogUtil.e(TAG, "Failed to send data via ITransaction: traceId=$traceId, error=${e.stackTraceToString()}")
                    updateStatus(ConnectionStatus.ERROR)
                    callback?.onError("SEND_ERROR", e.message ?: "Unknown error")
                }
            }
        }
    }

    /**
     * 实现IServiceKernel接口：断开连接
     */
    override fun disconnect() {
        if (currentConnectionStatus == ConnectionStatus.DISCONNECTED) {
            return
        }
        try {
            // 取消所有协程
            scope.coroutineContext.cancelChildren()

            val context = TaplinkServiceKernel.getInstance()?.getContext()
            val intent = currentIntent

            // 解绑服务
            if (isServiceBound && serviceConnection != null && context != null && intent != null) {
                try {
                    context.unbindService(serviceConnection!!)
                    LogUtil.d(TAG, "Service unbound successfully")
                } catch (e: Exception) {
                    LogUtil.e(TAG, "Failed to unbind service: ${e.message}")
                }
                isServiceBound = false
            }

            // 清理资源
            serviceConnection = null
            currentIntent = null
            currentConnectionCallback = null
            iTransaction = null
            dataReceiver = null

            updateStatus(ConnectionStatus.DISCONNECTED)
            LogUtil.d(TAG, "Local service disconnected")
        } catch (e: Exception) {
            LogUtil.e(TAG, "Error during disconnect: ${e.stackTraceToString()}")
            updateStatus(ConnectionStatus.ERROR)
        }
    }

    /**
     * 创建服务连接对象
     *
     * @param connectionCallback 连接回调
     * @return ServiceConnection 服务连接对象
     */
    private fun createServiceConnection(connectionCallback: ConnectionCallback): ServiceConnection {
        return object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                LogUtil.d(TAG, "Service connected: $name")
                isServiceBound = true

                if (binder != null) {
                    try {
                        // 将binder转换为ITransaction接口
                        iTransaction = ITransaction.Stub.asInterface(binder)
                        if (iTransaction != null) {
                            LogUtil.d(TAG, "ITransaction interface obtained successfully")
                            // 服务绑定成功
                            handleConnectionSuccess(connectionCallback, iTransaction!!.getExtraInfos())
                        } else {
                            LogUtil.e(TAG, "Failed to convert binder to ITransaction")
                            handleConnectionError(
                                "Failed to convert binder to ITransaction", connectionCallback,
                                InnerErrorCode.C25.code
                            )
                        }
                    } catch (e: Exception) {
                        LogUtil.e(TAG, "Exception while converting binder to ITransaction: ${e.message}")
                        handleConnectionError(
                            "Exception while converting binder: ${e.message}",
                            connectionCallback,
                            InnerErrorCode.C25.code
                        )
                    }
                } else {
                    // 服务绑定失败（binder为null）
                    handleConnectionError("Service binder is null", connectionCallback, InnerErrorCode.C25.code)
                }
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                LogUtil.d(TAG, "Service disconnected: $name")
                isServiceBound = false
                iTransaction = null

                // 服务异常断开
                updateStatus(ConnectionStatus.DISCONNECTED)
                currentConnectionCallback?.onDisconnected(InnerErrorCode.C36.code, "Service disconnected unexpectedly")
            }

            override fun onBindingDied(name: ComponentName?) {
                LogUtil.d(TAG, "Service binding died: $name")
                isServiceBound = false
                iTransaction = null

                // 服务绑定死亡
                updateStatus(ConnectionStatus.ERROR)
                currentConnectionCallback?.onDisconnected(InnerErrorCode.C36.code, "Service binding died")
            }

            override fun onNullBinding(name: ComponentName?) {
                LogUtil.d(TAG, "Service null binding: $name")
                isServiceBound = false

                // 服务返回null绑定
                handleConnectionError("Service returned null binding", connectionCallback, InnerErrorCode.C36.code)
            }
        }
    }
}

