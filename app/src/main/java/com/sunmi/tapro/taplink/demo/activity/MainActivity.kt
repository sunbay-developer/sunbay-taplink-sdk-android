package com.sunmi.tapro.taplink.demo.activity

import android.app.Activity
import android.app.AlertDialog
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
import com.sunmi.tapro.taplink.demo.activity.TransactionProgressActivity
import com.sunmi.tapro.taplink.demo.activity.RetryTransactionListener
import com.sunmi.tapro.taplink.demo.model.Transaction
import com.sunmi.tapro.taplink.demo.model.TransactionStatus
import com.sunmi.tapro.taplink.demo.model.TransactionType
import com.sunmi.tapro.taplink.demo.repository.TransactionRepository
import com.sunmi.tapro.taplink.demo.service.TaplinkPaymentService
import com.sunmi.tapro.taplink.demo.service.ConnectionListener
import com.sunmi.tapro.taplink.demo.service.PaymentCallback
import com.sunmi.tapro.taplink.demo.service.PaymentResult
import com.sunmi.tapro.taplink.demo.util.ConnectionPreferences
import com.sunmi.tapro.taplink.demo.util.Constants
import com.sunmi.tapro.taplink.sdk.TaplinkSDK

import java.math.BigDecimal
import java.text.DecimalFormat

/**
 * Main Activity for Taplink Demo Application
 *
 * This activity serves as the primary interface for payment operations, providing:
 * - Real-time connection status monitoring and display
 * - Amount selection through preset buttons and custom input
 * - Payment transaction initiation (SALE, AUTH, FORCED_AUTH)
 * - Automatic connection management with retry capabilities
 * - Navigation to settings and transaction history
 *
 * The activity maintains connection state and automatically handles reconnection
 * scenarios to provide a seamless user experience.
 */
class MainActivity : Activity() {

    companion object {
        private const val TAG = "MainActivity"
        private const val REQUEST_CODE_CONNECTION = Constants.REQUEST_CODE_CONNECTION
        private const val REQUEST_CODE_TRANSACTION_LIST = Constants.REQUEST_CODE_TRANSACTION_LIST
    }

    // UI component references for efficient access
    private lateinit var layoutTopBar: View
    private lateinit var statusIndicator: View
    private lateinit var connectionTypeText: TextView
    private lateinit var connectionStatusText: TextView
    private lateinit var settingsButton: Button
    private lateinit var transactionHistoryButton: Button
    private lateinit var amount10Button: View
    private lateinit var amount20Button: View
    private lateinit var amount50Button: View
    private lateinit var amount100Button: View
    private lateinit var customAmountInput: EditText
    private lateinit var saleButton: Button
    private lateinit var authButton: Button
    private lateinit var forcedAuthButton: Button
    private lateinit var paymentStatusText: TextView
    private lateinit var cardPaymentStatus: View
    private lateinit var progressPayment: View

    // Payment service instance for handling all payment operations
    private lateinit var paymentService: TaplinkPaymentService

    // Currently selected amount for transactions
    private var selectedAmount: BigDecimal = BigDecimal.ZERO

    // Formatter for consistent amount display throughout the app
    private val amountFormatter = DecimalFormat(Constants.AMOUNT_FORMAT_PATTERN)

    // Current alert dialog reference to prevent dialog leaks
    private var currentAlertDialog: AlertDialog? = null

    // Flag to prevent TextWatcher recursion during programmatic updates
    private var isUpdatingCustomAmount = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize UI components synchronously to ensure proper setup
        setupUIComponents()
        setupEventListeners()

        // Get payment service instance (SDK is already initialized by Application class)
        paymentService = TaplinkPaymentService.getInstance()

        // Set initial UI state before starting connection management
        updateAmountDisplay()
        updateTransactionButtonsState()

        // Setup retry transaction listener for TransactionProgressActivity
        setupRetryTransactionListener()

        Log.d(TAG, Constants.Messages.LOG_UI_INITIALIZATION_COMPLETED)
        startConnectionManagement()
    }

    /**
     * Setup retry transaction listener for TransactionProgressActivity communication
     * This allows TransactionProgressActivity to request transaction retries through MainActivity
     */
    private fun setupRetryTransactionListener() {
        TransactionProgressActivity.setRetryTransactionListener(object : RetryTransactionListener {
            override fun onRetryRequested(transaction: Transaction) {
                Log.d(TAG, "Retry requested for transaction: ${transaction.transactionRequestId}")

                // Execute the retry based on transaction type
                when (transaction.type) {
                    TransactionType.SALE -> {
                        retrySaleTransaction(transaction)
                    }

                    TransactionType.AUTH -> {
                        retryAuthTransaction(transaction)
                    }

                    TransactionType.FORCED_AUTH -> {
                        retryForcedAuthTransaction(transaction)
                    }

                    else -> {
                        Log.w(TAG, "Retry not supported for transaction type: ${transaction.type}")
                        showToast("Retry not supported for ${transaction.type} transactions")
                    }
                }
            }
        })
    }

    /**
     * Retry a SALE transaction with the same parameters
     */
    private fun retrySaleTransaction(originalTransaction: Transaction) {
        Log.d(TAG, "Retrying SALE transaction: ${originalTransaction.transactionRequestId}")

        // Create new transaction data for retry
        val retryTransactionData = createTransactionData()
        val retryTransaction = originalTransaction.copy(
            transactionRequestId = retryTransactionData.transactionRequestId,
            referenceOrderId = retryTransactionData.referenceOrderId,
            status = TransactionStatus.PROCESSING,
            timestamp = retryTransactionData.timestamp
        )

        // Save retry transaction to repository
        TransactionRepository.addTransaction(retryTransaction)

        // Launch TransactionProgressActivity for retry
        launchTransactionProgressActivity(retryTransaction)
    }

    /**
     * Retry an AUTH transaction with the same parameters
     */
    private fun retryAuthTransaction(originalTransaction: Transaction) {
        Log.d(TAG, "Retrying AUTH transaction: ${originalTransaction.transactionRequestId}")

        // Create new transaction data for retry
        val retryTransactionData = createTransactionData()
        val retryTransaction = originalTransaction.copy(
            transactionRequestId = retryTransactionData.transactionRequestId,
            referenceOrderId = retryTransactionData.referenceOrderId,
            status = TransactionStatus.PROCESSING,
            timestamp = retryTransactionData.timestamp
        )

        // Save retry transaction to repository
        TransactionRepository.addTransaction(retryTransaction)

        // Launch TransactionProgressActivity for retry
        launchTransactionProgressActivity(retryTransaction)
    }

    /**
     * Retry a FORCED_AUTH transaction with the same parameters
     */
    private fun retryForcedAuthTransaction(originalTransaction: Transaction) {
        Log.d(TAG, "Retrying FORCED_AUTH transaction: ${originalTransaction.transactionRequestId}")

        // Create new transaction data for retry
        val retryTransactionData = createTransactionData()
        val retryTransaction = originalTransaction.copy(
            transactionRequestId = retryTransactionData.transactionRequestId,
            referenceOrderId = retryTransactionData.referenceOrderId,
            status = TransactionStatus.PROCESSING,
            timestamp = retryTransactionData.timestamp
        )

        // Save retry transaction to repository
        TransactionRepository.addTransaction(retryTransaction)

        // Launch TransactionProgressActivity for retry
        launchTransactionProgressActivity(retryTransaction)
    }

    override fun onStart() {
        super.onStart()
        // Start connection management after UI is fully ready to handle status updates
    }

    /**
     * Initialize all UI components and set default states
     *
     * This method performs synchronous UI initialization only, without any
     * network operations or complex logic that could delay the UI setup.
     */
    private fun setupUIComponents() {
        // Top bar
        layoutTopBar = findViewById(R.id.layout_top_bar)
        statusIndicator = findViewById(R.id.status_indicator)
        connectionTypeText = findViewById(R.id.tv_connection_type)
        connectionStatusText = findViewById(R.id.tv_connection_status)
        settingsButton = findViewById(R.id.btn_settings)
        transactionHistoryButton = findViewById(R.id.btn_transaction_history)

        // Product selection buttons
        amount10Button = findViewById(R.id.btn_product_coffee)
        amount20Button = findViewById(R.id.btn_product_sandwich)
        amount50Button = findViewById(R.id.btn_product_lunch)
        amount100Button = findViewById(R.id.btn_product_dinner)

        // Custom amount input
        customAmountInput = findViewById(R.id.et_custom_amount)

        // Transaction buttons
        saleButton = findViewById(R.id.btn_sale)
        authButton = findViewById(R.id.btn_auth)
        forcedAuthButton = findViewById(R.id.btn_forced_auth)

        // Payment status display
        paymentStatusText = findViewById(R.id.tv_payment_status)
        cardPaymentStatus = findViewById(R.id.card_payment_status)
        progressPayment = findViewById(R.id.progress_payment)

        // Set initial connection status to provide immediate user feedback
        updateConnectionStatusDisplay()
    }

    /**
     * Configure all event listeners for user interactions
     *
     * Separates event listener setup into logical groups for better maintainability.
     * Each group handles a specific aspect of user interaction.
     */
    private fun setupEventListeners() {
        setupNavigationListeners()
        setupAmountButtonListeners()
        setupCustomAmountInputListener()
        setupTransactionButtonListeners()
    }

    /**
     * Configure navigation button click handlers
     *
     * These buttons provide access to configuration and transaction history,
     * which are essential for a complete payment application experience.
     */
    private fun setupNavigationListeners() {
        settingsButton.setOnClickListener {
            openConnectionSettings()
        }

        transactionHistoryButton.setOnClickListener {
            openTransactionHistory()
        }
    }

    /**
     * Configure preset amount button click handlers
     *
     * These buttons provide quick access to common transaction amounts,
     * improving user experience by reducing manual input for typical purchases.
     */
    private fun setupAmountButtonListeners() {
        amount10Button.setOnClickListener { addAmount(Constants.PRODUCT_COFFEE_PRICE.toDouble()) }  // Coffee
        amount20Button.setOnClickListener { addAmount(Constants.PRODUCT_SANDWICH_PRICE.toDouble()) }  // Sandwich
        amount50Button.setOnClickListener { addAmount(Constants.PRODUCT_COLA_PRICE.toDouble()) }  // Cola
        amount100Button.setOnClickListener { addAmount(Constants.PRODUCT_HOT_DOG_PRICE.toDouble()) } // Hot Dog
    }

    /**
     * Configure custom amount input text change monitoring
     *
     * Monitors text changes to update the selected amount in real-time,
     * providing immediate feedback to users as they type.
     */
    private fun setupCustomAmountInputListener() {
        customAmountInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                handleCustomAmountChanged(s.toString())
            }
        })
    }

    /**
     * Configure transaction button click handlers
     *
     * Each transaction type has different requirements and flows:
     * - SALE: Allows additional amounts (tip, tax, etc.)
     * - AUTH: Simple pre-authorization
     * - FORCED_AUTH: Requires manual authorization code input
     */
    private fun setupTransactionButtonListeners() {
        saleButton.setOnClickListener {
            showSaleAmountDialog()
        }

        authButton.setOnClickListener {
            startPayment(TransactionType.AUTH)
        }

        forcedAuthButton.setOnClickListener {
            startPayment(TransactionType.FORCED_AUTH)
        }
    }

    // Global connection listener for SDK status monitoring
    private var globalConnectionListener: com.sunmi.tapro.taplink.sdk.callback.ConnectionListener? = null

    // Connected device information
    private var connectedTaproVersion: String? = null

    /**
     * Start simplified connection management using SDK's global listener
     *
     * This simplified approach leverages SDK's built-in connection management:
     * 1. Set global connection listener once to monitor all connection events
     * 2. Attempt connection if not already connected
     * 3. Let SDK handle connection state management internally
     */
    private fun startConnectionManagement() {
        // Set global connection listener to monitor all connection events
        setupGlobalConnectionListener()

        // Check current status and connect if needed
        if (!TaplinkSDK.isConnected()) {
            attemptConnection()
        } else {
            // Already connected, just update UI
            updateConnectionStatusDisplay()
        }
    }

    /**
     * Setup global connection listener using SDK's setConnectionListener
     * This listener will receive all connection events globally
     */
    private fun setupGlobalConnectionListener() {
        globalConnectionListener = object : com.sunmi.tapro.taplink.sdk.callback.ConnectionListener {
            override fun onConnected(deviceId: String, taproVersion: String) {
                Log.d(TAG, "Global listener - Connected: $deviceId, Version: $taproVersion")
                connectedTaproVersion = taproVersion
                runOnUiThread {
                    updateConnectionStatusDisplay()
                }
            }

            override fun onDisconnected(reason: String) {
                Log.d(TAG, "Global listener - Disconnected: $reason")
                connectedTaproVersion = null
                runOnUiThread {
                    updateConnectionStatusDisplay()
                    showConnectionExceptionDialog(
                        Constants.Messages.TITLE_CONNECTION_LOST,
                        String.format(Constants.Messages.ERROR_CONNECTION_DISCONNECTED, reason)
                    )
                }
            }

            override fun onError(error: com.sunmi.tapro.taplink.sdk.error.ConnectionError) {
                Log.e(TAG, "Global listener - Error: ${error.code} - ${error.message}")
                connectedTaproVersion = null
                runOnUiThread {
                    updateConnectionStatusDisplay()
                    showConnectionExceptionDialog(
                        Constants.Messages.TITLE_CONNECTION_ERROR,
                        String.format(Constants.Messages.ERROR_FAILED_TO_CONNECT, error.message, error.code)
                    )
                }
            }
        }

        // Set the global listener in SDK
        TaplinkSDK.setConnectionListener(globalConnectionListener)
    }

    /**
     * Attempt connection using saved preferences
     * Simplified connection logic that relies on SDK's internal management
     */
    private fun attemptConnection() {
        updateConnectionStatusDisplayConnecting()

        val savedMode = ConnectionPreferences.getConnectionMode(this)
        val connectionConfig = createConnectionConfig(savedMode)

        Log.d(TAG, "Attempting connection with mode: $savedMode")

        // Use payment service to handle connection
        // The global listener will receive the connection events
        paymentService.connect(connectionConfig, object : ConnectionListener {
            override fun onConnected(deviceId: String, taproVersion: String) {
                // This will be handled by the global listener
                Log.d(TAG, "PaymentService connection callback - Connected")
            }

            override fun onDisconnected(reason: String) {
                // This will be handled by the global listener
                Log.d(TAG, "PaymentService connection callback - Disconnected")
            }

            override fun onError(code: String, message: String) {
                // This will be handled by the global listener
                Log.d(TAG, "PaymentService connection callback - Error")
            }
        })
    }

    /**
     * Create ConnectionConfig based on user's selected connection mode
     *
     * This method translates user preferences into SDK-compatible configuration:
     *
     * App-to-App Mode:
     * - Direct communication with Tapro app on the same device
     * - No additional configuration required
     *
     * Cable Mode:
     * - Physical connection via USB or RS232
     * - Supports multiple protocols (USB_AOA, USB_VSP, RS232)
     * - Protocol selection affects communication method
     *
     * LAN Mode:
     * - Network-based connection to remote payment terminal
     * - Requires IP address and port configuration
     * - Most complex setup but enables distributed payment processing
     *
     * @param mode The connection mode selected by the user
     * @return Configured ConnectionConfig object ready for SDK use
     */
    private fun createConnectionConfig(mode: ConnectionPreferences.ConnectionMode): com.sunmi.tapro.taplink.sdk.config.ConnectionConfig {
        val connectionConfig = com.sunmi.tapro.taplink.sdk.config.ConnectionConfig()

        when (mode) {
            ConnectionPreferences.ConnectionMode.APP_TO_APP -> {
                connectionConfig.setConnectionMode(com.sunmi.tapro.taplink.sdk.enums.ConnectionMode.APP_TO_APP)
            }

            ConnectionPreferences.ConnectionMode.CABLE -> {
                connectionConfig.setConnectionMode(com.sunmi.tapro.taplink.sdk.enums.ConnectionMode.CABLE)
                // Configure cable-specific protocol based on user selection
                // Different protocols handle different physical connection types
                val protocol = ConnectionPreferences.getCableProtocol(this)
                when (protocol) {
                    ConnectionPreferences.CableProtocol.AUTO -> {
                        // Let SDK auto-detect the best protocol for the connected hardware
                    }

                    ConnectionPreferences.CableProtocol.USB_AOA -> {
                        // Android Open Accessory protocol for USB connections
                        connectionConfig.setCableProtocol(com.sunmi.tapro.taplink.sdk.enums.CableProtocol.USB_AOA)
                    }

                    ConnectionPreferences.CableProtocol.USB_VSP -> {
                        // Virtual Serial Port protocol for USB connections
                        connectionConfig.setCableProtocol(com.sunmi.tapro.taplink.sdk.enums.CableProtocol.USB_VSP)
                    }

                    ConnectionPreferences.CableProtocol.RS232 -> {
                        // Traditional RS232 serial communication protocol
                        connectionConfig.setCableProtocol(com.sunmi.tapro.taplink.sdk.enums.CableProtocol.RS232)
                    }
                }
            }

            ConnectionPreferences.ConnectionMode.LAN -> {
                connectionConfig.setConnectionMode(com.sunmi.tapro.taplink.sdk.enums.ConnectionMode.LAN)
                // Configure network connection parameters
                val lanConfig = ConnectionPreferences.getLanConfig(this)
                val ip = lanConfig.first
                val port = lanConfig.second

                if (ip.isNullOrEmpty()) {
                    // Log warning but let SDK handle the missing IP scenario
                    // This allows for potential auto-discovery mechanisms
                    Log.w(TAG, Constants.Messages.LOG_LAN_MODE_NO_IP)
                } else {
                    // Set specific IP and port for targeted connection
                    connectionConfig.setHost(ip).setPort(port)
                }
            }
        }

        return connectionConfig
    }


    /**
     * Update connection status display based on SDK's actual connection state
     * No parameters needed - directly queries SDK for current status
     */
    private fun updateConnectionStatusDisplay() {
        // Get the real connection status from SDK
        val isConnected = try {
            TaplinkSDK.isConnected()
        } catch (e: Exception) {
            Log.e(TAG, "Error checking SDK connection status", e)
            false
        }

        Log.d(TAG, "Updating connection display based on SDK status: $isConnected")

        // Update connection mode display
        val currentMode = ConnectionPreferences.getConnectionMode(this)
        val modeText = when (currentMode) {
            ConnectionPreferences.ConnectionMode.APP_TO_APP -> Constants.Messages.MODE_APP_TO_APP
            ConnectionPreferences.ConnectionMode.CABLE -> Constants.Messages.MODE_CABLE
            ConnectionPreferences.ConnectionMode.LAN -> Constants.Messages.MODE_LAN
        }
        connectionTypeText.text = modeText

        // Update status indicator color based on actual SDK connection state
        val indicatorDrawable = if (isConnected) {
            R.drawable.status_indicator_connected
        } else {
            R.drawable.status_indicator_disconnected
        }
        statusIndicator.setBackgroundResource(indicatorDrawable)

        // Show version or status based on actual connection state
        connectionStatusText.text = if (isConnected) {
            if (connectedTaproVersion != null && connectedTaproVersion!!.isNotEmpty()) {
                "${Constants.Messages.VERSION_PREFIX}$connectedTaproVersion"
            } else {
                Constants.Messages.STATUS_CONNECTED
            }
        } else {
            Constants.Messages.STATUS_NOT_CONNECTED
        }

        // Update transaction button state
        updateTransactionButtonsState()
    }

    /**
     * Update connection status display with connecting state
     * Used only when showing connecting status before SDK connection completes
     */
    private fun updateConnectionStatusDisplayConnecting() {
        Log.d(TAG, "Showing connecting status")

        // Update connection mode display
        val currentMode = ConnectionPreferences.getConnectionMode(this)
        val modeText = when (currentMode) {
            ConnectionPreferences.ConnectionMode.APP_TO_APP -> Constants.Messages.MODE_APP_TO_APP
            ConnectionPreferences.ConnectionMode.CABLE -> Constants.Messages.MODE_CABLE
            ConnectionPreferences.ConnectionMode.LAN -> Constants.Messages.MODE_LAN
        }
        connectionTypeText.text = modeText

        // Show connecting indicator
        statusIndicator.setBackgroundResource(R.drawable.status_indicator_connecting)

        // Show connecting status
        connectionStatusText.text = Constants.Messages.STATUS_CONNECTING

        // Update transaction button state
        updateTransactionButtonsState()
    }

    /**
     * Show connection exception dialog with retry option
     */
    private fun showConnectionExceptionDialog(title: String, message: String) {
        // Check if activity is still valid and not finishing
        if (isFinishing || isDestroyed) {
            Log.w(TAG, "Activity is finishing or destroyed, cannot show connection exception dialog")
            return
        }

        try {
            // Dismiss any existing dialog first
            currentAlertDialog?.dismiss()

            currentAlertDialog = AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(Constants.Messages.BUTTON_RETRY) { dialog, _ ->
                    dialog.dismiss()
                    currentAlertDialog = null
                    attemptConnection()
                }
                .setNegativeButton(Constants.Messages.BUTTON_CANCEL) { dialog, _ ->
                    dialog.dismiss()
                    currentAlertDialog = null
                }
                .setOnDismissListener {
                    currentAlertDialog = null
                }
                .show()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show connection exception dialog", e)
        }
    }


    /**
     * Show simple error dialog
     */
    private fun showError(title: String, message: String) {
        // Check if activity is still valid and not finishing
        if (isFinishing || isDestroyed) {
            Log.w(TAG, "Activity is finishing or destroyed, cannot show error dialog")
            return
        }

        try {
            // Dismiss any existing dialog first
            currentAlertDialog?.dismiss()

            currentAlertDialog = AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(Constants.Messages.BUTTON_OK) { dialog, _ ->
                    dialog.dismiss()
                    currentAlertDialog = null
                }
                .setOnDismissListener {
                    currentAlertDialog = null
                }
                .show()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show error dialog", e)
        }
    }

    /**
     * Show connection failure dialog with retry option
     */
    private fun showConnectionFailure(message: String) {
        showConnectionExceptionDialog(Constants.Messages.TITLE_CONNECTION_FAILED, message)
    }

    /**
     * Add amount
     */
    private fun addAmount(amount: Double) {
        selectedAmount = selectedAmount.add(BigDecimal.valueOf(amount))

        // Update display
        updateAmountDisplay()
        updateTransactionButtonsState()

        Log.d(TAG, String.format(Constants.Messages.LOG_ADD_AMOUNT, amount, selectedAmount))
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

        Log.d(TAG, String.format(Constants.Messages.LOG_CUSTOM_AMOUNT_INPUT, selectedAmount))
    }

    /**
     * Update amount display in the input field
     */
    private fun updateAmountDisplay() {
        // Mark as programmatically updated to avoid TextWatcher trigger
        isUpdatingCustomAmount = true

        if (selectedAmount > BigDecimal.ZERO) {
            val amountText = selectedAmount.toString()
            customAmountInput.setText(amountText)
            // Set cursor to the end of the text
            customAmountInput.setSelection(amountText.length)
        } else {
            customAmountInput.setText("")
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

        saleButton.isEnabled = hasAmount
        authButton.isEnabled = hasAmount
        forcedAuthButton.isEnabled = hasAmount
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
        val transaction =
            createSaleTransaction(transactionData, surchargeAmount, tipAmount, taxAmount, cashbackAmount, serviceFee)

        TransactionRepository.addTransaction(transaction)

        // Launch TransactionProgressActivity instead of showing ProgressDialog
        launchTransactionProgressActivity(transaction)
    }

    /**
     * Validate payment conditions
     */
    private fun validatePaymentConditions(): Boolean {
        if (selectedAmount <= BigDecimal.ZERO) {
            showToast(Constants.Messages.TOAST_SELECT_PAYMENT_AMOUNT)
            return false
        }

        // Remove connection check - allow payment attempts even when not connected
        // The payment service will handle connection errors appropriately
        return true
    }

    /**
     * Launch TransactionProgressActivity for unified progress display
     * Replaces the old ProgressDialog approach with a modern full-screen activity
     *
     * @param transaction Transaction object containing all necessary data
     */
    private fun launchTransactionProgressActivity(transaction: Transaction) {
        Log.d(TAG, "Launching TransactionProgressActivity for transaction: ${transaction.transactionRequestId}")

        val intent = TransactionProgressActivity.createIntent(this, transaction)
        // Let TransactionProgressActivity handle the payment automatically
        // Remove the auto_start_payment = false setting
        startActivity(intent)

        Log.d(TAG, "TransactionProgressActivity launched successfully")
    }

    /**
     * Create transaction data with IDs
     */
    private fun createTransactionData(): TransactionData {
        val timestamp = System.currentTimeMillis()
        return TransactionData(
            timestamp = timestamp,
            referenceOrderId = "${Constants.ORDER_ID_PREFIX}$timestamp",
            transactionRequestId = "${Constants.TRANSACTION_REQUEST_ID_PREFIX}${timestamp}_${(Constants.TRANSACTION_ID_MIN_RANDOM..Constants.TRANSACTION_ID_MAX_RANDOM).random()}"
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
            currency = Constants.getDefaultCurrency(),
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
     * Create payment callback that forwards updates to TransactionProgressActivity
     * This replaces the old ProgressDialog approach with unified progress activity communication
     */
    private fun createPaymentCallback(transactionRequestId: String): PaymentCallback {
        return object : PaymentCallback {
            override fun onSuccess(result: PaymentResult) {
                runOnUiThread {
                    // Forward success result to TransactionProgressActivity if active
                    if (TransactionProgressActivity.isActive()) {
                        TransactionProgressActivity.showResultFromExternal(result, null, null)
                    } else {
                        // Fallback to original handling if progress activity is not active
                        handlePaymentSuccess(result)
                    }
                }
            }

            override fun onFailure(code: String, message: String) {
                runOnUiThread {
                    // Forward failure result to TransactionProgressActivity if active
                    if (TransactionProgressActivity.isActive()) {
                        TransactionProgressActivity.showResultFromExternal(null, code, message)
                    } else {
                        // Fallback to original handling if progress activity is not active
                        handlePaymentFailure(transactionRequestId, code, message)
                    }
                }
            }

            override fun onProgress(status: String, message: String) {
                runOnUiThread {
                    // Forward progress updates to TransactionProgressActivity if active
                    if (TransactionProgressActivity.isActive()) {
                        val displayMessage = when {
                            message.matches(Regex(".*transaction processing\\.\\.\\.", RegexOption.IGNORE_CASE)) ->
                                "Payment processing, please complete in Tapro app"

                            else -> message
                        }
                        TransactionProgressActivity.updateProgressFromExternal(status, displayMessage)
                    } else {
                        // Fallback to original handling if progress activity is not active
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
        // Validate payment conditions
        if (!validatePaymentConditions()) return

        // Create transaction data and record
        val transactionData = createTransactionData()
        val transaction = createTransactionRecord(transactionData, transactionType)

        // Save transaction to repository
        TransactionRepository.addTransaction(transaction)

        // Launch TransactionProgressActivity instead of showing ProgressDialog
        launchTransactionProgressActivity(transaction)
    }

    /**
     * Create transaction record for the given transaction type
     */
    private fun createTransactionRecord(data: TransactionData, transactionType: TransactionType): Transaction {
        return Transaction(
            transactionRequestId = data.transactionRequestId,
            transactionId = null,
            referenceOrderId = data.referenceOrderId,
            type = transactionType,
            amount = selectedAmount,
            currency = Constants.getDefaultCurrency(),
            status = TransactionStatus.PROCESSING,
            timestamp = data.timestamp
        )
    }

    /**
     * Show sale amount dialog for user to enter additional amounts
     */
    private fun showSaleAmountDialog() {
        // Only check amount, not connection status
        if (selectedAmount <= BigDecimal.ZERO) {
            showToast(Constants.Messages.TOAST_SELECT_PAYMENT_AMOUNT)
            return
        }

        // Create dialog layout
        val dialogView = layoutInflater.inflate(R.layout.dialog_additional_amounts, null)
        val surchargeAmountInput = dialogView.findViewById<EditText>(R.id.et_surcharge_amount)
        val tipAmountInput = dialogView.findViewById<EditText>(R.id.et_tip_amount)
        val taxAmountInput = dialogView.findViewById<EditText>(R.id.et_tax_amount)
        val cashbackAmountInput = dialogView.findViewById<EditText>(R.id.et_cashback_amount)
        val serviceFeeInput = dialogView.findViewById<EditText>(R.id.et_service_fee)

        // Set base amount
        val baseAmountText = dialogView.findViewById<TextView>(R.id.tv_base_amount)
        baseAmountText.text = amountFormatter.format(selectedAmount)

        // Hide service fee field
        val serviceFeeText = dialogView.findViewById<TextView>(R.id.tv_service_fee)
        serviceFeeInput.visibility = View.GONE
        serviceFeeText.visibility = View.GONE

        // Check if activity is still valid and not finishing
        if (isFinishing || isDestroyed) {
            Log.w(TAG, "Activity is finishing or destroyed, cannot show additional amounts dialog")
            return
        }

        try {
            AlertDialog.Builder(this)
                .setView(dialogView)
                .setPositiveButton(Constants.Messages.BUTTON_PROCEED) { _, _ ->
                    val surchargeAmount = surchargeAmountInput.text.toString().toDoubleOrNull()
                    val tipAmount = tipAmountInput.text.toString().toDoubleOrNull()
                    val taxAmount = taxAmountInput.text.toString().toDoubleOrNull()
                    val cashbackAmount = cashbackAmountInput.text.toString().toDoubleOrNull()
                    val serviceFee = serviceFeeInput.text.toString().toDoubleOrNull()

                    startSalePayment(surchargeAmount, tipAmount, taxAmount, cashbackAmount, serviceFee)
                }
                .setNegativeButton(Constants.Messages.BUTTON_CANCEL, null)
                .setNeutralButton(Constants.Messages.BUTTON_SKIP) { _, _ ->
                    // Execute multiple sale payments using for loop
                    for (i in 1..Constants.MULTIPLE_SALE_PAYMENT_COUNT) {
                        startSalePayment(null, null, null, null, null)
                    }
                }
                .show()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show additional amounts dialog", e)
            // Fallback: proceed with default values
            startSalePayment(null, null, null, null, null)
        }
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
            showToast(Constants.Messages.TOAST_SELECT_PAYMENT_AMOUNT)
            return
        }

        // Create dialog layout
        val dialogView = layoutInflater.inflate(R.layout.dialog_additional_amounts, null)
        val tipAmountInput = dialogView.findViewById<EditText>(R.id.et_tip_amount)
        val taxAmountInput = dialogView.findViewById<EditText>(R.id.et_tax_amount)
        val surchargeAmountText = dialogView.findViewById<TextView>(R.id.tv_surcharge_amount)
        val surchargeAmountInput = dialogView.findViewById<EditText>(R.id.et_surcharge_amount)
        val cashbackAmountText = dialogView.findViewById<TextView>(R.id.tv_cashback_amount)
        val cashbackAmountInput = dialogView.findViewById<EditText>(R.id.et_cashback_amount)
        val serviceFeeText = dialogView.findViewById<TextView>(R.id.tv_service_fee)
        val serviceFeeInput = dialogView.findViewById<EditText>(R.id.et_service_fee)

        // Set base amount
        val baseAmountText = dialogView.findViewById<TextView>(R.id.tv_base_amount)
        baseAmountText.text = amountFormatter.format(selectedAmount)

        // Hide fields not needed for FORCED_AUTH (only show tip and tax)
        surchargeAmountText.visibility = View.GONE
        surchargeAmountInput.visibility = View.GONE
        cashbackAmountText.visibility = View.GONE
        cashbackAmountInput.visibility = View.GONE
        serviceFeeText.visibility = View.GONE
        serviceFeeInput.visibility = View.GONE

        // Pre-fill with original transaction amounts if available
        transaction.tipAmount?.let { tipAmountInput.setText(it.toString()) }
        transaction.taxAmount?.let { taxAmountInput.setText(it.toString()) }

        // Check if activity is still valid and not finishing
        if (isFinishing || isDestroyed) {
            Log.w(TAG, "Activity is finishing or destroyed, cannot show forced auth amount dialog")
            return
        }

        try {
            AlertDialog.Builder(this)
                .setTitle(Constants.Messages.TITLE_FORCED_AUTH_AMOUNTS)
                .setMessage(String.format(Constants.Messages.RESULT_BASE_AMOUNT, amountFormatter.format(transaction.amount)))
                .setView(dialogView)
                .setPositiveButton(Constants.Messages.BUTTON_PROCEED) { _, _ ->
                    val tipAmount = tipAmountInput.text.toString().toDoubleOrNull()
                    val taxAmount = taxAmountInput.text.toString().toDoubleOrNull()

                    // Update transaction with additional amounts
                    val updatedTransaction = transaction.copy(
                        tipAmount = tipAmount?.let { BigDecimal.valueOf(it) },
                        taxAmount = taxAmount?.let { BigDecimal.valueOf(it) }
                    )

                    // Update transaction in repository
                    TransactionRepository.updateTransactionWithAmounts(
                        transactionRequestId = updatedTransaction.transactionRequestId,
                        status = updatedTransaction.status,
                        tipAmount = updatedTransaction.tipAmount,
                        taxAmount = updatedTransaction.taxAmount
                    )

                    // Launch TransactionProgressActivity with updated transaction
                    launchTransactionProgressActivity(updatedTransaction)
                }
                .setNegativeButton(Constants.Messages.BUTTON_CANCEL) { _, _ ->
                    // No need to hide progress dialog since we're not showing it anymore
                }
                .setNeutralButton(Constants.Messages.BUTTON_SKIP) { _, _ ->
                    // Launch TransactionProgressActivity with original transaction
                    launchTransactionProgressActivity(transaction)
                }
                .setCancelable(false)
                .show()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show forced auth amount dialog", e)
            // Fallback: launch progress activity and execute without additional amounts
            launchTransactionProgressActivity(transaction)
        }
    }

    /**
     * Update payment progress (legacy method - kept for fallback compatibility)
     *
     * This method is now primarily used as a fallback when TransactionProgressActivity
     * is not active. The unified progress display is handled by TransactionProgressActivity.
     */
    private fun updatePaymentProgress(message: String) {
        // Show modern payment status card as fallback
        cardPaymentStatus.visibility = View.VISIBLE
        progressPayment.visibility = View.VISIBLE
        paymentStatusText.text = message

        Log.d(TAG, "Payment progress (fallback): $message")
    }

    /**
     * Handle payment success
     */
    private fun handlePaymentSuccess(result: PaymentResult) {
        Log.d(TAG, "Result: $result")
        Log.d(TAG, "Payment result - TransactionId: ${result.transactionId}, Status: ${result.transactionStatus}")
        Log.d(TAG, "Result transactionRequestId: ${result.transactionRequestId}")

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
            title = Constants.Messages.TITLE_PAYMENT_SUCCESS,
            message = String.format(
                Constants.Messages.RESULT_TRANSACTION_COMPLETED,
                amountFormatter.format(result.amount?.transAmount),
                result.transactionId ?: Constants.Messages.PLACEHOLDER_NA,
                result.authCode ?: Constants.Messages.PLACEHOLDER_NA,
                result.transactionStatus
            ),
            isSuccess = true
        )
        resetAmountSelection()
    }

    /**
     * Show failed dialog
     */
    private fun showFailedDialog(result: PaymentResult) {
        showPaymentResultDialog(
            title = Constants.Messages.TITLE_PAYMENT_FAILED,
            message = String.format(
                Constants.Messages.RESULT_TRANSACTION_FAILED,
                amountFormatter.format(selectedAmount),
                result.transactionId ?: Constants.Messages.PLACEHOLDER_NA,
                result.transactionStatus,
                result.transactionResultCode ?: Constants.Messages.PLACEHOLDER_NA,
                result.transactionResultMsg ?: Constants.Messages.PLACEHOLDER_NA
            ),
            isSuccess = false
        )
    }

    /**
     * Show processing dialog
     */
    private fun showProcessingDialog(result: PaymentResult) {
        showPaymentResultDialog(
            title = Constants.Messages.TITLE_PAYMENT_PROCESSING,
            message = String.format(
                Constants.Messages.RESULT_TRANSACTION_PROCESSING,
                amountFormatter.format(selectedAmount),
                result.transactionId ?: Constants.Messages.PLACEHOLDER_NA,
                result.transactionStatus
            ),
            isSuccess = false
        )
    }

    /**
     * Show unknown status dialog
     */
    private fun showUnknownStatusDialog(result: PaymentResult) {
        showPaymentResultDialog(
            title = Constants.Messages.TITLE_UNKNOWN_PAYMENT_RESULT,
            message = String.format(
                Constants.Messages.RESULT_UNKNOWN_STATUS,
                amountFormatter.format(selectedAmount),
                result.transactionId ?: Constants.Messages.PLACEHOLDER_NA,
                result.transactionStatus ?: Constants.Messages.PLACEHOLDER_UNKNOWN
            ),
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
            // Force connection status update - the global listener will handle further updates
            updateConnectionStatusDisplay()
        }

        // Show simple payment error dialog
        paymentStatusText.postDelayed({
            showPaymentError(code, message)
        }, Constants.getErrorDialogShowDelay()) // Short delay to ensure any UI updates are complete
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

            // If TransactionProgressActivity is active, let it handle the query
            if (TransactionProgressActivity.isActive()) {
                Log.d(TAG, "TransactionProgressActivity is active, delegating query to it")
                // The progress activity will handle the query through its own queryTransactionStatus method
                return
            }

            // Launch TransactionProgressActivity for query operation
            Log.d(TAG, "Launching TransactionProgressActivity for query operation")
            launchTransactionProgressActivity(transaction)
        } ?: run {
            Log.w(TAG, "No transaction found to query")
            showToast("No transaction found to query")
        }
    }

    /**
     * Show payment result dialog
     */
    private fun showPaymentResultDialog(title: String, message: String, isSuccess: Boolean) {
        // Check if activity is still valid and not finishing
        if (isFinishing || isDestroyed) {
            Log.w(TAG, "Activity is finishing or destroyed, cannot show payment result dialog")
            return
        }

        try {
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
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show payment result dialog", e)
            // Fallback: hide status card directly
            cardPaymentStatus.visibility = View.GONE
        }
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
            .setTitle(Constants.Messages.TITLE_PAYMENT_ERROR)
            .setMessage(fullMessage)
            .setCancelable(false)

        // Add query button for timeout errors
        if (code == Constants.ConnectionErrorCodes.TRANSACTION_TIMEOUT) {
            builder.setPositiveButton(Constants.Messages.BUTTON_QUERY_STATUS) { _, _ ->
                queryLastTransaction()
            }
            builder.setNeutralButton(Constants.Messages.BUTTON_CANCEL) { _, _ ->
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

        // Simple connection status check - let global listener handle updates
        Log.d(TAG, "onResume - Current connection status: ${TaplinkSDK.isConnected()}")
    }

    override fun onPause() {
        super.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()

        // Clean up resources
        // Clear retry transaction listener
        TransactionProgressActivity.setRetryTransactionListener(null)

        // Dismiss any current alert dialog
        currentAlertDialog?.dismiss()
        currentAlertDialog = null

        // Clear global connection listener
        globalConnectionListener = null

        // Clear version info
        connectedTaproVersion = null

        // Clear any pending callbacks to prevent memory leaks
        paymentStatusText.removeCallbacks(null)
        customAmountInput.removeCallbacks(null)

        Log.d(TAG, "MainActivity destroyed, resources cleaned up")
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