package com.sunmi.tapro.taplink.sdk.model.common

/**
 * Staff information.
 * 
 * Used to describe staff information for processing transactions.
 * 
 * Field setters update this instance and return it for chaining. Use [copy] for
 * independent top-level values, and finish updates before request submission.
 * Serialize writes and do not mutate instances used as hash collection keys.
 *
 * @author Tapro Team
 * @since 2025-01-XX
 */
class StaffInfo(
    /**
     * Operator ID (optional).
     * Identifier for the staff member processing the transaction.
     */
    operatorId: String? = null,
    
    /**
     * Tip recipient ID (optional).
     * Identifier for the staff member receiving the tip (e.g., waiter).
     */
    tipRecipientId: String? = null
) {
    var operatorId: String? = operatorId
        private set

    var tipRecipientId: String? = tipRecipientId
        private set

    /**
     * Sets the operator ID.
     *
     * @param operatorId the operator ID
     * @return this StaffInfo instance for method chaining
     */
    fun setOperatorId(operatorId: String): StaffInfo = apply { this.operatorId = operatorId }
    
    /**
     * Sets the tip recipient ID.
     *
     * @param tipRecipientId the tip recipient ID
     * @return this StaffInfo instance for method chaining
     */
    fun setTipRecipientId(tipRecipientId: String): StaffInfo = apply { this.tipRecipientId = tipRecipientId }

    /** Creates an independent shallow copy; setters mutate this instance. */
    fun copy(
        operatorId: String? = this.operatorId,
        tipRecipientId: String? = this.tipRecipientId
    ): StaffInfo = StaffInfo(
        operatorId = operatorId,
        tipRecipientId = tipRecipientId
    )

    operator fun component1(): String? = operatorId
    operator fun component2(): String? = tipRecipientId

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is StaffInfo) return false
        return operatorId == other.operatorId &&
            tipRecipientId == other.tipRecipientId
    }

    override fun hashCode(): Int {
        var result = (operatorId?.hashCode() ?: 0)
        result = 31 * result + (tipRecipientId?.hashCode() ?: 0)
        return result
    }

    override fun toString(): String =
        "StaffInfo(operatorId=$operatorId, tipRecipientId=$tipRecipientId)"

}

