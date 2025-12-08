package com.sunmi.tapro.taplink.sdk.model.common

/**
 * 员工信息
 * 
 * 用于描述处理交易的员工信息
 * 
 * @author TaPro Team
 * @since 2025-01-XX
 */
data class StaffInfo(
    /**
     * 操作员 ID（可选）
     * 处理交易的员工标识符
     */
    val operatorId: String? = null,
    
    /**
     * 小费接收者 ID（可选）
     * 接收小费的员工标识符（如服务员）
     */
    val tipRecipientId: String? = null
) {
    /**
     * 链式调用：设置操作员 ID
     */
    fun setOperatorId(operatorId: String): StaffInfo = copy(operatorId = operatorId)
    
    /**
     * 链式调用：设置小费接收者 ID
     */
    fun setTipRecipientId(tipRecipientId: String): StaffInfo = copy(tipRecipientId = tipRecipientId)
}

