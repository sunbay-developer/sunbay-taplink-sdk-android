package com.sunmi.tapro.taplink.communication.serial

import android.content.Context
import com.sunmi.tapro.taplink.communication.enums.ConnectionStatus
import com.sunmi.tapro.taplink.communication.interfaces.BaseServiceKernel
import com.sunmi.tapro.taplink.communication.interfaces.InnerCallback
import com.sunmi.tapro.taplink.communication.interfaces.ConnectionCallback
import com.sunmi.tapro.taplink.communication.util.LogUtil
import com.sunmi.tapro.taplink.communication.util.ProtocolParser
import com.sunmi.tapro.taplink.communication.util.ProtocolParseResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException

/**
 * Serial Service Kernel Class
 * 
 * Provides serial communication functionality, implements IServiceKernel interface
 * 
 * @author TaPro Team
 * @since 2025-01-XX
 */
class SerialServiceKernel(private val context: Context) : BaseServiceKernel() {
    private val TAG = "SerialServiceKernel"
    
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    private var serialPort: File? = null
    private var inputStream: FileInputStream? = null
    private var outputStream: FileOutputStream? = null
    
    private var onDataReceivedListener: ((ByteArray) -> Unit)? = null
    private var isReading = false
    
    /**
     * Implement IServiceKernel interface: Connect to serial port
     * 
     * Communication Flow:
     * 1. Check if connection is allowed (not already connected/connecting)
     * 2. Update status to CONNECTING and notify callback
     * 3. Parse protocol string to determine serial port type and parameters
     * 4. Handle different protocol types:
     *    a. SerialProtocol: Open physical serial port with specified device path and baud rate
     *    b. VspProtocol: Open virtual serial port with specified parameters
     *    c. Other protocols: Return protocol mismatch error
     * 5. Notify connection success or failure
     * 
     * @param protocol Protocol string, supports formats:
     *   - serial://devicePath:baudRate (e.g., serial:///dev/ttyS1:9600)
     *   - vsp://baudRate/dataBits/parity/stopBits
     * @param connectionCallback Connection callback to notify status changes
     */
    override fun connect(protocol: String, connectionCallback: ConnectionCallback) {
        if (!canConnect()) {
            handleConnectionError(
                "Serial port is already connected or connecting. Current status: $currentConnectionStatus",
                connectionCallback
            )
            return
        }
        
        try {
            updateStatus(ConnectionStatus.CONNECTING)
            connectionCallback.onWaitingConnect()
            
            // Parse protocol string to determine serial port type and parameters
            val parseResult = ProtocolParser.parse(protocol)
            if (parseResult is ProtocolParseResult.Error) {
                handleConnectionError(parseResult.message, connectionCallback)
                return
            }
            
            // Support two protocol formats: serial:// and vsp://
            when (parseResult) {
                is ProtocolParseResult.SerialProtocol -> {
                    val success = open(parseResult.devicePath, parseResult.baudRate)
                    if (success) {
                        handleConnectionSuccess(connectionCallback, null)
                    } else {
                        handleConnectionError("Failed to open serial port: ${parseResult.devicePath}", connectionCallback)
                    }
                }
                is ProtocolParseResult.VspProtocol -> {
                    // VSP virtual serial port connection logic
                    val success = openVsp(parseResult.baudRate, parseResult.dataBits, parseResult.parity, parseResult.stopBits)
                    if (success) {
                        handleConnectionSuccess(connectionCallback, null)
                    } else {
                        handleConnectionError("Failed to open VSP port", connectionCallback)
                    }
                }
                else -> {
                    handleConnectionError("Protocol type mismatch, expected serial or vsp protocol", connectionCallback)
                }
            }
        } catch (e: Exception) {
            LogUtil.e(TAG, "Failed to connect serial port: ${e.message}")
            handleConnectionError(e.message ?: "Unknown error", connectionCallback)
        }
    }
    
    /**
     * Implement IServiceKernel interface: Send data
     * 
     * Communication Flow:
     * 1. Check if data can be sent (connection status is CONNECTED)
     * 2. If not connected, notify callback with NOT_CONNECTED error
     * 3. Call internal send method to write data to serial port
     * 4. Handle send result:
     *    a. Success: Notify callback with SUCCESS response
     *    b. Failure: Update status to ERROR and notify callback with SEND_FAILED error
     * 
     * @param traceId Trace ID for request tracing
     * @param data Data to send via serial port
     * @param callback Send result callback to notify success/failure
     */
    override fun sendData(traceId: String, data: ByteArray, callback: InnerCallback?) {
        if (!canSendData()) {
            LogUtil.e(TAG, "Cannot send data, not connected. Status: $currentConnectionStatus")
            callback?.onError("NOT_CONNECTED", "Service not connected")
            return
        }
        
        val result = send(data)
        if (!result) {
            updateStatus(ConnectionStatus.ERROR)
            callback?.onError("SEND_FAILED", "Failed to send data via serial port")
        } else {
            callback?.onResponse("SUCCESS")
        }
    }
    
    /**
     * Implement IServiceKernel interface: Register data receiver
     * 
     * @param receiver Data receive callback
     */
    override fun registerDataReceiver(receiver: (dataArray: ByteArray?) -> Unit) {
        // Call base class method to register receiver
        super.registerDataReceiver(receiver)
        // Also set the original listener for compatibility
        setOnDataReceivedListener { data ->
            receiver(data)
        }
    }
    
    /**
     * Implement IServiceKernel interface: Disconnect
     */
    override fun disconnect() {
        if (currentConnectionStatus == ConnectionStatus.DISCONNECTED) {
            return
        }
        
        updateStatus(ConnectionStatus.DISCONNECTED)
        disconnectInternal()
    }
    
    /**
     * Open VSP virtual serial port
     * 
     * @param baudRate Baud rate
     * @param dataBits Data bits
     * @param parity Parity
     * @param stopBits Stop bits
     * @return Boolean Whether opening succeeded
     */
    private fun openVsp(baudRate: Int, dataBits: Int, parity: String, stopBits: Int): Boolean {
        return try {
            // VSP virtual serial port connection logic
            // Note: Actual serial port operations require JNI or third-party libraries (e.g., usb-serial-for-android)
            // This is just an example framework, actual implementation needs to use appropriate library based on requirements
            LogUtil.d(TAG, "Opening VSP port: baudRate=$baudRate, dataBits=$dataBits, parity=$parity, stopBits=$stopBits")
            
            // Start data reading
            startReading()
            
            true
        } catch (e: Exception) {
            LogUtil.e(TAG, "Failed to open VSP port: ${e.message}")
            false
        }
    }
    
    /**
     * Open serial port
     * 
     * @param devicePath Serial device path (e.g., /dev/ttyS1)
     * @param baudRate Baud rate
     * @return Boolean Whether opening succeeded
     */
    private fun open(devicePath: String, baudRate: Int): Boolean {
        return try {
            val device = File(devicePath)
            if (!device.exists()) {
                LogUtil.e(TAG, "Serial device not found: $devicePath")
                return false
            }
            
            // Note: Actual serial port operations require JNI or third-party libraries (e.g., usb-serial-for-android)
            // This is just an example framework, actual implementation needs to use appropriate library based on requirements
            
            serialPort = device
            LogUtil.d(TAG, "Serial port opened: $devicePath, baudRate: $baudRate")
            
            // Start data reading
            startReading()
            
            true
        } catch (e: Exception) {
            LogUtil.e(TAG, "Failed to open serial port:${e.message}")
            false
        }
    }
    
    /**
     * Close serial port (internal implementation)
     */
    private fun disconnectInternal() {
        try {
            isReading = false
            inputStream?.close()
            outputStream?.close()
            inputStream = null
            outputStream = null
            serialPort = null
            LogUtil.d(TAG, "Serial port closed")
        } catch (e: Exception) {
            LogUtil.e(TAG, "Failed to close serial port:${e.message}")
        }
    }
    
    /**
     * Send data
     * 
     * @param data Data to send
     * @return Boolean Whether sending succeeded
     */
    private fun send(data: ByteArray): Boolean {
        return try {
            outputStream?.write(data)
            outputStream?.flush()
            LogUtil.d(TAG, "Data sent: ${data.size} bytes")
            true
        } catch (e: IOException) {
            LogUtil.e(TAG, "Failed to send data:${e.message}")
            false
        }
    }
    
    /**
     * Send string
     * 
     * @param text Text to send
     * @return Boolean Whether sending succeeded
     */
    fun send(text: String): Boolean {
        return send(text.toByteArray(Charsets.UTF_8))
    }
    
    /**
     * Start data reading
     */
    private fun startReading() {
        if (isReading) return
        
        isReading = true
        scope.launch {
            val buffer = ByteArray(1024)
            while (isReading && inputStream != null) {
                try {
                    val bytesRead = inputStream?.read(buffer) ?: -1
                    if (bytesRead > 0) {
                        val data = buffer.copyOf(bytesRead)
                        LogUtil.d(TAG, "Data received: $bytesRead bytes")
                        onDataReceivedListener?.invoke(data)
                        dataReceiver?.invoke(data)
                    }
                } catch (e: IOException) {
                    if (isReading) {
                        LogUtil.e(TAG, "Error reading serial data:${e.message}")
                        updateStatus(ConnectionStatus.ERROR)
                    }
                    break
                }
            }
        }
    }
    
    /**
     * Set data received listener
     * 
     * @param listener Listener
     */
    fun setOnDataReceivedListener(listener: (ByteArray) -> Unit) {
        onDataReceivedListener = listener
    }
    
    /**
     * Check if connected
     * 
     * @return Boolean Whether connected
     */
    fun isConnected(): Boolean {
        return serialPort != null && inputStream != null && outputStream != null
    }
}


