package com.sunmi.tapro.taplink.sdk.demo

import android.os.Bundle
import android.widget.Button
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.sunmi.tapro.taplink.communication.cable.aoa.host.CableAoaHostKernel
import com.sunmi.tapro.taplink.communication.cable.aoa.host.UsbStandardHostKernel
import com.sunmi.tapro.taplink.communication.enums.InnerConnectionStatus
import com.sunmi.tapro.taplink.communication.interfaces.AsyncServiceKernel
import com.sunmi.tapro.taplink.communication.interfaces.ConnectionCallback
import com.sunmi.tapro.taplink.communication.protocol.UsbStandardProtocol
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * USB Host Kernel 层测试 Activity
 * 
 * 用于对比测试新旧 Kernel 实现：
 * - SimplifiedAoaHostKernel（新架构）
 * - UsbStandardHostKernel（旧架构）
 */
class UsbHostKernelTestActivity : AppCompatActivity() {

    private lateinit var logTextView: TextView
    private lateinit var logScrollView: ScrollView
    private lateinit var switchKernel: Switch
    private lateinit var btnConnect: Button
    private lateinit var btnDisconnect: Button
    private lateinit var btnSendData: Button
    private lateinit var btnClearLog: Button
    private lateinit var tvKernelInfo: TextView

    private val dateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var kernel: AsyncServiceKernel? = null
    private var useSimplifiedKernel = true

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
        switchKernel = findViewById(R.id.switch_kernel)
        btnConnect = findViewById(R.id.btn_connect)
        btnDisconnect = findViewById(R.id.btn_disconnect)
        btnSendData = findViewById(R.id.btn_send_data)
        btnClearLog = findViewById(R.id.btn_clear_log)
        tvKernelInfo = findViewById(R.id.tv_kernel_info)

        updateButtonStates(false)
        updateKernelInfo()
    }

    private fun setupListeners() {
        switchKernel.setOnCheckedChangeListener { _, isChecked ->
            useSimplifiedKernel = isChecked
            recreateKernel()
        }

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

        kernel = if (useSimplifiedKernel) {
            log("创建 SimplifiedAoaHostKernel（新架构）")
            CableAoaHostKernel(
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
            }
        } else {
            log("创建 UsbStandardHostKernel（旧架构）")
            UsbStandardHostKernel(
                appId = "demo_app_id",
                appSecretKey = "demo_secret_key",
                context = this,
                usbStandardInfo = UsbStandardProtocol.UsbStandardInfo(),
                permissionAction = "com.sunmi.tapro.taplink.USB_PERMISSION",
                permissionPendingIntent = android.app.PendingIntent.getBroadcast(
                    this,
                    0,
                    android.content.Intent("com.sunmi.tapro.taplink.USB_PERMISSION"),
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_MUTABLE
                )
            ).apply {
                // 设置数据接收回调
                registerDataReceiver { data ->
                    data?.let {
                        log("📥 收到数据: ${it.size} bytes - ${String(it, Charsets.UTF_8)}")
                    }
                }
            }
        }

        updateKernelInfo()
    }


    private fun recreateKernel() {
        log("=== 切换 Kernel 实现 ===")
        
        // 先断开当前连接
        if (kernel?.currentInnerConnectionStatus == InnerConnectionStatus.CONNECTED) {
            scope.launch {
                kernel?.disconnect()
            }
        }
        
        // 重新创建 Kernel
        createKernel()
    }

    private fun connectToDevice() {
        log("=== 开始连接 ===")
        updateButtonStates(false)

        val protocol = "usb://"

        val callback = object : ConnectionCallback {
            override fun onWaitingConnect() {
                log("⏳ 等待连接...")
            }
            
            override fun onConnected(extraInfoMap: Map<String, String?>?) {
                log("✅ 连接成功")
                runOnUiThread {
                    updateButtonStates(true)
                }
            }

            override fun onDisconnected(code: String, msg: String) {
                log("⚠️ 连接断开: [$code] $msg")
                runOnUiThread {
                    updateButtonStates(false)
                }
            }
        }

        scope.launch {
            try {
                kernel?.connect(protocol, callback)
            } catch (e: Exception) {
                log("❌ 连接异常: ${e.message}")
                runOnUiThread {
                    updateButtonStates(false)
                }
            }
        }
    }

    private fun disconnectFromDevice() {
        log("=== 开始断开 ===")
        updateButtonStates(false)

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
        switchKernel.isEnabled = !connected
    }

    private fun updateKernelInfo() {
        val kernelName = if (useSimplifiedKernel) {
            "SimplifiedAoaHostKernel（新架构）"
        } else {
            "UsbStandardHostKernel（旧架构）"
        }
        
        val info = """
            当前使用: $kernelName
            
            特点:
            ${if (useSimplifiedKernel) {
                """
                ✅ 代码量减少 65%
                ✅ 状态机驱动，逻辑清晰
                ✅ 单一入口，幂等操作
                ✅ 无 session/token 复杂度
                """.trimIndent()
            } else {
                """
                ⚠️ 使用 session/token 机制
                ⚠️ 多路径触发连接
                ⚠️ 状态同步复杂
                """.trimIndent()
            }}
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
