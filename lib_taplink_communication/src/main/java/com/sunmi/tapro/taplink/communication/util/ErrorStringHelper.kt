package com.sunmi.tapro.taplink.communication.util

import android.content.Context
import com.sunmi.tapro.taplink.communication.TaplinkServiceKernel

/**
 * 错误字符串资源辅助类
 * 
 * 用于从资源文件中获取错误描述和解决方案的本地化字符串
 * 
 * @author TaPro Team
 * @since 2025-01-XX
 */
object ErrorStringHelper {
    
    /**
     * 获取错误描述
     * 
     * @param code 错误码
     * @return 错误描述字符串，如果未找到则返回默认值
     */
    fun getDescription(code: String): String {
        val context = getContext() ?: return getDefaultDescription(code)
        
        val resourceName = "error_${code.lowercase()}_description"
        val resourceId = getStringResourceId(context, resourceName)
        
        return if (resourceId != 0) {
            context.getString(resourceId)
        } else {
            getDefaultDescription(code)
        }
    }
    
    /**
     * 获取解决方案
     * 
     * @param code 错误码
     * @return 解决方案字符串，如果未找到则返回null
     */
    fun getSolution(code: String): String? {
        val context = getContext() ?: return getDefaultSolution(code)
        
        val resourceName = "error_${code.lowercase()}_solution"
        val resourceId = getStringResourceId(context, resourceName)
        
        return if (resourceId != 0) {
            context.getString(resourceId)
        } else {
            getDefaultSolution(code)
        }
    }
    
    /**
     * 获取应用上下文
     */
    private fun getContext(): Context? {
        return TaplinkServiceKernel.getInstance()?.getContext()
    }
    
    /**
     * 获取字符串资源ID
     * 
     * @param context 上下文
     * @param resourceName 资源名称
     * @return 资源ID，如果未找到则返回0
     */
    private fun getStringResourceId(context: Context, resourceName: String): Int {
        return try {
            context.resources.getIdentifier(
                resourceName,
                "string",
                context.packageName
            )
        } catch (e: Exception) {
            0
        }
    }
    
    /**
     * 获取默认错误描述（当资源文件不可用时的后备方案）
     */
    private fun getDefaultDescription(code: String): String {
        return when (code.uppercase()) {
            "" -> "UNMATCHED ERROR"
            else -> "Unknown error code: $code"
        }
    }
    
    /**
     * 获取默认解决方案（当资源文件不可用时的后备方案）
     */
    private fun getDefaultSolution(code: String): String? {
        return null // 当资源文件不可用时，不提供默认解决方案
    }
}