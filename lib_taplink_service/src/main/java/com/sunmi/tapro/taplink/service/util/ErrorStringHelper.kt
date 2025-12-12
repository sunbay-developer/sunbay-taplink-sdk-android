package com.sunmi.tapro.taplink.service.util

import android.content.Context
import com.sunmi.tapro.taplink.service.TaplinkServiceKernel

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
            "0" -> "Interface response successful"
            "S01" -> "Invalid IP address"
            "S02" -> "Request timestamp differs too much from server time"
            "S03" -> "Signature verification failed"
            "S04" -> "API call frequency exceeded limit"
            "S05" -> "Data decryption failed"
            "S06" -> "Insufficient permissions"
            "S07" -> "TMK key does not exist or has expired"
            "S08" -> "Working key does not exist or has expired"
            "S09" -> "Login session expired or not logged in"
            "C01" -> "Missing request parameters"
            "C02" -> "Request JSON format error"
            "C03" -> "Merchant (merchantId) does not exist or is disabled"
            "C04" -> "Store (storeId) does not exist or is disabled"
            "C05" -> "Device (sn) does not exist"
            "C06" -> "Duplicate request sequence number"
            "C07" -> "Duplicate merchant order number"
            "C08" -> "Transaction locked (anti-concurrent operation)"
            "C09" -> "Order not paid"
            "C10" -> "Order already paid"
            "C11" -> "Order closed"
            "C12" -> "Order does not exist"
            "C13" -> "Unsupported pricing currency"
            "C14" -> "Transaction amount exceeds allowed range"
            "C15" -> "Refund amount exceeds original order amount"
            "C16" -> "Request parameter is empty"
            "C17" -> "Request parameter format error"
            "C18" -> "Request interface does not exist"
            "C19" -> "Unsupported HTTP method"
            "C20" -> "SDK not initialized"
            "C21" -> "Device does not exist"
            "C22" -> "Tapro application not installed (inter-app mode)"
            "C23" -> "Cable not physically connected (cable mode)"
            "C24" -> "Unsupported cable protocol (cable mode)"
            "C25" -> "IPC service binding failed (inter-app mode)"
            "C26" -> "IPC service connection timeout (inter-app mode)"
            "C27" -> "Cable connection timeout (cable mode)"
            "C28" -> "Cable communication error (cable mode)"
            "C29" -> "Device not paired (LAN mode)"
            "C30" -> "Network connection failed (LAN mode)"
            "C31" -> "Network connection timeout (LAN mode)"
            "C32" -> "TLS handshake failed (LAN mode)"
            "C33" -> "WebSocket connection failed (LAN mode)"
            "C34" -> "Device offline (LAN mode)"
            "C35" -> "mDNS discovery failed (LAN mode)"
            "C36" -> "Connection disconnected"
            "C37" -> "No connection established"
            "C38" -> "Unsupported transaction type (Action)"
            "C39" -> "Transaction cancelled"
            "C40" -> "Application initialization failed"
            "C41" -> "Protocol data is invalid or parsing failed"
            "M01" -> "Insufficient interface call permissions or not activated"
            "M02" -> "Application (appId) does not exist or is disabled"
            "M03" -> "Application and merchant binding relationship mismatch"
            "M04" -> "Merchant has not activated this payment product"
            "P01" -> "Bank channel exception"
            "E01" -> "System busy, please try again later"
            "E02" -> "Network error"
            "E03" -> "Unknown exception"
            "E04" -> "System exception"
            "R01" -> "Risk control system rejected transaction"
            "996" -> "System timeout"
            "" -> "Error code is empty"
            else -> "Unknown error code: $code"
        }
    }
    
    /**
     * 获取默认解决方案（当资源文件不可用时的后备方案）
     */
    private fun getDefaultSolution(code: String): String? {
        return when (code.uppercase()) {
            "0" -> "Interface response successful, but does not represent transaction success. Check transaction status and result code to determine actual transaction status"
            "S01" -> "Check if merchant has configured IP whitelist and add calling server IP to whitelist"
            "S02" -> "Synchronize client system time, ensure time difference is within allowed range (±10 minutes)"
            "S03" -> "Check signature algorithm, key and parameter order, confirm signature parameters are not modified, regenerate signature"
            "S04" -> "Reduce call frequency, follow platform frequency limit rules"
            "S05" -> "Check code encryption logic or key settings"
            "S06" -> "Contact administrator to apply for permissions"
            "S07" -> "Re-obtain TMK"
            "S08" -> "Re-obtain working key"
            "S09" -> "Re-login"
            "C01" -> "Ensure all required parameters are provided"
            "C02" -> "Check JSON structure, bracket matching and data type validity"
            "C03" -> "Check if merchant ID is correct and confirm merchant status"
            "C04" -> "Check if store ID is correct and confirm store status"
            "C05" -> "Check if device SN is correct and confirm device binding"
            "C06" -> "Ensure uniqueness of merchant request number"
            "C07" -> "Ensure uniqueness of merchant order number"
            "C08" -> "Wait for current operation to complete before initiating new request"
            "C09" -> "Check order number and order status"
            "C10" -> "Check order status through order query interface"
            "C11" -> "Create new order and initiate request"
            "C12" -> "Check if order number is correct and order belongs to merchant"
            "C13" -> "Check merchant supported pricing currencies"
            "C14" -> "Check platform amount limit rules, adjust request amount"
            "C15" -> "Verify original order amount and refunded amount"
            "C16" -> "Ensure non-empty parameters are set with correct values"
            "C17" -> "Ensure all values are filled according to documentation specifications"
            "C18" -> "Check interface API address"
            "C19" -> "Check interface HTTP method"
            "C20" -> "Call TaplinkSDK.init() to initialize SDK before calling connect()"
            "C21" -> "Complete device activation or bind on platform"
            "C22" -> "Install Tapro application on device"
            "C23" -> "Check physical connection of USB or serial cable"
            "C24" -> "Use supported cable protocol (USB AOA/USB-VSP/RS232) or enable auto-detection"
            "C25" -> "Check if Tapro application is running normally, restart application or device"
            "C26" -> "Check Tapro application status, ensure application is not killed by system"
            "C27" -> "Check cable connection status, re-plug cable or replace cable"
            "C28" -> "Check cable quality and connection stability, try replacing cable"
            "C29" -> "First connection requires manual input of IP and port for pairing"
            "C30" -> "Check network connection, ensure device is on same network segment"
            "C31" -> "Check network status, confirm payment terminal is online"
            "C32" -> "Check certificate configuration, confirm SDK is configured to trust self-signed certificates"
            "C33" -> "Check network firewall settings, ensure ports 8443-8453 are not blocked"
            "C34" -> "Check payment terminal network connection and power status"
            "C35" -> "Check if network supports mDNS, or manually input IP address"
            "C36" -> "Check connection status, call connect() to re-establish connection"
            "C37" -> "Call connect() to establish connection before initiating transaction"
            "C38" -> "Check if requested action is correct and supported by current device"
            "C39" -> "Transaction has been cancelled by user or system, no retry needed"
            "C40" -> "Check initialization parameter configuration, confirm network connection and permission settings, retry initialization"
            "C41" -> "Check protocol format"
            "M01" -> "Apply for corresponding interface permissions or renew"
            "M02" -> "Check if appId is correct and confirm application status"
            "M03" -> "Confirm application has been correctly authorized to this merchant"
            "M04" -> "Confirm merchant has activated payment product and completed configuration"
            "P01" -> "Call order query interface to confirm transaction status, retry if not paid, contact technical support if still failing"
            "E01" -> "Call order query interface to confirm transaction status, retry if not paid, contact technical support if still failing"
            "E02" -> "Call order query interface to confirm transaction status, retry if not paid, contact technical support if still failing"
            "E03" -> "Call order query interface to confirm transaction status, retry if not paid, contact technical support if still failing"
            "E04" -> "Capture system thrown exception and feedback to caller"
            "R01" -> "Check if transaction behavior is abnormal, contact platform risk control team"
            "996" -> "Call order query interface to confirm transaction status, retry if not paid, contact technical support if still failing"
            else -> null
        }
    }
}