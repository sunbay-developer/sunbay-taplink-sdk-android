package com.sunmi.tapro.taplink.sdk.demo

import android.os.Bundle
import android.widget.Button
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.sunmi.tapro.taplink.communication.cable.aoa.host.CableAoaHostKernel
import com.sunmi.tapro.taplink.communication.enums.InnerConnectionStatus
import com.sunmi.tapro.taplink.communication.interfaces.AsyncServiceKernel
import com.sunmi.tapro.taplink.communication.interfaces.ConnectionCallback
import com.sunmi.tapro.taplink.demo.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * USB Host Kernel 测试 Activity
 * 
 * 使用新架构的 CableAoaHostKernel 进行测试
 */
class UsbHostKernelTestActivity : AppCompatActivity() {

    private lateinit var logTextView: TextView
    private lateinit var logScrollView: ScrollView
    private lateinit var btnConnect: Button
    private lateinit var btnDisconnect: Button
    private lateinit var btnSendData: Button
    private lateinit var btnClearLog: Button
    private lateinit var tvKernelInfo: TextView

    private val dateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var kernel: AsyncServiceKernel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_usb_host_kernel_test)

        initViews()
        setupListeners()
        createKernel()
    }

    private fun initViews() {
        logTextView = findViewById(R.id.tv_log)
        logScrollView = findViewById(R.id.scroll_log)
        btnConnect = findViewById(R.id.btn_connect)
        btnDisconnect = findViewById(R.id.btn_disconnect)
        btnSendData = findViewById(R.id.btn_send_data)
        btnClearLog = findViewById(R.id.btn_clear_log)
        tvKernelInfo = findViewById(R.id.tv_kernel_info)

        updateButtonStatesByStatus(InnerConnectionStatus.DISCONNECTED)
        updateKernelInfo()
    }

    private fun setupListeners() {
        btnConnect.setOnClickListener {
            connectToDevice()
        }

        btnDisconnect.setOnClickListener {
            disconnectFromDevice()
        }

        btnSendData.setOnClickListener {
            sendTestData()
        }

        btnClearLog.setOnClickListener {
            logTextView.text = ""
        }
    }

    private fun createKernel() {
        kernel?.let {
            scope.launch {
                it.disconnect()
            }
        }

        log("创建 CableAoaHostKernel")
        kernel = CableAoaHostKernel(
            appId = "demo_app_id",
            appSecretKey = "demo_secret_key",
            context = this
        ).apply {
            // 设置数据接收回调
            registerDataReceiver { data ->
                data?.let {
                    log("📥 收到数据: ${it.size} bytes - ${String(it, Charsets.UTF_8)}")
                }
            }
            
            // 设置状态变化监听器
            setStatusChangeListener { status ->
                runOnUiThread {
                    updateButtonStatesByStatus(status)
                    log("📊 状态变化: $status")
                }
            }
        }

        updateKernelInfo()
    }

    private fun connectToDevice() {
        log("=== 开始连接 ===")

        val protocol = "usb://"

        val callback = object : ConnectionCallback {
            override fun onWaitingConnect() {
                log("⏳ 等待连接...")
                runOnUiThread {
                    updateButtonStatesByStatus(InnerConnectionStatus.WAITING_CONNECT)
                }
            }
            
            override fun onConnected(extraInfoMap: Map<String, String?>?) {
                log("✅ 连接成功")
                runOnUiThread {
                    updateButtonStatesByStatus(InnerConnectionStatus.CONNECTED)
                }
            }

            override fun onDisconnected(code: String, msg: String) {
                log("⚠️ 断开通知: [$code] $msg")
                // 注意：状态更新由 StatusChangeListener 处理
            }
        }

        scope.launch {
            try {
                kernel?.connect(protocol, callback)
            } catch (e: Exception) {
                log("❌ 连接异常: ${e.message}")
                runOnUiThread {
                    updateButtonStatesByStatus(InnerConnectionStatus.ERROR)
                }
            }
        }
    }

    private fun disconnectFromDevice() {
        log("=== 主动断开连接 ===")

        scope.launch {
            try {
                kernel?.disconnect()
                log("✅ 断开成功")
            } catch (e: Exception) {
                log("❌ 断开异常: ${e.message}")
            }
        }
    }

    private fun sendTestData() {
        val testData = "Hello from Host Test at ${dateFormat.format(Date())}"
        log("📤 发送数据: $testData")

        kernel?.sendData(
            traceId = "test_${System.currentTimeMillis()}",
            data = testData.toByteArray(),
            callback = null
        )
    }

    private fun updateButtonStates(connected: Boolean) {
        btnConnect.isEnabled = !connected
        btnDisconnect.isEnabled = connected
        btnSendData.isEnabled = connected
    }
    
    /**
     * 根据连接状态更新按钮状态
     */
    private fun updateButtonStatesByStatus(status: InnerConnectionStatus) {
        when (status) {
            InnerConnectionStatus.DISCONNECTED -> {
                btnConnect.isEnabled = true
                btnConnect.text = "连接"
                btnDisconnect.isEnabled = false
                btnSendData.isEnabled = false
            }
            InnerConnectionStatus.WAITING_CONNECT -> {
                btnConnect.isEnabled = false
                btnConnect.text = "等待中..."
                btnDisconnect.isEnabled = false
                btnSendData.isEnabled = false
            }
            InnerConnectionStatus.CONNECTING,
            InnerConnectionStatus.RECONNECTING-> {
                btnConnect.isEnabled = false
                btnConnect.text = "连接中..."
                btnDisconnect.isEnabled = false
                btnSendData.isEnabled = false
            }
            InnerConnectionStatus.CONNECTED -> {
                btnConnect.isEnabled = false
                btnConnect.text = "已连接"
                btnDisconnect.isEnabled = true
                btnDisconnect.text = "断开"
                btnSendData.isEnabled = true
            }
            InnerConnectionStatus.ERROR -> {
                btnConnect.isEnabled = true
                btnConnect.text = "重新连接"
                btnDisconnect.isEnabled = false
                btnSendData.isEnabled = false
            }
        }
    }

    private fun updateKernelInfo() {
        val info = """
            USB Host Kernel 测试
            
            特点:
            ✅ 简化的状态机驱动
            ✅ 单一连接入口
            ✅ 自动设备检测
            ✅ AOA 协议切换
            ✅ 三层断开检测
            
            状态说明:
            • DISCONNECTED: 未连接
            • WAITING_CONNECT: 等待设备
            • CONNECTING: 正在连接
            • CONNECTED: 已连接
            
            断开检测:
            • 系统广播检测 (< 100ms)
            • 传输错误检测 (< 500ms)
            • 异常捕获检测 (< 500ms)
        """.trimIndent()
        
        tvKernelInfo.text = info
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
        scope.cancel()
        scope.launch {
            kernel?.disconnect()
        }
    }
}
