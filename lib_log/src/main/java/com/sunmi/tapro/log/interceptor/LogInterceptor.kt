package com.sunmi.tapro.log.interceptor

/**
 * @author sunmi-pupan
 * @date 2022/8/5
 */

interface LogInterceptor {

    fun logi(tag: String, msg: String)

    fun logd(tag: String, msg: String)

    fun loge(tag: String, msg: String)

    fun flush()

    fun close()

}