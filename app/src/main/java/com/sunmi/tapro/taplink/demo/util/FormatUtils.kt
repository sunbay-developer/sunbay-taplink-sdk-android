package com.sunmi.tapro.taplink.demo.util

import java.math.BigDecimal
import java.text.DecimalFormat

/**
 * Utility class for common formatting operations
 * 
 * Eliminates code duplication by providing standardized formatting methods
 */
object FormatUtils {

    private val currencyFormatter = DecimalFormat("$#,##0.00")
    private val amountFormatter = DecimalFormat("#,##0.00")

    /**
     * Format amount as currency with dollar sign
     */
    fun formatCurrency(amount: BigDecimal?): String {
        return if (amount != null) {
            currencyFormatter.format(amount)
        } else {
            "$0.00"
        }
    }

    /**
     * Format amount as currency with dollar sign (Double version)
     */
    fun formatCurrency(amount: Double?): String {
        return if (amount != null) {
            String.format("$%.2f", amount)
        } else {
            "$0.00"
        }
    }

    /**
     * Format amount without currency symbol
     */
    fun formatAmount(amount: BigDecimal?): String {
        return if (amount != null) {
            amountFormatter.format(amount)
        } else {
            "0.00"
        }
    }

    /**
     * Format amount without currency symbol (Double version)
     */
    fun formatAmount(amount: Double?): String {
        return if (amount != null) {
            String.format("%.2f", amount)
        } else {
            "0.00"
        }
    }

    /**
     * Format transaction ID with prefix if needed
     */
    fun formatTransactionId(transactionId: String?): String {
        return transactionId ?: "N/A"
    }

    /**
     * Format timestamp to readable date string
     */
    fun formatTimestamp(timestamp: Long): String {
        return java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
            .format(java.util.Date(timestamp))
    }

    /**
     * Format error message with code
     */
    fun formatErrorMessage(message: String, code: String?): String {
        return if (code != null) {
            "$message\n\nError Code: $code"
        } else {
            message
        }
    }

    /**
     * Format connection status display text
     */
    fun formatConnectionStatus(status: String, version: String?): String {
        return if (version != null && version.isNotEmpty()) {
            "${Constants.Messages.VERSION_PREFIX}$version"
        } else {
            status
        }
    }

    /**
     * Check if amount is positive and non-zero
     */
    fun isPositiveAmount(amount: BigDecimal?): Boolean {
        return amount != null && amount > BigDecimal.ZERO
    }

    /**
     * Check if amount is positive and non-zero (Double version)
     */
    fun isPositiveAmount(amount: Double?): Boolean {
        return amount != null && amount > 0.0
    }
}