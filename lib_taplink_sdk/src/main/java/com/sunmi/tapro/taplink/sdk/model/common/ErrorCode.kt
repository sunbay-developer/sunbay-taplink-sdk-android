//package com.sunmi.tapro.taplink.sdk.model.common
//
///**
// * 错误码定义
// *
// * 根据 Taplink SDK 文档定义的所有错误码及其说明
// *
// * @author TaPro Team
// * @since 2025-01-XX
// */
//sealed class ErrorCode(
//    /** 错误码 */
//    open val code: String,
//
//    /** 错误说明 */
//    open val description: String,
//
//    /** 解决方案（可选） */
//    open val solution: String? = null
//) {
//    // ==================== 成功响应 ====================
//    object Success : ErrorCode("0", "接口响应成功", "接口响应成功，不代表交易成功。需检查交易状态和交易结果码以确定实际交易状态")
//
//    // ==================== 安全限制错误（S 系列） ====================
//    object S01 : ErrorCode("S01", "IP 地址无效", "检查商户是否配置了 IP 白名单，并将调用服务器 IP 添加到白名单")
//    object S02 : ErrorCode("S02", "请求时间戳与服务器时间相差过大", "同步客户端系统时间，确保时间差在允许范围内（±10 分钟）")
//    object S03 : ErrorCode("S03", "签名验证失败", "检查签名算法、密钥和参数顺序，确认签名参数未被修改，重新生成签名")
//    object S04 : ErrorCode("S04", "API 调用频率超限", "降低调用频率，遵循平台频率限制规则")
//    object S05 : ErrorCode("S05", "数据解密失败", "检查代码加密逻辑或密钥设置")
//    object S06 : ErrorCode("S06", "权限不足", "联系管理员申请权限")
//    object S07 : ErrorCode("S07", "TMK 密钥不存在或已过期", "重新获取 TMK")
//    object S08 : ErrorCode("S08", "工作密钥不存在或已过期", "重新获取工作密钥")
//    object S09 : ErrorCode("S09", "登录会话过期或未登录", "重新登录")
//
//    // ==================== 客户端错误（C 系列） ====================
//    object C01 : ErrorCode("C01", "缺少请求参数", "确保提供所有必需参数")
//    object C02 : ErrorCode("C02", "请求 JSON 格式错误", "检查 JSON 结构、括号匹配和数据类型有效性")
//    object C03 : ErrorCode("C03", "商户（merchantId）不存在或已禁用", "检查商户 ID 是否正确并确认商户状态")
//    object C04 : ErrorCode("C04", "门店（storeId）不存在或已禁用", "检查门店 ID 是否正确并确认门店状态")
//    object C05 : ErrorCode("C05", "设备（sn）不存在", "检查设备 SN 是否正确并确认设备绑定")
//    object C06 : ErrorCode("C06", "重复的请求序列号", "确保商户请求号的唯一性")
//    object C07 : ErrorCode("C07", "重复的商户订单号", "确保商户订单号的唯一性")
//    object C08 : ErrorCode("C08", "交易锁定（防并发操作）", "等待当前操作完成后再发起新请求")
//    object C09 : ErrorCode("C09", "订单未支付", "检查订单号和订单状态")
//    object C10 : ErrorCode("C10", "订单已支付", "通过订单查询接口检查订单状态")
//    object C11 : ErrorCode("C11", "订单已关闭", "重新创建订单并发起请求")
//    object C12 : ErrorCode("C12", "订单不存在", "检查订单号是否正确且订单属于商户")
//    object C13 : ErrorCode("C13", "不支持的定价货币", "检查商户支持的定价货币")
//    object C14 : ErrorCode("C14", "交易金额超出允许范围", "检查平台金额限制规则，调整请求金额")
//    object C15 : ErrorCode("C15", "退款金额超出原订单金额", "验证原订单金额和已退款金额")
//    object C16 : ErrorCode("C16", "请求参数为空", "确保非空参数设置了正确的值")
//    object C17 : ErrorCode("C17", "请求参数格式错误", "确保所有值按文档规范填写")
//    object C18 : ErrorCode("C18", "请求接口不存在", "检查接口 API 地址")
//    object C19 : ErrorCode("C19", "不支持的 HTTP 方法", "检查接口 HTTP 方法")
//    object C20 : ErrorCode("C20", "SDK 未初始化", "在调用 connect() 之前先调用 TaplinkSDK.init() 初始化 SDK")
//    object C21 : ErrorCode("C21", "设备不存在", "完成设备激活或在平台上绑定")
//    object C22 : ErrorCode("C22", "Tapro 应用未安装（应用间模式）", "在设备上安装 Tapro 应用")
//    object C23 : ErrorCode("C23", "线缆未物理连接（线缆模式）", "检查 USB 或串口线缆的物理连接")
//    object C24 : ErrorCode("C24", "不支持的线缆协议（线缆模式）", "使用支持的线缆协议（USB AOA/USB-VSP/RS232）或启用自动检测")
//    object C25 : ErrorCode("C25", "IPC 服务绑定失败（应用间模式）", "检查 Tapro 应用是否正常运行，重启应用或设备")
//    object C26 : ErrorCode("C26", "IPC 服务连接超时（应用间模式）", "检查 Tapro 应用状态，确保应用未被系统杀死")
//    object C27 : ErrorCode("C27", "线缆连接超时（线缆模式）", "检查线缆连接状态，重新插拔线缆或更换线缆")
//    object C28 : ErrorCode("C28", "线缆通信错误（线缆模式）", "检查线缆质量和连接稳定性，尝试更换线缆")
//    object C29 : ErrorCode("C29", "设备未配对（局域网模式）", "首次连接需要手动输入 IP 和端口进行配对")
//    object C30 : ErrorCode("C30", "网络连接失败（局域网模式）", "检查网络连接，确保设备在同一网段")
//    object C31 : ErrorCode("C31", "网络连接超时（局域网模式）", "检查网络状态，确认支付终端在线")
//    object C32 : ErrorCode("C32", "TLS 握手失败（局域网模式）", "检查证书配置，确认 SDK 配置信任自签名证书")
//    object C33 : ErrorCode("C33", "WebSocket 连接失败（局域网模式）", "检查网络防火墙设置，确保端口 8443-8453 未被阻止")
//    object C34 : ErrorCode("C34", "设备离线（局域网模式）", "检查支付终端网络连接和电源状态")
//    object C35 : ErrorCode("C35", "mDNS 发现失败（局域网模式）", "检查网络是否支持 mDNS，或手动输入 IP 地址")
//    object C36 : ErrorCode("C36", "连接已断开", "检查连接状态，调用 connect() 重新建立连接")
//    object C37 : ErrorCode("C37", "未建立连接", "在发起交易前先调用 connect() 建立连接")
//    object C38 : ErrorCode("C38", "不支持的交易类型（Action）", "检查请求的 action 是否正确且被当前设备支持")
//    object C39 : ErrorCode("C39", "交易被取消", "交易已被用户或系统取消，无需重试")
//    object C40 : ErrorCode("C40", "应用初始化失败", "检查初始化参数配置，确认网络连接和权限设置，重新尝试初始化")
//
//    // ==================== 商户配置错误（M 系列） ====================
//    object M01 : ErrorCode("M01", "接口调用权限不足或未激活", "申请相应接口权限或续期")
//    object M02 : ErrorCode("M02", "应用（appId）不存在或已禁用", "检查 appId 是否正确并确认应用状态")
//    object M03 : ErrorCode("M03", "应用和商户绑定关系不匹配", "确认应用已正确授权给此商户")
//    object M04 : ErrorCode("M04", "商户未激活此支付产品", "确认商户已激活支付产品并完成配置")
//
//    // ==================== 支付渠道错误（P 系列） ====================
//    object P01 : ErrorCode("P01", "银行渠道异常", "调用订单查询接口确认交易状态，如未支付可重试，仍失败请联系技术支持")
//
//    // ==================== 系统错误（E 系列） ====================
//    object E01 : ErrorCode("E01", "系统繁忙，请稍后重试", "调用订单查询接口确认交易状态，如未支付可重试，仍失败请联系技术支持")
//    object E02 : ErrorCode("E02", "网络错误", "调用订单查询接口确认交易状态，如未支付可重试，仍失败请联系技术支持")
//    object E03 : ErrorCode("E03", "未知异常", "调用订单查询接口确认交易状态，如未支付可重试，仍失败请联系技术支持")
//
//    // ==================== 风控拦截（R 系列） ====================
//    object R01 : ErrorCode("R01", "风控系统拒绝交易", "检查交易行为是否异常，联系平台风控团队")
//
//    // ==================== 其他错误码 ====================
//    object Exception : ErrorCode("E04", "系统异常", "捕获系统抛出的异常，并反馈给调用者")
//    object Timeout : ErrorCode("996", "系统超时", "调用订单查询接口确认交易状态，如未支付可重试，仍失败请联系技术支持")
//
//    // ==================== 未知错误码 ====================
//    data class Unknown(
//        private val errorCode: String,
//        private val errorDescription: String = "未知错误码",
//        private val errorSolution: String? = null
//    ) : ErrorCode(errorCode, errorDescription, errorSolution) {
//        override val code: String
//            get() = errorCode
//
//        override val description: String
//            get() = errorDescription
//
//        override val solution: String?
//            get() = errorSolution
//    }
//
//    companion object {
//        /**
//         * 根据错误码字符串获取对应的 ErrorCode 对象
//         *
//         * @param code 错误码字符串
//         * @return ErrorCode 对象，如果未找到则返回 Unknown
//         */
//        fun fromCode(code: String?): ErrorCode {
//            if (code.isNullOrBlank()) {
//                return Unknown("", "错误码为空")
//            }
//
//            return when (code.uppercase()) {
//                // 成功响应
//                "0" -> Success
//
//                // 安全限制错误（S 系列）
//                "S01" -> S01
//                "S02" -> S02
//                "S03" -> S03
//                "S04" -> S04
//                "S05" -> S05
//                "S06" -> S06
//                "S07" -> S07
//                "S08" -> S08
//                "S09" -> S09
//
//                // 客户端错误（C 系列）
//                "C01" -> C01
//                "C02" -> C02
//                "C03" -> C03
//                "C04" -> C04
//                "C05" -> C05
//                "C06" -> C06
//                "C07" -> C07
//                "C08" -> C08
//                "C09" -> C09
//                "C10" -> C10
//                "C11" -> C11
//                "C12" -> C12
//                "C13" -> C13
//                "C14" -> C14
//                "C15" -> C15
//                "C16" -> C16
//                "C17" -> C17
//                "C18" -> C18
//                "C19" -> C19
//                "C20" -> C20
//                "C21" -> C21
//                "C22" -> C22
//                "C23" -> C23
//                "C24" -> C24
//                "C25" -> C25
//                "C26" -> C26
//                "C27" -> C27
//                "C28" -> C28
//                "C29" -> C29
//                "C30" -> C30
//                "C31" -> C31
//                "C32" -> C32
//                "C33" -> C33
//                "C34" -> C34
//                "C35" -> C35
//                "C36" -> C36
//                "C37" -> C37
//                "C38" -> C38
//                "C39" -> C39
//                "C40" -> C40
//
//                // 商户配置错误（M 系列）
//                "M01" -> M01
//                "M02" -> M02
//                "M03" -> M03
//                "M04" -> M04
//
//                // 支付渠道错误（P 系列）
//                "P01" -> P01
//
//                // 系统错误（E 系列）
//                "E01" -> E01
//                "E02" -> E02
//                "E03" -> E03
//                "E04" -> Exception
//
//                // 风控拦截（R 系列）
//                "R01" -> R01
//
//                // 其他错误码
//                "996" -> Timeout
//
//                // 未知错误码
//                else -> Unknown(code, "未知错误码: $code")
//            }
//        }
//
//        /**
//         * 获取所有预定义的错误码列表
//         *
//         * @return 所有预定义的错误码列表
//         */
//        fun getAllErrorCodes(): List<ErrorCode> {
//            return listOf(
//                Success,
//                S01, S02, S03, S04, S05, S06, S07, S08, S09,
//                C01, C02, C03, C04, C05, C06, C07, C08, C09, C10,
//                C11, C12, C13, C14, C15, C16, C17, C18, C19, C20,
//                C21, C22, C23, C24, C25, C26, C27, C28, C29, C30,
//                C31, C32, C33, C34, C35, C36, C37, C38, C39, C40,
//                M01, M02, M03, M04,
//                P01,
//                E01, E02, E03, Exception,
//                R01,
//                Timeout
//            )
//        }
//
//        /**
//         * 判断错误码是否为客户端错误（C 系列）
//         */
//        fun isClientError(code: String?): Boolean {
//            return code?.uppercase()?.startsWith("C") == true
//        }
//
//        /**
//         * 判断错误码是否为系统错误（E 系列）
//         */
//        fun isSystemError(code: String?): Boolean {
//            return code?.uppercase()?.startsWith("E") == true
//        }
//
//        /**
//         * 判断错误码是否为安全限制错误（S 系列）
//         */
//        fun isSecurityError(code: String?): Boolean {
//            return code?.uppercase()?.startsWith("S") == true
//        }
//
//        /**
//         * 判断错误码是否为商户配置错误（M 系列）
//         */
//        fun isMerchantConfigError(code: String?): Boolean {
//            return code?.uppercase()?.startsWith("M") == true
//        }
//
//        /**
//         * 判断错误码是否为支付渠道错误（P 系列）
//         */
//        fun isPaymentChannelError(code: String?): Boolean {
//            return code?.uppercase()?.startsWith("P") == true
//        }
//
//        /**
//         * 判断错误码是否为风控拦截错误（R 系列）
//         */
//        fun isRiskControlError(code: String?): Boolean {
//            return code?.uppercase()?.startsWith("R") == true
//        }
//    }
//}
//
