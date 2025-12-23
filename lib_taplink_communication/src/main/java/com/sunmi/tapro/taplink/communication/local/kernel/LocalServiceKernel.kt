package com.sunmi.tapro.taplink.communication.local.kernel

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import com.sunmi.tapro.taplink.communication.TaplinkServiceKernel
import com.sunmi.tapro.taplink.communication.enums.InnerConnectionStatus
import com.sunmi.tapro.taplink.communication.interfaces.AsyncServiceKernel
import com.sunmi.tapro.taplink.communication.interfaces.ConnectionCallback
import com.sunmi.tapro.taplink.communication.interfaces.InnerCallback
import com.sunmi.tapro.taplink.communication.local.ITransaction
import com.sunmi.tapro.taplink.communication.local.ITransactionCallback
import com.google.gson.Gson
import com.sunmi.tapro.taplink.communication.enums.InnerErrorCode
import com.sunmi.tapro.taplink.communication.util.LogUtil
import com.sunmi.tapro.taplink.communication.protocol.ProtocolParseResult

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
     * 服务是否被启动（用于 Android 14+ 兼容性）
     */
    private var isServiceStarted: Boolean = false

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

        LogUtil.d(TAG, "=== APP TO APP Service Binding Debug ===")
        LogUtil.d(TAG, "Target package: $packageName")
        LogUtil.d(TAG, "Target action: $action")
        LogUtil.d(TAG, "AppId: $appId")
        LogUtil.d(TAG, "SecretKey length: ${appSecretKey.length}")

        val context = TaplinkServiceKernel.getInstance()?.getContext()
        if (context == null) {
            LogUtil.e(TAG, "Application context is null")
            notifyConnectionError("Application context is null", InnerErrorCode.C20)
            return
        }

        // 创建Intent
        val intent = Intent(action)
        intent.setPackage(packageName)
        intent.putExtra("app_id", appId)
        intent.putExtra("secret_key", appSecretKey)

        LogUtil.d(TAG, "Created intent: $intent")
        LogUtil.d(TAG, "Intent extras: app_id=$appId, secret_key=***")

        // 检查目标应用是否安装
        try {
            val packageInfo = context.packageManager.getPackageInfo(packageName, 0)
            LogUtil.d(TAG, "Target app installed: version=${packageInfo.versionName}, versionCode=${packageInfo.versionCode}")
        } catch (e: Exception) {
            LogUtil.e(TAG, "Target app not installed: ${e.message}")
            notifyConnectionError("Target app not installed: $packageName", InnerErrorCode.C22)
            return
        }

        // 检查服务是否存在
        val resolveInfoList = context.packageManager.queryIntentServices(intent, 0)
        LogUtil.d(TAG, "Query intent services result: ${resolveInfoList.size}")

        resolveInfoList.forEachIndexed { index, resolveInfo ->
            LogUtil.d(TAG, "Service $index: ${resolveInfo.serviceInfo.name}")
            LogUtil.d(TAG, "  - Package: ${resolveInfo.serviceInfo.packageName}")
            LogUtil.d(TAG, "  - Exported: ${resolveInfo.serviceInfo.exported}")
            LogUtil.d(TAG, "  - Enabled: ${resolveInfo.serviceInfo.enabled}")
        }

        val matchService = resolveInfoList.find {
            it.serviceInfo.packageName == packageName
        }

        if (matchService == null) {
            LogUtil.e(TAG, "No matching service found for package: $packageName, action: $action")
            notifyConnectionError(
                "Service not found: packageName=$packageName, action=$action",
                InnerErrorCode.C22
            )
            return
        }

        LogUtil.d(TAG, "Found matching service: ${matchService.serviceInfo.name}")

        // 检查目标进程状态
        try {
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            val runningProcesses = activityManager.runningAppProcesses ?: emptyList()
            val targetProcess = runningProcesses.find { it.processName == packageName }
            if (targetProcess != null) {
                LogUtil.d(TAG, "Target process is running: pid=${targetProcess.pid}, importance=${targetProcess.importance}")
            } else {
                LogUtil.d(TAG, "Target process is not running (will be started by bindService)")
            }
        } catch (e: Exception) {
            LogUtil.w(TAG, "Failed to check process status: ${e.message}")
        }

        // 保存当前Intent
        currentIntent = intent

        // 创建服务连接
        serviceConnection = createServiceConnection(connectionCallback)

        // Android 14+ 兼容性：先启动服务，再绑定
        // 在 Android 14 中，如果服务没有运行，仅使用 bindService 可能无法启动服务
        try {
            LogUtil.d(TAG, "=== Starting Service Binding Process ===")

            // 使用推荐的标志组合
            val bindFlags = Context.BIND_AUTO_CREATE or
                    Context.BIND_IMPORTANT or
                    Context.BIND_ABOVE_CLIENT
            LogUtil.d(TAG, "Binding with flags: 0x${bindFlags.toString(16)}")
            LogUtil.d(TAG, "  - BIND_AUTO_CREATE: ${(bindFlags and Context.BIND_AUTO_CREATE) != 0}")
            LogUtil.d(TAG, "  - BIND_IMPORTANT: ${(bindFlags and Context.BIND_IMPORTANT) != 0}")
            LogUtil.d(TAG, "  - BIND_ABOVE_CLIENT: ${(bindFlags and Context.BIND_ABOVE_CLIENT) != 0}")

            // 然后绑定服务
            val bindResult = context.bindService(
                intent,
                serviceConnection!!,
                bindFlags
            )
            LogUtil.d(TAG, "Bind service result: $bindResult")

            if (!bindResult) {
                LogUtil.e(TAG, "bindService returned false - binding failed immediately")
                notifyConnectionError("Bind service result:$bindResult", InnerErrorCode.C25)
            } else {
                LogUtil.d(TAG, "bindService returned true - waiting for ServiceConnection callbacks...")
                LogUtil.d(TAG, "Expected callbacks:")
                LogUtil.d(TAG, "  - onServiceConnected: if binding succeeds")
                LogUtil.d(TAG, "  - onBindingDied: if target process crashes")
                LogUtil.d(TAG, "  - onNullBinding: if service returns null binder")
            }
            // 绑定成功会在ServiceConnection的onServiceConnected中处理
        } catch (e: Exception) {
            LogUtil.e(TAG, "Exception during bindService: ${e.message}")
            LogUtil.e(TAG, "Exception type: ${e.javaClass.simpleName}")
            e.printStackTrace()
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

//                callback?.onResponse(response)
//                // 通过 dataReceiver 传递响应（供 TaplinkApiImpl 使用）
//                // 这样可以通过 processReceivedResponse 统一管理回调的调用和移除
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

//                callback?.onError(errorCode, errorResponse)

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

        // 如果服务是通过 startService 启动的，需要停止服务
        if (isServiceStarted && currentIntent != null && context != null) {
            try {
                val stopResult = context.stopService(currentIntent)
                LogUtil.d(TAG, "Service stopped: $stopResult")
            } catch (e: Exception) {
                LogUtil.e(TAG, "Failed to stop service: ${e.message}")
            }
            isServiceStarted = false
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
                LogUtil.d(TAG, "=== ServiceConnection.onServiceConnected ===")
                LogUtil.d(TAG, "Service connected: $name")
                LogUtil.d(TAG, "Binder: $binder")
                isServiceBound = true

                if (binder != null) {
                    try {
                        LogUtil.d(TAG, "Converting binder to ITransaction interface...")
                        // 将binder转换为ITransaction接口
                        iTransaction = ITransaction.Stub.asInterface(binder)
                        if (iTransaction != null) {
                            LogUtil.d(TAG, "ITransaction interface obtained successfully")

                            try {
                                LogUtil.d(TAG, "Calling iTransaction.getExtraInfos()...")
                                val extraInfos = iTransaction!!.getExtraInfos()
                                LogUtil.d(TAG, "Extra infos received: $extraInfos")

                                LogUtil.d(TAG, "=== Calling notifyConnectionSuccess ===")
                                // 服务绑定成功
                                notifyConnectionSuccess(extraInfos)
                                LogUtil.d(TAG, "=== notifyConnectionSuccess completed ===")
                            } catch (e: Exception) {
                                LogUtil.e(TAG, "Failed to get extra infos: ${e.message}")
                                e.printStackTrace()
                                // 即使获取额外信息失败，也认为连接成功
                                notifyConnectionSuccess(mutableMapOf())
                            }
                        } else {
                            LogUtil.e(TAG, "Failed to convert binder to ITransaction - interface is null")
                            notifyConnectionError(
                                "Failed to convert binder to ITransaction",
                                InnerErrorCode.C25
                            )
                        }
                    } catch (e: Exception) {
                        LogUtil.e(TAG, "Exception while converting binder to ITransaction: ${e.message}")
                        e.printStackTrace()
                        notifyConnectionError(
                            "Exception while converting binder: ${e.message}",
                            InnerErrorCode.C25
                        )
                    }
                } else {
                    LogUtil.e(TAG, "Service binder is null")
                    // 服务绑定失败（binder为null）
                    notifyConnectionError("Service binder is null", InnerErrorCode.C25)
                }
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                LogUtil.d(TAG, "=== ServiceConnection.onServiceDisconnected ===")
                LogUtil.d(TAG, "Service disconnected: $name")
                LogUtil.d(TAG, "This usually means the service process crashed or was killed")
                isServiceBound = false
                iTransaction = null

                // 服务异常断开
                updateStatus(InnerConnectionStatus.DISCONNECTED)
                notifyConnectionDisconnected(InnerErrorCode.C36.code, "Service disconnected unexpectedly")
            }

            override fun onBindingDied(name: ComponentName?) {
                LogUtil.d(TAG, "=== ServiceConnection.onBindingDied ===")
                LogUtil.d(TAG, "Service binding died: $name")
                LogUtil.d(TAG, "This means the target process crashed during or after binding")
                LogUtil.d(TAG, "Common causes:")
                LogUtil.d(TAG, "  1. Target service crashed in onCreate() or onBind()")
                LogUtil.d(TAG, "  2. Target process was killed by system")
                LogUtil.d(TAG, "  3. AIDL interface mismatch")
                LogUtil.d(TAG, "  4. Target service returned null from onBind()")
                isServiceBound = false
                iTransaction = null

                // 服务绑定死亡
                updateStatus(InnerConnectionStatus.ERROR)
                notifyConnectionDisconnected(InnerErrorCode.C36.code, "Service binding died")
            }

            override fun onNullBinding(name: ComponentName?) {
                LogUtil.d(TAG, "=== ServiceConnection.onNullBinding ===")
                LogUtil.d(TAG, "Service null binding: $name")
                LogUtil.d(TAG, "This means the service's onBind() method returned null")
                isServiceBound = false
                iTransaction = null

                // 服务返回null绑定
                updateStatus(InnerConnectionStatus.ERROR)
                notifyConnectionError("Service returned null binding", InnerErrorCode.C36)
            }
        }
    }
}

