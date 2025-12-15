package com.sunmi.tapro.taplink.demo.activity

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import com.sunmi.tapro.taplink.demo.R
import com.sunmi.tapro.taplink.demo.service.AppToAppPaymentService
import com.sunmi.tapro.taplink.demo.service.ConnectionListener
import com.sunmi.tapro.taplink.demo.util.ConnectionPreferences

import java.util.regex.Pattern

/**
 * Connection Mode Selection Activity
 * 
 * Functions:
 * 1. Select connection mode (App-to-App, Cable, LAN, Cloud)
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
    private val paymentService = AppToAppPaymentService.getInstance()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_connection)
        initViews()
        loadCurrentConfig()
        setupListeners()
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
            ConnectionPreferences.ConnectionMode.CLOUD -> {
                rbCloud.isChecked = true
                showConfigArea(ConnectionPreferences.ConnectionMode.CLOUD)
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
                R.id.rb_cloud -> {
                    selectedMode = ConnectionPreferences.ConnectionMode.CLOUD
                    showConfigArea(ConnectionPreferences.ConnectionMode.CLOUD)
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
        
        // LAN configuration input focus change listener (hide error prompt)
        etLanIp.setOnFocusChangeListener { _, _ -> hideConfigError() }
        etLanPort.setOnFocusChangeListener { _, _ -> hideConfigError() }
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
            ConnectionPreferences.ConnectionMode.CLOUD -> {
                layoutCloudConfig.visibility = View.VISIBLE
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
        
        // Check if it's a mode under development
        if (selectedMode != ConnectionPreferences.ConnectionMode.APP_TO_APP) {
            showConfigError("This mode is under development, please stay tuned")
            return
        }
        
        // Save configuration
        saveConfig()
        
        // Reconnect
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
                
                if (!isValidIpAddress(ip)) {
                    return ValidationResult(false, "IP address format is incorrect")
                }
                
                if (TextUtils.isEmpty(portStr)) {
                    return ValidationResult(false, "Please enter port number")
                }
                
                val port = try {
                    portStr.toInt()
                } catch (e: NumberFormatException) {
                    return ValidationResult(false, "Port number format is incorrect")
                }
                
                if (port < 1 || port > 65535) {
                    return ValidationResult(false, "Port number must be between 1-65535")
                }
                
                return ValidationResult(true, "")
            }
            
            ConnectionPreferences.ConnectionMode.CABLE,
            ConnectionPreferences.ConnectionMode.CLOUD -> {
                // These modes are not yet implemented, but configuration validation passes
                return ValidationResult(true, "")
            }
        }
    }
    
    /**
     * Validate IP address format
     */
    private fun isValidIpAddress(ip: String): Boolean {
        val pattern = Pattern.compile(
            "^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$"
        )
        return pattern.matcher(ip).matches()
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
     * Reconnect with new mode
     */
    private fun reconnectWithNewMode() {
        Log.d(TAG, "Start reconnecting - Mode: $selectedMode")
        
        // Show connecting status
        btnConfirm.text = "Connecting..."
        btnConfirm.isEnabled = false
        btnCancel.isEnabled = false
        
        // Disconnect current connection
        paymentService.disconnect()
        
        // Wait a moment before reconnecting
        btnConfirm.postDelayed({
            initializeAndConnect()
        }, 500)
    }
    
    /**
     * Initialize and connect
     */
    private fun initializeAndConnect() {
        // Get configuration
        val appId = getString(R.string.taplink_app_id)
        val merchantId = getString(R.string.taplink_merchant_id)
        val secretKey = getString(R.string.taplink_secret_key)
        
        // Initialize SDK
        val initSuccess = paymentService.initialize(this, appId, merchantId, secretKey)
        if (!initSuccess) {
            Log.e(TAG, "SDK initialization failed")
            showConnectionResult(false, "SDK initialization failed")
            return
        }
        
        // Connect to payment terminal
        paymentService.connect(object : ConnectionListener {
            override fun onConnected(deviceId: String, taproVersion: String) {
                Log.d(TAG, "Reconnection successful - DeviceId: $deviceId, Version: $taproVersion")
                runOnUiThread {
                    showConnectionResult(true, "Connected (v$taproVersion)")
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
                    val errorMsg = when (code) {
                        "C22" -> "Tapro not installed"
                        "S03" -> "Signature verification failed, please check configuration"
                        else -> "Connection failed: $message"
                    }
                    showConnectionResult(false, errorMsg)
                }
            }
        })
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
     * Show configuration error
     */
    private fun showConfigError(message: String) {
        tvConfigError.text = message
        cardConfigError.visibility = View.VISIBLE
        
        Log.w(TAG, "Configuration error: $message")
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
                android.os.Process.killProcess(android.os.Process.myPid())
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