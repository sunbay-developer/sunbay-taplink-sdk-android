package com.sunmi.tapro.taplink.communication.util

import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Local Service Callback Manager Class
 *
 * Used to manage callbacks for multiple asynchronous requests, supports finding and removing callbacks by request ID
 * Provides thread-safe callback management with timeout cleanup mechanism
 *
 * @param T Callback type
 * @param timeoutMillis Callback timeout time (milliseconds), default 60 seconds, automatically cleaned up after timeout
 * @author TaPro Team
 * @since 2025-01-XX
 */
class LocalCallbackManager<T>(
    private val timeoutMillis: Long = 60_000L,
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
    
    /**
     * Add callback (by traceId)
     *
     * @param traceId Trace ID
     * @param callback Callback object
     * @return Boolean Whether added successfully
     */
    fun addCallbackByTraceId(traceId: String, callback: T): Boolean {
        if (traceId.isBlank()) {
            LogUtil.i(tag, "TraceId is blank, skip adding callback")
            return false
        }
        
        val wrapper = CallbackWrapper(callback, System.currentTimeMillis())
        val existing = traceIdCallbackMap.putIfAbsent(traceId, wrapper)
        
        if (existing != null) {
            LogUtil.i(tag, "Callback with traceId=$traceId already exists, overwriting")
            traceIdCallbackMap[traceId] = wrapper
            return false
        }

        LogUtil.d(tag, "Callback added by traceId: $traceId")
        
        // Start timeout cleanup task
        startTimeoutTaskForTraceId(traceId)
        
        return true
    }
    
    /**
     * Get callback by traceId (without removing)
     *
     * @param traceId Trace ID
     * @return T? Callback object, returns null if not exists or expired
     */
    fun getCallbackByTraceId(traceId: String): T? {
        if (traceId.isBlank()) {
            return null
        }
        
        val wrapper = traceIdCallbackMap[traceId]
        if (wrapper != null) {
            // Check if timeout occurred
            if (System.currentTimeMillis() - wrapper.timestamp > timeoutMillis) {
                LogUtil.i(tag, "Callback expired by traceId: $traceId")
                traceIdCallbackMap.remove(traceId)
                return null
            }
            return wrapper.callback
        }
        return null
    }
    
    /**
     * Get and remove callback by traceId
     *
     * @param traceId Trace ID
     * @return T? Callback object, returns null if not exists or expired
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
     * Remove callback by traceId
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
     * Clear expired callbacks
     *
     * @return Int Number of callbacks cleared
     */
    fun clearExpired(): Int {
        val now = System.currentTimeMillis()
        val expiredIds = callbackMap.entries
            .filter { (_, wrapper) -> now - wrapper.timestamp > timeoutMillis }
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
     * @return Int Number of callbacks
     */
    fun getCallbackCount(): Int {
        return callbackMap.size
    }

    /**
     * Start timeout cleanup task
     *
     * @param requestId Request ID
     */
    private fun startTimeoutTask(requestId: Long) {
        scope.launch {
            delay(timeoutMillis)
            // Check if callback still exists and is unused
            val wrapper = callbackMap[requestId]
            if (wrapper != null) {
                val elapsed = System.currentTimeMillis() - wrapper.timestamp
                if (elapsed >= timeoutMillis) {
                    callbackMap.remove(requestId)
                    LogUtil.i(tag, "Callback timeout and removed: requestId=$requestId, elapsed=${elapsed}ms")
                }
            }
        }
    }
    
    /**
     * Start timeout cleanup task by traceId
     *
     * @param traceId Trace ID
     */
    private fun startTimeoutTaskForTraceId(traceId: String) {
        scope.launch {
            delay(timeoutMillis)
            // Check if callback still exists and is unused
            val wrapper = traceIdCallbackMap[traceId]
            if (wrapper != null) {
                val elapsed = System.currentTimeMillis() - wrapper.timestamp
                if (elapsed >= timeoutMillis) {
                    traceIdCallbackMap.remove(traceId)
                    LogUtil.i(tag, "Callback timeout and removed by traceId: $traceId, elapsed=${elapsed}ms")
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
     */
    private data class CallbackWrapper<T>(
        val callback: T,
        val timestamp: Long
    )
}

