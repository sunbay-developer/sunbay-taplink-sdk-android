package com.sunmi.tapro.taplink.demo.activity

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.text.TextUtils
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import com.sunmi.tapro.taplink.demo.R
import com.sunmi.tapro.taplink.demo.service.TaplinkPaymentService
import com.sunmi.tapro.taplink.demo.service.ConnectionListener
import com.sunmi.tapro.taplink.demo.util.ConnectionPreferences
import com.sunmi.tapro.taplink.demo.util.NetworkUtils

/**
 * Connection Mode Selection Activity
 * 
 * Functions:
 * 1. Select connection mode (App-to-App, Cable, LAN)
 * 2. Configure connection parameters (LAN requires IP and port)
 * 3. Validate configuration integrity
 * 4. Save configuration and reconnect
 */
class ConnectionActivity : AppCompatActivity() {
    
    companion object {
        private const val TAG = "ConnectionActivity"
        const val RESULT_CONNECTION_CHANGED = 100
    }
    
    // UI components
    private lateinit var rgConnectionMode: RadioGroup
    private lateinit var rbAppToApp: RadioButton
    private lateinit var rbCable: RadioButton
    private lateinit var rbLan: RadioButton
    private lateinit var rbCloud: RadioButton
    
    // Configuration areas
    private lateinit var layoutAppToAppConfig: CardView
    private lateinit var layoutCableConfig: CardView
    private lateinit var layoutLanConfig: CardView
    private lateinit var layoutCloudConfig: CardView
    
    // LAN configuration inputs
    private lateinit var etLanIp: EditText
    private lateinit var etLanPort: EditText
    private lateinit var switchTls: Switch
    
    // Error prompts
    private lateinit var cardConfigError: CardView
    private lateinit var tvConfigError: TextView
    
    // Buttons
    private lateinit var btnCancel: Button
    private lateinit var btnConfirm: Button
    private lateinit var btnExitApp: Button
    
    // Currently selected connection mode
    private var selectedMode: ConnectionPreferences.ConnectionMode = ConnectionPreferences.ConnectionMode.APP_TO_APP
    
    // Payment service
    private val paymentService = TaplinkPaymentService.getInstance()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_connection)
        initViews()
        loadCurrentConfig()
        setupListeners()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // Clean up validation runnables to prevent memory leaks
        etLanIp.removeCallbacks(ipValidationRunnable)
        etLanPort.removeCallbacks(portValidationRunnable)
    }
    

    
    /**
     * Initialize view components
     */
    private fun initViews() {
        // Connection mode selection
        rgConnectionMode = findViewById(R.id.rg_connection_mode)
        rbAppToApp = findViewById(R.id.rb_app_to_app)
        rbCable = findViewById(R.id.rb_cable)
        rbLan = findViewById(R.id.rb_lan)
        rbCloud = findViewById(R.id.rb_cloud)
        
        // Configuration areas
        layoutAppToAppConfig = findViewById(R.id.layout_app_to_app_config)
        layoutCableConfig = findViewById(R.id.layout_cable_config)
        layoutLanConfig = findViewById(R.id.layout_lan_config)
        layoutCloudConfig = findViewById(R.id.layout_cloud_config)
        
        // LAN configuration inputs
        etLanIp = findViewById(R.id.et_lan_ip)
        etLanPort = findViewById(R.id.et_lan_port)
        switchTls = findViewById(R.id.switch_tls)
        
        // Error prompts
        cardConfigError = findViewById(R.id.card_config_error)
        tvConfigError = findViewById(R.id.tv_config_error)
        
        // Buttons
        btnCancel = findViewById(R.id.btn_cancel)
        btnConfirm = findViewById(R.id.btn_confirm)
        btnExitApp = findViewById(R.id.btn_exit_app)
    }
    
    /**
     * Load current configuration
     */
    private fun loadCurrentConfig() {
        // Load saved connection mode
        val currentMode = ConnectionPreferences.getConnectionMode(this)
        selectedMode = currentMode
        
        // Set corresponding RadioButton checked
        when (currentMode) {
            ConnectionPreferences.ConnectionMode.APP_TO_APP -> {
                rbAppToApp.isChecked = true
                showConfigArea(ConnectionPreferences.ConnectionMode.APP_TO_APP)
            }
            ConnectionPreferences.ConnectionMode.CABLE -> {
                rbCable.isChecked = true
                showConfigArea(ConnectionPreferences.ConnectionMode.CABLE)
            }
            ConnectionPreferences.ConnectionMode.LAN -> {
                rbLan.isChecked = true
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
        val (ip, port, tlsEnabled) = ConnectionPreferences.getLanConfig(this)
        
        ip?.let { etLanIp.setText(it) }
        etLanPort.setText(port.toString())
        switchTls.isChecked = tlsEnabled
        
        Log.d(TAG, "Load LAN configuration - IP: $ip, Port: $port, TLS: $tlsEnabled")
    }
    
    /**
     * Set up event listeners
     */
    private fun setupListeners() {
        // Connection mode selection listener
        rgConnectionMode.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.rb_app_to_app -> {
                    selectedMode = ConnectionPreferences.ConnectionMode.APP_TO_APP
                    showConfigArea(ConnectionPreferences.ConnectionMode.APP_TO_APP)
                }
                R.id.rb_cable -> {
                    selectedMode = ConnectionPreferences.ConnectionMode.CABLE
                    showConfigArea(ConnectionPreferences.ConnectionMode.CABLE)
                }
                R.id.rb_lan -> {
                    selectedMode = ConnectionPreferences.ConnectionMode.LAN
                    showConfigArea(ConnectionPreferences.ConnectionMode.LAN)
                }
            }
            
            // Hide error prompt
            hideConfigError()
        }
        
        // Cancel button click listener
        btnCancel.setOnClickListener {
            Log.d(TAG, "User cancels configuration")
            setResult(Activity.RESULT_CANCELED)
            finish()
        }
        
        // Confirm button click listener
        btnConfirm.setOnClickListener {
            handleConfirm()
        }
        
        // Exit app button click listener
        btnExitApp.setOnClickListener {
            handleExitApp()
        }
        
        // LAN configuration real-time validation listeners
        setupLanConfigValidation()
    }
    
    /**
     * Set up real-time validation for LAN configuration inputs
     */
    private fun setupLanConfigValidation() {
        // IP address real-time validation
        etLanIp.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                validateLanIpInput()
            } else {
                hideConfigError()
            }
        }
        
        // Port number real-time validation
        etLanPort.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                validateLanPortInput()
            } else {
                hideConfigError()
            }
        }
        
        // Add text change listeners for immediate feedback
        etLanIp.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                // Only validate if user has finished typing (after a short delay)
                etLanIp.removeCallbacks(ipValidationRunnable)
                etLanIp.postDelayed(ipValidationRunnable, 500)
            }
        })
        
        etLanPort.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                // Only validate if user has finished typing (after a short delay)
                etLanPort.removeCallbacks(portValidationRunnable)
                etLanPort.postDelayed(portValidationRunnable, 500)
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
        val ip = etLanIp.text.toString().trim()
        
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
        val portStr = etLanPort.text.toString().trim()
        
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
     * Handle confirm button click
     */
    private fun handleConfirm() {
        Log.d(TAG, "User clicks confirm - Selected mode: $selectedMode")
        
        // Validate configuration
        val validationResult = validateConfig()
        if (!validationResult.isValid) {
            showConfigError(validationResult.errorMessage)
            return
        }
        
        // Save configuration
        saveConfig()
        
        // Reconnect with new mode (includes SDK re-initialization)
        reconnectWithNewMode()
    }
    
    /**
     * Validate configuration
     */
    private fun validateConfig(): ValidationResult {
        when (selectedMode) {
            ConnectionPreferences.ConnectionMode.APP_TO_APP -> {
                // App-to-App mode requires no additional configuration
                return ValidationResult(true, "")
            }
            
            ConnectionPreferences.ConnectionMode.LAN -> {
                // Validate LAN configuration
                val ip = etLanIp.text.toString().trim()
                val portStr = etLanPort.text.toString().trim()
                
                if (TextUtils.isEmpty(ip)) {
                    return ValidationResult(false, "Please enter IP address")
                }
                
                if (!NetworkUtils.isValidIpAddress(ip)) {
                    return ValidationResult(false, "IP address format is incorrect. Please enter a valid IPv4 address (e.g., 192.168.1.100)")
                }
                
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
                
                return ValidationResult(true, "")
            }
            
            ConnectionPreferences.ConnectionMode.CABLE -> {
                // Cable mode requires no additional configuration (auto-detection)
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
        
        // Save LAN configuration (if in LAN mode)
        if (selectedMode == ConnectionPreferences.ConnectionMode.LAN) {
            val ip = etLanIp.text.toString().trim()
            val port = etLanPort.text.toString().trim().toInt()
            val tlsEnabled = switchTls.isChecked
            
            ConnectionPreferences.saveLanConfig(this, ip, port, tlsEnabled)
            
            Log.d(TAG, "Save LAN configuration - IP: $ip, Port: $port, TLS: $tlsEnabled")
        }
        
        Log.d(TAG, "Configuration saved successfully - Mode: $selectedMode")
    }
    
    /**
     * Reconnect with new mode (includes SDK re-initialization)
     */
    private fun reconnectWithNewMode() {
        Log.d(TAG, "Start reconnecting with mode switch - Mode: $selectedMode")
        
        // Show connecting status
        btnConfirm.text = "Connecting..."
        btnConfirm.isEnabled = false
        btnCancel.isEnabled = false
        
        // Disconnect current connection
        paymentService.disconnect()
        
        // Wait a moment before re-initializing SDK and connecting
        btnConfirm.postDelayed({
            reinitializeSDKAndConnect()
        }, 500)
    }
    
    /**
     * Re-initialize SDK and connect for mode switching
     * This method handles SDK re-initialization when switching between connection modes
     */
    private fun reinitializeSDKAndConnect() {
        Log.d(TAG, "=== SDK Re-initialization for Mode Switch ===")
        Log.d(TAG, "Target Mode: $selectedMode")
        
        // Re-initialize SDK for the new connection mode
        val reinitSuccess = paymentService.reinitializeForMode(this, selectedMode)
        if (!reinitSuccess) {
            Log.e(TAG, "SDK re-initialization failed for mode: $selectedMode")
            showConnectionResult(false, "SDK re-initialization failed")
            return
        }
        
        Log.d(TAG, "SDK re-initialized successfully for mode: $selectedMode")
        
        // Connect to payment terminal with new mode
        paymentService.connect(object : ConnectionListener {
            override fun onConnected(deviceId: String, taproVersion: String) {
                Log.d(TAG, "Mode switch connection successful - DeviceId: $deviceId, Version: $taproVersion")
                runOnUiThread {
                    showConnectionResult(true, "Connected (v$taproVersion)")
                }
            }
            
            override fun onDisconnected(reason: String) {
                Log.d(TAG, "Mode switch connection disconnected - Reason: $reason")
                runOnUiThread {
                    showConnectionResult(false, "Connection disconnected: $reason")
                }
            }
            
            override fun onError(code: String, message: String) {
                Log.e(TAG, "Mode switch connection failed - Code: $code, Message: $message")
                runOnUiThread {
                    val errorMsg = mapConnectionError(code, message)
                    showConnectionResult(false, errorMsg)
                }
            }
        })
    }
    
    /**
     * Use SDK provided error message directly without additional mapping
     */
    private fun mapConnectionError(code: String, message: String): String {
        // Return SDK provided error message as-is
        // SDK already provides user-friendly error messages and suggestions
        return if (message.isNotEmpty()) {
            message
        } else {
            "Connection failed (Code: $code)"
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
            // Connection failed, show error and restore button states
            Log.e(TAG, "Connection failed - $message")
            
            showConfigError(message)
            
            btnConfirm.text = getString(R.string.btn_confirm)
            btnConfirm.isEnabled = true
            btnCancel.isEnabled = true
        }
    }
    
    /**
     * Show configuration error with enhanced formatting and guidance
     */
    private fun showConfigError(message: String) {
        // Format error message with mode-specific guidance
        val enhancedMessage = buildString {
            append(message)
            
            // Add mode-specific troubleshooting tips
            when (selectedMode) {
                ConnectionPreferences.ConnectionMode.LAN -> {
                    append("\n\nTroubleshooting tips:")
                    append("\n• Ensure both devices are on the same network")
                    append("\n• Verify IP address and port number")
                    append("\n• Check if Tapro service is running")
                    append("\n• Try disabling TLS if connection fails")
                }
                ConnectionPreferences.ConnectionMode.CABLE -> {
                    append("\n\nTroubleshooting tips:")
                    append("\n• Check cable connection on both ends")
                    append("\n• Try a different USB/serial cable")
                    append("\n• Grant USB permissions if prompted")
                    append("\n• Ensure cable supports data transfer")
                }
                ConnectionPreferences.ConnectionMode.APP_TO_APP -> {
                    append("\n\nTroubleshooting tips:")
                    append("\n• Install Tapro app if not present")
                    append("\n• Start Tapro app and wait for initialization")
                    append("\n• Check app signature configuration")
                    append("\n• Restart both apps if needed")
                }
            }
        }
        
        tvConfigError.text = enhancedMessage
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
        
        // Show confirmation dialog
        android.app.AlertDialog.Builder(this)
            .setTitle("Exit Application")
            .setMessage("Are you sure you want to exit the application?")
            .setPositiveButton("Exit") { _, _ ->
                Log.d(TAG, "User confirms exit")
                
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