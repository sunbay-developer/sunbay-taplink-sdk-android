package com.sunmi.tapro.taplink.sdk.demo

import android.os.Bundle
import android.widget.Button
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.sunmi.tapro.taplink.sdk.TaplinkSDK
import com.sunmi.tapro.taplink.sdk.callback.ConnectionListener
import com.sunmi.tapro.taplink.sdk.config.ConnectionConfig
import com.sunmi.tapro.taplink.sdk.config.TaplinkConfig
import com.sunmi.tapro.taplink.sdk.enums.CableProtocol
import com.sunmi.tapro.taplink.sdk.error.ConnectionError
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * USB Host 模式测试 Demo
 * 
 * 使用 TaplinkSDK 进行连接和数据传输
 */
class UsbHostDemoActivity : AppCompatActivity() {

    private lateinit var logTextView: TextView
    private lateinit var logScrollView: ScrollView
    private lateinit var btnConnect: Button
    private lateinit var btnDisconnect: Button
    private lateinit var btnSendData: Button
    private lateinit var btnClearLog: Button

    private val dateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_usb_host_demo)

        initViews()
        initTaplinkSDK()
        setupListeners()
    }

    private fun initViews() {
        logTextView = findViewById(R.id.tv_log)
        logScrollView = findViewById(R.id.scroll_log)
        btnConnect = findViewById(R.id.btn_connect)
        btnDisconnect = findViewById(R.id.btn_disconnect)
        btnSendData = findViewById(R.id.btn_send_data)
        btnClearLog = findViewById(R.id.btn_clear_log)

        updateButtonStates(false)
    }

    private fun initTaplinkSDK() {
        // 创建 TaplinkConfig
        val config = TaplinkConfig.create(
            appId = "demo_app_id",
            merchantId = "demo_merchant_id",
            secretKey = "demo_secret_key",
        ).setLogEnabled(true)
        
        // 初始化 SDK
        TaplinkSDK.init(this, config)
        
        log("TaplinkSDK 初始化完成")
    }

    private fun setupListeners() {
        btnConnect.setOnClickListener {
            log("开始连接...")
            
            // 创建连接配置 - 使用 setCableProtocol 方法
            val connectionConfig = ConnectionConfig()
                .setCableProtocol(CableProtocol.USB_AOA)
            
            // 创建连接监听器
            val connectionListener = object : ConnectionListener {
                override fun onConnected(deviceId: String, taproVersion: String) {
                    log("连接成功: deviceId=$deviceId, taproVersion=$taproVersion")
                    updateButtonStates(true)
                }

                override fun onDisconnected(reason: String) {
                    log("连接断开: $reason")
                    updateButtonStates(false)
                }

                override fun onError(error: ConnectionError) {
                    log("连接错误: [${error.code}] ${error.message}")
                    updateButtonStates(false)
                }

                override fun onReconnecting(attempt: Int, maxRetries: Int) {
                    log("正在重连: 第 $attempt 次尝试 (最多 $maxRetries 次)")
                }
            }
            
            // 开始连接
            TaplinkSDK.connect(connectionConfig, connectionListener)
        }

        btnDisconnect.setOnClickListener {
            log("断开连接...")
            TaplinkSDK.disconnect()
            updateButtonStates(false)
        }

        btnSendData.setOnClickListener {
            sendTestData()
        }

        btnClearLog.setOnClickListener {
            logTextView.text = ""
        }
    }

    private fun sendTestData() {
        val testData = "Hello from USB Host at ${dateFormat.format(Date())}"
        log("发送数据: $testData")
        
        // 注意：TaplinkSDK 主要用于支付交易，原始数据发送需要使用底层 API
        log("提示：TaplinkSDK 主要用于支付交易，如需发送原始数据请使用 Kernel 层 API")
    }

    private fun updateButtonStates(connected: Boolean) {
        btnConnect.isEnabled = !connected
        btnDisconnect.isEnabled = connected
        btnSendData.isEnabled = connected
    }

    private fun log(message: String) {
        val timestamp = dateFormat.format(Date())
        val logMessage = "[$timestamp] $message\n"
        
        runOnUiThread {
            logTextView.append(logMessage)
            logScrollView.post {
                logScrollView.fullScroll(ScrollView.FOCUS_DOWN)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        TaplinkSDK.disconnect()
    }
}
