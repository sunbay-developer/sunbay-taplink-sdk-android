package com.sunmi.tapro.taplink.communication.enums

import com.sunmi.tapro.taplink.communication.util.ErrorStringHelper

/**
 * 错误码定义
 *
 * 根据 Taplink SDK 文档定义的所有错误码及其说明
 * 支持多语言本地化，错误描述和解决方案从资源文件中获取
 *
 * @author TaPro Team
 * @since 2025-01-XX
 */
sealed class InnerErrorCode(
    /** 错误码 */
    val code: String
) {
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
    object Success : InnerErrorCode("0")

    // ==================== T 系列错误码 ====================
    // 连接相关错误 (T01-T14)
    object T01 : InnerErrorCode("T01") // SDK not initialized
    object T02 : InnerErrorCode("T02") // Tapro application not installed
    object T03 : InnerErrorCode("T03") // IPC service binding failed
    object T04 : InnerErrorCode("T04") // WebSocket connection failed
    object T05 : InnerErrorCode("T05") // mDNS discovery failed
    object T06 : InnerErrorCode("T06") // Connection disconnected
    object T07 : InnerErrorCode("T07") // No connection established
    object T08 : InnerErrorCode("T08") // Manual disconnect
    object T09 : InnerErrorCode("T09") // Connection already established
    object T10 : InnerErrorCode("T10") // Invalid USB protocol
    object T11 : InnerErrorCode("T11") // USB device access denied
    object T12 : InnerErrorCode("T12") // USB device connection timeout
    object T13 : InnerErrorCode("T13") // Serial device not ready
    object T14 : InnerErrorCode("T14") // VSP client not ready

    // 参数校验错误 (T15-T19)
    object T15 : InnerErrorCode("T15") // Missing request parameters
    object T16 : InnerErrorCode("T16") // Request JSON format error
    object T17 : InnerErrorCode("T17") // Protocol data is invalid or parsing failed
    object T18 : InnerErrorCode("T18") // Invalid amount parameters
    object T19 : InnerErrorCode("T19") // Protocol type mismatch

    // 数据传输错误 (T20-T25)
    object T20 : InnerErrorCode("T20") // No service kernel available
    object T21 : InnerErrorCode("T21") // Failed to send data
    object T22 : InnerErrorCode("T22") // Cannot send data, not connected
    object T23 : InnerErrorCode("T23") // Failed to send data
    object T24 : InnerErrorCode("T24") // Unexpected error sending data
    object T25 : InnerErrorCode("T25") // ITransaction is null

    // 交易处理错误 (T27-T31) - T26 gap reserved
    object T27 : InnerErrorCode("T27") // Transaction locked
    object T28 : InnerErrorCode("T28") // Unsupported transaction type
    object T29 : InnerErrorCode("T29") // Transaction cancelled
    object T30 : InnerErrorCode("T30") // System is processing another transaction
    object T31 : InnerErrorCode("T31") // Init Failed: System is processing another transaction

    // 系统超时错误 (T34) - T32-T33 gap reserved
    object T34 : InnerErrorCode("T34") // Tapro 响应超时

    // 连接状态错误 (T35-T38) - 扩展连接相关错误
    object T35 : InnerErrorCode("T35") // 线缆未连接
    object T37 : InnerErrorCode("T37") // 未建立连接
    object T38 : InnerErrorCode("T38") // host交易拒绝
    
    // 交易处理错误 (T39) - Host交易处理中
    object T39 : InnerErrorCode("T39") // Host交易处理中，需要查询
    
    // 交易处理错误 (T40) - 终止失败
    object T40 : InnerErrorCode("T40") // 终止失败
    
    // 连接认证错误 (T41) - 连接认证失败
    object T41 : InnerErrorCode("T41") // 连接认证失败

    // ==================== 其他错误码 ====================
    object Exception : InnerErrorCode("E04")
    object Timeout : InnerErrorCode("996")

    // ==================== C 系列错误码（向后兼容） ====================
    object C01 : InnerErrorCode("C01") // Missing request parameters -> T15
    object C02 : InnerErrorCode("C02") // Request JSON format error -> T16
    object C08 : InnerErrorCode("C08") // Transaction locked -> T27
    object C20 : InnerErrorCode("C20") // SDK not initialized -> T01
    object C22 : InnerErrorCode("C22") // Tapro application not installed -> T02
    object C25 : InnerErrorCode("C25") // IPC service binding failed -> T03
    object C33 : InnerErrorCode("C33") // WebSocket connection failed -> T04
    object C35 : InnerErrorCode("C35") // mDNS discovery failed -> T05
    object C36 : InnerErrorCode("C36") // Connection disconnected -> T06
    object C37 : InnerErrorCode("C37") // No connection established -> T07
    object C38 : InnerErrorCode("C38") // Unsupported transaction type -> T28
    object C39 : InnerErrorCode("C39") // Transaction cancelled -> T29
    object C41 : InnerErrorCode("C41") // Protocol data is invalid or parsing failed -> T17
    object C42 : InnerErrorCode("C42") // System is processing another transaction -> T30
    object C43 : InnerErrorCode("C43") // Invalid amount parameters -> T18
    object C44 : InnerErrorCode("C44") // Init Failed: System is processing another transaction -> T31
    object C45 : InnerErrorCode("C45") // Manual disconnect -> T08
    object C46 : InnerErrorCode("C46") // No service kernel available -> T20
    object C47 : InnerErrorCode("C47") // Failed to send data -> T21
    object C48 : InnerErrorCode("C48") // Invalid USB protocol -> T10
    object C49 : InnerErrorCode("C49") // USB device access denied -> T11
    object C50 : InnerErrorCode("C50") // USB device connection timeout -> T12
    object C51 : InnerErrorCode("C51") // USB permission request failed -> T11
    object C52 : InnerErrorCode("C52") // USB device not found -> T11
    object C53 : InnerErrorCode("C53") // USB permission denied -> T11
    object C54 : InnerErrorCode("C54") // Failed to open USB device -> T11
    object C55 : InnerErrorCode("C55") // AOA protocol sequence failed -> T10
    object C56 : InnerErrorCode("C56") // USB device disconnected -> T06
    object C57 : InnerErrorCode("C57") // Connection already established -> T09
    object C58 : InnerErrorCode("C58") // Protocol type mismatch -> T19
    object C61 : InnerErrorCode("C61") // Cannot send data, not connected -> T22
    object C62 : InnerErrorCode("C62") // Failed to send data -> T23
    object C63 : InnerErrorCode("C63") // Unexpected error sending data -> T24
    object C65 : InnerErrorCode("C65") // Serial device not ready -> T13
    object C66 : InnerErrorCode("C66") // VSP client not ready -> T14
    object C67 : InnerErrorCode("C67") // ITransaction is null -> T25

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
        fun fromCode(code: String?): InnerErrorCode {
            if (code.isNullOrBlank()) {
                return Unknown("", ErrorStringHelper.getDescription(""))
            }

            return when (code.uppercase()) {
                // 成功响应
                "0" -> Success

                // T 系列错误码
                // 连接相关错误 (T01-T14)
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

                // 参数校验错误 (T15-T19)
                "T15" -> T15
                "T16" -> T16
                "T17" -> T17
                "T18" -> T18
                "T19" -> T19

                // 数据传输错误 (T20-T25)
                "T20" -> T20
                "T21" -> T21
                "T22" -> T22
                "T23" -> T23
                "T24" -> T24
                "T25" -> T25

                // 交易处理错误 (T27-T31)
                "T27" -> T27
                "T28" -> T28
                "T29" -> T29
                "T30" -> T30
                "T31" -> T31

                // 系统超时错误 (T34)
                "T34" -> T34

                // 连接状态错误 (T35-T38)
                "T35" -> T35
                "T37" -> T37
                "T38" -> T38
                
                // 交易处理错误 (T39)
                "T39" -> T39
                
                // 交易处理错误 (T40)
                "T40" -> T40
                
                // 连接认证错误 (T41)
                "T41" -> T41

                // 向后兼容：C系列错误码对象
                "C01" -> C01
                "C02" -> C02
                "C08" -> C08
                "C20" -> C20
                "C22" -> C22
                "C25" -> C25
                "C33" -> C33
                "C35" -> C35
                "C36" -> C36
                "C37" -> C37
                "C38" -> C38
                "C39" -> C39
                "C41" -> C41
                "C42" -> C42
                "C43" -> C43
                "C44" -> C44
                "C45" -> C45
                "C46" -> C46
                "C47" -> C47
                "C48" -> C48
                "C49" -> C49
                "C50" -> C50
                "C51" -> C51
                "C52" -> C52
                "C53" -> C53
                "C54" -> C54
                "C55" -> C55
                "C56" -> C56
                "C57" -> C57
                "C58" -> C58
                "C61" -> C61
                "C62" -> C62
                "C63" -> C63
                "C65" -> C65
                "C66" -> C66
                "C67" -> C67
                "996" -> T34  // Tapro 响应超时

                // 其他错误码
                "E04" -> Exception

                // 未知错误码
                else -> Unknown(code)
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
                // T 系列错误码
                T01, T02, T03, T04, T05, T06, T07, T08, T09, T10,
                T11, T12, T13, T14, T15, T16, T17, T18, T19, T20,
                T21, T22, T23, T24, T25, T27, T28, T29, T30, T31, T34,
                T35, T37, T38, T39, T40, T41,
                // 其他错误码
                Exception, Timeout,
                // C 系列错误码（向后兼容）
                C01, C02, C08, C20, C22, C25, C33, C35, C36, C37,
                C38, C39, C41, C42, C43, C44, C45, C46, C47, C48,
                C49, C50, C51, C52, C53, C54, C55, C56, C57, C58,
                C61, C62, C63, C65, C66, C67
            )
        }

        /**
         * 判断错误码是否为T系列错误码
         */
        fun isTSeriesError(code: String?): Boolean {
            return code?.uppercase()?.startsWith("T") == true
        }

        /**
         * 判断错误码是否为连接相关错误 (T01-T14)
         */
        fun isConnectionError(code: String?): Boolean {
            val upperCode = code?.uppercase()
            return upperCode != null && upperCode.startsWith("T") &&
                    upperCode.substring(1).toIntOrNull()?.let { it in 1..14 } == true
        }

        /**
         * 判断错误码是否为参数校验错误 (T15-T19)
         */
        fun isParameterValidationError(code: String?): Boolean {
            val upperCode = code?.uppercase()
            return upperCode != null && upperCode.startsWith("T") &&
                    upperCode.substring(1).toIntOrNull()?.let { it in 15..19 } == true
        }

        /**
         * 判断错误码是否为数据传输错误 (T20-T25)
         */
        fun isDataTransmissionError(code: String?): Boolean {
            val upperCode = code?.uppercase()
            return upperCode != null && upperCode.startsWith("T") &&
                    upperCode.substring(1).toIntOrNull()?.let { it in 20..25 } == true
        }

        /**
         * 判断错误码是否为交易处理错误 (T27-T31)
         */
        fun isTransactionProcessingError(code: String?): Boolean {
            val upperCode = code?.uppercase()
            return upperCode != null && upperCode.startsWith("T") &&
                    upperCode.substring(1).toIntOrNull()?.let { it in 27..31 } == true
        }

        /**
         * 判断错误码是否为系统超时错误 (T34)
         */
        fun isSystemTimeoutError(code: String?): Boolean {
            return code?.uppercase() == "T34"
        }
    }
}