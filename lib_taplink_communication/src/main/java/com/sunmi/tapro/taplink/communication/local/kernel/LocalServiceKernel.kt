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
 * Local service kernel class
 *
 * Provides local communication functionality, implements IServiceKernel interface
 * Supports starting and binding services via action and packageName
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
     * Gson instance for JSON parsing
     */
    private val gson = Gson()

    /**
     * Currently connected service Intent
     */
    private var currentIntent: Intent? = null

    /**
     * Service connection object
     */
    private var serviceConnection: ServiceConnection? = null

    /**
     * Service binding status
     */
    private var isServiceBound: Boolean = false

    /**
     * Whether service was started (for Android 14+ compatibility)
     */
    private var isServiceStarted: Boolean = false

    /**
     * ITransaction interface instance
     */
    private var iTransaction: ITransaction? = null

    // ==================== AsyncServiceKernel Abstract Method Implementation ====================

    override fun getServiceType(): String = "Local Service"

    override fun getExpectedProtocolType(): String = "local protocol"

    override fun isValidProtocolType(parseResult: ProtocolParseResult): Boolean {
        return parseResult is ProtocolParseResult.LocalProtocol
    }

    override fun performConnect(parseResult: ProtocolParseResult, connectionCallback: ConnectionCallback) {
        // Cancel old connection task to avoid concurrent connections
        connectJob?.cancel()
        connectJob = launchInScope {
            val localProtocol = parseResult as ProtocolParseResult.LocalProtocol
            val packageName = localProtocol.packageName
            val action = localProtocol.action

            LogUtil.d(TAG, "Connecting to package: $packageName, action: $action")

            val context = TaplinkServiceKernel.getInstance()?.getContext()
            if (context == null) {
                LogUtil.e(TAG, "Application context is null")
                notifyConnectionError("Application context is null", InnerErrorCode.E201)
                return@launchInScope
            }

            // Create Intent
            val intent = Intent(action)
            intent.setPackage(packageName)
            intent.putExtra("app_id", appId)
            intent.putExtra("secret_key", appSecretKey)


            // Check if target application is installed
            try {
                context.packageManager.getPackageInfo(packageName, 0)
            } catch (e: Exception) {
                LogUtil.e(TAG, "Target app not installed: ${e.message}")
                notifyConnectionError("packageName=$packageName", InnerErrorCode.E231)
                return@launchInScope
            }

            // Check if service exists
            val resolveInfoList = context.packageManager.queryIntentServices(intent, 0)

            val matchService = resolveInfoList.find {
                it.serviceInfo.packageName == packageName
            }

            if (matchService == null) {
                LogUtil.e(TAG, "No matching service found for package: $packageName, action: $action")
                notifyConnectionError(
                    "Service not found packageName=$packageName, action=$action",
                    InnerErrorCode.E231
                )
                return@launchInScope
            }


            // Save current Intent
            currentIntent = intent

            // Create service connection
            serviceConnection = createServiceConnection(connectionCallback)

            // Android 14+ compatibility: start service first, then bind
            try {
                val bindFlags = Context.BIND_AUTO_CREATE or
                        Context.BIND_IMPORTANT or
                        Context.BIND_ABOVE_CLIENT

                val bindResult = context.bindService(intent, serviceConnection!!, bindFlags)
                if (!bindResult) {
                    LogUtil.e(TAG, "bindService failed")
                    notifyConnectionError("Bind service result false", InnerErrorCode.E232)
                }
            } catch (e: Exception) {
                LogUtil.e(TAG, "Exception during bindService: ${e.message}")
                notifyConnectionError("${e.message}", InnerErrorCode.E232)
            }
        }
    }

    override suspend fun performSendData(traceId: String, data: ByteArray, callback: InnerCallback?) {
        if (iTransaction == null) {
            LogUtil.e(TAG, "ITransaction is null, cannot send data")
            callback?.onError(InnerErrorCode.E304.code, InnerErrorCode.E304.description)
            return
        }

        // Convert ByteArray to String (assume UTF-8 encoding)
        val requestData = String(data, Charsets.UTF_8)

        // Create callback object
        // Note: Don't directly call the passed callback, instead pass response through dataReceiver
        // This allows unified callback invocation and removal management in TaplinkApiImpl
        val transactionCallback = object : ITransactionCallback.Stub() {
            override fun callback(responseData: String?) {
                val response = responseData ?: "SUCCESS"
                LogUtil.d(TAG, "==========LocalSrviceKernel:$response")

//                callback?.onResponse(response)
//                // Pass response through dataReceiver (for TaplinkApiImpl use)
//                // This allows unified callback invocation and removal management through processReceivedResponse
                dataReceiver?.invoke(response.toByteArray(Charsets.UTF_8))
            }

            override fun onError(code: String?, msg: String?) {
                // Build error response JSON containing traceId so TaplinkApiImpl can match callback
                val errorCode = code ?: "UNKNOWN_ERROR"
                val errorMsg = msg ?: "Unknown error"
                val errorResponse = try {
                    """{"traceId":"$traceId","eventCode":"ERROR","errorCode":"$errorCode","eventMsg":"$errorMsg"}"""
                } catch (e: Exception) {
                    "ERROR: $errorCode - $errorMsg"
                }

//                callback?.onError(errorCode, errorResponse)

                // Error information also passed through dataReceiver
                dataReceiver?.invoke(errorResponse.toByteArray(Charsets.UTF_8))
            }
        }

        // Call ITransaction's onTransaction method (asynchronous)
        iTransaction?.onTransaction(requestData, transactionCallback)
    }

    override fun performDisconnect() {
        val context = TaplinkServiceKernel.getInstance()?.getContext()
        // Unbind service
        if (isServiceBound && serviceConnection != null && context != null) {
            try {
                context.unbindService(serviceConnection!!)
            } catch (e: Exception) {
                LogUtil.e(TAG, "Failed to unbind service: ${e.message}")
            }
            isServiceBound = false
        }

        // If service was started via startService, need to stop service
        if (isServiceStarted && currentIntent != null && context != null) {
            try {
                context.stopService(currentIntent)
            } catch (e: Exception) {
                LogUtil.e(TAG, "Failed to stop service: ${e.message}")
            }
            isServiceStarted = false
        }

        // Clean up resources
        serviceConnection = null
        currentIntent = null
        iTransaction = null
    }

    /**
     * Create service connection object
     *
     * @param connectionCallback Connection callback
     * @return ServiceConnection Service connection object
     */
    private fun createServiceConnection(connectionCallback: ConnectionCallback): ServiceConnection {
        return object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                LogUtil.d(TAG, "Service connected: $name")
                isServiceBound = true

                if (binder != null) {
                    try {
                        iTransaction = ITransaction.Stub.asInterface(binder)
                        if (iTransaction != null) {
                            try {
                                val extraInfos = iTransaction!!.getExtraInfos()
                                notifyConnectionSuccess(extraInfos)
                            } catch (e: Exception) {
                                LogUtil.e(TAG, "Failed to get extra infos: ${e.message}")
                                notifyConnectionSuccess(mutableMapOf())
                            }
                        } else {
                            LogUtil.e(TAG, "Failed to convert binder to ITransaction")
                            notifyConnectionError("Failed to convert binder to ITransaction", InnerErrorCode.E232)
                        }
                    } catch (e: Exception) {
                        LogUtil.e(TAG, "Exception while converting binder: ${e.message}")
                        notifyConnectionError("Exception while converting binder: ${e.message}", InnerErrorCode.E232)
                    }
                } else {
                    LogUtil.e(TAG, "Service binder is null")
                    notifyConnectionError("Service binder is null", InnerErrorCode.E232)
                }
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                LogUtil.w(TAG, "Service disconnected: $name (process crashed)")
                safeUnbind()
                isServiceBound = false
                iTransaction = null

                updateStatus(InnerConnectionStatus.DISCONNECTED)
                val packageName = name?.packageName ?: "unknown"
                val errorMessage = "Target application crashed: $packageName. " +
                        "The service disconnected unexpectedly, which usually means the service process crashed or was killed."
                notifyConnectionDisconnected(InnerErrorCode.E213.code, errorMessage)
            }

            override fun onBindingDied(name: ComponentName?) {
                LogUtil.w(TAG, "Service binding died: $name (target process crashed)")
                safeUnbind()
                isServiceBound = false
                iTransaction = null

                updateStatus(InnerConnectionStatus.ERROR)
                val packageName = name?.packageName ?: "unknown"
                val errorMessage = "Target application crashed: $packageName. " +
                        "The service binding died, which usually means the target process crashed during or after binding. " +
                        "Common causes: service crashed in onCreate()/onBind(), process killed by system, or AIDL interface mismatch."
                notifyConnectionDisconnected(InnerErrorCode.E213.code, errorMessage)
            }

            override fun onNullBinding(name: ComponentName?) {
                LogUtil.e(TAG, "Service null binding: $name")
                isServiceBound = false
                iTransaction = null

                updateStatus(InnerConnectionStatus.ERROR)
                notifyConnectionError("Service returned null binding", InnerErrorCode.E213)
            }
        }
    }

    private fun safeUnbind() {
        try {
            TaplinkServiceKernel.getInstance()?.getContext()?.unbindService(serviceConnection!!)
        } catch (_: IllegalArgumentException) {
            // already unbound / not registered
        } catch (_: Exception) {
        }
    }
}

