package com.sunmi.tapro.taplink.demo.util

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
    
    // Progress dialog timeout (in milliseconds)
    const val PROGRESS_DIALOG_TIMEOUT = 30000L // 30 seconds
    
    // Amount formatting and precision settings
    const val AMOUNT_DECIMAL_PLACES = 2
    const val AMOUNT_FORMAT_PATTERN = "$#,##0.00"
    
    // Default staff information for transaction processing
    const val DEFAULT_OPERATOR_ID = "Harry"
    const val DEFAULT_TIP_RECIPIENT_ID = "Harry"
    
    // Response code constants for SDK result validation
    // Note: Response success is now determined by SDK callback (onSuccess vs onFailure)
    // Code 100 is the only success code, all 20x-39x codes are error codes
    const val SUCCESS_CODE = "100"                   // SDK success code (for reference only)
    
    // Error code ranges (20x-39x)
    object ErrorCodes {
        // Initialization errors (20x)
        const val SDK_NOT_INITIALIZED = "201"        // SDK not initialized
        const val SDK_SERVICE_EXCEPTION = "202"      // SDK service exception
        const val TAPRO_INIT_FAILED = "203"          // Tapro initialization failed
        
        // Connection state errors (21x)
        const val CONNECTION_EXISTS = "211"          // Connection already exists
        const val DEVICE_NOT_CONNECTED = "212"       // Device not connected
        const val CONNECTION_DISCONNECTED = "213"    // Connection disconnected
        const val CONNECTION_FAILED = "214"          // Cannot establish connection
        const val AUTH_FAILED = "221"                // Authentication failed
        
        // Local mode connection errors (23x)
        const val TAPRO_NOT_INSTALLED = "231"        // Tapro app not installed
        const val CANNOT_CONNECT_TAPRO = "232"       // Cannot connect to Tapro
        
        // LAN mode connection errors (24x)
        const val CANNOT_CONNECT_SERVER = "241"      // Cannot connect to server
        const val CANNOT_DISCOVER_SERVER = "242"     // Cannot discover server
        
        // USB mode connection errors (25x)
        const val CABLE_NOT_CONNECTED = "251"        // Cable not connected
        const val USB_PERMISSION_DENIED = "252"      // USB permission denied
        const val USB_CONNECTION_TIMEOUT = "253"     // USB connection timeout
        const val USB_PROTOCOL_UNSUPPORTED = "254"   // USB protocol not supported
        const val SERIAL_DEVICE_NOT_READY = "255"    // Serial device not ready
        
        // Transaction processing errors (30x + 399)
        const val MISSING_REQUIRED_PARAM = "301"     // Missing required parameter
        const val DATA_FORMAT_ERROR = "302"          // Data format error
        const val UNSUPPORTED_TRANSACTION_TYPE = "303" // Unsupported transaction type
        const val REQUEST_SEND_FAILED = "304"        // Request send failed
        const val TRANSACTION_IN_PROGRESS = "305"    // Transaction in progress
        const val RESPONSE_TIMEOUT = "306"           // Response timeout
        const val TRANSACTION_FAILED = "307"         // Transaction failed
        const val TRANSACTION_PROCESSING = "308"     // Transaction processing
        const val TRANSACTION_TERMINATED = "309"     // Transaction terminated
        const val INSUFFICIENT_BALANCE = "310"       // Insufficient balance
        const val PASSWORD_ERROR = "311"             // Password error
        const val TRANSACTION_ERROR = "399"          // General transaction error
    }
    
    // Legacy connection error codes (for backward compatibility)
    object ConnectionErrorCodes {
        const val TARGET_APP_CRASHED = "C36"         // Tapro application crashed during operation
        const val CONNECTION_TIMEOUT = "C01"         // Connection attempt timed out
        const val CONNECTION_FAILED = "C02"          // General connection failure
        const val CONNECTION_LOST = "C03"            // Existing connection was lost
        const val SERVICE_DISCONNECTED = "C04"       // Payment service disconnected
        const val SERVICE_BINDING_FAILED = "C05"     // Failed to bind to payment service
        const val TRANSACTION_TIMEOUT = "E10"        // Transaction processing timed out
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
    const val TRANSACTION_REQUEST_ID_PREFIX = "REQ_"
    const val STANDALONE_ORDER_PREFIX = "ORD_"
    
    // Batch close operation defaults
    const val BATCH_CLOSE_DEFAULT_COUNT = 0
    val BATCH_CLOSE_DEFAULT_AMOUNT = BigDecimal.ZERO
    
    /**
     * Configuration values - hardcoded for simplicity and reliability
     */
    
    // UI timing configuration - controls user interface behavior and responsiveness
    fun getPaymentProgressHideDelay(): Long = 1500L
    fun getErrorDialogShowDelay(): Long = 100L
    fun getClickIntervalProtection(): Long = 500L
    fun getInputValidationDelay(): Long = 500L
    
    // Network configuration - controls connection behavior and timeouts
    fun getConnectionTimeout(): Long = 30000L
    fun getTransactionTimeout(): Long = 60000L
    fun getNetworkTestTimeout(): Long = 3000L
    fun getMaxRetryCount(): Int = 3
    fun getRetryDelay(): Long = 2000L
    
    // Transaction configuration - controls payment processing behavior
    fun getDefaultCurrency(): String = "USD"
    fun getMinTransactionAmount(): BigDecimal = BigDecimal("0.01")
    fun getMaxTransactionAmount(): BigDecimal = BigDecimal("99999.99")
    fun getAmountDecimalPlaces(): Int = 2
    fun getAmountFormatPattern(): String = "$#,##0.00"
    fun getMultipleSalePaymentCount(): Int = 3
    
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

        const val LOG_LAN_MODE_NO_IP = "LAN mode selected but no IP configured"

        const val LOG_ADD_AMOUNT = "Add amount: %s, Total: %s"
        const val LOG_CUSTOM_AMOUNT_INPUT = "Custom amount input: %s"
        
        // Error messages
        const val ERROR_CONNECTION_DISCONNECTED = "Connection was disconnected: %s"
        const val ERROR_FAILED_TO_CONNECT = "Failed to connect: %s\n\nError Code: %s"
        
        // Payment result messages
        const val RESULT_TRANSACTION_COMPLETED = "Transaction Completed\nTotalAmount: %s\nTransaction ID: %s\nAuth Code: %s\nStatus: %s"
        const val RESULT_TRANSACTION_FAILED = "Transaction Failed\nAmount: %s\nTransaction ID: %s\nStatus: %s\nError Code: %s\nError Message: %s"
        const val RESULT_TRANSACTION_PROCESSING = "Transaction Processing\nAmount: %s\nTransaction ID: %s\nStatus: %s\nPlease check transaction result later"
        const val RESULT_UNKNOWN_STATUS = "Unknown Transaction Status\nAmount: %s\nTransaction ID: %s\nStatus: %s\nPlease contact support or check transaction history"
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