package com.sunmi.tapro.taplink.demo.util

import android.content.Context
import android.util.Log
import com.sunmi.tapro.taplink.demo.activity.RetryTransactionListener
import com.sunmi.tapro.taplink.demo.activity.TransactionProgressActivity
import com.sunmi.tapro.taplink.demo.model.Transaction
import com.sunmi.tapro.taplink.demo.model.TransactionType
import com.sunmi.tapro.taplink.demo.service.PaymentCallback
import com.sunmi.tapro.taplink.demo.service.PaymentService

/**
 * Helper class for handling transaction retry functionality
 * 
 * This class provides utilities for retrying failed transactions using the same
 * transaction request ID, which is useful for handling temporary failures like
 * network issues, timeouts, or other recoverable errors.
 * 
 * When a transaction fails, the TransactionProgressActivity will show both:
 * - Retry button: Allows user to retry the same transaction with the same request ID
 * - Return button: Allows user to return to the main screen without retrying
 * 
 * This gives users the flexibility to either retry failed transactions or
 * abandon them and return to the main interface.
 */
class TransactionRetryHelper(
    private val context: Context,
    private val paymentService: PaymentService
) : RetryTransactionListener {
    
    companion object {
        private const val TAG = "TransactionRetryHelper"
    }
    
    /**
     * Set up retry functionality for TransactionProgressActivity
     * Call this method after starting TransactionProgressActivity
     */
    fun setupRetryListener() {
        TransactionProgressActivity.setRetryTransactionListener(this)
        Log.d(TAG, "Retry listener set up")
    }
    
    /**
     * Clean up retry listener
     * Call this when no longer needed to prevent memory leaks
     */
    fun cleanup() {
        TransactionProgressActivity.setRetryTransactionListener(null)
        Log.d(TAG, "Retry listener cleaned up")
    }
    
    /**
     * Handle retry request from TransactionProgressActivity
     */
    override fun onRetryRequested(transaction: Transaction) {
        Log.d(TAG, "Retry requested for transaction: ${transaction.transactionRequestId}")
        
        // Get the callback from TransactionProgressActivity to forward results
        val callback = TransactionProgressActivity.getPaymentCallback()
        
        if (callback == null) {
            Log.e(TAG, "No callback available for retry")
            return
        }
        
        // Retry the transaction based on its type
        retryTransaction(transaction, callback)
    }
    
    /**
     * Retry a transaction using the same request ID
     */
    private fun retryTransaction(transaction: Transaction, callback: PaymentCallback) {
        Log.d(TAG, "Retrying ${transaction.type} transaction with ID: ${transaction.transactionRequestId}")
        
        try {
            when (transaction.type) {
                TransactionType.SALE -> {
                    paymentService.executeSale(
                        referenceOrderId = transaction.referenceOrderId ?: "",
                        transactionRequestId = transaction.transactionRequestId,
                        amount = transaction.amount,
                        currency = transaction.currency,
                        description = "Retry: Sale Transaction",
                        surchargeAmount = transaction.surchargeAmount,
                        tipAmount = transaction.tipAmount,
                        taxAmount = transaction.taxAmount,
                        cashbackAmount = transaction.cashbackAmount,
                        serviceFee = transaction.serviceFee,
                        staffInfo = null,
                        callback = callback
                    )
                }
                
                TransactionType.AUTH -> {
                    paymentService.executeAuth(
                        referenceOrderId = transaction.referenceOrderId ?: "",
                        transactionRequestId = transaction.transactionRequestId,
                        amount = transaction.amount,
                        currency = transaction.currency,
                        description = "Retry: Authorization Transaction",
                        callback = callback
                    )
                }
                
                TransactionType.FORCED_AUTH -> {
                    paymentService.executeForcedAuth(
                        referenceOrderId = transaction.referenceOrderId ?: "",
                        transactionRequestId = transaction.transactionRequestId,
                        amount = transaction.amount,
                        currency = transaction.currency,
                        description = "Retry: Forced Authorization Transaction",
                        tipAmount = transaction.tipAmount,
                        taxAmount = transaction.taxAmount,
                        callback = callback
                    )
                }
                
                TransactionType.REFUND -> {
                    // For refund retry, we need the original transaction ID
                    // This would need to be stored in the transaction object
                    Log.w(TAG, "Refund retry not fully implemented - needs original transaction ID")
                    callback.onFailure("RETRY_NOT_SUPPORTED", "Refund retry requires original transaction ID")
                }
                
                TransactionType.VOID -> {
                    // For void retry, we need the original transaction ID
                    Log.w(TAG, "Void retry not fully implemented - needs original transaction ID")
                    callback.onFailure("RETRY_NOT_SUPPORTED", "Void retry requires original transaction ID")
                }
                
                TransactionType.POST_AUTH -> {
                    // For post auth retry, we need the original transaction ID
                    Log.w(TAG, "Post auth retry not fully implemented - needs original transaction ID")
                    callback.onFailure("RETRY_NOT_SUPPORTED", "Post auth retry requires original transaction ID")
                }
                
                TransactionType.INCREMENT_AUTH -> {
                    // For incremental auth retry, we need the original transaction ID
                    Log.w(TAG, "Incremental auth retry not fully implemented - needs original transaction ID")
                    callback.onFailure("RETRY_NOT_SUPPORTED", "Incremental auth retry requires original transaction ID")
                }
                
                TransactionType.TIP_ADJUST -> {
                    // For tip adjust retry, we need the original transaction ID
                    Log.w(TAG, "Tip adjust retry not fully implemented - needs original transaction ID")
                    callback.onFailure("RETRY_NOT_SUPPORTED", "Tip adjust retry requires original transaction ID")
                }
                
                TransactionType.QUERY -> {
                    paymentService.executeQuery(
                        transactionRequestId = transaction.transactionRequestId,
                        callback = callback
                    )
                }
                
                TransactionType.BATCH_CLOSE -> {
                    paymentService.executeBatchClose(
                        transactionRequestId = transaction.transactionRequestId,
                        description = "Retry: Batch Close",
                        callback = callback
                    )
                }
            }
            
            Log.d(TAG, "Retry transaction initiated successfully")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error retrying transaction", e)
            callback.onFailure("RETRY_ERROR", "Failed to retry transaction: ${e.message}")
        }
    }
}