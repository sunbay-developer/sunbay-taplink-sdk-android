package com.sunmi.tapro.taplink.communication.cable.aoa.manager

import com.sunmi.tapro.taplink.communication.cable.aoa.core.AoaSessionPhase
import com.sunmi.tapro.taplink.communication.enums.InnerConnectionStatus
import com.sunmi.tapro.taplink.communication.util.LogUtil

/**
 * AOA error handler
 * 
 * Responsible for handling various error situations during AOA connection process:
 * - Transfer error classification and handling
 * - Error recovery strategies
 * - Error statistics and analysis
 * 
 * Note: Reconnection logic is uniformly managed by SDK layer, here only responsible for error classification and local recovery
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
        private const val CONNECTION_GRACE_PERIOD_MS = 1000L // Disconnection within 1 second after connection may be caused by AOA switch
    }
    
    private var consecutiveErrors = 0
    private var totalErrors = 0
    private var lastErrorTime = 0L
    private var connectionEstablishedTime = 0L
    
    /**
     * Error type enumeration
     */
    enum class ErrorType {
        TRANSFER_ERROR,      // Transfer error (-1)
        TIMEOUT_ERROR,       // Timeout error (0)
        PARTIAL_TRANSFER,    // Partial transfer
        DEVICE_DISCONNECTED, // Device disconnected
        INTERFACE_ERROR,     // Interface error
        UNKNOWN_ERROR        // Unknown error
    }
    
    /**
     * Error handling result
     */
    data class ErrorHandlingResult(
        val shouldContinue: Boolean,
        val shouldBreak: Boolean,
        val delayMs: Long,
        val newStatus: InnerConnectionStatus?
    )
    
    /**
     * Handle data receive error
     * 
     * @param bytesRead Bytes read
     * @param isDeviceConnected Whether device is still connected
     * @param currentStatus Current connection status
     * @param sessionPhase Current session phase (optional, used to determine if in AOA switch period)
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
        
        // [Fix] During AOA protocol switch, device disconnection is normal, should not interrupt receive loop
        val isAoaSwitchPhase = sessionPhase == AoaSessionPhase.SENDING_AOA || 
                               sessionPhase == AoaSessionPhase.WAIT_AOA_ATTACH
        
        // [Fix] Within short time after connection establishment (grace period), device disconnection may be caused by AOA switch, should tolerate
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
                // Timeout is not an error, continue trying
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
                // Default handling for other error types
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
     * Handle data send error
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
                        delayMs = ERROR_RECOVERY_DELAY * 2, // Timeout errors have longer delay
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
                        newStatus = null // Partial transfer is not necessarily an error status
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
                // Default handling
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
     * Handle exception error
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
                    delayMs = CRITICAL_ERROR_DELAY, // Longer delay after exception
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
     * Classify receive error
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
     * Classify send error
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
     * Reset error count (called after successful operation)
     */
    fun resetErrorCount() {
        if (consecutiveErrors > 0) {
            LogUtil.d(TAG, "Resetting consecutive error count (was $consecutiveErrors)")
            consecutiveErrors = 0
        }
    }
    
    /**
     * Mark connection established (for grace period calculation)
     */
    fun markConnectionEstablished() {
        connectionEstablishedTime = System.currentTimeMillis()
        LogUtil.d(TAG, "Connection established, starting grace period")
    }
    
    /**
     * Reset connection time (called when disconnecting)
     */
    fun resetConnectionTime() {
        connectionEstablishedTime = 0L
    }
    
    /**
     * Get error statistics
     */
    fun getErrorStats(): String {
        return "Consecutive: $consecutiveErrors, Total: $totalErrors"
    }
    
    /**
     * Whether should attempt error recovery
     */
    fun shouldAttemptRecovery(): Boolean {
        val currentTime = System.currentTimeMillis()
        val timeSinceLastError = currentTime - lastErrorTime
        
        // If error frequency is too high, don't attempt recovery
        return timeSinceLastError > 1000 || consecutiveErrors < MAX_CONSECUTIVE_ERRORS
    }
    
    /**
     * Record error time
     */
    fun recordErrorTime() {
        lastErrorTime = System.currentTimeMillis()
    }
}