package com.sunmi.tapro.taplink.demo.activity

import android.content.Intent
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.lifecycle.lifecycleScope
import com.sunmi.tapro.taplink.demo.R
import com.sunmi.tapro.taplink.demo.service.ConnectionListener
import com.sunmi.tapro.taplink.demo.service.PaymentService
import com.sunmi.tapro.taplink.demo.service.TaplinkPaymentService
import com.sunmi.tapro.taplink.demo.util.ConnectionPreferences
import com.sunmi.tapro.taplink.demo.util.NetworkUtils
import com.sunmi.tapro.taplink.demo.util.Constants
import kotlinx.coroutines.launch

/**
 * Connection Configuration Activity
 * 
 * Provides a comprehensive interface for configuring payment terminal connections.
 * Supports multiple connection modes with mode-specific configuration options:
 * 
 * - App-to-App: Direct communication with Tapro app (no additional config needed)
 * - Cable: Physical connection via USB or RS232 with protocol selection
 * - LAN: Network connection requiring IP address and port configuration
 * 
 * The activity includes real-time validation, network connectivity testing,
 * and automatic reconnection with the new configuration upon confirmation.
 */
class ConnectionActivity : AppCompatActivity() {
    
    companion object {
        private const val TAG = "ConnectionActivity"
        const val RESULT_CONNECTION_CHANGED = 100
        // Click interval protection to prevent accidental double-clicks
        private const val CLICK_INTERVAL: Long = 1000L
    }
    
    // UI components for connection mode selection
    private lateinit var connectionModeGroup: RadioGroup
    private lateinit var appToAppRadio: RadioButton
    private lateinit var cableRadio: RadioButton
    private lateinit var lanRadio: RadioButton
    private lateinit var cloudRadio: RadioButton
    
    // Configuration panels for each connection mode
    private lateinit var layoutAppToAppConfig: CardView
    private lateinit var layoutCableConfig: CardView
    private lateinit var layoutLanConfig: CardView
    private lateinit var layoutCloudConfig: CardView
    
    // LAN-specific configuration inputs
    private lateinit var lanIpInput: EditText
    private lateinit var lanPortInput: EditText
    private lateinit var switchTls: Switch
    
    // Cable-specific configuration inputs
    private lateinit var spinnerCableProtocol: Spinner
    
    // Error display components
    private lateinit var cardConfigError: CardView
    private lateinit var configErrorText: TextView

    private lateinit var confirmButton: Button
    private lateinit var exitAppButton: Button
    
    // Currently selected connection mode
    private var selectedMode: ConnectionPreferences.ConnectionMode = ConnectionPreferences.ConnectionMode.APP_TO_APP
    
    // Payment service instance for connection management
    private val paymentService: PaymentService = TaplinkPaymentService.getInstance()
    
    // Anti-duplicate click protection mechanism
    private var lastClickTime: Long = 0
    
    // Dialog reference for proper cleanup and memory management
    private var currentAlertDialog: android.app.AlertDialog? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_connection)
        initViews()
        loadCurrentConfig()
        setupListeners()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        
        // Clean up validation runnables to prevent memory leaks and crashes
        lanIpInput.removeCallbacks(ipValidationRunnable)
        lanPortInput.removeCallbacks(portValidationRunnable)
        
        // Clear any other pending callbacks as a safety measure
        lanIpInput.removeCallbacks(null)
        lanPortInput.removeCallbacks(null)
        
        // Dismiss any current alert dialog to prevent window leaks
        currentAlertDialog?.dismiss()
        currentAlertDialog = null
    }
    
    /**
     * Prevent duplicate button clicks within the specified interval
     * 
     * This is important for network operations and connection attempts
     * which should not be triggered multiple times in quick succession.
     * 
     * @return true if click is allowed, false if too soon after last click
     */
    private fun canClick(): Boolean {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastClickTime > CLICK_INTERVAL) {
            lastClickTime = currentTime
            return true
        }
        return false
    }
    
    /**
     * Initialize all view components and establish references
     * 
     * This method sets up all UI component references needed throughout
     * the activity lifecycle. It's called once during onCreate to ensure
     * all views are properly initialized before use.
     */
    private fun initViews() {
        // Connection mode selection
        connectionModeGroup = findViewById(R.id.rg_connection_mode)
        appToAppRadio = findViewById(R.id.rb_app_to_app)
        cableRadio = findViewById(R.id.rb_cable)
        lanRadio = findViewById(R.id.rb_lan)
        cloudRadio = findViewById(R.id.rb_cloud)
        
        // Configuration areas
        layoutAppToAppConfig = findViewById(R.id.layout_app_to_app_config)
        layoutCableConfig = findViewById(R.id.layout_cable_config)
        layoutLanConfig = findViewById(R.id.layout_lan_config)
        layoutCloudConfig = findViewById(R.id.layout_cloud_config)
        
        // LAN configuration inputs
        lanIpInput = findViewById(R.id.et_lan_ip)
        lanPortInput = findViewById(R.id.et_lan_port)
        
        // Cable configuration inputs
        spinnerCableProtocol = findViewById(R.id.spinner_cable_protocol)
        
        // Error prompts
        cardConfigError = findViewById(R.id.card_config_error)
        configErrorText = findViewById(R.id.tv_config_error)
        
        // Buttons
        confirmButton = findViewById(R.id.btn_confirm)
        exitAppButton = findViewById(R.id.btn_exit_app)
    }
    
    /**
     * Load and apply the currently saved connection configuration
     * 
     * Restores the user's previous connection settings from preferences
     * and updates the UI to reflect the current configuration. This ensures
     * continuity between app sessions and provides immediate visual feedback.
     */
    private fun loadCurrentConfig() {
        // Load saved connection mode
        val currentMode = ConnectionPreferences.getConnectionMode(this)
        selectedMode = currentMode
        
        // Set corresponding RadioButton checked
        when (currentMode) {
            ConnectionPreferences.ConnectionMode.APP_TO_APP -> {
                appToAppRadio.isChecked = true
                showConfigArea(ConnectionPreferences.ConnectionMode.APP_TO_APP)
            }
            ConnectionPreferences.ConnectionMode.CABLE -> {
                cableRadio.isChecked = true
                showConfigArea(ConnectionPreferences.ConnectionMode.CABLE)
                setupCableProtocolSpinner()
            }
            ConnectionPreferences.ConnectionMode.LAN -> {
                lanRadio.isChecked = true
                showConfigArea(ConnectionPreferences.ConnectionMode.LAN)
                loadLanConfig()
            }
        }
        
        Log.d(TAG, "Load current configuration - Connection mode: $currentMode")
    }
    
    /**
     * Load LAN configuration
     */
    private fun loadLanConfig() {
        val lanConfig = ConnectionPreferences.getLanConfig(this)
        val ip = lanConfig.first
        val port = lanConfig.second
        
        ip?.let { lanIpInput.setText(it) }
        lanPortInput.setText(port.toString())
//        switchTls.isChecked = false // LAN mode defaults to TLS disabled
        
        Log.d(TAG, "Load LAN configuration - IP: $ip, Port: $port")
    }
    
    /**
     * Setup cable protocol spinner
     */
    private fun setupCableProtocolSpinner() {
        // Create protocol display names
        val protocolNames = arrayOf(
            "AUTO (Auto-detect)",
            "USB_AOA (USB Android Open Accessory)",
            "USB_VSP (USB Virtual Serial Port)",
            "RS232 (Standard RS232 Serial)"
        )
        
        // Create adapter for spinner
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, protocolNames)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        
        // Set adapter to spinner
        spinnerCableProtocol.adapter = adapter
        
        // Load saved protocol or set default to AUTO
        val savedProtocol = ConnectionPreferences.getCableProtocol(this)
        spinnerCableProtocol.setSelection(savedProtocol.ordinal)
        
        Log.d(TAG, "Cable protocol spinner setup with saved protocol: $savedProtocol")
    }
    
    /**
     * Set up event listeners
     */
    private fun setupListeners() {
        // Connection mode selection listener
        connectionModeGroup.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.rb_app_to_app -> {
                    selectedMode = ConnectionPreferences.ConnectionMode.APP_TO_APP
                    showConfigArea(ConnectionPreferences.ConnectionMode.APP_TO_APP)
                }
                R.id.rb_cable -> {
                    selectedMode = ConnectionPreferences.ConnectionMode.CABLE
                    showConfigArea(ConnectionPreferences.ConnectionMode.CABLE)
                    setupCableProtocolSpinner() // Initialize cable protocol spinner
                }
                R.id.rb_lan -> {
                    selectedMode = ConnectionPreferences.ConnectionMode.LAN
                    showConfigArea(ConnectionPreferences.ConnectionMode.LAN)
                    loadLanConfig() // Reload LAN configuration
                }
            }
            
            // Hide error prompt
            hideConfigError()
        }

        
        // Confirm button click listener
        confirmButton.setOnClickListener {
            if (canClick()) {
                handleConfirm()
            }
        }
        
        // Exit app button click listener
        exitAppButton.setOnClickListener {
            if (canClick()) {
                handleExitApp()
            }
        }
        
        // LAN configuration real-time validation listeners
        setupLanConfigValidation()
    }
    
    /**
     * Set up real-time validation for LAN configuration inputs
     */
    private fun setupLanConfigValidation() {
        // IP address real-time validation
        lanIpInput.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                validateLanIpInput()
            } else {
                hideConfigError()
            }
        }
        
        // Port number real-time validation
        lanPortInput.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                validateLanPortInput()
            } else {
                hideConfigError()
            }
        }
        
        // Add text change listeners for immediate feedback
        lanIpInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                // Only validate if user has finished typing (after a short delay)
                lanIpInput.removeCallbacks(ipValidationRunnable)
                lanIpInput.postDelayed(ipValidationRunnable, Constants.getInputValidationDelay())
            }
        })
        
        lanPortInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                // Only validate if user has finished typing (after a short delay)
                lanPortInput.removeCallbacks(portValidationRunnable)
                lanPortInput.postDelayed(portValidationRunnable, Constants.getInputValidationDelay())
            }
        })
    }
    
    // Validation runnables for delayed validation
    private val ipValidationRunnable = Runnable {
        if (selectedMode == ConnectionPreferences.ConnectionMode.LAN) {
            validateLanIpInput()
        }
    }
    
    private val portValidationRunnable = Runnable {
        if (selectedMode == ConnectionPreferences.ConnectionMode.LAN) {
            validateLanPortInput()
        }
    }
    
    /**
     * Validate LAN IP address input
     */
    private fun validateLanIpInput() {
        val ip = lanIpInput.text.toString().trim()
        
        if (ip.isNotEmpty() && !NetworkUtils.isValidIpAddress(ip)) {
            showConfigError("IP address format is incorrect. Please enter a valid IPv4 address (e.g., 192.168.1.100)")
        } else {
            hideConfigError()
        }
    }
    
    /**
     * Validate LAN port number input
     */
    private fun validateLanPortInput() {
        val portStr = lanPortInput.text.toString().trim()
        
        if (portStr.isNotEmpty()) {
            try {
                val port = portStr.toInt()
                if (!NetworkUtils.isPortValid(port)) {
                    showConfigError("Port number must be between 1-65535. Recommended range: 8443-8453")
                } else {
                    hideConfigError()
                }
            } catch (e: NumberFormatException) {
                showConfigError("Port number format is incorrect. Please enter a valid number")
            }
        } else {
            hideConfigError()
        }
    }

    /**
     * Show corresponding configuration area
     */
    private fun showConfigArea(mode: ConnectionPreferences.ConnectionMode) {
        // Hide all configuration areas
        layoutAppToAppConfig.visibility = View.GONE
        layoutCableConfig.visibility = View.GONE
        layoutLanConfig.visibility = View.GONE
        layoutCloudConfig.visibility = View.GONE
        
        // Show corresponding configuration area
        when (mode) {
            ConnectionPreferences.ConnectionMode.APP_TO_APP -> {
                layoutAppToAppConfig.visibility = View.VISIBLE
            }
            ConnectionPreferences.ConnectionMode.CABLE -> {
                layoutCableConfig.visibility = View.VISIBLE
            }
            ConnectionPreferences.ConnectionMode.LAN -> {
                layoutLanConfig.visibility = View.VISIBLE
            }
        }
        
        Log.d(TAG, "Show configuration area: $mode")
    }
    
    /**
     * Handle connection confirmation with comprehensive validation and reconnection
     * 
     * This method implements the complete connection configuration workflow:
     * 
     * 1. Configuration Validation:
     *    - Validates mode-specific parameters (IP/port for LAN, protocol for Cable)
     *    - Performs network connectivity checks for LAN mode
     *    - Provides user-friendly error messages for configuration issues
     * 
     * 2. Configuration Persistence:
     *    - Saves validated configuration to preferences for future use
     *    - Ensures configuration survives app restarts
     *    - Maintains separate settings for each connection mode
     * 
     * 3. Connection Establishment:
     *    - Disconnects existing connections cleanly
     *    - Reinitializes SDK with new configuration
     *    - Provides real-time feedback during connection process
     * 
     * The method ensures that configuration changes are applied immediately
     * and provides comprehensive error handling for various failure scenarios.
     */
    private fun handleConfirm() {
        Log.d(TAG, "User clicks confirm - Selected mode: $selectedMode")
        
        // Step 1: Validate configuration before attempting to save or connect
        val validationResult = validateConfig()
        if (!validationResult.isValid) {
            showConfigError(validationResult.errorMessage)
            return
        }
        
        // Step 2: Save validated configuration to preferences
        saveConfig()
        
        // Step 3: Attempt reconnection with new configuration
        reconnectWithNewMode()
    }
    
    /**
     * Validate configuration with mode-specific validation logic
     * 
     * Each connection mode has different validation requirements:
     * 
     * App-to-App Mode:
     * - No additional configuration needed
     * - Always valid if selected
     * 
     * LAN Mode:
     * - Requires network connectivity
     * - Validates IP address format (IPv4)
     * - Validates port number range (1-65535)
     * - Provides network topology warnings when appropriate
     * 
     * Cable Mode:
     * - No additional validation needed (protocol selection is always valid)
     * - Hardware compatibility is handled by SDK
     * 
     * @return ValidationResult indicating success/failure with descriptive error message
     */
    private fun validateConfig(): ValidationResult {
        when (selectedMode) {
            ConnectionPreferences.ConnectionMode.APP_TO_APP -> {
                // App-to-App mode requires no additional configuration
                // The Tapro app handles all communication details
                return ValidationResult(true, "")
            }
            
            ConnectionPreferences.ConnectionMode.LAN -> {
                // LAN mode requires comprehensive network validation
                
                // First, check if device has network connectivity
                if (!NetworkUtils.isNetworkConnected(this)) {
                    return ValidationResult(false, "No network connection available. Please check your network settings.")
                }
                
                // Validate IP address configuration
                val ip = lanIpInput.text.toString().trim()
                val portStr = lanPortInput.text.toString().trim()
                
                if (TextUtils.isEmpty(ip)) {
                    return ValidationResult(false, "Please enter IP address")
                }
                
                if (!NetworkUtils.isValidIpAddress(ip)) {
                    return ValidationResult(false, "IP address format is incorrect. Please enter a valid IPv4 address (e.g., 192.168.1.100)")
                }
                
                // Validate port number configuration
                if (TextUtils.isEmpty(portStr)) {
                    return ValidationResult(false, "Please enter port number")
                }
                
                val port = try {
                    portStr.toInt()
                } catch (e: NumberFormatException) {
                    return ValidationResult(false, "Port number format is incorrect. Please enter a valid number")
                }
                
                if (!NetworkUtils.isPortValid(port)) {
                    return ValidationResult(false, "Port number must be between 1-65535. Recommended range: 8443-8453")
                }
                
                // Perform network topology check (warning, not blocking)
                // This helps users identify potential connectivity issues early
                if (!NetworkUtils.isInSameSubnet(this, ip)) {
                    val networkType = NetworkUtils.getNetworkType(this)
                    val localIp = NetworkUtils.getLocalIpAddress(this)
                    Log.w(TAG, "Target IP $ip may not be in same subnet as local IP $localIp (Network: $networkType)")
                }
                
                return ValidationResult(true, "")
            }
            
            ConnectionPreferences.ConnectionMode.CABLE -> {
                // Cable mode requires no additional configuration validation
                // Protocol selection is always valid, hardware compatibility is handled by SDK
                return ValidationResult(true, "")
            }
        }
    }
    

    /**
     * Save configuration
     */
    private fun saveConfig() {
        // Save connection mode
        ConnectionPreferences.saveConnectionMode(this, selectedMode)
        
        // Save mode-specific configuration
        when (selectedMode) {
            ConnectionPreferences.ConnectionMode.LAN -> {
                val ip = lanIpInput.text.toString().trim()
                val port = lanPortInput.text.toString().trim().toInt()
                ConnectionPreferences.saveLanConfig(this, ip, port)
                Log.d(TAG, "Save LAN configuration - IP: $ip, Port: $port")
            }
            
            ConnectionPreferences.ConnectionMode.CABLE -> {
                // Save selected cable protocol
                val selectedProtocolIndex = spinnerCableProtocol.selectedItemPosition
                val selectedProtocol = ConnectionPreferences.CableProtocol.values()[selectedProtocolIndex]
                ConnectionPreferences.saveCableProtocol(this, selectedProtocol)
                Log.d(TAG, "Save Cable configuration - Protocol: $selectedProtocol")
            }
            
            ConnectionPreferences.ConnectionMode.APP_TO_APP -> {
                // No additional configuration needed for App-to-App mode
                Log.d(TAG, "App-to-App mode - no additional configuration to save")
            }
        }
        
        Log.d(TAG, "Configuration saved successfully - Mode: $selectedMode")
    }
    
    /**
     * Reconnect with new mode
     */
    private fun reconnectWithNewMode() {
        Log.d(TAG, "Start reconnecting with mode: $selectedMode")
        
        // Show connecting status with detailed progress
        updateConnectionProgress("Initializing SDK...")
        confirmButton.isEnabled = false

        // Re-initialize and connect
        reinitializeSDKAndConnect()
    }
    
    /**
     * Update connection progress display
     */
    private fun updateConnectionProgress(message: String) {
        confirmButton.text = message
        Log.d(TAG, "Connection progress: $message")
    }
    
    /**
     * Re-initialize SDK and connect for mode switching
     */
    private fun reinitializeSDKAndConnect() {
        Log.d(TAG, "Starting connection with mode: $selectedMode")

        when (selectedMode) {
            ConnectionPreferences.ConnectionMode.LAN -> {
                handleLanModeConnection()
                return
            }
            ConnectionPreferences.ConnectionMode.CABLE -> {
                updateConnectionProgress("Connecting via Cable...")
            }
            ConnectionPreferences.ConnectionMode.APP_TO_APP -> {
                updateConnectionProgress("Connecting to Tapro App...")
            }
        }
        
        // For non-LAN modes, start connection immediately
        startSDKConnectionWithConfig()
    }

    /**
     * Handle LAN mode connection with network pre-check
     */
    private fun handleLanModeConnection() {
        val lanConfig = ConnectionPreferences.getLanConfig(this)
        val ip = lanConfig.first ?: "unknown"
        val port = lanConfig.second
        updateConnectionProgress("Testing connectivity to $ip:$port...")
        
        // Pre-check network connectivity
        lifecycleScope.launch {
            val ipAddress = lanConfig.first ?: return@launch
            val portNumber = lanConfig.second
            
            performNetworkPreCheck(ipAddress, portNumber)
            
            // Continue with SDK connection regardless of pre-check result
            runOnUiThread {
                startSDKConnectionWithConfig()
            }
        }
    }

    /**
     * Perform network connectivity pre-check for LAN mode
     */
    private suspend fun performNetworkPreCheck(ipAddress: String, portNumber: Int) {
        val isReachable = NetworkUtils.testConnection(ipAddress, portNumber)
        
        runOnUiThread {
            if (!isReachable) {
                Log.w(TAG, "Pre-check failed: Cannot reach $ipAddress:$portNumber")
                updateConnectionProgress("Host unreachable, trying SDK connection...")
            } else {
                Log.d(TAG, "Pre-check successful: $ipAddress:$portNumber is reachable")
                updateConnectionProgress("Host reachable, establishing connection...")
            }
        }
    }
    
    /**
     * Start SDK connection process with ConnectionConfig
     */
    private fun startSDKConnectionWithConfig() {
        Log.d(TAG, "Starting SDK connection for mode: $selectedMode")
        
        // Create ConnectionConfig with ConnectionMode set
        val connectionConfig = createConnectionConfigWithMode()
        
        Log.d(TAG, "Connecting with ConnectionConfig: $connectionConfig")
        
        // Connect to payment terminal with new mode
        paymentService.connect(connectionConfig, object : ConnectionListener {
            override fun onConnected(deviceId: String, taproVersion: String) {
                Log.d(TAG, "Connection successful - DeviceId: $deviceId, Version: $taproVersion")
                runOnUiThread {
                    showConnectionResult(true, "Connected to $deviceId (v$taproVersion)")
                }
            }
            
            override fun onDisconnected(reason: String) {
                Log.d(TAG, "Connection disconnected - Reason: $reason")
                runOnUiThread {
                    showConnectionResult(false, "Connection disconnected: $reason")
                }
            }
            
            override fun onError(code: String, message: String) {
                Log.e(TAG, "Connection failed - Code: $code, Message: $message")
                runOnUiThread {
                    val errorMsg = mapConnectionError(code, message)
                    showConnectionResult(false, errorMsg)
                }
            }
        })
    }
    
    /**
     * Create ConnectionConfig with ConnectionMode set based on selected mode
     */
    private fun createConnectionConfigWithMode(): com.sunmi.tapro.taplink.sdk.config.ConnectionConfig {
        val sdkConnectionMode = mapToSDKConnectionMode(selectedMode)
        val connectionConfig = com.sunmi.tapro.taplink.sdk.config.ConnectionConfig()
            .setConnectionMode(sdkConnectionMode)
        
        // Add mode-specific configuration
        configureConnectionMode(connectionConfig)
        
        return connectionConfig
    }

    /**
     * Map internal connection mode to SDK connection mode
     */
    private fun mapToSDKConnectionMode(mode: ConnectionPreferences.ConnectionMode): com.sunmi.tapro.taplink.sdk.enums.ConnectionMode {
        return when (mode) {
            ConnectionPreferences.ConnectionMode.APP_TO_APP -> com.sunmi.tapro.taplink.sdk.enums.ConnectionMode.APP_TO_APP
            ConnectionPreferences.ConnectionMode.CABLE -> com.sunmi.tapro.taplink.sdk.enums.ConnectionMode.CABLE
            ConnectionPreferences.ConnectionMode.LAN -> com.sunmi.tapro.taplink.sdk.enums.ConnectionMode.LAN
        }
    }

    /**
     * Configure connection mode specific settings
     */
    private fun configureConnectionMode(connectionConfig: com.sunmi.tapro.taplink.sdk.config.ConnectionConfig) {
        when (selectedMode) {
            ConnectionPreferences.ConnectionMode.LAN -> {
                configureLanMode(connectionConfig)
            }
            
            ConnectionPreferences.ConnectionMode.CABLE -> {
                configureCableMode(connectionConfig)
            }
            
            ConnectionPreferences.ConnectionMode.APP_TO_APP -> {
                configureAppToAppMode()
            }
        }
    }

    /**
     * Configure LAN mode specific settings
     */
    private fun configureLanMode(connectionConfig: com.sunmi.tapro.taplink.sdk.config.ConnectionConfig) {
        val lanConfig = ConnectionPreferences.getLanConfig(this)
        val ip = lanConfig.first
        val port = lanConfig.second
        
        if (ip != null && ip.isNotEmpty()) {
            Log.d(TAG, "LAN config - IP: $ip, Port: $port")
            connectionConfig.setHost(ip).setPort(port)
        } else {
            Log.d(TAG, "No LAN IP configured, using auto-connect")
        }
    }

    /**
     * Configure Cable mode specific settings
     */
    private fun configureCableMode(connectionConfig: com.sunmi.tapro.taplink.sdk.config.ConnectionConfig) {
        val protocol = ConnectionPreferences.getCableProtocol(this)
        Log.d(TAG, "Cable config - Protocol: $protocol")
        
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

    /**
     * Configure App-to-App mode (no additional configuration needed)
     */
    private fun configureAppToAppMode() {
        Log.d(TAG, "App-to-App mode - no additional configuration needed")
    }
    
    /**
     * Map connection error to user-friendly message
     */
    private fun mapConnectionError(code: String, message: String): String {
        // Provide user-friendly error messages based on common connection issues
        return when {
            message.contains("ETIMEDOUT") || message.contains("Connection timed out") -> {
                when (selectedMode) {
                    ConnectionPreferences.ConnectionMode.LAN -> {
                        val lanConfig = ConnectionPreferences.getLanConfig(this)
                        val ip = lanConfig.first ?: "unknown"
                        val port = lanConfig.second
                        "Unable to connect to $ip:$port\n\n" +
                        "Possible solutions:\n" +
                        "• Check if the target device is powered on\n" +
                        "• Verify the IP address and port are correct\n" +
                        "• Ensure both devices are on the same network\n" +
                        "• Check firewall settings\n\n" +
                        "Error Code: $code"
                    }
                    else -> "Connection timeout. Please check network connectivity.\n\nError Code: $code"
                }
            }
            message.contains("failed to connect") -> {
                "Connection failed. Please check network settings and try again.\n\nError Code: $code"
            }
            message.contains("UnknownHostException") -> {
                "Cannot resolve host address. Please check IP address.\n\nError Code: $code"
            }
            message.contains("ConnectException") -> {
                "Connection refused. Please check if the service is running.\n\nError Code: $code"
            }
            message.isNotEmpty() -> {
                "$message\n\nError Code: $code"
            }
            else -> {
                "Connection failed. Please check your settings and try again.\n\nError Code: $code"
            }
        }
    }
    
    /**
     * Show connection result
     */
    private fun showConnectionResult(success: Boolean, message: String) {
        if (success) {
            // Connection successful, return result
            Log.d(TAG, "Connection configuration completed - $message")
            
            val resultIntent = Intent()
            resultIntent.putExtra("connection_mode", selectedMode.name)
            resultIntent.putExtra("connection_message", message)
            setResult(RESULT_CONNECTION_CHANGED, resultIntent)
            finish()
        } else {
            // Connection failed, show simple error dialog
            Log.e(TAG, "Connection failed - $message")
            
            showSimpleConnectionError(message)
            
            confirmButton.text = getString(R.string.btn_confirm)
            confirmButton.isEnabled = true
        }
    }
    
    /**
     * Show simple connection error dialog
     */
    private fun showSimpleConnectionError(message: String) {
        // Dismiss any existing dialog first
        currentAlertDialog?.dismiss()
        
        currentAlertDialog = android.app.AlertDialog.Builder(this)
            .setTitle("Connection Failed")
            .setMessage(message)
            .setPositiveButton("OK") { dialog, _ ->
                dialog.dismiss()
                currentAlertDialog = null
            }
            .setNeutralButton("Retry") { dialog, _ -> 
                dialog.dismiss()
                currentAlertDialog = null
                // Retry connection
                handleConfirm()
            }
            .setOnDismissListener {
                currentAlertDialog = null
            }
            .show()
    }
    
    /**
     * Show configuration error with simple message
     */
    private fun showConfigError(message: String) {
        configErrorText.text = message
        cardConfigError.visibility = View.VISIBLE
        
        Log.w(TAG, "Configuration error displayed - Mode: $selectedMode, Message: $message")
    }
    
    /**
     * Hide configuration error
     */
    private fun hideConfigError() {
        cardConfigError.visibility = View.GONE
    }
    
    /**
     * Handle exit app button click
     */
    private fun handleExitApp() {
        Log.d(TAG, "User clicks exit app")
        
        // Dismiss any existing dialog first
        currentAlertDialog?.dismiss()
        
        // Show confirmation dialog
        currentAlertDialog = android.app.AlertDialog.Builder(this)
            .setTitle("Exit Application")
            .setMessage("Are you sure you want to exit the application?")
            .setPositiveButton("Exit") { dialog, _ ->
                Log.d(TAG, "User confirms exit")
                dialog.dismiss()
                currentAlertDialog = null
                
                // Disconnect payment service
                try {
                    paymentService.disconnect()
                } catch (e: Exception) {
                    Log.e(TAG, "Error disconnecting payment service", e)
                }
                
                // Finish all activities and exit app
                finishAffinity()
                
                // Force exit the process
//                android.os.Process.killProcess(android.os.Process.myPid())
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                Log.d(TAG, "User cancels exit")
                dialog.dismiss()
                currentAlertDialog = null
            }
            .setOnDismissListener {
                currentAlertDialog = null
            }
            .setCancelable(true)
            .show()
    }
    
    /**
     * Configuration validation result
     */
    private data class ValidationResult(
        val isValid: Boolean,
        val errorMessage: String
    )
}