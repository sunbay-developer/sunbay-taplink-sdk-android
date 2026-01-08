package com.sunmi.tapro.taplink.sdk.api

import android.content.Context
import com.sunmi.tapro.taplink.sdk.callback.ConnectionListener
import com.sunmi.tapro.taplink.sdk.callback.PaymentCallback
import com.sunmi.tapro.taplink.sdk.config.ConnectionConfig
import com.sunmi.tapro.taplink.sdk.config.TaplinkConfig
import com.sunmi.tapro.taplink.sdk.enums.ConnectionMode
import com.sunmi.tapro.taplink.sdk.error.TaplinkException
import com.sunmi.tapro.taplink.sdk.model.request.PaymentRequest
import com.sunmi.tapro.taplink.sdk.model.request.QueryRequest
import com.sunmi.tapro.taplink.sdk.model.response.PaymentResult

/**
 * Taplink SDK API接口
 *
 * 根据 Taplink SDK for Android 集成指南文档定义的完整API接口
 * 参考文档: Taplink SDK for Android - 集成指南.md
 *
 * 主要特点：
 * - 统一使用 PaymentRequest 处理所有交易类型（通过 action 字段区分）
 * - 支持同步和异步两种调用方式
 * - 使用 ConnectionConfig 统一管理所有连接模式
 * - 方法签名与文档中的 TaplinkSDK 静态方法对应
 *
 * @author TaPro Team
 * @since 2025-01-XX
 */
interface TaplinkApi {

    // ==================== 初始化 ====================

    /**
     * 初始化SDK
     *
     * 初始化 Taplink SDK，配置应用信息和连接参数。必须在使用其他接口前调用。
     *
     * @param context Application 或 Activity 上下文
     * @param config SDK配置对象
     */
    fun init(context: Context, config: TaplinkConfig)

    // ==================== 连接管理 ====================

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
    fun connect(config: ConnectionConfig?, listener: ConnectionListener)

    /**
     * 断开连接
     *
     * 断开与支付终端的连接，释放资源。
     */
    fun disconnect()

    /**
     * 检查连接状态
     *
     * @return true 如果已连接，false 否则
     */
    fun isConnected(): Boolean

    /**
     * 获取已连接设备ID
     *
     * @return String 设备ID，如果未连接返回null
     */
    fun getConnectedDeviceId(): String?
    /**
     * 获取连接模式
     *
     * @return ConnectionMode 当前连接模式，如果未连接返回null
     */
    fun getConnectionMode(): String?

    /**
     * 获取 Tapro 版本号
     *
     * 获取已连接支付终端的 Tapro 版本号。
     * 只有在连接成功后才能获取到版本号。
     *
     * @return String Tapro 版本号，如果未连接或未获取到版本号返回null
     */
    fun getTaproVersion(): String?

    // ==================== 交易执行 ====================

    /**
     * 异步执行交易（推荐）
     *
     * 非阻塞调用，立即返回。交易结果通过回调方法传递。
     * 推荐用于跨设备场景和需要进度反馈的场景。
     *
     * @param request 支付请求对象
     * @param callback 支付回调接口
     */
    fun execute(request: PaymentRequest, callback: PaymentCallback)

    // ==================== 交易查询 ====================

    /**
     * 异步查询交易状态
     *
     * 查询交易状态，特别适用于超时场景。
     * 可以通过交易ID或交易请求ID查询。
     *
     * @param request 查询请求对象
     * @param callback 支付回调接口
     */
    fun query(request: QueryRequest, callback: PaymentCallback)

    // ==================== 监听器管理 ====================

    /**
     * 设置连接状态监听器
     *
     * 设置连接状态监听器，实时监听连接状态变化、断线重连等事件。
     *
     * @param listener 连接状态监听器，null表示移除监听器
     */
    fun setConnectionListener(listener: ConnectionListener?)

    /**
     * 移除连接状态监听器
     *
     * 移除之前设置的连接状态监听器。
     */
    fun removeConnectionListener()

    // ==================== 实用方法 ====================

    /**
     * 获取SDK版本
     *
     * @return String SDK版本号，格式：x.y.z
     */
    fun getVersion(): String

    /**
     * 清除缓存的设备信息
     *
     * 清除本地缓存的设备连接信息，下次连接时需要重新配对。
     */
    fun clearDeviceCache()
}

