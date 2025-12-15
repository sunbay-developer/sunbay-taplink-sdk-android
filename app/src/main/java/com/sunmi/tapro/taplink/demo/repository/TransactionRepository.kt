package com.sunmi.tapro.taplink.demo.repository

import com.sunmi.tapro.taplink.demo.model.BatchCloseInfo
import com.sunmi.tapro.taplink.demo.model.Transaction
import com.sunmi.tapro.taplink.demo.model.TransactionStatus
import com.sunmi.tapro.taplink.demo.model.TransactionType

/**
 * Transaction Repository (Singleton)
 * 
 * Responsible for managing CRUD operations for transaction records, using in-memory storage
 * 
 * Features:
 * - Add new transaction records
 * - Update transaction records
 * - Delete transaction records
 * - Query transaction records (by ID, order number, type, etc.)
 * - Get all transaction records
 * 
 * Note:
 * - Currently uses in-memory storage, data will be lost when app restarts
 * - Can be extended to persistent storage later (SharedPreferences, database, etc.)
 */
object TransactionRepository {
    
    /**
     * Transaction records list (in-memory storage)
     * Using MutableList to store, sorted in reverse chronological order (newest first)
     */
    private val transactions = mutableListOf<Transaction>()
    
    /**
     * Add transaction record
     * 
     * @param transaction Transaction record to add
     * @return Returns true if added successfully, false if transactionRequestId already exists
     */
    fun addTransaction(transaction: Transaction): Boolean {
        // Check if transactionRequestId already exists
        if (transactions.any { it.transactionRequestId == transaction.transactionRequestId }) {
            return false
        }
        
        // Add to the beginning of the list (maintain reverse chronological order)
        transactions.add(0, transaction)
        return true
    }
    
    /**
     * Update transaction record
     * 
     * @param transactionRequestId Transaction request ID to update
     * @param updater Update function that receives the old transaction and returns the new one
     * @return Returns true if update successful, false if record not found
     */
    fun updateTransaction(
        transactionRequestId: String,
        updater: (Transaction) -> Transaction
    ): Boolean {
        val index = transactions.indexOfFirst { it.transactionRequestId == transactionRequestId }
        if (index == -1) {
            return false
        }
        
        val oldTransaction = transactions[index]
        val newTransaction = updater(oldTransaction)
        transactions[index] = newTransaction
        return true
    }
    
    /**
     * Update transaction status
     * 
     * @param transactionRequestId Transaction request ID
     * @param status New status
     * @param transactionId Nexus transaction serial number (optional)
     * @param authCode Authorization code (optional)
     * @param errorCode Error code (optional)
     * @param errorMessage Error message (optional)
     * @return Returns true if update successful, false if record not found
     */
    fun updateTransactionStatus(
        transactionRequestId: String,
        status: TransactionStatus,
        transactionId: String? = null,
        authCode: String? = null,
        errorCode: String? = null,
        errorMessage: String? = null
    ): Boolean {
        return updateTransaction(transactionRequestId) { transaction ->
            transaction.copy(
                status = status,
                transactionId = transactionId ?: transaction.transactionId,
                authCode = authCode ?: transaction.authCode,
                errorCode = errorCode ?: transaction.errorCode,
                errorMessage = errorMessage ?: transaction.errorMessage
            )
        }
    }
    
    /**
     * Update transaction with complete amount information from SDK result
     * 
     * @param transactionRequestId Transaction request ID
     * @param status Transaction status
     * @param transactionId Nexus transaction serial number (optional)
     * @param authCode Authorization code (optional)
     * @param errorCode Error code (optional)
     * @param errorMessage Error message (optional)
     * @param orderAmount Order base amount from SDK (optional)
     * @param totalAmount Total transaction amount from SDK (optional)
     * @param surchargeAmount Surcharge amount from SDK (optional)
     * @param tipAmount Tip amount from SDK (optional)
     * @param cashbackAmount Cashback amount from SDK (optional)
     * @param batchNo Batch number (optional, for BATCH_CLOSE)
     * @param batchCloseInfo Batch close information (optional, for BATCH_CLOSE)
     * @return Returns true if update successful, false if record not found
     */
    fun updateTransactionWithAmounts(
        transactionRequestId: String,
        status: TransactionStatus,
        transactionId: String? = null,
        authCode: String? = null,
        errorCode: String? = null,
        errorMessage: String? = null,
        orderAmount: Double? = null,
        totalAmount: Double? = null,
        surchargeAmount: Double? = null,
        tipAmount: Double? = null,
        taxAmount: Double? = null,
        cashbackAmount: Double? = null,
        serviceFee: Double? = null,
        batchNo: Int? = null,
        batchCloseInfo: BatchCloseInfo? = null
    ): Boolean {
        return updateTransaction(transactionRequestId) { transaction ->
            transaction.copy(
                status = status,
                transactionId = transactionId ?: transaction.transactionId,
                authCode = authCode ?: transaction.authCode,
                errorCode = errorCode ?: transaction.errorCode,
                errorMessage = errorMessage ?: transaction.errorMessage,
                // For BatchClose transactions, keep original amount if SDK doesn't return amount info
                amount = if (orderAmount != null) orderAmount else transaction.amount,
                totalAmount = if (totalAmount != null) totalAmount else transaction.totalAmount,
                surchargeAmount = if (surchargeAmount != null) surchargeAmount else transaction.surchargeAmount,
                tipAmount = if (tipAmount != null) tipAmount else transaction.tipAmount,
                taxAmount = if (taxAmount != null) taxAmount else transaction.taxAmount,
                cashbackAmount = if (cashbackAmount != null) cashbackAmount else transaction.cashbackAmount,
                serviceFee = if (serviceFee != null) serviceFee else transaction.serviceFee,
                batchNo = batchNo ?: transaction.batchNo,
                batchCloseInfo = batchCloseInfo ?: transaction.batchCloseInfo
            )
        }
    }
    
    /**
     * Delete transaction record
     * 
     * @param transactionRequestId Transaction request ID to delete
     * @return Returns true if deletion successful, false if record not found
     */
    fun deleteTransaction(transactionRequestId: String): Boolean {
        val index = transactions.indexOfFirst { it.transactionRequestId == transactionRequestId }
        if (index == -1) {
            return false
        }
        
        transactions.removeAt(index)
        return true
    }
    
    /**
     * Query transaction by transaction request ID
     * 
     * @param transactionRequestId Transaction request ID to query
     * @return Found transaction record, or null if not found
     */
    fun getTransactionByRequestId(transactionRequestId: String): Transaction? {
        return transactions.find { it.transactionRequestId == transactionRequestId }
    }
    
    /**
     * Query transaction by Nexus transaction ID
     * 
     * @param transactionId Nexus transaction serial number
     * @return Found transaction record, or null if not found
     */
    fun getTransactionById(transactionId: String): Transaction? {
        return transactions.find { it.transactionId == transactionId }
    }
    
    /**
     * Query all transactions by order number
     * 
     * @param referenceOrderId Reference order ID
     * @return All transaction records for the order (reverse chronological order)
     */
    fun getTransactionsByOrderId(referenceOrderId: String): List<Transaction> {
        return transactions.filter { it.referenceOrderId == referenceOrderId }
    }
    
    /**
     * Query transactions by type
     * 
     * @param type Transaction type
     * @return All transactions of the type (reverse chronological order)
     */
    fun getTransactionsByType(type: TransactionType): List<Transaction> {
        return transactions.filter { it.type == type }
    }
    
    /**
     * Query transactions by status
     * 
     * @param status Transaction status
     * @return All transactions with the status (reverse chronological order)
     */
    fun getTransactionsByStatus(status: TransactionStatus): List<Transaction> {
        return transactions.filter { it.status == status }
    }
    
    /**
     * Get all transaction records
     * 
     * @return All transaction records (reverse chronological order)
     */
    fun getAllTransactions(): List<Transaction> {
        return transactions.toList()
    }
    
    /**
     * Get successful transaction records
     * 
     * @return All successful transaction records (reverse chronological order)
     */
    fun getSuccessfulTransactions(): List<Transaction> {
        return transactions.filter { it.status == TransactionStatus.SUCCESS }
    }
    
    /**
     * Get failed transaction records
     * 
     * @return All failed transaction records (reverse chronological order)
     */
    fun getFailedTransactions(): List<Transaction> {
        return transactions.filter { it.status == TransactionStatus.FAILED }
    }
    
    /**
     * Get transaction count
     * 
     * @return Total number of transaction records
     */
    fun getTransactionCount(): Int {
        return transactions.size
    }
    
    /**
     * Clear all transaction records
     */
    fun clearAllTransactions() {
        transactions.clear()
    }
    
    /**
     * Check if transaction request ID exists (for idempotency check)
     * 
     * @param transactionRequestId Transaction request ID
     * @return True if exists, false otherwise
     */
    fun isTransactionRequestIdExists(transactionRequestId: String): Boolean {
        return transactions.any { it.transactionRequestId == transactionRequestId }
    }
}
