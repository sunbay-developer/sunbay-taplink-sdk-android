package com.sunmi.tapro.taplink.communication.lan.connection

import com.sunmi.tapro.taplink.communication.util.LogUtil
import kotlinx.coroutines.*
import java.io.IOException
import java.net.*

/**
 * 网络连通性检查器
 * 
 * 用于检测与特定主机的网络连通性，补充WebSocket连接状态检查的不足
 * 
 * 重要说明：
 * - 默认方法（isHostReachable、checkConnectivityDetailed）仅使用ping检查，不会创建TCP连接
 * - 这样设计是为了避免干扰现有的WebSocket连接，特别是当服务器只支持单客户端连接时
 * - 如果需要TCP连接检查，请使用带"WithTcp"后缀的方法，但要确认服务器支持多客户端连接
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
     * 检查与指定主机的网络连通性
     * 
     * 注意：为避免干扰现有连接，此方法仅使用ping检查，不会创建TCP连接
     * 
     * @param host 目标主机
     * @param port 目标端口（仅用于日志记录，实际检查使用ping）
     * @param timeoutMs 超时时间（毫秒）
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
                
                // 仅使用ping检查，避免TCP连接干扰现有连接
                // 对于只支持单客户端的服务器，TCP连接检查会导致现有连接断开
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
     * 检查与指定主机的网络连通性（包含TCP检查）
     * 
     * 警告：此方法会创建TCP连接，可能会干扰现有连接！
     * 仅在确认服务器支持多客户端连接时使用
     * 
     * @param host 目标主机
     * @param port 目标端口
     * @param timeoutMs 超时时间（毫秒）
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
                
                // 方法1：尝试TCP连接
                val tcpReachable = checkTcpConnectivity(host, port, timeoutMs)
                if (tcpReachable) {
                    LogUtil.d(TAG, "Host $host:$port is reachable via TCP")
                    return@withContext true
                }
                
                // 方法2：尝试ping（如果TCP失败）
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
     * 检查TCP连通性
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
     * 检查Ping连通性
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
     * 批量检查多个主机的连通性
     * 
     * @param hosts 主机列表（host:port格式）
     * @param timeoutMs 每个主机的超时时间
     * @return 可达主机的列表
     */
    suspend fun checkMultipleHosts(
        hosts: List<Pair<String, Int>>, 
        timeoutMs: Int = DEFAULT_TIMEOUT_MS
    ): List<Pair<String, Int>> {
        return withContext(Dispatchers.IO) {
            val reachableHosts = mutableListOf<Pair<String, Int>>()
            
            // 并发检查所有主机
            val jobs = hosts.map { (host, port) ->
                async {
                    if (isHostReachable(host, port, timeoutMs)) {
                        synchronized(reachableHosts) {
                            reachableHosts.add(host to port)
                        }
                    }
                }
            }
            
            // 等待所有检查完成
            jobs.awaitAll()
            
            LogUtil.d(TAG, "Connectivity check completed: ${reachableHosts.size}/${hosts.size} hosts reachable")
            reachableHosts
        }
    }
    
    /**
     * 连通性检查结果
     */
    data class ConnectivityResult(
        val isReachable: Boolean,
        val responseTimeMs: Long,
        val method: String, // "TCP" or "PING"
        val error: String? = null
    )
    
    /**
     * 详细的连通性检查（仅使用ping）
     * 
     * 注意：为避免干扰现有连接，此方法仅使用ping检查
     * 
     * @param host 目标主机
     * @param port 目标端口（仅用于日志记录）
     * @param timeoutMs 超时时间
     * @return 详细的检查结果
     */
    suspend fun checkConnectivityDetailed(
        host: String, 
        port: Int, 
        timeoutMs: Int = DEFAULT_TIMEOUT_MS
    ): ConnectivityResult {
        return withContext(Dispatchers.IO) {
            try {
                LogUtil.d(TAG, "Performing detailed connectivity check for $host:$port (ping only)")
                
                // 仅使用ping检查，避免TCP连接干扰现有连接
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
     * 详细的连通性检查（包含TCP检查）
     * 
     * 警告：此方法会创建TCP连接，可能会干扰现有连接！
     * 仅在确认服务器支持多客户端连接时使用
     * 
     * @param host 目标主机
     * @param port 目标端口
     * @param timeoutMs 超时时间
     * @return 详细的检查结果
     */
    suspend fun checkConnectivityDetailedWithTcp(
        host: String, 
        port: Int, 
        timeoutMs: Int = DEFAULT_TIMEOUT_MS
    ): ConnectivityResult {
        return withContext(Dispatchers.IO) {
            try {
                // 先尝试TCP连接
                val tcpResult = checkTcpConnectivityDetailed(host, port, timeoutMs)
                if (tcpResult.isReachable) {
                    return@withContext tcpResult
                }
                
                // TCP失败，尝试ping
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
     * 详细的TCP连通性检查
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
     * 详细的Ping连通性检查
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