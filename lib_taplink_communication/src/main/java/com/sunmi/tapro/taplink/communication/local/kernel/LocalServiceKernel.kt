package com.sunmi.tapro.taplink.communication.local.kernel

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import com.sunmi.tapro.taplink.communication.TaplinkServiceKernel
import com.sunmi.tapro.taplink.communication.enums.ConnectionStatus
import com.sunmi.tapro.taplink.communication.interfaces.AsyncServiceKernel
import com.sunmi.tapro.taplink.communication.interfaces.ConnectionCallback
import com.sunmi.tapro.taplink.communication.interfaces.InnerCallback
import com.sunmi.tapro.taplink.communication.local.ITransaction
import com.sunmi.tapro.taplink.communication.local.ITransactionCallback
import com.google.gson.Gson
import com.sunmi.tapro.taplink.communication.enums.InnerErrorCode
import com.sunmi.tapro.taplink.communication.util.LogUtil
import com.sunmi.tapro.taplink.communication.util.ProtocolParseResult

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
    appId: String,
    appSecretKey: String
) : AsyncServiceKernel(appId, appSecretKey) {

    companion object {
        private const val TAG = "LocalServiceKernel"
    }

    override fun getTag(): String = TAG

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
     * 服务绑定状态
     */
    private var isServiceBound: Boolean = false

    /**
     * ITransaction接口实例
     */
    private var iTransaction: ITransaction? = null

    // ==================== AsyncServiceKernel 抽象方法实现 ====================

    override fun getServiceType(): String = "Local Service"

    override fun getExpectedProtocolType(): String = "local protocol"

    override fun isValidProtocolType(parseResult: ProtocolParseResult): Boolean {
        return parseResult is ProtocolParseResult.LocalProtocol
    }

    override fun performConnect(parseResult: ProtocolParseResult, connectionCallback: ConnectionCallback) {
        val localProtocol = parseResult as ProtocolParseResult.LocalProtocol
        val packageName = localProtocol.packageName
        val action = localProtocol.action

        // 本地服务连接逻辑
        LogUtil.d(TAG, "Connecting to local service: packageName=$packageName, action=$action")

        val context = TaplinkServiceKernel.getInstance()?.getContext()
        if (context == null) {
            notifyConnectionError("Application context is null", InnerErrorCode.C20)
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
            notifyConnectionError(
                "Service not found: packageName=$packageName, action=$action",
                InnerErrorCode.C22
            )
            return
        }

        // 保存当前Intent
        currentIntent = intent

        // 创建服务连接
        serviceConnection = createServiceConnection(connectionCallback)

        // 绑定服务
        try {
            val bindResult = context.bindService(
                intent,
                serviceConnection!!,
                Context.BIND_AUTO_CREATE
            )
            LogUtil.d(TAG, "Bind service result: $bindResult")

            if (!bindResult) {
                notifyConnectionError("Bind service result:$bindResult", InnerErrorCode.C25)
            }
            // 绑定成功会在ServiceConnection的onServiceConnected中处理
        } catch (e: Exception) {
            LogUtil.e(TAG, "Failed to bind service: ${e.message}")
            notifyConnectionError("Exception: ${e.message}", InnerErrorCode.C25)
        }
    }

    override fun performSendData(traceId: String, data: ByteArray, callback: InnerCallback?) {
        if (iTransaction == null) {
            LogUtil.e(TAG, "ITransaction is null, cannot send data")
            callback?.onError("ITRANSACTION_NULL", "ITransaction interface is null")
            return
        }

        // 将ByteArray转换为String（假设是UTF-8编码）
        val requestData = String(data, Charsets.UTF_8)

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
    }

    override fun performDisconnect() {
        val context = TaplinkServiceKernel.getInstance()?.getContext()

        // 解绑服务
        if (isServiceBound && serviceConnection != null && context != null) {
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
        iTransaction = null
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
                            notifyConnectionSuccess(iTransaction!!.getExtraInfos())
                        } else {
                            LogUtil.e(TAG, "Failed to convert binder to ITransaction")
                            notifyConnectionError(
                                "Failed to convert binder to ITransaction",
                                InnerErrorCode.C25
                            )
                        }
                    } catch (e: Exception) {
                        LogUtil.e(TAG, "Exception while converting binder to ITransaction: ${e.message}")
                        notifyConnectionError(
                            "Exception while converting binder: ${e.message}",
                            InnerErrorCode.C25
                        )
                    }
                } else {
                    // 服务绑定失败（binder为null）
                    notifyConnectionError("Service binder is null", InnerErrorCode.C25)
                }
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                LogUtil.d(TAG, "Service disconnected: $name")
                isServiceBound = false
                iTransaction = null

                // 服务异常断开
                updateStatus(ConnectionStatus.DISCONNECTED)
                notifyConnectionDisconnected(InnerErrorCode.C36.code, "Service disconnected unexpectedly")
            }

            override fun onBindingDied(name: ComponentName?) {
                LogUtil.d(TAG, "Service binding died: $name")
                isServiceBound = false
                iTransaction = null

                // 服务绑定死亡
                updateStatus(ConnectionStatus.ERROR)
                notifyConnectionDisconnected(InnerErrorCode.C36.code, "Service binding died")
            }

            override fun onNullBinding(name: ComponentName?) {
                LogUtil.d(TAG, "Service null binding: $name")
                isServiceBound = false

                // 服务返回null绑定
                notifyConnectionError("Service returned null binding", InnerErrorCode.C36)
            }
        }
    }
}

