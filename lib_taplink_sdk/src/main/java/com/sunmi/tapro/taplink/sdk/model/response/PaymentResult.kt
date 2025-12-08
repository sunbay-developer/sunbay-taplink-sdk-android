package com.sunmi.tapro.taplink.sdk.model.response

import com.sunmi.tapro.taplink.sdk.model.common.BatchAmount
import com.sunmi.tapro.taplink.sdk.model.common.BatchCloseInfo
import com.sunmi.tapro.taplink.sdk.model.common.CardInfo
import com.sunmi.tapro.taplink.sdk.model.common.TransactionAmount
import java.math.BigDecimal

/**
 * 支付结果类
 * 
 * 包含全面的交易信息
 * 
 * @author TaPro Team
 * @since 2025-01-XX
 */
data class PaymentResult(
    // ========== 基本响应字段 ==========
    
    /**
     * 响应码（"0" 表示成功）
     */
    val code: String,
    
    /**
     * 响应描述
     */
    val message: String? = null,
    
    /**
     * 追踪 ID（用于故障排查）
     */
    val traceId: String? = null,
    
    // ========== 交易标识符字段 ==========
    
    /**
     * 交易 ID（Nexus（SUNBAY支付网关）交易 ID）
     */
    val transactionId: String? = null,
    
    /**
     * 商户订单号/参考订单号
     */
    val referenceOrderId: String? = null,
    
    /**
     * 交易请求 ID
     */
    val transactionRequestId: String? = null,
    
    // ========== 交易状态字段 ==========
    
    /**
     * 交易状态
     * 值：SUCCESS, PROCESSING, FAILED
     */
    val transactionStatus: String? = null,
    
    /**
     * 交易类型
     * 值：SALE, AUTH, FORCED_AUTH, INCREMENTAL, POST_AUTH, VOID, REFUND
     */
    val transactionType: String? = null,
    
    // ========== 金额信息 ==========
    
    /**
     * 交易金额详情
     */
    val amount: TransactionAmount? = null,
    
    // ========== 时间字段 ==========
    
    /**
     * 交易创建时间（ISO 8601 格式）
     */
    val createTime: String? = null,
    
    /**
     * 交易完成时间（ISO 8601 格式）
     */
    val completeTime: String? = null,
    
    // ========== 卡信息 ==========
    
    /**
     * 卡片信息（包含卡号、卡类型、输入方式等）
     */
    val cardInfo: CardInfo? = null,
    
    // ========== 交易凭证信息 ==========
    
    /**
     * 批次号
     */
    val batchNo: Int? = null,
    
    /**
     * 凭证号
     */
    val voucherNo: String? = null,
    
    /**
     * 系统跟踪审计号（STAN）
     */
    val stan: String? = null,
    
    /**
     * 检索参考号（RRN）
     */
    val rrn: String? = null,
    
    /**
     * 授权码
     */
    val authCode: String? = null,
    
    // ========== 交易结果信息 ==========
    
    /**
     * 交易结果码
     */
    val transactionResultCode: String? = null,
    
    /**
     * 交易结果消息
     */
    val transactionResultMsg: String? = null,
    
    // ========== 终端和描述信息 ==========
    
    /**
     * 商品描述
     */
    val description: String? = null,
    
    /**
     * 附加数据（原样返回）
     */
    val attach: String? = null,
    
    // ========== 批次关闭特定字段 ==========
    
    /**
     * 批次关闭信息（仅批次关闭）
     */
    val batchCloseInfo: BatchCloseInfo? = null,
    
    // ========== 小费调整特定字段 ==========
    
    /**
     * 小费金额（仅小费调整，单位：基本货币单位）
     */
    var tipAmount: BigDecimal? = null,
    
    // ========== 增量授权特定字段 ==========
    
    /**
     * 增量金额（仅增量授权，单位：基本货币单位）
     */
    var incrementalAmount: BigDecimal? = null,
    
    /**
     * 总授权金额（仅增量授权，单位：基本货币单位）
     */
    var totalAuthorizedAmount: BigDecimal? = null,
    
    // ========== 退款特定字段 ==========
    
    /**
     * 商户退款号（仅退款）
     */
    val merchantRefundNo: String? = null,
    
    /**
     * 原始交易 ID（退款/撤销/授权完成）
     */
    val originalTransactionId: String? = null,
    
    /**
     * 原始交易请求 ID（退款/撤销/授权完成）
     */
    val originalTransactionRequestId: String? = null
) {
    /**
     * 检查交易是否成功
     * 
     * @return true 如果响应码为 "0" 且交易状态为 "SUCCESS"
     */
    fun isSuccess(): Boolean {
        return "0" == code && "SUCCESS" == transactionStatus
    }
    
    /**
     * 检查交易是否处理中
     * 
     * @return true 如果交易状态为 "PROCESSING"
     */
    fun isProcessing(): Boolean {
        return "PROCESSING" == transactionStatus
    }
    
    /**
     * 检查交易是否失败
     * 
     * @return true 如果交易状态为 "FAILED" 或响应码不为 "0"
     */
    fun isFailed(): Boolean {
        return "FAILED" == transactionStatus || "0" != code
    }
}
