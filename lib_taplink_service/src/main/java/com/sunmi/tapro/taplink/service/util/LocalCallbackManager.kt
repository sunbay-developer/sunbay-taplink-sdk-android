package com.sunmi.tapro.taplink.service.util

import com.sunmi.tapro.log.CLog
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * 本地服务回调管理类
 *
 * 用于管理多个异步请求的回调，支持根据请求ID查找和移除对应的回调
 * 提供线程安全的回调管理，支持超时清理机制
 *
 * @param T 回调类型
 * @param timeoutMillis 回调超时时间（毫秒），默认 60 秒，超时后自动清理
 * @author TaPro Team
 * @since 2025-01-XX
 */
class LocalCallbackManager<T>(
    private val timeoutMillis: Long = 60_000L,
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
    
    /**
     * 添加回调（通过traceId）
     *
     * @param traceId 追踪ID
     * @param callback 回调对象
     * @return Boolean 是否添加成功
     */
    fun addCallbackByTraceId(traceId: String, callback: T): Boolean {
        if (traceId.isBlank()) {
            CLog.i(tag, "TraceId is blank, skip adding callback")
            return false
        }
        
        val wrapper = CallbackWrapper(callback, System.currentTimeMillis())
        val existing = traceIdCallbackMap.putIfAbsent(traceId, wrapper)
        
        if (existing != null) {
            CLog.i(tag, "Callback with traceId=$traceId already exists, overwriting")
            traceIdCallbackMap[traceId] = wrapper
            return false
        }
        
        CLog.d(tag, "Callback added by traceId: $traceId")
        
        // 启动超时清理任务
        startTimeoutTaskForTraceId(traceId)
        
        return true
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
            if (System.currentTimeMillis() - wrapper.timestamp > timeoutMillis) {
                CLog.i(tag, "Callback expired by traceId: $traceId")
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
            CLog.d(tag, "Callback retrieved and removed by traceId: $traceId")
            return wrapper.callback
        }
        CLog.d(tag, "Callback not found by traceId: $traceId")
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
            CLog.d(tag, "Callback removed by traceId: $traceId")
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
        CLog.d(tag, "All callbacks cleared, requestId count=$count, traceId count=$traceIdCount")
    }

    /**
     * 清理超时的回调
     *
     * @return Int 清理的回调数量
     */
    fun clearExpired(): Int {
        val now = System.currentTimeMillis()
        val expiredIds = callbackMap.entries
            .filter { (_, wrapper) -> now - wrapper.timestamp > timeoutMillis }
            .map { it.key }
        
        expiredIds.forEach { callbackMap.remove(it) }
        
        if (expiredIds.isNotEmpty()) {
            CLog.d(tag, "Cleared ${expiredIds.size} expired callbacks")
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
     */
    private fun startTimeoutTask(requestId: Long) {
        scope.launch {
            delay(timeoutMillis)
            // 检查回调是否仍然存在且未使用
            val wrapper = callbackMap[requestId]
            if (wrapper != null) {
                val elapsed = System.currentTimeMillis() - wrapper.timestamp
                if (elapsed >= timeoutMillis) {
                    callbackMap.remove(requestId)
                    CLog.i(tag, "Callback timeout and removed: requestId=$requestId, elapsed=${elapsed}ms")
                }
            }
        }
    }
    
    /**
     * 启动超时清理任务（通过traceId）
     *
     * @param traceId 追踪ID
     */
    private fun startTimeoutTaskForTraceId(traceId: String) {
        scope.launch {
            delay(timeoutMillis)
            // 检查回调是否仍然存在且未使用
            val wrapper = traceIdCallbackMap[traceId]
            if (wrapper != null) {
                val elapsed = System.currentTimeMillis() - wrapper.timestamp
                if (elapsed >= timeoutMillis) {
                    traceIdCallbackMap.remove(traceId)
                    CLog.i(tag, "Callback timeout and removed by traceId: $traceId, elapsed=${elapsed}ms")
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
        CLog.d(tag, "LocalCallbackManager released")
    }

    /**
     * 回调包装类
     *
     * @param callback 回调对象
     * @param timestamp 创建时间戳
     */
    private data class CallbackWrapper<T>(
        val callback: T,
        val timestamp: Long
    )
}

