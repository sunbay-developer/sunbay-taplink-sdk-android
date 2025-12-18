package com.sunmi.tapro.taplink.demo.util

import java.util.regex.Pattern

/**
 * Network utility class
 * 
 * Provides network-related validation functions for LAN mode configuration
 */
object NetworkUtils {
    
    /**
     * Validate IP address format
     * 
     * @param ip IP address string
     * @return true if valid IPv4 address format, false otherwise
     */
    fun isValidIpAddress(ip: String): Boolean {
        if (ip.isBlank()) return false
        
        val pattern = Pattern.compile(
            "^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$"
        )
        return pattern.matcher(ip).matches()
    }
    
    /**
     * Validate port number range
     * 
     * @param port Port number
     * @return true if port is in valid range (1-65535), false otherwise
     */
    fun isPortValid(port: Int): Boolean {
        return port in 1..65535
    }
    
    /**
     * Validate port number string
     * 
     * @param portStr Port number string
     * @return true if port string is valid and in range, false otherwise
     */
    fun isPortValid(portStr: String): Boolean {
        return try {
            val port = portStr.toInt()
            isPortValid(port)
        } catch (e: NumberFormatException) {
            false
        }
    }
}