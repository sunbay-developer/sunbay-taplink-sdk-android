package com.sunmi.tapro.taplink.communication

import android.content.Context
import com.sunmi.tapro.taplink.communication.enums.ConnectionStatus
import com.sunmi.tapro.taplink.communication.enums.InnerErrorCode
import com.sunmi.tapro.taplink.communication.interfaces.ConnectionCallback
import com.sunmi.tapro.taplink.communication.interfaces.IServiceKernel
import com.sunmi.tapro.taplink.communication.interfaces.InnerCallback
import com.sunmi.tapro.taplink.communication.lan.WebSocketClientKernel
import com.sunmi.tapro.taplink.communication.local.kernel.LocalServiceKernel
import com.sunmi.tapro.taplink.communication.serial.SerialServiceKernel
import com.sunmi.tapro.taplink.communication.usb.UsbServiceKernel
import com.sunmi.tapro.taplink.communication.util.LogUtil
import com.sunmi.tapro.taplink.communication.util.ProtocolParser
import com.sunmi.tapro.taplink.communication.util.ProtocolParseResult

/**
 * Taplink Service Kernel Class
 *
 * Provides unified service interface for WebSocket, USB, Serial, and Local communication
 * Uses singleton pattern to ensure global unique instance
 *
 * @author TaPro Team
 * @since 2025-01-XX
 */
class TaplinkServiceKernel private constructor(context: Context) {
    private val TAG = "TaplinkServiceKernel"

    /**
     * Application context (using applicationContext to avoid memory leaks)
     */
    private val mContext: Context = context.applicationContext

    /**
     * Currently used service kernel
     * Note: Only one serviceKernel can be in connected state at a time
     */
    private var currentServiceKernel: IServiceKernel? = null

    init {
        LogUtil.d(TAG, "TaplinkServiceKernel initialized")
    }

    /**
     * Connect to service
     *
     * Note: Service kernels cannot be in connected state simultaneously. If the serviceKernel to connect is different from the already connected one,
     * you need to disconnect the old connection first, then connect to the new one.
     *
     * @param protocol Protocol string, supporting multiple protocol formats
     * @param appid Application ID
     * @param appSecretKey Application secret key
     * @param connectionCallback Connection callback
     */
    fun connect(protocol: String, appid: String, appSecretKey: String, connectionCallback: ConnectionCallback) {
        if (!isInitialized()) {
            connectionCallback.onDisconnected(
                InnerErrorCode.C20.code,
                InnerErrorCode.C20.description
            )
            return
        }

        // Parse protocol to determine which service to use
        val parseResult = ProtocolParser.parse(protocol)
        if (parseResult is ProtocolParseResult.Error) {
            connectionCallback.onDisconnected(InnerErrorCode.C41.code, parseResult.message)
            return
        }

        // Dynamically get corresponding service kernel based on protocol type
        val serviceKernel = getServiceKernelByProtocol(parseResult, appid, appSecretKey)
        if (serviceKernel == null) {
            connectionCallback.onDisconnected(
                InnerErrorCode.C41.code,
                "Unsupported protocol type: ${parseResult::class.simpleName}"
            )
            return
        }

        // Check if there is a currently connected service kernel
        val oldServiceKernel = currentServiceKernel
        if (oldServiceKernel != null && oldServiceKernel !== serviceKernel) {
            // If current service kernel is different from the one to connect, and current service is connected, disconnect the old one first
            val oldStatus = oldServiceKernel.getConnectionStatus()
            if (oldStatus != ConnectionStatus.DISCONNECTED) {
                LogUtil.d(TAG, "Disconnecting old service kernel before connecting new one. Old status: $oldStatus")
                try {
                    oldServiceKernel.disconnect()
                } catch (e: Exception) {
                    LogUtil.e(TAG, "Error disconnecting old service kernel: ${e.message}")
                }
            }
            // Clear old serviceKernel reference (because we're switching to a new type)
            currentServiceKernel = null
        } else if (oldServiceKernel === serviceKernel) {
            // If it's the same instance, check if we need to disconnect first
            val oldStatus = oldServiceKernel.getConnectionStatus()
            if (oldStatus != ConnectionStatus.DISCONNECTED) {
                LogUtil.d(TAG, "Disconnecting current service kernel before reconnecting. Status: $oldStatus")
                try {
                    oldServiceKernel.disconnect()
                } catch (e: Exception) {
                    LogUtil.e(TAG, "Error disconnecting current service kernel: ${e.message}")
                }
            }
        }

        // Save current service kernel
        currentServiceKernel = serviceKernel

        // Call connection method of corresponding service
        serviceKernel.connect(protocol, connectionCallback)
    }

    /**
     * Get corresponding service kernel by protocol type
     *
     * If current serviceKernel is of the same type and disconnected, reuse it; otherwise create new instance.
     *
     * @param parseResult Protocol parse result
     * @param appId Application ID
     * @param appSecretKey Application secret key
     * @return IServiceKernel? Corresponding service kernel, returns null if not supported
     */
    private fun getServiceKernelByProtocol(
        parseResult: ProtocolParseResult,
        appId: String,
        appSecretKey: String
    ): IServiceKernel? {
        val serviceKey = when (parseResult) {
            is ProtocolParseResult.LanProtocol -> SERVICE_KEY_LAN

            is ProtocolParseResult.UsbProtocol -> SERVICE_KEY_USB
            is ProtocolParseResult.SerialProtocol,
            is ProtocolParseResult.VspProtocol -> SERVICE_KEY_SERIAL

            is ProtocolParseResult.LocalProtocol -> SERVICE_KEY_LOCAL
            else -> return null
        }

        // Check if current serviceKernel is of the same type
        val current = currentServiceKernel
        if (current != null && isServiceKernelOfType(current, serviceKey)) {
            // If same type and disconnected, can be reused
            val status = current.getConnectionStatus()
            if (status == ConnectionStatus.DISCONNECTED) {
                LogUtil.d(TAG, "Reusing existing service kernel of type: $serviceKey")
                return current
            }
        }

        // Create new service kernel instance
        return createServiceKernel(serviceKey, appId, appSecretKey)
    }

    /**
     * Check if serviceKernel is of specified type
     *
     * @param kernel Service kernel instance
     * @param serviceKey Service type identifier
     * @return Boolean Whether it is the specified type
     */
    private fun isServiceKernelOfType(kernel: IServiceKernel, serviceKey: String): Boolean {
        return when (serviceKey) {
            SERVICE_KEY_LAN -> kernel is WebSocketClientKernel
            SERVICE_KEY_USB -> kernel is UsbServiceKernel
            SERVICE_KEY_SERIAL -> kernel is SerialServiceKernel
            SERVICE_KEY_LOCAL -> kernel is LocalServiceKernel
            else -> false
        }
    }

    /**
     * Create service kernel instance
     *
     * @param serviceKey Service type identifier
     * @param appId Application ID
     * @param appSecretKey Application secret key
     * @return IServiceKernel Service kernel instance
     */
    private fun createServiceKernel(
        serviceKey: String, appId: String,
        appSecretKey: String
    ): IServiceKernel {
        return when (serviceKey) {
            SERVICE_KEY_LAN -> {
                LogUtil.d(TAG, "Creating LAN service")
                WebSocketClientKernel(appId, appSecretKey, mContext)
            }

            SERVICE_KEY_USB -> {
                LogUtil.d(TAG, "Creating USB service")
                UsbServiceKernel(mContext)
            }

            SERVICE_KEY_SERIAL -> {
                LogUtil.d(TAG, "Creating Serial service")
                SerialServiceKernel(mContext)
            }

            SERVICE_KEY_LOCAL -> {
                LogUtil.d(TAG, "Creating Local service")
                LocalServiceKernel(appId, appSecretKey)
            }

            else -> {
                throw IllegalArgumentException("Unknown service key: $serviceKey")
            }
        }
    }

    /**
     * Get currently used service kernel
     *
     * @return IServiceKernel? Current service kernel, may be null
     */
    fun getCurrentServiceKernel(): IServiceKernel? {
        return currentServiceKernel
    }

    /**
     * Send data
     *
     * Send data through currently connected service kernel
     *
     * @param traceId Trace ID
     * @param data Data to send
     * @param callback Send result callback, can be null
     */
    fun sendData(traceId: String, data: ByteArray, callback: InnerCallback?) {
        val kernel = currentServiceKernel
        if (kernel == null) {
            LogUtil.e(TAG, "No service kernel available, cannot send data")
            callback?.onError("NO_SERVICE_KERNEL", "No service kernel available")
            return
        }

        try {
            kernel.sendData(traceId, data, callback)
        } catch (e: Exception) {
            LogUtil.e(TAG, "Failed to send data: traceId=$traceId, error=${e.message}")
            callback?.onError("SEND_ERROR", e.message ?: "Unknown error")
        }
    }

    /**
     * Get application context
     *
     * @return Context Application context
     */
    fun getContext(): Context {
        return mContext
    }

    /**
     * Check if service is initialized
     *
     * @return Boolean Whether initialized
     */
    fun isInitialized(): Boolean {
        return mContext != null
    }

    /**
     * Release resources
     */
    fun release() {
        // Disconnect current service connection
        currentServiceKernel?.let { kernel ->
            try {
                kernel.disconnect()
            } catch (e: Exception) {
                LogUtil.e(TAG, "Error disconnecting service: ${e.message}")
            }
        }

        // Clear current service kernel
        currentServiceKernel = null

        LogUtil.d(TAG, "TaplinkServiceKernel released")
    }

    companion object {
        // Service type identifier constants
        private const val SERVICE_KEY_LAN = "lan"
        private const val SERVICE_KEY_USB = "usb"
        private const val SERVICE_KEY_SERIAL = "serial"
        private const val SERVICE_KEY_LOCAL = "local"

        @Volatile
        private var instance: TaplinkServiceKernel? = null

        /**
         * Get singleton instance
         *
         * @param context Application context, must be provided for first call
         * @return TaplinkServiceKernel Singleton instance
         */
        @JvmStatic
        fun getInstance(context: Context): TaplinkServiceKernel {
            return instance ?: synchronized(this) {
                instance ?: TaplinkServiceKernel(context).also {
                    instance = it
                }
            }
        }

        /**
         * Get singleton instance (if initialized)
         *
         * @return TaplinkServiceKernel? Singleton instance, returns null if not initialized
         */
        @JvmStatic
        fun getInstance(): TaplinkServiceKernel? {
            return instance
        }

        /**
         * Destroy singleton instance (for testing or special scenarios)
         */
        @JvmStatic
        fun destroyInstance() {
            synchronized(this) {
                instance?.release()
                instance = null
            }
        }
    }
}
