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

    // ==================== 安全限制错误（S 系列） ====================
    object S01 : InnerErrorCode("S01")
    object S02 : InnerErrorCode("S02")
    object S03 : InnerErrorCode("S03")
    object S04 : InnerErrorCode("S04")
    object S05 : InnerErrorCode("S05")
    object S06 : InnerErrorCode("S06")
    object S07 : InnerErrorCode("S07")
    object S08 : InnerErrorCode("S08")
    object S09 : InnerErrorCode("S09")

    // ==================== 客户端错误（C 系列） ====================
    object C01 : InnerErrorCode("C01")
    object C02 : InnerErrorCode("C02")
    object C03 : InnerErrorCode("C03")
    object C04 : InnerErrorCode("C04")
    object C05 : InnerErrorCode("C05")
    object C06 : InnerErrorCode("C06")
    object C07 : InnerErrorCode("C07")
    object C08 : InnerErrorCode("C08")
    object C09 : InnerErrorCode("C09")
    object C10 : InnerErrorCode("C10")
    object C11 : InnerErrorCode("C11")
    object C12 : InnerErrorCode("C12")
    object C13 : InnerErrorCode("C13")
    object C14 : InnerErrorCode("C14")
    object C15 : InnerErrorCode("C15")
    object C16 : InnerErrorCode("C16")
    object C17 : InnerErrorCode("C17")
    object C18 : InnerErrorCode("C18")
    object C19 : InnerErrorCode("C19")
    object C20 : InnerErrorCode("C20")
    object C21 : InnerErrorCode("C21")
    object C22 : InnerErrorCode("C22")
    object C23 : InnerErrorCode("C23")
    object C24 : InnerErrorCode("C24")
    object C25 : InnerErrorCode("C25")
    object C26 : InnerErrorCode("C26")
    object C27 : InnerErrorCode("C27")
    object C28 : InnerErrorCode("C28")
    object C29 : InnerErrorCode("C29")
    object C30 : InnerErrorCode("C30")
    object C31 : InnerErrorCode("C31")
    object C32 : InnerErrorCode("C32")
    object C33 : InnerErrorCode("C33")
    object C34 : InnerErrorCode("C34")
    object C35 : InnerErrorCode("C35")
    object C36 : InnerErrorCode("C36")
    object C37 : InnerErrorCode("C37")
    object C38 : InnerErrorCode("C38")
    object C39 : InnerErrorCode("C39")
    object C40 : InnerErrorCode("C40")
    object C41 : InnerErrorCode("C41")
    object C42 : InnerErrorCode("C42")

    // ==================== 商户配置错误（M 系列） ====================
    object M01 : InnerErrorCode("M01")
    object M02 : InnerErrorCode("M02")
    object M03 : InnerErrorCode("M03")
    object M04 : InnerErrorCode("M04")

    // ==================== 支付渠道错误（P 系列） ====================
    object P01 : InnerErrorCode("P01")

    // ==================== 系统错误（E 系列） ====================
    object E01 : InnerErrorCode("E01")
    object E02 : InnerErrorCode("E02")
    object E03 : InnerErrorCode("E03")

    // ==================== 风控拦截（R 系列） ====================
    object R01 : InnerErrorCode("R01")

    // ==================== 其他错误码 ====================
    object Exception : InnerErrorCode("E04")
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
        fun fromCode(code: String?): InnerErrorCode {
            if (code.isNullOrBlank()) {
                return Unknown("", ErrorStringHelper.getDescription(""))
            }
            
            return when (code.uppercase()) {
                // 成功响应
                "0" -> Success
                
                // 安全限制错误（S 系列）
                "S01" -> S01
                "S02" -> S02
                "S03" -> S03
                "S04" -> S04
                "S05" -> S05
                "S06" -> S06
                "S07" -> S07
                "S08" -> S08
                "S09" -> S09
                
                // 客户端错误（C 系列）
                "C01" -> C01
                "C02" -> C02
                "C03" -> C03
                "C04" -> C04
                "C05" -> C05
                "C06" -> C06
                "C07" -> C07
                "C08" -> C08
                "C09" -> C09
                "C10" -> C10
                "C11" -> C11
                "C12" -> C12
                "C13" -> C13
                "C14" -> C14
                "C15" -> C15
                "C16" -> C16
                "C17" -> C17
                "C18" -> C18
                "C19" -> C19
                "C20" -> C20
                "C21" -> C21
                "C22" -> C22
                "C23" -> C23
                "C24" -> C24
                "C25" -> C25
                "C26" -> C26
                "C27" -> C27
                "C28" -> C28
                "C29" -> C29
                "C30" -> C30
                "C31" -> C31
                "C32" -> C32
                "C33" -> C33
                "C34" -> C34
                "C35" -> C35
                "C36" -> C36
                "C37" -> C37
                "C38" -> C38
                "C39" -> C39
                "C40" -> C40
                "C41" -> C41
                "C42" -> C42

                // 商户配置错误（M 系列）
                "M01" -> M01
                "M02" -> M02
                "M03" -> M03
                "M04" -> M04
                
                // 支付渠道错误（P 系列）
                "P01" -> P01
                
                // 系统错误（E 系列）
                "E01" -> E01
                "E02" -> E02
                "E03" -> E03
                "E04" -> Exception
                
                // 风控拦截（R 系列）
                "R01" -> R01
                
                // 其他错误码
                "996" -> Timeout
                
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
                S01, S02, S03, S04, S05, S06, S07, S08, S09,
                C01, C02, C03, C04, C05, C06, C07, C08, C09, C10,
                C11, C12, C13, C14, C15, C16, C17, C18, C19, C20,
                C21, C22, C23, C24, C25, C26, C27, C28, C29, C30,
                C31, C32, C33, C34, C35, C36, C37, C38, C39, C40, C41,
                M01, M02, M03, M04,
                P01,
                E01, E02, E03, Exception,
                R01,
                Timeout
            )
        }

        /**
         * 判断错误码是否为客户端错误（C 系列）
         */
        fun isClientError(code: String?): Boolean {
            return code?.uppercase()?.startsWith("C") == true
        }

        /**
         * 判断错误码是否为系统错误（E 系列）
         */
        fun isSystemError(code: String?): Boolean {
            return code?.uppercase()?.startsWith("E") == true
        }

        /**
         * 判断错误码是否为安全限制错误（S 系列）
         */
        fun isSecurityError(code: String?): Boolean {
            return code?.uppercase()?.startsWith("S") == true
        }

        /**
         * 判断错误码是否为商户配置错误（M 系列）
         */
        fun isMerchantConfigError(code: String?): Boolean {
            return code?.uppercase()?.startsWith("M") == true
        }

        /**
         * 判断错误码是否为支付渠道错误（P 系列）
         */
        fun isPaymentChannelError(code: String?): Boolean {
            return code?.uppercase()?.startsWith("P") == true
        }

        /**
         * 判断错误码是否为风控拦截错误（R 系列）
         */
        fun isRiskControlError(code: String?): Boolean {
            return code?.uppercase()?.startsWith("R") == true
        }
    }
}