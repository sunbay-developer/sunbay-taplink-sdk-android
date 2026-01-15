package com.sunmi.tapro.taplink.communication.enums

/**
 * 错误码分类枚举
 *
 * 根据错误码范围对错误进行分类，便于错误处理和统计
 *
 * 分类说明：
 * - INITIALIZATION: 20x - SDK初始化相关错误
 * - CONNECTION: 21x, 23x, 24x, 25x - 连接状态和连接失败错误
 * - AUTHENTICATION: 221 - 认证失败错误
 * - TRANSACTION: 30x, 399 - 交易处理相关错误
 *
 * @author TaPro Team
 * @since 2025-01-14
 */
enum class ErrorCategory(
    /** 分类名称 */
    val displayName: String,
    /** 分类描述 */
    val description: String
) {
    /** 初始化错误 */
    INITIALIZATION(
        displayName = "初始化错误",
        description = "SDK初始化相关错误"
    ),
    
    /** 连接错误 */
    CONNECTION(
        displayName = "连接错误",
        description = "连接状态和连接失败错误"
    ),
    
    /** 认证错误 */
    AUTHENTICATION(
        displayName = "认证错误",
        description = "认证失败错误"
    ),
    
    /** 交易错误 */
    TRANSACTION(
        displayName = "交易错误",
        description = "交易处理相关错误"
    ),
    
    /** 未知错误 */
    UNKNOWN(
        displayName = "未知错误",
        description = "未分类的错误码"
    );
    
    companion object {
        /**
         * T系列错误码到新错误码的映射表（向后兼容）
         */
        private val tSeriesToNewCodeMapping = mapOf(
            // 初始化错误 (20x)
            "T01" to "201",  // SDK未初始化
            "T14" to "202",  // VSP客户端未就绪
            "T20" to "203",  // 无可用服务内核
            "T25" to "282",  // 交易接口为空 -> 线缆模式错误
            "T31" to "287",  // 初始化失败：系统繁忙 -> USB设备忙碌
            
            // 连接错误 (21x, 23x, 24x, 25x, 27x, 28x, 29x)
            "T09" to "211",  // 连接已存在
            "T07" to "212",  // 未建立连接
            "T37" to "212",  // 未建立连接
            "T06" to "213",  // 连接已断开
            "T08" to "214",  // 手动断开连接
            "T41" to "221",  // 连接认证失败
            "T02" to "231",  // Tapro应用未安装
            "T03" to "232",  // IPC服务绑定失败
            "T04" to "251",  // WebSocket连接失败
            "T05" to "271",  // mDNS服务发现失败
            "T35" to "281",  // 线缆未连接
            "T11" to "252",  // USB权限被拒绝
            "T12" to "253",  // USB设备连接超时
            "T10" to "254",  // 无效的USB协议
            "T13" to "255",  // 串口设备未就绪
            
            // 交易错误 (30x, 399)
            "T15" to "301",  // 缺少请求参数
            "T16" to "302",  // JSON格式错误
            "T17" to "303",  // 不支持的交易类型
            "T18" to "301",  // 缺少请求参数（金额参数无效）
            "T19" to "254",  // USB协议不支持（协议类型不匹配）
            "T28" to "303",  // 不支持的交易类型
            "T21" to "304",  // 请求发送失败
            "T23" to "304",  // 请求发送失败
            "T22" to "304",  // 请求发送失败（无法发送数据未连接）
            "T24" to "304",  // 请求发送失败（发送数据时发生意外错误）
            "T27" to "331",  // 交易锁定
            "T30" to "331",  // 交易锁定
            "T34" to "332",  // Tapro响应超时
            "T38" to "333",  // Host交易拒绝
            "T39" to "334",  // Host交易处理中
            "T29" to "335",  // 交易已终止
            "T40" to "336"   // 终止失败
        )
        
        /**
         * 根据错误码获取对应的错误分类
         *
         * @param code 错误码（字符串格式，如 "201", "T01"）
         * @return 错误分类
         */
        fun fromCode(code: String?): ErrorCategory {
            if (code.isNullOrBlank()) {
                return UNKNOWN
            }
            
            val upperCode = code.uppercase()
            
            // 处理T系列错误码：先转换为新错误码，再根据新错误码分类
            val actualCode = tSeriesToNewCodeMapping[upperCode] ?: code
            
            // 处理数字错误码
            val codeInt = actualCode.toIntOrNull() ?: return UNKNOWN
            
            // 根据错误码范围判断分类
            return when {
                // 初始化错误 (20x，排除204和209，它们已移到线缆模式)
                codeInt in 201..203 -> INITIALIZATION
                
                // 连接错误 (21x, 23x, 24x, 25x, 27x, 28x)
                codeInt in 211..220 -> CONNECTION  // 通用连接管理错误
                codeInt in 231..250 -> CONNECTION  // APP_TO_APP模式错误
                codeInt in 251..280 -> CONNECTION  // LAN模式错误
                codeInt in 281..310 -> CONNECTION  // CABLE模式错误
                
                // 认证错误 (221)
                codeInt == 221 -> AUTHENTICATION
                
                // 交易错误 (30x, 399)
                codeInt in 301..399 -> TRANSACTION
                
                // 其他情况
                else -> UNKNOWN
            }
        }
    }
}
