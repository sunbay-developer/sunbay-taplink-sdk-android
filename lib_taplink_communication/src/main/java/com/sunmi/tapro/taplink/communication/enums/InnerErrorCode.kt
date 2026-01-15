package com.sunmi.tapro.taplink.communication.enums

import com.sunmi.tapro.taplink.communication.util.ErrorStringHelper

/**
 * 错误码定义
 *
 * 根据 Taplink SDK 集成指南定义的所有错误码及其说明
 * 支持多语言本地化，错误描述和解决方案从资源文件中获取
 *
 * 错误码范围说明：
 * - 100: 成功响应
 * - 20x: 初始化错误
 * - 21x: 连接状态/失败
 * - 23x: 本地模式连接失败
 * - 24x: 局域网模式连接失败
 * - 25x: USB模式连接失败
 * - 30x + 399: 交易处理错误
 *
 * @author TaPro Team
 * @since 2025-01-14
 */
sealed class InnerErrorCode(
    /** 错误码 */
    val code: String
) {
    /**
     * 获取错误码所属的分类
     */
    val category: ErrorCategory
        get() = ErrorCategory.fromCode(code)

    /** 错误说明（从资源文件获取） */
    val description: String
        get() = getDescriptionForCode(code)

    /**
     * 获取错误码对应的描述
     */
    protected open fun getDescriptionForCode(code: String): String {
        return ErrorStringHelper.getDescription(code)
    }

    // ==================== 成功响应 ====================
    /** 100 - 操作成功 */
    object Success : InnerErrorCode("100")

    // ==================== 初始化错误 (20x) ====================
    /** 201 - SDK未初始化 */
    object E201 : InnerErrorCode("201")

    /** 202 - SDK服务异常 */
    object E202 : InnerErrorCode("202")

    /** 203 - Tapro初始化失败 */
    object E203 : InnerErrorCode("203")

    // ==================== 连接状态/失败 (21x) ====================
    /** 211 - 连接已存在，无需重复连接 */
    object E211 : InnerErrorCode("211")

    /** 212 - 设备未连接 */
    object E212 : InnerErrorCode("212")

    /** 213 - 连接已断开 */
    object E213 : InnerErrorCode("213")

    /** 214 - 无法建立连接 */
    object E214 : InnerErrorCode("214")

    /** 221 - 认证失败 */
    object E221 : InnerErrorCode("221")

    // ==================== 本地模式连接失败 (23x) ====================
    /** 231 - Tapro应用未安装 */
    object E231 : InnerErrorCode("231")

    /** 232 - 无法连接到Tapro */
    object E232 : InnerErrorCode("232")

    // ==================== 局域网模式连接失败 (24x) ====================
    /** 241 - 无法连接到服务端 */
    object E241 : InnerErrorCode("241")

    /** 242 - 无法发现服务端 */
    object E242 : InnerErrorCode("242")

    // ==================== USB模式连接失败 (25x) ====================
    /** 251 - 251 USB模式连接失败 */
    object E251 : InnerErrorCode("251")

    /** 252 - USB权限被拒绝 */
    object E252 : InnerErrorCode("252")

    /** 253 - USB连接超时 */
    object E253 : InnerErrorCode("253")

    /** 254 - USB协议不支持 */
    object E254 : InnerErrorCode("254")

    /** 255 - 串口设备未就绪 */
    object E255 : InnerErrorCode("255")

    // ==================== 交易处理错误 (30x + 399) ====================
    /** 301 - 缺少必填参数 */
    object E301 : InnerErrorCode("301")

    /** 302 - 请求数据格式错误 */
    object E302 : InnerErrorCode("302")

    /** 303 - 不支持的交易类型 */
    object E303 : InnerErrorCode("303")

    /** 304 - 请求发送失败 */
    object E304 : InnerErrorCode("304")

    /** 305 - 交易正在进行 */
    object E305 : InnerErrorCode("305")

    /** 306 - 响应超时（需先查询交易状态） */
    object E306 : InnerErrorCode("306")

    /** 307 - 交易被拒绝（必须使用新ID重试） */
    object E307 : InnerErrorCode("307")

    /** 308 - 交易处理中（需先查询交易状态） */
    object E308 : InnerErrorCode("308")

    /** 309 - 交易已终止（不要重试） */
    object E309 : InnerErrorCode("309")

    /** 310 - 余额不足（必须使用新ID重试） */
    object E310 : InnerErrorCode("310")

    /** 311 - 密码错误 */
    object E311 : InnerErrorCode("311")

    /** 312 - 查询交易失败 */
    object E312 : InnerErrorCode("312")

    /** 399 - 交易失败（必须使用新ID重试） */
    object E399 : InnerErrorCode("399")

    // ==================== 向后兼容：旧错误码 ====================
    // T 系列错误码（向后兼容，已映射到新的3位数字错误码）
    @Deprecated("使用新的错误码 E201", ReplaceWith("E201"))
    object T01 : InnerErrorCode("T01") // SDK not initialized -> 201

    @Deprecated("使用新的错误码 E231", ReplaceWith("E231"))
    object T02 : InnerErrorCode("T02") // Tapro application not installed -> 231

    @Deprecated("使用新的错误码 E232", ReplaceWith("E232"))
    object T03 : InnerErrorCode("T03") // IPC service binding failed -> 232

    @Deprecated("使用新的错误码 E251", ReplaceWith("E251"))
    object T04 : InnerErrorCode("T04") // WebSocket connection failed -> 251

    @Deprecated("T系列错误码已废弃，请使用新的错误码系统")
    object T05 : InnerErrorCode("T05") // mDNS discovery failed -> 271 (已移除)

    @Deprecated("使用新的错误码 E213", ReplaceWith("E213"))
    object T06 : InnerErrorCode("T06") // Connection disconnected -> 213

    @Deprecated("使用新的错误码 E212", ReplaceWith("E212"))
    object T07 : InnerErrorCode("T07") // No connection established -> 212

    @Deprecated("使用新的错误码 E214", ReplaceWith("E214"))
    object T08 : InnerErrorCode("T08") // Manual disconnect -> 214

    @Deprecated("使用新的错误码 E211", ReplaceWith("E211"))
    object T09 : InnerErrorCode("T09") // Connection already established -> 211

    @Deprecated("T系列错误码已废弃，请使用新的错误码系统")
    object T10 : InnerErrorCode("T10") // Invalid USB protocol -> 291 (已移除)

    @Deprecated("T系列错误码已废弃，请使用新的错误码系统")
    object T11 : InnerErrorCode("T11") // USB device access denied -> 283 (已移除)

    @Deprecated("T系列错误码已废弃，请使用新的错误码系统")
    object T12 : InnerErrorCode("T12") // USB device connection timeout -> 286 (已移除)

    @Deprecated("T系列错误码已废弃，请使用新的错误码系统")
    object T13 : InnerErrorCode("T13") // Serial device not ready -> 294 (已移除)

    @Deprecated("使用新的错误码 E202", ReplaceWith("E202"))
    object T14 : InnerErrorCode("T14") // VSP client not ready -> 202

    @Deprecated("T系列错误码已废弃，请使用新的错误码系统")
    object T15 : InnerErrorCode("T15") // Missing request parameters -> 311 (已移除)

    @Deprecated("T系列错误码已废弃，请使用新的错误码系统")
    object T16 : InnerErrorCode("T16") // Request JSON format error -> 312 (已移除)

    @Deprecated("T系列错误码已废弃，请使用新的错误码系统")
    object T17 : InnerErrorCode("T17") // Protocol data is invalid or parsing failed -> 313 (已移除)

    @Deprecated("T系列错误码已废弃，请使用新的错误码系统")
    object T18 : InnerErrorCode("T18") // Invalid amount parameters -> 314 (已移除)

    @Deprecated("T系列错误码已废弃，请使用新的错误码系统")
    object T19 : InnerErrorCode("T19") // Protocol type mismatch -> 315 (已移除)

    @Deprecated("使用新的错误码 E203", ReplaceWith("E203"))
    object T20 : InnerErrorCode("T20") // No service kernel available -> 203

    @Deprecated("T系列错误码已废弃，请使用新的错误码系统")
    object T21 : InnerErrorCode("T21") // Failed to send data -> 321 (已移除)

    @Deprecated("T系列错误码已废弃，请使用新的错误码系统")
    object T22 : InnerErrorCode("T22") // Cannot send data, not connected -> 322 (已移除)

    @Deprecated("T系列错误码已废弃，请使用新的错误码系统")
    object T23 : InnerErrorCode("T23") // Failed to send data -> 321 (已移除)

    @Deprecated("T系列错误码已废弃，请使用新的错误码系统")
    object T24 : InnerErrorCode("T24") // Unexpected error sending data -> 323 (已移除)

    @Deprecated("T系列错误码已废弃，请使用新的错误码系统")
    object T25 : InnerErrorCode("T25") // ITransaction is null -> 282 (已移除)

    @Deprecated("T系列错误码已废弃，请使用新的错误码系统")
    object T27 : InnerErrorCode("T27") // Transaction locked -> 331 (已移除)

    @Deprecated("T系列错误码已废弃，请使用新的错误码系统")
    object T28 : InnerErrorCode("T28") // Unsupported transaction type -> 316 (已移除)

    @Deprecated("T系列错误码已废弃，请使用新的错误码系统")
    object T29 : InnerErrorCode("T29") // Transaction cancelled -> 335 (已移除)

    @Deprecated("T系列错误码已废弃，请使用新的错误码系统")
    object T30 : InnerErrorCode("T30") // System is processing another transaction -> 331 (已移除)

    @Deprecated("T系列错误码已废弃，请使用新的错误码系统")
    object T31 : InnerErrorCode("T31") // Init Failed: System is processing another transaction -> 287 (已移除)

    @Deprecated("T系列错误码已废弃，请使用新的错误码系统")
    object T34 : InnerErrorCode("T34") // Tapro 响应超时 -> 332 (已移除)

    @Deprecated("T系列错误码已废弃，请使用新的错误码系统")
    object T35 : InnerErrorCode("T35") // 线缆未连接 -> 281 (已移除)

    @Deprecated("使用新的错误码 E212", ReplaceWith("E212"))
    object T37 : InnerErrorCode("T37") // 未建立连接 -> 212

    @Deprecated("T系列错误码已废弃，请使用新的错误码系统")
    object T38 : InnerErrorCode("T38") // host交易拒绝 -> 333 (已移除)

    @Deprecated("T系列错误码已废弃，请使用新的错误码系统")
    object T39 : InnerErrorCode("T39") // Host交易处理中，需要查询 -> 334 (已移除)

    @Deprecated("T系列错误码已废弃，请使用新的错误码系统")
    object T40 : InnerErrorCode("T40") // 终止失败 -> 336 (已移除)

    @Deprecated("使用新的错误码 E221", ReplaceWith("E221"))
    object T41 : InnerErrorCode("T41") // 连接认证失败 -> 221

    // ==================== 其他错误码 ====================
    object Exception : InnerErrorCode("E04")

    @Deprecated("使用新的错误码 E306", ReplaceWith("E306"))
    object Timeout : InnerErrorCode("996")


    // ==================== 未知错误码 ====================
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
         * 根据错误码字符串获取对应的 ErrorCode 对象
         *
         * @param code 错误码字符串
         * @return ErrorCode 对象，如果未找到则返回 Unknown
         */
        fun fromCode(code: String?, errorMsg: String? = ""): InnerErrorCode {
            if (code.isNullOrBlank()) {
                return Unknown("", ErrorStringHelper.getDescription(""))
            }

            return when (code.uppercase()) {
                // 成功响应
                "0", "100" -> Success

                // 新错误码 (20x-39x)
                // 初始化错误 (20x)
                "201" -> E201
                "202" -> E202
                "203" -> E203

                // 连接状态/失败 (21x)
                "211" -> E211
                "212" -> E212
                "213" -> E213
                "214" -> E214
                "221" -> E221

                // 本地模式连接失败 (23x)
                "231" -> E231
                "232" -> E232

                // 局域网模式连接失败 (24x)
                "241" -> E241
                "242" -> E242
                "251" -> E251

                // USB模式连接失败 (25x)
                "252" -> E252
                "253" -> E253
                "254" -> E254
                "255" -> E255

                // 交易处理错误 (30x + 399)
                "301" -> E301
                "302" -> E302
                "303" -> E303
                "304" -> E304
                "305" -> E305
                "306" -> E306
                "307" -> E307
                "308" -> E308
                "309" -> E309
                "310" -> E310
                "311" -> E311
                "312" -> E312
                "399" -> E399

                // 向后兼容：T 系列错误码
                "T01" -> T01
                "T02" -> T02
                "T03" -> T03
                "T04" -> T04
                "T05" -> T05
                "T06" -> T06
                "T07" -> T07
                "T08" -> T08
                "T09" -> T09
                "T10" -> T10
                "T11" -> T11
                "T12" -> T12
                "T13" -> T13
                "T14" -> T14
                "T15" -> T15
                "T16" -> T16
                "T17" -> T17
                "T18" -> T18
                "T19" -> T19
                "T20" -> T20
                "T21" -> T21
                "T22" -> T22
                "T23" -> T23
                "T24" -> T24
                "T25" -> T25
                "T27" -> T27
                "T28" -> T28
                "T29" -> T29
                "T30" -> T30
                "T31" -> T31
                "T34" -> T34
                "T35" -> T35
                "T37" -> T37
                "T38" -> T38
                "T39" -> T39
                "T40" -> T40
                "T41" -> T41

                "996" -> Timeout

                // 其他错误码
                "E04" -> Exception

                // 未知错误码
                else -> Unknown(code, customDescription = errorMsg)
            }
        }

        /**
         * 获取所有预定义的错误码列表
         *
         * @return 所有预定义的错误码列表
         */
        fun getAllErrorCodes(): List<InnerErrorCode> {
            return listOf(
                Success,
                // 新错误码 (根据集成指南(New))
                E201, E202, E203,
                E211, E212, E213, E214, E221,
                E231, E232,
                E241, E242,
                E251, E252, E253, E254, E255,
                E301, E302, E303, E304, E305, E306, E307, E308, E309, E310, E311, E312, E399,
                // 其他错误码
                Exception, Timeout,
                // T 系列错误码（向后兼容）
                T01, T02, T03, T04, T05, T06, T07, T08, T09, T10,
                T11, T12, T13, T14, T15, T16, T17, T18, T19, T20,
                T21, T22, T23, T24, T25, T27, T28, T29, T30, T31, T34,
                T35, T37, T38, T39, T40, T41
            )
        }

        /**
         * 判断错误码是否为初始化错误 (20x)
         */
        fun isInitializationError(code: String?): Boolean {
            return code in listOf("201", "202", "203")
        }

        /**
         * 判断错误码是否为连接状态/失败错误 (21x)
         */
        fun isConnectionStateError(code: String?): Boolean {
            return code in listOf("211", "212", "213", "214", "221")
        }

        /**
         * 判断错误码是否为本地模式连接失败 (23x)
         */
        fun isLocalModeConnectionError(code: String?): Boolean {
            return code in listOf("231", "232")
        }

        /**
         * 判断错误码是否为局域网模式连接失败 (24x)
         */
        fun isLanModeConnectionError(code: String?): Boolean {
            return code in listOf("241", "242")
        }

        /**
         * 判断错误码是否为USB模式连接失败 (25x)
         */
        fun isUsbModeConnectionError(code: String?): Boolean {
            return code in listOf("251", "252", "253", "254", "255")
        }

        /**
         * 判断错误码是否为交易处理错误 (30x + 399)
         */
        fun isTransactionProcessingError(code: String?): Boolean {
            return code in listOf("301", "302", "303", "304", "305", "306", "307", "308", "309", "310", "311", "312", "399")
        }

        /**
         * 判断是否需要先查询交易状态再重试
         * 错误码 306, 308 需要先查询
         */
        fun needsQueryBeforeRetry(code: String?): Boolean {
            return code in listOf("306", "308")
        }

        /**
         * 判断是否必须使用新的 transactionRequestId 重试
         * 错误码 307, 310, 311, 399 必须使用新ID
         */
        fun mustUseNewTransactionId(code: String?): Boolean {
            return code in listOf("307", "310", "311", "399")
        }

        /**
         * 判断是否可以使用相同的 transactionRequestId 重试
         * 错误码 301-305 可以使用相同ID
         */
        fun canRetryWithSameId(code: String?): Boolean {
            return code in listOf("301", "302", "303", "304", "305")
        }

        /**
         * 判断是否不应该重试
         * 错误码 309 表示交易已终止，不应该重试
         */
        fun shouldNotRetry(code: String?): Boolean {
            return code == "309"
        }

        // ==================== 向后兼容的辅助方法 ====================

        /**
         * 判断错误码是否为T系列错误码
         */
        @Deprecated("T系列错误码已废弃，请使用新的错误码分类方法")
        fun isTSeriesError(code: String?): Boolean {
            return code?.uppercase()?.startsWith("T") == true
        }

        /**
         * 判断错误码是否为连接相关错误 (T01-T14)
         */
        @Deprecated("使用 isConnectionStateError, isLocalModeConnectionError, isLanModeConnectionError, isUsbModeConnectionError")
        fun isConnectionError(code: String?): Boolean {
            val upperCode = code?.uppercase()
            return upperCode != null && upperCode.startsWith("T") &&
                    upperCode.substring(1).toIntOrNull()?.let { it in 1..14 } == true
        }

        /**
         * 判断错误码是否为参数校验错误 (T15-T19)
         */
        @Deprecated("使用 isTransactionProcessingError")
        fun isParameterValidationError(code: String?): Boolean {
            val upperCode = code?.uppercase()
            return upperCode != null && upperCode.startsWith("T") &&
                    upperCode.substring(1).toIntOrNull()?.let { it in 15..19 } == true
        }

        /**
         * 判断错误码是否为数据传输错误 (T20-T25)
         */
        @Deprecated("使用 isTransactionProcessingError")
        fun isDataTransmissionError(code: String?): Boolean {
            val upperCode = code?.uppercase()
            return upperCode != null && upperCode.startsWith("T") &&
                    upperCode.substring(1).toIntOrNull()?.let { it in 20..25 } == true
        }

        /**
         * 判断错误码是否为系统超时错误 (T34)
         */
        @Deprecated("使用 needsQueryBeforeRetry")
        fun isSystemTimeoutError(code: String?): Boolean {
            return code?.uppercase() == "T34"
        }
    }
}