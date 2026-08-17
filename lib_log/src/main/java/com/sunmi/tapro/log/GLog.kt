package com.sunmi.tapro.log

import android.util.Log
import com.sunmi.tapro.log.interceptor.LogInterceptor
import java.io.IOException
import java.io.OutputStream

/**
 * 打印通用的日志
 * @author sunmi-pupan
 */
object GLog : BaseLog {

    /**
     * 应用构建模式
     */
    val isDebug = true

    /**
     * 日志拦截器
     */
    var logInterceptor: LogInterceptor? = null

    /**
     * 添加拦截器
     */
    override fun addInterceptor(logInterceptor: LogInterceptor) {
        CLog.logInterceptor = logInterceptor
    }

    /**
     * 移出拦截器
     */
    override fun clearInterceptor() {
        logInterceptor = null
    }

    override fun flushSync() {
        logInterceptor?.flush()
    }

    override fun close() {
        logInterceptor?.close()
    }


    override fun i(tag: String, msg: String) {
        if (isDebug) {
            log(Log.INFO, tag, getContent(msg))
        }
        if (logInterceptor != null) {
            logInterceptor?.logi(tag, msg)
        }
    }

    override fun d(tag: String, msg: String) {
        if (isDebug) {
            log(Log.DEBUG, tag, getContent(msg))
        }
        if (logInterceptor != null) {
            logInterceptor?.logd(tag, msg)
        }
    }

    override fun e(tag: String, msg: String) {
        if (isDebug) {
            log(Log.ERROR, tag, getContent(msg))
        }
        if (logInterceptor != null) {
            logInterceptor?.loge(tag, msg)
        }
    }
}