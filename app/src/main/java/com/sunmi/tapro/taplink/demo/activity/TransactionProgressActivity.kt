package com.sunmi.tapro.taplink.demo.activity

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import com.sunmi.tapro.taplink.demo.R
import com.sunmi.tapro.taplink.demo.model.Transaction
import com.sunmi.tapro.taplink.demo.model.TransactionType
import com.sunmi.tapro.taplink.demo.service.PaymentCallback
import com.sunmi.tapro.taplink.demo.service.PaymentResult
import com.sunmi.tapro.taplink.demo.util.Constants
import com.sunmi.tapro.taplink.demo.util.DialogUtils
import java.math.BigDecimal
import java.text.DecimalFormat

/**
 * Unified Transaction Progress Activity
 * 
 * Provides a modern, full-screen transaction progress interface that replaces
 * the scattered ProgressDialog and status card displays. Features:
 * - Material Design 3 styling
 * - Real-time progress updates using SDK events
 * - Integrated abort functionality for supported transaction types
 * - Persistent result display until user returns
 * - Immersive full-screen experience
 */
class TransactionProgressActivity : AppCompatActivity() {
    
    companion object {
        private const val TAG = "TransactionProgressActivity"
        
        // Intent extras
        const val EXTRA_TRANSACTION_TYPE = "transaction_type"
        const val EXTRA_AMOUNT = "amount"
        const val EXTRA_CURRENCY = "currency"
        const val EXTRA_ORDER_ID = "order_id"
        const val EXTRA_REQUEST_ID = "request_id"
        const val EXTRA_DESCRIPTION = "description"
        const val EXTRA_ADDITIONAL_AMOUNTS = "additional_amounts"
        
        // Static reference to current activity instance for communication
        private var currentInstance: TransactionProgressActivity? = null
        
        /**
         * Create intent for launching TransactionProgressActivity
         */
        fun createIntent(
            context: Context,
            transaction: Transaction
        ): Intent {
            return Intent(context, TransactionProgressActivity::class.java).apply {
                putExtra(EXTRA_TRANSACTION_TYPE, transaction.type.name)
                putExtra(EXTRA_AMOUNT, transaction.amount.toString())
                putExtra(EXTRA_CURRENCY, transaction.currency)
                putExtra(EXTRA_ORDER_ID, transaction.referenceOrderId ?: "")
                putExtra(EXTRA_REQUEST_ID, transaction.transactionRequestId)
                putExtra(EXTRA_DESCRIPTION, "")
                
                // Store additional amounts as individual extras
                transaction.surchargeAmount?.let { putExtra("surcharge_amount", it.toString()) }
                transaction.tipAmount?.let { putExtra("tip_amount", it.toString()) }
                transaction.taxAmount?.let { putExtra("tax_amount", it.toString()) }
                transaction.cashbackAmount?.let { putExtra("cashback_amount", it.toString()) }
                transaction.serviceFee?.let { putExtra("service_fee", it.toString()) }
            }
        }
        
        /**
         * Static method to update progress from MainActivity
         * This method allows MainActivity to send progress updates to the current activity instance
         * 
         * @param status Current transaction status
         * @param message Progress message from SdkPaymentEvent.eventMsg
         */
        fun updateProgressFromExternal(status: String, message: String) {
            currentInstance?.updateProgress(status, message)
        }
        
        /**
         * Static method to show result from MainActivity
         * This method allows MainActivity to send transaction results to the current activity instance
         * 
         * @param result Payment result object (null for failure cases)
         * @param errorCode Error code for failure cases (null for success)
         * @param errorMessage Error message for failure cases (null for success)
         */
        fun showResultFromExternal(result: PaymentResult?, errorCode: String?, errorMessage: String?) {
            currentInstance?.let { activity ->
                if (result != null) {
                    activity.showResult(result, null, null)
                } else if (errorCode != null && errorMessage != null) {
                    // Use enhanced error handling for external errors
                    if (activity.isConnectionError(errorCode)) {
                        activity.handleConnectionError(errorCode, errorMessage)
                    } else {
                        activity.handleSdkError(errorCode, errorMessage)
                    }
                }
            }
        }
        
        /**
         * Static method to show PaymentError from external sources
         * This method allows external components to send PaymentError to the current activity instance
         * 
         * @param errorCode Error code from PaymentError
         * @param errorMessage Error message from PaymentError
         * @param orderNo Order number from PaymentError (optional)
         * @param transactionId Transaction ID from PaymentError (optional)
         * @param suggestion Processing suggestion from PaymentError (optional)
         */
        fun showPaymentErrorFromExternal(errorCode: String, errorMessage: String, orderNo: String?, transactionId: String?, suggestion: String?) {
            currentInstance?.showPaymentError(errorCode, errorMessage, orderNo, transactionId, suggestion)
        }
        
        /**
         * Check if TransactionProgressActivity is currently active
         * 
         * @return true if activity is active and can receive updates
         */
        fun isActive(): Boolean {
            return currentInstance != null
        }
        
        /**
         * Get the current transaction request ID if activity is active
         * 
         * @return current transaction request ID or null if activity is not active
         */
        fun getCurrentTransactionRequestId(): String? {
            return currentInstance?.transaction?.transactionRequestId
        }
        
        /**
         * Get PaymentCallback for the current active instance
         * 
         * @return PaymentCallback that forwards to current activity, or null if no active instance
         */
        fun getPaymentCallback(): PaymentCallback? {
            return currentInstance?.createPaymentCallback()
        }
        
        /**
         * Set retry transaction listener for the current active instance
         * 
         * @param listener Listener to handle retry requests
         */
        fun setRetryTransactionListener(listener: RetryTransactionListener?) {
            currentInstance?.retryTransactionListener = listener
        }
    }
    
    // Transaction information display
    private lateinit var transactionInfoCard: CardView
    private lateinit var transactionTypeText: TextView
    private lateinit var amountText: TextView
    private lateinit var orderIdLayout: LinearLayout
    private lateinit var orderIdText: TextView
    private lateinit var requestIdText: TextView
    private lateinit var additionalAmountsLayout: LinearLayout
    
    // Progress display
    private lateinit var progressCard: CardView
    private lateinit var progressIndicator: ProgressBar
    private lateinit var progressStatusText: TextView
    private lateinit var progressMessageText: TextView
    
    // Operation buttons
    private lateinit var abortButton: Button
    private lateinit var retryButton: Button
    private lateinit var queryButton: Button
    
    // Result display
    private lateinit var resultCard: CardView
    private lateinit var resultIcon: ImageView
    private lateinit var resultText: TextView
    private lateinit var resultDetailsLayout: LinearLayout
    
    // Result detail views
    private lateinit var transactionIdLayout: LinearLayout
    private lateinit var authCodeLayout: LinearLayout
    private lateinit var completedTimeLayout: LinearLayout
    private lateinit var resultAmountLayout: LinearLayout
    private lateinit var resultMessageLayout: LinearLayout
    
    private lateinit var detailTransactionIdText: TextView
    private lateinit var detailAuthCodeText: TextView
    private lateinit var detailCompletedTimeText: TextView
    private lateinit var detailAmountText: TextView
    private lateinit var detailMessageText: TextView
    
    // Result detail label views (for dynamic title changes)
    private lateinit var labelTransactionIdText: TextView
    private lateinit var labelAuthCodeText: TextView
    private lateinit var labelCompletedTimeText: TextView
    private lateinit var labelAmountText: TextView
    private lateinit var labelMessageText: TextView
    
    // Transaction data
    private lateinit var transaction: Transaction
    
    // Retry listener for external communication
    private var retryTransactionListener: RetryTransactionListener? = null
    
    // Amount formatter
    private val amountFormatter = DecimalFormat("#,##0.00")
    
    /**
     * Format currency symbol - replace USD with $ symbol
     */
    private fun formatCurrencySymbol(currency: String): String {
        return when (currency.uppercase()) {
            "USD" -> "$"
            else -> currency
        }
    }
    
    /**
     * Format amount with currency symbol
     */
    private fun formatAmountWithCurrency(amount: BigDecimal, currency: String): String {
        val formattedAmount = amountFormatter.format(amount)
        return when (currency.uppercase()) {
            "USD" -> "$$formattedAmount"
            else -> "$formattedAmount $currency"
        }
    }
    
    // Back press callback for handling system back navigation
    private lateinit var backPressCallback: OnBackPressedCallback
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_transaction_progress)
        
        Log.d(TAG, "TransactionProgressActivity created")
        
        // Set current instance for static communication
        currentInstance = this
        
        // Extract transaction data from intent
        extractTransactionData()
        
        // Initialize UI components
        initializeViews()
        
        // Setup initial display
        setupInitialDisplay()
        
        // Setup event listeners
        setupEventListeners()
        
        // Setup back press handling
        setupBackPressHandling()
        
        // Check if this is launched from MainActivity (which handles payment itself)
        // or from other activities (which need TransactionProgressActivity to handle payment)
        val shouldStartPayment = intent.getBooleanExtra("auto_start_payment", true)
        
        if (shouldStartPayment) {
            // Start the actual payment transaction
            startPaymentTransaction()
        } else {
            // Just show the progress UI, payment will be handled by the calling activity
            Log.d(TAG, "TransactionProgressActivity started in display-only mode")
        }
    }
    
    /**
     * Extract transaction data from intent extras
     */
    private fun extractTransactionData() {
        val typeString = intent.getStringExtra(EXTRA_TRANSACTION_TYPE) 
            ?: throw IllegalArgumentException("Transaction type is required")
        val amountString = intent.getStringExtra(EXTRA_AMOUNT)
            ?: throw IllegalArgumentException("Amount is required")
        val currency = intent.getStringExtra(EXTRA_CURRENCY)
            ?: throw IllegalArgumentException("Currency is required")
        val orderId = intent.getStringExtra(EXTRA_ORDER_ID) ?: ""
        val requestId = intent.getStringExtra(EXTRA_REQUEST_ID)
            ?: throw IllegalArgumentException("Request ID is required")
        
        // Parse additional amounts if present
        val surchargeAmount = intent.getStringExtra("surcharge_amount")?.let { BigDecimal(it) }
        val tipAmount = intent.getStringExtra("tip_amount")?.let { BigDecimal(it) }
        val taxAmount = intent.getStringExtra("tax_amount")?.let { BigDecimal(it) }
        val cashbackAmount = intent.getStringExtra("cashback_amount")?.let { BigDecimal(it) }
        val serviceFee = intent.getStringExtra("service_fee")?.let { BigDecimal(it) }
        
        transaction = Transaction(
            transactionRequestId = requestId,
            referenceOrderId = orderId.takeIf { it.isNotEmpty() },
            type = TransactionType.valueOf(typeString),
            amount = BigDecimal(amountString),
            currency = currency,
            status = com.sunmi.tapro.taplink.demo.model.TransactionStatus.PROCESSING,
            timestamp = System.currentTimeMillis(),
            surchargeAmount = surchargeAmount,
            tipAmount = tipAmount,
            taxAmount = taxAmount,
            cashbackAmount = cashbackAmount,
            serviceFee = serviceFee
        )
        
        Log.d(TAG, "Transaction data extracted: $transaction")
    }
    
    /**
     * Get display name for transaction type
     */
    private fun getTransactionTypeDisplayName(type: TransactionType): String {
        return when (type) {
            TransactionType.SALE -> "Sale Transaction"
            TransactionType.AUTH -> "Authorization"
            TransactionType.FORCED_AUTH -> "Forced Authorization"
            TransactionType.INCREMENT_AUTH -> "Incremental Authorization"
            TransactionType.POST_AUTH -> "Post Authorization"
            TransactionType.REFUND -> "Refund"
            TransactionType.VOID -> "Void"
            TransactionType.TIP_ADJUST -> "Tip Adjustment"
            TransactionType.QUERY -> "Query"
            TransactionType.BATCH_CLOSE -> "Batch Close"
        }
    }
    
    /**
     * Initialize all view references
     */
    private fun initializeViews() {
        // Transaction information views
        transactionInfoCard = findViewById(R.id.card_transaction_info)
        transactionTypeText = findViewById(R.id.tv_transaction_type)
        amountText = findViewById(R.id.tv_amount)
        orderIdLayout = findViewById(R.id.layout_order_id)
        orderIdText = findViewById(R.id.tv_order_id)
        requestIdText = findViewById(R.id.tv_request_id)
        additionalAmountsLayout = findViewById(R.id.layout_additional_amounts)
        
        // Progress views
        progressCard = findViewById(R.id.card_progress)
        progressIndicator = findViewById(R.id.progress_indicator)
        progressStatusText = findViewById(R.id.tv_progress_status)
        progressMessageText = findViewById(R.id.tv_progress_message)
        
        // Button views
        abortButton = findViewById(R.id.btn_abort)
        retryButton = findViewById(R.id.btn_retry)
        queryButton = findViewById(R.id.btn_query)
        
        // Result views
        resultCard = findViewById(R.id.card_result)
        resultIcon = findViewById(R.id.iv_result_icon)
        resultText = findViewById(R.id.tv_result_text)
        resultDetailsLayout = findViewById(R.id.layout_result_details)
        
        // Result detail views
        transactionIdLayout = findViewById(R.id.layout_transaction_id)
        authCodeLayout = findViewById(R.id.layout_auth_code)
        completedTimeLayout = findViewById(R.id.layout_completed_time)
        resultAmountLayout = findViewById(R.id.layout_result_amount)
        resultMessageLayout = findViewById(R.id.layout_result_message)
        
        detailTransactionIdText = findViewById(R.id.tv_detail_transaction_id)
        detailAuthCodeText = findViewById(R.id.tv_detail_auth_code)
        detailCompletedTimeText = findViewById(R.id.tv_detail_completed_time)
        detailAmountText = findViewById(R.id.tv_detail_amount)
        detailMessageText = findViewById(R.id.tv_detail_message)
        
        // Initialize label views
        labelTransactionIdText = findViewById(R.id.tv_label_transaction_id)
        labelAuthCodeText = findViewById(R.id.tv_label_auth_code)
        labelCompletedTimeText = findViewById(R.id.tv_label_completed_time)
        labelAmountText = findViewById(R.id.tv_label_amount)
        labelMessageText = findViewById(R.id.tv_label_message)
    }
    
    /**
     * Setup initial display with transaction information
     */
    private fun setupInitialDisplay() {
        // Display transaction information
        setupTransactionInfoDisplay()
        
        // Setup additional amounts if present
        setupAdditionalAmountsDisplay()
        
        // Setup initial progress state
        setupInitialProgressState()
        
        // Setup button visibility
        setupButtonVisibility()
    }
    
    /**
     * Setup transaction information display
     */
    private fun setupTransactionInfoDisplay() {
        // Set transaction type
        transactionTypeText.text = getTransactionTypeDisplayName(transaction.type)
        
        // Set amount with currency symbol (combined format like "$8.99")
        amountText.text = formatAmountWithCurrency(transaction.amount, transaction.currency)
        
        // Hide Order ID for Batch Close transactions
        if (transaction.type == TransactionType.BATCH_CLOSE) {
            orderIdLayout.visibility = View.GONE
        } else {
            orderIdLayout.visibility = View.VISIBLE
            
            // Set order ID (show "N/A" if empty, truncate if too long)
            orderIdText.text = if (transaction.referenceOrderId.isNullOrEmpty()) {
                "N/A"
            } else {
                val orderId = transaction.referenceOrderId
                if (orderId?.length ?: 0 > 25) {
                    "${orderId?.take(12)}...${orderId?.takeLast(8)}"
                } else {
                    orderId ?: "N/A"
                }
            }
        }
        
        // Set request ID - truncate long IDs for better display
        val requestId = transaction.transactionRequestId
        requestIdText.text = if (requestId.length > 25) {
            "${requestId.take(12)}...${requestId.takeLast(8)}"
        } else {
            requestId
        }
        
        Log.d(TAG, "Transaction info displayed: ${transaction.type} ${formatAmountWithCurrency(transaction.amount, transaction.currency)}")
    }
    
    /**
     * Setup additional amounts display
     */
    private fun setupAdditionalAmountsDisplay() {
        // Add detailed logging for debugging
        Log.d(TAG, "Setting up additional amounts display")
        Log.d(TAG, "Transaction surchargeAmount: ${transaction.surchargeAmount}")
        Log.d(TAG, "Transaction tipAmount: ${transaction.tipAmount}")
        Log.d(TAG, "Transaction taxAmount: ${transaction.taxAmount}")
        Log.d(TAG, "Transaction cashbackAmount: ${transaction.cashbackAmount}")
        Log.d(TAG, "Transaction serviceFee: ${transaction.serviceFee}")
        Log.d(TAG, "Transaction hasAdditionalAmounts(): ${transaction.hasAdditionalAmounts()}")
        
        if (transaction.hasAdditionalAmounts()) {
            additionalAmountsLayout.visibility = View.VISIBLE
            
            // Clear any existing additional amount views
            clearAdditionalAmountViews()
            
            // Add each additional amount that exists
            transaction.surchargeAmount?.let { amount ->
                Log.d(TAG, "Adding surcharge amount: $amount")
                addAdditionalAmountRow("Surcharge", amount, transaction.currency)
            }
            
            transaction.tipAmount?.let { amount ->
                Log.d(TAG, "Adding tip amount: $amount")
                addAdditionalAmountRow("Tip", amount, transaction.currency)
            }
            
            transaction.taxAmount?.let { amount ->
                Log.d(TAG, "Adding tax amount: $amount")
                addAdditionalAmountRow("Tax", amount, transaction.currency)
            }
            
            transaction.cashbackAmount?.let { amount ->
                Log.d(TAG, "Adding cashback amount: $amount")
                addAdditionalAmountRow("Cashback", amount, transaction.currency)
            }
            
            transaction.serviceFee?.let { amount ->
                Log.d(TAG, "Adding service fee: $amount")
                addAdditionalAmountRow("Service Fee", amount, transaction.currency)
            }
            
            // Add total additional amount if there are multiple additional amounts
            val additionalAmountCount = listOfNotNull(
                transaction.surchargeAmount,
                transaction.tipAmount,
                transaction.taxAmount,
                transaction.cashbackAmount,
                transaction.serviceFee
            ).size
            
            if (additionalAmountCount > 1) {
                addAdditionalAmountDivider()
                addAdditionalAmountRow("Total Additional", transaction.getTotalAdditionalAmount(), transaction.currency, isTotal = true)
            }
            
            Log.d(TAG, "Additional amounts displayed: ${additionalAmountCount} items")
        } else {
            additionalAmountsLayout.visibility = View.GONE
            Log.d(TAG, "No additional amounts to display")
        }
    }
    
    /**
     * Clear existing additional amount views (except the header)
     */
    private fun clearAdditionalAmountViews() {
        // Keep the first 3 views (divider, header text, and spacing)
        // Remove any dynamically added amount rows
        val childCount = additionalAmountsLayout.childCount
        if (childCount > 3) {
            additionalAmountsLayout.removeViews(3, childCount - 3)
        }
    }
    
    /**
     * Add an additional amount row to the layout
     */
    private fun addAdditionalAmountRow(label: String, amount: BigDecimal, currency: String, isTotal: Boolean = false) {
        val rowLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = resources.getDimensionPixelSize(R.dimen.spacing_small)
            }
        }
        
        // Label text view
        val labelTextView = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
            text = label
            textSize = if (isTotal) 14f else 13f
            setTextColor(resources.getColor(if (isTotal) R.color.text_primary else R.color.text_secondary, null))
            if (isTotal) {
                setTypeface(null, android.graphics.Typeface.BOLD)
            }
        }
        
        // Amount text view with formatted currency
        val amountTextView = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            text = formatAmountWithCurrency(amount, currency)
            textSize = if (isTotal) 14f else 13f
            setTextColor(resources.getColor(if (isTotal) R.color.text_primary else R.color.text_secondary, null))
            if (isTotal) {
                setTypeface(null, android.graphics.Typeface.BOLD)
            }
        }
        
        rowLayout.addView(labelTextView)
        rowLayout.addView(amountTextView)
        additionalAmountsLayout.addView(rowLayout)
    }
    
    /**
     * Add a divider line for total section
     */
    private fun addAdditionalAmountDivider() {
        val divider = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                resources.getDimensionPixelSize(R.dimen.divider_height)
            ).apply {
                topMargin = resources.getDimensionPixelSize(R.dimen.spacing_small)
                bottomMargin = resources.getDimensionPixelSize(R.dimen.spacing_small)
            }
            setBackgroundColor(resources.getColor(R.color.divider, null))
        }
        additionalAmountsLayout.addView(divider)
    }
    
    /**
     * Setup initial progress state
     */
    private fun setupInitialProgressState() {
        progressCard.visibility = View.VISIBLE
        progressIndicator.visibility = View.VISIBLE
        progressStatusText.text = "Initializing"
        progressMessageText.text = "Preparing transaction..."
        
        // Hide result card initially
        resultCard.visibility = View.GONE
        
        // Block back navigation during initial transaction processing
        updateBackNavigationState(false)
    }
    
    /**
     * Setup button visibility based on transaction type
     */
    private fun setupButtonVisibility() {
        // Show abort button for supported transaction types
        // Query and Batch Close do not show abort button
        val shouldShowAbort = when (transaction.type) {
            TransactionType.QUERY, TransactionType.BATCH_CLOSE -> false
            else -> true
        }
        
        abortButton.visibility = if (shouldShowAbort) View.VISIBLE else View.GONE
        
        // Hide retry and query buttons initially
        retryButton.visibility = View.GONE
        queryButton.visibility = View.GONE
    }
    
    /**
     * Setup event listeners
     */
    private fun setupEventListeners() {
        abortButton.setOnClickListener {
            handleAbortButtonClick()
        }
        
        retryButton.setOnClickListener {
            handleRetryButtonClick()
        }
        
        queryButton.setOnClickListener {
            handleQueryButtonClick()
        }
    }
    
    /**
     * Setup back press handling for both old and new Android APIs
     * Ensures consistent behavior across different Android versions
     */
    private fun setupBackPressHandling() {
        // Create callback for handling back press
        backPressCallback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                handleBackNavigation()
            }
        }
        
        // Register the callback
        this.onBackPressedDispatcher.addCallback(this, backPressCallback)
        
        Log.d(TAG, "Back press handling configured")
    }
    
    /**
     * Handle back navigation logic
     * Centralized method for both old onBackPressed and new OnBackPressedCallback
     */
    private fun handleBackNavigation() {
        // Only allow back if transaction is complete (query button is visible)
        if (queryButton.visibility == View.VISIBLE) {
            Log.d(TAG, "Back navigation allowed - transaction complete")
            // Set result to indicate successful completion of progress display
            setResult(RESULT_OK)
            finish()
        } else {
            Log.d(TAG, "Back navigation blocked - transaction in progress")
            // Transaction is still in progress, prevent navigation
            // Could show a toast or dialog explaining why back is blocked
        }
    }
    
    /**
     * Handle abort button click
     * Implements direct abort functionality without confirmation dialog
     * Controls button display based on transaction type (Query and Batch Close hidden)
     * Manages button state (disable/enable) during abort process
     */
    private fun handleAbortButtonClick() {
        Log.d(TAG, "Abort button clicked for transaction: ${transaction.transactionRequestId}")
        
        // Check if abort is supported for this transaction type
        if (!isAbortSupportedForTransactionType(transaction.type)) {
            Log.w(TAG, "Abort not supported for transaction type: ${transaction.type}")
            return
        }
        
        // Disable abort button immediately to prevent multiple clicks
        abortButton.isEnabled = false
        abortButton.text = "Aborting..."
        
        // Update progress to show aborting state
        updateProgress("ABORTING", "Aborting transaction...")
        
        // Get PaymentService instance and execute abort
        try {
            val paymentService = com.sunmi.tapro.taplink.demo.service.TaplinkPaymentService.getInstance()
            
            // Execute abort with current transaction request ID
            paymentService.executeAbort(
                originalTransactionId = null, // For ongoing transactions, we typically don't have transaction ID yet
                originalTransactionRequestId = transaction.transactionRequestId,
                description = "User initiated transaction abort",
                callback = createAbortCallback()
            )
            
            Log.d(TAG, "Abort request sent to PaymentService")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to execute abort", e)
            
            // Re-enable abort button on error
            abortButton.isEnabled = true
            abortButton.text = "Abort Transaction"
            
            // Show error message
            updateProgress("ERROR", "Failed to abort transaction: ${e.message}")
        }
    }
    
    /**
     * Check if abort is supported for the given transaction type
     * Query and Batch Close transactions do not support abort
     * 
     * @param transactionType The transaction type to check
     * @return true if abort is supported, false otherwise
     */
    private fun isAbortSupportedForTransactionType(transactionType: TransactionType): Boolean {
        return when (transactionType) {
            TransactionType.QUERY, TransactionType.BATCH_CLOSE -> {
                Log.d(TAG, "Abort not supported for transaction type: $transactionType")
                false
            }
            else -> {
                Log.d(TAG, "Abort supported for transaction type: $transactionType")
                true
            }
        }
    }
    
    /**
     * Handle retry button click
     * Retry the failed transaction or repeat a successful transaction using the original transaction request ID
     */
    private fun handleRetryButtonClick() {
        Log.d(TAG, "Retry/Repeat button clicked for transaction: ${transaction.transactionRequestId}")
        
        // Hide result card and show progress card again
        resultCard.visibility = View.GONE
        progressCard.visibility = View.VISIBLE
        
        // Hide retry button and show abort button (if applicable)
        retryButton.visibility = View.GONE
        setupButtonVisibility()
        
        // Reset progress display
        setupInitialProgressState()
        
        // Update progress to show retry state
        updateProgress("RETRYING", "Retrying transaction...")
        
        // Directly start the payment transaction again with original transaction info
        startPaymentTransaction()
        
        // Also notify external listener about retry request (for compatibility)
        retryTransactionListener?.onRetryRequested(transaction)
    }
    
    /**
     * Handle query button click
     * Query the current transaction status using the transaction request ID
     */
    private fun handleQueryButtonClick() {
        Log.d(TAG, "Query button clicked for transaction: ${transaction.transactionRequestId}")
        
        // Hide result card and show progress card again
        resultCard.visibility = View.GONE
        progressCard.visibility = View.VISIBLE
        
        // Hide query button during query operation
        queryButton.visibility = View.GONE
        
        // Reset progress display
        setupInitialProgressState()
        
        // Update progress to show query state
        updateProgress("QUERYING", "Querying transaction status...")
        
        // Execute query operation
        queryTransactionStatus()
    }
    
    /**
     * Update progress status and message
     * This method handles SDK returned SdkPaymentEvent.eventMsg display
     * Also manages back navigation state during transaction processing
     * 
     * @param status Current transaction status
     * @param message Progress message from SdkPaymentEvent.eventMsg
     */
    fun updateProgress(status: String, message: String) {
        runOnUiThread {
            Log.d(TAG, "Updating progress - Status: $status, Message: $message")
            
            // Update status text
            progressStatusText.text = status
            
            // Update message text (use SDK's eventMsg if available, fallback to status)
            val displayMessage = if (message.isNotBlank()) {
                message
            } else {
                // Fallback to default messages based on status
                when (status.uppercase()) {
                    "PROCESSING" -> "Processing ${transaction.type.name.lowercase()} transaction..."
                    "WAITING" -> "Waiting for user action..."
                    "CONNECTING" -> "Connecting to payment terminal..."
                    "ABORTING" -> "Aborting transaction..."
                    else -> status
                }
            }
            
            progressMessageText.text = displayMessage
            
            // Update button states based on status
            updateButtonStates(status)
            
            // Ensure back navigation is blocked during transaction processing
            updateBackNavigationState(false)
            
            Log.d(TAG, "Progress updated successfully - back navigation blocked during processing")
        }
    }
    
    /**
     * Update button states based on current status
     */
    private fun updateButtonStates(status: String) {
        when (status.uppercase()) {
            "ABORTING" -> {
                // Disable abort button during abort process
                abortButton.isEnabled = false
                abortButton.text = "Aborting..."
            }
            "PROCESSING", "WAITING", "CONNECTING" -> {
                // Enable abort button for supported transaction types
                val shouldShowAbort = when (transaction.type) {
                    TransactionType.QUERY, TransactionType.BATCH_CLOSE -> false
                    else -> true
                }
                
                if (shouldShowAbort) {
                    abortButton.isEnabled = true
                    abortButton.text = "Abort Transaction"
                    abortButton.visibility = View.VISIBLE
                } else {
                    abortButton.visibility = View.GONE
                }
            }
        }
    }
    
    /**
     * Update back navigation state
     * Controls whether back navigation is allowed based on transaction state
     * 
     * @param allowBackNavigation true to allow back navigation, false to block it
     */
    private fun updateBackNavigationState(allowBackNavigation: Boolean) {
        if (::backPressCallback.isInitialized) {
            // Enable/disable the callback based on transaction state
            // When disabled, back navigation is allowed through normal system behavior
            // When enabled, our custom logic in handleOnBackPressed will be called
            backPressCallback.isEnabled = !allowBackNavigation
            
            Log.d(TAG, "Back navigation state updated - blocked: ${!allowBackNavigation}")
        }
    }
    
    /**
     * Show transaction result (success, failure, or abort)
     * This method handles the final result display and switches to result view
     * Ensures return button is properly displayed for user to return to MainActivity
     * 
     * @param result Payment result object (null for failure cases)
     * @param errorCode Error code for failure cases (null for success)
     * @param errorMessage Error message for failure cases (null for success)
     */
    fun showResult(result: PaymentResult?, errorCode: String?, errorMessage: String?) {
        runOnUiThread {
            Log.d(TAG, "Showing result - PaymentResult: $result, Error: $errorCode")
            
            // Update transaction status in repository
            updateTransactionStatus(result, errorCode, errorMessage)
            
            // Hide progress card and show result card
            progressCard.visibility = View.GONE
            resultCard.visibility = View.VISIBLE
            
            // Hide abort button (not needed after transaction completion)
            abortButton.visibility = View.GONE
            
            if (result != null) {
                // We have a PaymentResult from onSuccess callback, always treat as success
                // The SDK's onSuccess callback indicates transaction success regardless of code value
                showSuccessResult(result)
                retryButton.visibility = View.VISIBLE
                retryButton.text = "Repeat Transaction"
                queryButton.visibility = View.VISIBLE
                Log.d(TAG, "Transaction successful - showing success result")
            } else {
                // No PaymentResult, use error code and message
                showRequestFailureResult(errorCode ?: "UNKNOWN", errorMessage ?: "Unknown error occurred")
                
                // Check if transaction is retryable
                val isRetryable = isTransactionRetryable(errorCode)
                
                if (isRetryable) {
                    retryButton.visibility = View.VISIBLE
                    retryButton.text = "Retry"
                    queryButton.visibility = View.VISIBLE
                    Log.d(TAG, "Retryable failure result displayed")
                } else {
                    retryButton.visibility = View.GONE
                    queryButton.visibility = View.VISIBLE
                    Log.d(TAG, "Non-retryable failure result displayed")
                }
            }
            
            // Update back press callback state - now that transaction is complete, back navigation is allowed
            updateBackNavigationState(true)
            
            Log.d(TAG, "Result displayed successfully - query navigation enabled")
        }
    }
    
    /**
     * Show PaymentError result
     * This handles SDK PaymentError cases with specific error information
     */
    fun showPaymentError(errorCode: String, errorMessage: String, orderNo: String?, transactionId: String?, suggestion: String?) {
        runOnUiThread {
            Log.d(TAG, "Showing PaymentError - Code: $errorCode, Message: $errorMessage")
            
            // Update transaction status in repository for error case
            updateTransactionStatus(null, errorCode, errorMessage)
            
            // Hide progress card and show result card
            progressCard.visibility = View.GONE
            resultCard.visibility = View.VISIBLE
            
            // Hide abort button (not needed after error)
            abortButton.visibility = View.GONE
            
            // Show payment error result
            showPaymentErrorResult(errorCode, errorMessage, orderNo, transactionId, suggestion)
            
            // Show retry and query buttons for payment errors
            retryButton.visibility = View.VISIBLE
            retryButton.text = "Retry"
            queryButton.visibility = View.VISIBLE
            
            // Update back press callback state - now that transaction is complete, back navigation is allowed
            updateBackNavigationState(true)
            
            Log.d(TAG, "PaymentError displayed successfully")
        }
    }
    
    /**
     * Check if a transaction is retryable based on error code
     * 
     * Based on new error code specification:
     * - 301-305: Can retry with same transactionRequestId
     * - 306: Need to query status first
     * - 307-311, 399: Must use new transactionRequestId
     * - 309: Transaction terminated, should not retry
     */
    private fun isTransactionRetryable(errorCode: String?): Boolean {
        if (errorCode == null) return true
        
        // Check for non-retryable transaction states
        return when (errorCode) {
            // Transaction terminated - should not retry
            Constants.ErrorCodes.TRANSACTION_TERMINATED -> false
            
            // Abort/cancel operations - should not retry
            "ABORT", "CANCEL" -> false
            
            // All other errors are potentially retryable
            // (though some may require new transactionRequestId)
            else -> !errorCode.contains("ABORT", ignoreCase = true) &&
                    !errorCode.contains("CANCEL", ignoreCase = true) &&
                    !errorCode.contains("VOID", ignoreCase = true)
        }
    }
    
    /**
     * Show success result
     */
    private fun showSuccessResult(result: PaymentResult) {
        // Set success icon
        resultIcon.setImageResource(R.drawable.ic_check_circle)
        resultIcon.setColorFilter(resources.getColor(R.color.success_green, null))
        
        // Set result text based on transaction type
        val resultTitle = "Approved"
        
        resultText.text = resultTitle
        resultText.setTextColor(resources.getColor(R.color.success_green, null))
        
        // Create formatted result details with key-value pairs
        createFormattedResultDetails(result)
    }

    /**
     * Show transaction failure result (code=0 but transactionStatus != SUCCESS)
     * This handles cases where the request was successful but the transaction failed
     */
    private fun showTransactionFailureResult(result: PaymentResult) {
        // Set error icon
        resultIcon.setImageResource(R.drawable.ic_close)
        resultIcon.setColorFilter(resources.getColor(R.color.error_red, null))
        
        // Set result text based on transaction status
        val resultTitle = when (result.transactionStatus?.uppercase()) {
            "FAILED" -> "Transaction Failed"
            "DECLINED" -> "Transaction Declined"
            "CANCELLED" -> "Transaction Cancelled"
            "TIMEOUT" -> "Transaction Timeout"
            else -> "Transaction Failed"
        }
        
        resultText.text = resultTitle
        resultText.setTextColor(resources.getColor(R.color.error_red, null))
        
        // Create formatted transaction failure details
        createFormattedTransactionFailureDetails(result)
    }
    
    /**
     * Show request failure result (code != "100")
     * This handles cases where the request failed (any code other than "100")
     */
    private fun showRequestFailureResult(result: PaymentResult) {
        // Set error icon
        resultIcon.setImageResource(R.drawable.ic_close)
        resultIcon.setColorFilter(resources.getColor(R.color.error_red, null))
        
        // Set result text
        resultText.text = "Transaction Failed"
        resultText.setTextColor(resources.getColor(R.color.error_red, null))
        
        // Create formatted request failure details
        createFormattedRequestFailureDetails(result)
    }
    
    /**
     * Show request failure result with error code and message (no PaymentResult)
     */
    private fun showRequestFailureResult(errorCode: String, errorMessage: String) {
        // Set error icon
        resultIcon.setImageResource(R.drawable.ic_close)
        resultIcon.setColorFilter(resources.getColor(R.color.error_red, null))
        
        // Set result text based on error type
        val resultTitle = when {
            errorCode.contains("ABORT", ignoreCase = true) -> "Transaction Aborted"
            errorCode.contains("CANCEL", ignoreCase = true) -> "Transaction Cancelled"
            errorCode.contains("TIMEOUT", ignoreCase = true) -> "Connection Timeout"
            errorCode.contains("CONNECTION", ignoreCase = true) -> "Connection Error"
            else -> "Request Failed"
        }
        
        resultText.text = resultTitle
        resultText.setTextColor(resources.getColor(R.color.error_red, null))
        
        // Create formatted error details with key-value pairs
        createFormattedErrorDetails(errorCode, errorMessage)
    }
    
    /**
     * Show payment error result (SDK PaymentError)
     * This handles PaymentError cases with specific error fields
     */
    private fun showPaymentErrorResult(errorCode: String, errorMessage: String, orderNo: String?, transactionId: String?, suggestion: String?) {
        // Set error icon
        resultIcon.setImageResource(R.drawable.ic_close)
        resultIcon.setColorFilter(resources.getColor(R.color.error_red, null))
        
        // Set result text based on error code
        val resultTitle = when {
            errorCode.startsWith("E01", ignoreCase = true) -> "Payment Error"
            errorCode.startsWith("E10", ignoreCase = true) -> "Connection Error"
            errorCode.startsWith("E30", ignoreCase = true) -> "Transaction Error"
            else -> "Payment Error"
        }
        
        resultText.text = resultTitle
        resultText.setTextColor(resources.getColor(R.color.error_red, null))
        
        // Create formatted payment error details
        createFormattedPaymentErrorDetails(errorCode, errorMessage, orderNo, transactionId, suggestion)
    }
    
    /**
     * Create formatted result details with key-value pairs for success results
     */
    private fun createFormattedResultDetails(result: PaymentResult) {
        // Show the details container
        resultDetailsLayout.visibility = View.VISIBLE
        
        // Hide all detail rows initially
        transactionIdLayout.visibility = View.GONE
        authCodeLayout.visibility = View.GONE
        completedTimeLayout.visibility = View.GONE
        resultAmountLayout.visibility = View.GONE
        resultMessageLayout.visibility = View.GONE
        
        // Reset labels to default values
        labelTransactionIdText.text = "Transaction ID"
        labelAuthCodeText.text = "Auth Code"
        labelCompletedTimeText.text = "Completed"
        labelAmountText.text = "Amount"
        labelMessageText.text = "Message"
        
        // Special handling for Batch Close - display BatchCloseInfo details
        if (transaction.type == TransactionType.BATCH_CLOSE && result.batchCloseInfo != null) {
            val batchInfo = result.batchCloseInfo
            
            // Show Batch No in Transaction ID field
            transactionIdLayout.visibility = View.VISIBLE
            labelTransactionIdText.text = "Batch No"
            detailTransactionIdText.text = result.batchNo?.toString() ?: "--"
            
            // Show Total Count in Auth Code field
            authCodeLayout.visibility = View.VISIBLE
            labelAuthCodeText.text = "Total Count"
            detailAuthCodeText.text = batchInfo.totalCount.toString()
            
            // Show Close Time in Completed field
            completedTimeLayout.visibility = View.VISIBLE
            labelCompletedTimeText.text = "Close Time"
            detailCompletedTimeText.text = batchInfo.closeTime ?: "--"
            
            // Show Total Amount
            resultAmountLayout.visibility = View.VISIBLE
            labelAmountText.text = "Total Amount"
            detailAmountText.text = formatAmountWithCurrency(batchInfo.totalAmount, transaction.currency)
            
            // Build detailed message with all batch info fields
            val batchDetails = buildString {
                // Add tip if present
                if (batchInfo.totalTip > BigDecimal.ZERO) {
                    append("Total Tip: ${formatAmountWithCurrency(batchInfo.totalTip, transaction.currency)}\n")
                }
                
                // Add tax if present
                if (batchInfo.totalTax > BigDecimal.ZERO) {
                    append("Total Tax: ${formatAmountWithCurrency(batchInfo.totalTax, transaction.currency)}\n")
                }
                
                // Add surcharge if present
                if (batchInfo.totalSurchargeAmount > BigDecimal.ZERO) {
                    append("Total Surcharge: ${formatAmountWithCurrency(batchInfo.totalSurchargeAmount, transaction.currency)}\n")
                }
                
                // Add service fee if present
                if (batchInfo.totalServiceFee > BigDecimal.ZERO) {
                    append("Total Service Fee: ${formatAmountWithCurrency(batchInfo.totalServiceFee, transaction.currency)}\n")
                }
                
                // Add cash discount if present
                if (batchInfo.cashDiscount > BigDecimal.ZERO) {
                    append("Cash Discount: ${formatAmountWithCurrency(batchInfo.cashDiscount, transaction.currency)}\n")
                }
                
                // Remove trailing newline if present
                if (isNotEmpty() && endsWith("\n")) {
                    deleteCharAt(length - 1)
                }
            }
            
            // Show batch details in message field if there are any additional amounts
            if (batchDetails.isNotEmpty()) {
                resultMessageLayout.visibility = View.VISIBLE
                labelMessageText.text = "Amount Details"
                detailMessageText.text = batchDetails
            }
            
            return
        }
        
        // Standard handling for non-Batch Close transactions
        
        // Show and populate Transaction ID if available, otherwise show "--"
        transactionIdLayout.visibility = View.VISIBLE
        detailTransactionIdText.text = result.transactionId ?: "--"
        
        // Show and populate Auth Code if available, otherwise show "--"
        authCodeLayout.visibility = View.VISIBLE
        detailAuthCodeText.text = result.authCode ?: "--"
        
        // Show and populate Completed Time if available, otherwise show "--"
        completedTimeLayout.visibility = View.VISIBLE
        detailCompletedTimeText.text = result.completeTime ?: "--"
        
        // Add amount information for relevant transaction types (exclude QUERY)
        if (transaction.type != TransactionType.QUERY) {
            resultAmountLayout.visibility = View.VISIBLE
            result.amount?.transAmount?.let { amount ->
                detailAmountText.text = formatAmountWithCurrency(amount, transaction.currency)
            } ?: run {
                detailAmountText.text = "--"
            }
        }
        
        // Show and populate Message if available, otherwise show "--"
        resultMessageLayout.visibility = View.VISIBLE
        detailMessageText.text = result.message ?: "--"
    }
    
    /**
     * Create formatted error details with key-value pairs for failure results
     */
    private fun createFormattedErrorDetails(errorCode: String, errorMessage: String) {
        // Show the details container
        resultDetailsLayout.visibility = View.VISIBLE
        
        // Reset labels to default values
        labelTransactionIdText.text = "Transaction ID"
        labelAuthCodeText.text = "Auth Code"
        labelCompletedTimeText.text = "Completed"
        labelAmountText.text = "Amount"
        labelMessageText.text = "Message"
        
        // Hide all detail rows initially
        transactionIdLayout.visibility = View.GONE
        authCodeLayout.visibility = View.GONE
        completedTimeLayout.visibility = View.GONE
        resultAmountLayout.visibility = View.GONE
        resultMessageLayout.visibility = View.VISIBLE
        
        // Show error message
        detailMessageText.text = errorMessage
        
        // Show transaction ID as "--" since it's null for failed transactions
        transactionIdLayout.visibility = View.VISIBLE
        detailTransactionIdText.text = "--"
        
        // Show auth code as "--" since it's null for failed transactions
        authCodeLayout.visibility = View.VISIBLE
        detailAuthCodeText.text = "--"
    }
    
    /**
     * Create formatted transaction failure details (code=0 but transactionStatus != SUCCESS)
     */
    private fun createFormattedTransactionFailureDetails(result: PaymentResult) {
        // Show the details container
        resultDetailsLayout.visibility = View.VISIBLE
        
        // Reset labels to default values
        labelTransactionIdText.text = "Transaction ID"
        labelAuthCodeText.text = "Auth Code"
        labelCompletedTimeText.text = "Completed"
        labelAmountText.text = "Amount"
        labelMessageText.text = "Message"
        
        // Show all detail rows
        transactionIdLayout.visibility = View.VISIBLE
        authCodeLayout.visibility = View.VISIBLE
        completedTimeLayout.visibility = View.VISIBLE
        resultAmountLayout.visibility = View.VISIBLE
        resultMessageLayout.visibility = View.VISIBLE
        
        // Show transaction ID (should be null for failed transactions)
        detailTransactionIdText.text = result.transactionId ?: "--"
        
        // Show auth code (should be null for failed transactions)
        detailAuthCodeText.text = result.authCode ?: "--"
        
        // Show completed time (should be null for failed transactions)
        detailCompletedTimeText.text = result.completeTime ?: "--"
        
        // Show amount
        result.amount?.transAmount?.let { amount ->
            detailAmountText.text = formatAmountWithCurrency(amount, transaction.currency)
        } ?: run {
            detailAmountText.text = "--"
        }
        
        // Show transaction result message (this is the important part for transaction failures)
        detailMessageText.text = result.transactionResultMsg ?: result.message ?: "--"
    }
    
    /**
     * Create formatted request failure details (code != "100")
     */
    private fun createFormattedRequestFailureDetails(result: PaymentResult) {
        // Show the details container
        resultDetailsLayout.visibility = View.VISIBLE
        
        // Show relevant detail rows
        transactionIdLayout.visibility = View.VISIBLE
        authCodeLayout.visibility = View.VISIBLE
        completedTimeLayout.visibility = View.GONE
        resultAmountLayout.visibility = View.GONE
        resultMessageLayout.visibility = View.VISIBLE
        
        // Show code in transaction ID field for request failures
        detailTransactionIdText.text = "Code: ${result.code}"
        
        // Show trace ID in auth code field if available
        detailAuthCodeText.text = result.traceId ?: "--"
        
        // Show error message
        detailMessageText.text = result.message ?: "--"
    }
    
    /**
     * Create formatted payment error details (SDK PaymentError)
     */
    private fun createFormattedPaymentErrorDetails(errorCode: String, errorMessage: String, orderNo: String?, transactionId: String?, suggestion: String?) {
        // Show the details container
        resultDetailsLayout.visibility = View.VISIBLE
        
        // Only show Transaction ID, Error Code, and Message for PaymentError
        transactionIdLayout.visibility = View.VISIBLE
        authCodeLayout.visibility = View.VISIBLE
        completedTimeLayout.visibility = View.GONE
        resultAmountLayout.visibility = View.GONE
        resultMessageLayout.visibility = View.VISIBLE
        
        // Set custom labels for PaymentError
        labelTransactionIdText.text = "Transaction ID"
        labelAuthCodeText.text = "Error Code"
        labelMessageText.text = "Message"
        
        // Show transaction ID if available, otherwise show "--"
        detailTransactionIdText.text = transactionId ?: "--"
        
        // Show error code
        detailAuthCodeText.text = errorCode
        
        // Show error message
        detailMessageText.text = errorMessage
    }
    
    /**
     * Append a key-value pair with proper formatting
     */
    private fun StringBuilder.appendKeyValue(key: String, value: String) {
        val maxKeyLength = 20 // Maximum length for key alignment
        val paddedKey = key.padEnd(maxKeyLength, ' ')
        append("$paddedKey: $value\n")
    }

    /**
     * This callback handles the abort operation progress and results
     *
     * @return PaymentCallback instance for abort operations
     */
    private fun createAbortCallback(): PaymentCallback {
        return object : PaymentCallback {
            override fun onSuccess(result: PaymentResult) {
                Log.d(TAG, "Abort callback - Success: ${result.message}")
                runOnUiThread {
                    // Show abort success result
                    showResult(result, null, null)
                }
            }
            
            override fun onFailure(code: String, message: String) {
                Log.d(TAG, "Abort callback - Failure: $code - $message")
                runOnUiThread {
                    // Handle abort failure - restore original transaction state
                    handleAbortFailure(code, message)
                }
            }
            
            override fun onProgress(status: String, message: String) {
                Log.d(TAG, "Abort callback - Progress: $status - $message")
                runOnUiThread {
                    // Update progress with abort status
                    updateProgress(status, message)
                }
            }
        }
    }
    
    /**
     * Handle abort operation failure
     * Restores the UI to the original transaction processing state
     * Implements comprehensive error recovery and user feedback
     * 
     * @param errorCode Error code from abort failure
     * @param errorMessage Error message from abort failure
     */
    private fun handleAbortFailure(errorCode: String, errorMessage: String) {
        Log.w(TAG, "Abort failed: $errorCode - $errorMessage")
        
        // Re-enable abort button
        abortButton.isEnabled = true
        abortButton.text = "Abort Transaction"
        
        // Restore processing state
        updateProgress("PROCESSING", "Abort failed, transaction continues")
        
        // Show detailed error information to user
        showAbortFailureDialog(errorCode, errorMessage)
        
        Log.d(TAG, "Abort failure handled, transaction restored to processing state")
    }
    
    /**
     * Show abort failure dialog with error details and recovery options
     * Provides user with clear information about the abort failure and next steps
     * 
     * @param errorCode Error code from abort failure
     * @param errorMessage Error message from abort failure
     */
    private fun showAbortFailureDialog(errorCode: String, errorMessage: String) {
        val title = "Abort Failed"
        val message = buildString {
            append("Failed to abort the transaction.\n\n")
            append("Error Code: $errorCode\n")
            append("Error Message: $errorMessage\n\n")
            append("The transaction will continue processing. ")
            append("You can wait for it to complete or try aborting again.")
        }
        
        safeShowDialog {
            DialogUtils.createErrorDialog(
                context = this,
                title = title,
                message = message,
                errorCode = errorCode,
                onRetryClick = {
                    // Retry abort operation
                    Log.d(TAG, "User chose to retry abort operation")
                    handleAbortButtonClick()
                },
                onCancelClick = {
                    // Continue with original transaction
                    Log.d(TAG, "User chose to continue with original transaction")
                    updateProgress("PROCESSING", "Continuing with original transaction...")
                }
            )
        }
    }
    
    /**
     * Safely show a dialog, checking if activity is still valid
     * Prevents BadTokenException when activity is finishing or destroyed
     * 
     * @param dialogBuilder Function that creates the dialog
     */
    private fun safeShowDialog(dialogBuilder: () -> AlertDialog) {
        if (isFinishing || isDestroyed) {
            Log.w(TAG, "Activity is finishing or destroyed, cannot show dialog")
            return
        }
        
        try {
            val dialog = dialogBuilder()
            dialog.show()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show dialog", e)
        }
    }
    
    /**
     * Handle connection errors during transaction processing
     * Provides appropriate error messages and recovery options based on error type
     * 
     * @param errorCode Connection error code
     * @param errorMessage Connection error message
     */
    private fun handleConnectionError(errorCode: String, errorMessage: String) {
        Log.e(TAG, "Connection error during transaction: $errorCode - $errorMessage")
        
        when (errorCode) {
            Constants.ConnectionErrorCodes.CONNECTION_LOST -> {
                handleConnectionLostError(errorMessage)
            }
            Constants.ConnectionErrorCodes.CONNECTION_TIMEOUT -> {
                handleConnectionTimeoutError(errorMessage)
            }
            Constants.ConnectionErrorCodes.TARGET_APP_CRASHED -> {
                handleTargetAppCrashedError(errorMessage)
            }
            Constants.ConnectionErrorCodes.SERVICE_DISCONNECTED -> {
                handleServiceDisconnectedError(errorMessage)
            }
            else -> {
                handleGenericConnectionError(errorCode, errorMessage)
            }
        }
    }
    
    /**
     * Handle connection lost error
     * Shows connection lost dialog with reconnection options
     * 
     * @param errorMessage Error message details
     */
    private fun handleConnectionLostError(errorMessage: String) {
        val title = Constants.Messages.TITLE_CONNECTION_LOST
        val message = buildString {
            append("Connection to the payment terminal was lost during transaction processing.\n\n")
            append("Error: $errorMessage\n\n")
            append("The transaction status is unknown. You can:\n")
            append("• Query the transaction status to check if it completed\n")
            append("• Return to main screen and check transaction history\n")
            append("• Retry the connection if the terminal is available")
        }
        
        safeShowDialog {
            DialogUtils.createErrorDialog(
                context = this,
                title = title,
                message = message,
                errorCode = Constants.ConnectionErrorCodes.CONNECTION_LOST,
                onRetryClick = {
                    // Query transaction status
                    Log.d(TAG, "User chose to query transaction status after connection lost")
                    queryTransactionStatus()
                },
                onCancelClick = {
                    // Return to main screen
                    Log.d(TAG, "User chose to return to main screen after connection lost")
                    showResult(null, Constants.ConnectionErrorCodes.CONNECTION_LOST, errorMessage)
                }
            )
        }
    }
    
    /**
     * Handle connection timeout error
     * Shows timeout dialog with retry and query options
     * 
     * @param errorMessage Error message details
     */
    private fun handleConnectionTimeoutError(errorMessage: String) {
        val title = "Connection Timeout"
        val message = buildString {
            append("The connection to the payment terminal timed out.\n\n")
            append("Error: $errorMessage\n\n")
            append("This may be due to:\n")
            append("• Network connectivity issues\n")
            append("• Terminal being busy or unresponsive\n")
            append("• Transaction taking longer than expected\n\n")
            append("You can query the transaction status to check if it completed.")
        }
        
        DialogUtils.createErrorDialog(
            context = this,
            title = title,
            message = message,
            errorCode = Constants.ConnectionErrorCodes.CONNECTION_TIMEOUT,
            onRetryClick = {
                // Query transaction status
                Log.d(TAG, "User chose to query transaction status after timeout")
                queryTransactionStatus()
            },
            onCancelClick = {
                // Show timeout result
                Log.d(TAG, "User chose to return after timeout")
                showResult(null, Constants.ConnectionErrorCodes.CONNECTION_TIMEOUT, errorMessage)
            }
        ).show()
    }
    
    /**
     * Handle target app crashed error
     * Shows app crash dialog with recovery options
     * 
     * @param errorMessage Error message details
     */
    private fun handleTargetAppCrashedError(errorMessage: String) {
        val title = "Payment App Error"
        val message = buildString {
            append("The payment processing app (Tapro) encountered an error or crashed.\n\n")
            append("Error: $errorMessage\n\n")
            append("The transaction status is unknown. Please:\n")
            append("• Check if the Tapro app is running\n")
            append("• Query the transaction status to verify completion\n")
            append("• Contact support if the issue persists")
        }
        
        DialogUtils.createErrorDialog(
            context = this,
            title = title,
            message = message,
            errorCode = Constants.ConnectionErrorCodes.TARGET_APP_CRASHED,
            onRetryClick = {
                // Query transaction status
                Log.d(TAG, "User chose to query transaction status after app crash")
                queryTransactionStatus()
            },
            onCancelClick = {
                // Show crash result
                Log.d(TAG, "User chose to return after app crash")
                showResult(null, Constants.ConnectionErrorCodes.TARGET_APP_CRASHED, errorMessage)
            }
        ).show()
    }
    
    /**
     * Handle service disconnected error
     * Shows service disconnection dialog with reconnection options
     * 
     * @param errorMessage Error message details
     */
    private fun handleServiceDisconnectedError(errorMessage: String) {
        val title = "Service Disconnected"
        val message = buildString {
            append("The payment service was disconnected during transaction processing.\n\n")
            append("Error: $errorMessage\n\n")
            append("This may be due to:\n")
            append("• System resource constraints\n")
            append("• Service being stopped by the system\n")
            append("• Application being backgrounded\n\n")
            append("You can query the transaction status to check completion.")
        }
        
        DialogUtils.createErrorDialog(
            context = this,
            title = title,
            message = message,
            errorCode = Constants.ConnectionErrorCodes.SERVICE_DISCONNECTED,
            onRetryClick = {
                // Query transaction status
                Log.d(TAG, "User chose to query transaction status after service disconnect")
                queryTransactionStatus()
            },
            onCancelClick = {
                // Show disconnect result
                Log.d(TAG, "User chose to return after service disconnect")
                showResult(null, Constants.ConnectionErrorCodes.SERVICE_DISCONNECTED, errorMessage)
            }
        ).show()
    }
    
    /**
     * Handle generic connection errors
     * Shows generic error dialog for unspecified connection issues
     * 
     * @param errorCode Error code
     * @param errorMessage Error message details
     */
    private fun handleGenericConnectionError(errorCode: String, errorMessage: String) {
        val title = Constants.Messages.TITLE_CONNECTION_ERROR
        val message = buildString {
            append("A connection error occurred during transaction processing.\n\n")
            append("Error Code: $errorCode\n")
            append("Error Message: $errorMessage\n\n")
            append("The transaction status is unknown. You can query the status or return to the main screen.")
        }
        
        DialogUtils.createErrorDialog(
            context = this,
            title = title,
            message = message,
            errorCode = errorCode,
            onRetryClick = {
                // Query transaction status
                Log.d(TAG, "User chose to query transaction status after generic error")
                queryTransactionStatus()
            },
            onCancelClick = {
                // Show error result
                Log.d(TAG, "User chose to return after generic error")
                showResult(null, errorCode, errorMessage)
            }
        ).show()
    }
    
    /**
     * Query transaction status to check completion after errors
     * Attempts to determine the final status of the transaction after connection issues
     */
    private fun queryTransactionStatus() {
        Log.d(TAG, "Querying transaction status for: ${transaction.transactionRequestId}")
        
        // Update UI to show querying state
        updateProgress("QUERYING", "Checking transaction status...")
        
        try {
            val paymentService = com.sunmi.tapro.taplink.demo.service.TaplinkPaymentService.getInstance()
            
            // Execute query with current transaction request ID
            paymentService.executeQuery(
                transactionRequestId = transaction.transactionRequestId,
                callback = createQueryCallback()
            )
            
            Log.d(TAG, "Query request sent to PaymentService")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to execute query", e)
            
            // Show query failure and allow return
            val errorMessage = "Failed to query transaction status: ${e.message}"
            showResult(null, "QUERY_FAILED", errorMessage)
        }
    }
    
    /**
     * Create a PaymentCallback specifically for query operations
     * Handles query results to determine final transaction status
     * 
     * @return PaymentCallback instance for query operations
     */
    private fun createQueryCallback(): PaymentCallback {
        return object : PaymentCallback {
            override fun onSuccess(result: PaymentResult) {
                Log.d(TAG, "Query callback - Success: ${result.message}")
                runOnUiThread {
                    // Query succeeded, show the actual transaction result
                    showResult(result, null, null)
                }
            }
            
            override fun onFailure(code: String, message: String) {
                Log.d(TAG, "Query callback - Failure: $code - $message")
                runOnUiThread {
                    // Query failed, show query failure result
                    val errorMessage = "Failed to determine transaction status: $message"
                    showResult(null, code, errorMessage)
                }
            }
            
            override fun onProgress(status: String, message: String) {
                Log.d(TAG, "Query callback - Progress: $status - $message")
                runOnUiThread {
                    // Update progress with query status
                    updateProgress(status, message)
                }
            }
        }
    }
    
    /**
     * Handle SDK errors during transaction processing
     * Provides appropriate error handling for various SDK error conditions
     * 
     * @param errorCode SDK error code
     * @param errorMessage SDK error message
     */
    private fun handleSdkError(errorCode: String, errorMessage: String) {
        Log.e(TAG, "SDK error during transaction: $errorCode - $errorMessage")
        
        // Check if this is a connection-related error
        if (isConnectionError(errorCode)) {
            handleConnectionError(errorCode, errorMessage)
            return
        }
        
        // Handle other SDK errors
        val title = "Transaction Error"
        val message = buildString {
            append("An error occurred during transaction processing.\n\n")
            append("Error Code: $errorCode\n")
            append("Error Message: $errorMessage\n\n")
            
            // Provide specific guidance based on error type
            when {
                errorCode.contains("TIMEOUT", ignoreCase = true) -> {
                    append("The transaction timed out. This may be due to:\n")
                    append("• Network connectivity issues\n")
                    append("• Terminal being unresponsive\n")
                    append("• Transaction taking longer than expected\n\n")
                    append("You can query the transaction status to check if it completed.")
                }
                errorCode.contains("DECLINED", ignoreCase = true) -> {
                    append("The transaction was declined. This is a normal result and no further action is needed.")
                }
                errorCode.contains("CANCELLED", ignoreCase = true) -> {
                    append("The transaction was cancelled. This may have been done by the user or the system.")
                }
                else -> {
                    append("Please try again or contact support if the issue persists.")
                }
            }
        }
        
        // Show error result directly for non-retryable errors
        if (errorCode.contains("DECLINED", ignoreCase = true) || 
            errorCode.contains("CANCELLED", ignoreCase = true)) {
            showResult(null, errorCode, errorMessage)
        } else {
            // Show error dialog with query option for other errors
            DialogUtils.createErrorDialog(
                context = this,
                title = title,
                message = message,
                errorCode = errorCode,
                onRetryClick = {
                    // Query transaction status for timeout and unknown errors
                    Log.d(TAG, "User chose to query transaction status after SDK error")
                    queryTransactionStatus()
                },
                onCancelClick = {
                    // Show error result
                    Log.d(TAG, "User chose to return after SDK error")
                    showResult(null, errorCode, errorMessage)
                }
            ).show()
        }
    }
    
    /**
     * Check if an error code represents a connection-related error
     * Supports both new error code format (21x-25x) and legacy format (Cxx)
     * 
     * @param errorCode Error code to check
     * @return true if the error is connection-related, false otherwise
     */
    private fun isConnectionError(errorCode: String): Boolean {
        // New error code format (21x-25x range for connection errors)
        if (errorCode.startsWith("21") || errorCode.startsWith("22") || 
            errorCode.startsWith("23") || errorCode.startsWith("24") || 
            errorCode.startsWith("25")) {
            return true
        }
        
        // Legacy error code format
        return errorCode in listOf(
            Constants.ConnectionErrorCodes.CONNECTION_LOST,
            Constants.ConnectionErrorCodes.CONNECTION_TIMEOUT,
            Constants.ConnectionErrorCodes.CONNECTION_FAILED,
            Constants.ConnectionErrorCodes.TARGET_APP_CRASHED,
            Constants.ConnectionErrorCodes.SERVICE_DISCONNECTED,
            Constants.ConnectionErrorCodes.SERVICE_BINDING_FAILED
        )
    }
    
    /**
     * Ensure error state allows proper query navigation
     * Updates UI state to allow user to query transaction status even after errors
     */
    private fun ensureErrorStateAllowsReturn() {
        // Make sure query button is visible and back navigation is allowed
        queryButton.visibility = View.VISIBLE
        updateBackNavigationState(true)
        
        Log.d(TAG, "Error state configured to allow query navigation")
    }
    
    /**
     * Start the actual payment transaction based on transaction type
     * This method is called after UI setup to initiate the payment request
     */
    private fun startPaymentTransaction() {
        Log.d(TAG, "Starting payment transaction: ${transaction.type} for ${transaction.amount} ${transaction.currency}")
        
        try {
            val paymentService = com.sunmi.tapro.taplink.demo.service.TaplinkPaymentService.getInstance()
            val callback = createPaymentCallback()
            
            when (transaction.type) {
                TransactionType.SALE -> {
                    paymentService.executeSale(
                        referenceOrderId = transaction.referenceOrderId ?: "",
                        transactionRequestId = transaction.transactionRequestId,
                        amount = transaction.amount,
                        currency = transaction.currency,
                        description = "Sale transaction from TransactionProgressActivity",
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
                        description = "Auth transaction from TransactionProgressActivity",
                        callback = callback
                    )
                }
                
                TransactionType.FORCED_AUTH -> {
                    paymentService.executeForcedAuth(
                        referenceOrderId = transaction.referenceOrderId ?: "",
                        transactionRequestId = transaction.transactionRequestId,
                        amount = transaction.amount,
                        currency = transaction.currency,
                        description = "Forced Auth transaction from TransactionProgressActivity",
                        tipAmount = transaction.tipAmount,
                        taxAmount = transaction.taxAmount,
                        callback = callback
                    )
                }
                
                TransactionType.REFUND -> {
                    // For refund, we need original transaction ID
                    // This should be passed from the calling activity
                    val originalTransactionId = intent.getStringExtra("original_transaction_id") ?: ""
                    paymentService.executeRefund(
                        referenceOrderId = transaction.referenceOrderId ?: "",
                        transactionRequestId = transaction.transactionRequestId,
                        originalTransactionId = originalTransactionId,
                        amount = transaction.amount,
                        currency = transaction.currency,
                        description = "Refund transaction from TransactionProgressActivity",
                        reason = "User requested refund",
                        callback = callback
                    )
                }
                
                TransactionType.VOID -> {
                    // For void, we need original transaction ID
                    val originalTransactionId = intent.getStringExtra("original_transaction_id") ?: ""
                    paymentService.executeVoid(
                        referenceOrderId = transaction.referenceOrderId ?: "",
                        transactionRequestId = transaction.transactionRequestId,
                        originalTransactionId = originalTransactionId,
                        description = "Void transaction from TransactionProgressActivity",
                        reason = "User requested void",
                        callback = callback
                    )
                }
                
                TransactionType.POST_AUTH -> {
                    // For post auth, we need original transaction ID
                    val originalTransactionId = intent.getStringExtra("original_transaction_id") ?: ""
                    paymentService.executePostAuth(
                        referenceOrderId = transaction.referenceOrderId ?: "",
                        transactionRequestId = transaction.transactionRequestId,
                        originalTransactionId = originalTransactionId,
                        amount = transaction.amount,
                        currency = transaction.currency,
                        description = "Post Auth transaction from TransactionProgressActivity",
                        surchargeAmount = transaction.surchargeAmount,
                        tipAmount = transaction.tipAmount,
                        taxAmount = transaction.taxAmount,
                        cashbackAmount = transaction.cashbackAmount,
                        serviceFee = transaction.serviceFee,
                        callback = callback
                    )
                }
                
                TransactionType.INCREMENT_AUTH -> {
                    // For incremental auth, we need original transaction ID
                    val originalTransactionId = intent.getStringExtra("original_transaction_id") ?: ""
                    paymentService.executeIncrementalAuth(
                        referenceOrderId = transaction.referenceOrderId ?: "",
                        transactionRequestId = transaction.transactionRequestId,
                        originalTransactionId = originalTransactionId,
                        amount = transaction.amount,
                        currency = transaction.currency,
                        description = "Incremental Auth transaction from TransactionProgressActivity",
                        callback = callback
                    )
                }
                
                TransactionType.TIP_ADJUST -> {
                    // For tip adjust, we need original transaction ID
                    val originalTransactionId = intent.getStringExtra("original_transaction_id") ?: ""
                    paymentService.executeTipAdjust(
                        referenceOrderId = transaction.referenceOrderId ?: "",
                        transactionRequestId = transaction.transactionRequestId,
                        originalTransactionId = originalTransactionId,
                        tipAmount = transaction.tipAmount ?: BigDecimal.ZERO,
                        description = "Tip Adjust transaction from TransactionProgressActivity",
                        callback = callback
                    )
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
                        description = "Batch Close transaction from TransactionProgressActivity",
                        callback = callback
                    )
                }
            }
            
            Log.d(TAG, "Payment transaction started successfully")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start payment transaction", e)
            
            // Show error result
            showResult(null, "TRANSACTION_START_ERROR", "Failed to start transaction: ${e.message}")
        }
    }

    /**
     * Create a PaymentCallback that forwards updates to this activity
     * This method creates a callback that can be used by MainActivity to forward
     * SDK callbacks to the progress activity with enhanced error handling
     * 
     * @return PaymentCallback instance that updates this activity
     */
    fun createPaymentCallback(): PaymentCallback {
        return object : PaymentCallback {
            override fun onSuccess(result: PaymentResult) {
                Log.d(TAG, "Payment callback - Success: ${result.message}")
                showResult(result, null, null)
            }
            
            override fun onFailure(code: String, message: String) {
                Log.d(TAG, "Payment callback - Failure: $code - $message")
                
                // Use showPaymentError for better error handling and display
                // This provides more detailed error information to the user
                showPaymentError(code, message, null, null, null)
            }
            
            override fun onProgress(status: String, message: String) {
                Log.d(TAG, "Payment callback - Progress: $status - $message")
                updateProgress(status, message)
            }
        }
    }
    

    
    override fun onResume() {
        super.onResume()
        // Ensure current instance is set when activity resumes
        currentInstance = this
        Log.d(TAG, "TransactionProgressActivity resumed")
    }
    
    override fun onPause() {
        super.onPause()
        Log.d(TAG, "TransactionProgressActivity paused")
    }
    
    /**
     * Update transaction status in repository when transaction completes
     * This ensures that the transaction record reflects the final result
     * 
     * @param result Payment result for successful transactions
     * @param errorCode Error code for failed transactions
     * @param errorMessage Error message for failed transactions
     */
    private fun updateTransactionStatus(result: PaymentResult?, errorCode: String?, errorMessage: String?) {
        try {
            if (result != null) {
                // Success case - update with success status and result data
                com.sunmi.tapro.taplink.demo.repository.TransactionRepository.updateTransactionWithAmounts(
                    transactionRequestId = transaction.transactionRequestId,
                    status = com.sunmi.tapro.taplink.demo.model.TransactionStatus.SUCCESS,
                    transactionId = result.transactionId,
                    authCode = result.authCode,
                    orderAmount = result.amount?.orderAmount,
                    totalAmount = result.amount?.transAmount,
                    surchargeAmount = result.amount?.surchargeAmount,
                    tipAmount = result.amount?.tipAmount,
                    taxAmount = result.amount?.taxAmount,
                    cashbackAmount = result.amount?.cashbackAmount,
                    serviceFee = result.amount?.serviceFee,
                    batchNo = result.batchNo,
                    batchCloseInfo = result.batchCloseInfo?.let { bci ->
                        com.sunmi.tapro.taplink.demo.model.BatchCloseInfo(
                            totalCount = bci.totalCount,
                            totalAmount = bci.totalAmount,
                            totalTip = bci.totalTip,
                            totalTax = bci.totalTax,
                            totalSurchargeAmount = bci.totalSurchargeAmount,
                            cashDiscount = bci.cashDiscount,
                            closeTime = bci.closeTime
                        )
                    },
                    errorCode = null,
                    errorMessage = null
                )
                
                Log.d(TAG, "Transaction updated with success status: ${result.transactionId}")
            } else {
                // Failure case - update with failed status and error information
                val finalStatus = when {
                    errorCode?.contains("ABORT", ignoreCase = true) == true -> 
                        com.sunmi.tapro.taplink.demo.model.TransactionStatus.CANCELLED
                    errorCode?.contains("CANCEL", ignoreCase = true) == true -> 
                        com.sunmi.tapro.taplink.demo.model.TransactionStatus.CANCELLED
                    else -> 
                        com.sunmi.tapro.taplink.demo.model.TransactionStatus.FAILED
                }
                
                com.sunmi.tapro.taplink.demo.repository.TransactionRepository.updateTransactionStatus(
                    transactionRequestId = transaction.transactionRequestId,
                    status = finalStatus,
                    errorCode = errorCode,
                    errorMessage = errorMessage
                )
                
                Log.d(TAG, "Transaction updated with failure status: $finalStatus, Error: $errorCode")
            }
            
            Log.d(TAG, "Transaction status updated in repository successfully")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update transaction status in repository", e)
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        
        // Clear current instance when activity is destroyed
        if (currentInstance == this) {
            currentInstance = null
        }
        
        // Clean up back press callback
        if (::backPressCallback.isInitialized) {
            backPressCallback.remove()
        }
        
        Log.d(TAG, "TransactionProgressActivity destroyed")
    }
}

/**
 * Interface for handling transaction retry requests
 */
interface RetryTransactionListener {
    /**
     * Called when user requests to retry a failed transaction
     * 
     * @param transaction The original transaction to retry
     */
    fun onRetryRequested(transaction: Transaction)
}