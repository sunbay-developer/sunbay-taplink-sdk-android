package com.sunmi.tapro.log

import android.util.Log
import com.sunmi.tapro.log.interceptor.LogInterceptor
import java.io.IOException
import java.io.OutputStream

/**
 * Transaction log printer
 * @author sunmi-pupan
 */
object TLog : BaseLog {

    /**
     * Application build mode
     */
    val isDebug = true

    /**
     * Log interceptor
     */
    var logInterceptor: LogInterceptor? = null

    /**
     * Add interceptor
     */
    override fun addInterceptor(logInterceptor: LogInterceptor) {
        CLog.logInterceptor = logInterceptor
    }

    /**
     * Remove interceptor
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