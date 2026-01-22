package com.sunmi.tapro.taplink.communication.enums

import com.sunmi.tapro.taplink.communication.util.ErrorStringHelper

/**
 * Error code definitions
 *
 * All error codes and their descriptions according to Taplink SDK integration guide
 * Supports multi-language localization, error descriptions and solutions retrieved from resource files
 *
 * Error code range descriptions:
 * - 100: Success response
 * - 20x: Initialization errors
 * - 21x: Connection status/failure
 * - 23x: Local mode connection failure
 * - 24x: LAN mode connection failure
 * - 25x: USB mode connection failure
 * - 30x: Transaction processing errors
 *
 * @author TaPro Team
 * @since 2025-01-14
 */
sealed class InnerErrorCode(
    /** Error code */
    val code: String
) {
    /**
     * Get the category this error code belongs to
     */
    val category: ErrorCategory
        get() = ErrorCategory.fromCode(code)

    /** Error description (retrieved from resource files) */
    val description: String
        get() = getDescriptionForCode(code)

    /**
     * Get description for the error code
     */
    protected open fun getDescriptionForCode(code: String): String {
        return ErrorStringHelper.getDescription(code)
    }

    // ==================== Success Response ====================
    /** 100 - Operation successful */
    object Success : InnerErrorCode("100")

    // ==================== Initialization Errors (20x) ====================
    /** 201 - SDK not initialized */
    object E201 : InnerErrorCode("201")

    /** 202 - SDK service exception */
    object E202 : InnerErrorCode("202")

    /** 203 - Tapro initialization failed */
    object E203 : InnerErrorCode("203")

    // ==================== Connection Status/Failure (21x) ====================
    /** 211 - Connection already exists, no need to reconnect */
    object E211 : InnerErrorCode("211")

    /** 212 - Device not connected */
    object E212 : InnerErrorCode("212")

    /** 213 - Connection disconnected */
    object E213 : InnerErrorCode("213")

    /** 214 - Unable to establish connection */
    object E214 : InnerErrorCode("214")

    /** 221 - Authentication failed */
    object E221 : InnerErrorCode("221")

    // ==================== Local Mode Connection Failure (23x) ====================
    /** 231 - Tapro application not installed */
    object E231 : InnerErrorCode("231")

    /** 232 - Unable to connect to Tapro */
    object E232 : InnerErrorCode("232")

    // ==================== LAN Mode Connection Failure (24x) ====================
    /** 241 - Unable to connect to server */
    object E241 : InnerErrorCode("241")

    /** 242 - Unable to discover server */
    object E242 : InnerErrorCode("242")

    // ==================== USB Mode Connection Failure (25x) ====================
    /** 251 - USB mode connection failed */
    object E251 : InnerErrorCode("251")

    /** 252 - USB permission denied */
    object E252 : InnerErrorCode("252")

    /** 253 - USB connection timeout */
    object E253 : InnerErrorCode("253")

    /** 254 - USB protocol not supported */
    object E254 : InnerErrorCode("254")

    /** 255 - Serial port device not ready */
    object E255 : InnerErrorCode("255")

    // ==================== Transaction Processing Errors (30x) ====================
    /** 301 - Missing required parameters */
    object E301 : InnerErrorCode("301")

    /** 302 - Request data format error */
    object E302 : InnerErrorCode("302")

    /** 303 - Unsupported transaction type */
    object E303 : InnerErrorCode("303")

    /** 304 - Request send failed */
    object E304 : InnerErrorCode("304")

    /** 305 - Transaction in progress */
    object E305 : InnerErrorCode("305")

    /** 306 - Response timeout (need to query transaction status first) */
    object E306 : InnerErrorCode("306")

    /** 307 - Transaction rejected (must retry with new ID) */
    object E307 : InnerErrorCode("307")

    /** 308 - Transaction processing (need to query transaction status first) */
    object E308 : InnerErrorCode("308")

    /** 309 - Transaction terminated (do not retry) */
    object E309 : InnerErrorCode("309")

    /** 310 - Insufficient balance (must retry with new ID) */
    object E310 : InnerErrorCode("310")

    /** 311 - Password error */
    object E311 : InnerErrorCode("311")

    /** 312 - Query transaction failed */
    object E312 : InnerErrorCode("312")

    // ==================== Unknown Error Code ====================
    class Unknown(
        errorCode: String,
        private val customDescription: String? = null
    ) : InnerErrorCode(errorCode) {

        override fun getDescriptionForCode(code: String): String {
            return customDescription ?: ErrorStringHelper.getDescription("unknown")
        }
    }

    companion object {

        /**
         * Get corresponding ErrorCode object based on error code string
         *
         * @param code Error code string
         * @return ErrorCode object, returns Unknown if not found
         */
        fun fromCode(code: String?, errorMsg: String? = ""): InnerErrorCode {
            if (code.isNullOrBlank()) {
                return Unknown("", ErrorStringHelper.getDescription(""))
            }

            return when (code.uppercase()) {
                // Success response
                "0", "100" -> Success

                // New error codes (20x-39x)
                // Initialization errors (20x)
                "201" -> E201
                "202" -> E202
                "203" -> E203

                // Connection status/failure (21x)
                "211" -> E211
                "212" -> E212
                "213" -> E213
                "214" -> E214
                "221" -> E221

                // Local mode connection failure (23x)
                "231" -> E231
                "232" -> E232

                // LAN mode connection failure (24x)
                "241" -> E241
                "242" -> E242
                "251" -> E251

                // USB mode connection failure (25x)
                "252" -> E252
                "253" -> E253
                "254" -> E254
                "255" -> E255

                // Transaction processing errors (30x)
                "301" -> E301
                "302" -> E302
                "303" -> E303
                "304" -> E304
                "305" -> E305
                "306" -> E306
                "307" -> E307
                "308" -> E308
                "309" -> E309
                "310" -> E310
                "311" -> E311
                "312" -> E312

                // Unknown error code
                else -> Unknown(code, customDescription = errorMsg)
            }
        }

        /**
         * Get all predefined error code list
         *
         * @return All predefined error code list
         */
        fun getAllErrorCodes(): List<InnerErrorCode> {
            return listOf(
                Success,
                // New error codes (according to integration guide (New))
                E201, E202, E203,
                E211, E212, E213, E214, E221,
                E231, E232,
                E241, E242,
                E251, E252, E253, E254, E255,
                E301, E302, E303, E304, E305, E306, E307, E308, E309, E310, E311, E312
            )
        }

        /**
         * Determine if error code is initialization error (20x)
         */
        fun isInitializationError(code: String?): Boolean {
            return code in listOf("201", "202", "203")
        }

        /**
         * Determine if error code is connection status/failure error (21x)
         */
        fun isConnectionStateError(code: String?): Boolean {
            return code in listOf("211", "212", "213", "214", "221")
        }

        /**
         * Determine if error code is local mode connection failure (23x)
         */
        fun isLocalModeConnectionError(code: String?): Boolean {
            return code in listOf("231", "232")
        }

        /**
         * Determine if error code is LAN mode connection failure (24x)
         */
        fun isLanModeConnectionError(code: String?): Boolean {
            return code in listOf("241", "242")
        }

        /**
         * Determine if error code is USB mode connection failure (25x)
         */
        fun isUsbModeConnectionError(code: String?): Boolean {
            return code in listOf("251", "252", "253", "254", "255")
        }

        /**
         * Determine if error code is transaction processing error (30x)
         */
        fun isTransactionProcessingError(code: String?): Boolean {
            return code in listOf("301", "302", "303", "304", "305", "306", "307", "308", "309", "310", "311", "312")
        }

        /**
         * Determine if need to query transaction status before retry
         * Error codes 306, 308 need to query first
         */
        fun needsQueryBeforeRetry(code: String?): Boolean {
            return code in listOf("306", "308")
        }

        /**
         * Determine if must use new transactionRequestId for retry
         * Error codes 307, 310, 311 must use new ID
         */
        fun mustUseNewTransactionId(code: String?): Boolean {
            return code in listOf("307", "310", "311")
        }

        /**
         * Determine if can retry with the same transactionRequestId
         * Error codes 301-305 can use the same ID
         */
        fun canRetryWithSameId(code: String?): Boolean {
            return code in listOf("301", "302", "303", "304", "305")
        }

        /**
         * Determine if should not retry
         * Error code 309 indicates transaction terminated, should not retry
         */
        fun shouldNotRetry(code: String?): Boolean {
            return code == "309"
        }
    }
}