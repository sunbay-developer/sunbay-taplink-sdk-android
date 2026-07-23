package com.sunmi.tapro.taplink.sdk.api

import android.content.Context
import com.sunmi.tapro.taplink.sdk.callback.ConnectionListener
import com.sunmi.tapro.taplink.sdk.callback.DiscoveryListener
import com.sunmi.tapro.taplink.sdk.callback.PaymentCallback
import com.sunmi.tapro.taplink.sdk.config.ConnectionConfig
import com.sunmi.tapro.taplink.sdk.config.TaplinkConfig
import com.sunmi.tapro.taplink.sdk.model.request.PaymentRequest
import com.sunmi.tapro.taplink.sdk.model.request.QueryRequest
import com.sunmi.tapro.taplink.sdk.enums.ConnectionStatus

/**
 * Taplink SDK API interface
 *
 * Defines the complete public API contract for the Taplink SDK.
 *
 * Main features:
 * - Unified use of PaymentRequest to handle all transaction types (distinguished by action field)
 * - Supports both synchronous and asynchronous calling methods
 * - Uses ConnectionConfig to uniformly manage all connection modes
 * - Method signatures correspond to TaplinkSDK static methods in documentation
 *
 * @author TaPro Team
 * @since 2025-01-XX
 */
interface TaplinkApi {

    // ==================== Initialization ====================

    /**
     * Initialize SDK
     *
     * Initialize Taplink SDK, configure application information and connection parameters. Must be called before using other interfaces.
     *
     * @param context Application or Activity context
     * @param config SDK configuration object
     */
    fun init(context: Context, config: TaplinkConfig)

    /**
     * Whether the SDK has been initialized via [init].
     *
     * @return true if [init] has been called successfully
     */
    fun isInitialized(): Boolean

    /**
     * Returns the current connection status.
     *
     * More granular than [isConnected]: distinguishes CONNECTING, CONNECTED,
     * WAIT_CONNECTING, DISCONNECTED, and ERROR states.
     *
     * @return [ConnectionStatus] current status
     */
    fun getConnectionStatus(): ConnectionStatus

    // ==================== Connection Management ====================

    /**
     * Establish connection (unified connection method)
     *
     * Supports all connection modes:
     * - App-to-app mode: Use empty config, SDK auto-detects
     * - Cable mode: Use empty config for auto-detection, or specify protocol type
     * - LAN mode: Specify IP and port (first time), or use empty config (subsequent auto-connect)
     *
     * @param config Connection configuration object (can be null, uses default config)
     * @param listener Connection listener
     */
    fun connect(config: ConnectionConfig?, listener: ConnectionListener)

    /**
     * Discover LAN Taplink services via mDNS WITHOUT connecting.
     *
     * Runs a one-shot mDNS (Android NSD) discovery of _taplink._tcp services and
     * returns the resolved host/port list. The caller decides which service to connect
     * to (e.g. auto-fill the address and call [connect] with a LAN config).
     *
     * @param listener Discovery result callback:
     *   - onDiscovered: list of resolved services (may be empty)
     *   - onError: SDK not initialized / internal error
     *
     * Call disconnect() to cancel an in-progress discovery.
     */
    fun discoverLanServices(listener: DiscoveryListener)

    /**
     * Auto-discover LAN Taplink services and connect to the first available.
     *
     * Uses mDNS (Android NSD) to discover _taplink._tcp services on the local network,
     * then tries connecting to each in discovery order until one succeeds.
     *
     * @param listener Connection result callback:
     *   - onConnected: discovered and connected to a Taplink service
     *   - onError: no services found / all candidates failed / SDK not initialized
     *
     * Call disconnect() to cancel an in-progress discovery.
     */
    fun autoDiscoverAndConnect(listener: ConnectionListener)

    /**
     * Launch QR scanner and connect using scanned Taplink QR code.
     *
     * SDK opens its built-in camera scanner Activity and handles CAMERA permission.
     * Only lan://host/port format QR codes are accepted.
     *
     * @param listener Connection result callback:
     *   - onConnected: scanned valid QR and connected
     *   - onError: user cancelled / permission denied / SDK not initialized
     *
     * Call disconnect() to cancel an in-progress scan session.
     */
    fun scanAndConnect(listener: ConnectionListener)

    /**
     * Launch QR scanner and return the scanned lan:// host/port WITHOUT connecting.
     *
     * SDK opens its built-in camera scanner Activity and handles CAMERA permission.
     * Only lan://host/port format QR codes are accepted. The resolved host/port is
     * delivered via [DiscoveryListener] as a single-element list, so the caller can
     * auto-fill the address and call [connect] with a LAN config.
     *
     * @param listener Scan result callback:
     *   - onDiscovered: single-element list with the scanned service (host/port)
     *   - onError: user cancelled / permission denied / SDK not initialized
     *
     * Call disconnect() to cancel an in-progress scan session.
     */
    fun scanLanQrCode(listener: DiscoveryListener)

    /**
     * Disconnect
     *
     * Disconnect from payment terminal, release resources.
     */
    fun disconnect()

    /**
     * Check connection status
     *
     * @return true if connected, false otherwise
     */
    fun isConnected(): Boolean

    /**
     * Get connected device ID
     *
     * @return String Device ID, returns null if not connected
     */
    fun getConnectedDeviceId(): String?

    /**
     * Get connection mode
     *
     * @return ConnectionMode Current connection mode, returns null if not connected
     */
    fun getConnectionMode(): String?

    /**
     * Get Tapro version number
     *
     * Get Tapro version number of connected payment terminal.
     * Version number can only be obtained after successful connection.
     *
     * @return String Tapro version number, returns null if not connected or version not obtained
     */
    fun getTaproVersion(): String?

    // ==================== Transaction Execution ====================

    /**
     * Execute transaction asynchronously (recommended)
     *
     * Non-blocking call, returns immediately. Transaction results are passed through callback methods.
     * Recommended for cross-device scenarios and scenarios requiring progress feedback.
     *
     * @param request Payment request object
     * @param callback Payment callback interface
     */
    fun execute(request: PaymentRequest, callback: PaymentCallback)

    // ==================== Transaction Query ====================

    /**
     * Query transaction status asynchronously
     *
     * Query transaction status, especially suitable for timeout scenarios.
     * Can query by transaction ID or transaction request ID.
     *
     * @param request Query request object
     * @param callback Payment callback interface
     */
    fun query(request: QueryRequest, callback: PaymentCallback)

    // ==================== Listener Management ====================

    /**
     * Set connection status listener
     *
     * Set connection status listener to monitor connection status changes, reconnection events, etc. in real-time.
     *
     * @param listener Connection status listener, null means remove listener
     */
    fun setConnectionListener(listener: ConnectionListener?)

    /**
     * Remove connection status listener
     *
     * Remove previously set connection status listener.
     */
    fun removeConnectionListener()

    // ==================== Utility Methods ====================

    /**
     * Get SDK version
     *
     * @return String SDK version number, format: x.y.z
     */
    fun getVersion(): String

    /**
     * Clear cached device information
     *
     * Clear locally cached device connection information, need to re-pair on next connection.
     */
    fun clearDeviceCache()

    /**
     * Get connection information
     */
    fun getConnectionConfig(): ConnectionConfig?


    // ==================== Headless Mode APIs ====================

    /**
     * Cancel the current in-progress transaction.
     *
     * Only effective when the transaction is in a cancelable stage
     * (e.g., WaitingCard, WaitingPin). Once in OnlineProcessing stage,
     * cancellation is not allowed to avoid one-sided transactions.
     *
     * This method sends an ABORT request to Tapro.
     *
     * @param transactionRequestId The ID of the transaction to cancel (optional, for validation)
     * @param callback Callback to receive the cancellation result
     */
    fun cancelTransaction(transactionRequestId: String? = null, callback: PaymentCallback)

    /**
     * Query the current transaction status.
     *
     * Used for crash recovery — when the POS app restarts after a crash,
     * it can query whether a transaction is still in progress and get the current state.
     *
     * @param transactionRequestId The transaction request ID to query
     * @param callback Callback to receive the status query result
     */
    fun queryTransactionStatus(transactionRequestId: String, callback: PaymentCallback)

    /**
     * Switch current Headless transaction to manual card entry.
     *
     * This control action is only valid when the in-progress transaction
     * is currently at WAITING_CARD stage.
     *
     * @param transactionRequestId The transaction request ID to switch (optional, for validation)
     * @param callback Callback to receive switch result
     */
    fun switchToManualEntry(transactionRequestId: String? = null, callback: PaymentCallback)

    /**
     * Open TaPro USB secondary-screen player application.
     *
     * This control action is used by POS apps before showing Android [android.app.Presentation]
     * content on a secondary display. TaPro will handle launching:
     * `sunmi.intent.action.SunmiUsbScreenPlayer`.
     *
     * @param callback Callback to receive control result
     */
    fun openUsbScreenPlayer(callback: PaymentCallback)


}
