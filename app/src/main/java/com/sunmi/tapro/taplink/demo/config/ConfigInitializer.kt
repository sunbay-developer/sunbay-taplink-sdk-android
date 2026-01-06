package com.sunmi.tapro.taplink.demo.config

import android.content.Context
import android.util.Log

/**
 * Configuration Initializer
 * 
 * Handles initialization of the configuration management system
 * during application startup
 */
object ConfigInitializer {
    
    private const val TAG = "ConfigInitializer"
    
    /**
     * Initialize all configuration systems
     */
    fun initialize(context: Context) {
        Log.d(TAG, "Initializing configuration systems...")
        
        try {
            // Initialize environment config manager
            EnvironmentConfigManager.getInstance().initialize(context)
            
            // Determine environment from configuration
            val environment = determineEnvironment()
            
            // Initialize app config with determined environment
            AppConfig.initialize(context, environment)
            
            // Validate configuration
            val validationResult = AppConfig.validateConfiguration(context)
            if (!validationResult.isValid) {
                Log.w(TAG, "Configuration validation failed:")
                validationResult.errors.forEach { error ->
                    Log.w(TAG, "  - $error")
                }
            } else {
                Log.d(TAG, "Configuration validation passed")
            }
            
            Log.d(TAG, "Configuration systems initialized successfully")
            Log.d(TAG, "Current environment: ${AppConfig.getCurrentEnvironmentName()}")
            
            if (AppConfig.isDebugMode()) {
                logConfigurationSummary()
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize configuration systems", e)
            throw e
        }
    }
    
    /**
     * Determine environment from configuration sources
     */
    private fun determineEnvironment(): AppConfig.Environment {
        val envConfigManager = EnvironmentConfigManager.getInstance()
        val environmentName = envConfigManager.getString(
            EnvironmentConfigManager.Keys.ENVIRONMENT,
            AppConfig.Environment.DEVELOPMENT.name
        )
        
        return try {
            AppConfig.Environment.valueOf(environmentName.uppercase())
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Invalid environment name: $environmentName, defaulting to DEVELOPMENT")
            AppConfig.Environment.DEVELOPMENT
        }
    }
    
    /**
     * Log configuration summary for debugging
     */
    private fun logConfigurationSummary() {
        Log.d(TAG, "=== Configuration Summary ===")
        Log.d(TAG, "Environment: ${AppConfig.getCurrentEnvironmentName()}")
        Log.d(TAG, "Debug Mode: ${AppConfig.isDebugMode()}")
        Log.d(TAG, "Logging Enabled: ${AppConfig.isLoggingEnabled()}")
        
        val networkConfig = AppConfig.getNetworkConfig()
        Log.d(TAG, "Network - Connection Timeout: ${networkConfig.connectionTimeoutMs}ms")
        Log.d(TAG, "Network - Transaction Timeout: ${networkConfig.transactionTimeoutMs}ms")
        Log.d(TAG, "Network - Max Retry Count: ${networkConfig.maxRetryCount}")
        
        val uiConfig = AppConfig.getUiConfig()
        Log.d(TAG, "UI - Payment Progress Hide Delay: ${uiConfig.paymentProgressHideDelayMs}ms")
        Log.d(TAG, "UI - Click Interval Protection: ${uiConfig.clickIntervalProtectionMs}ms")
        
        val transactionConfig = AppConfig.getTransactionConfig()
        Log.d(TAG, "Transaction - Default Currency: ${transactionConfig.defaultCurrency}")
        Log.d(TAG, "Transaction - Amount Range: ${transactionConfig.minTransactionAmount} - ${transactionConfig.maxTransactionAmount}")
        
        Log.d(TAG, "=== End Configuration Summary ===")
    }
    
    /**
     * Get configuration status for debugging
     */
    fun getConfigurationStatus(context: Context): ConfigurationStatus {
        return try {
            val validationResult = AppConfig.validateConfiguration(context)
            val allConfigs = EnvironmentConfigManager.getInstance().getAllConfigurations()
            
            ConfigurationStatus(
                isInitialized = true,
                environment = AppConfig.getCurrentEnvironmentName(),
                isValid = validationResult.isValid,
                validationErrors = validationResult.errors,
                configurationCount = allConfigs.size,
                allConfigurations = if (AppConfig.isDebugMode()) allConfigs else emptyMap()
            )
        } catch (e: Exception) {
            ConfigurationStatus(
                isInitialized = false,
                environment = "UNKNOWN",
                isValid = false,
                validationErrors = listOf("Configuration not initialized: ${e.message}"),
                configurationCount = 0,
                allConfigurations = emptyMap()
            )
        }
    }
    
    /**
     * Configuration status data class
     */
    data class ConfigurationStatus(
        val isInitialized: Boolean,
        val environment: String,
        val isValid: Boolean,
        val validationErrors: List<String>,
        val configurationCount: Int,
        val allConfigurations: Map<String, String>
    )
}