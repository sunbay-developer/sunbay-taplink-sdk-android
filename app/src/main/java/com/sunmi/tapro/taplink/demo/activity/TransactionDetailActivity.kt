package com.sunmi.tapro.taplink.demo.activity

import android.app.AlertDialog
import android.content.Intent
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
            totalAmountText.text = String.format("$%,.2f", txn.batchCloseInfo.totalAmount)
            // Hide order amount for batch close
            orderAmountLayout.visibility = View.GONE
        } else {
            // For regular transactions
            val displayTotalAmount = txn.totalAmount ?: txn.amount
            totalAmountText.text = String.format("$%,.2f", displayTotalAmount)
            
            // Show order amount separately
            orderAmountLayout.visibility = View.VISIBLE
            orderAmountText.text = String.format("$%,.2f", txn.amount)
        }
        
        // Display order base amount (orderAmount) separately if different from total
//        if (txn.totalAmount != null && txn.totalAmount != txn.amount) {
//            orderAmountLayout.visibility = View.VISIBLE
//            orderAmountText.text = String.format("$%.2f", txn.amount)
//        } else {
//            orderAmountLayout.visibility = View.GONE
//        }
        
        // Set order ID with truncation for long IDs
        val orderId = txn.referenceOrderId ?: "N/A"
        orderIdText.text = if (orderId != "N/A" && orderId.length > 30) {
            "${orderId.take(15)}...${orderId.takeLast(10)}"
        } else {
            orderId
        }
        
        // Set transaction ID with truncation for long IDs
        val transactionId = txn.transactionId ?: txn.transactionRequestId
        transactionIdText.text = if (transactionId.length > 30) {
            "${transactionId.take(15)}...${transactionId.takeLast(10)}"
        } else {
            transactionId
        }
        transactionTimeText.text = dateFormat.format(Date(txn.timestamp))

        // Additional amounts (only show for non-batch-close transactions if they exist and are greater than 0)
        if (txn.type != TransactionType.BATCH_CLOSE) {
            if (txn.surchargeAmount != null && txn.surchargeAmount > BigDecimal.ZERO) {
                surchargeAmountLayout.visibility = View.VISIBLE
                surchargeAmountText.text = String.format("$%,.2f", txn.surchargeAmount)
            } else {
                surchargeAmountLayout.visibility = View.GONE
            }

            if (txn.tipAmount != null && txn.tipAmount > BigDecimal.ZERO) {
                tipAmountLayout.visibility = View.VISIBLE
                tipAmountText.text = String.format("$%,.2f", txn.tipAmount)
            } else {
                tipAmountLayout.visibility = View.GONE
            }

            if (txn.taxAmount != null && txn.taxAmount > BigDecimal.ZERO) {
                taxAmountLayout.visibility = View.VISIBLE
                taxAmountText.text = String.format("$%,.2f", txn.taxAmount)
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
                    if (amount <= BigDecimal.ZERO) {
                        showToast("Refund amount must be greater than 0")
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
                    if (amount <= BigDecimal.ZERO) {
                        showToast("Completion amount must be greater than 0")
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
    private fun executeRefund(amount: BigDecimal) {
        val txn = transaction ?: return

        Log.d(TAG, "Executing refund, amount: $amount")

        val transactionRequestId = generateTransactionRequestId()
        val referenceOrderId = generateOrderId()

        // For refund, use the original transaction's transactionId (SDK returned ID) as originalTransactionId
        // If transactionId is null, fall back to transactionRequestId
        val originalTxnId = txn.transactionId

        // Create transaction record
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
        
        TransactionRepository.addTransaction(newTransaction)

        // Launch TransactionProgressActivity for unified progress display
        launchTransactionProgressActivity(newTransaction)
    }

    /**
     * Execute void
     */
    private fun executeVoid() {
        val txn = transaction ?: return

        Log.d(TAG, "Executing void")

        val transactionRequestId = generateTransactionRequestId()
        val referenceOrderId = generateOrderId()

        // For void, use the original transaction's transactionId (SDK returned ID) as originalTransactionId
        val originalTxnId = txn.transactionId

        // Create transaction record
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
        
        TransactionRepository.addTransaction(newTransaction)

        // Launch TransactionProgressActivity for unified progress display
        launchTransactionProgressActivity(newTransaction)
    }

    /**
     * Execute tip adjustment
     */
    private fun executeTipAdjust(tipAmount: BigDecimal) {
        val txn = transaction ?: return

        Log.d(TAG, "Executing tip adjustment, tip amount: $tipAmount")

        val transactionRequestId = generateTransactionRequestId()
        val referenceOrderId = generateOrderId()

        val originalTxnId = txn.transactionId ?: txn.transactionRequestId

        // Create a tip adjust transaction record
        val tipAdjustTransaction = Transaction(
            transactionRequestId = transactionRequestId,
            transactionId = null,
            referenceOrderId = referenceOrderId,
            type = TransactionType.TIP_ADJUST,
            amount = txn.amount,
            currency = txn.currency,
            status = TransactionStatus.PROCESSING,
            timestamp = System.currentTimeMillis(),
            originalTransactionId = originalTxnId,
            tipAmount = tipAmount
        )
        
        TransactionRepository.addTransaction(tipAdjustTransaction)

        // Launch TransactionProgressActivity for unified progress display
        launchTransactionProgressActivity(tipAdjustTransaction)
    }

    /**
     * Execute incremental authorization
     */
    private fun executeIncrementalAuth(incrementalAmount: BigDecimal, existingTransactionRequestId: String? = null) {
        val txn = transaction ?: return

        Log.d(TAG, "Executing incremental authorization, incremental amount: $incrementalAmount, existingTransactionRequestId: $existingTransactionRequestId")

        val transactionRequestId = existingTransactionRequestId ?: generateTransactionRequestId()
        var referenceOrderId = txn.referenceOrderId // Use the same order ID as original transaction
        if (referenceOrderId.isNullOrEmpty()){
            referenceOrderId = generateOrderId()
        }

        val originalTxnId = txn.transactionId ?: txn.transactionRequestId

        // Create transaction record for incremental auth
        val incrementalAuthTransaction = Transaction(
            transactionRequestId = transactionRequestId,
            transactionId = null,
            referenceOrderId = referenceOrderId,
            type = TransactionType.INCREMENT_AUTH,
            amount = incrementalAmount,
            currency = txn.currency,
            status = TransactionStatus.PROCESSING,
            timestamp = System.currentTimeMillis(),
            originalTransactionId = originalTxnId
        )
        
        TransactionRepository.addTransaction(incrementalAuthTransaction)

        // Launch TransactionProgressActivity for unified progress display
        launchTransactionProgressActivity(incrementalAuthTransaction)
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

        // Launch TransactionProgressActivity for unified progress display
        launchTransactionProgressActivity(newTransaction)
    }

    /**
     * Execute query using transaction request ID
     */
    private fun executeQueryByRequestId() {
        val txn = transaction ?: return

        Log.d(TAG, "Executing query using transactionRequestId: ${txn.transactionRequestId}")

        // Update current transaction status to processing
        TransactionRepository.updateTransactionStatus(txn.transactionRequestId, TransactionStatus.PROCESSING)
        
        // Execute query using current transaction's request ID
        paymentService.executeQuery(
            transactionRequestId = txn.transactionRequestId,
            callback = object : PaymentCallback {
                override fun onSuccess(result: PaymentResult) {
                    Log.d(TAG, "Query by request ID successful: $result")
                    runOnUiThread {
                        updateTransactionFromQueryResult(result)
                        showQueryResultDialog(result)
                    }
                }

                override fun onFailure(code: String, message: String) {
                    Log.e(TAG, "Query by request ID failed: $code - $message")
                    runOnUiThread {
                        TransactionRepository.updateTransactionStatus(txn.transactionRequestId, TransactionStatus.FAILED)
                        loadTransaction() // Refresh display
                        showErrorDialog("Query Failed", code, message)
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

        // Update current transaction status to processing
        TransactionRepository.updateTransactionStatus(txn.transactionRequestId, TransactionStatus.PROCESSING)
        
        // Execute query using current transaction's transaction ID
        paymentService.executeQueryByTransactionId(
            transactionId = txn.transactionId!!,
            callback = object : PaymentCallback {
                override fun onSuccess(result: PaymentResult) {
                    Log.d(TAG, "Query by transaction ID successful: $result")
                    runOnUiThread {
                        updateTransactionFromQueryResult(result)
                        showQueryResultDialog(result)
                    }
                }

                override fun onFailure(code: String, message: String) {
                    Log.e(TAG, "Query by transaction ID failed: $code - $message")
                    runOnUiThread {
                        TransactionRepository.updateTransactionStatus(txn.transactionRequestId, TransactionStatus.FAILED)
                        loadTransaction() // Refresh display
                        showErrorDialog("Query Failed", code, message)
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
            errorCode = if (status == TransactionStatus.FAILED) result.code else null,
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
        
        // Dismiss any current alert dialog
        currentAlertDialog?.dismiss()
        currentAlertDialog = null
    }

    /**
     * Launch TransactionProgressActivity for unified progress display
     */
    private fun launchTransactionProgressActivity(transaction: Transaction) {
        val intent = TransactionProgressActivity.createIntent(this, transaction)
        // Add original transaction ID for operations that need it
        val originalTxn = this.transaction
        if (originalTxn != null) {
            val originalTxnId = originalTxn.transactionId ?: originalTxn.transactionRequestId
            intent.putExtra("original_transaction_id", originalTxnId)
        }
        startActivity(intent)
    }

    /**
     * Show Toast message
     */
    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}