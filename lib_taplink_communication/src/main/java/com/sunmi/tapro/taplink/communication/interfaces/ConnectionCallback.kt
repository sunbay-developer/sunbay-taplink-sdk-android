package com.sunmi.tapro.taplink.communication.interfaces

/**
 * Callback interface for service connection lifecycle events.
 *
 * Implement this interface and pass it to [IServiceKernel.connect] to receive
 * connection state changes.
 *
 * @author TaPro Team
 * @since 2025-01-01
 */
interface ConnectionCallback {

    /**
     * Called when the connection is fully established and the service is ready.
     *
     * @param extraInfoMap Optional map of extra metadata returned by the service
     *   (e.g. device info, firmware version). May be null if not provided.
     */
    fun onConnected(extraInfoMap: Map<String, String?>?)

    /**
     * Called when the connection attempt is accepted and the SDK is waiting for
     * the remote side to complete the handshake (e.g. service binding, WebSocket upgrade).
     *
     * This is an intermediate state between calling [IServiceKernel.connect] and
     * receiving [onConnected] or [onDisconnected].
     */
    fun onWaitingConnect()

    /**
     * Called when the connection is lost or terminated, whether deliberately via
     * [IServiceKernel.disconnect] or due to an error.
     *
     * @param code   Error/status code. See [InnerErrorCode] for possible values.
     * @param msg    Human-readable description of the disconnection reason.
     */
    fun onDisconnected(code: String, msg: String)
}
