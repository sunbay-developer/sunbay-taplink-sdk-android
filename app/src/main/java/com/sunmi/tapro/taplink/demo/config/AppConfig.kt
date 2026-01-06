package com.sunmi.tapro.taplink.demo.config

import android.content.Context
import com.sunmi.tapro.taplink.demo.R
import java.math.BigDecimal

/**
 * Application configuration management
 * 
 * Provides centralized configuration management for different environments
 * and application settings
 */
object AppConfig {
    
    /**
     * Environment types
     */
    enum class Environment {
        DEVELOPMENT,
        STAGING,
        PRODUCTION
    }
    
    /**
     * Current environment - can be overridden by build configuration
     */
    var currentEnvironment: Environment = Environment.DEVELOPMENT
        private set
    
    /**
     * SDK Configuration
     */
    data class SdkConfig(
        val appId: String,
        val merchantId: String,
        val secretKey: String,
        val logEnabled: Boolean = true,
        val debugMode: Boolean = false
    )
    
    /**
     * Network Configuration
     */
    data class NetworkConfig(
        val connectionTimeoutMs: Long,
        val transactionTimeoutMs: Long,
        val networkTestTimeoutMs: Long,
        val maxRetryCount: Int,
        val retryDelayMs: Long
    )
    
    /**
     * UI Configuration
     */
    data class UiConfig(
        val paymentProgressHideDelayMs: Long,
        val errorDialogShowDelayMs: Long,
        val clickIntervalProtectionMs: Long,
        val progressDialogTimeoutMs: Long,
        val inputValidationDelayMs: Long
    )
    
    /**
     * Transaction Configuration
     */
    data class TransactionConfig(
        val defaultCurrency: String,
        val minTransactionAmount: BigDecimal,
        val maxTransactionAmount: BigDecimal,
        val amountDecimalPlaces: Int,
        val amountFormatPattern: String,
        val multipleSalePaymentCount: Int
    )
    
    /**
     * Initialize configuration with context
     */
    fun initialize(context: Context, environment: Environment = Environment.DEVELOPMENT) {
        currentEnvironment = environment
    }
    
    /**
     * Get SDK configuration for current environment
     */
    fun getSdkConfig(context: Context): SdkConfig {
        return when (currentEnvironment) {
            Environment.DEVELOPMENT -> SdkConfig(
                appId = context.getString(R.string.taplink_app_id),
                merchantId = context.getString(R.string.taplink_merchant_id),
                secretKey = context.getString(R.string.taplink_secret_key),
                logEnabled = true,
                debugMode = true
            )
            Environment.STAGING -> SdkConfig(
                appId = context.getString(R.string.taplink_app_id),
                merchantId = context.getString(R.string.taplink_merchant_id),
                secretKey = context.getString(R.string.taplink_secret_key),
                logEnabled = true,
                debugMode = false
            )
            Environment.PRODUCTION -> SdkConfig(
                appId = context.getString(R.string.taplink_app_id),
                merchantId = context.getString(R.string.taplink_merchant_id),
                secretKey = context.getString(R.string.taplink_secret_key),
                logEnabled = false,
                debugMode = false
            )
        }
    }
    
    /**
     * Get network configuration for current environment
     */
    fun getNetworkConfig(): NetworkConfig {
        return when (currentEnvironment) {
            Environment.DEVELOPMENT -> NetworkConfig(
                connectionTimeoutMs = 30000L,
                transactionTimeoutMs = 60000L,
                networkTestTimeoutMs = 5000L,
                maxRetryCount = 5,
                retryDelayMs = 1000L
            )
            Environment.STAGING -> NetworkConfig(
                connectionTimeoutMs = 30000L,
                transactionTimeoutMs = 60000L,
                networkTestTimeoutMs = 3000L,
                maxRetryCount = 3,
                retryDelayMs = 2000L
            )
            Environment.PRODUCTION -> NetworkConfig(
                connectionTimeoutMs = 30000L,
                transactionTimeoutMs = 60000L,
                networkTestTimeoutMs = 3000L,
                maxRetryCount = 3,
                retryDelayMs = 2000L
            )
        }
    }
    
    /**
     * Get UI configuration for current environment
     */
    fun getUiConfig(): UiConfig {
        return when (currentEnvironment) {
            Environment.DEVELOPMENT -> UiConfig(
                paymentProgressHideDelayMs = 2000L,
                errorDialogShowDelayMs = 200L,
                clickIntervalProtectionMs = 300L,
                progressDialogTimeoutMs = 300000L,
                inputValidationDelayMs = 300L
            )
            Environment.STAGING, Environment.PRODUCTION -> UiConfig(
                paymentProgressHideDelayMs = 1500L,
                errorDialogShowDelayMs = 100L,
                clickIntervalProtectionMs = 500L,
                progressDialogTimeoutMs = 360000L,
                inputValidationDelayMs = 500L
            )
        }
    }
    
    /**
     * Get transaction configuration
     */
    fun getTransactionConfig(): TransactionConfig {
        return TransactionConfig(
            defaultCurrency = "USD",
            minTransactionAmount = BigDecimal("0.01"),
            maxTransactionAmount = BigDecimal("99999.99"),
            amountDecimalPlaces = 2,
            amountFormatPattern = "$#,##0.00",
            multipleSalePaymentCount = 3
        )
    }
    
    /**
     * Get current environment name
     */
    fun getCurrentEnvironmentName(): String {
        return currentEnvironment.name
    }
    
    /**
     * Check if debug mode is enabled
     */
    fun isDebugMode(): Boolean {
        return currentEnvironment == Environment.DEVELOPMENT
    }
    
    /**
     * Check if logging is enabled
     */
    fun isLoggingEnabled(): Boolean {
        return currentEnvironment != Environment.PRODUCTION
    }
    
    /**
     * Validate configuration
     */
    fun validateConfiguration(context: Context): ConfigValidationResult {
        val errors = mutableListOf<String>()
        
        try {
            val sdkConfig = getSdkConfig(context)
            
            if (sdkConfig.appId.isBlank()) {
                errors.add("SDK App ID is not configured")
            }
            
            if (sdkConfig.merchantId.isBlank()) {
                errors.add("SDK Merchant ID is not configured")
            }
            
            if (sdkConfig.secretKey.isBlank()) {
                errors.add("SDK Secret Key is not configured")
            }
            
            val networkConfig = getNetworkConfig()
            if (networkConfig.connectionTimeoutMs <= 0) {
                errors.add("Invalid connection timeout configuration")
            }
            
            if (networkConfig.transactionTimeoutMs <= 0) {
                errors.add("Invalid transaction timeout configuration")
            }
            
            val transactionConfig = getTransactionConfig()
            if (transactionConfig.minTransactionAmount >= transactionConfig.maxTransactionAmount) {
                errors.add("Invalid transaction amount range configuration")
            }
            
        } catch (e: Exception) {
            errors.add("Configuration validation failed: ${e.message}")
        }
        
        return ConfigValidationResult(
            isValid = errors.isEmpty(),
            errors = errors
        )
    }
    
    /**
     * Configuration validation result
     */
    data class ConfigValidationResult(
        val isValid: Boolean,
        val errors: List<String>
    )
}