package com.sunmi.tapro.taplink.communication.util

import android.content.Context
import com.sunmi.tapro.taplink.communication.TaplinkServiceKernel

/**
 * Error string resource helper class
 * 
 * Used to retrieve localized strings for error descriptions and solutions from resource files
 * 
 * @author TaPro Team
 * @since 2025-01-XX
 */
object ErrorStringHelper {
    
    /**
     * Get error description
     * 
     * @param code Error code
     * @return Error description string, returns default value if not found
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
     * Get solution
     * 
     * @param code Error code
     * @return Solution string, returns null if not found
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
     * Get application context
     */
    private fun getContext(): Context? {
        return TaplinkServiceKernel.getInstance()?.getContext()
    }
    
    /**
     * Get string resource ID
     * 
     * @param context Context
     * @param resourceName Resource name
     * @return Resource ID, returns 0 if not found
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
     * Get default error description (fallback when resource files are unavailable)
     */
    private fun getDefaultDescription(code: String): String {
        return when (code.uppercase()) {
            "" -> "UNMATCHED ERROR"
            else -> "Unknown error code: $code"
        }
    }
    
    /**
     * Get default solution (fallback when resource files are unavailable)
     */
    private fun getDefaultSolution(code: String): String? {
        return null // Do not provide default solution when resource files are unavailable
    }
    
    /**
     * Get error category display name
     * 
     * @param categoryName Category name (e.g., "initialization", "connection")
     * @return Category display name, returns default value if not found
     */
    fun getCategoryDisplayName(categoryName: String): String {
        val context = getContext() ?: return getDefaultCategoryDisplayName(categoryName)
        
        val resourceName = "category_${categoryName.lowercase()}_display_name"
        val resourceId = getStringResourceId(context, resourceName)
        
        return if (resourceId != 0) {
            context.getString(resourceId)
        } else {
            getDefaultCategoryDisplayName(categoryName)
        }
    }
    
    /**
     * Get error category description
     * 
     * @param categoryName Category name (e.g., "initialization", "connection")
     * @return Category description, returns default value if not found
     */
    fun getCategoryDescription(categoryName: String): String {
        val context = getContext() ?: return getDefaultCategoryDescription(categoryName)
        
        val resourceName = "category_${categoryName.lowercase()}_description"
        val resourceId = getStringResourceId(context, resourceName)
        
        return if (resourceId != 0) {
            context.getString(resourceId)
        } else {
            getDefaultCategoryDescription(categoryName)
        }
    }
    
    /**
     * Get default category display name (fallback when resource files are unavailable)
     */
    private fun getDefaultCategoryDisplayName(categoryName: String): String {
        return when (categoryName.lowercase()) {
            "initialization" -> "Initialization Error"
            "connection" -> "Connection Error"
            "authentication" -> "Authentication Error"
            "transaction" -> "Transaction Error"
            "unknown" -> "Unknown Error"
            else -> "Unknown Category"
        }
    }
    
    /**
     * Get default category description (fallback when resource files are unavailable)
     */
    private fun getDefaultCategoryDescription(categoryName: String): String {
        return when (categoryName.lowercase()) {
            "initialization" -> "SDK initialization related errors"
            "connection" -> "Connection state and connection failure errors"
            "authentication" -> "Authentication failure errors"
            "transaction" -> "Transaction processing related errors"
            "unknown" -> "Unclassified error codes"
            else -> "Unknown error category"
        }
    }
}