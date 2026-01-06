package com.sunmi.tapro.taplink.demo.config

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import java.util.Properties

/**
 * Environment Configuration Manager
 * 
 * Manages configuration loading from multiple sources:
 * 1. Environment variables
 * 2. SharedPreferences (runtime overrides)
 * 3. Properties files
 * 4. Default values
 */
class EnvironmentConfigManager private constructor() {
    
    companion object {
        private const val TAG = "EnvironmentConfigManager"
        private const val PREFS_NAME = "taplink_config"
        
        @Volatile
        private var instance: EnvironmentConfigManager? = null
        
        fun getInstance(): EnvironmentConfigManager {
            return instance ?: synchronized(this) {
                instance ?: EnvironmentConfigManager().also { instance = it }
            }
        }
    }
    
    private var context: Context? = null
    private var preferences: SharedPreferences? = null
    private var properties: Properties? = null
    
    /**
     * Initialize the configuration manager
     */
    fun initialize(context: Context) {
        this.context = context
        this.preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        loadPropertiesFile(context)
        
        Log.d(TAG, "EnvironmentConfigManager initialized")
    }
    
    /**
     * Load properties file if exists
     */
    private fun loadPropertiesFile(context: Context) {
        try {
            val inputStream = context.assets.open("taplink.properties")
            properties = Properties().apply {
                load(inputStream)
            }
            inputStream.close()
            Log.d(TAG, "Loaded configuration from taplink.properties")
        } catch (e: Exception) {
            Log.d(TAG, "No taplink.properties file found, using default configuration")
            properties = Properties()
        }
    }
    
    /**
     * Get string configuration value with fallback chain
     */
    fun getString(key: String, defaultValue: String = ""): String {
        // 1. Check SharedPreferences (runtime overrides)
        preferences?.getString(key, null)?.let { return it }
        
        // 2. Check environment variables
        System.getenv(key)?.let { return it }
        
        // 3. Check properties file
        properties?.getProperty(key)?.let { return it }
        
        // 4. Return default value
        return defaultValue
    }
    
    /**
     * Get long configuration value with fallback chain
     */
    fun getLong(key: String, defaultValue: Long = 0L): Long {
        return try {
            getString(key).toLongOrNull() ?: defaultValue
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse long value for key: $key", e)
            defaultValue
        }
    }
    
    /**
     * Get int configuration value with fallback chain
     */
    fun getInt(key: String, defaultValue: Int = 0): Int {
        return try {
            getString(key).toIntOrNull() ?: defaultValue
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse int value for key: $key", e)
            defaultValue
        }
    }
    
    /**
     * Get boolean configuration value with fallback chain
     */
    fun getBoolean(key: String, defaultValue: Boolean = false): Boolean {
        return try {
            val value = getString(key).lowercase()
            when (value) {
                "true", "1", "yes", "on" -> true
                "false", "0", "no", "off" -> false
                "" -> defaultValue
                else -> defaultValue
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse boolean value for key: $key", e)
            defaultValue
        }
    }
    
    /**
     * Set runtime configuration override
     */
    fun setRuntimeOverride(key: String, value: String) {
        preferences?.edit()?.putString(key, value)?.apply()
        Log.d(TAG, "Set runtime override: $key = $value")
    }
    
    /**
     * Remove runtime configuration override
     */
    fun removeRuntimeOverride(key: String) {
        preferences?.edit()?.remove(key)?.apply()
        Log.d(TAG, "Removed runtime override: $key")
    }
    
    /**
     * Clear all runtime overrides
     */
    fun clearRuntimeOverrides() {
        preferences?.edit()?.clear()?.apply()
        Log.d(TAG, "Cleared all runtime overrides")
    }
    
    /**
     * Get all configuration keys and values for debugging
     */
    fun getAllConfigurations(): Map<String, String> {
        val configs = mutableMapOf<String, String>()
        
        // Add properties file configurations
        properties?.forEach { key, value ->
            configs[key.toString()] = value.toString()
        }
        
        // Add runtime overrides (these will override properties)
        preferences?.all?.forEach { (key, value) ->
            configs[key] = value.toString()
        }
        
        return configs
    }
    
    /**
     * Configuration keys for common settings
     */
    object Keys {
        // Environment
        const val ENVIRONMENT = "TAPLINK_ENVIRONMENT"
        
        // SDK Configuration
        const val SDK_APP_ID = "TAPLINK_SDK_APP_ID"
        const val SDK_MERCHANT_ID = "TAPLINK_SDK_MERCHANT_ID"
        const val SDK_SECRET_KEY = "TAPLINK_SDK_SECRET_KEY"
        const val SDK_LOG_ENABLED = "TAPLINK_SDK_LOG_ENABLED"
        const val SDK_DEBUG_MODE = "TAPLINK_SDK_DEBUG_MODE"
        
        // Network Configuration
        const val CONNECTION_TIMEOUT_MS = "TAPLINK_CONNECTION_TIMEOUT_MS"
        const val TRANSACTION_TIMEOUT_MS = "TAPLINK_TRANSACTION_TIMEOUT_MS"
        const val NETWORK_TEST_TIMEOUT_MS = "TAPLINK_NETWORK_TEST_TIMEOUT_MS"
        const val MAX_RETRY_COUNT = "TAPLINK_MAX_RETRY_COUNT"
        const val RETRY_DELAY_MS = "TAPLINK_RETRY_DELAY_MS"
        
        // UI Configuration
        const val PAYMENT_PROGRESS_HIDE_DELAY_MS = "TAPLINK_PAYMENT_PROGRESS_HIDE_DELAY_MS"
        const val ERROR_DIALOG_SHOW_DELAY_MS = "TAPLINK_ERROR_DIALOG_SHOW_DELAY_MS"
        const val CLICK_INTERVAL_PROTECTION_MS = "TAPLINK_CLICK_INTERVAL_PROTECTION_MS"
        const val PROGRESS_DIALOG_TIMEOUT_MS = "TAPLINK_PROGRESS_DIALOG_TIMEOUT_MS"
        
        // Transaction Configuration
        const val DEFAULT_CURRENCY = "TAPLINK_DEFAULT_CURRENCY"
        const val MIN_TRANSACTION_AMOUNT = "TAPLINK_MIN_TRANSACTION_AMOUNT"
        const val MAX_TRANSACTION_AMOUNT = "TAPLINK_MAX_TRANSACTION_AMOUNT"
        const val MULTIPLE_SALE_PAYMENT_COUNT = "TAPLINK_MULTIPLE_SALE_PAYMENT_COUNT"
        
        // Feature Flags
        const val ENABLE_ADVANCED_LOGGING = "TAPLINK_ENABLE_ADVANCED_LOGGING"
        const val ENABLE_CRASH_REPORTING = "TAPLINK_ENABLE_CRASH_REPORTING"
        const val ENABLE_ANALYTICS = "TAPLINK_ENABLE_ANALYTICS"
    }
}