package com.sunmi.tapro.taplink.sdk.model.common

/**
 * Payment method category enumeration.
 * 
 * Defines all supported payment method categories.
 * 
 * @author TaPro Team
 * @since 2025-01-XX
 */
enum class PaymentCategory {
    /**
     * Bank card (generic).
     */
    CARD,
    
    /**
     * Credit card.
     */
    CARD_CREDIT,
    
    /**
     * Debit card.
     */
    CARD_DEBIT,
    
    /**
     * QR code merchant-presented mode (merchant scans customer).
     */
    QR_MPM,
    
    /**
     * QR code customer-presented mode (customer scans merchant).
     */
    QR_CPM,
    
    /**
     * Electronic Benefit Transfer.
     */
    EBT,
    
    /**
     * Cash.
     */
    CASH;
    
    companion object {
        /**
         * Converts from string to enum.
         * 
         * @param value the string value
         * @return the corresponding enum value, or null if not recognized
         */
        fun fromString(value: String?): PaymentCategory? {
            if (value.isNullOrBlank()) return null
            
            return when (value.uppercase()) {
                "CARD" -> CARD
                "CARD-CREDIT" -> CARD_CREDIT
                "CARD-DEBIT" -> CARD_DEBIT
                "QR-MPM" -> QR_MPM
                "QR-CPM" -> QR_CPM
                "EBT" -> EBT
                "CASH" -> CASH
                else -> null
            }
        }
    }
    
    /**
     * Converts to string (for API transmission).
     */
    fun toApiString(): String {
        return when (this) {
            CARD -> "CARD"
            CARD_CREDIT -> "CARD-CREDIT"
            CARD_DEBIT -> "CARD-DEBIT"
            QR_MPM -> "QR-MPM"
            QR_CPM -> "QR-CPM"
            EBT -> "EBT"
            CASH -> "CASH"
        }
    }
}


