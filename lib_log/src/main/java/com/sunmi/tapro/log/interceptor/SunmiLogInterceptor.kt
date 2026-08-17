package com.sunmi.tapro.log.interceptor

import android.content.Context
import com.tencent.mars.xlog.Log
import com.tencent.mars.xlog.Xlog
import com.sunmi.tapro.log.BuildConfig

/**
 * @author sunmi-pupan
 * @date 2025/8/25
 */
class SunmiLogInterceptor(val context: Context) : LogInterceptor {
    init {
        System.loadLibrary("c++_shared")
        System.loadLibrary("marsxlog")

        val logPath = context.filesDir.path + "/cashierLog"
        val cachePath = context.filesDir.path + "/cache"
        val appName = getAppName()

        val xlog = Xlog()
        xlog.setMaxAliveTime(0, 7 * 24 * 60 * 60)
        Log.setLogImp(xlog)

        if (BuildConfig.DEBUG) {
            Log.setConsoleLogOpen(true)
            Log.appenderOpen(
                Xlog.LEVEL_DEBUG,
                Xlog.AppednerModeAsync,
                cachePath,
                logPath,
                appName,
                0
            )
        } else {
            Log.setConsoleLogOpen(false)
            Log.appenderOpen(
                Xlog.LEVEL_INFO,
                Xlog.AppednerModeAsync,
                cachePath,
                logPath,
                appName,
                0
            )
        }

    }

    fun getAppName(): String {
        return context.applicationInfo.loadLabel(context.packageManager).toString()
    }

    override fun logi(tag: String, msg: String) {
        Log.i(tag, msg)
    }

    override fun logd(tag: String, msg: String) {
        Log.d(tag, msg)
    }

    override fun loge(tag: String, msg: String) {
        Log.e(tag, msg)
    }

    override fun flush() {
        Log.appenderFlushSync(true)
    }

    override fun close() {
        Log.appenderClose()
    }

}