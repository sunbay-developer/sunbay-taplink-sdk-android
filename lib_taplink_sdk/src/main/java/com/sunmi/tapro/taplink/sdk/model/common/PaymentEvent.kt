package com.sunmi.tapro.taplink.sdk.model.common

/**
 * 交易进度事件
 *
 * 使用 sealed class 提供类型安全的事件定义
 * 支持 when 表达式的穷尽检查
 *
 * @author TaPro Team
 * @since 2025-01-XX
 */
sealed class PaymentEvent(
    /** 状态码 */
    open val eventCode: String,

    /** 状态描述 */
    open val eventMsg: String,

    /** 进度（0-100） */
    val progress: Int,

    /** 时间戳 */
    val timestamp: Long = System.currentTimeMillis()
) {
    /**
     * 处理中事件
     */
    object Processing : PaymentEvent("PROCESSING", "Processing", 10)

    /**
     * 等待卡片事件
     */
    object WaitingCard : PaymentEvent("WAITING_CARD", "Waiting for card", 20)

    /**
     * 卡片已检测事件
     */
    object CardDetected : PaymentEvent("CARD_DETECTED", "Card detected", 30)

    /**
     * 读取卡片事件
     */
    object ReadingCard : PaymentEvent("READING_CARD", "Reading card", 40)

    /**
     * 等待PIN码事件
     */
    object WaitingPin : PaymentEvent("WAITING_PIN", "Waiting for PIN", 50)

    /**
     * 等待签名事件
     */
    object WaitingSignature : PaymentEvent("WAITING_SIGNATURE", "Waiting for signature", 55)

    /**
     * 等待联机请求结果事件
     */
    object WaitingOnlineResponse : PaymentEvent("WAITING_RESPONSE", "Waiting for online response", 70)

    /**
     * 打印中事件
     */
    object Printing : PaymentEvent("PRINTING", "Printing receipt", 80)

    /**
     * 已完成事件
     */
    object Completed : PaymentEvent("COMPLETED", "Transaction completed", 100)

    /**
     * 取消事件
     */
    object Cancel : PaymentEvent("CANCEL", "Transaction cancelled", 0)

    /**
     * 重连中事件
     */
    data class Reconnecting(
        val attempt: Int,
        val maxRetries: Int,
        override val eventCode: String = "RECONNECTING",
        override val eventMsg: String = "Reconnecting... (attempt $attempt/$maxRetries)"
    ) : PaymentEvent(eventCode, eventMsg, 0)

    companion object {
        /**
         * 根据 eventCode 字符串创建对应的 PaymentEvent
         * 用于 JSON 反序列化
         *
         * @param eventCode 事件码
         * @return PaymentEvent 对应的子类实例
         */
        fun fromEventCode(eventCode: String): PaymentEvent {
            return when (eventCode.uppercase()) {
                "WAITING_CARD" -> WaitingCard
                "CARD_DETECTED" -> CardDetected
                "READING_CARD" -> ReadingCard
                "WAITING_PIN" -> WaitingPin
                "WAITING_SIGNATURE" -> WaitingSignature
                "PROCESSING" -> Processing
                "WAITING_RESPONSE" -> WaitingOnlineResponse
                "PRINTING" -> Printing
                "COMPLETED", "4003" -> Completed  // 4003 是完成事件的数字编码
                "CANCEL" -> Cancel
                else -> {
                    // 未知事件类型，使用 Processing 作为默认值
                    Processing
                }
            }
        }
    }
}
