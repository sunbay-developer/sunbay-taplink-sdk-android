package com.sunmi.tapro.taplink.demo.activity

import android.app.Activity
import android.app.AlertDialog
import android.app.ProgressDialog
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import com.sunmi.tapro.taplink.demo.R
import com.sunmi.tapro.taplink.demo.model.Transaction
import com.sunmi.tapro.taplink.demo.model.TransactionStatus
import com.sunmi.tapro.taplink.demo.model.TransactionType
import com.sunmi.tapro.taplink.demo.repository.TransactionRepository
import com.sunmi.tapro.taplink.demo.service.AppToAppPaymentService
import com.sunmi.tapro.taplink.demo.service.ConnectionListener
import com.sunmi.tapro.taplink.demo.service.PaymentCallback
import com.sunmi.tapro.taplink.demo.service.PaymentResult
import com.sunmi.tapro.taplink.demo.util.ErrorHandler
import java.text.DecimalFormat

/**
 * Main Activity
 *
 * Functions:
 * - Display connection status
 * - Amount selection (preset amount buttons + custom amount input)
 * - Initiate payment transactions
 * - Background auto-connection management
 * - Navigate to settings and transaction history pages
 */
class MainActivity : Activity() {

    companion object {
        private const val TAG = "MainActivity"
        private const val REQUEST_CODE_CONNECTION = 1001
        private const val REQUEST_CODE_TRANSACTION_LIST = 1002
    }

    // UI element references
    private lateinit var layoutTopBar: View
    private lateinit var tvConnectionStatus: TextView
    private lateinit var btnSettings: Button
    private lateinit var btnTransactionHistory: Button
    private lateinit var btnAmount10: Button
    private lateinit var btnAmount20: Button
    private lateinit var btnAmount50: Button
    private lateinit var btnAmount100: Button
    private lateinit var etCustomAmount: EditText
    private lateinit var tvSelectedAmount: TextView
    private lateinit var btnSale: Button
    private lateinit var btnAuth: Button
    private lateinit var btnForcedAuth: Button
    private lateinit var tvPaymentStatus: TextView

    // Payment service instance
    private lateinit var paymentService: AppToAppPaymentService

    // Currently selected amount
    private var selectedAmount: Double = 0.0

    // Cache last transaction type for retry
    private var lastTransactionType: TransactionType? = null

    // Amount formatter
    private val amountFormatter = DecimalFormat("$#,##0.00")

    // Payment progress dialog
    private var paymentProgressDialog: ProgressDialog? = null

    // Flag to indicate if custom amount is being programmatically updated (to avoid TextWatcher trigger)
    private var isUpdatingCustomAmount = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            Log.d(TAG, "MainActivity starting creation")
            setContentView(R.layout.activity_main)

            Log.d(TAG, "Layout file loaded successfully")


            // Initialize UI components
            initViews()
            Log.d(TAG, "UI components initialization completed")

            // Initialize payment service
            initPaymentService()
            Log.d(TAG, "Payment service initialization completed")

            // Now safe to update transaction buttons state
            updateTransactionButtonsState()
            Log.d(TAG, "Transaction buttons state updated")

            // Set up event listeners
            setupEventListeners()
            Log.d(TAG, "Event listeners setup completed")

            // Start background connection
            startBackgroundConnection()
            Log.d(TAG, "MainActivity creation completed")

        } catch (e: Exception) {
            Log.e(TAG, "MainActivity creation process failed", e)
            // Show error message to user
            Toast.makeText(this, "Application startup failed: ${e.message}", Toast.LENGTH_LONG)
                .show()
        }
    }


    /**
     * Initialize UI components
     */
    private fun initViews() {
        try {
            Log.d(TAG, "Begin initializing UI components")

            // Top bar
            layoutTopBar = findViewById(R.id.layout_top_bar)
            tvConnectionStatus = findViewById(R.id.tv_connection_status)
            btnSettings = findViewById(R.id.btn_settings)
            btnTransactionHistory = findViewById(R.id.btn_transaction_history)

            // Product selection buttons
            btnAmount10 = findViewById(R.id.btn_product_coffee)
            btnAmount20 = findViewById(R.id.btn_product_sandwich)
            btnAmount50 = findViewById(R.id.btn_product_lunch)
            btnAmount100 = findViewById(R.id.btn_product_dinner)

            // Custom amount input
            etCustomAmount = findViewById(R.id.et_custom_amount)

            // Currently selected amount display
            tvSelectedAmount = findViewById(R.id.tv_selected_amount)

            // Transaction buttons
            btnSale = findViewById(R.id.btn_sale)
            btnAuth = findViewById(R.id.btn_auth)
            btnForcedAuth = findViewById(R.id.btn_forced_auth)

            // Payment status display
            tvPaymentStatus = findViewById(R.id.tv_payment_status)

            Log.d(TAG, "All UI components found successfully")


            // Initial state
            updateSelectedAmountDisplay()
            // Note: updatePayButtonState() will be called after initPaymentService()

            Log.d(TAG, "UI components initialization completed")
        } catch (e: Exception) {
            Log.e(TAG, "UI components initialization failed", e)
            throw e
        }
    }

    /**
     * Initialize payment service
     * SDK is already initialized in TaplinkDemoApplication, only need to get service instance here
     */
    private fun initPaymentService() {
        try {
            Log.d(TAG, "Begin initializing payment service instance")

            // Get service instance (SDK already initialized in Application)
            paymentService = AppToAppPaymentService.getInstance()

            Log.d(TAG, "Payment service instance initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Payment service instance initialization failed", e)
            Toast.makeText(
                this,
                "Payment service initialization failed: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    /**
     * Set up event listeners
     */
    private fun setupEventListeners() {
        // Set up button click listeners
        btnSettings.setOnClickListener {
            openConnectionSettings()
        }

        // Transaction history button
        btnTransactionHistory.setOnClickListener {
            openTransactionHistory()
        }

        // Preset amount buttons - accumulate amount
        btnAmount10.setOnClickListener { addAmount(1.01) }
        btnAmount20.setOnClickListener { addAmount(5.99) }
        btnAmount50.setOnClickListener { addAmount(7.49) }
        btnAmount100.setOnClickListener { addAmount(10.0) }

        // Custom amount input listener
        etCustomAmount.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                handleCustomAmountChanged(s.toString())
            }
        })

        // Transaction buttons
        btnSale.setOnClickListener {
            showSaleAmountDialog()
        }

        btnAuth.setOnClickListener {
            startPayment(TransactionType.AUTH)
        }

        btnForcedAuth.setOnClickListener {
            startPayment(TransactionType.FORCED_AUTH)
        }


    }

    /**
     * Start background connection
     */
    private fun startBackgroundConnection() {
        Log.d(TAG, "Start background connection")

        // Update connection status display
        updateConnectionStatus("Connecting...", false)

        // Connect to payment terminal (SDK already initialized in initPaymentService)
        connectToPaymentService()
    }

    /**
     * Connect to payment service
     * Call TaplinkSDK.connect to establish connection
     */
    private fun connectToPaymentService() {
        paymentService.connect(object : ConnectionListener {
            override fun onConnected(deviceId: String, taproVersion: String) {
                runOnUiThread {
                    Log.d(TAG, "Connected - Device ID: $deviceId, Version: $taproVersion")
                    updateConnectionStatus("Connected (v$taproVersion)", true)
                }
            }

            override fun onDisconnected(reason: String) {
                runOnUiThread {
                    Log.d(TAG, "Disconnected - Reason: $reason")
                    updateConnectionStatus("Not Connected", false)
                }
            }

            override fun onError(code: String, message: String) {
                runOnUiThread {
                    Log.e(TAG, "Connection error - Code: $code, Message: $message")
                    val errorMsg = when (code) {
                        "C22" -> "Tapro not installed"
                        "S03" -> "Signature verification failed"
                        else -> "Connection failed: $message"
                    }
                    updateConnectionStatus(errorMsg, false)

                    // Use unified error handling
                    ErrorHandler.handleConnectionError(
                        context = this@MainActivity,
                        errorCode = code,
                        errorMessage = message,
                        onRetry = {
                            // Retry connection
                            connectToPaymentService()
                        }
                    )
                }
            }
        })
    }

    /**
     * Update connection status display
     */
    private fun updateConnectionStatus(status: String, connected: Boolean) {
        tvConnectionStatus.text = status

        // Update transaction button state based on connection status
        updateTransactionButtonsState()
    }

    /**
     * Add amount
     */
    private fun addAmount(amount: Double) {
        selectedAmount += amount

        // Clear custom amount input field (mark as programmatically updated to avoid TextWatcher trigger)
        isUpdatingCustomAmount = true
        etCustomAmount.setText("")
        isUpdatingCustomAmount = false

        // Update display
        updateSelectedAmountDisplay()
        updateTransactionButtonsState()

        Log.d(TAG, "Add amount: $amount, Total: $selectedAmount")
    }

    /**
     * Handle custom amount input changes
     */
    private fun handleCustomAmountChanged(amountText: String) {
        // If programmatically updated, ignore this change
        if (isUpdatingCustomAmount) {
            return
        }

        if (amountText.isBlank()) {
            selectedAmount = 0.0
        } else {
            try {
                selectedAmount = amountText.toDouble()
            } catch (e: NumberFormatException) {
                selectedAmount = 0.0
            }
        }

        // Update display
        updateSelectedAmountDisplay()
        updateTransactionButtonsState()

        Log.d(TAG, "Custom amount input: $selectedAmount")
    }

    /**
     * Update selected amount display
     */
    private fun updateSelectedAmountDisplay() {
        tvSelectedAmount.text = amountFormatter.format(selectedAmount)
    }

    /**
     * Update transaction buttons state
     */
    private fun updateTransactionButtonsState() {
        try {
            // Check if paymentService is initialized
            if (!::paymentService.isInitialized) {
                Log.d(TAG, "PaymentService not initialized yet, skip button state update")
                btnSale.isEnabled = false
                btnAuth.isEnabled = false
                btnForcedAuth.isEnabled = false
                return
            }

            val connected = paymentService.isConnected()
            val connecting = paymentService.isConnecting()
            val hasAmount = selectedAmount > 0
            val enabled = connected && hasAmount

            btnSale.isEnabled = enabled
            btnAuth.isEnabled = enabled
            btnForcedAuth.isEnabled = enabled

            Log.d(
                TAG,
                "Update transaction buttons state - Connected: $connected, Connecting: $connecting, HasAmount: $hasAmount"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error updating transaction buttons state", e)
            btnSale.isEnabled = false
            btnAuth.isEnabled = false
            btnForcedAuth.isEnabled = false
        }
    }

    /**
     * Start sale payment with additional amounts
     */
    private fun startSalePayment(
        surchargeAmount: Double?,
        tipAmount: Double?,
        taxAmount: Double?,
        cashbackAmount: Double?,
        serviceFee: Double?
    ) {
        if (!paymentService.isConnected()) {
            showToast("Not connected to payment terminal")
            return
        }

        if (selectedAmount <= 0) {
            showToast("Please select payment amount")
            return
        }

        // Cache transaction type for retry
        lastTransactionType = TransactionType.SALE

        Log.d(
            TAG,
            "Start SALE payment - Amount: $selectedAmount, SurchargeAmount: $surchargeAmount, TipAmount: $tipAmount, TaxAmount: $taxAmount, CashbackAmount: $cashbackAmount"
        )

        // Generate order ID and transaction request ID
        val timestamp = System.currentTimeMillis()
        val referenceOrderId = "ORDER_$timestamp"
        val transactionRequestId = "TXN_REQ_${timestamp}_${(1000..9999).random()}"

        Log.d(
            TAG,
            "Creating SALE transaction - RequestId: $transactionRequestId, OrderId: $referenceOrderId"
        )

        // Create transaction record (initial status is PROCESSING)
        val transaction = Transaction(
            transactionRequestId = transactionRequestId,
            transactionId = null,
            referenceOrderId = referenceOrderId,
            type = TransactionType.SALE,
            amount = selectedAmount,
            currency = "USD",
            status = TransactionStatus.PROCESSING,
            timestamp = timestamp,
            surchargeAmount = surchargeAmount,
            tipAmount = tipAmount,
            taxAmount = taxAmount,
            cashbackAmount = cashbackAmount,
            serviceFee = serviceFee
        )

        // Save transaction to repository
        val added = TransactionRepository.addTransaction(transaction)
        Log.d(TAG, "Transaction added to repository: $added")

        // Show payment progress dialog
        showPaymentProgressDialog()

        // Prompt user to launch Tapro app
        showToast("Launch Tapro app for payment")

        // Execute SALE payment
        val callback = object : PaymentCallback {
            override fun onSuccess(result: PaymentResult) {
                runOnUiThread {
                    handlePaymentSuccess(result)
                }
            }

            override fun onFailure(code: String, message: String) {
                runOnUiThread {
                    handlePaymentFailure(transactionRequestId, code, message)
                    hidePaymentProgressDialog()
                }

            }

            override fun onProgress(status: String, message: String) {
                runOnUiThread {
                    Log.d(TAG, "Payment progress callback: $message")

                    val displayMessage = when {
                        message.contains("processing", ignoreCase = true) ->
                            "Payment processing, please complete in Tapro app"

                        else -> message
                    }
                    updatePaymentProgress(displayMessage)
                }
            }
        }

        paymentService.executeSale(
            referenceOrderId = referenceOrderId,
            transactionRequestId = transactionRequestId,
            amount = selectedAmount,
            currency = "USD",
            description = "Demo SALE Payment - ${amountFormatter.format(selectedAmount)}",
            surchargeAmount = surchargeAmount,
            tipAmount = tipAmount,
            taxAmount = taxAmount,
            cashbackAmount = cashbackAmount,
            serviceFee = serviceFee,
            callback = callback
        )
    }

    /**
     * Start payment
     */
    private fun startPayment(transactionType: TransactionType) {
//        if (!paymentService.isConnected()) {
//            showToast("Not connected to payment terminal")
//            return
//        }

        if (selectedAmount <= 0) {
            showToast("Please select payment amount")
            return
        }

        // Cache transaction type for retry
        lastTransactionType = transactionType

        Log.d(TAG, "Start payment - Type: $transactionType, Amount: $selectedAmount")

        // Generate order ID and transaction request ID
        val timestamp = System.currentTimeMillis()
        val referenceOrderId = "ORDER_$timestamp"
        val transactionRequestId = "TXN_REQ_${timestamp}_${(1000..9999).random()}"

        Log.d(
            TAG,
            "Creating transaction - RequestId: $transactionRequestId, OrderId: $referenceOrderId"
        )

        // Create transaction record (initial status is PROCESSING)
        val transaction = Transaction(
            transactionRequestId = transactionRequestId,
            transactionId = null,
            referenceOrderId = referenceOrderId,
            type = transactionType,
            amount = selectedAmount,
            currency = "USD",
            status = TransactionStatus.PROCESSING,
            timestamp = timestamp
        )

        // Save transaction to repository
        val added = TransactionRepository.addTransaction(transaction)
        Log.d(TAG, "Transaction added to repository: $added")

        // Show payment progress dialog
        showPaymentProgressDialog()

        // Prompt user to launch Tapro app
        showToast("Launch Tapro app for payment")

        // Execute payment method based on transaction type
        val callback = object : PaymentCallback {
            override fun onSuccess(result: PaymentResult) {
                runOnUiThread {
                    handlePaymentSuccess(result)
                }
            }

            override fun onFailure(code: String, message: String) {
                runOnUiThread {
                    handlePaymentFailure(transactionRequestId, code, message)
                }
            }

            override fun onProgress(status: String, message: String) {
                runOnUiThread {
                    // In App-to-App mode, progress callbacks are mainly for logging
                    // Actual payment operations are performed in Tapro app
                    Log.d(TAG, "Payment progress callback: $message")

                    // Display appropriate message for App-to-App mode
                    val displayMessage = when {
                        message.contains("processing", ignoreCase = true) ->
                            "Payment processing, please complete in Tapro app"

                        else -> message
                    }
                    updatePaymentProgress(displayMessage)
                }
            }
        }

        // Execute payment method based on transaction type
        when (transactionType) {
            TransactionType.SALE -> {
                paymentService.executeSale(
                    referenceOrderId = referenceOrderId,
                    transactionRequestId = transactionRequestId,
                    amount = selectedAmount,
                    currency = "USD",
                    description = "Demo SALE Payment - ${amountFormatter.format(selectedAmount)}",
                    surchargeAmount = null,
                    tipAmount = null,
                    taxAmount = null,
                    cashbackAmount = null,
                    serviceFee = null,
                    callback = callback
                )
            }

            TransactionType.AUTH -> {
                paymentService.executeAuth(
                    referenceOrderId = referenceOrderId,
                    transactionRequestId = transactionRequestId,
                    amount = selectedAmount,
                    currency = "USD",
                    description = "Demo AUTH Payment - ${amountFormatter.format(selectedAmount)}",
                    callback = callback
                )
            }

            TransactionType.FORCED_AUTH -> {
                // FORCED_AUTH requires an authorization code; here we use a sample code.
                // In a real-world app, the user should be prompted to enter the authorization code.
                showForcedAuthDialog(referenceOrderId, transactionRequestId, callback)
            }
            else -> {
                showToast("Unsupported transaction type: $transactionType")
                hidePaymentProgressDialog()
            }
        }
    }

    /**
     * Show sale amount dialog for user to enter additional amounts
     */
    private fun showSaleAmountDialog() {
        if (!paymentService.isConnected()) {
            showToast("Not connected to payment terminal")
            return
        }

        if (selectedAmount <= 0) {
            showToast("Please select payment amount")
            return
        }

        // Create dialog layout
        val dialogView = layoutInflater.inflate(R.layout.dialog_additional_amounts, null)
        val etSurchargeAmount = dialogView.findViewById<EditText>(R.id.et_surcharge_amount)
        val etTipAmount = dialogView.findViewById<EditText>(R.id.et_tip_amount)
        val etTaxAmount = dialogView.findViewById<EditText>(R.id.et_tax_amount)
        val etCashbackAmount = dialogView.findViewById<EditText>(R.id.et_cashback_amount)
        val etServiceFee = dialogView.findViewById<EditText>(R.id.et_service_fee)
        
        // Hide service fee field
        val tvServiceFee = dialogView.findViewById<TextView>(R.id.tv_service_fee)
        etServiceFee.visibility = View.GONE
        tvServiceFee.visibility = View.GONE

        AlertDialog.Builder(this)
            .setTitle("Additional Amounts (Optional)")
            .setMessage("Base Amount: ${amountFormatter.format(selectedAmount)}")
            .setView(dialogView)
            .setPositiveButton("Proceed") { _, _ ->
                val surchargeAmount = etSurchargeAmount.text.toString().toDoubleOrNull()
                val tipAmount = etTipAmount.text.toString().toDoubleOrNull()
                val taxAmount = etTaxAmount.text.toString().toDoubleOrNull()
                val cashbackAmount = etCashbackAmount.text.toString().toDoubleOrNull()
                val serviceFee = etServiceFee.text.toString().toDoubleOrNull()

                startSalePayment(surchargeAmount, tipAmount, taxAmount, cashbackAmount, serviceFee)
            }
            .setNegativeButton("Cancel", null)
            .setNeutralButton("Skip") { _, _ ->
                startSalePayment(null, null, null, null, null)
            }
            .show()
    }



    /**
     * Show payment progress dialog
     *
     * Note: In App-to-App mode, payment request will immediately launch Tapro app,
     * user completes payment in Tapro and returns to Demo app after completion.
     * Therefore, this dialog mainly shows "Starting payment" status.
     */
    private fun showPaymentProgressDialog() {
        paymentProgressDialog = ProgressDialog(this).apply {
            setTitle("Starting Payment")
            setMessage("Launching Tapro payment app...")
            setCancelable(true)
            show()
        }
    }

    /**
     * Hide payment progress dialog
     */
    private fun hidePaymentProgressDialog() {
        try {
            paymentProgressDialog?.let { dialog ->
                if (dialog.isShowing) {
                    dialog.dismiss()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error dismissing payment progress dialog", e)
        } finally {
            paymentProgressDialog = null
        }
    }

    /**
     * Update payment progress
     *
     * In App-to-App mode, progress updates are mainly used for:
     * 1. Showing "Starting Tapro" status
     * 2. Logging for debugging purposes
     *
     * Actual payment operations are performed in Tapro app, user cannot see Demo app's progress dialog
     */
    private fun updatePaymentProgress(message: String) {
        // Update progress dialog (if still visible)
        paymentProgressDialog?.setMessage(message)

        // Update status text (visible when user returns)
        tvPaymentStatus.text = message
        tvPaymentStatus.visibility = View.VISIBLE

        Log.d(TAG, "Payment progress: $message")

        // In App-to-App mode, if progress callback is received, it means Tapro is about to launch
        // Consider automatically hiding progress dialog after a short time
        if (message.contains("Processing...", ignoreCase = true)) {
            // Delay hiding progress dialog because Tapro app is about to launch
            tvPaymentStatus.postDelayed({
                hidePaymentProgressDialog()
            }, 1500) // Hide after 1.5 seconds
        }
    }

    /**
     * Handle payment success
     */
    private fun handlePaymentSuccess(result: PaymentResult) {
        Log.d(TAG, "Result: $result")
        Log.d(
            TAG,
            "Payment result - TransactionId: ${result.transactionId}, Status: ${result.transactionStatus}"
        )
        Log.d(TAG, "Result transactionRequestId: ${result.transactionRequestId}")

        // Hide payment progress dialog
        hidePaymentProgressDialog()

        // Determine transaction status based on transactionStatus field
        val transactionStatus = when (result.transactionStatus) {
            "SUCCESS" -> TransactionStatus.SUCCESS
            "FAILED" -> TransactionStatus.FAILED
            "PROCESSING" -> TransactionStatus.PROCESSING
            else -> TransactionStatus.FAILED // Default to FAILED for unknown status
        }

        // Update transaction record status with actual amounts from SDK
        // First try using SDK returned transactionRequestId, if empty try using transactionId
        val requestId = result.transactionRequestId
        if (requestId != null && requestId.isNotEmpty()) {
            val updated = TransactionRepository.updateTransactionWithAmounts(
                transactionRequestId = requestId,
                status = transactionStatus,
                transactionId = result.transactionId,
                authCode = result.authCode,
                errorCode = if (transactionStatus == TransactionStatus.FAILED) result.transactionResultCode else null,
                errorMessage = if (transactionStatus == TransactionStatus.FAILED) result.transactionResultMsg else null,
                orderAmount = result.amount?.orderAmount,
                totalAmount = result.amount?.transAmount,
                surchargeAmount = result.amount?.surchargeAmount,
                tipAmount = result.amount?.tipAmount,
                cashbackAmount = result.amount?.cashbackAmount,
                serviceFee = result.amount?.serviceFee
            )
            if (!updated) {
                Log.e(
                    TAG,
                    "Failed to update transaction status - transactionRequestId not found: $requestId"
                )
                // If the transactionRequestId returned by the SDK does not exist, try to find all transactions in PROCESSING status and update the latest one
                val processingTransactions =
                    TransactionRepository.getTransactionsByStatus(TransactionStatus.PROCESSING)
                if (processingTransactions.isNotEmpty()) {
                    val latestProcessing = processingTransactions.first()
                    Log.d(
                        TAG,
                        "Updating latest PROCESSING transaction: ${latestProcessing.transactionRequestId}"
                    )
                    TransactionRepository.updateTransactionWithAmounts(
                        transactionRequestId = latestProcessing.transactionRequestId,
                        status = transactionStatus,
                        transactionId = result.transactionId,
                        authCode = result.authCode,
                        errorCode = if (transactionStatus == TransactionStatus.FAILED) result.transactionResultCode else null,
                        errorMessage = if (transactionStatus == TransactionStatus.FAILED) result.transactionResultMsg else null,
                        orderAmount = result.amount?.orderAmount,
                        totalAmount = result.amount?.transAmount,
                        surchargeAmount = result.amount?.surchargeAmount,
                        tipAmount = result.amount?.tipAmount,
                        cashbackAmount = result.amount?.cashbackAmount,
                        serviceFee = result.amount?.serviceFee
                    )
                }
            }
        } else {
            Log.w(
                TAG,
                "transactionRequestId is null or empty, trying to find by latest PROCESSING status"
            )
            // If the SDK does not return transactionRequestId, try to find the latest transaction in PROCESSING status
            val processingTransactions =
                TransactionRepository.getTransactionsByStatus(TransactionStatus.PROCESSING)
            if (processingTransactions.isNotEmpty()) {
                val latestProcessing = processingTransactions.first()
                Log.d(
                    TAG,
                    "Updating latest PROCESSING transaction: ${latestProcessing.transactionRequestId}"
                )
                TransactionRepository.updateTransactionWithAmounts(
                    transactionRequestId = latestProcessing.transactionRequestId,
                    status = transactionStatus,
                    transactionId = result.transactionId,
                    authCode = result.authCode,
                    errorCode = if (transactionStatus == TransactionStatus.FAILED) result.transactionResultCode else null,
                    errorMessage = if (transactionStatus == TransactionStatus.FAILED) result.transactionResultMsg else null,
                    orderAmount = result.amount?.orderAmount,
                    totalAmount = result.amount?.transAmount,
                    surchargeAmount = result.amount?.surchargeAmount,
                    tipAmount = result.amount?.tipAmount,
                    cashbackAmount = result.amount?.cashbackAmount,
                    serviceFee = result.amount?.serviceFee
                )
            } else {
                Log.e(TAG, "No PROCESSING transaction found to update")
            }
        }

        // Show result dialog based on transactionStatus
        when (result.transactionStatus) {
            "SUCCESS" -> {
                showPaymentResultDialog(
                    title = "Payment Success",
                    message = "Transaction Completed\n" +
                            "TotalAmount: ${amountFormatter.format(result.amount?.transAmount)}\n" +
                            "Transaction ID: ${result.transactionId ?: "N/A"}\n" +
                            "Auth Code: ${result.authCode ?: "N/A"}\n" +
                            "Status: ${result.transactionStatus}",
                    isSuccess = true
                )
                // Reset amount selection only on success
                resetAmountSelection()
            }

            "FAILED" -> {
                showPaymentResultDialog(
                    title = "Payment Failed",
                    message = "Transaction Failed\n" +
                            "Amount: ${amountFormatter.format(selectedAmount)}\n" +
                            "Transaction ID: ${result.transactionId ?: "N/A"}\n" +
                            "Status: ${result.transactionStatus}\n" +
                            "Error Code: ${result.transactionResultCode ?: "N/A"}\n" +
                            "Error Message: ${result.transactionResultMsg ?: "N/A"}",
                    isSuccess = false
                )
            }

            "PROCESSING" -> {
                showPaymentResultDialog(
                    title = "Payment Processing",
                    message = "Transaction Processing\n" +
                            "Amount: ${amountFormatter.format(selectedAmount)}\n" +
                            "Transaction ID: ${result.transactionId ?: "N/A"}\n" +
                            "Status: ${result.transactionStatus}\n" +
                            "Please check transaction result later",
                    isSuccess = false
                )
            }

            else -> {
                showPaymentResultDialog(
                    title = "Unknown Payment Result",
                    message = "Unknown Transaction Status\n" +
                            "Amount: ${amountFormatter.format(selectedAmount)}\n" +
                            "Transaction ID: ${result.transactionId ?: "N/A"}\n" +
                            "Status: ${result.transactionStatus ?: "UNKNOWN"}\n" +
                            "Please contact support or check transaction history",
                    isSuccess = false
                )
            }
        }
    }

    /**
     * Handle payment failure with intelligent retry
     */
    private fun handlePaymentFailure(transactionRequestId: String, code: String, message: String) {
        Log.e(TAG, "Payment failed - Code: $code, Message: $message")
        Log.d(TAG, "Failed transactionRequestId: $transactionRequestId")

        // Ensure payment progress dialog is hidden before showing error dialog
        hidePaymentProgressDialog()

        // Update transaction record status
        val updated = TransactionRepository.updateTransactionStatus(
            transactionRequestId = transactionRequestId,
            status = TransactionStatus.FAILED,
            errorCode = code,
            errorMessage = message
        )

        if (!updated) {
            Log.e(
                TAG,
                "Failed to update transaction status - transactionRequestId not found: $transactionRequestId"
            )
            // If the transactionRequestId returned by the SDK does not exist, try to find all transactions in PROCESSING status and update the latest one
            val processingTransactions =
                TransactionRepository.getTransactionsByStatus(TransactionStatus.PROCESSING)
            if (processingTransactions.isNotEmpty()) {
                val latestProcessing = processingTransactions.first()
                Log.d(
                    TAG,
                    "Updating latest PROCESSING transaction: ${latestProcessing.transactionRequestId}"
                )
                TransactionRepository.updateTransactionStatus(
                    transactionRequestId = latestProcessing.transactionRequestId,
                    status = TransactionStatus.FAILED,
                    errorCode = code,
                    errorMessage = message
                )
            }
        }

        // Use postDelayed to ensure progress dialog is fully dismissed before showing error dialog
        tvPaymentStatus.postDelayed({
            // Show error dialog using enhanced error handler with intelligent retry
            ErrorHandler.handlePaymentError(
                context = this,
                errorCode = code,
                errorMessage = message,
                onRetryWithSameId = if (lastTransactionType != null) {
                    {
                        // Retry with same transaction request ID (for temporary errors)
                        Log.d(TAG, "Retrying with same ID for temporary error: $code")
                        retryPaymentWithSameId()
                    }
                } else null,
                onRetryWithNewId = if (lastTransactionType != null) {
                    {
                        // Retry with new transaction request ID (for definite failures)
                        Log.d(TAG, "Retrying with new ID for definite failure: $code")
                        retryPaymentWithNewId()
                    }
                } else null,
                onQuery = if (code == "E10") {
                    {
                        // Query transaction status for timeout errors
                        Log.d(TAG, "Querying transaction status for timeout error")
                        queryLastTransaction()
                    }
                } else null,
                onCancel = {
                    Log.d(TAG, "User cancelled retry")
                    // Reset amount selection on cancel
                    resetAmountSelection()
                }
            )
        }, 100) // Short delay to ensure progress dialog is dismissed
    }

    /**
     * Retry payment with same transaction request ID (for temporary errors)
     */
    private fun retryPaymentWithSameId() {
        lastTransactionType?.let { transactionType ->
            Log.d(TAG, "Retrying payment with same ID - Type: $transactionType")

            // Find the last failed transaction to get its transaction request ID
            val failedTransactions =
                TransactionRepository.getTransactionsByStatus(TransactionStatus.FAILED)
            if (failedTransactions.isNotEmpty()) {
                val lastFailedTransaction = failedTransactions.first()

                // Show retry progress
                showPaymentProgressDialog()

                // Execute payment with same transaction request ID
                executePaymentWithId(
                    transactionType = transactionType,
                    referenceOrderId = lastFailedTransaction.referenceOrderId,
                    transactionRequestId = lastFailedTransaction.transactionRequestId
                )
            } else {
                Log.e(TAG, "No failed transaction found for retry")
                startPayment(transactionType)
            }
        }
    }

    /**
     * Retry payment with new transaction request ID (for definite failures)
     */
    private fun retryPaymentWithNewId() {
        lastTransactionType?.let { transactionType ->
            Log.d(TAG, "Retrying payment with new ID - Type: $transactionType")
            // Start new payment (will generate new IDs)
            startPayment(transactionType)
        }
    }

    /**
     * Query last transaction status (for timeout errors)
     */
    private fun queryLastTransaction() {
        val processingTransactions =
            TransactionRepository.getTransactionsByStatus(TransactionStatus.PROCESSING)
        val failedTransactions =
            TransactionRepository.getTransactionsByStatus(TransactionStatus.FAILED)

        // Find the most recent transaction that might need querying
        val transactionToQuery = when {
            processingTransactions.isNotEmpty() -> processingTransactions.first()
            failedTransactions.isNotEmpty() -> failedTransactions.first()
            else -> null
        }

        transactionToQuery?.let { transaction ->
            Log.d(TAG, "Querying transaction status: ${transaction.transactionRequestId}")

            // Show query progress
            val progressDialog = ProgressDialog(this).apply {
                setTitle("Querying Status")
                setMessage("Checking transaction status...")
                setCancelable(false)
                show()
            }

            // Execute query
            paymentService.executeQuery(
                transactionRequestId = transaction.transactionRequestId,
                callback = object : PaymentCallback {
                    override fun onSuccess(result: PaymentResult) {
                        runOnUiThread {
                            progressDialog.dismiss()
                            handleQueryResult(transaction, result)
                        }
                    }

                    override fun onFailure(code: String, message: String) {
                        runOnUiThread {
                            progressDialog.dismiss()
                            Log.e(TAG, "Query failed: $code - $message")

                            // Show query failure dialog
                            AlertDialog.Builder(this@MainActivity)
                                .setTitle("Query Failed")
                                .setMessage("Failed to query transaction status:\n$message\n\nError Code: $code")
                                .setPositiveButton("OK", null)
                                .show()
                        }
                    }
                }
            )
        } ?: run {
            Log.w(TAG, "No transaction found to query")
            showToast("No transaction found to query")
        }
    }

    /**
     * Handle query result
     */
    private fun handleQueryResult(originalTransaction: Transaction, result: PaymentResult) {
        Log.d(
            TAG,
            "Query result - Status: ${result.transactionStatus}, ID: ${result.transactionId}"
        )

        when (result.transactionStatus) {
            "SUCCESS" -> {
                // Update transaction to success with actual amounts from SDK
                TransactionRepository.updateTransactionWithAmounts(
                    transactionRequestId = originalTransaction.transactionRequestId,
                    status = TransactionStatus.SUCCESS,
                    transactionId = result.transactionId,
                    authCode = result.authCode,
                    orderAmount = result.amount?.orderAmount,
                    totalAmount = result.amount?.transAmount,
                    surchargeAmount = result.amount?.surchargeAmount,
                    tipAmount = result.amount?.tipAmount,
                    cashbackAmount = result.amount?.cashbackAmount,
                    serviceFee = result.amount?.serviceFee
                )

                // Show success dialog
                showPaymentResultDialog(
                    title = "Transaction Found - Success",
                    message = "Transaction was actually successful:\n" +
                            "Amount: ${amountFormatter.format(originalTransaction.amount)}\n" +
                            "Transaction ID: ${result.transactionId ?: "N/A"}\n" +
                            "Auth Code: ${result.authCode ?: "N/A"}",
                    isSuccess = true
                )

                // Reset amount selection
                resetAmountSelection()
            }

            "FAILED" -> {
                // Update transaction with failure details
                TransactionRepository.updateTransactionStatus(
                    transactionRequestId = originalTransaction.transactionRequestId,
                    status = TransactionStatus.FAILED,
                    transactionId = result.transactionId,
                    errorCode = result.transactionResultCode,
                    errorMessage = result.transactionResultMsg
                )

                // Show failure dialog with retry option
                AlertDialog.Builder(this)
                    .setTitle("Transaction Found - Failed")
                    .setMessage(
                        "Transaction was confirmed as failed:\n" +
                                "Amount: ${amountFormatter.format(originalTransaction.amount)}\n" +
                                "Error: ${result.transactionResultMsg ?: "Unknown error"}\n" +
                                "Error Code: ${result.transactionResultCode ?: "N/A"}"
                    )
                    .setPositiveButton("Try New Transaction") { _, _ ->
                        retryPaymentWithNewId()
                    }
                    .setNegativeButton("Cancel") { _, _ ->
                        resetAmountSelection()
                    }
                    .show()
            }

            "PROCESSING" -> {
                // Still processing
                AlertDialog.Builder(this)
                    .setTitle("Transaction Still Processing")
                    .setMessage(
                        "Transaction is still being processed:\n" +
                                "Amount: ${amountFormatter.format(originalTransaction.amount)}\n" +
                                "Transaction ID: ${result.transactionId ?: "N/A"}\n" +
                                "Please check again later."
                    )
                    .setPositiveButton("Query Again") { _, _ ->
                        queryLastTransaction()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }

            else -> {
                // Unknown status
                AlertDialog.Builder(this)
                    .setTitle("Unknown Transaction Status")
                    .setMessage(
                        "Transaction status is unknown:\n" +
                                "Amount: ${amountFormatter.format(originalTransaction.amount)}\n" +
                                "Status: ${result.transactionStatus ?: "UNKNOWN"}\n" +
                                "Please contact support."
                    )
                    .setPositiveButton("OK", null)
                    .show()
            }
        }
    }

    /**
     * Execute payment with specific transaction request ID
     */
    private fun executePaymentWithId(
        transactionType: TransactionType,
        referenceOrderId: String,
        transactionRequestId: String
    ) {
        Log.d(
            TAG,
            "Executing payment with specific ID - Type: $transactionType, RequestId: $transactionRequestId"
        )

        val callback = object : PaymentCallback {
            override fun onSuccess(result: PaymentResult) {
                runOnUiThread {
                    handlePaymentSuccess(result)
                }
            }

            override fun onFailure(code: String, message: String) {
                runOnUiThread {
                    handlePaymentFailure(transactionRequestId, code, message)
                }
            }

            override fun onProgress(status: String, message: String) {
                runOnUiThread {
                    updatePaymentProgress(message)
                }
            }
        }

        // Execute payment method based on transaction type
        when (transactionType) {
            TransactionType.SALE -> {
                paymentService.executeSale(
                    referenceOrderId = referenceOrderId,
                    transactionRequestId = transactionRequestId,
                    amount = selectedAmount,
                    currency = "USD",
                    description = "Demo SALE Payment (Retry) - ${
                        amountFormatter.format(
                            selectedAmount
                        )
                    }",
                    surchargeAmount = null,
                    tipAmount = null,
                    taxAmount = null,
                    cashbackAmount = null,
                    serviceFee = null,
                    callback = callback
                )
            }

            TransactionType.AUTH -> {
                paymentService.executeAuth(
                    referenceOrderId = referenceOrderId,
                    transactionRequestId = transactionRequestId,
                    amount = selectedAmount,
                    currency = "USD",
                    description = "Demo AUTH Payment (Retry) - ${
                        amountFormatter.format(
                            selectedAmount
                        )
                    }",
                    callback = callback
                )
            }

            TransactionType.FORCED_AUTH -> {
                // FORCED_AUTH requires an authorization code; here we use a sample code.
                // In a real-world app, the user should be prompted to enter the authorization code.
                showForcedAuthDialog(referenceOrderId, transactionRequestId, callback)
            }

            else -> {
                showToast("Unsupported transaction type for retry: $transactionType")
                hidePaymentProgressDialog()
            }
        }
    }

    /**
     * Show forced authorization dialog for user to enter authorization code and additional amounts
     */
    private fun showForcedAuthDialog(
        referenceOrderId: String,
        transactionRequestId: String,
        callback: PaymentCallback
    ) {
        // Create dialog layout for additional amounts
        val dialogView = layoutInflater.inflate(R.layout.dialog_additional_amounts, null)
        val etTipAmount = dialogView.findViewById<EditText>(R.id.et_tip_amount)
        val etTaxAmount = dialogView.findViewById<EditText>(R.id.et_tax_amount)
        val tvSurchargeAmount = dialogView.findViewById<TextView>(R.id.tv_surcharge_amount)
        val etSurchargeAmount = dialogView.findViewById<EditText>(R.id.et_surcharge_amount)
        val tvCashbackAmount = dialogView.findViewById<TextView>(R.id.tv_cashback_amount)
        val etCashbackAmount = dialogView.findViewById<EditText>(R.id.et_cashback_amount)
        val tvServiceFee = dialogView.findViewById<TextView>(R.id.tv_service_fee)
        val etServiceFee = dialogView.findViewById<EditText>(R.id.et_service_fee)

        // Hide all fields except tip and tax
        tvSurchargeAmount.visibility = View.GONE
        etSurchargeAmount.visibility = View.GONE
        tvCashbackAmount.visibility = View.GONE
        etCashbackAmount.visibility = View.GONE
        tvServiceFee.visibility = View.GONE
        etServiceFee.visibility = View.GONE

        AlertDialog.Builder(this)
            .setTitle("Forced Authorization - Additional Amounts")
            .setMessage("Base Amount: ${amountFormatter.format(selectedAmount)}")
            .setView(dialogView)
            .setPositiveButton("Proceed") { _, _ ->
                val tipAmount = etTipAmount.text.toString().toDoubleOrNull()
                val taxAmount = etTaxAmount.text.toString().toDoubleOrNull()

                // Now show dialog for authorization code
                showAuthCodeDialog(referenceOrderId, transactionRequestId, tipAmount, taxAmount, callback)
            }
            .setNegativeButton("Cancel") { _, _ ->
                hidePaymentProgressDialog()
            }
            .setNeutralButton("Skip") { _, _ ->
                // Show dialog for authorization code without additional amounts
                showAuthCodeDialog(referenceOrderId, transactionRequestId, null, null, callback)
            }
            .setCancelable(false)
            .show()
    }

    /**
     * Show dialog for entering authorization code
     */
    private fun showAuthCodeDialog(
        referenceOrderId: String,
        transactionRequestId: String,
        tipAmount: Double?,
        taxAmount: Double?,
        callback: PaymentCallback
    ) {
        val input = EditText(this).apply {
            hint = "Enter authorization code"
            inputType = android.text.InputType.TYPE_CLASS_TEXT
        }

        AlertDialog.Builder(this)
            .setTitle("Forced Authorization")
            .setMessage("Please enter authorization code to complete forced auth transaction")
            .setView(input)
            .setPositiveButton("OK") { dialog, _ ->
                val authCode = input.text.toString().trim()
                if (authCode.isNotEmpty()) {
                    // Execute forced authorization
                    paymentService.executeForcedAuth(
                        referenceOrderId = referenceOrderId,
                        transactionRequestId = transactionRequestId,
                        amount = selectedAmount,
                        currency = "USD",
                        authCode = authCode,
                        description = "Demo FORCED_AUTH Payment - ${amountFormatter.format(selectedAmount)}",
                        tipAmount = tipAmount,
                        taxAmount = taxAmount,
                        callback = callback
                    )
                    dialog.dismiss()
                } else {
                    showToast("Please enter valid authorization code")
                }
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
                hidePaymentProgressDialog()
            }
            .setCancelable(false)
            .show()
    }

    /**
     * Show payment result dialog
     */
    private fun showPaymentResultDialog(title: String, message: String, isSuccess: Boolean) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("OK") { dialog, _ ->
                dialog.dismiss()

                // Hide status text
                tvPaymentStatus.visibility = View.GONE
            }
            .setCancelable(false)
            .show()
    }

    /**
     * Reset amount selection
     */
    private fun resetAmountSelection() {
        selectedAmount = 0.0
        etCustomAmount.setText("")
        tvPaymentStatus.visibility = View.GONE
        updateSelectedAmountDisplay()
        updateTransactionButtonsState()
    }

    /**
     * Open connection settings page
     */
    private fun openConnectionSettings() {
        Log.d(TAG, "Open connection settings")

        val intent = Intent(this, ConnectionActivity::class.java)
        startActivityForResult(intent, REQUEST_CODE_CONNECTION)
    }

    /**
     * Open transaction history page
     */
    private fun openTransactionHistory() {
        Log.d(TAG, "Open transaction history")

        val intent = Intent(this, TransactionListActivity::class.java)
        startActivity(intent)
    }

    /**
     * Show Toast message
     */
    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun onResume() {
        super.onResume()

        // Ensure progress dialog is hidden when returning from Tapro app
        // Because payment results are returned via callbacks
        hidePaymentProgressDialog()

        // Check connection status when returning to the activity
        checkConnectionStatus()

        Log.d(TAG, "MainActivity display reset")
    }

    /**
     * Check current connection status and update UI accordingly
     */
    private fun checkConnectionStatus() {
        try {
            if (::paymentService.isInitialized) {
                val isConnecting = paymentService.isConnecting()
                val isConnected = paymentService.isConnected()
                Log.d(TAG, "Connection status check - Connecting: $isConnecting, Connected: $isConnected")

                if (isConnecting) {
                    updateConnectionStatus("Connecting...", false)
                } else if (isConnected) {
                    // If connected, get version info and update status
                    val version = paymentService.getTaproVersion()
                    val statusText = if (version != null) {
                        "Connected (v$version)"
                    } else {
                        "Connected"
                    }
                    updateConnectionStatus(statusText, true)
                } else {
                    updateConnectionStatus("Not Connected", false)
                    Log.d(TAG, "Connection lost")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking connection status", e)
            updateConnectionStatus("Connection Error", false)
        }
    }

    override fun onPause() {
        super.onPause()

        Log.d(TAG, "MainActivity paused")
    }

    override fun onDestroy() {
        super.onDestroy()

        // Clean up resources
        hidePaymentProgressDialog()

        Log.d(TAG, "MainActivity destroyed")
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        when (requestCode) {
            REQUEST_CODE_CONNECTION -> {
                if (resultCode == ConnectionActivity.RESULT_CONNECTION_CHANGED) {
                    // Connection settings changed, show result information
                    val connectionMode = data?.getStringExtra("connection_mode")
                    val connectionMessage = data?.getStringExtra("connection_message")

                    Log.d(
                        TAG,
                        "Connection configuration changed - Mode: $connectionMode, Message: $connectionMessage"
                    )

                    // Show connection result
                    connectionMessage?.let { message ->
                        showToast(message)
                    }

                    // Update connection status display
                    updateConnectionStatus(
                        connectionMessage ?: "Connection configuration updated",
                        true
                    )
                }
            }
            REQUEST_CODE_TRANSACTION_LIST -> {
                // From transaction history page return, no special processing required
            }
        }
    }
}