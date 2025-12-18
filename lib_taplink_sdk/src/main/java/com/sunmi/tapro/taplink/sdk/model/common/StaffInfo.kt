package com.sunmi.tapro.taplink.sdk.model.common

/**
 * Staff Information
 * 
 * Used to describe staff information for processing transactions
 * 
 * @author TaPro Team
 * @since 2025-01-XX
 */
data class StaffInfo(
    /**
     * Operator ID (optional)
     * Staff identifier for processing transactions
     */
    val operatorId: String? = null,
    
    /**
     * Tip recipient ID (optional)
     * Staff identifier for receiving tips (e.g., waiter)
     */
    val tipRecipientId: String? = null
) {
    /**
     * Chained call: Set operator ID
     */
    fun setOperatorId(operatorId: String): StaffInfo = copy(operatorId = operatorId)
    
    /**
     * Chained call: Set tip recipient ID
     */
    fun setTipRecipientId(tipRecipientId: String): StaffInfo = copy(tipRecipientId = tipRecipientId)
}

