package com.sunmi.tapro.taplink.demo.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import com.sunmi.tapro.taplink.demo.util.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.util.regex.Pattern

/**
 * Network utility class
 * 
 * Provides network-related validation functions for LAN mode configuration
 */
object NetworkUtils {
    
    /**
     * Check if device is connected to network
     * 
     * @param context Android Context
     * @return true if network is connected, false otherwise
     */
    fun isNetworkConnected(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork ?: return false
            val networkCapabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
            
            networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) &&
            (networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
             networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
             networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET))
        } else {
            @Suppress("DEPRECATION")
            val networkInfo = connectivityManager.activeNetworkInfo
            networkInfo?.isConnected == true
        }
    }
    
    /**
     * Get network connection type
     * 
     * @param context Android Context
     * @return Network type string (WIFI, CELLULAR, ETHERNET, NONE)
     */
    fun getNetworkType(context: Context): String {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork ?: return "NONE"
            val networkCapabilities = connectivityManager.getNetworkCapabilities(network) ?: return "NONE"
            
            when {
                networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WIFI"
                networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "CELLULAR"
                networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ETHERNET"
                else -> "UNKNOWN"
            }
        } else {
            @Suppress("DEPRECATION")
            val networkInfo = connectivityManager.activeNetworkInfo
            when (networkInfo?.type) {
                ConnectivityManager.TYPE_WIFI -> "WIFI"
                ConnectivityManager.TYPE_MOBILE -> "CELLULAR"
                ConnectivityManager.TYPE_ETHERNET -> "ETHERNET"
                else -> "NONE"
            }
        }
    }
    
    /**
     * Test network connectivity to specific host and port
     * 
     * @param host Target host IP address
     * @param port Target port number
     * @param timeoutMs Connection timeout in milliseconds
     * @return true if connection successful, false otherwise
     */
    suspend fun testConnection(host: String, port: Int, timeoutMs: Int = Constants.getNetworkTestTimeout().toInt()): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(host, port), timeoutMs)
                    true
                }
            } catch (e: IOException) {
                false
            }
        }
    }
    
    /**
     * Get local IP address information
     * 
     * @param context Android Context
     * @return Local IP address string or null if not available
     */
    fun getLocalIpAddress(context: Context): String? {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork ?: return null
            val linkProperties = connectivityManager.getLinkProperties(network) ?: return null
            
            for (linkAddress in linkProperties.linkAddresses) {
                val address = linkAddress.address
                if (!address.isLoopbackAddress && address.address.size == 4) {
                    return address.hostAddress
                }
            }
        }
        
        return null
    }
    
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
    
    /**
     * Check if IP address is in same subnet as local network
     * 
     * @param context Android Context
     * @param targetIp Target IP address to check
     * @return true if likely in same subnet, false otherwise
     */
    fun isInSameSubnet(context: Context, targetIp: String): Boolean {
        val localIp = getLocalIpAddress(context) ?: return false
        
        // Simple subnet check - compare first 3 octets
        val localParts = localIp.split(".")
        val targetParts = targetIp.split(".")
        
        if (localParts.size != 4 || targetParts.size != 4) return false
        
        return localParts[0] == targetParts[0] && 
               localParts[1] == targetParts[1] && 
               localParts[2] == targetParts[2]
    }
}