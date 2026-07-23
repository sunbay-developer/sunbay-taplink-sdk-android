package com.sunmi.tapro.taplink.sdk.scanner

import android.content.Context
import android.content.Intent
import com.sunmi.tapro.taplink.sdk.callback.ConnectionListener
import com.sunmi.tapro.taplink.sdk.config.ConnectionConfig
import com.sunmi.tapro.taplink.sdk.error.ConnectionError
import com.sunmi.tapro.taplink.sdk.manager.ConnectionManager
import com.sunmi.tapro.taplink.sdk.model.DiscoveredService
import com.sunmi.tapro.taplink.communication.protocol.ProtocolParser
import com.sunmi.tapro.taplink.communication.protocol.ProtocolParseResult
import com.sunmi.tapro.taplink.communication.util.LogUtil
import java.lang.ref.WeakReference

/**
 * Coordinates QR scanner sessions, ensuring single-session semantics.
 *
 * Manages the lifecycle of [QrScannerActivity] and bridges scan results
 * to the SDK connection flow. Only one scan session is active at a time.
 */
object QrScanCoordinator {

    private const val TAG = "QrScanCoordinator"

    private var currentListener: ConnectionListener? = null
    private var connectAction: ((ConnectionConfig, ConnectionListener) -> Unit)? = null
    private var isSessionActive = false
    private var activityRef: WeakReference<QrScannerActivity>? = null

    // Scan-only mode (no connect): delivers the parsed host/port back to the caller.
    private var scanOnly = false
    private var scanOnlyOnResult: ((DiscoveredService) -> Unit)? = null
    private var scanOnlyOnError: ((ConnectionError) -> Unit)? = null

    /**
     * Start a new scan session.
     *
     * @param context Application context (used to start Activity with FLAG_ACTIVITY_NEW_TASK)
     * @param listener Connection listener for result callbacks
     * @param connectAction Lambda to execute SDK connect with parsed config
     * @return true if session started, false if already active
     */
    @Synchronized
    fun startSession(
        context: Context,
        listener: ConnectionListener,
        connectAction: (ConnectionConfig, ConnectionListener) -> Unit
    ): Boolean {
        if (isSessionActive) {
            LogUtil.w(TAG, "Scan session already active, rejecting new session")
            return false
        }

        isSessionActive = true
        currentListener = listener
        this.connectAction = connectAction

        try {
            val intent = Intent(context, QrScannerActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            LogUtil.d(TAG, "Scanner activity launched")
        } catch (e: Exception) {
            LogUtil.e(TAG, "Failed to launch scanner: ${e.message}")
            cleanupSession()
            listener.onError(
                ConnectionError(ConnectionManager.ERROR_SCAN_CANCELLED, "Failed to launch scanner: ${e.message}")
            )
            return false
        }

        return true
    }

    /**
     * Start a scan-only session that returns the parsed lan:// host/port WITHOUT
     * connecting. The caller decides what to do with the result (e.g. auto-fill the
     * address fields and then connect).
     *
     * @param context Application context (used to start Activity with FLAG_ACTIVITY_NEW_TASK)
     * @param onResult Callback with the parsed service (host/port)
     * @param onError Callback for cancel / permission denied / launch failure
     * @return true if session started, false if a session is already active
     */
    @Synchronized
    fun startScanOnlySession(
        context: Context,
        onResult: (DiscoveredService) -> Unit,
        onError: (ConnectionError) -> Unit
    ): Boolean {
        if (isSessionActive) {
            LogUtil.w(TAG, "Scan session already active, rejecting new session")
            return false
        }

        isSessionActive = true
        scanOnly = true
        scanOnlyOnResult = onResult
        scanOnlyOnError = onError

        try {
            val intent = Intent(context, QrScannerActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            LogUtil.d(TAG, "Scanner activity launched (scan-only)")
        } catch (e: Exception) {
            LogUtil.e(TAG, "Failed to launch scanner: ${e.message}")
            cleanupSession()
            onError(
                ConnectionError(ConnectionManager.ERROR_SCAN_CANCELLED, "Failed to launch scanner: ${e.message}")
            )
            return false
        }

        return true
    }

    /**
     * Called by [QrScannerActivity] when a QR code is scanned.
     *
     * @param qrData Raw QR code content
     * @return true if the QR data was valid and connection initiated; false to continue scanning
     */
    @Synchronized
    fun onScanResult(qrData: String): Boolean {
        LogUtil.d(TAG, "Scan result received: $qrData")

        if (scanOnly) {
            val onResult = scanOnlyOnResult ?: return false
            val parseResult = ProtocolParser.parse(qrData)
            return if (parseResult is ProtocolParseResult.LanProtocol) {
                LogUtil.d(TAG, "Valid LAN QR (scan-only): host=${parseResult.ip}, port=${parseResult.port}")
                val service = DiscoveredService("QR", parseResult.ip, parseResult.port)
                cleanupSession()
                onResult(service)
                true
            } else {
                LogUtil.w(TAG, "Invalid QR format: $qrData (parse result: $parseResult)")
                false
            }
        }

        val listener = currentListener ?: return false
        val action = connectAction ?: return false

        val parseResult = ProtocolParser.parse(qrData)
        if (parseResult is ProtocolParseResult.LanProtocol) {
            LogUtil.d(TAG, "Valid LAN QR: host=${parseResult.ip}, port=${parseResult.port}")

            val config = ConnectionConfig.createLanMode(parseResult.ip, parseResult.port)

            // Create a proxy listener that cleans up session state
            val proxyListener = object : ConnectionListener {
                override fun onConnected(deviceId: String, taproVersion: String) {
                    cleanupSession()
                    listener.onConnected(deviceId, taproVersion)
                }

                override fun onDisconnected(reason: String) {
                    cleanupSession()
                    listener.onDisconnected(reason)
                }

                override fun onError(error: ConnectionError) {
                    cleanupSession()
                    listener.onError(error)
                }

                override fun onReconnecting(attempt: Int, maxRetries: Int) {
                    listener.onReconnecting(attempt, maxRetries)
                }
            }

            action(config, proxyListener)
            return true
        } else {
            LogUtil.w(TAG, "Invalid QR format: $qrData (parse result: $parseResult)")
            return false
        }
    }

    /**
     * Called by [QrScannerActivity] when user cancels (back press).
     */
    @Synchronized
    fun onScanCancelled() {
        LogUtil.d(TAG, "Scan cancelled by user")
        if (scanOnly) {
            val onError = scanOnlyOnError
            cleanupSession()
            onError?.invoke(
                ConnectionError(ConnectionManager.ERROR_SCAN_CANCELLED, "QR scanning cancelled by user")
            )
            return
        }
        val listener = currentListener
        cleanupSession()
        listener?.onError(
            ConnectionError(ConnectionManager.ERROR_SCAN_CANCELLED, "QR scanning cancelled by user")
        )
    }

    /**
     * Called by [QrScannerActivity] when camera permission is denied.
     */
    @Synchronized
    fun onCameraPermissionDenied() {
        LogUtil.d(TAG, "Camera permission denied")
        if (scanOnly) {
            val onError = scanOnlyOnError
            cleanupSession()
            onError?.invoke(
                ConnectionError(ConnectionManager.ERROR_CAMERA_PERMISSION_DENIED, "Camera permission denied")
            )
            return
        }
        val listener = currentListener
        cleanupSession()
        listener?.onError(
            ConnectionError(ConnectionManager.ERROR_CAMERA_PERMISSION_DENIED, "Camera permission denied")
        )
    }

    /**
     * Called by [QrScannerActivity] when no usable camera can be resolved / the
     * camera fails to start.
     */
    @Synchronized
    fun onCameraUnavailable() {
        LogUtil.d(TAG, "Camera unavailable")
        if (scanOnly) {
            val onError = scanOnlyOnError
            cleanupSession()
            onError?.invoke(
                ConnectionError(ConnectionManager.ERROR_CAMERA_UNAVAILABLE, "No usable camera available on this device")
            )
            return
        }
        val listener = currentListener
        cleanupSession()
        listener?.onError(
            ConnectionError(ConnectionManager.ERROR_CAMERA_UNAVAILABLE, "No usable camera available on this device")
        )
    }

    /**
     * Cancel the current scan session (called by disconnect()).
     */
    @Synchronized
    fun cancelSession() {
        if (!isSessionActive) return
        LogUtil.d(TAG, "Cancelling scan session")

        activityRef?.get()?.finish()
        cleanupSession()
        // No callback on cancel from disconnect()
    }

    /**
     * Register the scanner activity reference for cancellation.
     */
    @Synchronized
    fun registerActivity(activity: QrScannerActivity) {
        activityRef = WeakReference(activity)
    }

    @Synchronized
    private fun cleanupSession() {
        isSessionActive = false
        currentListener = null
        connectAction = null
        activityRef = null
        scanOnly = false
        scanOnlyOnResult = null
        scanOnlyOnError = null
    }
}
