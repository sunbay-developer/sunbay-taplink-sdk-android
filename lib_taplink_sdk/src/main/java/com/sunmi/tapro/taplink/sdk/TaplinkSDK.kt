package com.sunmi.tapro.taplink.sdk

import android.content.Context
import com.sunmi.tapro.taplink.sdk.api.TaplinkApi
import com.sunmi.tapro.taplink.sdk.callback.ConnectionListener
import com.sunmi.tapro.taplink.sdk.callback.PaymentCallback
import com.sunmi.tapro.taplink.sdk.config.ConnectionConfig
import com.sunmi.tapro.taplink.sdk.config.TaplinkConfig
import com.sunmi.tapro.taplink.sdk.enums.ConnectionMode
import com.sunmi.tapro.taplink.sdk.error.TaplinkException
import com.sunmi.tapro.taplink.sdk.impl.TaplinkApiImpl
import com.sunmi.tapro.taplink.sdk.model.request.PaymentRequest
import com.sunmi.tapro.taplink.sdk.model.request.QueryRequest
import com.sunmi.tapro.taplink.sdk.model.response.PaymentResult
import kotlin.jvm.JvmStatic

/**
 * Taplink SDK 主类
 *
 * 提供统一的SDK入口，实现TaplinkApi接口
 * 内部通过代理类TaplinkApiImpl实现具体功能
 *
 * 使用方式：
 * - 静态方法调用：TaplinkSDK.init(context, config)
 * - 单例模式：TaplinkSDK.getInstance().init(context, config)
 *
 * @author TaPro Team
 * @since 2025-01-XX
 */
class TaplinkSDK private constructor() : TaplinkApi {

    /**
     * 内部代理类，实现具体的接口功能
     */
    private val apiImpl: TaplinkApiImpl = TaplinkApiImpl()

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

        // ==================== 初始化（静态方法） ====================

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

        // ==================== 连接管理（静态方法） ====================

        /**
         * 建立连接（统一连接方法）
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

        // ==================== 交易执行（静态方法） ====================

        /**
         * 异步执行交易（推荐）
         *
         * 非阻塞调用，立即返回。交易结果通过回调方法传递。
         * 推荐用于跨设备场景和需要进度反馈的场景。
         *
         * @param request 支付请求对象
         * @param callback 支付回调接口
         */
        @JvmStatic
        @JvmName("executeSDK")
        fun execute(request: PaymentRequest, callback: PaymentCallback) {
            getInstance().execute(request, callback)
        }

        // ==================== 交易查询（静态方法） ====================

        /**
         * 异步查询交易状态
         *
         * 查询交易状态，特别适用于超时场景。
         * 可以通过交易ID或交易请求ID查询。
         *
         * @param request 查询请求对象
         * @param callback 支付回调接口
         */
        @JvmStatic
        @JvmName("querySDK")
        fun query(request: QueryRequest, callback: PaymentCallback) {
            getInstance().query(request, callback)
        }

        // ==================== 监听器管理（静态方法） ====================

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

        // ==================== 实用方法（静态方法） ====================

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
    }

    // ==================== 初始化（实例方法） ====================

    override fun init(context: Context, config: TaplinkConfig) {
        apiImpl.init(context, config)
    }

    // ==================== 连接管理（实例方法） ====================

    override fun connect(config: ConnectionConfig?, listener: ConnectionListener) {
        apiImpl.connect(config, listener)
    }

    override fun disconnect() {
        apiImpl.disconnect()
    }

    override fun isConnected(): Boolean {
        return apiImpl.isConnected()
    }

    override fun getConnectedDeviceId(): String? {
        return apiImpl.getConnectedDeviceId()
    }

    override fun getConnectionMode(): String? {
        return apiImpl.getConnectionMode()
    }

    override fun getTaproVersion(): String? {
        return apiImpl.getTaproVersion()
    }

    // ==================== 交易执行（实例方法） ====================

    override fun execute(request: PaymentRequest, callback: PaymentCallback) {
        apiImpl.execute(request, callback)
    }

    // ==================== 交易查询（实例方法） ====================

    override fun query(request: QueryRequest, callback: PaymentCallback) {
        apiImpl.query(request, callback)
    }

    // ==================== 监听器管理（实例方法） ====================

    override fun setConnectionListener(listener: ConnectionListener?) {
        apiImpl.setConnectionListener(listener)
    }

    override fun removeConnectionListener() {
        apiImpl.removeConnectionListener()
    }

    // ==================== 实用方法（实例方法） ====================

    override fun getVersion(): String {
        return apiImpl.getVersion()
    }

    override fun clearDeviceCache() {
        apiImpl.clearDeviceCache()
    }
}
