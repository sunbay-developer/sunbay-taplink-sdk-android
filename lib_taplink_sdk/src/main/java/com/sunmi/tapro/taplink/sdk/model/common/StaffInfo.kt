package com.sunmi.tapro.taplink.sdk.model.common

/**
 * Staff information.
 * 
 * Used to describe staff information for processing transactions.
 * 
 * @author TaPro Team
 * @since 2025-01-XX
 */
data class StaffInfo(
    /**
     * Operator ID (optional).
     * Identifier for the staff member processing the transaction.
     */
    val operatorId: String? = null,
    
    /**
     * Tip recipient ID (optional).
     * Identifier for the staff member receiving the tip (e.g., waiter).
     */
    val tipRecipientId: String? = null
) {
    /**
     * Sets the operator ID.
     *
     * @param operatorId the operator ID
     * @return the updated StaffInfo instance for method chaining
     */
    fun setOperatorId(operatorId: String): StaffInfo = copy(operatorId = operatorId)
    
    /**
     * Sets the tip recipient ID.
     *
     * @param tipRecipientId the tip recipient ID
     * @return the updated StaffInfo instance for method chaining
     */
    fun setTipRecipientId(tipRecipientId: String): StaffInfo = copy(tipRecipientId = tipRecipientId)
}

