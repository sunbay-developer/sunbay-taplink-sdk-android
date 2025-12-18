package com.sunmi.tapro.taplink.communication.interfaces

import com.sunmi.tapro.taplink.communication.enums.ConnectionStatus

/**
 * Service Kernel Interface
 * 
 * Defines basic functions that all service kernels must implement
 * 
 * @author TaPro Team
 * @since 2025-01-XX
 */
interface IServiceKernel {
    
    /**
     * Connect to service
     * 
     * @param protocol Protocol string, different formats for different service types
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
     * When server actively pushes data, call this method to pass data to registered data receivers
     * 
     * @param data Received data
     */
    fun receiveData(data: ByteArray)
    
    /**
     * Disconnect
     */
    fun disconnect()
    
    /**
     * Get current connection status
     * 
     * @return ConnectionStatus Current connection status
     */
    fun getConnectionStatus(): ConnectionStatus
    
    /**
     * Set status change listener
     * 
     * @param listener Status change listener
     */
    fun setStatusChangeListener(listener: (ConnectionStatus) -> Unit)
}