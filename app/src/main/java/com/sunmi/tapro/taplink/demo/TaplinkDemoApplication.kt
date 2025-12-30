package com.sunmi.tapro.taplink.demo

import android.app.Application
import android.util.Log
import com.sunmi.tapro.taplink.sdk.TaplinkSDK
import com.sunmi.tapro.taplink.sdk.callback.ConnectionListener
import com.sunmi.tapro.taplink.sdk.callback.PaymentCallback
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
 * Taplink Demo Application Class
 * Cold start testing - automatically initialize SDK and execute SALE on app startup
 * This is for debugging and testing cold start functionality with loading
 */
class TaplinkDemoApplication : Application() {

    companion object {
        private const val TAG = "TaplinkDemoApp"
    }

    override fun onCreate() {
        super.onCreate()

        Log.d(TAG, "=== Application Cold Start Test Started ===")
        Log.d(TAG, "Testing SDK initialization and SALE execution on cold start (Loading Mode)")

        // Execute cold start test sequence: init -> connect -> sale (loading)
        initializeTaplinkSDK()
        startLazyLoadingConnection()
    }

    /**
     * Initialize Taplink SDK
     */
    private fun initializeTaplinkSDK() {
        try {
            Log.d(TAG, "=== Taplink SDK Initialization Started ===")

            // Read configuration from resource files
            val appId = getString(R.string.taplink_app_id)
            val merchantId = getString(R.string.taplink_merchant_id)
            val secretKey = getString(R.string.taplink_secret_key)

            // Get user's saved connection mode preference
            val savedMode = com.sunmi.tapro.taplink.demo.util.ConnectionPreferences.getConnectionMode(this)

            // Map to SDK ConnectionMode
            val sdkConnectionMode = when (savedMode) {
                com.sunmi.tapro.taplink.demo.util.ConnectionPreferences.ConnectionMode.APP_TO_APP -> {
                    Log.d(TAG, "Using saved mode: App-to-App")
                    ConnectionMode.APP_TO_APP
                }
                com.sunmi.tapro.taplink.demo.util.ConnectionPreferences.ConnectionMode.CABLE -> {
                    Log.d(TAG, "Using saved mode: Cable")
                    ConnectionMode.CABLE
                }
                com.sunmi.tapro.taplink.demo.util.ConnectionPreferences.ConnectionMode.LAN -> {
                    Log.d(TAG, "Using saved mode: LAN")
                    ConnectionMode.LAN
                }
            }

            // Log configuration parameters (mask sensitive data)
            Log.d(TAG, "=== SDK Init Request Parameters ===")
            Log.d(TAG, "App ID: $appId")
            Log.d(TAG, "Merchant ID: $merchantId")
            Log.d(TAG, "Secret Key: ${secretKey.take(4)}****${secretKey.takeLast(4)}")
            Log.d(TAG, "Connection Mode: $sdkConnectionMode")

            // Create SDK configuration
            val config = TaplinkConfig(
                appId = appId,
                merchantId = merchantId,
                secretKey = secretKey
            ).setLogEnabled(true).setLogLevel(LogLevel.DEBUG).setConnectionMode(sdkConnectionMode)

            // Initialize SDK
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
     * Start loading connection - connect and execute sale when connected
     */
    private fun startLazyLoadingConnection() {
        Log.d(TAG, "=== Starting Loading Connection ===")

        val taplinkApi = TaplinkSDK.getInstance()

        Log.d(TAG, "Attempting connection...")
        taplinkApi.connect(null, object : ConnectionListener {
            override fun onConnected(deviceId: String, taproVersion: String) {
                Log.d(TAG, "=== Connection SUCCESS ===")
                Log.d(TAG, "Device ID: $deviceId")
                Log.d(TAG, "Tapro Version: $taproVersion")

                // loading: Execute sale immediately after connection success
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
     * Execute SALE transaction (called automatically after connection success)
     */
    private fun executeSale() {
        Log.d(TAG, "=== Executing SALE Transaction ===")

        // Double check connection status
//        if (!TaplinkSDK.isConnected()) {
//            Log.e(TAG, "SDK not connected, cannot execute sale")
//            return
//        }

        val referenceOrderId = "LAZY_ORDER_${System.currentTimeMillis()}"
        val transactionRequestId = "LAZY_REQ_${System.currentTimeMillis()}"
        val amount = BigDecimal("1")
        val currency = "USD"

        Log.d(TAG, "Order ID: $referenceOrderId")
        Log.d(TAG, "Transaction Request ID: $transactionRequestId")
        Log.d(TAG, "Amount: $amount $currency")

        try {
            // Create AmountInfo with required parameters
            val amountInfo = AmountInfo(
                orderAmount = amount,
                pricingCurrency = currency
            )

            // Create PaymentRequest using chain methods
            val paymentRequest = PaymentRequest(
                action = TransactionAction.SALE.value
            ).setReferenceOrderId(referenceOrderId)
                .setTransactionRequestId(transactionRequestId)
                .setAmount(amountInfo)
                .setDescription("Loading SALE Transaction")

            Log.d(TAG, "PaymentRequest created, executing...")

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
