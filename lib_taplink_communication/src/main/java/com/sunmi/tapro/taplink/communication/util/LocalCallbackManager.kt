package com.sunmi.tapro.taplink.communication.util

import com.sunmi.tapro.taplink.communication.enums.InnerErrorCode
import com.sunmi.tapro.taplink.communication.interfaces.InnerCallback
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * 本地服务回调管理类
 *
 * 用于管理多个异步请求的回调，支持根据请求ID查找和移除对应的回调
 * 提供线程安全的回调管理，支持为每个回调配置不同的超时时间
 *
 * @param T 回调类型
 * @param defaultTimeoutMillis 默认回调超时时间（毫秒），默认 60 秒
 * @author TaPro Team
 * @since 2025-01-XX
 */
class LocalCallbackManager<T>(
    private val defaultTimeoutMillis: Long = 60_000L,
    private val tag: String = "LocalCallbackManager"
) {
    /**
     * 请求ID生成器
     */
    private val requestIdGenerator = AtomicLong(0)

    /**
     * 回调映射表（线程安全）
     * Key: 请求ID，Value: 回调包装对象
     */
    private val callbackMap = ConcurrentHashMap<Long, CallbackWrapper<T>>()

    /**
     * 追踪ID到回调的映射表（线程安全）
     * Key: traceId（String），Value: 回调包装对象
     */
    private val traceIdCallbackMap = ConcurrentHashMap<String, CallbackWrapper<T>>()

    /**
     * 协程作用域
     */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    companion object {
        /**
         * 连接超时时间（60秒）
         */
        const val CONNECTION_TIMEOUT = 60_000L

        /**
         * 交易超时时间（180秒）
         */
        const val TRANSACTION_TIMEOUT = 180_000L

        /**
         * 查询超时时间（30秒）
         */
        const val QUERY_TIMEOUT = 60_000L

        /**
         * 初始化超时时间（60秒）
         */
        const val INIT_TIMEOUT = 180_000L
    }

    /**
     * 添加回调（通过traceId）
     *
     * @param traceId 追踪ID
     * @param callback 回调对象
     * @param app2app 是否为应用间模式（app2app模式不设置超时）
     * @param timeoutMillis 自定义超时时间（毫秒），如果为null则使用默认超时时间
     * @return Boolean 是否添加成功
     */
    fun addCallbackByTraceId(traceId: String, callback: T, app2app: Boolean, timeoutMillis: Long? = null): Boolean {
        if (traceId.isBlank()) {
            LogUtil.i(tag, "TraceId is blank, skip adding callback")
            return false
        }

        val actualTimeout = timeoutMillis ?: defaultTimeoutMillis
        val wrapper = CallbackWrapper(callback, if (app2app) -1 else System.currentTimeMillis(), actualTimeout)
        val existing = traceIdCallbackMap.putIfAbsent(traceId, wrapper)

        if (existing != null) {
            LogUtil.i(tag, "Callback with traceId=$traceId already exists, overwriting")
            traceIdCallbackMap[traceId] = wrapper
            return false
        }

        LogUtil.d(tag, "Callback added by traceId: $traceId, timeout: ${actualTimeout}ms")

        // 启动超时清理任务
        startTimeoutTaskForTraceId(traceId, actualTimeout)

        return true
    }

    /**
     * 添加连接回调（使用连接超时时间）
     *
     * @param traceId 追踪ID
     * @param callback 回调对象
     * @param app2app 是否为应用间模式
     * @return Boolean 是否添加成功
     */
    fun addConnectionCallback(traceId: String, callback: T, app2app: Boolean): Boolean {
        return addCallbackByTraceId(traceId, callback, app2app, CONNECTION_TIMEOUT)
    }

    /**
     * 添加交易回调（使用交易超时时间）
     *
     * @param traceId 追踪ID
     * @param callback 回调对象
     * @param app2app 是否为应用间模式
     * @return Boolean 是否添加成功
     */
    fun addTransactionCallback(traceId: String, callback: T, app2app: Boolean, requestTimeout: Long?): Boolean {
        return addCallbackByTraceId(traceId, callback, app2app, requestTimeout ?: TRANSACTION_TIMEOUT)
    }

    /**
     * 添加查询回调（使用查询超时时间）
     *
     * @param traceId 追踪ID
     * @param callback 回调对象
     * @param app2app 是否为应用间模式
     * @return Boolean 是否添加成功
     */
    fun addQueryCallback(traceId: String, callback: T, app2app: Boolean, requestTimeout: Long?): Boolean {
        return addCallbackByTraceId(traceId, callback, app2app, requestTimeout ?: QUERY_TIMEOUT)
    }

    /**
     * 添加初始化回调（使用初始化超时时间）
     *
     * @param traceId 追踪ID
     * @param callback 回调对象
     * @param app2app 是否为应用间模式
     * @return Boolean 是否添加成功
     */
    fun addInitCallback(traceId: String, callback: T, app2app: Boolean, requestTimeout: Long?): Boolean {
        return addCallbackByTraceId(traceId, callback, app2app, requestTimeout ?: INIT_TIMEOUT)
    }

    /**
     * 获取回调（通过traceId，不移除）
     *
     * @param traceId 追踪ID
     * @return T? 回调对象，如果不存在或已超时则返回null
     */
    fun getCallbackByTraceId(traceId: String): T? {
        if (traceId.isBlank()) {
            return null
        }

        val wrapper = traceIdCallbackMap[traceId]
        if (wrapper != null) {
            // 检查是否超时
            if (wrapper.timestamp != -1L && System.currentTimeMillis() - wrapper.timestamp > wrapper.timeoutMillis) {
                LogUtil.i(tag, "Callback expired by traceId: $traceId")
                traceIdCallbackMap.remove(traceId)
                return null
            }
            return wrapper.callback
        }
        return null
    }

    /**
     * 获取并移除回调（通过traceId）
     *
     * @param traceId 追踪ID
     * @return T? 回调对象，如果不存在或已超时则返回null
     */
    fun getAndRemoveCallbackByTraceId(traceId: String): T? {
        if (traceId.isBlank()) {
            return null
        }

        val wrapper = traceIdCallbackMap.remove(traceId)
        if (wrapper != null) {
            LogUtil.d(tag, "Callback retrieved and removed by traceId: $traceId")
            return wrapper.callback
        }
        LogUtil.d(tag, "Callback not found by traceId: $traceId")
        return null
    }

    /**
     * 移除回调（通过traceId）
     *
     * @param traceId 追踪ID
     * @return Boolean 是否成功移除
     */
    fun removeCallbackByTraceId(traceId: String): Boolean {
        if (traceId.isBlank()) {
            return false
        }

        val removed = traceIdCallbackMap.remove(traceId) != null
        if (removed) {
            LogUtil.d(tag, "Callback removed by traceId: $traceId")
        }
        return removed
    }

    /**
     * 清理所有回调
     */
    fun clearAll() {
        val count = callbackMap.size
        val traceIdCount = traceIdCallbackMap.size
        callbackMap.clear()
        traceIdCallbackMap.clear()
        LogUtil.d(tag, "All callbacks cleared, requestId count=$count, traceId count=$traceIdCount")
    }

    /**
     * 立即触发所有待处理回调的错误
     * 用于连接断开或进程崩溃时立即通知应用层
     *
     * @param errorCode 错误码
     * @param errorMessage 错误消息
     */
    fun failAllCallbacks(errorCode: String, errorMessage: String) {
        val callbacks = traceIdCallbackMap.values.toList()
        traceIdCallbackMap.clear()
        callbackMap.clear()

        if (callbacks.isNotEmpty()) {
            LogUtil.w(tag, "Failing ${callbacks.size} pending callbacks due to connection loss: $errorCode")
            callbacks.forEach { wrapper ->
                try {
                    (wrapper.callback as? InnerCallback)?.onError(errorCode, errorMessage)
                } catch (e: Exception) {
                    LogUtil.e(tag, "Failed to trigger callback error: ${e.message}")
                }
            }
        }
    }

    /**
     * 清理超时的回调
     *
     * @return Int 清理的回调数量
     */
    fun clearExpired(): Int {
        val now = System.currentTimeMillis()
        val expiredIds = callbackMap.entries
            .filter { (_, wrapper) -> now - wrapper.timestamp > wrapper.timeoutMillis }
            .map { it.key }

        expiredIds.forEach { callbackMap.remove(it) }

        if (expiredIds.isNotEmpty()) {
            LogUtil.d(tag, "Cleared ${expiredIds.size} expired callbacks")
        }

        return expiredIds.size
    }

    /**
     * 获取当前回调数量
     *
     * @return Int 回调数量
     */
    fun getCallbackCount(): Int {
        return callbackMap.size
    }

    /**
     * 启动超时清理任务
     *
     * @param requestId 请求ID
     * @param timeoutMillis 超时时间（毫秒）
     */
    private fun startTimeoutTask(requestId: Long, timeoutMillis: Long) {
        scope.launch {
            delay(timeoutMillis)
            // 检查回调是否仍然存在且未使用
            val wrapper = callbackMap[requestId]
            if (wrapper != null) {
                val elapsed = System.currentTimeMillis() - wrapper.timestamp
                if (elapsed >= wrapper.timeoutMillis) {
                    callbackMap.remove(requestId)
                    LogUtil.i(tag, "Callback timeout and removed: requestId=$requestId, elapsed=${elapsed}ms")
                }
            }
        }
    }

    /**
     * 启动超时清理任务（通过traceId）
     *
     * @param traceId 追踪ID
     * @param timeoutMillis 超时时间（毫秒）
     */
    private fun startTimeoutTaskForTraceId(traceId: String, timeoutMillis: Long) {
        scope.launch {
            delay(timeoutMillis)
            // 检查回调是否仍然存在且未使用
            val wrapper = traceIdCallbackMap[traceId]
            if (wrapper != null) {
                if (wrapper.timestamp == -1L) return@launch
                val elapsed = System.currentTimeMillis() - wrapper.timestamp
                if (elapsed >= wrapper.timeoutMillis) {
                    val removedCallback = traceIdCallbackMap.remove(traceId)
                    LogUtil.i(tag, "Callback timeout and removed by traceId: $traceId, elapsed=${elapsed}ms")
                    (removedCallback?.callback as InnerCallback).onError(InnerErrorCode.Timeout.code, "Callback timeout")
                }
            }
        }
    }

    /**
     * 释放资源
     */
    fun release() {
        clearAll()
        scope.cancel()
        LogUtil.d(tag, "LocalCallbackManager released")
    }

    /**
     * 回调包装类
     *
     * @param callback 回调对象
     * @param timestamp 创建时间戳
     * @param timeoutMillis 超时时间（毫秒）
     */
    private data class CallbackWrapper<T>(
        val callback: T,
        val timestamp: Long,
        val timeoutMillis: Long
    )
}

