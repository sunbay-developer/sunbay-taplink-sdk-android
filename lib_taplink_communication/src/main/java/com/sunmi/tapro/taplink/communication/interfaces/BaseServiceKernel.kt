package com.sunmi.tapro.taplink.communication.interfaces

import com.sunmi.tapro.taplink.communication.enums.ConnectionStatus
import com.sunmi.tapro.taplink.communication.util.LogUtil

/**
 * Abstract Base Service Kernel Class
 *
 * Provides unified status management functionality to reduce code duplication
 *
 * @author TaPro Team
 * @since 2025-01-XX
 */
abstract class BaseServiceKernel : IServiceKernel {
    /**
     * Current connection status
     */
    @Volatile
    protected var currentConnectionStatus: ConnectionStatus = ConnectionStatus.DISCONNECTED

    /**
     * Status change listener
     */
    private var statusChangeListener: ((ConnectionStatus) -> Unit)? = null

    /**
     * Data receiver
     * Used to receive data pushed from server
     */
    protected var dataReceiver: ((ByteArray?) -> Unit)? = null

    /**
     * Get current connection status
     *
     * @return ConnectionStatus Current connection status
     */
    override fun getConnectionStatus(): ConnectionStatus {
        return currentConnectionStatus
    }

    /**
     * Set status change listener
     *
     * @param listener Status change listener
     */
    override fun setStatusChangeListener(listener: (ConnectionStatus) -> Unit) {
        statusChangeListener = listener
    }

    /**
     * Update connection status
     *
     * @param newStatus New status
     */
    protected fun updateStatus(newStatus: ConnectionStatus) {
        if (currentConnectionStatus != newStatus) {
            val oldStatus = currentConnectionStatus
            currentConnectionStatus = newStatus
            logStatusChange(oldStatus, newStatus)
            statusChangeListener?.invoke(newStatus)
        }
    }

    /**
     * Log status change
     *
     * @param oldStatus Old status
     * @param newStatus New status
     */
    protected open fun logStatusChange(oldStatus: ConnectionStatus, newStatus: ConnectionStatus) {
        LogUtil.d(getTag(), "Status changed: $oldStatus -> $newStatus")
    }

    /**
     * Get log tag
     *
     * Defaults to class name
     *
     * @return String Log tag
     */
    protected open fun getTag(): String = this::class.simpleName ?: "BaseServiceKernel"

    /**
     * Check if data can be sent
     *
     * @return Boolean Whether data can be sent
     */
    protected fun canSendData(): Boolean {
        return currentConnectionStatus == ConnectionStatus.CONNECTED
    }

    /**
     * Check if connection is allowed
     *
     * @return Boolean Whether connection is allowed
     */
    protected fun canConnect(): Boolean {
        return currentConnectionStatus == ConnectionStatus.DISCONNECTED ||
                currentConnectionStatus == ConnectionStatus.ERROR
    }

    /**
     * Handle connection error
     *
     * @param errorMessage Error message
     * @param connectionCallback Connection callback
     * @param errorCode Error code, default is -1
     */
    protected fun handleConnectionError(
        errorMessage: String,
        connectionCallback: ConnectionCallback,
        errorCode: String ="-1"
    ) {
        updateStatus(ConnectionStatus.ERROR)
        connectionCallback.onDisconnected(errorCode, errorMessage)
    }

    /**
     * Handle connection success
     *
     * @param connectionCallback Connection callback
     * @param extraInfoMap Extra information map, can be null
     */
    protected fun handleConnectionSuccess(
        connectionCallback: ConnectionCallback,
        extraInfoMap: Map<String, String?>? = null
    ) {
        updateStatus(ConnectionStatus.CONNECTED)
        connectionCallback.onConnected(extraInfoMap)
    }

    /**
     * Implement IServiceKernel interface: Register data receiver
     *
     * @param receiver Data receive callback
     */
    override fun registerDataReceiver(receiver: (dataArray: ByteArray?) -> Unit) {
        dataReceiver = receiver
        LogUtil.d(getTag(), "Data receiver registered")
    }

    /**
     * Implement IServiceKernel interface: Receive data
     *
     * Pass received data to registered data receiver
     *
     * @param data Received data
     */
    override fun receiveData(data: ByteArray) {
        if (dataReceiver != null) {
            LogUtil.d(getTag(), "Receiving data: ${data.size} bytes")
            dataReceiver?.invoke(data)
        } else {
            LogUtil.d(getTag(), "Data receiver not registered, ignoring received data: ${data.size} bytes")
        }
    }
}


