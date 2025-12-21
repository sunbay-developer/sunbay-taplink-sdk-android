package com.sunmi.tapro.taplink.sdk.example

import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.sunmi.tapro.taplink.sdk.TaplinkSDK
import com.sunmi.tapro.taplink.sdk.callback.ConnectionListener
import com.sunmi.tapro.taplink.sdk.callback.PaymentCallback
import com.sunmi.tapro.taplink.sdk.config.ConnectionConfig
import com.sunmi.tapro.taplink.sdk.config.TaplinkConfig
import com.sunmi.tapro.taplink.sdk.enums.ConnectionMode
import com.sunmi.tapro.taplink.sdk.error.ConnectionError
import com.sunmi.tapro.taplink.sdk.error.PaymentError
import com.sunmi.tapro.taplink.sdk.model.common.AmountInfo
import com.sunmi.tapro.taplink.sdk.model.common.PaymentEvent
import com.sunmi.tapro.taplink.sdk.model.request.PaymentRequest
import com.sunmi.tapro.taplink.sdk.model.response.PaymentResult

/**
 * 自动重连功能示例
 * 
 * 演示如何使用 Taplink SDK 的自动重连功能
 */
class AutoReconnectExampleActivity : AppCompatActivity() {

    // UI 组件
    private lateinit var statusTextView: TextView
    private lateinit var reconnectProgressBar: ProgressBar
    private lateinit var reconnectTextView: TextView
    private lateinit var connectButton: Button
    private lateinit var disconnectButton: Button
    private lateinit var paymentButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 初始化 UI（实际项目中应该使用 layout XML）
        initializeUI()
        
        // 初始化 SDK
        initializeSDK()
        
        // 设置按钮点击事件
        setupButtonListeners()
    }

    /**
     * 初始化 UI 组件
     */
    private fun initializeUI() {
        // 实际项目中应该使用 setContentView(R.layout.activity_auto_reconnect_example)
        // 这里仅作示例
        statusTextView = TextView(this)
        reconnectProgressBar = ProgressBar(this)
        reconnectTextView = TextView(this)
        connectButton = Button(this)
        disconnectButton = Button(this)
        paymentButton = Button(this)
        
        reconnectProgressBar.visibility = View.GONE
    }

    /**
     * 初始化 SDK
     */
    private fun initializeSDK() {
        // 方式 1: 使用默认配置（推荐）
        val config = TaplinkConfig(
            appId = "your_app_id",
            merchantId = "your_merchant_id",
            secretKey = "your_secret_key"
        )
        // 自动重连已默认启用：
        // - autoReconnect = true
        // - maxRetryCount = 3
        // - retryDelayMs = 2000L
        
        TaplinkSDK.init(this, config)
        
        // 方式 2: 自定义重连策略
        // val config = TaplinkConfig(
        //     appId = "your_app_id",
        //     merchantId = "your_merchant_id",
        //     secretKey = "your_secret_key"
        // )
        //     .setConnectionMode(ConnectionMode.LAN)
        //     .setAutoReconnect(true)
        //     .setRetryPolicy(maxRetries = 5, delayMs = 3000L)
        //     .setLogEnabled(true)
        // 
        // TaplinkSDK.init(this, config)
    }

    /**
     * 设置按钮点击事件
     */
    private fun setupButtonListeners() {
        connectButton.setOnClickListener {
            connectToDevice()
        }
        
        disconnectButton.setOnClickListener {
            disconnectFromDevice()
        }
        
        paymentButton.setOnClickListener {
            processPayment()
        }
    }

    /**
     * 连接到设备
     */
    private fun connectToDevice() {
        // 创建连接配置（根据实际情况选择）
        val connectionConfig = when {
            // 应用间模式（同设备）
            true -> null  // 使用默认配置
            
            // 局域网模式
            false -> ConnectionConfig()
                .setHost("192.168.1.100")
                .setPort(8443)
            
            // 线缆模式
            else -> ConnectionConfig()
                .setCableProtocol(com.sunmi.tapro.taplink.sdk.enums.CableProtocol.USB_AOA)
        }
        
        // 连接到设备
        TaplinkSDK.connect(connectionConfig, connectionListener)
    }

    /**
     * 断开连接
     */
    private fun disconnectFromDevice() {
        TaplinkSDK.disconnect()
        updateConnectionStatus("已断开连接")
        enablePaymentButton(false)
    }

    /**
     * 连接监听器
     */
    private val connectionListener = object : ConnectionListener {
        override fun onConnected(deviceId: String, taproVersion: String) {
            // 连接成功（包括重连成功）
            runOnUiThread {
                reconnectProgressBar.visibility = View.GONE
                updateConnectionStatus("已连接到设备: $deviceId (Tapro $taproVersion)")
                enablePaymentButton(true)
                Toast.makeText(
                    this@AutoReconnectExampleActivity,
                    "连接成功",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
        
        override fun onDisconnected(reason: String) {
            // 连接断开（重试失败或达到最大重试次数）
            runOnUiThread {
                reconnectProgressBar.visibility = View.GONE
                updateConnectionStatus("未连接")
                enablePaymentButton(false)
                
                // 显示重连失败对话框
                showReconnectFailedDialog(reason)
            }
        }
        
        override fun onError(error: ConnectionError) {
            // 连接错误
            runOnUiThread {
                reconnectProgressBar.visibility = View.GONE
                updateConnectionStatus("连接错误: ${error.message}")
                enablePaymentButton(false)
                
                // 处理特定错误
                when (error.code) {
                    "C22" -> {
                        // Tapro 未安装
                        showErrorDialog("Tapro 未安装", "请先安装 Tapro 应用")
                    }
                    "C20" -> {
                        // SDK 未初始化
                        showErrorDialog("SDK 未初始化", "请先调用 TaplinkSDK.init()")
                    }
                    else -> {
                        showErrorDialog("连接错误", error.message)
                    }
                }
            }
        }
        
        override fun onReconnecting(attempt: Int, maxRetries: Int) {
            // 正在重连（可选实现）
            runOnUiThread {
                reconnectProgressBar.visibility = View.VISIBLE
                reconnectTextView.text = "正在重连... ($attempt/$maxRetries)"
                updateConnectionStatus("连接中断，正在尝试重连...")
                
                Toast.makeText(
                    this@AutoReconnectExampleActivity,
                    "正在重连... ($attempt/$maxRetries)",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    /**
     * 处理支付
     */
    private fun processPayment() {
        // 检查连接状态
        if (!TaplinkSDK.isConnected()) {
            Toast.makeText(this, "请先连接设备", Toast.LENGTH_SHORT).show()
            return
        }
        
        // 创建金额信息
        val amount = AmountInfo()
            .setOrderAmount(100.00)  // $100.00
            .setPricingCurrency("USD")
        
        // 创建支付请求（推荐使用枚举）
        val request = PaymentRequest(
            action = TransactionAction.SALE.value,
            referenceOrderId = "ORDER${System.currentTimeMillis()}",
            transactionRequestId = "TXNREQ${System.currentTimeMillis()}",
            amount = amount,
            description = "商品购买"
        )
        
        // 发起支付
        TaplinkSDK.execute(request, paymentCallback)
    }

    /**
     * 支付回调
     */
    private val paymentCallback = object : PaymentCallback {
        override fun onSuccess(result: PaymentResult) {
            runOnUiThread {
                if (result.isSuccess()) {
                    showSuccessDialog(
                        "支付成功",
                        "交易ID: ${result.transactionId}\n" +
                        "授权码: ${result.authCode}"
                    )
                } else {
                    showErrorDialog("支付失败", result.message ?: "未知错误")
                }
            }
        }
        
        override fun onFailure(error: PaymentError) {
            runOnUiThread {
                // 检查是否是连接错误
                if (error.code == "NOT_CONNECTED" || error.code == "CONNECTION_LOST") {
                    // 连接断开，SDK 会自动重连
                    showReconnectingDialog()
                } else {
                    showErrorDialog("支付失败", error.message)
                }
            }
        }
        
        override fun onProgress(event: PaymentEvent) {
            runOnUiThread {
                updatePaymentProgress(event)
            }
        }
    }

    /**
     * 更新连接状态
     */
    private fun updateConnectionStatus(status: String) {
        statusTextView.text = status
    }

    /**
     * 启用/禁用支付按钮
     */
    private fun enablePaymentButton(enabled: Boolean) {
        paymentButton.isEnabled = enabled
        paymentButton.alpha = if (enabled) 1.0f else 0.5f
    }

    /**
     * 更新支付进度
     */
    private fun updatePaymentProgress(event: PaymentEvent) {
        val progressText = when (event) {
            is PaymentEvent.Processing -> "处理中..."
            is PaymentEvent.WaitingCard -> "请刷卡/插卡/挥卡"
            is PaymentEvent.CardDetected -> "卡片已检测"
            is PaymentEvent.ReadingCard -> "读取卡片中..."
            is PaymentEvent.WaitingPin -> "请输入PIN码"
            is PaymentEvent.WaitingSignature -> "请签名"
            is PaymentEvent.WaitingOnlineResponse -> "等待联机响应..."
            is PaymentEvent.Printing -> "打印小票中..."
            is PaymentEvent.Completed -> "交易完成"
            is PaymentEvent.Cancel -> "交易已取消"
            is PaymentEvent.Reconnecting -> "正在重连... (${event.attempt}/${event.maxRetries})"
        }
        
        Toast.makeText(this, progressText, Toast.LENGTH_SHORT).show()
    }

    /**
     * 显示重连失败对话框
     */
    private fun showReconnectFailedDialog(reason: String) {
        AlertDialog.Builder(this)
            .setTitle("连接失败")
            .setMessage("无法连接到设备\n\n原因: $reason\n\n请检查设备连接或手动重新连接")
            .setPositiveButton("重新连接") { _, _ ->
                connectToDevice()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    /**
     * 显示重连中对话框
     */
    private fun showReconnectingDialog() {
        AlertDialog.Builder(this)
            .setTitle("连接中断")
            .setMessage("正在尝试重新连接，请稍候...")
            .setPositiveButton("等待", null)
            .setNegativeButton("取消") { _, _ ->
                // 用户取消，停止重连
                TaplinkSDK.disconnect()
            }
            .show()
    }

    /**
     * 显示成功对话框
     */
    private fun showSuccessDialog(title: String, message: String) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("确定", null)
            .show()
    }

    /**
     * 显示错误对话框
     */
    private fun showErrorDialog(title: String, message: String) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("确定", null)
            .show()
    }

    override fun onDestroy() {
        super.onDestroy()
        // 清理资源
        TaplinkSDK.disconnect()
    }
}

// ========== 不同场景的配置示例 ==========

/**
 * 场景 1: 局域网模式（网络波动）
 */
fun configureLANMode(context: Context) {
    val config = TaplinkConfig(
        appId = "your_app_id",
        merchantId = "your_merchant_id",
        secretKey = "your_secret_key"
    )
        .setConnectionMode(ConnectionMode.LAN)
        .setAutoReconnect(true)
        .setRetryPolicy(maxRetries = 5, delayMs = 3000L)  // 网络重连需要更多时间
        .setLogEnabled(true)
    
    TaplinkSDK.init(context, config)
}

/**
 * 场景 2: 线缆模式（线缆松动）
 */
fun configureCableMode(context: Context) {
    val config = TaplinkConfig(
        appId = "your_app_id",
        merchantId = "your_merchant_id",
        secretKey = "your_secret_key"
    )
        .setConnectionMode(ConnectionMode.CABLE)
        .setAutoReconnect(true)
        .setRetryPolicy(maxRetries = 3, delayMs = 2000L)
        .setLogEnabled(true)
    
    TaplinkSDK.init(context, config)
}

/**
 * 场景 3: 应用间模式（服务重启）
 */
fun configureAppToAppMode(context: Context) {
    val config = TaplinkConfig(
        appId = "your_app_id",
        merchantId = "your_merchant_id",
        secretKey = "your_secret_key"
    )
        .setConnectionMode(ConnectionMode.APP_TO_APP)
        .setAutoReconnect(true)
        .setRetryPolicy(maxRetries = 3, delayMs = 1000L)  // IPC 重连较快
        .setLogEnabled(true)
    
    TaplinkSDK.init(context, config)
}

/**
 * 场景 4: 禁用自动重连（手动控制）
 */
fun configureManualReconnect(context: Context) {
    val config = TaplinkConfig(
        appId = "your_app_id",
        merchantId = "your_merchant_id",
        secretKey = "your_secret_key"
    )
        .setAutoReconnect(false)  // 禁用自动重连
        .setLogEnabled(true)
    
    TaplinkSDK.init(context, config)
}

/**
 * 场景 5: 根据连接模式动态配置
 */
fun configureDynamicRetryPolicy(context: Context, connectionMode: ConnectionMode) {
    val (maxRetries, delayMs) = when (connectionMode) {
        ConnectionMode.APP_TO_APP -> Pair(3, 1000L)  // IPC 连接快
        ConnectionMode.CABLE -> Pair(3, 2000L)       // 线缆连接中等
        ConnectionMode.LAN -> Pair(5, 3000L)         // 网络连接慢
        else -> Pair(3, 2000L)
    }
    
    val config = TaplinkConfig(
        appId = "your_app_id",
        merchantId = "your_merchant_id",
        secretKey = "your_secret_key"
    )
        .setConnectionMode(connectionMode)
        .setAutoReconnect(true)
        .setRetryPolicy(maxRetries, delayMs)
        .setLogEnabled(true)
    
    TaplinkSDK.init(context, config)
}
