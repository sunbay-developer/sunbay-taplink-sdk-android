package com.sunmi.tapro.taplink.communication.enums

import com.sunmi.tapro.taplink.communication.util.ErrorStringHelper

/**
 * 错误码分类枚举
 *
 * 根据错误码范围对错误进行分类，便于错误处理和统计
 *
 * 分类说明：
 * - INITIALIZATION: 20x - SDK初始化相关错误
 * - CONNECTION: 21x, 23x, 24x, 25x - 连接状态和连接失败错误
 * - AUTHENTICATION: 221 - 认证失败错误
 * - TRANSACTION: 30x - 交易处理相关错误
 *
 * @author TaPro Team
 * @since 2025-01-14
 */
enum class ErrorCategory {
    /** 初始化错误 */
    INITIALIZATION,
    
    /** 连接错误 */
    CONNECTION,
    
    /** 认证错误 */
    AUTHENTICATION,
    
    /** 交易错误 */
    TRANSACTION,
    
    /** 未知错误 */
    UNKNOWN;
    
    /**
     * 获取分类显示名称（从资源文件获取）
     */
    val displayName: String
        get() = ErrorStringHelper.getCategoryDisplayName(name)
    
    /**
     * 获取分类描述（从资源文件获取）
     */
    val description: String
        get() = ErrorStringHelper.getCategoryDescription(name)
    
    companion object {
        /**
         * 根据错误码获取对应的错误分类
         *
         * @param code 错误码（字符串格式，如 "201"）
         * @return 错误分类
         */
        fun fromCode(code: String?): ErrorCategory {
            if (code.isNullOrBlank()) {
                return UNKNOWN
            }
            
            // 处理数字错误码
            val codeInt = code.toIntOrNull() ?: return UNKNOWN
            
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
                
                // 交易错误 (30x)
                codeInt in 301..399 -> TRANSACTION
                
                // 其他情况
                else -> UNKNOWN
            }
        }
    }
}
