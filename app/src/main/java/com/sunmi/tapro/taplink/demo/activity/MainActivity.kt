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
import com.sunmi.tapro.taplink.demo.service.TaplinkPaymentService
import com.sunmi.tapro.taplink.demo.service.ConnectionListener
import com.sunmi.tapro.taplink.demo.service.PaymentCallback
import com.sunmi.tapro.taplink.demo.service.PaymentResult

import com.sunmi.tapro.taplink.demo.util.ConnectionPreferences

import java.math.BigDecimal
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
    private lateinit var tvConnectionType: TextView
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
    private lateinit var paymentService: TaplinkPaymentService

    // Currently selected amount
    private var selectedAmount: BigDecimal = BigDecimal.ZERO

    // Amount formatter
    private val amountFormatter = DecimalFormat("$#,##0.00")

    // Payment progress dialog
    private var paymentProgressDialog: ProgressDialog? = null
    
    // Current alert dialog reference for proper cleanup
    private var currentAlertDialog: AlertDialog? = null

    // Flag to indicate if custom amount is being programmatically updated (to avoid TextWatcher trigger)
    private var isUpdatingCustomAmount = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize components in simple sequence
        initViews()
        initPaymentService()
        setupEventListeners()
        attemptAutoConnection()
        
        Log.d(TAG, "MainActivity initialization completed")
    }


    /**
     * Initialize UI components
     */
    private fun initViews() {
        // Top bar
        layoutTopBar = findViewById(R.id.layout_top_bar)
        tvConnectionType = findViewById(R.id.tv_connection_type)
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

        // Initialize display
        updateSelectedAmountDisplay()
    }

    /**
     * Initialize payment service
     */
    private fun initPaymentService() {
        // Get service instance
        paymentService = TaplinkPaymentService.getInstance()
        
        // Get current connection mode
        val currentMode = ConnectionPreferences.getConnectionMode(this)
        val serviceMode = paymentService.getCurrentConnectionMode()
        
        // Re-initialize SDK if mode has changed
        if (currentMode != serviceMode) {
            val success = paymentService.initialize(
                context = this,
                appId = "", // Will be read from resources in initialize method
                merchantId = "", // Will be read from resources in initialize method
                secretKey = "" // Will be read from resources in initialize method
            )
            if (!success) {
                showError("SDK Initialization Failed", "Failed to initialize SDK for $currentMode mode")
            }
        }
        
        // Update transaction buttons state after service initialization
        updateTransactionButtonsState()
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
     * Start auto-connection based on saved connection mode
     */
    private fun attemptAutoConnection() {
        val savedMode = ConnectionPreferences.getConnectionMode(this)
        updateConnectionStatus("Connecting...", false)
        
        when (savedMode) {
            ConnectionPreferences.ConnectionMode.APP_TO_APP,
            ConnectionPreferences.ConnectionMode.CABLE -> {
                connectToPaymentService()
            }
            ConnectionPreferences.ConnectionMode.LAN -> {
                attemptLanAutoConnection()
            }
        }
    }
    
    /**
     * Attempt LAN auto-connection
     */
    private fun attemptLanAutoConnection() {
        val lanConfig = ConnectionPreferences.getLanConfig(this)
        val ip = lanConfig.first
        
        if (ip.isNullOrEmpty()) {
            updateConnectionStatus("Configuration Required", false)
            return
        }
        
        val success = paymentService.attemptAutoConnect(object : ConnectionListener {
            override fun onConnected(deviceId: String, taproVersion: String) {
                runOnUiThread {
                    val statusText = if (taproVersion.isNotEmpty()) {
                        "Connected (v$taproVersion)"
                    } else {
                        "Connected"
                    }
                    updateConnectionStatus(statusText, true)
                }
            }
            
            override fun onDisconnected(reason: String) {
                runOnUiThread {
                    updateConnectionStatus("Not Connected", false)
                }
            }
            
            override fun onError(code: String, message: String) {
                runOnUiThread {
                    updateConnectionStatus("Connection Failed", false)
                    showConnectionFailure(message.ifEmpty { "Auto-connect failed (Code: $code)" })
                }
            }
        })
        
        if (!success) {
            updateConnectionStatus("Configuration Required", false)
        }
    }


    /**
     * Connect to payment service
     */
    private fun connectToPaymentService() {
        // Temporarily commented out to avoid multiple connect() calls
        paymentService.connect(object : ConnectionListener {
            override fun onConnected(deviceId: String, taproVersion: String) {
                runOnUiThread {
                    val statusText = if (taproVersion.isNotEmpty()) {
                        "Connected (v$taproVersion)"
                    } else {
                        "Connected"
                    }
                    updateConnectionStatus(statusText, true)
                }
            }

            override fun onDisconnected(reason: String) {
                runOnUiThread {
                    updateConnectionStatus("Not Connected", false)
                }
            }

            override fun onError(code: String, message: String) {
                runOnUiThread {
                    updateConnectionStatus("Connection Failed", false)
                    showConnectionFailure(message.ifEmpty { "Connection failed (Code: $code)" })
                }
            }
        })
        
        // For testing: Assume connection is successful since Application handles it
        runOnUiThread {
            updateConnectionStatus("Connected (App-level)", true)
        }
    }
    


    /**
     * Update connection status display
     */
    private fun updateConnectionStatus(status: String, connected: Boolean) {
        // Update connection mode display
        val currentMode = ConnectionPreferences.getConnectionMode(this)
        val modeText = when (currentMode) {
            ConnectionPreferences.ConnectionMode.APP_TO_APP -> "App-to-App"
            ConnectionPreferences.ConnectionMode.CABLE -> "Cable"
            ConnectionPreferences.ConnectionMode.LAN -> "LAN"
        }
        tvConnectionType.text = modeText
        tvConnectionStatus.text = status

        // Update transaction button state based on connection status
        updateTransactionButtonsState()
    }
    


    /**
     * Show simple error dialog
     */
    private fun showError(title: String, message: String) {
        // Dismiss any existing dialog first
        currentAlertDialog?.dismiss()
        
        currentAlertDialog = AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("OK") { dialog, _ ->
                dialog.dismiss()
                currentAlertDialog = null
            }
            .setOnDismissListener {
                currentAlertDialog = null
            }
            .show()
    }

    /**
     * Show connection failure dialog with retry option
     */
    private fun showConnectionFailure(message: String) {
        // Dismiss any existing dialog first
        currentAlertDialog?.dismiss()
        
        currentAlertDialog = AlertDialog.Builder(this)
            .setTitle("Connection Failed")
            .setMessage(message)
            .setPositiveButton("Retry") { dialog, _ -> 
                dialog.dismiss()
                currentAlertDialog = null
                attemptAutoConnection()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
                currentAlertDialog = null
            }
            .setOnDismissListener {
                currentAlertDialog = null
            }
            .show()
    }

    /**
     * Add amount
     */
    private fun addAmount(amount: Double) {
        selectedAmount = selectedAmount.add(BigDecimal.valueOf(amount))

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
            selectedAmount = BigDecimal.ZERO
        } else {
            try {
                selectedAmount = BigDecimal(amountText)
            } catch (e: NumberFormatException) {
                selectedAmount = BigDecimal.ZERO
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
        if (!::paymentService.isInitialized) {
            btnSale.isEnabled = false
            btnAuth.isEnabled = false
            btnForcedAuth.isEnabled = false
            return
        }

        val connected = paymentService.isConnected()
        val hasAmount = selectedAmount > BigDecimal.ZERO
        val enabled = connected && hasAmount

        btnSale.isEnabled = enabled
        btnAuth.isEnabled = enabled
        btnForcedAuth.isEnabled = enabled
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
        if (!validatePaymentConditions()) return

        val transactionData = createTransactionData()
        val transaction = createSaleTransaction(transactionData, surchargeAmount, tipAmount, taxAmount, cashbackAmount, serviceFee)
        
        TransactionRepository.addTransaction(transaction)
        executeSalePayment(transactionData, surchargeAmount, tipAmount, taxAmount, cashbackAmount, serviceFee)
    }

    /**
     * Validate payment conditions
     */
    private fun validatePaymentConditions(): Boolean {
        if (!paymentService.isConnected()) {
            showToast("Not connected to payment terminal")
            return false
        }

        if (selectedAmount <= BigDecimal.ZERO) {
            showToast("Please select payment amount")
            return false
        }

        return true
    }

    /**
     * Create transaction data with IDs
     */
    private fun createTransactionData(): TransactionData {
        val timestamp = System.currentTimeMillis()
        return TransactionData(
            timestamp = timestamp,
            referenceOrderId = "ORDER_$timestamp",
            transactionRequestId = "TXN_REQ_${timestamp}_${(1000..9999).random()}"
        )
    }

    /**
     * Create sale transaction record
     */
    private fun createSaleTransaction(
        data: TransactionData,
        surchargeAmount: Double?,
        tipAmount: Double?,
        taxAmount: Double?,
        cashbackAmount: Double?,
        serviceFee: Double?
    ): Transaction {
        return Transaction(
            transactionRequestId = data.transactionRequestId,
            transactionId = null,
            referenceOrderId = data.referenceOrderId,
            type = TransactionType.SALE,
            amount = selectedAmount,
            currency = "USD",
            status = TransactionStatus.PROCESSING,
            timestamp = data.timestamp,
            surchargeAmount = surchargeAmount?.let { BigDecimal.valueOf(it) },
            tipAmount = tipAmount?.let { BigDecimal.valueOf(it) },
            taxAmount = taxAmount?.let { BigDecimal.valueOf(it) },
            cashbackAmount = cashbackAmount?.let { BigDecimal.valueOf(it) },
            serviceFee = serviceFee?.let { BigDecimal.valueOf(it) }
        )
    }

    /**
     * Execute sale payment
     */
    private fun executeSalePayment(
        data: TransactionData,
        surchargeAmount: Double?,
        tipAmount: Double?,
        taxAmount: Double?,
        cashbackAmount: Double?,
        serviceFee: Double?
    ) {
        val callback = createPaymentCallback(data.transactionRequestId)
        
        paymentService.executeSale(
            referenceOrderId = data.referenceOrderId,
            transactionRequestId = data.transactionRequestId,
            amount = selectedAmount,
            currency = "USD",
            description = "Demo SALE Payment - ${amountFormatter.format(selectedAmount)}",
            surchargeAmount = surchargeAmount?.let { BigDecimal.valueOf(it) },
            tipAmount = tipAmount?.let { BigDecimal.valueOf(it) },
            taxAmount = taxAmount?.let { BigDecimal.valueOf(it) },
            cashbackAmount = cashbackAmount?.let { BigDecimal.valueOf(it) },
            serviceFee = serviceFee?.let { BigDecimal.valueOf(it) },
            callback = callback
        )
    }

    /**
     * Create payment callback
     */
    private fun createPaymentCallback(transactionRequestId: String): PaymentCallback {
        return object : PaymentCallback {
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
                    val displayMessage = when {
                        message.matches(Regex(".*transaction processing\\.\\.\\.", RegexOption.IGNORE_CASE)) ->
                            "Payment processing, please complete in Tapro app"
                        else -> message
                    }
                    updatePaymentProgress(displayMessage)
                }
            }
        }
    }

    /**
     * Data class for transaction information
     */
    private data class TransactionData(
        val timestamp: Long,
        val referenceOrderId: String,
        val transactionRequestId: String
    )

    /**
     * Start payment
     */
    private fun startPayment(transactionType: TransactionType) {
        if (!paymentService.isConnected()) {
            showToast("Not connected to payment terminal")
            return
        }

        if (selectedAmount <= BigDecimal.ZERO) {
            showToast("Please select payment amount")
            return
        }

        // Generate order ID and transaction request ID
        val timestamp = System.currentTimeMillis()
        val referenceOrderId = "ORDER_$timestamp"
        val transactionRequestId = "TXN_REQ_${timestamp}_${(1000..9999).random()}"

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
        TransactionRepository.addTransaction(transaction)

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
                    val displayMessage = when {
                        message.matches(Regex(".*transaction processing\\.\\.\\.", RegexOption.IGNORE_CASE)) ->
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
                // FORCED_AUTH allows user to input tip and tax amounts, but uses predefined auth code
                showForcedAuthAmountDialog(referenceOrderId, transactionRequestId, transaction, callback)
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

        if (selectedAmount <= BigDecimal.ZERO) {
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

        // Set base amount
        val tvBaseAmount = dialogView.findViewById<TextView>(R.id.tv_base_amount)
        tvBaseAmount.text = amountFormatter.format(selectedAmount)

        // Hide service fee field
        val tvServiceFee = dialogView.findViewById<TextView>(R.id.tv_service_fee)
        etServiceFee.visibility = View.GONE
        tvServiceFee.visibility = View.GONE

        AlertDialog.Builder(this)
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
     * Show forced auth amount dialog for user to enter tip and tax amounts
     */
    private fun showForcedAuthAmountDialog(
        referenceOrderId: String,
        transactionRequestId: String,
        transaction: Transaction,
        callback: PaymentCallback
    ) {
        if (!paymentService.isConnected()) {
            showToast("Not connected to payment terminal")
            return
        }

        if (selectedAmount <= BigDecimal.ZERO) {
            showToast("Please select payment amount")
            return
        }

        // Create dialog layout
        val dialogView = layoutInflater.inflate(R.layout.dialog_additional_amounts, null)
        val etTipAmount = dialogView.findViewById<EditText>(R.id.et_tip_amount)
        val etTaxAmount = dialogView.findViewById<EditText>(R.id.et_tax_amount)
        val tvSurchargeAmount = dialogView.findViewById<TextView>(R.id.tv_surcharge_amount)
        val etSurchargeAmount = dialogView.findViewById<EditText>(R.id.et_surcharge_amount)
        val tvCashbackAmount = dialogView.findViewById<TextView>(R.id.tv_cashback_amount)
        val etCashbackAmount = dialogView.findViewById<EditText>(R.id.et_cashback_amount)
        val tvServiceFee = dialogView.findViewById<TextView>(R.id.tv_service_fee)
        val etServiceFee = dialogView.findViewById<EditText>(R.id.et_service_fee)

        // Set base amount
        val tvBaseAmount = dialogView.findViewById<TextView>(R.id.tv_base_amount)
        tvBaseAmount.text = amountFormatter.format(selectedAmount)

        // Hide fields not needed for FORCED_AUTH (only show tip and tax)
        tvSurchargeAmount.visibility = View.GONE
        etSurchargeAmount.visibility = View.GONE
        tvCashbackAmount.visibility = View.GONE
        etCashbackAmount.visibility = View.GONE
        tvServiceFee.visibility = View.GONE
        etServiceFee.visibility = View.GONE

        // Pre-fill with original transaction amounts if available
        transaction.tipAmount?.let { etTipAmount.setText(it.toString()) }
        transaction.taxAmount?.let { etTaxAmount.setText(it.toString()) }

        AlertDialog.Builder(this)
            .setTitle("Forced Authorization - Additional Amounts")
            .setMessage("Base Amount: ${amountFormatter.format(transaction.amount)}")
            .setView(dialogView)
            .setPositiveButton("Proceed") { _, _ ->
                val tipAmount = etTipAmount.text.toString().toDoubleOrNull()
                val taxAmount = etTaxAmount.text.toString().toDoubleOrNull()

                // Execute forced authorization with predefined auth code
                paymentService.executeForcedAuth(
                    referenceOrderId = referenceOrderId,
                    transactionRequestId = transactionRequestId,
                    amount = selectedAmount,
                    currency = "USD",
                    description = "Demo FORCED_AUTH Payment - ${amountFormatter.format(selectedAmount)}",
                    tipAmount = tipAmount?.let { BigDecimal.valueOf(it) },
                    taxAmount = taxAmount?.let { BigDecimal.valueOf(it) },
                    callback = callback
                )
            }
            .setNegativeButton("Cancel") { _, _ ->
                hidePaymentProgressDialog()
            }
            .setNeutralButton("Skip") { _, _ ->
                // Execute forced authorization without additional amounts
                paymentService.executeForcedAuth(
                    referenceOrderId = referenceOrderId,
                    transactionRequestId = transactionRequestId,
                    amount = selectedAmount,
                    currency = "USD",
                    description = "Demo FORCED_AUTH Payment - ${amountFormatter.format(selectedAmount)}",
                    tipAmount = null,
                    taxAmount = null,
                    callback = callback
                )
            }
            .setCancelable(false)
            .show()
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
        Log.d(TAG, "Payment result - TransactionId: ${result.transactionId}, Status: ${result.transactionStatus}")
        Log.d(TAG, "Result transactionRequestId: ${result.transactionRequestId}")

        // Hide payment progress dialog
        hidePaymentProgressDialog()

        // Update transaction record
        updateTransactionFromResult(result)

        // Show result dialog
        showResultDialog(result)
    }

    /**
     * Update transaction record from payment result
     * Handles both successful and failed payment results by updating the transaction status
     * and associated metadata in the repository
     */
    private fun updateTransactionFromResult(result: PaymentResult) {
        val transactionStatus = mapTransactionStatus(result.transactionStatus)
        val requestId = result.transactionRequestId

        if (requestId != null && requestId.isNotEmpty()) {
            updateTransactionByRequestId(requestId, result, transactionStatus)
        } else {
            updateLatestProcessingTransaction(result, transactionStatus)
        }
    }

    /**
     * Map SDK transaction status to internal status
     * Converts SDK status strings to internal TransactionStatus enum values
     * Defaults to FAILED for unknown status values
     */
    private fun mapTransactionStatus(status: String?): TransactionStatus {
        return when (status) {
            "SUCCESS" -> TransactionStatus.SUCCESS
            "FAILED" -> TransactionStatus.FAILED
            "PROCESSING" -> TransactionStatus.PROCESSING
            else -> TransactionStatus.FAILED
        }
    }

    /**
     * Update transaction by request ID
     * Attempts to update the transaction using the SDK-provided request ID
     * Falls back to updating the latest processing transaction if the ID is not found
     */
    private fun updateTransactionByRequestId(requestId: String, result: PaymentResult, status: TransactionStatus) {
        val updated = TransactionRepository.updateTransactionWithAmounts(
            transactionRequestId = requestId,
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

        if (!updated) {
            Log.e(TAG, "Failed to update transaction status - transactionRequestId not found: $requestId")
            updateLatestProcessingTransaction(result, status)
        }
    }

    /**
     * Update latest processing transaction
     * Fallback method when SDK doesn't return a valid transaction request ID
     * Updates the most recent transaction with PROCESSING status
     */
    private fun updateLatestProcessingTransaction(result: PaymentResult, status: TransactionStatus) {
        val processingTransactions = TransactionRepository.getTransactionsByStatus(TransactionStatus.PROCESSING)
        if (processingTransactions.isNotEmpty()) {
            val latestProcessing = processingTransactions.first()
            Log.d(TAG, "Updating latest PROCESSING transaction: ${latestProcessing.transactionRequestId}")
            
            TransactionRepository.updateTransactionWithAmounts(
                transactionRequestId = latestProcessing.transactionRequestId,
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
        } else {
            Log.e(TAG, "No PROCESSING transaction found to update")
        }
    }

    /**
     * Show payment result dialog based on status
     */
    private fun showResultDialog(result: PaymentResult) {
        when (result.transactionStatus) {
            "SUCCESS" -> showSuccessDialog(result)
            "FAILED" -> showFailedDialog(result)
            "PROCESSING" -> showProcessingDialog(result)
            else -> showUnknownStatusDialog(result)
        }
    }

    /**
     * Show success dialog
     */
    private fun showSuccessDialog(result: PaymentResult) {
        showPaymentResultDialog(
            title = "Payment Success",
            message = "Transaction Completed\n" +
                    "TotalAmount: ${amountFormatter.format(result.amount?.transAmount)}\n" +
                    "Transaction ID: ${result.transactionId ?: "N/A"}\n" +
                    "Auth Code: ${result.authCode ?: "N/A"}\n" +
                    "Status: ${result.transactionStatus}",
            isSuccess = true
        )
        resetAmountSelection()
    }

    /**
     * Show failed dialog
     */
    private fun showFailedDialog(result: PaymentResult) {
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

    /**
     * Show processing dialog
     */
    private fun showProcessingDialog(result: PaymentResult) {
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

    /**
     * Show unknown status dialog
     */
    private fun showUnknownStatusDialog(result: PaymentResult) {
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

    /**
     * Handle payment failure
     */
    private fun handlePaymentFailure(transactionRequestId: String, code: String, message: String) {
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
            // If the transactionRequestId returned by the SDK does not exist, try to find all transactions in PROCESSING status and update the latest one
            val processingTransactions =
                TransactionRepository.getTransactionsByStatus(TransactionStatus.PROCESSING)
            if (processingTransactions.isNotEmpty()) {
                val latestProcessing = processingTransactions.first()
                TransactionRepository.updateTransactionStatus(
                    transactionRequestId = latestProcessing.transactionRequestId,
                    status = TransactionStatus.FAILED,
                    errorCode = code,
                    errorMessage = message
                )
            }
        }

        // Show simple payment error dialog
        tvPaymentStatus.postDelayed({
            showPaymentError(code, message)
        }, 100) // Short delay to ensure progress dialog is dismissed
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
     * Show payment result dialog
     */
    private fun showPaymentResultDialog(title: String, message: String, isSuccess: Boolean) {
        // Dismiss any existing dialog first
        currentAlertDialog?.dismiss()
        
        currentAlertDialog = AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("OK") { dialog, _ ->
                dialog.dismiss()
                currentAlertDialog = null
                // Hide status text
                tvPaymentStatus.visibility = View.GONE
            }
            .setOnDismissListener {
                currentAlertDialog = null
            }
            .setCancelable(true)
            .show()
    }

    /**
     * Reset amount selection
     */
    private fun resetAmountSelection() {
        selectedAmount = BigDecimal.ZERO
        etCustomAmount.setText("")
        tvPaymentStatus.visibility = View.GONE
        updateSelectedAmountDisplay()
        updateTransactionButtonsState()
    }

    /**
     * Open connection settings page
     */
    private fun openConnectionSettings() {
        val intent = Intent(this, ConnectionActivity::class.java)
        startActivityForResult(intent, REQUEST_CODE_CONNECTION)
    }

    /**
     * Open transaction history page
     */
    private fun openTransactionHistory() {
        val intent = Intent(this, TransactionListActivity::class.java)
        startActivity(intent)
    }

    /**
     * Show simple payment error dialog
     */
    private fun showPaymentError(code: String, message: String) {
        val fullMessage = "$message\n\nError Code: $code"
        
        val builder = AlertDialog.Builder(this)
            .setTitle("Payment Error")
            .setMessage(fullMessage)
            .setCancelable(false)

        // Add query button for timeout errors
        if (code == "E10") {
            builder.setPositiveButton("Query Status") { _, _ ->
                queryLastTransaction()
            }
            builder.setNeutralButton("Cancel") { _, _ ->
                resetAmountSelection()
            }
        } else {
            builder.setPositiveButton("OK") { _, _ ->
                resetAmountSelection()
            }
        }

        builder.show()
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
        hidePaymentProgressDialog()

        // Check connection status when returning to the activity
        checkConnectionStatus()
    }

    /**
     * Check current connection status and update UI accordingly
     */
    private fun checkConnectionStatus() {
        if (::paymentService.isInitialized) {
            val isConnected = paymentService.isConnected()
            
            if (isConnected) {
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
            }
        }
    }

    override fun onPause() {
        super.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()

        // Clean up resources
        hidePaymentProgressDialog()
        
        // Dismiss any current alert dialog
        currentAlertDialog?.dismiss()
        currentAlertDialog = null
        
        // Clear any pending callbacks to prevent memory leaks
        tvPaymentStatus.removeCallbacks(null)
        etCustomAmount.removeCallbacks(null)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        when (requestCode) {
            REQUEST_CODE_CONNECTION -> {
                if (resultCode == ConnectionActivity.RESULT_CONNECTION_CHANGED) {
                    // Connection settings changed, show result information
                    val connectionMessage = data?.getStringExtra("connection_message")

                    // Show connection result
                    connectionMessage?.let { message ->
                        showToast(message)
                    }

                    // Re-initialize payment service for the new mode
                    initPaymentService()
                    
                    // Attempt auto-connection with new settings
                    attemptAutoConnection()
                }
            }
            REQUEST_CODE_TRANSACTION_LIST -> {
                // From transaction history page return, no special processing required
            }
        }
    }
}