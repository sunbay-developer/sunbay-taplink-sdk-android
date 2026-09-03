package com.sunmi.tapro.taplink.sdk.impl

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.sunmi.tapro.taplink.sdk.BuildConfig
import com.sunmi.tapro.taplink.sdk.api.TaplinkApi
import com.sunmi.tapro.taplink.sdk.callback.ConnectionListener
import com.sunmi.tapro.taplink.sdk.callback.PaymentCallback
import com.sunmi.tapro.taplink.sdk.callback.TerminalInfoCallback
import com.sunmi.tapro.taplink.sdk.model.response.TerminalInfo
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
import com.sunmi.tapro.taplink.sdk.enums.ConnectionStatus
import com.sunmi.tapro.taplink.sdk.enums.ConnectionMode
import com.sunmi.tapro.taplink.sdk.error.PaymentError
import com.sunmi.tapro.taplink.sdk.error.ConnectionError
import com.sunmi.tapro.taplink.sdk.model.common.PaymentEvent
import com.sunmi.tapro.taplink.sdk.model.response.PaymentResult
import java.util.concurrent.atomic.AtomicBoolean

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

    internal companion object {
        /** Longest delay of `onConnected` while waiting for the screen player to open. */
        const val SCREEN_PLAYER_TIMEOUT_MS = 3_000L

        /**
         * Error code returned when TaPro does not support [openUsbScreenPlayer].
         * This replaces misleading error codes from legacy TaPro versions.
         */
        const val ERROR_CODE_USB_SCREEN_PLAYER_UNSUPPORTED = "350"

        /**
         * Error code returned when Sub_Screen mode fails to open the USB screen
         * player on the remote TaPro device. In this mode, the screen player is mandatory
         * — failure means the connection is aborted.
         */
        const val ERROR_CODE_SUB_SCREEN_OPEN_FAILED = "351"

        /**
         * Error code returned when TaPro does not support [getTerminalInfo] (GET_TERMINAL_INFO).
         * This replaces misleading error codes from legacy TaPro versions with a clear,
         * actionable "please upgrade TaPro" error.
         */
        const val ERROR_CODE_GET_TERMINAL_INFO_UNSUPPORTED = "352"

        /**
         * Minimum TaPro version that supports the OPEN_USB_SCREEN_PLAYER action.
         * Versions below this will be rejected locally without sending a request.
         */
        const val MIN_TAPRO_VERSION_FOR_USB_SCREEN_PLAYER = "1.0.5"

        /**
         * Error codes that indicate the legacy TaPro does not support the
         * OPEN_USB_SCREEN_PLAYER action (rather than a genuine transactional error).
         *
         * - 307: "System is processing another transaction" — TaPro was on a transaction
         *   page and rejected the unknown action.
         * - 305: "Transaction in progress" — similar busy state.
         * - 303: "Unsupported transaction type" — TaPro explicitly doesn't recognize the action.
         * - 309: "Transaction terminated" — TaPro terminated the unknown request.
         */
        val LEGACY_TAPRO_UNSUPPORTED_ERROR_CODES = setOf("307", "305", "303", "309")

        /**
         * Returns true if the error code suggests TaPro does not support
         * OPEN_USB_SCREEN_PLAYER (legacy version incompatibility).
         */
        fun isLegacyTaproUnsupportedError(code: String): Boolean {
            return code in LEGACY_TAPRO_UNSUPPORTED_ERROR_CODES
        }

        /**
         * Compare TaPro version against the minimum required for USB screen player.
         * Returns true if the version is >= [MIN_TAPRO_VERSION_FOR_USB_SCREEN_PLAYER].
         *
         * Uses semantic versioning comparison (major.minor.patch).
         * If version cannot be parsed, returns true (optimistic — let the request go through
         * and rely on the error-code fallback).
         */
        fun isTaproVersionSupported(version: String): Boolean {
            return try {
                compareVersions(version, MIN_TAPRO_VERSION_FOR_USB_SCREEN_PLAYER) >= 0
            } catch (e: Exception) {
                // Cannot parse version — optimistic fallback: allow the request and rely
                // on error-code interception if TaPro rejects it.
                true
            }
        }

        /**
         * Compare two semantic version strings (major.minor.patch).
         * Returns positive if a > b, negative if a < b, zero if equal.
         */
        private fun compareVersions(a: String, b: String): Int {
            val partsA = a.split(".").map { it.toIntOrNull() ?: 0 }
            val partsB = b.split(".").map { it.toIntOrNull() ?: 0 }
            val maxLen = maxOf(partsA.size, partsB.size)
            for (i in 0 until maxLen) {
                val partA = partsA.getOrElse(i) { 0 }
                val partB = partsB.getOrElse(i) { 0 }
                if (partA != partB) return partA - partB
            }
            return 0
        }
    }

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
                LogLevel.VERBOSE -> LogUtil.setLevel(Log.VERBOSE)
                LogLevel.DEBUG -> LogUtil.setLevel(Log.DEBUG)
                LogLevel.INFO -> LogUtil.setLevel(Log.INFO)
                LogLevel.WARN -> LogUtil.setLevel(Log.WARN)
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

        // Only Sub_Screen mode auto-opens the TaPro USB customer-facing screen player as part of
        // connecting (screen player failure = connection failure). All other modes connect without
        // touching the screen player; integrators that need it can call openUsbScreenPlayer()
        // explicitly after onConnected.
        val wrappedListener = if (config?.connectionMode == ConnectionMode.SUB_SCREEN) {
            wrapWithSubScreen(listener)
        } else {
            listener
        }

        connectionMgr.connect(config, wrappedListener)
    }

    override fun discoverLanServices(listener: com.sunmi.tapro.taplink.sdk.callback.DiscoveryListener) {
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
        connectionMgr.discoverLanServices(listener)
    }

    override fun autoDiscoverAndConnect(listener: ConnectionListener) {
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
        connectionMgr.autoDiscoverAndConnect(listener)
    }

    override fun scanAndConnect(listener: ConnectionListener) {
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
        connectionMgr.scanAndConnect(listener)
    }

    /**
     * Wrap [listener] for Sub_Screen mode.
     *
     * After the VSP transport is established (onConnected from ConnectionManager),
     * this wrapper sends an OPEN_USB_SCREEN_PLAYER request to the remote TaPro.
     * Unlike [wrapWithUsbScreenPlayer], failure here is fatal:
     * - Success → deliver onConnected to the integrator.
     * - Failure or timeout → disconnect and deliver onError with code [ERROR_CODE_SUB_SCREEN_OPEN_FAILED].
     *
     * This ensures the integrator receives onConnected only when both the VSP link
     * AND the sub-screen are ready for use.
     */
    private fun wrapWithSubScreen(listener: ConnectionListener): ConnectionListener {
        return object : ConnectionListener by listener {
            override fun onConnected(deviceId: String, taproVersion: String) {
                val delivered = AtomicBoolean(false)

                fun deliverSuccess(source: String) {
                    if (delivered.compareAndSet(false, true)) {
                        LogUtil.d(TAG, "Sub_Screen ready ($source), delivering onConnected")
                        listener.onConnected(deviceId, taproVersion)
                    }
                }

                fun deliverFailure(source: String, detail: String) {
                    if (delivered.compareAndSet(false, true)) {
                        LogUtil.e(TAG, "Sub_Screen open failed ($source): $detail — disconnecting")
                        disconnect()
                        listener.onError(
                            ConnectionError(
                                code = ERROR_CODE_SUB_SCREEN_OPEN_FAILED,
                                message = "Failed to open sub-screen: $detail",
                                suggestion = "Ensure the remote TaPro device supports USB screen player " +
                                    "(TaPro $MIN_TAPRO_VERSION_FOR_USB_SCREEN_PLAYER+) and that a " +
                                    "customer-facing display is connected to it."
                            )
                        )
                    }
                }

                // Timeout guard: if TaPro doesn't respond within the allowed window, fail.
                Handler(Looper.getMainLooper()).postDelayed(
                    { deliverFailure("timeout", "TaPro did not respond within ${SCREEN_PLAYER_TIMEOUT_MS}ms") },
                    SCREEN_PLAYER_TIMEOUT_MS
                )

                openUsbScreenPlayer(object : PaymentCallback {
                    override fun onSuccess(result: PaymentResult) {
                        deliverSuccess("screen_player_success")
                    }

                    override fun onFailure(error: PaymentError) {
                        deliverFailure("screen_player_failure", "${error.code}: ${error.message}")
                    }

                    override fun onProgress(event: PaymentEvent) = Unit
                })
            }
        }
    }

    override fun scanLanQrCode(listener: com.sunmi.tapro.taplink.sdk.callback.DiscoveryListener) {        val connectionMgr = connectionManager
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
        connectionMgr.scanLanQrCode(listener)
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
                PaymentError.create(
                    code = errorCode.code,
                    message = errorCode.description,
                    suggestion = ErrorStringHelper.getSolution(errorCode.code) ?: "",
                    transactionRequestId = request.transactionRequestId
                )
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

    override fun isInitialized(): Boolean = connectionManager != null

    override fun getConnectionStatus(): ConnectionStatus {
        return connectionManager?.getConnectionStatus() ?: ConnectionStatus.DISCONNECTED
    }

    override fun getVersion(): String {
        return config?.version ?: BuildConfig.VERSION_NAME
    }

    override fun clearDeviceCache() {
        LogUtil.d(TAG, "Clearing device cache")
        connectionManager?.clearDeviceCache()
    }

    override fun getConnectionConfig(): ConnectionConfig? {
        return connectionManager?.getAutoConnectConfig()
    }



    // ==================== Headless Mode APIs ====================

    override fun cancelTransaction(transactionRequestId: String?, callback: PaymentCallback) {
        LogUtil.d(TAG, "cancelTransaction: transactionRequestId=$transactionRequestId")

        if (!isConnected()) {
            LogUtil.e(TAG, "cancelTransaction: Not connected")
            callback.onFailure(
                PaymentError.create(
                    code = InnerErrorCode.E303.code,
                    message = "Not connected to payment terminal",
                    suggestion = ErrorStringHelper.getSolution(InnerErrorCode.E303.code) ?: ""
                )
            )
            return
        }

        // 发送 ABORT 请求到 Tapro（复用现有的 ABORT action 机制）
        val abortRequest = PaymentRequest(
            action = com.sunmi.tapro.taplink.sdk.enums.TransactionAction.ABORT.name,
            transactionRequestId = transactionRequestId
        )
        paymentManager?.execute(abortRequest, callback)
    }

    override fun queryTransactionStatus(transactionRequestId: String, callback: PaymentCallback) {
        LogUtil.d(TAG, "queryTransactionStatus: transactionRequestId=$transactionRequestId")

        if (!isConnected()) {
            LogUtil.e(TAG, "queryTransactionStatus: Not connected")
            callback.onFailure(
                PaymentError.create(
                    code = InnerErrorCode.E303.code,
                    message = "Not connected to payment terminal",
                    suggestion = ErrorStringHelper.getSolution(InnerErrorCode.E303.code) ?: ""
                )
            )
            return
        }

        // 使用 QUERY action 查询交易状态
        val queryRequest = QueryRequest.byTransactionRequestId(transactionRequestId)
        paymentManager?.query(queryRequest, callback)
    }

    override fun switchToManualEntry(transactionRequestId: String?, callback: PaymentCallback) {
        LogUtil.d(TAG, "switchToManualEntry: transactionRequestId=$transactionRequestId")

        if (!isConnected()) {
            LogUtil.e(TAG, "switchToManualEntry: Not connected")
            callback.onFailure(
                PaymentError.create(
                    code = InnerErrorCode.E303.code,
                    message = "Not connected to payment terminal",
                    suggestion = ErrorStringHelper.getSolution(InnerErrorCode.E303.code) ?: ""
                )
            )
            return
        }

        // 发送 SWITCH_TO_MANUAL_ENTRY 控制请求到 Tapro。
        // 请求会由 Tapro 按当前在途交易状态（必须 WAITING_CARD）做最终门控。
        val switchRequest = PaymentRequest(
            action = com.sunmi.tapro.taplink.sdk.enums.TransactionAction.SWITCH_TO_MANUAL_ENTRY.name,
            originalTransactionRequestId = transactionRequestId
        )
        paymentManager?.execute(switchRequest, callback)
    }

    override fun openUsbScreenPlayer(callback: PaymentCallback) {
        LogUtil.d(TAG, "openUsbScreenPlayer requested")

        if (!isConnected()) {
            LogUtil.e(TAG, "openUsbScreenPlayer: Not connected")
            callback.onFailure(
                PaymentError.create(
                    code = InnerErrorCode.E303.code,
                    message = "Not connected to payment terminal",
                    suggestion = ErrorStringHelper.getSolution(InnerErrorCode.E303.code) ?: ""
                )
            )
            return
        }

        // If TaPro version is already known and below the minimum that supports this action,
        // skip sending the request entirely to avoid unnecessary error responses and log noise.
        val knownVersion = connectionManager?.getTaproVersion()
        if (knownVersion != null && knownVersion != "unknown" && !isTaproVersionSupported(knownVersion)) {
            LogUtil.d(TAG, "openUsbScreenPlayer: TaPro $knownVersion does not support this action, skipping")
            callback.onFailure(
                PaymentError.create(
                    code = ERROR_CODE_USB_SCREEN_PLAYER_UNSUPPORTED,
                    message = "TaPro $knownVersion does not support USB screen player control " +
                        "(requires TaPro $MIN_TAPRO_VERSION_FOR_USB_SCREEN_PLAYER+)",
                    suggestion = "This is non-fatal. The secondary display can still " +
                        "be launched locally via Intent if the device supports it."
                )
            )
            return
        }

        // Send OPEN_USB_SCREEN_PLAYER control request to TaPro.
        // The generated id must stay within the 32-character transactionRequestId limit,
        // so it uses a short prefix instead of the (22-character) action name.
        val request = PaymentRequest(
            action = com.sunmi.tapro.taplink.sdk.enums.TransactionAction.OPEN_USB_SCREEN_PLAYER.name,
            transactionRequestId = "USP_${System.currentTimeMillis()}"
        )

        // Wrap callback to handle legacy TaPro versions that do not recognize this action.
        // Older TaPro treats OPEN_USB_SCREEN_PLAYER as an unknown transaction request and
        // returns misleading errors (E307 "System is processing another transaction",
        // E305 "Transaction in progress", or E303 "Unsupported transaction type").
        // We intercept these and convert them to a clear "unsupported" error so integrators
        // are not confused by false "transaction in progress" reports.
        val wrappedCallback = object : PaymentCallback {
            override fun onSuccess(result: PaymentResult) = callback.onSuccess(result)
            override fun onProgress(event: PaymentEvent) = callback.onProgress(event)
            override fun onFailure(error: PaymentError) {
                if (isLegacyTaproUnsupportedError(error.code)) {
                    LogUtil.w(
                        TAG,
                        "openUsbScreenPlayer: TaPro does not support this action " +
                            "(received ${error.code}: ${error.message}). " +
                            "This is expected on older TaPro versions."
                    )
                    callback.onFailure(
                        PaymentError.create(
                            code = ERROR_CODE_USB_SCREEN_PLAYER_UNSUPPORTED,
                            message = "TaPro does not support USB screen player control " +
                                "(openUsbScreenPlayer requires TaPro $MIN_TAPRO_VERSION_FOR_USB_SCREEN_PLAYER+)",
                            suggestion = "This is non-fatal. The secondary display can still " +
                                "be launched locally via Intent if the device supports it."
                        )
                    )
                } else {
                    callback.onFailure(error)
                }
            }
        }

        paymentManager?.execute(request, wrappedCallback)
    }

    override fun getTerminalInfo(callback: TerminalInfoCallback) {
        LogUtil.d(TAG, "getTerminalInfo requested")

        val paymentMgr = paymentManager
        if (paymentMgr == null) {
            val errorCode = InnerErrorCode.E201
            LogUtil.e(TAG, "getTerminalInfo: SDK not initialized")
            callback.onFailure(
                PaymentError.create(
                    code = errorCode.code,
                    message = errorCode.description,
                    suggestion = ErrorStringHelper.getSolution(errorCode.code) ?: ""
                )
            )
            return
        }

        // Wrap callback to handle legacy TaPro versions that do not recognize the GET_TERMINAL_INFO
        // action. Older TaPro treats GET_TERMINAL_INFO as an unknown transaction request and returns
        // misleading errors (E307 "System is processing another transaction", E305
        // "Transaction in progress", or E303 "Unsupported transaction type"). We intercept
        // these and convert them to a clear "please upgrade TaPro" error so integrators are
        // not confused by false "transaction in progress" reports.
        val wrappedCallback = object : TerminalInfoCallback {
            override fun onSuccess(info: TerminalInfo) = callback.onSuccess(info)
            override fun onFailure(error: PaymentError) {
                if (isLegacyTaproUnsupportedError(error.code)) {
                    LogUtil.w(
                        TAG,
                        "getTerminalInfo: TaPro does not support GET_TERMINAL_INFO " +
                            "(received ${error.code}: ${error.message}). " +
                            "This is expected on older TaPro versions."
                    )
                    callback.onFailure(
                        PaymentError.create(
                            code = ERROR_CODE_GET_TERMINAL_INFO_UNSUPPORTED,
                            message = "TaPro does not support GET_TERMINAL_INFO, please upgrade TaPro",
                            suggestion = "Upgrade TaPro to a version that supports the GET_TERMINAL_INFO action."
                        )
                    )
                } else {
                    callback.onFailure(error)
                }
            }
        }

        paymentMgr.getTerminalInfo(wrappedCallback)
    }
}
