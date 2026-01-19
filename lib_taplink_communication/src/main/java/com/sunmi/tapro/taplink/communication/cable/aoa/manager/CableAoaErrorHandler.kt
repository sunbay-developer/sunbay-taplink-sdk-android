package com.sunmi.tapro.taplink.communication.cable.aoa.manager

import com.sunmi.tapro.taplink.communication.cable.aoa.core.AoaSessionPhase
import com.sunmi.tapro.taplink.communication.enums.InnerConnectionStatus
import com.sunmi.tapro.taplink.communication.util.LogUtil

/**
 * AOA错误处理器
 * 
 * 负责处理AOA连接过程中的各种错误情况：
 * - 传输错误分类和处理
 * - 错误恢复策略
 * - 错误统计和分析
 * 
 * 注意：重连逻辑由SDK层统一管理，这里只负责错误分类和本地恢复
 * 
 * @author TaPro Team
 * @since 2025-01-XX
 */
class CableAoaErrorHandler {
    
    companion object {
        private const val TAG = "AoaErrorHandler"
        private const val MAX_CONSECUTIVE_ERRORS = 5
        private const val ERROR_RECOVERY_DELAY = 100L
        private const val CRITICAL_ERROR_DELAY = 500L
        private const val CONNECTION_GRACE_PERIOD_MS = 1000L // 连接后1秒内的断开可能是AOA切换导致
    }
    
    private var consecutiveErrors = 0
    private var totalErrors = 0
    private var lastErrorTime = 0L
    private var connectionEstablishedTime = 0L
    
    /**
     * 错误类型枚举
     */
    enum class ErrorType {
        TRANSFER_ERROR,      // 传输错误 (-1)
        TIMEOUT_ERROR,       // 超时错误 (0)
        PARTIAL_TRANSFER,    // 部分传输
        DEVICE_DISCONNECTED, // 设备断开
        INTERFACE_ERROR,     // 接口错误
        UNKNOWN_ERROR        // 未知错误
    }
    
    /**
     * 错误处理结果
     */
    data class ErrorHandlingResult(
        val shouldContinue: Boolean,
        val shouldBreak: Boolean,
        val delayMs: Long,
        val newStatus: InnerConnectionStatus?
    )
    
    /**
     * 处理数据接收错误
     * 
     * @param bytesRead 读取的字节数
     * @param isDeviceConnected 设备是否仍然连接
     * @param currentStatus 当前连接状态
     * @param sessionPhase 当前会话阶段（可选，用于判断是否在AOA切换期）
     */
    suspend fun handleReceiveError(
        bytesRead: Int,
        isDeviceConnected: Boolean,
        currentStatus: InnerConnectionStatus,
        sessionPhase: AoaSessionPhase? = null
    ): ErrorHandlingResult {
        
        val errorType = classifyReceiveError(bytesRead, isDeviceConnected)
        totalErrors++
        
        LogUtil.w(TAG, "Handling receive error: type=$errorType, bytesRead=$bytesRead, " +
                "consecutiveErrors=$consecutiveErrors, totalErrors=$totalErrors, phase=$sessionPhase")
        
        // 【修复】AOA协议切换期间，设备断开是正常现象，不应该中断接收循环
        val isAoaSwitchPhase = sessionPhase == AoaSessionPhase.SENDING_AOA || 
                               sessionPhase == AoaSessionPhase.WAIT_AOA_ATTACH
        
        // 【修复】连接建立后的短时间内（grace period），设备断开可能是AOA切换导致，应该容忍
        val isWithinGracePeriod = connectionEstablishedTime > 0 && 
                                  (System.currentTimeMillis() - connectionEstablishedTime) < CONNECTION_GRACE_PERIOD_MS
        
        if ((isAoaSwitchPhase || isWithinGracePeriod) && errorType == ErrorType.DEVICE_DISCONNECTED) {
            val reason = if (isAoaSwitchPhase) "AOA switch phase" else "grace period"
            LogUtil.d(TAG, "Device disconnected during $reason - this may be expected, continuing...")
            return ErrorHandlingResult(
                shouldContinue = true,
                shouldBreak = false,
                delayMs = ERROR_RECOVERY_DELAY,
                newStatus = null
            )
        }
        
        return when (errorType) {
            ErrorType.TIMEOUT_ERROR -> {
                // 超时不算错误，继续尝试
                ErrorHandlingResult(
                    shouldContinue = true,
                    shouldBreak = false,
                    delayMs = 0,
                    newStatus = null
                )
            }
            
            ErrorType.TRANSFER_ERROR -> {
                consecutiveErrors++
                
                if (!isDeviceConnected) {
                    LogUtil.w(TAG, "Transfer error and device disconnected")
                    ErrorHandlingResult(
                        shouldContinue = false,
                        shouldBreak = true,
                        delayMs = 0,
                        newStatus = InnerConnectionStatus.DISCONNECTED
                    )
                } else if (consecutiveErrors >= MAX_CONSECUTIVE_ERRORS) {
                    LogUtil.e(TAG, "Too many consecutive transfer errors ($consecutiveErrors)")
                    ErrorHandlingResult(
                        shouldContinue = false,
                        shouldBreak = true,
                        delayMs = 0,
                        newStatus = InnerConnectionStatus.ERROR
                    )
                } else {
                    LogUtil.d(TAG, "Transfer error, retrying after delay")
                    ErrorHandlingResult(
                        shouldContinue = true,
                        shouldBreak = false,
                        delayMs = ERROR_RECOVERY_DELAY,
                        newStatus = null
                    )
                }
            }
            
            ErrorType.DEVICE_DISCONNECTED -> {
                LogUtil.w(TAG, "Device disconnected error")
                ErrorHandlingResult(
                    shouldContinue = false,
                    shouldBreak = true,
                    delayMs = 0,
                    newStatus = InnerConnectionStatus.DISCONNECTED
                )
            }
            
            ErrorType.UNKNOWN_ERROR -> {
                consecutiveErrors++
                
                if (consecutiveErrors >= MAX_CONSECUTIVE_ERRORS) {
                    LogUtil.e(TAG, "Too many consecutive unknown errors ($consecutiveErrors)")
                    ErrorHandlingResult(
                        shouldContinue = false,
                        shouldBreak = true,
                        delayMs = 0,
                        newStatus = InnerConnectionStatus.ERROR
                    )
                } else {
                    LogUtil.w(TAG, "Unknown error, retrying after delay")
                    ErrorHandlingResult(
                        shouldContinue = true,
                        shouldBreak = false,
                        delayMs = ERROR_RECOVERY_DELAY,
                        newStatus = null
                    )
                }
            }
            
            else -> {
                // 其他错误类型的默认处理
                consecutiveErrors++
                ErrorHandlingResult(
                    shouldContinue = consecutiveErrors < MAX_CONSECUTIVE_ERRORS,
                    shouldBreak = consecutiveErrors >= MAX_CONSECUTIVE_ERRORS,
                    delayMs = ERROR_RECOVERY_DELAY,
                    newStatus = if (consecutiveErrors >= MAX_CONSECUTIVE_ERRORS) InnerConnectionStatus.ERROR else null
                )
            }
        }
    }
    
    /**
     * 处理数据发送错误
     */
    fun handleSendError(
        result: Int,
        expectedSize: Int,
        attempt: Int,
        maxAttempts: Int,
        isDeviceConnected: Boolean
    ): ErrorHandlingResult {
        
        val errorType = classifySendError(result, expectedSize, isDeviceConnected)
        totalErrors++
        
        LogUtil.w(TAG, "Handling send error: type=$errorType, result=$result, " +
                "expected=$expectedSize, attempt=${attempt + 1}/$maxAttempts")
        
        return when (errorType) {
            ErrorType.TRANSFER_ERROR -> {
                if (!isDeviceConnected) {
                    LogUtil.w(TAG, "Send transfer error and device disconnected")
                    ErrorHandlingResult(
                        shouldContinue = false,
                        shouldBreak = true,
                        delayMs = 0,
                        newStatus = InnerConnectionStatus.DISCONNECTED
                    )
                } else if (attempt >= maxAttempts - 1) {
                    LogUtil.e(TAG, "Send failed after $maxAttempts attempts")
                    ErrorHandlingResult(
                        shouldContinue = false,
                        shouldBreak = true,
                        delayMs = 0,
                        newStatus = InnerConnectionStatus.ERROR
                    )
                } else {
                    LogUtil.d(TAG, "Send transfer error, retrying")
                    ErrorHandlingResult(
                        shouldContinue = true,
                        shouldBreak = false,
                        delayMs = ERROR_RECOVERY_DELAY,
                        newStatus = null
                    )
                }
            }
            
            ErrorType.TIMEOUT_ERROR -> {
                if (attempt >= maxAttempts - 1) {
                    LogUtil.e(TAG, "Send timeout after $maxAttempts attempts")
                    ErrorHandlingResult(
                        shouldContinue = false,
                        shouldBreak = true,
                        delayMs = 0,
                        newStatus = InnerConnectionStatus.ERROR
                    )
                } else {
                    LogUtil.w(TAG, "Send timeout, retrying")
                    ErrorHandlingResult(
                        shouldContinue = true,
                        shouldBreak = false,
                        delayMs = ERROR_RECOVERY_DELAY * 2, // 超时错误延迟更长
                        newStatus = null
                    )
                }
            }
            
            ErrorType.PARTIAL_TRANSFER -> {
                if (attempt >= maxAttempts - 1) {
                    LogUtil.w(TAG, "Partial transfer after $maxAttempts attempts")
                    ErrorHandlingResult(
                        shouldContinue = false,
                        shouldBreak = true,
                        delayMs = 0,
                        newStatus = null // 部分传输不一定是错误状态
                    )
                } else {
                    LogUtil.d(TAG, "Partial transfer, retrying")
                    ErrorHandlingResult(
                        shouldContinue = true,
                        shouldBreak = false,
                        delayMs = ERROR_RECOVERY_DELAY,
                        newStatus = null
                    )
                }
            }
            
            else -> {
                // 默认处理
                ErrorHandlingResult(
                    shouldContinue = attempt < maxAttempts - 1,
                    shouldBreak = attempt >= maxAttempts - 1,
                    delayMs = ERROR_RECOVERY_DELAY,
                    newStatus = if (attempt >= maxAttempts - 1) InnerConnectionStatus.ERROR else null
                )
            }
        }
    }
    
    /**
     * 处理异常错误
     */
    suspend fun handleException(
        exception: Exception,
        currentStatus: InnerConnectionStatus,
        isDeviceConnected: Boolean
    ): ErrorHandlingResult {
        
        consecutiveErrors++
        totalErrors++
        
        LogUtil.e(TAG, "Handling exception: ${exception.javaClass.simpleName}: ${exception.message}")
        LogUtil.e(TAG, "Consecutive errors: $consecutiveErrors, total errors: $totalErrors")
        
        return if (currentStatus == InnerConnectionStatus.CONNECTED) {
            if (consecutiveErrors >= MAX_CONSECUTIVE_ERRORS) {
                LogUtil.e(TAG, "Too many consecutive exceptions, marking as error")
                ErrorHandlingResult(
                    shouldContinue = false,
                    shouldBreak = true,
                    delayMs = 0,
                    newStatus = InnerConnectionStatus.ERROR
                )
            } else {
                LogUtil.d(TAG, "Exception during connected state, retrying")
                ErrorHandlingResult(
                    shouldContinue = true,
                    shouldBreak = false,
                    delayMs = CRITICAL_ERROR_DELAY, // 异常后延迟更长
                    newStatus = null
                )
            }
        } else {
            LogUtil.d(TAG, "Exception during disconnect, this is expected")
            ErrorHandlingResult(
                shouldContinue = false,
                shouldBreak = true,
                delayMs = 0,
                newStatus = null
            )
        }
    }
    
    /**
     * 分类接收错误
     */
    private fun classifyReceiveError(bytesRead: Int, isDeviceConnected: Boolean): ErrorType {
        return when {
            bytesRead == 0 -> ErrorType.TIMEOUT_ERROR
            bytesRead == -1 && !isDeviceConnected -> ErrorType.DEVICE_DISCONNECTED
            bytesRead == -1 -> ErrorType.TRANSFER_ERROR
            bytesRead < -1 -> ErrorType.UNKNOWN_ERROR
            else -> ErrorType.UNKNOWN_ERROR
        }
    }
    
    /**
     * 分类发送错误
     */
    private fun classifySendError(result: Int, expectedSize: Int, isDeviceConnected: Boolean): ErrorType {
        return when {
            result == 0 -> ErrorType.TIMEOUT_ERROR
            result == -1 && !isDeviceConnected -> ErrorType.DEVICE_DISCONNECTED
            result == -1 -> ErrorType.TRANSFER_ERROR
            result > 0 && result < expectedSize -> ErrorType.PARTIAL_TRANSFER
            result < 0 -> ErrorType.UNKNOWN_ERROR
            else -> ErrorType.UNKNOWN_ERROR
        }
    }
    
    /**
     * 重置错误计数（成功操作后调用）
     */
    fun resetErrorCount() {
        if (consecutiveErrors > 0) {
            LogUtil.d(TAG, "Resetting consecutive error count (was $consecutiveErrors)")
            consecutiveErrors = 0
        }
    }
    
    /**
     * 标记连接已建立（用于grace period计算）
     */
    fun markConnectionEstablished() {
        connectionEstablishedTime = System.currentTimeMillis()
        LogUtil.d(TAG, "Connection established, starting grace period")
    }
    
    /**
     * 重置连接时间（断开连接时调用）
     */
    fun resetConnectionTime() {
        connectionEstablishedTime = 0L
    }
    
    /**
     * 获取错误统计
     */
    fun getErrorStats(): String {
        return "Consecutive: $consecutiveErrors, Total: $totalErrors"
    }
    
    /**
     * 是否应该进行错误恢复
     */
    fun shouldAttemptRecovery(): Boolean {
        val currentTime = System.currentTimeMillis()
        val timeSinceLastError = currentTime - lastErrorTime
        
        // 如果错误频率太高，不尝试恢复
        return timeSinceLastError > 1000 || consecutiveErrors < MAX_CONSECUTIVE_ERRORS
    }
    
    /**
     * 记录错误时间
     */
    fun recordErrorTime() {
        lastErrorTime = System.currentTimeMillis()
    }
}