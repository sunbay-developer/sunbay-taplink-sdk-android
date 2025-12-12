package com.sunmi.tapro.taplink.sdk.enums

/**
 * 交易类型枚举
 *
 * 定义所有支持的交易类型，避免使用魔法字符串
 *
 * @author TaPro Team
 * @since 2025-12-12
 */
enum class TransactionAction(val value: String) {
    /**
     * 初始化连接
     * 用于建立连接后初始化 TaPro 应用
     */
    INIT("INIT"),

    /**
     * 销售交易
     * 标准的支付交易
     */
    SALE("SALE"),

    /**
     * 退款交易
     * 对已完成的交易进行退款
     */
    REFUND("REFUND"),

    /**
     * 撤销交易
     * 撤销当天的交易（当日退款）
     */
    VOID("VOID"),

    /**
     * 预授权
     * 冻结资金但不扣款
     */
    AUTH("AUTH"),

    /**
     * 预授权完成
     * 完成预授权并扣款
     */
    POST_AUTH("POST_AUTH"),

    /**
     * 预授权追加
     * 增加预授权金额
     */
    INCREMENT_AUTH("INCREMENT_AUTH"),

    /**
     * 强制授权
     * 使用授权码强制完成交易
     */
    FORCED_AUTH("FORCED_AUTH"),

    /**
     * 小费调整
     * 调整已完成交易的小费金额
     */
    TIP_ADJUST("TIP_ADJUST"),

    /**
     * 批次结算
     * 结算当前批次的所有交易
     */
    BATCH_CLOSE("BATCH_CLOSE"),

    /**
     * 查询交易
     * 查询交易状态
     */
    QUERY("QUERY"),

    /**
     * 终止交易
     * 终止当前正在进行的交易
     */
    ABORT("ABORT");

    companion object {
        /**
         * 从字符串值获取枚举
         *
         * @param value 字符串值
         * @return TransactionAction? 对应的枚举，如果不存在则返回 null
         */
        @JvmStatic
        fun fromValue(value: String): TransactionAction? {
            return values().find { it.value.equals(value, ignoreCase = true) }
        }

        /**
         * 获取所有支持的交易类型值
         *
         * @return List<String> 所有交易类型的字符串值列表
         */
        @JvmStatic
        fun getAllValues(): List<String> {
            return values().map { it.value }
        }
    }

    /**
     * 转换为字符串
     */
    override fun toString(): String = value
}
