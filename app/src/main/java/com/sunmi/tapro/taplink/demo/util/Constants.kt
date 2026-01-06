package com.sunmi.tapro.taplink.demo.util

import com.sunmi.tapro.taplink.demo.config.AppConfig
import java.math.BigDecimal

/**
 * Application Constants and Configuration Values
 * 
 * Centralized repository for all application constants, eliminating magic values
 * throughout the codebase. This class is integrated with the configuration management
 * system to provide both static constants and dynamic configuration values.
 * 
 * The constants are organized into logical groups:
 * - Request codes for activity communication
 * - Product pricing for preset amount buttons
 * - Transaction processing parameters
 * - Network and connection settings
 * - UI timing and behavior constants
 * - Error codes and status values
 * - String resources for messages and labels
 */
object Constants {
    
    // Activity result request codes for inter-activity communication
    const val REQUEST_CODE_CONNECTION = 1001
    const val REQUEST_CODE_TRANSACTION_LIST = 1002
    
    // Preset product amounts (in USD) for quick transaction entry
    // These values provide common purchase amounts for demonstration purposes
    val PRODUCT_COFFEE_PRICE = BigDecimal("3.50")
    val PRODUCT_SANDWICH_PRICE = BigDecimal("8.99")
    val PRODUCT_COLA_PRICE = BigDecimal("2.50")
    val PRODUCT_HOT_DOG_PRICE = BigDecimal("6.50")
    
    // Transaction ID generation parameters for unique identifier creation
    const val TRANSACTION_ID_MIN_RANDOM = 1000
    const val TRANSACTION_ID_MAX_RANDOM = 9999
    
    // Network configuration defaults (can be overridden by configuration system)
    const val DEFAULT_LAN_PORT = 8443
    
    // Currency conversion constants for SDK amount handling
    const val CENTS_TO_DOLLARS_MULTIPLIER = 100
    
    // UI behavior constants
    const val MULTIPLE_SALE_PAYMENT_COUNT = 3
    
    // Amount formatting and precision settings
    const val AMOUNT_DECIMAL_PLACES = 2
    const val AMOUNT_FORMAT_PATTERN = "$#,##0.00"
    
    // Default staff information for transaction processing
    const val DEFAULT_OPERATOR_ID = "Harry"
    const val DEFAULT_TIP_RECIPIENT_ID = "Harry"
    
    // Connection error codes for standardized error handling
    object ConnectionErrorCodes {
        const val TARGET_APP_CRASHED = "C36"        // Tapro application crashed during operation
        const val CONNECTION_TIMEOUT = "C01"        // Connection attempt timed out
        const val CONNECTION_FAILED = "C02"         // General connection failure
        const val CONNECTION_LOST = "C03"           // Existing connection was lost
        const val SERVICE_DISCONNECTED = "C04"      // Payment service disconnected
        const val SERVICE_BINDING_FAILED = "C05"    // Failed to bind to payment service
        const val TRANSACTION_TIMEOUT = "E10"       // Transaction processing timed out
    }
    
    // Transaction result codes from payment platform
    object TransactionResultCodes {
        const val SUCCESS = "000"                    // Transaction completed successfully
        const val APPROVED = "00"                    // Alternative success code
        const val DECLINED = "05"                    // Transaction declined by issuer
    }
    
    // Transaction status values for internal state management
    object TransactionStatus {
        const val SUCCESS = "SUCCESS"                // Transaction completed successfully
        const val FAILED = "FAILED"                 // Transaction failed or was declined
        const val PROCESSING = "PROCESSING"          // Transaction is being processed
    }
    
    // Identifier prefixes for transaction tracking and debugging
    const val ORDER_ID_PREFIX = "ORDER_"
    const val TRANSACTION_REQUEST_ID_PREFIX = "TXN_REQ_"
    const val STANDALONE_ORDER_PREFIX = "ORD_"
    
    // Batch close operation defaults
    const val BATCH_CLOSE_DEFAULT_COUNT = 0
    val BATCH_CLOSE_DEFAULT_AMOUNT = BigDecimal.ZERO
    
    /**
     * Configuration-based constants accessor methods
     * 
     * These methods provide access to configurable values managed by the
     * configuration system, allowing runtime adjustment of application behavior
     * without code changes. Each method delegates to the appropriate configuration
     * category for centralized management.
     */
    
    // UI timing configuration - controls user interface behavior and responsiveness
    fun getPaymentProgressHideDelay(): Long = AppConfig.getUiConfig().paymentProgressHideDelayMs
    fun getErrorDialogShowDelay(): Long = AppConfig.getUiConfig().errorDialogShowDelayMs
    fun getClickIntervalProtection(): Long = AppConfig.getUiConfig().clickIntervalProtectionMs
    fun getProgressDialogTimeout(): Long = AppConfig.getUiConfig().progressDialogTimeoutMs
    fun getInputValidationDelay(): Long = AppConfig.getUiConfig().inputValidationDelayMs
    
    // Network configuration - controls connection behavior and timeouts
    fun getConnectionTimeout(): Long = AppConfig.getNetworkConfig().connectionTimeoutMs
    fun getTransactionTimeout(): Long = AppConfig.getNetworkConfig().transactionTimeoutMs
    fun getNetworkTestTimeout(): Long = AppConfig.getNetworkConfig().networkTestTimeoutMs
    fun getMaxRetryCount(): Int = AppConfig.getNetworkConfig().maxRetryCount
    fun getRetryDelay(): Long = AppConfig.getNetworkConfig().retryDelayMs
    
    // Transaction configuration - controls payment processing behavior
    fun getDefaultCurrency(): String = AppConfig.getTransactionConfig().defaultCurrency
    fun getMinTransactionAmount(): BigDecimal = AppConfig.getTransactionConfig().minTransactionAmount
    fun getMaxTransactionAmount(): BigDecimal = AppConfig.getTransactionConfig().maxTransactionAmount
    fun getAmountDecimalPlaces(): Int = AppConfig.getTransactionConfig().amountDecimalPlaces
    fun getAmountFormatPattern(): String = AppConfig.getTransactionConfig().amountFormatPattern
    fun getMultipleSalePaymentCount(): Int = AppConfig.getTransactionConfig().multipleSalePaymentCount
    
    // User interface messages and labels for consistent text throughout the application
    object Messages {
        // Connection status display messages
        const val STATUS_NOT_CONNECTED = "Not Connected"
        const val STATUS_CONNECTED = "Connected"
        const val STATUS_CONNECTING = "Connecting..."
        const val STATUS_CONNECTION_FAILED = "Connection Failed"
        const val STATUS_UNKNOWN = "Status Unknown"
        
        // Connection mode display names for user interface
        const val MODE_APP_TO_APP = "App-to-App"
        const val MODE_CABLE = "Cable"
        const val MODE_LAN = "LAN"
        
        // Dialog titles
        const val TITLE_CONNECTION_LOST = "Connection Lost"
        const val TITLE_CONNECTION_ERROR = "Connection Error"
        const val TITLE_CONNECTION_FAILED = "Connection Failed"
        const val TITLE_PAYMENT_SUCCESS = "Payment Success"
        const val TITLE_PAYMENT_FAILED = "Payment Failed"
        const val TITLE_PAYMENT_PROCESSING = "Payment Processing"
        const val TITLE_PAYMENT_ERROR = "Payment Error"
        const val TITLE_UNKNOWN_PAYMENT_RESULT = "Unknown Payment Result"
        const val TITLE_QUERY_FAILED = "Query Failed"
        const val TITLE_QUERYING_STATUS = "Querying Status"
        const val TITLE_TRANSACTION_FOUND_SUCCESS = "Transaction Found - Success"
        const val TITLE_TRANSACTION_FOUND_FAILED = "Transaction Found - Failed"
        const val TITLE_TRANSACTION_STILL_PROCESSING = "Transaction Still Processing"
        const val TITLE_UNKNOWN_TRANSACTION_STATUS = "Unknown Transaction Status"
        const val TITLE_FORCED_AUTH_AMOUNTS = "Forced Authorization - Additional Amounts"
        
        // Dialog button labels
        const val BUTTON_RETRY = "Retry"
        const val BUTTON_CANCEL = "Cancel"
        const val BUTTON_OK = "OK"
        const val BUTTON_PROCEED = "Proceed"
        const val BUTTON_SKIP = "Skip"
        const val BUTTON_QUERY_AGAIN = "Query Again"
        const val BUTTON_QUERY_STATUS = "Query Status"
        
        // Toast messages
        const val TOAST_SELECT_PAYMENT_AMOUNT = "Please select payment amount"
        const val TOAST_UNSUPPORTED_TRANSACTION_TYPE = "Unsupported transaction type: %s"
        const val TOAST_NO_TRANSACTION_TO_QUERY = "No transaction found to query"
        
        // Payment progress messages
        const val PAYMENT_PROCESSING_TAPRO = "Payment processing, please complete in Tapro app"
        const val CHECKING_TRANSACTION_STATUS = "Checking transaction status..."
        
        // Log messages
        const val LOG_UI_INITIALIZATION_COMPLETED = "MainActivity UI initialization completed"
        const val LOG_SDK_CONNECTION_SUCCESS = "=== SDK Connection SUCCESS ==="
        const val LOG_SDK_CONNECTION_DISCONNECTED = "=== SDK Connection DISCONNECTED ==="
        const val LOG_SDK_CONNECTION_ERROR = "=== SDK Connection ERROR ==="
        const val LOG_ATTEMPTING_SDK_CONNECTION = "=== Attempting SDK Connection ==="
        const val LOG_UI_UPDATED_CONNECTION_SUCCESSFUL = "UI updated - connection successful"
        const val LOG_UI_UPDATED_CONNECTION_LOST = "UI updated - connection lost"
        const val LOG_UI_UPDATED_CONNECTION_ERROR = "UI updated - connection error"
        const val LOG_SDK_ALREADY_CONNECTED = "SDK already connected, registering listener for status monitoring"
        const val LOG_DIRECT_SDK_LISTENER_REGISTERED = "Direct SDK listener registered - already connected"
        const val LOG_SDK_DISCONNECTED_VIA_LISTENER = "===SDK DISCONNECTED (via listener) ==="
        const val LOG_SDK_ERROR_VIA_LISTENER = "===SDK ERROR (via listener) ==="
        const val LOG_CONNECTION_STATUS_CHECK = "Connection status check - SDK: %s, PaymentService: %s"
        const val LOG_CONNECTED_NO_VERSION_INFO = "Connected but no version info available yet"
        const val LOG_CURRENT_CONNECTION_STATUS = "Current connection status: %s (SDK: %s, Version: %s)"
        const val LOG_ERROR_CHECKING_CONNECTION_STATUS = "Error checking SDK connection"
        const val LOG_LAN_MODE_NO_IP = "LAN mode selected but no IP configured"
        const val LOG_UPDATE_CONNECTION_STATUS_DISPLAY = "updateConnectionStatusDisplay - status: %s, connected: %s"
        const val LOG_DISPLAYING_VERSION = "Displaying version: %s"
        const val LOG_ADD_AMOUNT = "Add amount: %s, Total: %s"
        const val LOG_CUSTOM_AMOUNT_INPUT = "Custom amount input: %s"
        const val LOG_LIST_ITEM_CLICKED = "List item clicked at position: %s"
        const val LOG_TRANSACTION_CLICKED = "Transaction: %s, type: %s"
        const val LOG_PAYMENT_PROGRESS = "Payment progress: %s"
        const val LOG_PAYMENT_RESULT = "Payment result - TransactionId: %s, Status: %s"
        const val LOG_RESULT_TRANSACTION_REQUEST_ID = "Result transactionRequestId: %s"
        const val LOG_FAILED_UPDATE_TRANSACTION_STATUS = "Failed to update transaction status - transactionRequestId not found: %s"
        const val LOG_UPDATING_LATEST_PROCESSING_TRANSACTION = "Updating latest PROCESSING transaction: %s"
        const val LOG_NO_PROCESSING_TRANSACTION_FOUND = "No PROCESSING transaction found to update"
        const val LOG_PAYMENT_FAILED_CONNECTION_ERROR = "Payment failed due to connection error: %s - %s"
        const val LOG_QUERYING_TRANSACTION_STATUS = "Querying transaction status: %s"
        const val LOG_QUERY_FAILED = "Query failed: %s - %s"
        const val LOG_QUERY_RESULT = "Query result - Status: %s, ID: %s"
        const val LOG_NO_TRANSACTION_FOUND_TO_QUERY = "No transaction found to query"
        const val LOG_ERROR_DISMISSING_PROGRESS_DIALOG = "Error dismissing payment progress dialog"
        
        // Error messages
        const val ERROR_CONNECTION_DISCONNECTED = "Connection was disconnected: %s"
        const val ERROR_FAILED_TO_CONNECT = "Failed to connect: %s\n\nError Code: %s"
        const val ERROR_CONNECTION_ERROR = "Connection error: %s\n\nError Code: %s"
        const val ERROR_QUERY_FAILED = "Failed to query transaction status:\n%s\n\nError Code: %s"
        
        // Transaction descriptions
        const val DESC_DEMO_SALE_PAYMENT = "Demo SALE Payment - %s"
        const val DESC_DEMO_AUTH_PAYMENT = "Demo AUTH Payment - %s"
        const val DESC_DEMO_FORCED_AUTH_PAYMENT = "Demo FORCED_AUTH Payment - %s"
        
        // Payment result messages
        const val RESULT_TRANSACTION_COMPLETED = "Transaction Completed\nTotalAmount: %s\nTransaction ID: %s\nAuth Code: %s\nStatus: %s"
        const val RESULT_TRANSACTION_FAILED = "Transaction Failed\nAmount: %s\nTransaction ID: %s\nStatus: %s\nError Code: %s\nError Message: %s"
        const val RESULT_TRANSACTION_PROCESSING = "Transaction Processing\nAmount: %s\nTransaction ID: %s\nStatus: %s\nPlease check transaction result later"
        const val RESULT_UNKNOWN_STATUS = "Unknown Transaction Status\nAmount: %s\nTransaction ID: %s\nStatus: %s\nPlease contact support or check transaction history"
        const val RESULT_TRANSACTION_WAS_SUCCESSFUL = "Transaction was actually successful:\nAmount: %s\nTransaction ID: %s\nAuth Code: %s"
        const val RESULT_TRANSACTION_WAS_FAILED = "Transaction was confirmed as failed:\nAmount: %s\nError: %s\nError Code: %s"
        const val RESULT_TRANSACTION_STILL_PROCESSING = "Transaction is still being processed:\nAmount: %s\nTransaction ID: %s\nPlease check again later."
        const val RESULT_TRANSACTION_STATUS_UNKNOWN = "Transaction status is unknown:\nAmount: %s\nStatus: %s\nPlease contact support."
        const val RESULT_BASE_AMOUNT = "Base Amount: %s"
        
        // Placeholder values
        const val PLACEHOLDER_NA = "N/A"
        const val PLACEHOLDER_UNKNOWN = "UNKNOWN"
        
        // Version prefix
        const val VERSION_PREFIX = "v"
    }
    
    // Transaction type strings
    object TransactionTypes {
        const val SALE = "SALE"
        const val AUTH = "AUTH"
        const val FORCED_AUTH = "FORCED_AUTH"
        const val REFUND = "REFUND"
        const val VOID = "VOID"
        const val POST_AUTH = "POST_AUTH"
        const val INCREMENT_AUTH = "INCREMENT_AUTH"
        const val TIP_ADJUST = "TIP_ADJUST"
        const val QUERY = "QUERY"
        const val BATCH_CLOSE = "BATCH_CLOSE"
    }
    
    // SDK initialization messages
    object SdkMessages {
        const val SDK_INITIALIZATION_FAILED_MISSING_CONFIG = "SDK initialization failed: Missing configuration"
        const val SDK_INITIALIZED_SUCCESSFULLY = "SDK initialized successfully"
        const val SDK_INITIALIZATION_FAILED = "SDK initialization failed: %s"
        const val CONNECTING_WITH_PROVIDED_CONFIG = "Connecting with provided ConnectionConfig: %s"
        const val ALREADY_CONNECTED_REGISTERING_LISTENER = "Already connected, but registering new listener"
        const val ALREADY_CONNECTING_UPDATING_LISTENER = "Already connecting, just updating listener"
        const val CALLING_TAPLINK_SDK_CONNECT = "Calling TaplinkSDK.connect() with provided config: %s"
        const val CONNECTED_DEVICE_VERSION = "Connected - Device: %s, Version: %s"
        const val DISCONNECTED_REASON = "Disconnected - Reason: %s"
        const val CONNECTION_ERROR_CODE_MESSAGE = "Connection error - Code: %s, Message: %s"
        const val HANDLE_CONNECTED_CALLED = "=== handleConnected called ==="
        const val DEVICE_ID_VERSION = "Device ID: %s, Version: %s"
        const val CONNECTION_LISTENER_IS_NULL = "connectionListener is null: %s"
        const val CURRENT_THREAD = "Current thread: %s"
        const val ABOUT_TO_CALL_CONNECTION_LISTENER = "About to call connectionListener.onConnected directly"
        const val CONNECTION_LISTENER_OBJECT = "connectionListener object: %s"
        const val CALLING_LISTENER_ON_CONNECTED = "Calling listener.onConnected(%s, %s)"
        const val LISTENER_ON_CONNECTED_COMPLETED = "listener.onConnected call completed successfully"
        const val CONNECTION_LISTENER_NULL_CANNOT_CALL = "connectionListener is null, cannot call onConnected"
        const val EXCEPTION_IN_CONNECTION_LISTENER = "Exception in connectionListener.onConnected"
        const val HANDLE_CONNECTED_COMPLETED = "=== handleConnected completed ==="
        const val HANDLE_DISCONNECTED_CALLED = "=== handleDisconnected called ==="
        const val CALLING_LISTENER_ON_DISCONNECTED = "Calling listener.onDisconnected(%s)"
        const val LISTENER_ON_DISCONNECTED_COMPLETED = "listener.onDisconnected call completed successfully"
        const val CONNECTION_LISTENER_NULL_CANNOT_CALL_DISCONNECTED = "connectionListener is null, cannot call onDisconnected"
        const val EXCEPTION_IN_CONNECTION_LISTENER_DISCONNECTED = "Exception in connectionListener.onDisconnected"
        const val HANDLE_DISCONNECTED_COMPLETED = "=== handleDisconnected completed ==="
        const val HANDLE_CONNECTION_ERROR_CALLED = "=== handleConnectionError called ==="
        const val ABOUT_TO_CALL_CONNECTION_LISTENER_ERROR = "About to call connectionListener.onError directly"
        const val CALLING_LISTENER_ON_ERROR = "Calling listener.onError(%s, %s)"
        const val LISTENER_ON_ERROR_COMPLETED = "listener.onError call completed successfully"
        const val CONNECTION_LISTENER_NULL_CANNOT_CALL_ERROR = "connectionListener is null, cannot call onError"
        const val EXCEPTION_IN_CONNECTION_LISTENER_ERROR = "Exception in connectionListener.onError"
        const val HANDLE_CONNECTION_ERROR_COMPLETED = "=== handleConnectionError completed ==="
        const val PAYMENT_FAILED_CODE_MESSAGE = "%s failed - Code: %s, Message: %s"
        const val PAYMENT_FAILURE_DUE_TO_CONNECTION_ERROR = "Payment failure due to connection error: %s"
        const val CONNECTION_LOST_DETECTED = "Connection lost detected: %s"
        const val PAYMENT_RESULT_CODE_STATUS = "Payment result - Code: %s, Status: %s"
        const val DISCONNECTING_CONNECTION = "Disconnecting connection..."
        const val USER_INITIATED_DISCONNECTION = "User initiated disconnection"
        const val ERROR_CHECKING_SDK_CONNECTION_STATUS = "Error checking SDK connection status"
        const val SDK_REPORTS_DISCONNECTED = "SDK reports disconnected but internal state was connected - updating"
        const val SDK_CONNECTION_LOST = "SDK connection lost"
        const val EXECUTING_SALE_TRANSACTION = "Executing SALE transaction - OrderId: %s, Amount: %s %s"
        const val SALE_REQUEST = "=== SALE Request ==="
        const val REQUEST = "Request: %s"
        const val SALE_PROGRESS = "SALE progress: %s"
        const val EXECUTING_AUTH_TRANSACTION = "Executing AUTH transaction - OrderId: %s, Amount: %s %s"
        const val AUTH_REQUEST = "=== AUTH Request ==="
        const val AUTH_PROGRESS = "AUTH progress: %s"
        const val EXECUTING_FORCED_AUTH_TRANSACTION = "Executing FORCED_AUTH transaction - OrderId: %s"
        const val FORCED_AUTH_REQUEST = "=== FORCED_AUTH Request ==="
        const val FORCED_AUTH_PROGRESS = "FORCED_AUTH progress: %s"
        const val EXECUTING_REFUND_TRANSACTION = "Executing REFUND transaction - OriginalTxnId: %s, Amount: %s %s"
        const val CREATING_REFUND_WITH_ORIGINAL_ID = "Creating REFUND request with originalTransactionId: %s"
        const val CREATING_STANDALONE_REFUND = "Creating standalone REFUND request without originalTransactionId"
        const val SETTING_REFUND_REASON = "Setting refund reason: %s"
        const val REFUND_REQUEST = "=== REFUND Request ==="
        const val REFUND_PROGRESS = "REFUND progress: %s"
        const val EXECUTING_VOID_TRANSACTION = "Executing VOID transaction - OriginalTxnId: %s"
        const val VOID_REQUEST = "=== VOID Request ==="
        const val VOID_PROGRESS = "VOID progress: %s"
        const val EXECUTING_POST_AUTH_TRANSACTION = "Executing POST_AUTH transaction - OriginalTxnId: %s, Amount: %s %s"
        const val POST_AUTH_REQUEST = "=== POST_AUTH Request ==="
        const val POST_AUTH_PROGRESS = "POST_AUTH progress: %s"
        const val EXECUTING_INCREMENT_AUTH_TRANSACTION = "Executing INCREMENT_AUTH transaction - OriginalTxnId: %s, Amount: %s %s"
        const val INCREMENT_AUTH_REQUEST = "=== INCREMENT_AUTH Request ==="
        const val INCREMENT_AUTH_PROGRESS = "INCREMENT_AUTH progress: %s"
        const val EXECUTING_TIP_ADJUST_TRANSACTION = "Executing TIP_ADJUST transaction - OriginalTxnId: %s, TipAmount: %s"
        const val TIP_ADJUST_REQUEST = "=== TIP_ADJUST Request ==="
        const val TIP_ADJUST_PROGRESS = "TIP_ADJUST progress: %s"
        const val EXECUTING_QUERY_TRANSACTION = "Executing QUERY transaction - TransactionRequestId: %s"
        const val QUERY_REQUEST_BY_REQUEST_ID = "=== QUERY Request (by RequestId) ==="
        const val QUERY_PROGRESS = "QUERY progress: %s"
        const val EXECUTING_QUERY_BY_TRANSACTION_ID = "Executing QUERY transaction - TransactionId: %s"
        const val QUERY_REQUEST_BY_TRANSACTION_ID = "=== QUERY Request (by TransactionId) ==="
        const val QUERY_TRANSACTION_PROCESSING = "QUERY transaction processing..."
        const val EXECUTING_BATCH_CLOSE_TRANSACTION = "Executing BATCH_CLOSE transaction"
        const val BATCH_CLOSE_REQUEST = "=== BATCH_CLOSE Request ==="
        const val BATCH_CLOSE_PROGRESS = "BATCH_CLOSE progress: %s"
    }
}