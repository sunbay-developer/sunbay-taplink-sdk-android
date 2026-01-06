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
import com.sunmi.tapro.taplink.sdk.TaplinkSDK

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
    private lateinit var statusIndicator: View
    private lateinit var tvConnectionType: TextView
    private lateinit var tvConnectionStatus: TextView
    private lateinit var btnSettings: Button
    private lateinit var btnTransactionHistory: Button
    private lateinit var btnAmount10: View
    private lateinit var btnAmount20: View
    private lateinit var btnAmount50: View
    private lateinit var btnAmount100: View
    private lateinit var etCustomAmount: EditText
    private lateinit var btnSale: Button
    private lateinit var btnAuth: Button
    private lateinit var btnForcedAuth: Button
    private lateinit var tvPaymentStatus: TextView
    private lateinit var cardPaymentStatus: View
    private lateinit var progressPayment: View

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

        // Initialize UI components synchronously - no init methods
        setupUIComponents()
        setupEventListeners()
        
        // Get payment service instance (SDK already initialized by Application)
        paymentService = TaplinkPaymentService.getInstance()
        
        // Update initial UI state
        updateAmountDisplay()
        updateTransactionButtonsState()
        
        Log.d(TAG, "MainActivity UI initialization completed")
    }

    override fun onStart() {
        super.onStart()
        // Start connection management after UI is ready
        startConnectionManagement()
    }

    /**
     * Setup UI components - synchronous initialization only
     */
    private fun setupUIComponents() {
        // Top bar
        layoutTopBar = findViewById(R.id.layout_top_bar)
        statusIndicator = findViewById(R.id.status_indicator)
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

        // Transaction buttons
        btnSale = findViewById(R.id.btn_sale)
        btnAuth = findViewById(R.id.btn_auth)
        btnForcedAuth = findViewById(R.id.btn_forced_auth)

        // Payment status display
        tvPaymentStatus = findViewById(R.id.tv_payment_status)
        cardPaymentStatus = findViewById(R.id.card_payment_status)
        progressPayment = findViewById(R.id.progress_payment)

        // Set initial connection status display
        updateConnectionStatusDisplay("Not Connected", false)
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

        // Preset amount buttons - 每个商品添加自己的价格
        btnAmount10.setOnClickListener { addAmount(3.50) }  // Coffee $3.50
        btnAmount20.setOnClickListener { addAmount(8.99) }  // Sandwich $8.99
        btnAmount50.setOnClickListener { addAmount(2.50) }  // Cola $2.50
        btnAmount100.setOnClickListener { addAmount(6.50) } // Hot Dog $6.50

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

    // Connection listener management
    private var currentConnectionListener: ConnectionListener? = null
    
    // SDK Connection management
    private var isDirectlyConnected = false
    private var connectedTaproVersion: String? = null

    /**
     * Start connection management - called after UI is ready
     */
    private fun startConnectionManagement() {
        // Check current connection status first
        checkCurrentConnectionStatus()
        
        // If not connected, attemptSDK connection
        if (!isSDKConnected()) {
            connect()
        } else {
            // Already connected, register listener for status monitoring
            registerDirectConnectionListener()
        }
    }
    
    /**
     * Check if SDK is directly connected
     */
    private fun isSDKConnected(): Boolean {
        return try {
            TaplinkSDK.isConnected()
        } catch (e: Exception) {
            Log.e(TAG, "Error checking SDK connection", e)
            false
        }
    }
    
    /**
     * Attempt SDK Connection following official example
     */
    private fun connect() {
        updateConnectionStatusDisplay("Connecting...", false)
        
        val savedMode = ConnectionPreferences.getConnectionMode(this)
        val connectionConfig = createConnectionConfig(savedMode)
        
        Log.d(TAG, "=== Attempting SDK Connection ===")
        Log.d(TAG, "Connection Mode: $savedMode")
        
        // SDK Connection as shown in example
        TaplinkSDK.connect(connectionConfig, object : com.sunmi.tapro.taplink.sdk.callback.ConnectionListener {
            override fun onConnected(deviceId: String, taproVersion: String) {
                Log.d(TAG, "=== SDK Connection SUCCESS ===")
                Log.d(TAG, "Device: $deviceId, Version: $taproVersion")
                
                isDirectlyConnected = true
                connectedTaproVersion = taproVersion
                
                runOnUiThread {
                    updateConnectionStatusDisplay("Connected", true)
                    Log.d(TAG, "UI updated - connection successful")
                }
            }
            
            override fun onDisconnected(reason: String) {
                Log.d(TAG, "=== SDK Connection DISCONNECTED ===")
                Log.d(TAG, "Reason: $reason")
                
                isDirectlyConnected = false
                connectedTaproVersion = null
                
                runOnUiThread {
                    updateConnectionStatusDisplay("Not Connected", false)
                    showConnectionExceptionDialog("Connection Lost", "Connection was disconnected: $reason")
                    Log.w(TAG, "UI updated - connection lost")
                }
            }
            
            override fun onError(error: com.sunmi.tapro.taplink.sdk.error.ConnectionError) {
                Log.d(TAG, "=== SDK Connection ERROR ===")
                Log.d(TAG, "Code: ${error.code}, Message: ${error.message}")
                
                isDirectlyConnected = false
                connectedTaproVersion = null
                
                runOnUiThread {
                    updateConnectionStatusDisplay("Connection Failed", false)
                    showConnectionExceptionDialog("Connection Error", "Failed to connect: ${error.message}\n\nError Code: ${error.code}")
                    Log.e(TAG, "UI updated - connection error")
                }
            }
        })
    }
    
    /**
     * Register direct connection listener for already connected SDK
     */
    private fun registerDirectConnectionListener() {
        Log.d(TAG, "SDK already connected, registering listener for status monitoring")
        
        // For already connected SDK, we still need to register a listener
        // This ensures we get notified of future disconnections
        val savedMode = ConnectionPreferences.getConnectionMode(this)
        val connectionConfig = createConnectionConfig(savedMode)
        
        TaplinkSDK.connect(connectionConfig, object : com.sunmi.tapro.taplink.sdk.callback.ConnectionListener {
            override fun onConnected(deviceId: String, taproVersion: String) {
                Log.d(TAG, "Direct SDK listener registered - already connected")
                isDirectlyConnected = true
                connectedTaproVersion = taproVersion
                runOnUiThread {
                    updateConnectionStatusDisplay("Connected", true)
                }
            }
            
            override fun onDisconnected(reason: String) {
                Log.d(TAG, "===SDK DISCONNECTED (via listener) ===")
                Log.d(TAG, "Reason: $reason")
                
                isDirectlyConnected = false
                connectedTaproVersion = null
                
                runOnUiThread {
                    updateConnectionStatusDisplay("Not Connected", false)
                    showConnectionExceptionDialog("Connection Lost", "Connection was disconnected: $reason")
                }
            }
            
            override fun onError(error: com.sunmi.tapro.taplink.sdk.error.ConnectionError) {
                Log.d(TAG, "===SDK ERROR (via listener) ===")
                Log.d(TAG, "Code: ${error.code}, Message: ${error.message}")
                
                isDirectlyConnected = false
                connectedTaproVersion = null
                
                runOnUiThread {
                    updateConnectionStatusDisplay("Connection Failed", false)
                    showConnectionExceptionDialog("Connection Error", "Connection error: ${error.message}\n\nError Code: ${error.code}")
                }
            }
        })
    }
    
    /**
     * Check current connection status without triggering new connection
     * This method performs a comprehensive status check usingSDK
     */
    private fun checkCurrentConnectionStatus() {
        try {
            val sdkConnected = isSDKConnected()
            val paymentServiceConnected = paymentService.isConnected()
            
            Log.d(TAG, "Connection status check - SDK: $sdkConnected, PaymentService: $paymentServiceConnected")
            
            // Use SDK status as the source of truth
            isDirectlyConnected = sdkConnected
            
            // If connected but no version info, we'll show "Connected" until we get version from callback
            if (sdkConnected && connectedTaproVersion == null) {
                Log.d(TAG, "Connected but no version info available yet")
            }
            
            val status = if (sdkConnected) "Connected" else "Not Connected"
            updateConnectionStatusDisplay(status, sdkConnected)
            
            Log.d(TAG, "Current connection status: $status (SDK: $sdkConnected, Version: $connectedTaproVersion)")
        } catch (e: Exception) {
            Log.e(TAG, "Error checking connection status", e)
            updateConnectionStatusDisplay("Status Unknown", false)
        }
    }

    /**
     * Start auto-connection based on saved connection mode
     * This method is called when we need to establish a new connection
     */
    private fun attemptAutoConnection() {
        // Use SDK Connection instead of PaymentService wrapper
        connect()
    }
    
    /**
     * Create connection configuration based on connection mode
     */
    private fun createConnectionConfig(mode: ConnectionPreferences.ConnectionMode): com.sunmi.tapro.taplink.sdk.config.ConnectionConfig {
        val connectionConfig = com.sunmi.tapro.taplink.sdk.config.ConnectionConfig()
        
        when (mode) {
            ConnectionPreferences.ConnectionMode.APP_TO_APP -> {
                connectionConfig.setConnectionMode(com.sunmi.tapro.taplink.sdk.enums.ConnectionMode.APP_TO_APP)
            }
            ConnectionPreferences.ConnectionMode.CABLE -> {
                connectionConfig.setConnectionMode(com.sunmi.tapro.taplink.sdk.enums.ConnectionMode.CABLE)
                // Add cable-specific configuration if needed
                val protocol = ConnectionPreferences.getCableProtocol(this)
                when (protocol) {
                    ConnectionPreferences.CableProtocol.AUTO -> {
                        // Let SDK auto-detect, no additional config needed
                    }
                    ConnectionPreferences.CableProtocol.USB_AOA -> {
                        connectionConfig.setCableProtocol(com.sunmi.tapro.taplink.sdk.enums.CableProtocol.USB_AOA)
                    }
                    ConnectionPreferences.CableProtocol.USB_VSP -> {
                        connectionConfig.setCableProtocol(com.sunmi.tapro.taplink.sdk.enums.CableProtocol.USB_VSP)
                    }
                    ConnectionPreferences.CableProtocol.RS232 -> {
                        connectionConfig.setCableProtocol(com.sunmi.tapro.taplink.sdk.enums.CableProtocol.RS232)
                    }
                }
            }
            ConnectionPreferences.ConnectionMode.LAN -> {
                connectionConfig.setConnectionMode(com.sunmi.tapro.taplink.sdk.enums.ConnectionMode.LAN)
                // Add LAN-specific configuration
                val lanConfig = ConnectionPreferences.getLanConfig(this)
                val ip = lanConfig.first
                val port = lanConfig.second
                
                if (ip.isNullOrEmpty()) {
                    // If no IP configured, this will likely fail, but let the SDK handle it
                    Log.w(TAG, "LAN mode selected but no IP configured")
                } else {
                    connectionConfig.setHost(ip).setPort(port)
                }
            }
        }
        
        return connectionConfig
    }



    /**
     * Update connection status display - separated from business logic
     */
    private fun updateConnectionStatusDisplay(status: String, connected: Boolean) {
        Log.d(TAG, "updateConnectionStatusDisplay - status: $status, connected: $connected")
        
        // Update connection mode display
        val currentMode = ConnectionPreferences.getConnectionMode(this)
        val modeText = when (currentMode) {
            ConnectionPreferences.ConnectionMode.APP_TO_APP -> "App-to-App"
            ConnectionPreferences.ConnectionMode.CABLE -> "Cable"
            ConnectionPreferences.ConnectionMode.LAN -> "LAN"
        }
        tvConnectionType.text = modeText
        
        // Update status indicator color based on connection state
        val indicatorDrawable = when {
            connected -> R.drawable.status_indicator_connected
            status.contains("Connecting", ignoreCase = true) -> R.drawable.status_indicator_connecting
            else -> R.drawable.status_indicator_disconnected
        }
        statusIndicator.setBackgroundResource(indicatorDrawable)
        
        // Show version code only when connected, otherwise show connection status
        if (connected) {
            val version = connectedTaproVersion
            tvConnectionStatus.text = if (version != null && version.isNotEmpty()) {
                "v$version"
            } else {
                "Connected"
            }
            Log.d(TAG, "Displaying version: $version")
        } else {
            tvConnectionStatus.text = status
        }

        // Update transaction button state (only depends on amount, not connection)
        updateTransactionButtonsState()
    }
    
    /**
     * Show connection exception dialog - unified error handling
     */
    private fun showConnectionExceptionDialog(title: String, message: String) {
        // Dismiss any existing dialog first
        currentAlertDialog?.dismiss()
        
        currentAlertDialog = AlertDialog.Builder(this)
            .setTitle(title)
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
        showConnectionExceptionDialog("Connection Failed", message)
    }

    /**
     * Add amount
     */
    private fun addAmount(amount: Double) {
        selectedAmount = selectedAmount.add(BigDecimal.valueOf(amount))

        // Update display
        updateAmountDisplay()
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

        // Update transaction buttons state
        updateTransactionButtonsState()

        Log.d(TAG, "Custom amount input: $selectedAmount")
    }

    /**
     * Update amount display in the input field
     */
    private fun updateAmountDisplay() {
        // Mark as programmatically updated to avoid TextWatcher trigger
        isUpdatingCustomAmount = true
        
        if (selectedAmount > BigDecimal.ZERO) {
            val amountText = selectedAmount.toString()
            etCustomAmount.setText(amountText)
            // Set cursor to the end of the text
            etCustomAmount.setSelection(amountText.length)
        } else {
            etCustomAmount.setText("")
        }
        
        isUpdatingCustomAmount = false
    }

    /**
     * Update transaction buttons state
     */
    private fun updateTransactionButtonsState() {
        // Allow buttons to be clicked regardless of connection status
        // Only check if amount is selected
        val hasAmount = selectedAmount > BigDecimal.ZERO

        btnSale.isEnabled = hasAmount
        btnAuth.isEnabled = hasAmount
        btnForcedAuth.isEnabled = hasAmount
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
        if (selectedAmount <= BigDecimal.ZERO) {
            showToast("Please select payment amount")
            return false
        }

        // Remove connection check - allow payment attempts even when not connected
        // The payment service will handle connection errors appropriately
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
        // Only check amount, not connection status
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
        // Only check amount, not connection status
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
                // Execute multiple sale payments using for loop
                for (i in 1..3) {
                    startSalePayment(null, null, null, null, null)
                }
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
        // Only check amount, not connection status
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

        // Show modern payment status card
        cardPaymentStatus.visibility = View.VISIBLE
        progressPayment.visibility = View.VISIBLE
        tvPaymentStatus.text = message

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
     * Check if error code indicates a connection problem
     */
    private fun isConnectionError(code: String): Boolean {
        return when (code) {
            "C36" -> true  // Target application crashed
            "C01" -> true  // Connection timeout
            "C02" -> true  // Connection failed
            "C03" -> true  // Connection lost
            "C04" -> true  // Service disconnected
            "C05" -> true  // Service binding failed
            "E10" -> true  // Transaction timeout (often connection related)
            else -> code.startsWith("C")  // Most C-codes are connection related
        }
    }
    
    /**
     * Handle payment failure with enhanced connection status management
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

        // Check if this is a connection-related error and update status immediately
        if (isConnectionError(code)) {
            Log.w(TAG, "Payment failed due to connection error: $code - $message")
            // Force connection status update
            updateConnectionStatusDisplay("Connection Failed", false)
            
            // Also trigger a comprehensive status check
            performConnectionStatusValidation()
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
                // Hide status card
                cardPaymentStatus.visibility = View.GONE
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
        updateAmountDisplay()
        cardPaymentStatus.visibility = View.GONE
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

        // Perform comprehensive connection status check when returning to activity
        performConnectionStatusValidation()
    }
    
    /**
     * Perform comprehensive connection status validation
     * This ensures UI reflects the actual connection state
     */
    private fun performConnectionStatusValidation() {
        try {
            // Check current status
            checkCurrentConnectionStatus()
            
            // If we think we're connected, validate it's actually working
            if (paymentService.isConnected()) {
                // The isConnected() method now includes SDK validation
                Log.d(TAG, "Connection validation completed - status should be accurate")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during connection status validation", e)
            updateConnectionStatusDisplay("Validation Failed", false)
        }
    }

    /**
     * Check current connection status and update UI accordingly
     * This method only checks status without triggering new connections
     */
    private fun checkConnectionStatus() {
        checkCurrentConnectionStatus()
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
        
        // Clear connection state
        isDirectlyConnected = false
        connectedTaproVersion = null
        
        // Clear any pending callbacks to prevent memory leaks
        tvPaymentStatus.removeCallbacks(null)
        etCustomAmount.removeCallbacks(null)
        
        Log.d(TAG, "MainActivity destroyed, connection state cleared")
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

                    // Restart connection management with new settings usingSDK
                    startConnectionManagement()
                }
            }
            REQUEST_CODE_TRANSACTION_LIST -> {
                // From transaction history page return, no special processing required
            }
        }
    }
}