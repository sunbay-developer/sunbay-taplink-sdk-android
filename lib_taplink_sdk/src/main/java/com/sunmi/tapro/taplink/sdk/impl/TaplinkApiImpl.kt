package com.sunmi.tapro.taplink.sdk.impl

import android.content.Context
import android.util.Log
import com.sunmi.tapro.taplink.sdk.BuildConfig
import com.sunmi.tapro.taplink.sdk.api.TaplinkApi
import com.sunmi.tapro.taplink.sdk.callback.ConnectionListener
import com.sunmi.tapro.taplink.sdk.callback.PaymentCallback
import com.sunmi.tapro.taplink.sdk.config.ConnectionConfig
import com.sunmi.tapro.taplink.sdk.config.TaplinkConfig
import com.sunmi.tapro.taplink.sdk.enums.LogLevel
import com.sunmi.tapro.taplink.sdk.model.request.PaymentRequest
import com.sunmi.tapro.taplink.sdk.model.request.QueryRequest
import com.sunmi.tapro.taplink.sdk.manager.ConnectionManager
import com.sunmi.tapro.taplink.sdk.manager.PaymentManager
import com.sunmi.tapro.taplink.communication.TaplinkServiceKernel
import com.sunmi.tapro.taplink.communication.enums.InnerErrorCode
import com.sunmi.tapro.taplink.communication.util.ErrorStringHelper
import com.sunmi.tapro.taplink.communication.util.LogUtil
import com.sunmi.tapro.taplink.sdk.callback.onFailure
import com.sunmi.tapro.taplink.sdk.error.PaymentError

/**
 * Taplink API implementation class
 *
 * Main entry point for Taplink SDK operations.
 * Delegates specific responsibilities to specialized manager classes:
 * - ConnectionManager: Handles connection operations
 * - PaymentManager: Handles payment and query operations
 * - ResponseProcessor: Handles response processing
 *
 * @author TaPro Team
 * @since 2025-01-XX
 */
class TaplinkApiImpl : TaplinkApi {

    private val TAG = "TaplinkApiImpl"

    /**
     * SDK configuration
     */
    private var config: TaplinkConfig? = null

    /**
     * Connection manager
     */
    private var connectionManager: ConnectionManager? = null

    /**
     * Payment manager
     */
    private var paymentManager: PaymentManager? = null

    /**
     * Response processor
     */
    private var responseProcessor: ResponseProcessor? = null

    init {
        LogUtil.d(TAG, "TaplinkApiImpl initialized")
    }

    // ==================== Initialization ====================

    override fun init(context: Context, config: TaplinkConfig) {
        LogUtil.d(TAG, "Initializing SDK with appId: ${config.appId}")
        this.config = config

        // Initialize specialized managers
        connectionManager = ConnectionManager(config, context)
        responseProcessor = ResponseProcessor()
        paymentManager = PaymentManager(config, connectionManager!!, responseProcessor!!, context)

        // Set payment manager reference in connection manager for connection loss notifications
        connectionManager?.setPaymentManager(paymentManager!!)

        // Initialize ServiceKernel
        TaplinkServiceKernel.getInstance(context)

        // Configure log level
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

    // ==================== Connection Management ====================

    override fun connect(config: ConnectionConfig?, listener: ConnectionListener) {
        val connectionMgr = connectionManager
        if (connectionMgr == null) {
            LogUtil.e(TAG, "SDK not initialized, please call init() first")
            listener.onError(
                com.sunmi.tapro.taplink.sdk.error.ConnectionError(
                    "T01",
                    "SDK not initialized, please call init() first"
                )
            )
            return
        }

        connectionMgr.connect(config, listener)
    }

    override fun disconnect() {
        LogUtil.d(TAG, "Manual disconnect")
        connectionManager?.disconnect()

        // Clear all pending payment callbacks
        paymentManager?.clearAllCallbacks()
    }

    override fun isConnected(): Boolean {
        return connectionManager?.isConnected() ?: false
    }

    override fun getConnectedDeviceId(): String? {
        return connectionManager?.getConnectedDeviceId()
    }

    override fun getConnectionMode(): String? {
        return connectionManager?.getConnectionMode()
    }

    override fun getTaproVersion(): String? {
        return connectionManager?.getTaproVersion()
    }

    // ==================== Payment Operations ====================

    override fun execute(request: PaymentRequest, callback: PaymentCallback) {
        val paymentMgr = paymentManager
        if (paymentMgr == null) {
            LogUtil.e(TAG, "SDK not initialized, please call init() first")
            val errorCode = InnerErrorCode.E201
            callback.onFailure(
                errorCode = errorCode,
                transactionRequestId = request.transactionRequestId
            )
            return
        }

        paymentMgr.execute(request, callback)
    }

    override fun query(request: QueryRequest, callback: PaymentCallback) {
        val paymentMgr = paymentManager
        if (paymentMgr == null) {
            LogUtil.e(TAG, "SDK not initialized, please call init() first")
            val errorCode = InnerErrorCode.E201
            callback.onFailure(
                PaymentError.create(
                    code = errorCode.code,
                    message = errorCode.description,
                    suggestion = ErrorStringHelper.getSolution(errorCode.code) ?: "",
                    transactionRequestId = request.transactionRequestId
                )
            )
            return
        }

        paymentMgr.query(request, callback)
    }

    // ==================== Listener Management ====================

    override fun setConnectionListener(listener: ConnectionListener?) {
        connectionManager?.setConnectionListener(listener)
    }

    override fun removeConnectionListener() {
        connectionManager?.removeConnectionListener()
    }

    // ==================== Utility Methods ====================

    override fun getVersion(): String {
        return config?.version ?: BuildConfig.VERSION_NAME
    }

    override fun clearDeviceCache() {
        LogUtil.d(TAG, "Clearing device cache")
        // Clear device cache through connection manager
        // This could be implemented in ConnectionManager if needed
        // TODO: Implement device cache clearing logic
    }

    override fun getConnectionConfig(): ConnectionConfig? {
        return connectionManager?.getAutoConnectConfig()
    }
}
