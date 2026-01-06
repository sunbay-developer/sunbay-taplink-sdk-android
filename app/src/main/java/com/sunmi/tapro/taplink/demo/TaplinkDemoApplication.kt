package com.sunmi.tapro.taplink.demo

import android.app.Application
import android.util.Log
import com.sunmi.tapro.taplink.sdk.TaplinkSDK
import com.sunmi.tapro.taplink.sdk.callback.ConnectionListener
import com.sunmi.tapro.taplink.sdk.callback.PaymentCallback
import com.sunmi.tapro.taplink.sdk.config.ConnectionConfig
import com.sunmi.tapro.taplink.sdk.config.TaplinkConfig
import com.sunmi.tapro.taplink.sdk.enums.ConnectionMode
import com.sunmi.tapro.taplink.sdk.enums.LogLevel
import com.sunmi.tapro.taplink.sdk.enums.TransactionAction
import com.sunmi.tapro.taplink.sdk.error.ConnectionError
import com.sunmi.tapro.taplink.sdk.error.PaymentError
import com.sunmi.tapro.taplink.sdk.model.common.AmountInfo
import com.sunmi.tapro.taplink.sdk.model.request.PaymentRequest
import com.sunmi.tapro.taplink.sdk.model.response.PaymentResult
import java.math.BigDecimal

/**
 * Main application class for Taplink Demo
 * 
 * Handles global SDK initialization and provides a centralized entry point
 * for SDK configuration. The SDK is initialized once at application startup
 * to ensure consistent configuration across all activities.
 */
class TaplinkDemoApplication : Application() {

    companion object {
        private const val TAG = "TaplinkDemoApp"
    }

    override fun onCreate() {
        super.onCreate()

        Log.d(TAG, "=== Application Started ===")

        // Initialize SDK once at application level to ensure consistent configuration
        initializeTaplinkSDK()
    }

    /**
     * Initialize Taplink SDK with basic configuration
     * 
     * Performs one-time SDK initialization using credentials from resources.
     * Connection mode is intentionally not set here because it needs to be
     * configurable at runtime based on user preferences.
     * 
     * @throws Exception if SDK initialization fails due to missing configuration
     */
    private fun initializeTaplinkSDK() {
        try {
            Log.d(TAG, "=== Taplink SDK Initialization Started ===")

            // Read configuration from resource files to avoid hardcoding credentials
            val appId = getString(R.string.taplink_app_id)
            val merchantId = getString(R.string.taplink_merchant_id)
            val secretKey = getString(R.string.taplink_secret_key)

            // Log configuration parameters with sensitive data masked for security
            Log.d(TAG, "=== SDK Init Request Parameters ===")
            Log.d(TAG, "App ID: $appId")
            Log.d(TAG, "Merchant ID: $merchantId")
            Log.d(TAG, "Secret Key: ${secretKey.take(4)}****${secretKey.takeLast(4)}")

            // Create SDK configuration without ConnectionMode because connection mode
            // needs to be configurable at runtime based on user preferences
            val config = TaplinkConfig(
                appId = appId,
                merchantId = merchantId,
                secretKey = secretKey
            ).setLogEnabled(true).setLogLevel(LogLevel.DEBUG)

            // Initialize SDK with basic configuration
            Log.d(TAG, "=== Calling TaplinkSDK.init() ===")
            TaplinkSDK.init(this, config)

            Log.d(TAG, "=== Taplink SDK Initialization Response ===")
            Log.d(TAG, "Status: SUCCESS")
            Log.d(TAG, "SDK Version: ${TaplinkSDK.getVersion()}")

        } catch (e: Exception) {
            Log.e(TAG, "=== Taplink SDK Initialization Response ===")
            Log.e(TAG, "Status: FAILURE")
            Log.e(TAG, "Error: ${e.message}")
            Log.e(TAG, "Exception: ", e)
        }
    }

    /**
     * Test connection and execute sale transaction for development purposes
     * 
     * This method demonstrates the complete flow of connecting to Tapro and
     * executing a transaction. It's primarily used for testing and development
     * to verify SDK integration is working correctly.
     */
    private fun testConnectSale() {
        Log.d(TAG, "=== Starting Loading Connection ===")

        val taplinkApi = TaplinkSDK.getInstance()
        val connectionConfig = ConnectionConfig().setConnectionMode(ConnectionMode.APP_TO_APP)

        Log.d(TAG, "Attempting connection...")
        taplinkApi.connect(connectionConfig, object : ConnectionListener {
            override fun onConnected(deviceId: String, taproVersion: String) {
                Log.d(TAG, "=== Connection SUCCESS ===")
                Log.d(TAG, "Device ID: $deviceId")
                Log.d(TAG, "Tapro Version: $taproVersion")

                // Execute sale immediately after successful connection to test the complete flow
                Log.d(TAG, "Connection successful, executing repeated SALE transactions (Loading)")
                executeSale()
            }

            override fun onDisconnected(reason: String) {
                Log.d(TAG, "=== Connection DISCONNECTED ===")
                Log.d(TAG, "Disconnect Reason: $reason")
                Log.d(TAG, "loading failed - connection lost")
            }

            override fun onError(error: ConnectionError) {
                Log.e(TAG, "=== Connection FAILURE ===")
                Log.e(TAG, "Error Code: ${error.code}")
                Log.e(TAG, "Error Message: ${error.message}")
                Log.e(TAG, "loading failed - connection error")
            }
        })
    }

    /**
     * Execute a test SALE transaction
     * 
     * Creates and executes a zero-amount SALE transaction for testing purposes.
     * This demonstrates the proper way to construct payment requests and handle
     * callbacks. The zero amount is used to avoid actual charges during testing.
     */
    private fun executeSale() {
        Log.d(TAG, "=== Executing SALE Transaction ===")

        // Verify connection status before attempting transaction
        // This check is commented out because it may not be reliable in all scenarios
//        if (!TaplinkSDK.isConnected()) {
//            Log.e(TAG, "SDK not connected, cannot execute sale")
//            return
//        }

        // Generate unique identifiers for this transaction to ensure idempotency
        val referenceOrderId = "LAZY_ORDER_${System.currentTimeMillis()}"
        val transactionRequestId = "LAZY_REQ_${System.currentTimeMillis()}"
        val amount = BigDecimal("0") // Zero amount for testing to avoid actual charges
        val currency = "USD"

        Log.d(TAG, "Order ID: $referenceOrderId")
        Log.d(TAG, "Transaction Request ID: $transactionRequestId")
        Log.d(TAG, "Amount: $amount $currency")

        try {
            // Create AmountInfo with the required parameters for the SDK
            val amountInfo = AmountInfo(
                orderAmount = amount,
                pricingCurrency = currency
            )

            // Create PaymentRequest using builder pattern for better readability
            val paymentRequest = PaymentRequest(
                action = TransactionAction.SALE.value
            ).setReferenceOrderId(referenceOrderId)
                .setTransactionRequestId(transactionRequestId)
                .setAmount(amountInfo)
                .setDescription("Loading SALE Transaction")

            Log.d(TAG, "PaymentRequest created, executing...")

            // Execute the payment request with callback handlers
            TaplinkSDK.execute(paymentRequest, object : PaymentCallback {
                override fun onSuccess(result: PaymentResult) {
                    Log.d(TAG, "=== LOADING SALE SUCCESS ===")
                    Log.d(TAG, "Transaction ID: ${result.transactionId}")
                    Log.d(TAG, "Status: ${result.transactionStatus}")
                    Log.d(TAG, "Amount: ${result.amount?.orderAmount}")
                    Log.d(TAG, "Currency: ${result.amount?.priceCurrency}")
                    Log.d(TAG, "loading completed successfully!")
                }

                override fun onFailure(error: PaymentError) {
                    Log.e(TAG, "=== LOADING SALE FAILURE ===")
                    Log.e(TAG, "Code: ${error.code}")
                    Log.e(TAG, "Message: ${error.message}")
                    Log.e(TAG, "loading failed at sale execution")
                }

                override fun onProgress(event: com.sunmi.tapro.taplink.sdk.model.common.PaymentEvent) {
                    Log.d(TAG, "LOADING SALE Progress: ${event.progress} - ${event.eventMsg}")
                }
            })

        } catch (e: Exception) {
            Log.e(TAG, "Error: ${e.message}")
            Log.e(TAG, "Exception: ", e)
        }
    }
}
