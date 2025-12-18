package com.sunmi.tapro.taplink.demo.activity

import android.app.AlertDialog
import android.app.ProgressDialog
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

    private lateinit var btnQueryTransaction: Button
    private lateinit var btnBatchClose: Button
    private lateinit var btnStandaloneRefund: Button
    private lateinit var lvTransactions: ListView
    private lateinit var layoutEmpty: LinearLayout
    
    private lateinit var adapter: TransactionAdapter
    private var transactions: List<Transaction> = emptyList()
    private lateinit var paymentService: TaplinkPaymentService

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
        btnQueryTransaction = findViewById(R.id.btn_query_transaction)
        btnBatchClose = findViewById(R.id.btn_batch_close)
        btnStandaloneRefund = findViewById(R.id.btn_standalone_refund)
        lvTransactions = findViewById(R.id.lv_transactions)
        layoutEmpty = findViewById(R.id.layout_empty)
        

        // Initialize adapter
        adapter = TransactionAdapter(this, transactions)
        lvTransactions.adapter = adapter
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
        btnQueryTransaction.setOnClickListener {
            showQueryTransactionDialog()
        }
        
        // Batch close button
        btnBatchClose.setOnClickListener {
            showBatchCloseDialog()
        }

        // Refund button
        btnStandaloneRefund.setOnClickListener {
            showRefundDialog()
        }
        
        // List item click event
        lvTransactions.onItemClickListener = AdapterView.OnItemClickListener { _, _, position, _ ->
            val transaction = adapter.getItem(position)
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
            lvTransactions.visibility = View.GONE
            layoutEmpty.visibility = View.VISIBLE
        } else {
            lvTransactions.visibility = View.VISIBLE
            layoutEmpty.visibility = View.GONE
        }
    }

    /**
     * Show query transaction dialog
     */
    private fun showQueryTransactionDialog() {
        if (!paymentService.isConnected()) {
            showToast("Not connected to payment terminal")
            return
        }

        val input = android.widget.EditText(this)
        input.hint = "Enter Transaction Request ID"
        input.inputType = android.text.InputType.TYPE_CLASS_TEXT

        AlertDialog.Builder(this)
            .setTitle("Query Transaction")
            .setMessage("Please enter the Transaction Request ID to query")
            .setView(input)
            .setPositiveButton("Query") { _, _ ->
                val transactionRequestId = input.text.toString().trim()
                if (transactionRequestId.isNotEmpty()) {
                    executeQuery(transactionRequestId)
                } else {
                    showToast("Please enter valid Transaction Request ID")
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /**
     * Execute query transaction
     */
    private fun executeQuery(transactionRequestId: String) {
        Log.d(TAG, "Executing query for transaction: $transactionRequestId")

        val progressDialog = ProgressDialog(this)
        progressDialog.setMessage("Querying transaction...")
        progressDialog.setCancelable(false)
        progressDialog.show()

        paymentService.executeQuery(
            transactionRequestId = transactionRequestId,
            callback = object : PaymentCallback {
                override fun onSuccess(result: PaymentResult) {
                    runOnUiThread {
                        progressDialog.dismiss()
                        showQueryResultDialog(result, transactionRequestId)
                    }
                }

                override fun onFailure(code: String, message: String) {
                    runOnUiThread {
                        progressDialog.dismiss()
                        showToast("Query failed: $message")
                    }
                }
            }
        )
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
                referenceOrderId = result.referenceOrderId ?: "UNKNOWN_ORDER_${System.currentTimeMillis()}",
                type = transactionType,
                amount = result.amount?.orderAmount ?: java.math.BigDecimal.ZERO,
                totalAmount = result.amount?.transAmount,
                currency = result.amount?.priceCurrency ?: "USD",
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
        if (!paymentService.isConnected()) {
            showToast("Not connected to payment terminal")
            return
        }

        AlertDialog.Builder(this)
            .setTitle("Batch Close")
            .setMessage("Are you sure you want to close the current batch? This will settle all transactions of the day.")
            .setPositiveButton("OK") { _, _ ->
                executeBatchClose()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /**
     * Show Refund confirmation dialog
     */
    private fun showRefundDialog() {
        if (!paymentService.isConnected()) {
            showToast("Not connected to payment terminal")
            return
        }

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
    }

    /**
     * Execute refund transaction
     */
    private fun executeRefund(amount: Double) {
        Log.d(TAG, "Executing refund transaction - Amount: $amount")

        // Generate transaction IDs first
        val transactionRequestId = generateTransactionRequestId()
        val referenceOrderId = generateOrderId()

        val progressDialog = ProgressDialog(this)
        progressDialog.setMessage("Processing refund...")
        progressDialog.setCancelable(true)
        progressDialog.show()

        // Create transaction record
        val newTransaction = Transaction(
            transactionRequestId = transactionRequestId,
            transactionId = null,
            referenceOrderId = referenceOrderId,
            type = TransactionType.REFUND,
            amount = java.math.BigDecimal.valueOf(amount),
            currency = "USD",
            status = TransactionStatus.PROCESSING,
            timestamp = System.currentTimeMillis()
        )
        TransactionRepository.addTransaction(newTransaction)

        paymentService.executeRefund(
            referenceOrderId = referenceOrderId,
            transactionRequestId = transactionRequestId,
            originalTransactionId = "", // 无参考号退款，不设置原始交易ID
            amount = java.math.BigDecimal.valueOf(amount),
            currency = "USD",
            description = "Refund without reference",
            reason = "Refund requested by merchant",
            callback = object : PaymentCallback {
                override fun onSuccess(result: PaymentResult) {
                    runOnUiThread {
                        progressDialog.dismiss()

                        // Update transaction status
                        val status = when (result.transactionStatus) {
                            "SUCCESS" -> TransactionStatus.SUCCESS
                            "FAILED" -> TransactionStatus.FAILED
                            "PROCESSING" -> TransactionStatus.PROCESSING
                            else -> TransactionStatus.FAILED
                        }

                        TransactionRepository.updateTransactionWithAmounts(
                            transactionRequestId = transactionRequestId,
                            status = status,
                            transactionId = result.transactionId,
                            authCode = result.authCode,
                            errorCode = if (status == TransactionStatus.FAILED) result.transactionResultCode else null,
                            errorMessage = if (status == TransactionStatus.FAILED) result.transactionResultMsg else null,
                            orderAmount = result.amount?.orderAmount,
                            totalAmount = result.amount?.transAmount,
                            surchargeAmount = result.amount?.surchargeAmount,
                            tipAmount = result.amount?.tipAmount,
                            cashbackAmount = result.amount?.cashbackAmount,
                            serviceFee = result.amount?.serviceFee
                        )

                        if (status == TransactionStatus.SUCCESS) {
                            showToast("Refund successful!")
                        } else {
                            showToast("Refund completed with status: ${result.transactionStatus}")
                        }
                        loadTransactions()
                    }
                }

                override fun onFailure(code: String, message: String) {
                    runOnUiThread {
                        progressDialog.dismiss()

                        // Update transaction status to failed
                        TransactionRepository.updateTransactionStatus(
                            transactionRequestId = transactionRequestId,
                            status = TransactionStatus.FAILED,
                            errorCode = code,
                            errorMessage = message
                        )

                        showToast("Refund failed: $message")
                        loadTransactions()
                    }
                }

                override fun onProgress(status: String, message: String) {
                    runOnUiThread {
                        progressDialog.setMessage(message)
                    }
                }
            }
        )
    }

    /**
     * Execute batch close
     */
    private fun executeBatchClose() {
        Log.d(TAG, "Executing batch close")

        val progressDialog = ProgressDialog(this)
        progressDialog.setMessage("Processing batch close...")
        progressDialog.setCancelable(true)
        progressDialog.show()

        val transactionRequestId = generateTransactionRequestId()
        val referenceOrderId = generateOrderId()

        // Create transaction record
        val newTransaction = Transaction(
            transactionRequestId = transactionRequestId,
            transactionId = null,
            referenceOrderId = referenceOrderId,
            type = TransactionType.BATCH_CLOSE,
            amount = java.math.BigDecimal.ZERO,
            currency = "USD",
            status = TransactionStatus.PROCESSING,
            timestamp = System.currentTimeMillis()
        )
        TransactionRepository.addTransaction(newTransaction)

        paymentService.executeBatchClose(
            referenceOrderId = referenceOrderId,
            transactionRequestId = transactionRequestId,
            description = "Batch close",
            callback = object : PaymentCallback {
                override fun onSuccess(result: PaymentResult) {
                    runOnUiThread {
                        progressDialog.dismiss()

                        // Update transaction status
                        val status = when (result.transactionStatus) {
                            "SUCCESS" -> TransactionStatus.SUCCESS
                            "FAILED" -> TransactionStatus.FAILED
                            "PROCESSING" -> TransactionStatus.PROCESSING
                            else -> TransactionStatus.FAILED
                        }
                        // Convert BatchCloseInfo from PaymentResult to Transaction model
                        val batchCloseInfo = result.batchCloseInfo?.let { bci ->
                            BatchCloseInfo(
                                totalCount = bci.totalCount,
                                totalAmount = bci.totalAmount,
                                totalTip = bci.totalTip,
                                totalTax = bci.totalTax,
                                totalSurchargeAmount = bci.totalSurchargeAmount,
                                cashDiscount = bci.cashDiscount,
                                closeTime = bci.closeTime
                            )
                        }
                        
                        TransactionRepository.updateTransactionWithAmounts(
                            transactionRequestId = transactionRequestId,
                            status = status,
                            totalAmount = result.batchCloseInfo?.totalAmount,
                            transactionId = result.transactionId,
                            authCode = result.authCode,
                            batchNo = result.batchNo,
                            batchCloseInfo = batchCloseInfo
                        )

                        if (status == TransactionStatus.SUCCESS) {
                            // Show success dialog with batch info
                            showBatchCloseSuccessDialog(result)
                            
                            // Clear all transactions after successful batch close
//                            clearAllTransactionsAfterBatchClose()
                        } else {
                            showToast("Batch close completed with status: ${result.transactionStatus}")
                            loadTransactions()
                        }
                    }
                }

                override fun onFailure(code: String, message: String) {
                    runOnUiThread {
                        progressDialog.dismiss()

                        // Update transaction status to failed
                        TransactionRepository.updateTransactionStatus(
                            transactionRequestId = transactionRequestId,
                            status = TransactionStatus.FAILED,
                            errorCode = code,
                            errorMessage = message
                        )

                        showToast("Batch close failed: $message")
                        loadTransactions()
                    }
                }
            }
        )
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
        return "TXN_REQ_${System.currentTimeMillis()}_${(1000..9999).random()}"
    }

    /**
     * Generate order ID
     */
    private fun generateOrderId(): String {
        return "ORD_${System.currentTimeMillis()}_${(1000..9999).random()}"
    }

    /**
     * Show Toast message
     */
    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    /**
     * Open transaction detail page
     */
    private fun openTransactionDetail(transaction: Transaction) {
        val intent = Intent(this, TransactionDetailActivity::class.java)
        intent.putExtra("transaction_request_id", transaction.transactionRequestId)
        startActivity(intent)
    }
}