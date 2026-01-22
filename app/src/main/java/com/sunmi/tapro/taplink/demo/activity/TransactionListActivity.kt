package com.sunmi.tapro.taplink.demo.activity

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.sunmi.tapro.taplink.demo.R
import com.sunmi.tapro.taplink.demo.adapter.TransactionAdapter
import com.sunmi.tapro.taplink.demo.model.BatchCloseInfo
import com.sunmi.tapro.taplink.demo.model.Transaction
import com.sunmi.tapro.taplink.demo.model.TransactionStatus
import com.sunmi.tapro.taplink.demo.model.TransactionType
import com.sunmi.tapro.taplink.demo.repository.TransactionRepository
import com.sunmi.tapro.taplink.demo.service.TaplinkPaymentService
import com.sunmi.tapro.taplink.demo.service.PaymentCallback
import com.sunmi.tapro.taplink.demo.service.PaymentResult
import com.sunmi.tapro.taplink.demo.util.Constants


/**
 * Transaction List Page
 * 
 * Features:
 * - Display all transaction records
 * - Support clicking to view transaction details
 * - Support clearing all records
 * - Support returning to main page
 */
class TransactionListActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "TransactionListActivity"
    }

    private lateinit var queryTransactionButton: Button
    private lateinit var batchCloseButton: Button
    private lateinit var standaloneRefundButton: Button
    private lateinit var transactionsList: ListView
    private lateinit var emptyLayout: LinearLayout
    
    private lateinit var adapter: TransactionAdapter
    private var transactions: List<Transaction> = emptyList()
    private lateinit var paymentService: TaplinkPaymentService
    
    // Current alert dialog reference for proper cleanup
    private var currentAlertDialog: AlertDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_transaction_list)
        

        initViews()
        initPaymentService()
        initListeners()
        loadTransactions()
    }

    override fun onResume() {
        super.onResume()
        // Refresh data every time returning to the page (there may be new transaction records)
        loadTransactions()
    }



    /**
     * Initialize views
     */
    private fun initViews() {
        queryTransactionButton = findViewById(R.id.btn_query_transaction)
        batchCloseButton = findViewById(R.id.btn_batch_close)
        standaloneRefundButton = findViewById(R.id.btn_standalone_refund)
        transactionsList = findViewById(R.id.lv_transactions)
        emptyLayout = findViewById(R.id.layout_empty)
        

        // Initialize adapter
        adapter = TransactionAdapter(this, transactions)
        transactionsList.adapter = adapter
    }



    /**
     * Initialize payment service
     */
    private fun initPaymentService() {
        paymentService = TaplinkPaymentService.getInstance()
    }

    /**
     * Initialize event listeners
     */
    private fun initListeners() {
        // Query transaction button
        queryTransactionButton.setOnClickListener {
            showQueryTransactionDialog()
        }
        
        // Batch close button
        batchCloseButton.setOnClickListener {
            showBatchCloseDialog()
        }

        // Refund button
        standaloneRefundButton.setOnClickListener {
            showRefundDialog()
        }
        
        // List item click event
        transactionsList.onItemClickListener = AdapterView.OnItemClickListener { _, _, position, _ ->
            Log.d(TAG, "List item clicked at position: $position")
            val transaction = adapter.getItem(position)
            Log.d(TAG, "Transaction: ${transaction.transactionRequestId}, type: ${transaction.type}")
            openTransactionDetail(transaction)
        }
    }

    /**
     * Load transaction records
     */
    private fun loadTransactions() {
        transactions = TransactionRepository.getAllTransactions()
        adapter.updateData(transactions)
        updateEmptyState()
    }

    /**
     * Update empty state display
     */
    private fun updateEmptyState() {
        if (transactions.isEmpty()) {
            transactionsList.visibility = View.GONE
            emptyLayout.visibility = View.VISIBLE
        } else {
            transactionsList.visibility = View.VISIBLE
            emptyLayout.visibility = View.GONE
        }
    }

    /**
     * Show query transaction dialog
     */
    private fun showQueryTransactionDialog() {
        // Check if activity is still valid and not finishing
        if (isFinishing || isDestroyed) {
            Log.w(TAG, "Activity is finishing or destroyed, cannot show query dialog")
            return
        }
        
        try {
            // Dismiss any existing dialog first
            currentAlertDialog?.dismiss()

            val input = android.widget.EditText(this)
            input.hint = "Enter Transaction Request ID"
            input.inputType = android.text.InputType.TYPE_CLASS_TEXT

            currentAlertDialog = AlertDialog.Builder(this)
                .setTitle("Query Transaction")
                .setMessage("Please enter the Transaction Request ID to query")
                .setView(input)
                .setPositiveButton("Query") { dialog, _ ->
                    val transactionRequestId = input.text.toString().trim()
                    if (transactionRequestId.isNotEmpty()) {
                        dialog.dismiss()
                        currentAlertDialog = null
                        executeQuery(transactionRequestId)
                    } else {
                        showToast("Please enter valid Transaction Request ID")
                    }
                }
                .setNegativeButton("Cancel") { dialog, _ ->
                    dialog.dismiss()
                    currentAlertDialog = null
                }
                .setOnDismissListener {
                    currentAlertDialog = null
                }
                .show()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show query dialog", e)
        }
    }

    /**
     * Execute query transaction
     */
    private fun executeQuery(transactionRequestId: String) {
        Log.d(TAG, "Executing query for transaction: $transactionRequestId")

        // Create query transaction record
        val queryTransaction = Transaction(
            transactionRequestId = generateTransactionRequestId(),
            transactionId = null,
            referenceOrderId = transactionRequestId, // Use the queried transaction ID as reference
            type = TransactionType.QUERY,
            amount = java.math.BigDecimal.ZERO,
            currency = Constants.getDefaultCurrency(),
            status = TransactionStatus.PROCESSING,
            timestamp = System.currentTimeMillis()
        )
        
        // Add to repository
        TransactionRepository.addTransaction(queryTransaction)
        
        // Launch TransactionProgressActivity for unified progress display
        launchTransactionProgressActivity(queryTransaction)
    }

    /**
     * Show query result dialog
     */
    private fun showQueryResultDialog(result: PaymentResult, queriedRequestId: String) {
        val message = buildString {
//            append("Query Result:\n\n")
            append("Transaction Request ID: $queriedRequestId\n")
            append("Transaction ID: ${result.transactionId ?: "N/A"}\n")
            append("Status: ${result.transactionStatus ?: "Unknown"}\n")
            append("Type: ${result.transactionType ?: "N/A"}\n")
            
            if (result.amount?.orderAmount != null) {
                append("Amount: $${String.format("%.2f", result.amount.orderAmount)}\n")
            }
            
            if (result.transactionStatus == "SUCCESS") {
                append("Auth Code: ${result.authCode ?: "N/A"}\n")
                append("Complete Time: ${result.completeTime ?: "N/A"}\n")
            } else if (result.transactionStatus == "FAILED") {
                append("Error Code: ${result.transactionResultCode ?: "N/A"}\n")
                append("Error Message: ${result.transactionResultMsg ?: "N/A"}\n")
            }
            
            if (!result.description.isNullOrEmpty()) {
                append("\nDescription: ${result.description}")
            }
        }

        AlertDialog.Builder(this)
            .setTitle("Transaction Query Result")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .setNeutralButton("Update Local Record") { _, _ ->
                updateLocalTransactionFromQuery(result, queriedRequestId)
            }
            .show()
    }

    /**
     * Update local transaction record from query result
     */
    private fun updateLocalTransactionFromQuery(result: PaymentResult, queriedRequestId: String) {
        // Try to find the transaction in local repository
        val localTransaction = TransactionRepository.getTransactionByRequestId(queriedRequestId)
        
        if (localTransaction != null) {
            // Update existing transaction
            val status = when (result.transactionStatus) {
                "SUCCESS" -> TransactionStatus.SUCCESS
                "FAILED" -> TransactionStatus.FAILED
                "PROCESSING" -> TransactionStatus.PROCESSING
                else -> TransactionStatus.FAILED
            }
            
            TransactionRepository.updateTransactionWithAmounts(
                transactionRequestId = queriedRequestId,
                status = status,
                transactionId = result.transactionId,
                authCode = result.authCode,
                errorCode = if (status == TransactionStatus.FAILED) result.transactionResultCode else null,
                errorMessage = if (status == TransactionStatus.FAILED) result.transactionResultMsg else null,
                orderAmount = result.amount?.orderAmount,
                totalAmount = result.amount?.transAmount,
                surchargeAmount = result.amount?.surchargeAmount,
                tipAmount = result.amount?.tipAmount,
                taxAmount = result.amount?.taxAmount,
                cashbackAmount = result.amount?.cashbackAmount,
                serviceFee = result.amount?.serviceFee
            )
            
            showToast("Local transaction record updated")
            loadTransactions()
        } else {
            // Transaction not found in local records, add it to the list
            val status = when (result.transactionStatus) {
                "SUCCESS" -> TransactionStatus.SUCCESS
                "FAILED" -> TransactionStatus.FAILED
                "PROCESSING" -> TransactionStatus.PROCESSING
                else -> TransactionStatus.FAILED
            }
            
            val transactionType = when (result.transactionType) {
                "SALE" -> TransactionType.SALE
                "AUTH" -> TransactionType.AUTH
                "INCREMENT_AUTH" -> TransactionType.INCREMENT_AUTH
                "FORCED_AUTH" -> TransactionType.FORCED_AUTH
                "POST_AUTH" -> TransactionType.POST_AUTH
                "REFUND" -> TransactionType.REFUND
                "VOID" -> TransactionType.VOID
                "TIP_ADJUST" -> TransactionType.TIP_ADJUST
                "BATCH_CLOSE" -> TransactionType.BATCH_CLOSE
                else -> TransactionType.SALE // Default to SALE if unknown
            }
            
            val newTransaction = Transaction(
                transactionRequestId = queriedRequestId,
                transactionId = result.transactionId,
                referenceOrderId = result.referenceOrderId ?: "${Constants.ORDER_ID_PREFIX}UNKNOWN_${System.currentTimeMillis()}",
                type = transactionType,
                amount = result.amount?.orderAmount ?: java.math.BigDecimal.ZERO,
                totalAmount = result.amount?.transAmount,
                currency = result.amount?.priceCurrency ?: Constants.getDefaultCurrency(),
                status = status,
                timestamp = System.currentTimeMillis(),
                authCode = result.authCode,
                errorCode = if (status == TransactionStatus.FAILED) result.transactionResultCode else null,
                errorMessage = if (status == TransactionStatus.FAILED) result.transactionResultMsg else null,
                surchargeAmount = result.amount?.surchargeAmount,
                tipAmount = result.amount?.tipAmount,
                taxAmount = result.amount?.taxAmount,
                cashbackAmount = result.amount?.cashbackAmount
            )
            
            val addResult = TransactionRepository.addTransaction(newTransaction)
            if (addResult) {
                showToast("Transaction added to local records")
                loadTransactions()
            } else {
                showToast("Failed to add transaction to local records")
            }
        }
    }

    /**
     * Show batch close confirmation dialog
     */
    private fun showBatchCloseDialog() {
        // Check if activity is still valid and not finishing
        if (isFinishing || isDestroyed) {
            Log.w(TAG, "Activity is finishing or destroyed, cannot show batch close dialog")
            return
        }
        
        try {
            AlertDialog.Builder(this)
                .setTitle("Batch Close")
                .setMessage("Are you sure you want to close the current batch? This will settle all transactions of the day.")
                .setPositiveButton("OK") { _, _ ->
                    executeBatchClose()
                }
                .setNegativeButton("Cancel", null)
                .show()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show batch close dialog", e)
        }
    }

    /**
     * Show Refund confirmation dialog
     */
    private fun showRefundDialog() {
        // Check if activity is still valid and not finishing
        if (isFinishing || isDestroyed) {
            Log.w(TAG, "Activity is finishing or destroyed, cannot show refund dialog")
            return
        }
        
        try {
            val input = android.widget.EditText(this)
            input.hint = "Enter Refund Amount"
            input.inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL

            AlertDialog.Builder(this)
                .setTitle("Refund Transaction")
                .setMessage("Please enter the refund amount")
                .setView(input)
                .setPositiveButton("Refund") { _, _ ->
                    val amountStr = input.text.toString().trim()
                    if (amountStr.isNotEmpty()) {
                        val amount = amountStr.toDouble()
                        if (amount > 0) {
                            executeRefund(amount)
                        } else {
                            showToast("Please enter a valid refund amount")
                        }
                    } else {
                        showToast("Please enter refund amount")
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show refund dialog", e)
        }
    }

    /**
     * Execute refund transaction
     */
    private fun executeRefund(amount: Double) {
        Log.d(TAG, "Executing refund transaction - Amount: $amount")

        // Generate transaction IDs first
        val transactionRequestId = generateTransactionRequestId()
        val referenceOrderId = generateOrderId()

        // Create transaction record
        val newTransaction = Transaction(
            transactionRequestId = transactionRequestId,
            transactionId = null,
            referenceOrderId = referenceOrderId,
            type = TransactionType.REFUND,
            amount = java.math.BigDecimal.valueOf(amount),
            currency = Constants.getDefaultCurrency(),
            status = TransactionStatus.PROCESSING,
            timestamp = System.currentTimeMillis()
        )
        TransactionRepository.addTransaction(newTransaction)

        // Launch TransactionProgressActivity for unified progress display
        launchTransactionProgressActivity(newTransaction)
    }

    /**
     * Execute batch close
     */
    private fun executeBatchClose() {
        Log.d(TAG, "Executing batch close")

        val transactionRequestId = generateTransactionRequestId()

        // Create transaction record
        val newTransaction = Transaction(
            transactionRequestId = transactionRequestId,
            transactionId = null,
            type = TransactionType.BATCH_CLOSE,
            amount = java.math.BigDecimal.ZERO,
            currency = Constants.getDefaultCurrency(),
            status = TransactionStatus.PROCESSING,
            timestamp = System.currentTimeMillis()
        )
        TransactionRepository.addTransaction(newTransaction)

        // Launch TransactionProgressActivity for unified progress display
        launchTransactionProgressActivity(newTransaction)
    }

    /**
     * Show batch close success dialog
     */
    private fun showBatchCloseSuccessDialog(result: PaymentResult) {
        val message = buildString {
            append("Batch closed successfully!\n\n")
            
            result.batchCloseInfo?.let { info ->
                append("Total Count: ${info.totalCount}\n")
                append("Total Amount: $${String.format("%.2f", info.totalAmount)}\n")
                if (info.totalTip > java.math.BigDecimal.ZERO) {
                    append("Total Tip: $${String.format("%.2f", info.totalTip)}\n")
                }
                if (info.totalTax > java.math.BigDecimal.ZERO) {
                    append("Total Tax: $${String.format("%.2f", info.totalTax)}\n")
                }
                append("Close Time: ${info.closeTime}\n")
            } ?: run {
                append("Transaction ID: ${result.transactionId ?: "N/A"}\n")
            }

        }

        AlertDialog.Builder(this)
            .setTitle("Batch Close Successful")
            .setMessage(message)
            .setPositiveButton("OK") { _, _ ->
                // Refresh list after dialog dismissed
                loadTransactions()
            }
            .setCancelable(false)
            .show()
    }


    /**
     * Generate transaction request ID
     */
    private fun generateTransactionRequestId(): String {
        return "${Constants.TRANSACTION_REQUEST_ID_PREFIX}${System.currentTimeMillis()}_${(Constants.TRANSACTION_ID_MIN_RANDOM..Constants.TRANSACTION_ID_MAX_RANDOM).random()}"
    }

    /**
     * Generate order ID
     */
    private fun generateOrderId(): String {
        return "${Constants.STANDALONE_ORDER_PREFIX}${System.currentTimeMillis()}_${(Constants.TRANSACTION_ID_MIN_RANDOM..Constants.TRANSACTION_ID_MAX_RANDOM).random()}"
    }

    /**
     * Show Toast message
     */
    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        
        // Clean up any progress dialogs
        // ProgressDialog instances are created locally in methods and should be
        // properly dismissed in their respective callbacks
        
        // Dismiss any current alert dialog
        currentAlertDialog?.dismiss()
        currentAlertDialog = null
        
        // No specific cleanup needed as progress dialogs are managed locally
    }

    /**
     * Launch TransactionProgressActivity for unified progress display
     */
    private fun launchTransactionProgressActivity(transaction: Transaction) {
        val intent = TransactionProgressActivity.createIntent(this, transaction)
        startActivity(intent)
    }

    /**
     * Open transaction detail page
     */
    private fun openTransactionDetail(transaction: Transaction) {
        Log.d(TAG, "Opening transaction detail for: ${transaction.transactionRequestId}")
        try {
            val intent = Intent(this, TransactionDetailActivity::class.java)
            intent.putExtra("transaction_request_id", transaction.transactionRequestId)
            Log.d(TAG, "Starting TransactionDetailActivity with intent")
            startActivity(intent)
            Log.d(TAG, "TransactionDetailActivity started successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting TransactionDetailActivity", e)
            showToast("Error opening transaction detail: ${e.message}")
        }
    }
}