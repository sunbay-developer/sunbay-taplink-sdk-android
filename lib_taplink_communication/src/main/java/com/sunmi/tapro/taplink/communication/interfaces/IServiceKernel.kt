package com.sunmi.tapro.taplink.communication.interfaces

import com.sunmi.tapro.taplink.communication.enums.InnerConnectionStatus

/**
 * Service kernel interface
 * 
 * Defines the basic functionality that all service kernels must implement
 * 
 * @author TaPro Team
 * @since 2025-01-01
 */
interface IServiceKernel {
    
    /**
     * Connect to service
     * 
     * @param protocol Protocol string, format varies by service type
     * @param connectionCallback Connection callback
     */
    fun connect(protocol: String, connectionCallback: ConnectionCallback)
    
    /**
     * Send data
     * 
     * @param traceId Trace ID
     * @param data Data to send
     * @param callback Send result callback, can be null
     */
    fun sendData(traceId: String, data: ByteArray, callback: InnerCallback?)
    
    
    /**
     * Register data receiver
     * 
     * @param receiver Data receive callback
     */
    fun registerDataReceiver(receiver: (dataArray: ByteArray?) -> Unit)
    
    /**
     * Receive data
     * 
     * When the server actively pushes data, call this method to pass the data to registered data receivers
     * 
     * @param data Received data
     */
    fun receiveData(data: ByteArray)
    
    /**
     * Disconnect
     */
    fun disconnect()

    /**
     * Actively probe whether the underlying link is still alive.
     *
     * Unlike [getConnectionStatus], which only reports the last known state, this performs a
     * best-effort round-trip check so callers can fail fast when the peer has become
     * unresponsive (e.g. a stuck UART port) instead of waiting for a transaction timeout.
     *
     * The default implementation is passive (reports the cached CONNECTED state). Transports that
     * support a lightweight application-level ping (e.g. VSP handshake) override this with an
     * active probe.
     *
     * @param timeoutMs Maximum time to wait for the peer to acknowledge the probe.
     * @return `true` if the link is confirmed alive, `false` otherwise.
     */
    suspend fun checkLinkAlive(timeoutMs: Long): Boolean
    
    /**
     * Get current connection status
     * 
     * @return ConnectionStatus Current connection status
     */
    fun getConnectionStatus(): InnerConnectionStatus
    
    /**
     * Set status change listener
     * 
     * @param listener Status change listener
     */
    fun setStatusChangeListener(listener: (InnerConnectionStatus) -> Unit)
}