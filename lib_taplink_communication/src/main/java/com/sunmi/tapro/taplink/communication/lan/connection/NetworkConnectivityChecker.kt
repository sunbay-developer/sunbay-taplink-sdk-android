package com.sunmi.tapro.taplink.communication.lan.connection

import com.sunmi.tapro.taplink.communication.util.LogUtil
import kotlinx.coroutines.*
import java.io.IOException
import java.net.*

/**
 * Network connectivity checker
 * 
 * Used to detect network connectivity to specific hosts, supplementing WebSocket connection status checks
 * 
 * Important notes:
 * - Default methods (isHostReachable, checkConnectivityDetailed) only use ping checks, do not create TCP connections
 * - This design is to avoid interfering with existing WebSocket connections, especially when server only supports single client connection
 * - If TCP connection check is needed, use methods with "WithTcp" suffix, but confirm server supports multiple client connections
 * 
 * @author TaPro Team
 * @since 2025-01-XX
 */
class NetworkConnectivityChecker {
    
    companion object {
        private const val TAG = "NetworkConnectivityChecker"
        private const val DEFAULT_TIMEOUT_MS = 3000
        private const val PING_TIMEOUT_MS = 2000
    }
    
    /**
     * Check network connectivity to specified host
     * 
     * Note: To avoid interfering with existing connections, this method only uses ping checks, does not create TCP connections
     * 
     * @param host Target host
     * @param port Target port (only for logging, actual check uses ping)
     * @param timeoutMs Timeout duration (milliseconds)
     * @return true if reachable, false otherwise
     */
    suspend fun isHostReachable(
        host: String, 
        port: Int, 
        timeoutMs: Int = DEFAULT_TIMEOUT_MS
    ): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                LogUtil.d(TAG, "Checking connectivity to $host:$port with timeout ${timeoutMs}ms (using ping only)")
                
                // Only use ping check to avoid TCP connection interfering with existing connections
                // For servers that only support single client, TCP connection check will cause existing connection to disconnect
                val pingReachable = checkPingConnectivity(host, timeoutMs)
                if (pingReachable) {
                    LogUtil.d(TAG, "Host $host is reachable via ping")
                    return@withContext true
                }
                
                LogUtil.w(TAG, "Host $host is not reachable via ping")
                false
                
            } catch (e: Exception) {
                LogUtil.e(TAG, "Error checking connectivity to $host:$port: ${e.message}")
                false
            }
        }
    }
    
    /**
     * Check network connectivity to specified host (includes TCP check)
     * 
     * Warning: This method will create TCP connection, may interfere with existing connections!
     * Only use when confirmed server supports multiple client connections
     * 
     * @param host Target host
     * @param port Target port
     * @param timeoutMs Timeout duration (milliseconds)
     * @return true if reachable, false otherwise
     */
    suspend fun isHostReachableWithTcp(
        host: String, 
        port: Int, 
        timeoutMs: Int = DEFAULT_TIMEOUT_MS
    ): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                LogUtil.d(TAG, "Checking connectivity to $host:$port with TCP and ping")
                
                // Method 1: Try TCP connection
                val tcpReachable = checkTcpConnectivity(host, port, timeoutMs)
                if (tcpReachable) {
                    LogUtil.d(TAG, "Host $host:$port is reachable via TCP")
                    return@withContext true
                }
                
                // Method 2: Try ping (if TCP fails)
                val pingReachable = checkPingConnectivity(host, PING_TIMEOUT_MS)
                if (pingReachable) {
                    LogUtil.d(TAG, "Host $host is reachable via ping")
                    return@withContext true
                }
                
                LogUtil.w(TAG, "Host $host:$port is not reachable")
                false
                
            } catch (e: Exception) {
                LogUtil.e(TAG, "Error checking connectivity to $host:$port: ${e.message}")
                false
            }
        }
    }
    
    /**
     * Check TCP connectivity
     */
    private suspend fun checkTcpConnectivity(host: String, port: Int, timeoutMs: Int): Boolean {
        return try {
            withTimeout(timeoutMs.toLong()) {
                withContext(Dispatchers.IO) {
                    val socket = Socket()
                    try {
                        socket.connect(InetSocketAddress(host, port), timeoutMs)
                        socket.isConnected
                    } finally {
                        try {
                            socket.close()
                        } catch (e: Exception) {
                            // Ignore close errors
                        }
                    }
                }
            }
        } catch (e: Exception) {
            LogUtil.d(TAG, "TCP connectivity check failed for $host:$port: ${e.message}")
            false
        }
    }
    
    /**
     * Check Ping connectivity
     */
    private suspend fun checkPingConnectivity(host: String, timeoutMs: Int): Boolean {
        return try {
            withTimeout(timeoutMs.toLong()) {
                withContext(Dispatchers.IO) {
                    val address = InetAddress.getByName(host)
                    address.isReachable(timeoutMs)
                }
            }
        } catch (e: Exception) {
            LogUtil.d(TAG, "Ping connectivity check failed for $host: ${e.message}")
            false
        }
    }
    
    /**
     * Batch check connectivity of multiple hosts
     * 
     * @param hosts Host list (host:port format)
     * @param timeoutMs Timeout for each host
     * @return List of reachable hosts
     */
    suspend fun checkMultipleHosts(
        hosts: List<Pair<String, Int>>, 
        timeoutMs: Int = DEFAULT_TIMEOUT_MS
    ): List<Pair<String, Int>> {
        return withContext(Dispatchers.IO) {
            val reachableHosts = mutableListOf<Pair<String, Int>>()
            
            // Concurrently check all hosts
            val jobs = hosts.map { (host, port) ->
                async {
                    if (isHostReachable(host, port, timeoutMs)) {
                        synchronized(reachableHosts) {
                            reachableHosts.add(host to port)
                        }
                    }
                }
            }
            
            // Wait for all checks to complete
            jobs.awaitAll()
            
            LogUtil.d(TAG, "Connectivity check completed: ${reachableHosts.size}/${hosts.size} hosts reachable")
            reachableHosts
        }
    }
    
    /**
     * Connectivity check result
     */
    data class ConnectivityResult(
        val isReachable: Boolean,
        val responseTimeMs: Long,
        val method: String, // "TCP" or "PING"
        val error: String? = null
    )
    
    /**
     * Detailed connectivity check (ping only)
     * 
     * Note: To avoid interfering with existing connections, this method only uses ping checks
     * 
     * @param host Target host
     * @param port Target port (only for logging)
     * @param timeoutMs Timeout duration
     * @return Detailed check result
     */
    suspend fun checkConnectivityDetailed(
        host: String, 
        port: Int, 
        timeoutMs: Int = DEFAULT_TIMEOUT_MS
    ): ConnectivityResult {
        return withContext(Dispatchers.IO) {
            try {
                LogUtil.d(TAG, "Performing detailed connectivity check for $host:$port (ping only)")
                
                // Only use ping check to avoid TCP connection interfering with existing connections
                val pingResult = checkPingConnectivityDetailed(host, timeoutMs)
                return@withContext pingResult
                
            } catch (e: Exception) {
                val startTime = System.currentTimeMillis()
                val responseTime = System.currentTimeMillis() - startTime
                ConnectivityResult(
                    isReachable = false,
                    responseTimeMs = responseTime,
                    method = "ERROR",
                    error = e.message
                )
            }
        }
    }
    
    /**
     * Detailed connectivity check (includes TCP check)
     * 
     * Warning: This method will create TCP connection, may interfere with existing connections!
     * Only use when confirmed server supports multiple client connections
     * 
     * @param host Target host
     * @param port Target port
     * @param timeoutMs Timeout duration
     * @return Detailed check result
     */
    suspend fun checkConnectivityDetailedWithTcp(
        host: String, 
        port: Int, 
        timeoutMs: Int = DEFAULT_TIMEOUT_MS
    ): ConnectivityResult {
        return withContext(Dispatchers.IO) {
            try {
                // First try TCP connection
                val tcpResult = checkTcpConnectivityDetailed(host, port, timeoutMs)
                if (tcpResult.isReachable) {
                    return@withContext tcpResult
                }
                
                // TCP failed, try ping
                val pingResult = checkPingConnectivityDetailed(host, PING_TIMEOUT_MS)
                return@withContext pingResult
                
            } catch (e: Exception) {
                val startTime = System.currentTimeMillis()
                val responseTime = System.currentTimeMillis() - startTime
                ConnectivityResult(
                    isReachable = false,
                    responseTimeMs = responseTime,
                    method = "ERROR",
                    error = e.message
                )
            }
        }
    }
    
    /**
     * Detailed TCP connectivity check
     */
    private suspend fun checkTcpConnectivityDetailed(
        host: String, 
        port: Int, 
        timeoutMs: Int
    ): ConnectivityResult {
        val startTime = System.currentTimeMillis()
        
        return try {
            withTimeout(timeoutMs.toLong()) {
                withContext(Dispatchers.IO) {
                    val socket = Socket()
                    try {
                        socket.connect(InetSocketAddress(host, port), timeoutMs)
                        val responseTime = System.currentTimeMillis() - startTime
                        ConnectivityResult(
                            isReachable = socket.isConnected,
                            responseTimeMs = responseTime,
                            method = "TCP"
                        )
                    } finally {
                        try {
                            socket.close()
                        } catch (e: Exception) {
                            // Ignore close errors
                        }
                    }
                }
            }
        } catch (e: Exception) {
            val responseTime = System.currentTimeMillis() - startTime
            ConnectivityResult(
                isReachable = false,
                responseTimeMs = responseTime,
                method = "TCP",
                error = e.message
            )
        }
    }
    
    /**
     * Detailed Ping connectivity check
     */
    private suspend fun checkPingConnectivityDetailed(
        host: String, 
        timeoutMs: Int
    ): ConnectivityResult {
        val startTime = System.currentTimeMillis()
        
        return try {
            withTimeout(timeoutMs.toLong()) {
                withContext(Dispatchers.IO) {
                    val address = InetAddress.getByName(host)
                    val isReachable = address.isReachable(timeoutMs)
                    val responseTime = System.currentTimeMillis() - startTime
                    
                    ConnectivityResult(
                        isReachable = isReachable,
                        responseTimeMs = responseTime,
                        method = "PING"
                    )
                }
            }
        } catch (e: Exception) {
            val responseTime = System.currentTimeMillis() - startTime
            ConnectivityResult(
                isReachable = false,
                responseTimeMs = responseTime,
                method = "PING",
                error = e.message
            )
        }
    }
}