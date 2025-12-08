package com.sunmi.tapro.taplink.service.local.constant

/**
 * 本地服务常量类
 *
 * 定义本地IPC服务相关的常量
 *
 * @author TaPro Team
 * @since 2025-01-XX
 */
object LocalServiceConstant {

    /**
     * 默认的Service Action
     * 用于通过Intent绑定服务
     */
    const val DEFAULT_ACTION = "com.sunmi.tapro.taplink.service.local.ACTION_TRANSACTION"

    /**
     * 默认的包名
     * 用于通过packageName和action启动服务
     */
    const val DEFAULT_PACKAGE_NAME = "com.sunmi.tapro"
}

