package com.sunmi.tapro.taplink.communication.util

import com.sunmi.tapro.taplink.communication.enums.InnerErrorCode
import com.sunmi.tapro.taplink.communication.interfaces.InnerCallback
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Local service callback management class
 *
 * Used to manage callbacks for multiple asynchronous requests, supports finding and removing corresponding callbacks by request ID
 * Provides thread-safe callback management, supports configuring different timeout times for each callback
 *
 * @param T Callback type
 * @param defaultTimeoutMillis Default callback timeout time (milliseconds), default 60 seconds
 * @author TaPro Team
 * @since 2025-01-XX
 */
class LocalCallbackManager<T>(
    private val defaultTimeoutMillis: Long = 60_000L,
    private val tag: String = "LocalCallbackManager"
) {
    /**
     * Request ID generator
     */
    private val requestIdGenerator = AtomicLong(0)

    /**
     * Callback mapping table (thread-safe)
     * Key: Request ID, Value: Callback wrapper object
     */
    private val callbackMap = ConcurrentHashMap<Long, CallbackWrapper<T>>()

    /**
     * Trace ID to callback mapping table (thread-safe)
     * Key: traceId (String), Value: Callback wrapper object
     */
    private val traceIdCallbackMap = ConcurrentHashMap<String, CallbackWrapper<T>>()

    /**
     * Coroutine scope
     */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    companion object {
        /**
         * Connection timeout (60 seconds)
         */
        const val CONNECTION_TIMEOUT = 60_000L

        /**
         * Transaction timeout (180 seconds)
         */
        const val TRANSACTION_TIMEOUT = 180_000L

        /**
         * Query timeout (30 seconds)
         */
        const val QUERY_TIMEOUT = 60_000L

        /**
         * Initialization timeout (60 seconds)
         */
        const val INIT_TIMEOUT = 180_000L
    }

    /**
     * Add callback (by traceId)
     *
     * @param traceId Trace ID
     * @param callback Callback object
     * @param app2app Whether it's app-to-app mode (app2app mode doesn't set timeout)
     * @param timeoutMillis Custom timeout time (milliseconds), if null then use default timeout time
     * @return Boolean Whether addition was successful
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

        // Start timeout cleanup task
        startTimeoutTaskForTraceId(traceId, actualTimeout)

        return true
    }

    /**
     * Add connection callback (using connection timeout)
     *
     * @param traceId Trace ID
     * @param callback Callback object
     * @param app2app Whether it's app-to-app mode
     * @return Boolean Whether addition was successful
     */
    fun addConnectionCallback(traceId: String, callback: T, app2app: Boolean): Boolean {
        return addCallbackByTraceId(traceId, callback, app2app, CONNECTION_TIMEOUT)
    }

    /**
     * Add transaction callback (using transaction timeout)
     *
     * @param traceId Trace ID
     * @param callback Callback object
     * @param app2app Whether it's app-to-app mode
     * @return Boolean Whether addition was successful
     */
    fun addTransactionCallback(traceId: String, callback: T, app2app: Boolean, requestTimeout: Long?): Boolean {
        return addCallbackByTraceId(traceId, callback, app2app, requestTimeout ?: TRANSACTION_TIMEOUT)
    }

    /**
     * Add query callback (using query timeout)
     *
     * @param traceId Trace ID
     * @param callback Callback object
     * @param app2app Whether it's app-to-app mode
     * @return Boolean Whether addition was successful
     */
    fun addQueryCallback(traceId: String, callback: T, app2app: Boolean, requestTimeout: Long?): Boolean {
        return addCallbackByTraceId(traceId, callback, app2app, requestTimeout ?: QUERY_TIMEOUT)
    }

    /**
     * Add initialization callback (using initialization timeout)
     *
     * @param traceId Trace ID
     * @param callback Callback object
     * @param app2app Whether it's app-to-app mode
     * @return Boolean Whether addition was successful
     */
    fun addInitCallback(traceId: String, callback: T, app2app: Boolean, requestTimeout: Long?): Boolean {
        return addCallbackByTraceId(traceId, callback, app2app, requestTimeout ?: INIT_TIMEOUT)
    }

    /**
     * Get callback (by traceId, don't remove)
     *
     * @param traceId Trace ID
     * @return T? Callback object, returns null if doesn't exist or has timed out
     */
    fun getCallbackByTraceId(traceId: String): T? {
        if (traceId.isBlank()) {
            return null
        }

        val wrapper = traceIdCallbackMap[traceId]
        if (wrapper != null) {
            // Check if timed out
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
     * Get and remove callback (by traceId)
     *
     * @param traceId Trace ID
     * @return T? Callback object, returns null if doesn't exist or has timed out
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
     * Remove callback (by traceId)
     *
     * @param traceId Trace ID
     * @return Boolean Whether removal was successful
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
     * Clear all callbacks
     */
    fun clearAll() {
        val count = callbackMap.size
        val traceIdCount = traceIdCallbackMap.size
        callbackMap.clear()
        traceIdCallbackMap.clear()
        LogUtil.d(tag, "All callbacks cleared, requestId count=$count, traceId count=$traceIdCount")
    }

    /**
     * Immediately trigger error for all pending callbacks
     * Used to immediately notify application layer when connection is lost or process crashes
     *
     * @param errorCode Error code
     * @param errorMessage Error message
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
     * Clear expired callbacks
     *
     * @return Int Number of cleared callbacks
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
     * Get current callback count
     *
     * @return Int Callback count
     */
    fun getCallbackCount(): Int {
        return callbackMap.size
    }

    /**
     * Get current traceId callback count
     *
     * @return Int traceId callback count
     */
    fun getTraceIdCallbackCount(): Int {
        return traceIdCallbackMap.size
    }

    /**
     * Start timeout cleanup task
     *
     * @param requestId Request ID
     * @param timeoutMillis Timeout time (milliseconds)
     */
    private fun startTimeoutTask(requestId: Long, timeoutMillis: Long) {
        scope.launch {
            delay(timeoutMillis)
            // Check if callback still exists and unused
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
     * Start timeout cleanup task (by traceId)
     *
     * @param traceId Trace ID
     * @param timeoutMillis Timeout time (milliseconds)
     */
    private fun startTimeoutTaskForTraceId(traceId: String, timeoutMillis: Long) {
        scope.launch {
            delay(timeoutMillis)
            // Check if callback still exists and unused
            val wrapper = traceIdCallbackMap[traceId]
            if (wrapper != null) {
                if (wrapper.timestamp == -1L) return@launch
                val elapsed = System.currentTimeMillis() - wrapper.timestamp
                if (elapsed >= wrapper.timeoutMillis) {
                    val removedCallback = traceIdCallbackMap.remove(traceId)
                    LogUtil.i(tag, "Callback timeout and removed by traceId: $traceId, elapsed=${elapsed}ms")
                    (removedCallback?.callback as InnerCallback).onError(InnerErrorCode.E306.code, "Callback timeout")
                }
            }
        }
    }

    /**
     * Release resources
     */
    fun release() {
        clearAll()
        scope.cancel()
        LogUtil.d(tag, "LocalCallbackManager released")
    }

    /**
     * Callback wrapper class
     *
     * @param callback Callback object
     * @param timestamp Creation timestamp
     * @param timeoutMillis Timeout time (milliseconds)
     */
    private data class CallbackWrapper<T>(
        val callback: T,
        val timestamp: Long,
        val timeoutMillis: Long
    )
}

