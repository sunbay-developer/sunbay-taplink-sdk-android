package com.sunmi.tapro.taplink.service.interfaces

/**
 * 内部回调接口
 *
 * 用于内部服务层和SDK层之间的回调通信
 * 提供成功响应和错误响应两种回调方式
 *
 * @author TaPro Team
 * @since 2025-01-XX
 */
interface InnerCallback {
    /**
     * 错误回调
     *
     * @param code 错误码
     * @param msg 错误信息
     */
    fun onError(code: String, msg: String)

    /**
     * 响应回调
     *
     * @param responseData 响应数据（JSON字符串）
     */
    fun onResponse(responseData: String)
}



