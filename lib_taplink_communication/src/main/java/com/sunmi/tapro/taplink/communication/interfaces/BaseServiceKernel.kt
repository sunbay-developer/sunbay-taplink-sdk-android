package com.sunmi.tapro.taplink.communication.interfaces

import com.sunmi.tapro.taplink.communication.enums.InnerConnectionStatus
import com.sunmi.tapro.taplink.communication.util.LogUtil

/**
 * Abstract base class for service kernel
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
    var currentInnerConnectionStatus: InnerConnectionStatus = InnerConnectionStatus.DISCONNECTED

    /**
     * Status change listener
     */
    private var statusChangeListener: ((InnerConnectionStatus) -> Unit)? = null

    /**
     * Data receiver
     * Used to receive data pushed from the server
     */
    protected var dataReceiver: ((ByteArray?) -> Unit)? = null

    /**
     * Get current connection status
     *
     * @return ConnectionStatus Current connection status
     */
    override fun getConnectionStatus(): InnerConnectionStatus {
        return currentInnerConnectionStatus
    }

    /**
     * Set status change listener
     *
     * @param listener Status change listener
     */
    override fun setStatusChangeListener(listener: (InnerConnectionStatus) -> Unit) {
        statusChangeListener = listener
    }

    /**
     * Update connection status
     *
     * @param newStatus New status
     */
    protected fun updateStatus(newStatus: InnerConnectionStatus) {
        if (currentInnerConnectionStatus != newStatus) {
            val oldStatus = currentInnerConnectionStatus
            currentInnerConnectionStatus = newStatus
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
    protected open fun logStatusChange(oldStatus: InnerConnectionStatus, newStatus: InnerConnectionStatus) {
        LogUtil.d(getTag(), "Status changed: $oldStatus -> $newStatus")
    }

    /**
     * Get log tag
     *
     * Default uses class name
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
        return currentInnerConnectionStatus == InnerConnectionStatus.CONNECTED
    }

    /**
     * Check if connection can be established
     *
     * @return Boolean Whether connection can be established
     */
    protected fun canConnect(): Boolean {
        return currentInnerConnectionStatus == InnerConnectionStatus.DISCONNECTED ||
                currentInnerConnectionStatus == InnerConnectionStatus.ERROR
    }

    /**
     * Handle connection error
     *
     * @param errorMessage Error message
     * @param connectionCallback Connection callback
     * @param errorCode Error code, defaults to "-1"
     */
    protected fun handleConnectionError(
        errorMessage: String,
        connectionCallback: ConnectionCallback,
        errorCode: String ="-1"
    ) {
        updateStatus(InnerConnectionStatus.ERROR)
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
        updateStatus(InnerConnectionStatus.CONNECTED)
        connectionCallback.onConnected(extraInfoMap)
    }

    /**
     * Implement IServiceKernel interface: register data receiver
     *
     * @param receiver Data receive callback
     */
    override fun registerDataReceiver(receiver: (dataArray: ByteArray?) -> Unit) {
        dataReceiver = receiver
        LogUtil.d(getTag(), "Data receiver registered")
    }

    /**
     * Implement IServiceKernel interface: receive data
     *
     * Pass received data to the registered data receiver
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


