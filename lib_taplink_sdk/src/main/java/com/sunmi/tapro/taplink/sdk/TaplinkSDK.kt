package com.sunmi.tapro.taplink.sdk

import android.content.Context
import com.sunmi.tapro.taplink.sdk.api.TaplinkApi
import com.sunmi.tapro.taplink.sdk.callback.ConnectionListener
import com.sunmi.tapro.taplink.sdk.callback.PaymentCallback
import com.sunmi.tapro.taplink.sdk.config.ConnectionConfig
import com.sunmi.tapro.taplink.sdk.config.TaplinkConfig
import com.sunmi.tapro.taplink.sdk.impl.TaplinkApiImpl
import com.sunmi.tapro.taplink.sdk.model.request.PaymentRequest
import com.sunmi.tapro.taplink.sdk.model.request.QueryRequest
import kotlin.jvm.JvmStatic

/**
 * Taplink SDK 主类
 *
 * 提供SDK的基础功能：初始化、连接管理、版本信息等
 * 主要职责：
 * - SDK初始化和配置
 * - 连接管理（连接、断开、状态查询）
 * - 版本信息管理
 * - 设备信息管理
 * - 提供TaplinkClient实例进行交易操作
 *
 * 推荐使用方式：
 * ```kotlin
 * // 1. 初始化SDK
 * val config = TaplinkConfig.create(appId, merchantId, secretKey)
 * TaplinkSDK.init(context, config)
 * 
 * // 2. 建立连接
 * TaplinkSDK.connect(connectionConfig) { connected ->
 *     if (connected) {
 *         // 3. 获取客户端进行交易
 *         val client = TaplinkSDK.getClient()
 *         client.quickSale(
 *             totalAmount = BigDecimal("10.00"),
 *             orderId = "ORDER001",
 *             callback = paymentCallback
 *         )
 *     }
 * }
 * 
 * // 4. 断开连接
 * TaplinkSDK.disconnect()
 * ```
 * 
 * 职责分工：
 * - **TaplinkSDK**: 初始化、连接、断开、状态管理、版本信息
 * - **TaplinkClient**: 专注交易操作（sale、refund、void等）
 *
 * @author TaPro Team
 * @since 2025-01-XX
 */
class TaplinkSDK private constructor() : TaplinkApi {

    /**
     * 内部代理类，实现具体的接口功能
     */
    private val apiImpl: TaplinkApiImpl = TaplinkApiImpl()
    
    /**
     * TaplinkClient 实例（懒加载）
     */
    private val clientInstance: TaplinkClient by lazy { TaplinkClient(apiImpl) }

    companion object {
        @Volatile
        private var INSTANCE: TaplinkSDK? = null

        /**
         * 获取TaplinkSDK单例实例
         *
         * @return TaplinkSDK 单例实例
         */
        @JvmStatic
        fun getInstance(): TaplinkSDK {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: TaplinkSDK().also { INSTANCE = it }
            }
        }

        // ==================== 初始化功能 ====================

        /**
         * 初始化SDK
         *
         * 初始化 Taplink SDK，配置应用信息和连接参数。必须在使用其他接口前调用。
         *
         * @param context Application 或 Activity 上下文
         * @param config SDK配置对象
         */
        @JvmStatic
        @JvmName("initSDK")
        fun init(context: Context, config: TaplinkConfig) {
            getInstance().init(context, config)
        }

        // ==================== 连接管理功能 ====================

        /**
         * 建立连接
         *
         * 支持所有连接模式：
         * - 应用间模式：使用空配置，SDK自动检测
         * - 线缆模式：使用空配置自动检测，或指定协议类型
         * - 局域网模式：指定IP和端口（首次），或使用空配置（后续自动连接）
         *
         * @param config 连接配置对象（可为null，使用默认配置）
         * @param listener 连接监听器
         */
        @JvmStatic
        @JvmName("connectSDK")
        fun connect(config: ConnectionConfig?, listener: ConnectionListener) {
            getInstance().connect(config, listener)
        }

        /**
         * 断开连接
         *
         * 断开与支付终端的连接，释放资源。
         */
        @JvmStatic
        @JvmName("disconnectSDK")
        fun disconnect() {
            getInstance().disconnect()
        }

        /**
         * 检查连接状态
         *
         * @return true 如果已连接，false 否则
         */
        @JvmStatic
        @JvmName("isSDKConnected")
        fun isConnected(): Boolean {
            return getInstance().isConnected()
        }

        /**
         * 设置连接状态监听器
         *
         * 设置连接状态监听器，实时监听连接状态变化、断线重连等事件。
         *
         * @param listener 连接状态监听器，null表示移除监听器
         */
        @JvmStatic
        @JvmName("setSDKConnectionListener")
        fun setConnectionListener(listener: ConnectionListener?) {
            getInstance().setConnectionListener(listener)
        }

        /**
         * 移除连接状态监听器
         *
         * 移除之前设置的连接状态监听器。
         */
        @JvmStatic
        @JvmName("removeSDKConnectionListener")
        fun removeConnectionListener() {
            getInstance().removeConnectionListener()
        }

        // ==================== 设备和版本信息 ====================

        /**
         * 获取已连接设备ID
         *
         * @return String 设备ID，如果未连接返回null
         */
        @JvmStatic
        @JvmName("getSDKConnectedDeviceId")
        fun getConnectedDeviceId(): String? {
            return getInstance().getConnectedDeviceId()
        }

        /**
         * 获取连接模式
         *
         * @return ConnectionMode 当前连接模式，如果未连接返回null
         */
        @JvmStatic
        @JvmName("getSDKConnectionMode")
        fun getConnectionMode(): String? {
            return getInstance().getConnectionMode()
        }

        /**
         * 获取 Tapro 版本号
         *
         * 获取已连接支付终端的 Tapro 版本号。
         * 只有在连接成功后才能获取到版本号。
         *
         * @return String Tapro 版本号，如果未连接或未获取到版本号返回null
         */
        @JvmStatic
        @JvmName("getSDKTaproVersion")
        fun getTaproVersion(): String? {
            return getInstance().getTaproVersion()
        }

        /**
         * 获取SDK版本
         *
         * @return String SDK版本号，格式：x.y.z
         */
        @JvmStatic
        @JvmName("getSDKVersion")
        fun getVersion(): String {
            return getInstance().getVersion()
        }

        /**
         * 清除缓存的设备信息
         *
         * 清除本地缓存的设备连接信息，下次连接时需要重新配对。
         */
        @JvmStatic
        @JvmName("clearSDKDeviceCache")
        fun clearDeviceCache() {
            getInstance().clearDeviceCache()
        }

        // ==================== 获取交易客户端 ====================

        /**
         * 获取TaplinkClient实例
         * 
         * 用于执行交易操作，需要先建立连接
         *
         * @return TaplinkClient 交易客户端实例
         */
        @JvmStatic
        fun getClient(): TaplinkClient {
            return getInstance().clientInstance
        }

        // ==================== 向后兼容接口（已弃用） ====================

        /**
         * 异步执行交易（推荐）
         *
         * @deprecated 推荐使用 TaplinkSDK.getClient() 的具体交易方法代替
         */
        @Deprecated(
            message = "推荐使用 TaplinkSDK.getClient() 的具体交易方法代替",
            replaceWith = ReplaceWith("TaplinkSDK.getClient().sale() 或其他具体交易方法")
        )
        @JvmStatic
        @JvmName("executeSDK")
        fun execute(request: PaymentRequest, callback: PaymentCallback) {
            getInstance().execute(request, callback)
        }

        /**
         * 异步查询交易状态
         *
         * @deprecated 推荐使用 TaplinkSDK.getClient().query() 代替
         */
        @Deprecated(
            message = "推荐使用 TaplinkSDK.getClient().query() 代替",
            replaceWith = ReplaceWith("TaplinkSDK.getClient().query(request, callback)")
        )
        @JvmStatic
        @JvmName("querySDK")
        fun query(request: QueryRequest, callback: PaymentCallback) {
            getInstance().query(request, callback)
        }
    }

    // ==================== 初始化功能（实例方法） ====================

    override fun init(context: Context, config: TaplinkConfig) {
        apiImpl.init(context, config)
    }

    // ==================== 连接管理功能（实例方法） ====================

    override fun connect(config: ConnectionConfig?, listener: ConnectionListener) {
        apiImpl.connect(config, listener)
    }

    override fun disconnect() {
        apiImpl.disconnect()
    }

    override fun isConnected(): Boolean {
        return apiImpl.isConnected()
    }

    override fun setConnectionListener(listener: ConnectionListener?) {
        apiImpl.setConnectionListener(listener)
    }

    override fun removeConnectionListener() {
        apiImpl.removeConnectionListener()
    }

    // ==================== 设备和版本信息（实例方法） ====================

    override fun getConnectedDeviceId(): String? {
        return apiImpl.getConnectedDeviceId()
    }

    override fun getConnectionMode(): String? {
        return apiImpl.getConnectionMode()
    }

    override fun getTaproVersion(): String? {
        return apiImpl.getTaproVersion()
    }

    override fun getVersion(): String {
        return apiImpl.getVersion()
    }

    override fun clearDeviceCache() {
        apiImpl.clearDeviceCache()
    }

    override fun getConnectionConfig(): ConnectionConfig? {
        return apiImpl.getConnectionConfig()
    }

    // ==================== 向后兼容接口（实例方法，已弃用） ====================

    override fun execute(request: PaymentRequest, callback: PaymentCallback) {
        apiImpl.execute(request, callback)
    }

    override fun query(request: QueryRequest, callback: PaymentCallback) {
        apiImpl.query(request, callback)
    }
}
