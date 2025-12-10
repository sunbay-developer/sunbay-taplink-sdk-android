package com.sunmi.tapro.taplink.service

import android.content.Context
import com.sunmi.tapro.taplink.service.enums.ConnectionStatus
import com.sunmi.tapro.taplink.service.enums.InnerErrorCode
import com.sunmi.tapro.taplink.service.interfaces.ConnectionCallback
import com.sunmi.tapro.taplink.service.interfaces.IServiceKernel
import com.sunmi.tapro.taplink.service.interfaces.InnerCallback
import com.sunmi.tapro.taplink.service.local.kernel.LocalServiceKernel
import com.sunmi.tapro.taplink.service.serial.SerialServiceKernel
import com.sunmi.tapro.taplink.service.usb.UsbServiceKernel
import com.sunmi.tapro.taplink.service.util.LogUtil
import com.sunmi.tapro.taplink.service.util.ProtocolParser
import com.sunmi.tapro.taplink.service.util.ProtocolParseResult

//import com.sunmi.tapro.taplink.service.websocket.WebSocketServiceKernel

/**
 * Taplink服务内核类
 *
 * 提供WebSocket、USB、Serial、Local通信的统一服务接口
 * 采用单例模式，确保全局唯一实例
 *
 * @author TaPro Team
 * @since 2025-01-XX
 */
class TaplinkServiceKernel private constructor(context: Context) {
    private val TAG = "TaplinkServiceKernel"

    /**
     * 应用上下文（使用applicationContext避免内存泄漏）
     */
    private val mContext: Context = context.applicationContext

    /**
     * 当前使用的服务内核
     * 注意：同一时间只能有一个serviceKernel处于连接状态
     */
    private var currentServiceKernel: IServiceKernel? = null

    init {
        LogUtil.d(TAG, "TaplinkServiceKernel initialized")
    }

    /**
     * 连接服务
     *
     * 注意：serviceKernel不能同时存在连接状态。如果要连接的serviceKernel和已经连接的不一致，
     * 则需要先断开旧的连接，然后再连接新的。
     *
     * @param protocol 协议字符串，支持多种协议格式
     * @param connectionCallback 连接回调
     */
    fun connect(protocol: String, appid: String, appSecretKey: String, connectionCallback: ConnectionCallback) {
        if (!isInitialized()) {
            connectionCallback.onDisconnected(
                InnerErrorCode.C20.code,
                InnerErrorCode.C20.description
            )
            return
        }

        // 解析协议，确定使用哪个服务
        val parseResult = ProtocolParser.parse(protocol)
        if (parseResult is ProtocolParseResult.Error) {
            connectionCallback.onDisconnected(InnerErrorCode.C41.code, parseResult.message)
            return
        }

        // 根据协议类型动态获取对应的服务内核
        val serviceKernel = getServiceKernelByProtocol(parseResult, appid, appSecretKey)
        if (serviceKernel == null) {
            connectionCallback.onDisconnected(
                InnerErrorCode.C41.code,
                "Unsupported protocol type: ${parseResult::class.simpleName}"
            )
            return
        }

        // 检查当前是否有已连接的服务内核
        val oldServiceKernel = currentServiceKernel
        if (oldServiceKernel != null && oldServiceKernel !== serviceKernel) {
            // 如果当前连接的服务内核和要连接的不是同一个，且当前服务处于连接状态，先断开旧的
            val oldStatus = oldServiceKernel.getConnectionStatus()
            if (oldStatus != ConnectionStatus.DISCONNECTED) {
                LogUtil.d(TAG, "Disconnecting old service kernel before connecting new one. Old status: $oldStatus")
                try {
                    oldServiceKernel.disconnect()
                } catch (e: Exception) {
                    LogUtil.e(TAG, "Error disconnecting old service kernel: ${e.message}")
                }
            }
            // 清空旧的serviceKernel引用（因为要切换到新的类型）
            currentServiceKernel = null
        } else if (oldServiceKernel === serviceKernel) {
            // 如果是同一个实例，检查是否需要先断开
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

        // 保存当前使用的服务内核
        currentServiceKernel = serviceKernel

        // 调用对应服务的连接方法
        serviceKernel.connect(protocol, connectionCallback)
    }

    /**
     * 根据协议类型获取对应的服务内核
     *
     * 如果当前serviceKernel是同一类型且已断开，则复用；否则创建新实例。
     *
     * @param parseResult 协议解析结果
     * @return IServiceKernel? 对应的服务内核，如果不支持则返回null
     */
    private fun getServiceKernelByProtocol(
        parseResult: ProtocolParseResult,
        appId: String,
        appSecretKey: String
    ): IServiceKernel? {
        val serviceKey = when (parseResult) {
            is ProtocolParseResult.WebSocketProtocol -> SERVICE_KEY_WEBSOCKET
            is ProtocolParseResult.LanProtocol -> SERVICE_KEY_LAN

            is ProtocolParseResult.UsbProtocol -> SERVICE_KEY_USB
            is ProtocolParseResult.SerialProtocol,
            is ProtocolParseResult.VspProtocol -> SERVICE_KEY_SERIAL

            is ProtocolParseResult.LocalProtocol -> SERVICE_KEY_LOCAL
            is ProtocolParseResult.Error -> return null
        }

        // 检查当前serviceKernel是否是同一类型
        val current = currentServiceKernel
        if (current != null && isServiceKernelOfType(current, serviceKey)) {
            // 如果是同一类型且已断开，可以复用
            val status = current.getConnectionStatus()
            if (status == ConnectionStatus.DISCONNECTED) {
                LogUtil.d(TAG, "Reusing existing service kernel of type: $serviceKey")
                return current
            }
        }

        // 创建新的服务内核实例
        return createServiceKernel(serviceKey, appId, appSecretKey)
    }

    /**
     * 检查serviceKernel是否是指定类型
     *
     * @param kernel 服务内核实例
     * @param serviceKey 服务类型标识
     * @return Boolean 是否是指定类型
     */
    private fun isServiceKernelOfType(kernel: IServiceKernel, serviceKey: String): Boolean {
        return when (serviceKey) {
//            SERVICE_KEY_WEBSOCKET -> kernel is WebSocketServiceKernel
//            SERVICE_KEY_LAN -> kernel is WebSocketServiceKernel
            SERVICE_KEY_USB -> kernel is UsbServiceKernel
            SERVICE_KEY_SERIAL -> kernel is SerialServiceKernel
            SERVICE_KEY_LOCAL -> kernel is LocalServiceKernel
            else -> false
        }
    }

    /**
     * 创建服务内核实例
     *
     * @param serviceKey 服务类型标识
     * @return IServiceKernel 服务内核实例
     */
    private fun createServiceKernel(
        serviceKey: String, appId: String,
        appSecretKey: String
    ): IServiceKernel {
        return when (serviceKey) {
//            SERVICE_KEY_WEBSOCKET -> {
//                CLog.d(TAG, "Creating WebSocket service")
//                WebSocketServiceKernel(appId, appSecretKey)
//            }
//
//            SERVICE_KEY_LAN -> {
//                CLog.d(TAG, "Creating LAN service")
//                WebSocketServiceKernel(appId, appSecretKey)
//            }

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
     * 获取当前使用的服务内核
     *
     * @return IServiceKernel? 当前服务内核，可能为null
     */
    fun getCurrentServiceKernel(): IServiceKernel? {
        return currentServiceKernel
    }

    /**
     * 发送数据
     *
     * 通过当前连接的服务内核发送数据
     *
     * @param traceId 追踪ID
     * @param data 要发送的数据
     * @param callback 发送结果回调，可为null
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
     * 获取应用上下文
     *
     * @return Context 应用上下文
     */
    fun getContext(): Context {
        return mContext
    }

    /**
     * 检查服务是否已初始化
     *
     * @return Boolean 是否已初始化
     */
    fun isInitialized(): Boolean {
        return mContext != null
    }

    /**
     * 释放资源
     */
    fun release() {
        // 断开当前服务连接
        currentServiceKernel?.let { kernel ->
            try {
                kernel.disconnect()
            } catch (e: Exception) {
                LogUtil.e(TAG, "Error disconnecting service: ${e.message}")
            }
        }

        // 清空当前服务内核
        currentServiceKernel = null

        LogUtil.d(TAG, "TaplinkServiceKernel released")
    }

    companion object {
        // 服务类型标识常量
        private const val SERVICE_KEY_WEBSOCKET = "websocket"
        private const val SERVICE_KEY_LAN = "lan"
        private const val SERVICE_KEY_USB = "usb"
        private const val SERVICE_KEY_SERIAL = "serial"
        private const val SERVICE_KEY_LOCAL = "local"

        @Volatile
        private var instance: TaplinkServiceKernel? = null

        /**
         * 获取单例实例
         *
         * @param context 应用上下文，首次调用时必须提供
         * @return TaplinkServiceKernel 单例实例
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
         * 获取单例实例（如果已初始化）
         *
         * @return TaplinkServiceKernel? 单例实例，如果未初始化则返回null
         */
        @JvmStatic
        fun getInstance(): TaplinkServiceKernel? {
            return instance
        }

        /**
         * 销毁单例实例（用于测试或特殊场景）
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
