package com.sunmi.tapro.taplink.communication

import android.content.Context
import com.sunmi.tapro.taplink.communication.cable.aoa.host.CableAoaHostKernel
import com.sunmi.tapro.taplink.communication.cable.serial.SerialServiceKernel
import com.sunmi.tapro.taplink.communication.enums.InnerConnectionStatus
import com.sunmi.tapro.taplink.communication.enums.InnerErrorCode
import com.sunmi.tapro.taplink.communication.interfaces.ConnectionCallback
import com.sunmi.tapro.taplink.communication.interfaces.IServiceKernel
import com.sunmi.tapro.taplink.communication.interfaces.InnerCallback
import com.sunmi.tapro.taplink.communication.lan.LanClientKernel
import com.sunmi.tapro.taplink.communication.local.kernel.LocalServiceKernel
import com.sunmi.tapro.taplink.communication.cable.vsp.VSPClientKernel
import com.sunmi.tapro.taplink.communication.util.ErrorStringHelper
import com.sunmi.tapro.taplink.communication.util.LogUtil
import com.sunmi.tapro.taplink.communication.protocol.ProtocolManager
import com.sunmi.tapro.taplink.communication.protocol.ProtocolParseResult

/**
 * Taplink service kernel class
 *
 * Provides unified service interface for WebSocket, USB, Serial, Local communication
 * Uses singleton pattern to ensure globally unique instance
 *
 * @author TaPro Team
 * @since 2025-01-01
 */
class TaplinkServiceKernel private constructor(context: Context) {
    private val TAG = "TaplinkServiceKernel"

    /**
     * Application context (use applicationContext to avoid memory leaks)
     */
    private val mContext: Context = context.applicationContext

    /**
     * Currently used service kernel
     * Note: Only one serviceKernel can be in connected state at a time
     */
    private var currentServiceKernel: IServiceKernel? = null

    init {
        ErrorStringHelper.init(context)
        LogUtil.d(TAG, "TaplinkServiceKernel initialized")
    }

    /**
     * Connect service
     *
     * Note: serviceKernels cannot be in connected state simultaneously. If the serviceKernel to connect
     * is different from the already connected one, need to disconnect the old connection first, then connect the new one.
     *
     * @param protocol Protocol string, supports multiple protocol formats
     * @param appid Application ID
     * @param appSecretKey Application secret key
     * @param taproAppWidthRatio Width ratio for TaPro application (0.0 to 1.0, optional)
     * @param connectionCallback Connection callback
     */
    fun connect(protocol: String, appid: String, appSecretKey: String, taproAppWidthRatio: Float? = null, connectionCallback: ConnectionCallback) {
        if (!isInitialized()) {
            connectionCallback.onDisconnected(
                InnerErrorCode.E201.code,
                InnerErrorCode.E201.description
            )
            return
        }

        // Parse protocol to determine which service to use
        val parseResult = ProtocolManager.parseProtocol(protocol)
        if (parseResult is ProtocolParseResult.Error) {
            connectionCallback.onDisconnected(InnerErrorCode.E214.code, parseResult.message)
            return
        }

        // Dynamically get corresponding service kernel based on protocol type
        val serviceKernel = getServiceKernelByProtocol(parseResult, appid, appSecretKey, taproAppWidthRatio)
        if (serviceKernel == null) {
            connectionCallback.onDisconnected(
                InnerErrorCode.E214.code,
                "${InnerErrorCode.E214.description}: ${parseResult::class.simpleName}"
            )
            return
        }

        // Check if there is currently a connected service kernel
        val oldServiceKernel = currentServiceKernel
        if (oldServiceKernel != null && oldServiceKernel !== serviceKernel) {
            // If currently connected service kernel is different from the one to connect, disconnect old one first
            // Note: Even if status is DISCONNECTED, still call disconnect() to ensure resource cleanup
            // (e.g., running coroutines, service discovery, etc.)
            val oldStatus = oldServiceKernel.getConnectionStatus()
            LogUtil.d(TAG, "Disconnecting old service kernel before connecting new one. Old status: $oldStatus")
            try {
                oldServiceKernel.disconnect()
            } catch (e: Exception) {
                LogUtil.e(TAG, "Error disconnecting old service kernel: ${e.message}")
            }
            // Clear old serviceKernel reference (because switching to new type)
            currentServiceKernel = null
        } else if (oldServiceKernel === serviceKernel) {
            // If it's the same instance, check if need to disconnect first
            val oldStatus = oldServiceKernel.getConnectionStatus()
            if (oldStatus != InnerConnectionStatus.DISCONNECTED) {
                LogUtil.d(TAG, "Disconnecting current service kernel before reconnecting. Status: $oldStatus")
                try {
                    oldServiceKernel.disconnect()
                } catch (e: Exception) {
                    LogUtil.e(TAG, "Error disconnecting current service kernel: ${e.message}")
                }
            }
        }

        // Save currently used service kernel
        currentServiceKernel = serviceKernel

        // Call connection method of corresponding service
        serviceKernel.connect(protocol, connectionCallback)
    }

    /**
     * Get corresponding service kernel based on protocol type
     *
     * If current serviceKernel is of the same type and disconnected, reuse it; otherwise create new instance.
     *
     * @param parseResult Protocol parse result
     * @param appId Application ID
     * @param appSecretKey Application secret key
     * @param taproAppWidthRatio Width ratio for TaPro application (0.0 to 1.0, optional)
     * @return IServiceKernel? Corresponding service kernel, returns null if not supported
     */
    private fun getServiceKernelByProtocol(
        parseResult: ProtocolParseResult,
        appId: String,
        appSecretKey: String,
        taproAppWidthRatio: Float? = null
    ): IServiceKernel? {
        val serviceType = when (parseResult) {
            is ProtocolParseResult.LanProtocol -> ServiceType.LAN
            is ProtocolParseResult.UsbProtocol -> ServiceType.USB
            is ProtocolParseResult.SerialProtocol -> ServiceType.SERIAL
            is ProtocolParseResult.VspProtocol -> ServiceType.VSP
            is ProtocolParseResult.LocalProtocol -> ServiceType.LOCAL
            else -> return null
        }

        // Reuse the existing kernel if it is the same transport type and disconnected
        val current = currentServiceKernel
        if (current != null && isServiceKernelOfType(current, serviceType)) {
            val status = current.getConnectionStatus()
            if (status == InnerConnectionStatus.DISCONNECTED) {
                LogUtil.d(TAG, "Reusing existing service kernel of type: $serviceType")
                return current
            }
        }

        return createServiceKernel(serviceType, appId, appSecretKey, taproAppWidthRatio)
    }

    /**
     * Check if [kernel] is the implementation for [serviceType].
     */
    private fun isServiceKernelOfType(kernel: IServiceKernel, serviceType: ServiceType): Boolean {
        return when (serviceType) {
            ServiceType.LAN -> kernel is LanClientKernel
            ServiceType.USB -> kernel is CableAoaHostKernel
            ServiceType.SERIAL -> kernel is SerialServiceKernel
            ServiceType.VSP -> kernel is VSPClientKernel
            ServiceType.LOCAL -> kernel is LocalServiceKernel
        }
    }

    /**
     * Create a new kernel instance for [serviceType].
     */
    private fun createServiceKernel(
        serviceType: ServiceType,
        appId: String,
        appSecretKey: String,
        taproAppWidthRatio: Float? = null
    ): IServiceKernel {
        return when (serviceType) {
            ServiceType.LAN -> {
                LogUtil.d(TAG, "Creating LAN service")
                LanClientKernel(appId, appSecretKey, mContext)
            }
            ServiceType.USB -> {
                LogUtil.d(TAG, "Creating USB service")
                CableAoaHostKernel(appId, appSecretKey, mContext)
            }
            ServiceType.SERIAL -> {
                LogUtil.d(TAG, "Creating Serial service (RS232 Hex mode)")
                SerialServiceKernel(appId, appSecretKey, mContext)
            }
            ServiceType.VSP -> {
                LogUtil.d(TAG, "Creating VSP service")
                VSPClientKernel(appId, appSecretKey, mContext)
            }
            ServiceType.LOCAL -> {
                LogUtil.d(TAG, "Creating Local service")
                LocalServiceKernel(appId, appSecretKey, taproAppWidthRatio)
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
            callback?.onError(InnerErrorCode.E203.code, "No service kernel available")
            return
        }

        try {
            kernel.sendData(traceId, data, callback)
        } catch (e: Exception) {
            LogUtil.e(TAG, "Failed to send data: traceId=$traceId, error=${e.message}")
            callback?.onError(InnerErrorCode.E304.code, "${InnerErrorCode.E304.description}:${e.message}")
        }
    }

    /**
     * Actively probe whether the current link is alive.
     *
     * Delegates to the connected service kernel's [IServiceKernel.checkLinkAlive]. Returns `false`
     * when there is no connected kernel, so callers can fail fast instead of waiting for a
     * transaction timeout.
     *
     * @param timeoutMs Maximum time to wait for the peer to acknowledge the probe.
     */
    suspend fun checkLinkAlive(timeoutMs: Long): Boolean {
        val kernel = currentServiceKernel
        if (kernel == null) {
            LogUtil.w(TAG, "checkLinkAlive: no service kernel available")
            return false
        }
        return try {
            kernel.checkLinkAlive(timeoutMs)
        } catch (e: Exception) {
            LogUtil.e(TAG, "checkLinkAlive failed: ${e.message}")
            false
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
     * @return Boolean Whether the singleton instance has been created
     */
    fun isInitialized(): Boolean {
        return instance != null
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
        /** Transport type, used to select and reuse the correct kernel implementation. */
        private enum class ServiceType { LAN, USB, SERIAL, VSP, LOCAL }

        @Volatile
        private var instance: TaplinkServiceKernel? = null

        /**
         * Get singleton instance
         *
         * @param context Application context, must be provided on first call
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
