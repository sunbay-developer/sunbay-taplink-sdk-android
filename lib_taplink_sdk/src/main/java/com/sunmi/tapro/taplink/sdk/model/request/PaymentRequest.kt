package com.sunmi.tapro.taplink.sdk.model.request

import com.sunmi.tapro.taplink.sdk.enums.TransactionAction
import com.sunmi.tapro.taplink.sdk.model.common.AmountInfo
import com.sunmi.tapro.taplink.sdk.model.common.DeviceInfo
import com.sunmi.tapro.taplink.sdk.model.common.GoodsDetail
import com.sunmi.tapro.taplink.sdk.model.common.PaymentMethodInfo
import com.sunmi.tapro.taplink.sdk.model.common.StaffInfo
import java.math.BigDecimal

/**
 * 支付请求类
 *
 * 具有全面的字段支持，用于各种交易类型
 *
 * **推荐使用枚举：**
 * ```kotlin
 * val request = PaymentRequest(
 *     action = TransactionAction.SALE,
 *     referenceOrderId = "ORDER001",
 *     transactionRequestId = "TXN001",
 *     amount = amountInfo,
 *     description = "商品购买"
 * )
 * ```
 *
 * **Java 调用说明：**
 * ```java
 * PaymentRequest request = PaymentRequest.builder()
 *     .setAction(TransactionAction.SALE)
 *     .setReferenceOrderId("ORDER001")
 *     .setTransactionRequestId("TXN001")
 *     .setAmount(amountInfo)
 *     .setDescription("商品购买")
 *     .build();
 * ```
 *
 * **重要说明：**
 * - referenceOrderId 和 transactionRequestId 可以使用相同的值
 * - transactionRequestId 用于幂等性控制，相同值的请求视为同一笔交易
 * - 临时性错误（网络超时、系统繁忙）时，可使用相同 transactionRequestId 重试
 * - 明确失败或修改参数后，必须使用新的 transactionRequestId
 *
 * @author TaPro Team
 * @since 2025-01-XX
 */
data class PaymentRequest(
    // ========== 基本交易字段 ==========

    /**
     * 交易类型（必需）
     * 推荐使用 TransactionAction 枚举，也支持字符串（向后兼容）
     *
     * 支持的类型：
     * - SALE: 销售交易
     * - REFUND: 退款交易
     * - VOID: 撤销交易
     * - AUTH: 预授权
     * - POST_AUTH: 预授权完成
     * - INCREMENT_AUTH: 预授权追加
     * - FORCED_AUTH: 强制授权
     * - TIP_ADJUST: 小费调整
     * - BATCH_CLOSE: 批次结算
     * - QUERY: 查询交易
     */
    val action: String,

    /**
     * 商户订单号/参考订单号（必需，6-32 个字符）
     * 商户系统中订单的唯一标识符
     *
     * 说明：
     * - 用于标识商户业务系统中的订单或业务记录
     * - 一个订单可以关联多笔交易（如原始交易、退款、小费调整等）
     * - 同一订单的多笔交易使用相同的 referenceOrderId
     * - 可以与 transactionRequestId 使用相同的值（简化场景）
     */
    val referenceOrderId: String? = null,

    /**
     * 交易请求 ID（必需，用于幂等性）
     * 此特定交易请求的唯一标识符
     *
     * 幂等性说明：
     * - 相同 transactionRequestId 的请求被视为同一笔交易
     * - 临时性错误（网络超时、系统繁忙）时，可使用相同 transactionRequestId 重试
     * - 明确失败或修改参数后，必须使用新的 transactionRequestId
     * - 同一订单的不同交易类型，必须使用不同的 transactionRequestId
     *
     * 可以与 referenceOrderId 使用相同的值（简化场景）
     */
    val transactionRequestId: String? = null,


    /**
     * 交易描述（必需）
     * 交易描述或商品描述
     */
    val description: String? = null,

    // ========== 金额信息 ==========

    /**
     * 金额信息（必需）
     * 包含详细金额明细的结构化对象
     */
    val amount: AmountInfo? = null,

    // ========== 支付方式 ==========

    /**
     * 支付方式信息（可选）
     * 指定首选支付方式类别和 ID
     */
    val paymentMethod: PaymentMethodInfo? = null,

    // ========== 员工信息 ==========

    /**
     * 员工信息（可选）
     * 操作员和小费接收者的信息
     */
    val staffInfo: StaffInfo? = null,

    // ========== 设备信息 ==========

    /**
     * 设备信息（可选）
     * 发起交易的设备详情
     */
    val deviceInfo: DeviceInfo? = null,

    // ========== 商品详情 ==========

    /**
     * 商品详情列表（可选）
     * 交易中商品/服务的明细列表
     */
    val goodsDetail: List<GoodsDetail>? = null,

    // ========== 引用交易字段 ==========

    /**
     * 原始交易 ID（退款/撤销/授权完成时必需）
     * 引用原始交易
     */
    val originalTransactionId: String? = null,

    /**
     * 原始交易请求 ID（originalTransactionId 的替代）
     * 使用原始交易请求 ID 进行引用
     */
    val originalTransactionRequestId: String? = null,

    // ========== 小费调整 ==========

    /**
     * 小费金额（小费调整时必需，单位：基本货币单位）
     */
    val tipAmount: BigDecimal? = null,

    // ========== 强制授权特定字段 ==========

    /**
     * 强制授权码（强制授权时必需）
     * 用于强制授权交易的授权码
     */
    val forcedAuthCode: String? = null,

    // ========== 附加字段 ==========

    /**
     * 原因（退款/撤销时可选）
     * 退款或撤销的原因
     */
    val reason: String? = null,

    /**
     * 附加数据（可选）
     * 附加到交易的自定义数据（推荐 JSON 格式）
     */
    val attach: String? = null,

    /**
     * 通知 URL（可选）
     * 接收交易结果通知的 URL
     */
    val notifyUrl: String? = null,

    /**
     * 请求超时时间(单位:秒)
     */
    val requestTimeout: Long? = null
) {
    // ========== 基本交易字段的链式调用方法 ==========

    /**
     * 链式调用：设置交易类型（使用枚举）
     * 推荐使用此方法，类型安全
     */
    fun setAction(action: TransactionAction): PaymentRequest = copy(action = action.value)

    /**
     * 链式调用：设置交易类型（使用字符串）
     * 为了向后兼容保留，推荐使用 TransactionAction 枚举
     */
    fun setAction(action: String): PaymentRequest = copy(action = action)

    /**
     * 链式调用：设置商户订单号/参考订单号
     */
    fun setReferenceOrderId(referenceOrderId: String): PaymentRequest = copy(referenceOrderId = referenceOrderId)

    /**
     * 链式调用：设置商户订单号（兼容旧方法名）
     * @deprecated 使用 setReferenceOrderId 代替
     */
    @Deprecated("使用 setReferenceOrderId 代替", ReplaceWith("setReferenceOrderId(merchantOrderNo)"))
    fun setMerchantOrderNo(merchantOrderNo: String): PaymentRequest = copy(referenceOrderId = merchantOrderNo)

    /**
     * 链式调用：设置交易请求 ID
     */
    fun setTransactionRequestId(transactionRequestId: String): PaymentRequest = copy(transactionRequestId = transactionRequestId)

    /**
     * 链式调用：设置交易描述
     */
    fun setDescription(description: String): PaymentRequest = copy(description = description)

    // ========== 金额信息的链式调用方法 ==========

    /**
     * 链式调用：设置金额信息
     */
    fun setAmount(amount: AmountInfo): PaymentRequest = copy(amount = amount)

    // ========== 支付方式的链式调用方法 ==========

    /**
     * 链式调用：设置支付方式信息
     */
    fun setPaymentMethod(paymentMethod: PaymentMethodInfo): PaymentRequest = copy(paymentMethod = paymentMethod)

    // ========== 员工信息的链式调用方法 ==========

    /**
     * 链式调用：设置员工信息
     */
    fun setStaffInfo(staffInfo: StaffInfo): PaymentRequest = copy(staffInfo = staffInfo)

    // ========== 设备信息的链式调用方法 ==========

    /**
     * 链式调用：设置设备信息
     */
    fun setDeviceInfo(deviceInfo: DeviceInfo): PaymentRequest = copy(deviceInfo = deviceInfo)

    // ========== 商品详情的链式调用方法 ==========

    /**
     * 链式调用：设置商品详情列表
     */
    fun setGoodsDetail(goodsDetail: List<GoodsDetail>): PaymentRequest = copy(goodsDetail = goodsDetail)

    // ========== 引用交易字段的链式调用方法 ==========

    /**
     * 链式调用：设置原始交易 ID
     */
    fun setOriginalTransactionId(originalTransactionId: String): PaymentRequest =
        copy(originalTransactionId = originalTransactionId)

    /**
     * 链式调用：设置原始交易请求 ID
     */
    fun setOriginalTransactionRequestId(originalTransactionRequestId: String): PaymentRequest =
        copy(originalTransactionRequestId = originalTransactionRequestId)

    // ========== 小费调整的链式调用方法 ==========

    /**
     * 链式调用：设置小费金额
     */
    fun setTipAmount(tipAmount: BigDecimal): PaymentRequest = copy(tipAmount = tipAmount)

    // ========== 强制授权的链式调用方法 ==========

    /**
     * 链式调用：设置强制授权码
     */
    fun setForcedAuthCode(forcedAuthCode: String): PaymentRequest = copy(forcedAuthCode = forcedAuthCode)

    // ========== 附加字段的链式调用方法 ==========

    /**
     * 链式调用：设置原因
     */
    fun setReason(reason: String): PaymentRequest = copy(reason = reason)

    /**
     * 链式调用：设置附加数据
     */
    fun setAttach(attach: String): PaymentRequest = copy(attach = attach)

    /**
     * 链式调用：设置通知 URL
     */
    fun setNotifyUrl(notifyUrl: String): PaymentRequest = copy(notifyUrl = notifyUrl)

    /**
     * 链式调用：设置交易过期时间
     */
    fun setRequestTimeout(requestTimeout: Long): PaymentRequest = copy(requestTimeout = requestTimeout)

    /**
     * 获取交易类型枚举
     *
     * @return TransactionAction? 对应的枚举，如果无法识别则返回 null
     */
    fun getActionEnum(): TransactionAction? = TransactionAction.fromValue(action)

    companion object {
        /**
         * 创建一个新的 PaymentRequest 构建器
         *
         * 使用示例（推荐使用枚举）：
         * ```kotlin
         * val request = PaymentRequest.builder()
         *     .setAction(TransactionAction.SALE)
         *     .setReferenceOrderId("ORDER001")
         *     .setTransactionRequestId("TXN001")
         *     .setAmount(amountInfo)
         *     .setDescription("商品购买")
         *     .build()
         * ```
         */
        @JvmStatic
        fun builder(): Builder = Builder()
    }

    /**
     * PaymentRequest 构建器
     *
     * 提供更灵活的构建方式，特别适合 Java 调用
     */
    class Builder {
        private var action: String = ""
        private var referenceOrderId: String = ""
        private var transactionRequestId: String = ""
        private var description: String? = null
        private var amount: AmountInfo? = null
        private var paymentMethod: PaymentMethodInfo? = null
        private var staffInfo: StaffInfo? = null
        private var deviceInfo: DeviceInfo? = null
        private var goodsDetail: List<GoodsDetail>? = null
        private var originalTransactionId: String? = null
        private var originalTransactionRequestId: String? = null
        private var tipAmount: BigDecimal? = null
        private var forcedAuthCode: String? = null
        private var reason: String? = null
        private var attach: String? = null
        private var notifyUrl: String? = null
        private var requestTimeout: Long? = null

        /**
         * 设置交易类型（使用枚举，推荐）
         */
        fun setAction(action: TransactionAction) = apply { this.action = action.value }

        /**
         * 设置交易类型（使用字符串，向后兼容）
         */
        fun setAction(action: String) = apply { this.action = action }
        fun setReferenceOrderId(referenceOrderId: String) = apply { this.referenceOrderId = referenceOrderId }
        fun setTransactionRequestId(transactionRequestId: String) = apply { this.transactionRequestId = transactionRequestId }
        fun setDescription(description: String) = apply { this.description = description }
        fun setAmount(amount: AmountInfo) = apply { this.amount = amount }
        fun setPaymentMethod(paymentMethod: PaymentMethodInfo) = apply { this.paymentMethod = paymentMethod }
        fun setStaffInfo(staffInfo: StaffInfo) = apply { this.staffInfo = staffInfo }
        fun setDeviceInfo(deviceInfo: DeviceInfo) = apply { this.deviceInfo = deviceInfo }
        fun setGoodsDetail(goodsDetail: List<GoodsDetail>) = apply { this.goodsDetail = goodsDetail }
        fun setOriginalTransactionId(originalTransactionId: String) = apply { this.originalTransactionId = originalTransactionId }
        fun setOriginalTransactionRequestId(originalTransactionRequestId: String) =
            apply { this.originalTransactionRequestId = originalTransactionRequestId }

        fun setTipAmount(tipAmount: BigDecimal) = apply { this.tipAmount = tipAmount }
        fun setForcedAuthCode(forcedAuthCode: String) = apply { this.forcedAuthCode = forcedAuthCode }
        fun setReason(reason: String) = apply { this.reason = reason }
        fun setAttach(attach: String) = apply { this.attach = attach }
        fun setNotifyUrl(notifyUrl: String) = apply { this.notifyUrl = notifyUrl }
        fun setRequestTimeout(requestTimeout: Long) = apply { this.requestTimeout = requestTimeout }

        /**
         * 构建 PaymentRequest 对象
         */
        fun build(): PaymentRequest {
            require(action.isNotEmpty()) { "action 是必需的" }
            require(referenceOrderId.isNotEmpty()) { "referenceOrderId 是必需的" }
            require(transactionRequestId.isNotEmpty()) { "transactionRequestId 是必需的" }

            return PaymentRequest(
                action = action,
                referenceOrderId = referenceOrderId,
                transactionRequestId = transactionRequestId,
                description = description,
                amount = amount,
                paymentMethod = paymentMethod,
                staffInfo = staffInfo,
                deviceInfo = deviceInfo,
                goodsDetail = goodsDetail,
                originalTransactionId = originalTransactionId,
                originalTransactionRequestId = originalTransactionRequestId,
                tipAmount = tipAmount,
                forcedAuthCode = forcedAuthCode,
                reason = reason,
                attach = attach,
                notifyUrl = notifyUrl,
                requestTimeout = requestTimeout
            )
        }
    }
}

