package com.sunmi.tapro.taplink.demo.activity

import android.app.AlertDialog
import android.app.ProgressDialog
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.sunmi.tapro.taplink.demo.R
import com.sunmi.tapro.taplink.demo.model.Transaction
import com.sunmi.tapro.taplink.demo.model.TransactionStatus
import com.sunmi.tapro.taplink.demo.model.TransactionType
import com.sunmi.tapro.taplink.demo.repository.TransactionRepository
import com.sunmi.tapro.taplink.demo.service.TaplinkPaymentService
import com.sunmi.tapro.taplink.demo.service.PaymentCallback
import com.sunmi.tapro.taplink.demo.service.PaymentResult
import com.sunmi.tapro.taplink.demo.util.Constants

import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Transaction Detail Page
 * 
 * Features:
 * - Display transaction details
 * - Show available operations based on transaction type and status
 * - Implement refund, void, inquiry and other follow-up operations
 * - Implement tip adjustment, pre-authorization completion and other functions
 */
class TransactionDetailActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "TransactionDetailActivity"
        private const val PROGRESS_DIALOG_TIMEOUT = 30000L // Use direct value to avoid circular dependency
    }

    // UI
    private lateinit var transactionTypeText: TextView
    private lateinit var statusText: TextView
    private lateinit var totalAmountText: TextView
    private lateinit var orderAmountLayout: LinearLayout
    private lateinit var orderAmountText: TextView
    private lateinit var surchargeAmountLayout: LinearLayout
    private lateinit var surchargeAmountText: TextView
    private lateinit var tipAmountLayout: LinearLayout
    private lateinit var tipAmountText: TextView
    private lateinit var cashbackAmountLayout: LinearLayout
    private lateinit var cashbackAmountText: TextView
    private lateinit var serviceFeeLayout: LinearLayout
    private lateinit var serviceFeeText: TextView
    private lateinit var taxAmountLayout: LinearLayout
    private lateinit var taxAmountText: TextView
    private lateinit var orderIdText: TextView
    private lateinit var transactionIdText: TextView
    private lateinit var originalTransactionIdLayout: LinearLayout
    private lateinit var originalTransactionIdText: TextView
    private lateinit var transactionTimeText: TextView
    private lateinit var authCodeLayout: LinearLayout
    private lateinit var authCodeText: TextView
    private lateinit var errorLayout: LinearLayout
    private lateinit var errorCodeText: TextView
    private lateinit var errorMessageText: TextView
    private lateinit var batchCloseInfoLayout: LinearLayout
    private lateinit var batchNoText: TextView
    private lateinit var batchTotalCountText: TextView
    private lateinit var batchTotalAmountText: TextView
    private lateinit var batchTotalTipLayout: LinearLayout
    private lateinit var batchTotalTipText: TextView
    private lateinit var batchTotalSurchargeLayout: LinearLayout
    private lateinit var batchTotalSurchargeText: TextView
    private lateinit var batchCloseTimeText: TextView
    private lateinit var operationsLayout: LinearLayout
    private lateinit var refundButton: Button
    private lateinit var voidButton: Button
    private lateinit var tipAdjustButton: Button
    private lateinit var incrementalAuthButton: Button
    private lateinit var postAuthButton: Button
    private lateinit var queryByRequestIdButton: Button
    private lateinit var queryByTransactionIdButton: Button
    private lateinit var noOperationsText: TextView

    // Data
    private var transaction: Transaction? = null
    private lateinit var paymentService: TaplinkPaymentService
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    
    // Current alert dialog reference for proper cleanup
    private var currentAlertDialog: AlertDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_transaction_detail)

        initViews()
        initPaymentService()
        loadTransaction()
        initListeners()
    }



    /**
     * Initialize views
     */
    private fun initViews() {
        transactionTypeText = findViewById(R.id.tv_transaction_type)
        statusText = findViewById(R.id.tv_status)
        totalAmountText = findViewById(R.id.tv_total_amount)
        orderAmountLayout = findViewById(R.id.layout_order_amount)
        orderAmountText = findViewById(R.id.tv_order_amount)
        surchargeAmountLayout = findViewById(R.id.layout_surcharge_amount)
        surchargeAmountText = findViewById(R.id.tv_surcharge_amount)
        tipAmountLayout = findViewById(R.id.layout_tip_amount)
        tipAmountText = findViewById(R.id.tv_tip_amount)
        cashbackAmountLayout = findViewById(R.id.layout_cashback_amount)
        cashbackAmountText = findViewById(R.id.tv_cashback_amount)
        serviceFeeLayout = findViewById(R.id.layout_service_fee)
        serviceFeeText = findViewById(R.id.tv_service_fee)
        taxAmountLayout = findViewById(R.id.layout_tax_amount)
        taxAmountText = findViewById(R.id.tv_tax_amount)
        orderIdText = findViewById(R.id.tv_order_id)
        transactionIdText = findViewById(R.id.tv_transaction_id)
        originalTransactionIdLayout = findViewById(R.id.layout_original_transaction_id)
        originalTransactionIdText = findViewById(R.id.tv_original_transaction_id)
        transactionTimeText = findViewById(R.id.tv_transaction_time)
        authCodeLayout = findViewById(R.id.layout_auth_code)
        authCodeText = findViewById(R.id.tv_auth_code)
        errorLayout = findViewById(R.id.layout_error)
        errorCodeText = findViewById(R.id.tv_error_code)
        errorMessageText = findViewById(R.id.tv_error_message)
        batchCloseInfoLayout = findViewById(R.id.layout_batch_close_info)
        batchNoText = findViewById(R.id.tv_batch_no)
        batchTotalCountText = findViewById(R.id.tv_batch_total_count)
        batchTotalAmountText = findViewById(R.id.tv_batch_total_amount)
        batchTotalTipLayout = findViewById(R.id.layout_batch_total_tip)
        batchTotalTipText = findViewById(R.id.tv_batch_total_tip)
        batchTotalSurchargeLayout = findViewById(R.id.layout_batch_total_surcharge)
        batchTotalSurchargeText = findViewById(R.id.tv_batch_total_surcharge)
        batchCloseTimeText = findViewById(R.id.tv_batch_close_time)
        operationsLayout = findViewById(R.id.layout_operations)
        refundButton = findViewById(R.id.btn_refund)
        voidButton = findViewById(R.id.btn_void)
        tipAdjustButton = findViewById(R.id.btn_tip_adjust)
        incrementalAuthButton = findViewById(R.id.btn_incremental_auth)
        postAuthButton = findViewById(R.id.btn_post_auth)
        queryByRequestIdButton = findViewById(R.id.btn_query_by_request_id)
        queryByTransactionIdButton = findViewById(R.id.btn_query_by_transaction_id)
        noOperationsText = findViewById(R.id.tv_no_operations)
    }



    /**
     * Initialize payment service
     */
    private fun initPaymentService() {
        paymentService = TaplinkPaymentService.getInstance()
    }

    /**
     * Load transaction data
     */
    private fun loadTransaction() {
        val transactionRequestId = intent.getStringExtra("transaction_request_id")
        if (transactionRequestId == null) {
            showToast("Transaction ID cannot be empty")
            finish()
            return
        }

        transaction = TransactionRepository.getTransactionByRequestId(transactionRequestId)
        if (transaction == null) {
            showToast("Transaction record not found")
            finish()
            return
        }

        displayTransactionInfo()
        setupOperationButtons()
    }

    /**
     * Display transaction information
     */
    private fun displayTransactionInfo() {
        val txn = transaction ?: return

        // basic information
        transactionTypeText.text = txn.getDisplayName()
        statusText.text = txn.getStatusDisplayName()
        statusText.setTextColor(getStatusColor(txn.status))
        
        // For batch close transactions, display batchCloseInfo totalAmount; for others, display total amount (transAmount) if available, otherwise display base amount
        if (txn.type == TransactionType.BATCH_CLOSE && txn.batchCloseInfo != null) {
            // For batch close, show batch total amount
            totalAmountText.text = String.format("$%.2f", txn.batchCloseInfo.totalAmount)
            // Hide order amount for batch close
            orderAmountLayout.visibility = View.GONE
        } else {
            // For regular transactions
            val displayTotalAmount = txn.totalAmount ?: txn.amount
            totalAmountText.text = String.format("$%.2f", displayTotalAmount)
            
            // Show order amount separately
            orderAmountLayout.visibility = View.VISIBLE
            orderAmountText.text = String.format("$%.2f", txn.amount)
        }
        
        // Display order base amount (orderAmount) separately if different from total
//        if (txn.totalAmount != null && txn.totalAmount != txn.amount) {
//            orderAmountLayout.visibility = View.VISIBLE
//            orderAmountText.text = String.format("$%.2f", txn.amount)
//        } else {
//            orderAmountLayout.visibility = View.GONE
//        }
        
        orderIdText.text = txn.referenceOrderId
        transactionIdText.text = txn.transactionId ?: txn.transactionRequestId
        transactionTimeText.text = dateFormat.format(Date(txn.timestamp))

        // Additional amounts (only show for non-batch-close transactions if they exist and are greater than 0)
        if (txn.type != TransactionType.BATCH_CLOSE) {
            if (txn.surchargeAmount != null && txn.surchargeAmount > BigDecimal.ZERO) {
                surchargeAmountLayout.visibility = View.VISIBLE
                surchargeAmountText.text = String.format("$%.2f", txn.surchargeAmount)
            } else {
                surchargeAmountLayout.visibility = View.GONE
            }

            if (txn.tipAmount != null && txn.tipAmount > BigDecimal.ZERO) {
                tipAmountLayout.visibility = View.VISIBLE
                tipAmountText.text = String.format("$%.2f", txn.tipAmount)
            } else {
                tipAmountLayout.visibility = View.GONE
            }

            if (txn.taxAmount != null && txn.taxAmount > BigDecimal.ZERO) {
                taxAmountLayout.visibility = View.VISIBLE
                taxAmountText.text = String.format("$%.2f", txn.taxAmount)
            } else {
                taxAmountLayout.visibility = View.GONE
            }

            if (txn.cashbackAmount != null && txn.cashbackAmount > BigDecimal.ZERO) {
                cashbackAmountLayout.visibility = View.VISIBLE
                cashbackAmountText.text = String.format("$%.2f", txn.cashbackAmount)
            } else {
                cashbackAmountLayout.visibility = View.GONE
            }

            if (txn.serviceFee != null && txn.serviceFee > BigDecimal.ZERO) {
                serviceFeeLayout.visibility = View.VISIBLE
                serviceFeeText.text = String.format("$%.2f", txn.serviceFee)
            } else {
                serviceFeeLayout.visibility = View.GONE
            }
        } else {
            // Hide all additional amounts for batch close transactions
            surchargeAmountLayout.visibility = View.GONE
            tipAmountLayout.visibility = View.GONE
            cashbackAmountLayout.visibility = View.GONE
            serviceFeeLayout.visibility = View.GONE
            taxAmountLayout.visibility = View.GONE
        }

        // Original Transaction ID (only shown for REFUND, VOID, POST_AUTH)
        if (shouldShowOriginalTransactionId(txn)) {
            originalTransactionIdLayout.visibility = View.VISIBLE
            originalTransactionIdText.text = txn.originalTransactionId ?: "N/A"
        } else {
            originalTransactionIdLayout.visibility = View.GONE
        }

        // Authorization code (only shown for successful non-batch-close transactions)
        if (txn.type != TransactionType.BATCH_CLOSE && txn.isSuccess() && !txn.authCode.isNullOrEmpty()) {
            authCodeLayout.visibility = View.VISIBLE
            authCodeText.text = txn.authCode
        } else {
            authCodeLayout.visibility = View.GONE
        }

        // Error information (only shown for failed transactions)
        if (txn.isFailed() && (!txn.errorCode.isNullOrEmpty() || !txn.errorMessage.isNullOrEmpty())) {
            errorLayout.visibility = View.VISIBLE
            errorCodeText.text = txn.errorCode ?: "Unknown Error"
            errorMessageText.text = txn.errorMessage ?: "Unknown Error"
        } else {
            errorLayout.visibility = View.GONE
        }

        // Batch close information (only shown for successful BATCH_CLOSE transactions)
        if (txn.type == TransactionType.BATCH_CLOSE && txn.isSuccess()) {
            batchCloseInfoLayout.visibility = View.VISIBLE
            
            // Display batch number
            batchNoText.text = txn.batchNo?.toString() ?: "N/A"
            
            // Display batch close info if available
            txn.batchCloseInfo?.let { batchInfo ->
                batchTotalCountText.text = batchInfo.totalCount.toString()
                batchTotalAmountText.text = String.format("$%.2f", batchInfo.totalAmount)
                batchCloseTimeText.text = batchInfo.closeTime
                
                // Show total tip if > 0
                if (batchInfo.totalTip > BigDecimal.ZERO) {
                    batchTotalTipLayout.visibility = View.VISIBLE
                    batchTotalTipText.text = String.format("$%.2f", batchInfo.totalTip)
                } else {
                    batchTotalTipLayout.visibility = View.GONE
                }
                
                // Show total surcharge if > 0
                if (batchInfo.totalSurchargeAmount > BigDecimal.ZERO) {
                    batchTotalSurchargeLayout.visibility = View.VISIBLE
                    batchTotalSurchargeText.text = String.format("$%.2f", batchInfo.totalSurchargeAmount)
                } else {
                    batchTotalSurchargeLayout.visibility = View.GONE
                }
            } ?: run {
                // If no batch close info, show basic info
                batchTotalCountText.text = "N/A"
                batchTotalAmountText.text = "N/A"
                batchCloseTimeText.text = "N/A"
                batchTotalTipLayout.visibility = View.GONE
                batchTotalSurchargeLayout.visibility = View.GONE
            }
        } else {
            batchCloseInfoLayout.visibility = View.GONE
        }
    }

    /**
     * Check if original transaction ID should be displayed
     * Only for REFUND, VOID, POST_AUTH transaction types
     */
    private fun shouldShowOriginalTransactionId(txn: Transaction): Boolean {
        return txn.type == TransactionType.REFUND ||
               txn.type == TransactionType.VOID ||
               txn.type == TransactionType.POST_AUTH
    }

    /**
     * Set up operation buttons
     */
    private fun setupOperationButtons() {
        val txn = transaction ?: return

        // Hide all buttons
        refundButton.visibility = View.GONE
        voidButton.visibility = View.GONE
        tipAdjustButton.visibility = View.GONE
        incrementalAuthButton.visibility = View.GONE
        postAuthButton.visibility = View.GONE
        noOperationsText.visibility = View.GONE

        var hasOperations = false

        // Show available operations based on transaction status and type
        if (txn.canRefund()) {
            refundButton.visibility = View.VISIBLE
            hasOperations = true
        }

        if (txn.canVoid()) {
            voidButton.visibility = View.VISIBLE
            hasOperations = true
        }

        if (txn.canAdjustTip()) {
            tipAdjustButton.visibility = View.VISIBLE
            hasOperations = true
        }

        if (txn.canIncrementalAuth()) {
            incrementalAuthButton.visibility = View.VISIBLE
            hasOperations = true
        }

        if (txn.canPostAuth()) {
            postAuthButton.visibility = View.VISIBLE
            hasOperations = true
        }

        // Query buttons are displayed for all transactions except BATCH_CLOSE
        if (txn.type != TransactionType.BATCH_CLOSE) {
            queryByRequestIdButton.visibility = View.VISIBLE
            
            // Query by transaction ID button is only shown if transactionId is available
            if (!txn.transactionId.isNullOrEmpty()) {
                queryByTransactionIdButton.visibility = View.VISIBLE
            } else {
                queryByTransactionIdButton.visibility = View.GONE
            }
            
            hasOperations = true
        } else {
            // Hide query buttons for BATCH_CLOSE transactions
            queryByRequestIdButton.visibility = View.GONE
            queryByTransactionIdButton.visibility = View.GONE
        }

        // Show prompt if no other operations are available
        if (!hasOperations) {
            noOperationsText.visibility = View.VISIBLE
        }
    }

    /**
     * Initialize event listeners
     */
    private fun initListeners() {
        refundButton.setOnClickListener {
            showRefundDialog()
        }

        voidButton.setOnClickListener {
            showVoidConfirmDialog()
        }

        tipAdjustButton.setOnClickListener {
            showTipAdjustDialog()
        }

        incrementalAuthButton.setOnClickListener {
            showIncrementalAuthDialog()
        }

        postAuthButton.setOnClickListener {
            showPostAuthDialog()
        }

        queryByRequestIdButton.setOnClickListener {
            executeQueryByRequestId()
        }

        queryByTransactionIdButton.setOnClickListener {
            executeQueryByTransactionId()
        }
    }

    /**
     * Show refund dialog
     */
    private fun showRefundDialog() {
        val txn = transaction ?: return

        val input = EditText(this)
        input.hint = "Enter refund amount"
        input.setText(String.format("%.2f", txn.amount))
        input.inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL

        AlertDialog.Builder(this)
            .setTitle("Refund")
            .setMessage("Original amount: $${String.format("%.2f", txn.amount)}")
            .setView(input)
            .setPositiveButton("OK") { _, _ ->
                val amountStr = input.text.toString().trim()
                if (amountStr.isEmpty()) {
                    showToast("Please enter refund amount")
                    return@setPositiveButton
                }

                try {
                    val amount = BigDecimal(amountStr)
                    val originalTotalAmount = txn.totalAmount ?: txn.amount
                    if (amount <= BigDecimal.ZERO || amount > originalTotalAmount) {
                        showToast("Refund amount must be > 0 and <= original amount")
                        return@setPositiveButton
                    }
                    executeRefund(amount)
                } catch (e: NumberFormatException) {
                    showToast("Please enter valid amount")
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /**
     * Show void confirmation dialog
     */
    private fun showVoidConfirmDialog() {
        val txn = transaction ?: return

        AlertDialog.Builder(this)
            .setTitle("Void Transaction")
            .setMessage("Are you sure you want to void this transaction?\n\nAmount: $${String.format("%.2f", txn.totalAmount ?: txn.amount)}\nOrder: ${txn.referenceOrderId}")
            .setPositiveButton("OK") { _, _ ->
                executeVoid()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /**
     * Show tip adjustment dialog
     */
    private fun showTipAdjustDialog() {
        val input = EditText(this)
        input.hint = "Enter tip amount"
        input.inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL

        AlertDialog.Builder(this)
            .setTitle("Tip Adjust")
            .setMessage("Please enter tip amount to adjust")
            .setView(input)
            .setPositiveButton("OK") { _, _ ->
                val amountStr = input.text.toString().trim()
                if (amountStr.isEmpty()) {
                    showToast("Please enter tip amount")
                    return@setPositiveButton
                }

                try {
                    val tipAmount = BigDecimal(amountStr)
                    if (tipAmount < BigDecimal.ZERO) {
                        showToast("Tip amount cannot be negative")
                        return@setPositiveButton
                    }
                    executeTipAdjust(tipAmount)
                } catch (e: NumberFormatException) {
                    showToast("Please enter valid amount")
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /**
     * Show incremental authorization dialog
     */
    private fun showIncrementalAuthDialog() {
        val input = EditText(this)
        input.hint = "Enter incremental amount"
        input.inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL

        AlertDialog.Builder(this)
            .setTitle("Incremental Auth")
            .setMessage("Please enter incremental amount to add to the authorization")
            .setView(input)
            .setPositiveButton("OK") { _, _ ->
                val amountStr = input.text.toString().trim()
                if (amountStr.isEmpty()) {
                    showToast("Please enter incremental amount")
                    return@setPositiveButton
                }

                try {
                    val incrementalAmount = BigDecimal(amountStr)
                    if (incrementalAmount <= BigDecimal.ZERO) {
                        showToast("Incremental amount must be greater than 0")
                        return@setPositiveButton
                    }
                    executeIncrementalAuth(incrementalAmount)
                } catch (e: NumberFormatException) {
                    showToast("Please enter valid amount")
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /**
     * Show pre-authorization completion dialog
     */
    private fun showPostAuthDialog() {
        val txn = transaction ?: return

        val input = EditText(this)
        input.hint = "Enter completion amount"
        input.setText(String.format("%.2f", txn.amount))
        input.inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL

        AlertDialog.Builder(this)
            .setTitle("Post Auth")
            .setMessage("Original auth amount: $${String.format("%.2f", txn.amount)}")
            .setView(input)
            .setPositiveButton("OK") { _, _ ->
                val amountStr = input.text.toString().trim()
                if (amountStr.isEmpty()) {
                    showToast("Please enter completion amount")
                    return@setPositiveButton
                }

                try {
                    val amount = BigDecimal(amountStr)
                    if (amount <= BigDecimal.ZERO || amount > txn.amount) {
                        showToast("Completion amount must be > 0 and <= auth amount")
                        return@setPositiveButton
                    }
                    showPostAuthAmountDialog(amount)
                } catch (e: NumberFormatException) {
                    showToast("Please enter valid amount")
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /**
     * Show post auth additional amounts dialog
     */
    private fun showPostAuthAmountDialog(completionAmount: BigDecimal) {
        // Create dialog layout
        val dialogView = layoutInflater.inflate(R.layout.dialog_additional_amounts, null)
        val etSurchargeAmount = dialogView.findViewById<EditText>(R.id.et_surcharge_amount)
        val etTipAmount = dialogView.findViewById<EditText>(R.id.et_tip_amount)
        val etTaxAmount = dialogView.findViewById<EditText>(R.id.et_tax_amount)
        val etCashbackAmount = dialogView.findViewById<EditText>(R.id.et_cashback_amount)
        val etServiceFee = dialogView.findViewById<EditText>(R.id.et_service_fee)

        
        // Get the corresponding title TextView
        val tvOrderAmount = dialogView.findViewById<TextView>(R.id.tv_base_amount_t)
        val tvSurchargeAmount = dialogView.findViewById<TextView>(R.id.tv_surcharge_amount)
        val tvCashbackAmount = dialogView.findViewById<TextView>(R.id.tv_cashback_amount)
        val tvServiceFee = dialogView.findViewById<TextView>(R.id.tv_service_fee)

        // Hide surcharge, cashback and service fee fields as they're not supported for POST_AUTH
        tvOrderAmount.visibility = View.GONE
        etSurchargeAmount.visibility = View.GONE
        tvSurchargeAmount.visibility = View.GONE
        etCashbackAmount.visibility = View.GONE
        tvCashbackAmount.visibility = View.GONE
        etServiceFee.visibility = View.GONE
        tvServiceFee.visibility = View.GONE
        
        AlertDialog.Builder(this)
            .setTitle("Additional Amounts (Optional)")
            .setMessage("Completion Amount: ${String.format("%.2f", completionAmount)}")
            .setView(dialogView)
            .setPositiveButton("Proceed") { _, _ ->
                val tipAmount = etTipAmount.text.toString().let { if (it.isBlank()) null else BigDecimal(it) }
                val taxAmount = etTaxAmount.text.toString().let { if (it.isBlank()) null else BigDecimal(it) }
                
                executePostAuth(completionAmount, null, tipAmount, taxAmount, null, null)
            }
            .setNegativeButton("Cancel", null)
            .setNeutralButton("Skip") { _, _ ->
                executePostAuth(completionAmount, null, null, null, null, null)
            }
            .show()
    }

    /**
     * Execute refund
     */
    private fun executeRefund(amount: BigDecimal, existingTransactionRequestId: String? = null) {
        val txn = transaction ?: return

        Log.d(TAG, "Executing refund, amount: $amount, existingTransactionRequestId: $existingTransactionRequestId")

        val progressDialog = createProgressDialogWithTimeout(
            message = "Processing refund...",
            onTimeout = {
                showToast("Refund timeout. Please try query to check status.")
            }
        )
        progressDialog.show()

        val transactionRequestId = existingTransactionRequestId ?: generateTransactionRequestId()
        val referenceOrderId = generateOrderId()

        // For refund, use the original transaction's transactionId (SDK returned ID) as originalTransactionId
        // If transactionId is null, fall back to transactionRequestId
        val originalTxnId = txn.transactionId ?: txn.transactionRequestId

        // Create transaction record or update existing one
        val newTransaction = Transaction(
            transactionRequestId = transactionRequestId,
            transactionId = null,
            referenceOrderId = referenceOrderId,
            type = TransactionType.REFUND,
            amount = amount,
            currency = txn.currency,
            status = TransactionStatus.PROCESSING,
            timestamp = System.currentTimeMillis(),
            originalTransactionId = originalTxnId
        )
        
        if (existingTransactionRequestId == null) {
            TransactionRepository.addTransaction(newTransaction)
        } else {
            TransactionRepository.updateTransaction(existingTransactionRequestId) {
                it.copy(status = TransactionStatus.PROCESSING, errorCode = null, errorMessage = null)
            }
        }

        paymentService.executeRefund(
            referenceOrderId = referenceOrderId,
            transactionRequestId = transactionRequestId,
            originalTransactionId = originalTxnId,
            amount = amount,
            currency = txn.currency,
            description = "Refund transaction",
            reason = "User requested refund",
            callback = object : PaymentCallback {
                override fun onSuccess(result: PaymentResult) {
                    runOnUiThread {
                        progressDialog.dismiss()
                        
                        // Update transaction status with actual amounts from SDK
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
                            orderAmount = result.amount?.orderAmount,
                            totalAmount = result.amount?.transAmount,
                            surchargeAmount = result.amount?.surchargeAmount,
                            tipAmount = result.amount?.tipAmount,
                            taxAmount = result.amount?.taxAmount,
                            cashbackAmount = result.amount?.cashbackAmount,
                            serviceFee = result.amount?.serviceFee
                        )
                        
                        showSuccessDialog("Refund Successful", result)
                        // Refresh current transaction information
                        loadTransaction()
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
                        
                        showPaymentError(code, message)
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
     * Execute void
     */
    private fun executeVoid(existingTransactionRequestId: String? = null) {
        val txn = transaction ?: return

        Log.d(TAG, "Executing void, existingTransactionRequestId: $existingTransactionRequestId")

        val progressDialog = createProgressDialogWithTimeout(
            message = "Processing void...",
            onTimeout = {
                showToast("Void timeout. Please try query to check status.")
            }
        )
        progressDialog.show()

        val transactionRequestId = existingTransactionRequestId ?: generateTransactionRequestId()
        val referenceOrderId = generateOrderId()

        // For void, use the original transaction's transactionId (SDK returned ID) as originalTransactionId
        // If transactionId is null, fall back to transactionRequestId
        val originalTxnId = txn.transactionId ?: txn.transactionRequestId

        // Create transaction record or update existing one
        val newTransaction = Transaction(
            transactionRequestId = transactionRequestId,
            transactionId = null,
            referenceOrderId = referenceOrderId,
            type = TransactionType.VOID,
            amount = txn.totalAmount ?: txn.amount,
            totalAmount = txn.totalAmount,
            currency = txn.currency,
            status = TransactionStatus.PROCESSING,
            timestamp = System.currentTimeMillis(),
            originalTransactionId = originalTxnId
        )
        
        if (existingTransactionRequestId == null) {
            TransactionRepository.addTransaction(newTransaction)
        } else {
            TransactionRepository.updateTransaction(existingTransactionRequestId) {
                it.copy(status = TransactionStatus.PROCESSING, errorCode = null, errorMessage = null)
            }
        }

        paymentService.executeVoid(
            referenceOrderId = referenceOrderId,
            transactionRequestId = transactionRequestId,
            originalTransactionId = originalTxnId,
            description = "Void transaction",
            reason = "User requested void",
            callback = object : PaymentCallback {
                override fun onSuccess(result: PaymentResult) {
                    runOnUiThread {
                        progressDialog.dismiss()
                        
                        // Update transaction status with actual amounts from SDK
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
                            orderAmount = result.amount?.orderAmount,
                            totalAmount = result.amount?.transAmount,
                            surchargeAmount = result.amount?.surchargeAmount,
                            tipAmount = result.amount?.tipAmount,
                            taxAmount = result.amount?.taxAmount,
                            cashbackAmount = result.amount?.cashbackAmount,
                            serviceFee = result.amount?.serviceFee
                        )
                        
                        showSuccessDialog("Void Successful", result)
                        // Refresh current transaction information
                        loadTransaction()
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
                        
                        showPaymentErrorWithRetry(code, message, 
                            onRetry = { executeVoid() })
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
     * Execute tip adjustment
     */
    private fun executeTipAdjust(tipAmount: BigDecimal, existingTransactionRequestId: String? = null) {
        val txn = transaction ?: return

        Log.d(TAG, "Executing tip adjustment, tip amount: $tipAmount, existingTransactionRequestId: $existingTransactionRequestId")

        val progressDialog = createProgressDialogWithTimeout(
            message = "Processing tip adjust...",
            onTimeout = {
                showToast("Tip adjustment timeout. Please try query to check status.")
            }
        )
        progressDialog.show()

        val transactionRequestId = existingTransactionRequestId ?: generateTransactionRequestId()
        val referenceOrderId = generateOrderId()

        val originalTxnId = txn.transactionId ?: txn.transactionRequestId

        // Tip adjust does not create new transaction record, it updates the original transaction
        paymentService.executeTipAdjust(
            referenceOrderId = referenceOrderId,
            transactionRequestId = transactionRequestId,
            originalTransactionId = originalTxnId,
            tipAmount = tipAmount,
            description = "Tip adjustment",
            callback = object : PaymentCallback {
                override fun onSuccess(result: PaymentResult) {
                    runOnUiThread {
                        progressDialog.dismiss()
                        
                        // Update the original transaction's tip amount (overwrite, not add)
                        if (result.transactionStatus == "SUCCESS") {
                            val newTipAmount = result.amount?.tipAmount ?: tipAmount
                            
                            // Update the original transaction with new tip amount
                            TransactionRepository.updateTransaction(txn.transactionRequestId) { transaction ->
                                transaction.copy(
                                    tipAmount = newTipAmount,
                                )
                            }
                            
                            showSuccessDialog("Tip Adjustment Successful", result)
                            // Refresh current transaction information
                            loadTransaction()
                        } else {
                            showToast("Tip adjustment failed: ${result.transactionResultMsg ?: "Unknown error"}")
                        }
                    }
                }

                override fun onFailure(code: String, message: String) {
                    runOnUiThread {
                        progressDialog.dismiss()
                        
                        showPaymentErrorWithRetry(code, message, 
                            onRetry = { executeTipAdjust(tipAmount) })
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
     * Execute incremental authorization
     */
    private fun executeIncrementalAuth(incrementalAmount: BigDecimal, existingTransactionRequestId: String? = null) {
        val txn = transaction ?: return

        Log.d(TAG, "Executing incremental authorization, incremental amount: $incrementalAmount, existingTransactionRequestId: $existingTransactionRequestId")

        val progressDialog = createProgressDialogWithTimeout(
            message = "Processing incremental auth...",
            onTimeout = {
                showToast("Incremental auth timeout. Please try query to check status.")
            }
        )
        progressDialog.show()

        val transactionRequestId = existingTransactionRequestId ?: generateTransactionRequestId()
        var referenceOrderId = txn.referenceOrderId // Use the same order ID as original transaction
        if (referenceOrderId.isNullOrEmpty()){
            referenceOrderId = generateOrderId()
        }

        val originalTxnId = txn.transactionId ?: txn.transactionRequestId

        paymentService.executeIncrementalAuth(
            referenceOrderId = referenceOrderId,
            transactionRequestId = transactionRequestId,
            originalTransactionId = originalTxnId,
            amount = incrementalAmount,
            currency = txn.currency,
            description = "Incremental authorization",
            callback = object : PaymentCallback {
                override fun onSuccess(result: PaymentResult) {
                    runOnUiThread {
                        progressDialog.dismiss()
                        
                        if (result.transactionStatus == "SUCCESS") {
                            // Update the original transaction by adding the incremental amount
                            val newOrderAmount = txn.amount + incrementalAmount
                            val newTotalAmount = (txn.totalAmount ?: txn.amount) + incrementalAmount
                            
                            // Update the original transaction with new amounts
                            TransactionRepository.updateTransaction(txn.transactionRequestId) { transaction ->
                                transaction.copy(
                                    amount = newOrderAmount,
                                    totalAmount = newTotalAmount,
                                    authCode = result.authCode ?: transaction.authCode
                                )
                            }
                            
                            showSuccessDialog("Incremental Authorization Successful", result)
                            // Refresh current transaction information
                            loadTransaction()
                        } else {
                            showToast("Incremental authorization failed: ${result.transactionResultMsg ?: "Unknown error"}")
                        }
                    }
                }

                override fun onFailure(code: String, message: String) {
                    runOnUiThread {
                        progressDialog.dismiss()
                        
                        showPaymentErrorWithRetry(code, message, 
                            onRetry = { executeIncrementalAuth(incrementalAmount) })
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
     * Execute pre-authorization completion
     */
    private fun executePostAuth(
        amount: BigDecimal,
        surchargeAmount: BigDecimal? = null,
        tipAmount: BigDecimal? = null,
        taxAmount: BigDecimal? = null,
        cashbackAmount: BigDecimal? = null,
        serviceFee: BigDecimal? = null,
        existingTransactionRequestId: String? = null
    ) {
        val txn = transaction ?: return

        Log.d(TAG, "Executing pre-authorization completion, amount: $amount")

        val progressDialog = createProgressDialogWithTimeout(
            message = "Processing post auth...",
            onTimeout = {
                showToast("Post auth timeout. Please try query to check status.")
            }
        )
        progressDialog.show()

        val transactionRequestId = existingTransactionRequestId ?: generateTransactionRequestId()
        val referenceOrderId = generateOrderId()
        Log.d(TAG, "Post auth transactionRequestId: $transactionRequestId, existingTransactionRequestId: $existingTransactionRequestId")

        val originalTxnId = txn.transactionId ?: txn.transactionRequestId

        // Create transaction record
        val newTransaction = Transaction(
            transactionRequestId = transactionRequestId,
            transactionId = null,
            referenceOrderId = referenceOrderId,
            type = TransactionType.POST_AUTH,
            amount = amount,
            currency = txn.currency,
            status = TransactionStatus.PROCESSING,
            timestamp = System.currentTimeMillis(),
            originalTransactionId = originalTxnId,
            surchargeAmount = surchargeAmount,
            tipAmount = tipAmount,
            taxAmount = taxAmount,
            cashbackAmount = cashbackAmount,
            serviceFee = serviceFee
        )
        TransactionRepository.addTransaction(newTransaction)

        paymentService.executePostAuth(
            referenceOrderId = referenceOrderId,
            transactionRequestId = transactionRequestId,
            originalTransactionId = originalTxnId,
            amount = amount,
            currency = txn.currency,
            description = "Pre-authorization completion",
            surchargeAmount = surchargeAmount,
            tipAmount = tipAmount,
            taxAmount = taxAmount,
            cashbackAmount = cashbackAmount,
            serviceFee = serviceFee,
            callback = object : PaymentCallback {
                override fun onSuccess(result: PaymentResult) {
                    runOnUiThread {
                        progressDialog.dismiss()
                        // Update transaction status with actual amounts from SDK
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
                            orderAmount = result.amount?.orderAmount,
                            totalAmount = result.amount?.transAmount,
                            surchargeAmount = result.amount?.surchargeAmount,
                            tipAmount = result.amount?.tipAmount,
                            taxAmount = result.amount?.taxAmount,
                            cashbackAmount = result.amount?.cashbackAmount,
                            serviceFee = result.amount?.serviceFee
                        )
                        
                        showSuccessDialog("Pre-authorization Completion Successful", result)
                        // Refresh current transaction information
                        loadTransaction()
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
                        
                        showPaymentErrorWithRetry(code, message, 
                            onRetry = { executePostAuth(amount, surchargeAmount, tipAmount, taxAmount, cashbackAmount, serviceFee) })
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
     * Execute query using transaction request ID
     */
    private fun executeQueryByRequestId() {
        val txn = transaction ?: return

        Log.d(TAG, "Executing query using transactionRequestId: ${txn.transactionRequestId}")

        val progressDialog = createProgressDialogWithTimeout(
            message = "Querying transaction status...",
            onTimeout = {
                showToast("Query timeout. Please try again.")
            }
        )
        progressDialog.show()

        paymentService.executeQuery(
            transactionRequestId = txn.transactionRequestId,
            callback = object : PaymentCallback {
                override fun onSuccess(result: PaymentResult) {
                    runOnUiThread {
                        progressDialog.dismiss()
                        updateTransactionFromQueryResult(result)
                        showQueryResultDialog(result)
                    }
                }

                override fun onFailure(code: String, message: String) {
                    runOnUiThread {
                        progressDialog.dismiss()
                        showErrorDialog("Query Failed", code, message)
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
     * Execute query using transaction ID
     */
    private fun executeQueryByTransactionId() {
        val txn = transaction ?: return
        
        if (txn.transactionId.isNullOrEmpty()) {
            showToast("Transaction ID not available for query")
            return
        }

        Log.d(TAG, "Executing query using transactionId: ${txn.transactionId}")

        val progressDialog = createProgressDialogWithTimeout(
            message = "Querying transaction status...",
            onTimeout = {
                showToast("Query timeout. Please try again.")
            }
        )
        progressDialog.show()

        paymentService.executeQueryByTransactionId(
            transactionId = txn.transactionId,
            callback = object : PaymentCallback {
                override fun onSuccess(result: PaymentResult) {
                    runOnUiThread {
                        progressDialog.dismiss()
                        updateTransactionFromQueryResult(result)
                        showQueryResultDialog(result)
                    }
                }

                override fun onFailure(code: String, message: String) {
                    runOnUiThread {
                        progressDialog.dismiss()
                        showErrorDialog("Query Failed", code, message)
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
     * Update transaction record from query result
     */
    private fun updateTransactionFromQueryResult(result: PaymentResult) {
        val txn = transaction ?: return
        
        // Update transaction status based on query result
        val status = when (result.transactionStatus) {
            "SUCCESS" -> TransactionStatus.SUCCESS
            "FAILED" -> TransactionStatus.FAILED
            "PROCESSING" -> TransactionStatus.PROCESSING
            else -> TransactionStatus.FAILED
        }
        
        // Update transaction with complete information including amounts
        TransactionRepository.updateTransactionWithAmounts(
            transactionRequestId = txn.transactionRequestId,
            status = status,
            transactionId = result.transactionId,
            authCode = result.authCode,
            errorCode = if (status == TransactionStatus.FAILED) result.code.toString() else null,
            errorMessage = if (status == TransactionStatus.FAILED) result.message else null,
            orderAmount = result.amount?.orderAmount,
            totalAmount = result.amount?.transAmount,
            surchargeAmount = result.amount?.surchargeAmount,
            tipAmount = result.amount?.tipAmount,
            taxAmount = result.amount?.taxAmount,
            cashbackAmount = result.amount?.cashbackAmount,
            serviceFee = result.amount?.serviceFee
        )
        
        // Reload transaction to reflect updates
        loadTransaction()
    }

    /**
     * Show success dialog
     */
    private fun showSuccessDialog(title: String, result: PaymentResult) {
        val message = buildString {
            append("Transaction successful!\n\n")
            append("Transaction ID: ${result.transactionId ?: "N/A"}\n")
            append("Auth Code: ${result.authCode ?: "N/A"}\n")
            if (!result.description.isNullOrEmpty()) {
                append("Additional Info: ${result.description}")
            }
        }

        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }

    /**
     * Show error dialog
     */
    private fun showErrorDialog(title: String, errorCode: String, errorMessage: String) {
        val message = "Error Code: $errorCode\nError Message: $errorMessage"

        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }

    /**
     * Show inquiry result dialog
     */
    private fun showQueryResultDialog(result: PaymentResult) {
        val message = buildString {
//            append("Query Result:\n\n")
            append("Transaction ID: ${result.transactionId ?: "N/A"}\n")
            append("Status: ${if (result.isSuccess()) "Success" else "Failed"}\n")
            if (result.isSuccess()) {
                append("Auth Code: ${result.authCode ?: "N/A"}\n")
            } else {
                append("Error Code: ${result.code}\n")
                append("Error Message: ${result.message}\n")
            }
            if (!result.description.isNullOrEmpty()) {
                append("Additional Info: ${result.description}")
            }
        }

        AlertDialog.Builder(this)
            .setTitle("Query Result")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }

    /**
     * Get status color
     */
    private fun getStatusColor(status: TransactionStatus): Int {
        return when (status) {
            TransactionStatus.SUCCESS -> 0xFF4CAF50.toInt() // Green
            TransactionStatus.FAILED -> 0xFFF44336.toInt()   // Red
            TransactionStatus.PENDING -> 0xFFFF9800.toInt()  // Orange
            TransactionStatus.PROCESSING -> 0xFF2196F3.toInt() // Blue
            TransactionStatus.CANCELLED -> 0xFF9E9E9E.toInt() // Gray
        }
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
     * Create a progress dialog with timeout
     * 
     * @param message Message to display
     * @param onTimeout Callback when timeout occurs
     * @return ProgressDialog instance
     */
    private fun createProgressDialogWithTimeout(
        message: String,
        onTimeout: () -> Unit
    ): ProgressDialog {
        val progressDialog = ProgressDialog(this)
        progressDialog.setMessage(message)
        progressDialog.setCancelable(true) // Allow user to cancel
        progressDialog.setOnCancelListener {
            // User cancelled the dialog
            showToast("Operation cancelled by user")
        }
        
        // Set up timeout handler
        val timeoutHandler = android.os.Handler(android.os.Looper.getMainLooper())
        val timeoutRunnable = Runnable {
            if (progressDialog.isShowing) {
                progressDialog.dismiss()
                onTimeout()
            }
        }
        
        // Store the handler and runnable in the dialog's tag for later cleanup
        progressDialog.setOnDismissListener {
            timeoutHandler.removeCallbacks(timeoutRunnable)
        }
        
        // Start timeout timer
        timeoutHandler.postDelayed(timeoutRunnable, PROGRESS_DIALOG_TIMEOUT)
        
        return progressDialog
    }
    
    /**
     * Show simple payment error dialog
     */
    private fun showPaymentError(code: String, message: String) {
        val fullMessage = "$message\n\nError Code: $code"
        
        AlertDialog.Builder(this)
            .setTitle("Payment Error")
            .setMessage(fullMessage)
            .setPositiveButton("OK", null)
            .setCancelable(false)
            .show()
    }
    
    /**
     * Show payment error dialog with retry option
     */
    private fun showPaymentErrorWithRetry(code: String, message: String, onRetry: () -> Unit) {
        val fullMessage = "$message\n\nError Code: $code"
        
        AlertDialog.Builder(this)
            .setTitle("Payment Error")
            .setMessage(fullMessage)
            .setPositiveButton("Retry") { _, _ -> onRetry() }
            .setNegativeButton("Cancel", null)
            .setCancelable(false)
            .show()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        
        // Clean up any progress dialogs and their timeout handlers
        // The createProgressDialogWithTimeout method already handles cleanup in onDismissListener
        // but we ensure any remaining dialogs are dismissed
        
        // Dismiss any current alert dialog
        currentAlertDialog?.dismiss()
        currentAlertDialog = null
        
        // No specific cleanup needed as progress dialogs are properly managed
        // with timeout handlers that are cleaned up in onDismissListener
    }

    /**
     * Show Toast message
     */
    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}