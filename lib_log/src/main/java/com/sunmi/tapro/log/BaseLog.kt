package com.sunmi.tapro.log

import android.util.Log
import com.sunmi.tapro.log.interceptor.LogInterceptor
import java.util.Locale

/**
 * 日志打印接口
 */
interface BaseLog {
    val MAX_LOG_LENGTH: Int
        get() = 1024

    fun getContent(msg: String): String {
        return getCodeLocation(4) + " " + msg
    }

    private fun getCodeLocation(locationIndex: Int): String {
        val stackTrace = Throwable().stackTrace[locationIndex]
        val className = stackTrace.fileName
        var methodName = stackTrace.methodName
        val lineNumber = stackTrace.lineNumber
        methodName =
            methodName.substring(0, 1).uppercase(Locale.getDefault()) + methodName.substring(1)
        return "[ ($className:$lineNumber)#$methodName ] "
    }

    fun log(priority: Int, tag: String, msg: String) {
        if (msg.length > MAX_LOG_LENGTH) {
            // Print in segments
            var start = 0
            while (start < msg.length) {
                val end = minOf(start + MAX_LOG_LENGTH, msg.length)
                printLog(priority, tag, getContent(msg.substring(start, end)))
                start = end
            }
        } else {
            printLog(priority, tag, getContent(msg))
        }
    }

    // New unified segment printing method
    private fun printLog(priority: Int, tag: String, message: String) {
        when (priority) {
            Log.INFO -> Log.i(tag, message)
            Log.ERROR -> Log.e(tag, message)
            Log.DEBUG -> Log.d(tag, message)
            else -> Log.i(tag, message) // Default to INFO level
        }
        return
    }

    /**
     * 添加拦截器
     */
    fun addInterceptor(logInterceptor: LogInterceptor) {
    }

    /**
     * 移出拦截器
     */
    fun clearInterceptor() {
    }

    fun flushSync() {
    }

    fun close() {
    }


    fun i(tag: String, msg: String) {
    }

    fun d(tag: String, msg: String) {
    }

    fun e(tag: String, msg: String) {
    }
}