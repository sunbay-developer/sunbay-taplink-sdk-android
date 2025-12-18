package com.sunmi.tapro.taplink.communication.enums

import com.sunmi.tapro.taplink.communication.util.ErrorStringHelper

/**
 * Error Code Definition
 * 
 * Defines all error codes and their descriptions according to Taplink SDK documentation
 * Supports multi-language localization, error descriptions and solutions are retrieved from resource files
 * 
 * @author TaPro Team
 * @since 2025-01-XX
 */
sealed class InnerErrorCode(
    /** Error code */
    val code: String
) {
    /** Error description (retrieved from resource files) */
    val description: String
        get() = getDescriptionForCode(code)

    /**
     * Get description for error code
     */
    protected open fun getDescriptionForCode(code: String): String {
        return ErrorStringHelper.getDescription(code)
    }

    // ==================== Success Response ====================
    object Success : InnerErrorCode("0")

    // ==================== Security Restriction Errors (S Series) ====================
    object S01 : InnerErrorCode("S01")
    object S02 : InnerErrorCode("S02")
    object S03 : InnerErrorCode("S03")
    object S04 : InnerErrorCode("S04")
    object S05 : InnerErrorCode("S05")
    object S06 : InnerErrorCode("S06")
    object S07 : InnerErrorCode("S07")
    object S08 : InnerErrorCode("S08")
    object S09 : InnerErrorCode("S09")

    // ==================== Client Errors (C Series) ====================
    object C01 : InnerErrorCode("C01")
    object C02 : InnerErrorCode("C02")
    object C03 : InnerErrorCode("C03")
    object C04 : InnerErrorCode("C04")
    object C05 : InnerErrorCode("C05")
    object C06 : InnerErrorCode("C06")
    object C07 : InnerErrorCode("C07")
    object C08 : InnerErrorCode("C08")
    object C09 : InnerErrorCode("C09")
    object C10 : InnerErrorCode("C10")
    object C11 : InnerErrorCode("C11")
    object C12 : InnerErrorCode("C12")
    object C13 : InnerErrorCode("C13")
    object C14 : InnerErrorCode("C14")
    object C15 : InnerErrorCode("C15")
    object C16 : InnerErrorCode("C16")
    object C17 : InnerErrorCode("C17")
    object C18 : InnerErrorCode("C18")
    object C19 : InnerErrorCode("C19")
    object C20 : InnerErrorCode("C20")
    object C21 : InnerErrorCode("C21")
    object C22 : InnerErrorCode("C22")
    object C23 : InnerErrorCode("C23")
    object C24 : InnerErrorCode("C24")
    object C25 : InnerErrorCode("C25")
    object C26 : InnerErrorCode("C26")
    object C27 : InnerErrorCode("C27")
    object C28 : InnerErrorCode("C28")
    object C29 : InnerErrorCode("C29")
    object C30 : InnerErrorCode("C30")
    object C31 : InnerErrorCode("C31")
    object C32 : InnerErrorCode("C32")
    object C33 : InnerErrorCode("C33")
    object C34 : InnerErrorCode("C34")
    object C35 : InnerErrorCode("C35")
    object C36 : InnerErrorCode("C36")
    object C37 : InnerErrorCode("C37")
    object C38 : InnerErrorCode("C38")
    object C39 : InnerErrorCode("C39")
    object C40 : InnerErrorCode("C40")
    object C41 : InnerErrorCode("C41")

    // ==================== Merchant Configuration Errors (M Series) ====================
    object M01 : InnerErrorCode("M01")
    object M02 : InnerErrorCode("M02")
    object M03 : InnerErrorCode("M03")
    object M04 : InnerErrorCode("M04")

    // ==================== Payment Channel Errors (P Series) ====================
    object P01 : InnerErrorCode("P01")

    // ==================== System Errors (E Series) ====================
    object E01 : InnerErrorCode("E01")
    object E02 : InnerErrorCode("E02")
    object E03 : InnerErrorCode("E03")

    // ==================== Risk Control Interception (R Series) ====================
    object R01 : InnerErrorCode("R01")

    // ==================== Other Error Codes ====================
    object Exception : InnerErrorCode("E04")
    object Timeout : InnerErrorCode("996")

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
         * Get corresponding ErrorCode object from error code string
         * 
         * @param code Error code string
         * @return ErrorCode object, returns Unknown if not found
         */
        fun fromCode(code: String?): InnerErrorCode {
            if (code.isNullOrBlank()) {
                return Unknown("", ErrorStringHelper.getDescription(""))
            }
            
            return when (code.uppercase()) {
                // Success Response
                "0" -> Success
                
                // Security Restriction Errors (S Series)
                "S01" -> S01
                "S02" -> S02
                "S03" -> S03
                "S04" -> S04
                "S05" -> S05
                "S06" -> S06
                "S07" -> S07
                "S08" -> S08
                "S09" -> S09
                
                // Client Errors (C Series)
                "C01" -> C01
                "C02" -> C02
                "C03" -> C03
                "C04" -> C04
                "C05" -> C05
                "C06" -> C06
                "C07" -> C07
                "C08" -> C08
                "C09" -> C09
                "C10" -> C10
                "C11" -> C11
                "C12" -> C12
                "C13" -> C13
                "C14" -> C14
                "C15" -> C15
                "C16" -> C16
                "C17" -> C17
                "C18" -> C18
                "C19" -> C19
                "C20" -> C20
                "C21" -> C21
                "C22" -> C22
                "C23" -> C23
                "C24" -> C24
                "C25" -> C25
                "C26" -> C26
                "C27" -> C27
                "C28" -> C28
                "C29" -> C29
                "C30" -> C30
                "C31" -> C31
                "C32" -> C32
                "C33" -> C33
                "C34" -> C34
                "C35" -> C35
                "C36" -> C36
                "C37" -> C37
                "C38" -> C38
                "C39" -> C39
                "C40" -> C40
                "C41" -> C41
                
                // Merchant Configuration Errors (M Series)
                "M01" -> M01
                "M02" -> M02
                "M03" -> M03
                "M04" -> M04
                
                // Payment Channel Errors (P Series)
                "P01" -> P01
                
                // System Errors (E Series)
                "E01" -> E01
                "E02" -> E02
                "E03" -> E03
                "E04" -> Exception
                
                // Risk Control Interception (R Series)
                "R01" -> R01
                
                // Other Error Codes
                "996" -> Timeout
                
                // Unknown Error Code
                else -> Unknown(code)
            }
        }

        /**
         * Get all predefined error codes list
         * 
         * @return All predefined error codes list
         */
        fun getAllErrorCodes(): List<InnerErrorCode> {
            return listOf(
                Success,
                S01, S02, S03, S04, S05, S06, S07, S08, S09,
                C01, C02, C03, C04, C05, C06, C07, C08, C09, C10,
                C11, C12, C13, C14, C15, C16, C17, C18, C19, C20,
                C21, C22, C23, C24, C25, C26, C27, C28, C29, C30,
                C31, C32, C33, C34, C35, C36, C37, C38, C39, C40, C41,
                M01, M02, M03, M04,
                P01,
                E01, E02, E03, Exception,
                R01,
                Timeout
            )
        }

        /**
         * Check if error code is client error (C Series)
         */
        fun isClientError(code: String?): Boolean {
            return code?.uppercase()?.startsWith("C") == true
        }

        /**
         * Check if error code is system error (E Series)
         */
        fun isSystemError(code: String?): Boolean {
            return code?.uppercase()?.startsWith("E") == true
        }

        /**
         * Check if error code is security restriction error (S Series)
         */
        fun isSecurityError(code: String?): Boolean {
            return code?.uppercase()?.startsWith("S") == true
        }

        /**
         * Check if error code is merchant configuration error (M Series)
         */
        fun isMerchantConfigError(code: String?): Boolean {
            return code?.uppercase()?.startsWith("M") == true
        }

        /**
         * Check if error code is payment channel error (P Series)
         */
        fun isPaymentChannelError(code: String?): Boolean {
            return code?.uppercase()?.startsWith("P") == true
        }

        /**
         * Check if error code is risk control error (R Series)
         */
        fun isRiskControlError(code: String?): Boolean {
            return code?.uppercase()?.startsWith("R") == true
        }
    }
}