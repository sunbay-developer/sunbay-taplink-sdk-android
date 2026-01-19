package com.sunmi.tapro.taplink.sdk.demo

import android.content.Intent
import android.hardware.usb.UsbAccessory
import android.hardware.usb.UsbManager
import android.os.Bundle
import android.widget.Button
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.sunmi.tapro.taplink.communication.cable.aoa.accessory.UsbStandardAccessoryKernel
import com.sunmi.tapro.taplink.communication.interfaces.ConnectionCallback
import com.sunmi.tapro.taplink.communication.interfaces.InnerCallback
import com.sunmi.tapro.taplink.communication.protocol.ProtocolParseResult
import com.sunmi.tapro.taplink.communication.protocol.UsbStandardProtocol
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * USB Accessory 模式测试 Demo
 *
 * 直接使用 UsbStandardAccessoryKernel 进行连接和数据传输
 */
class UsbAccessoryDemoActivity : AppCompatActivity() {

    private lateinit var logTextView: TextView
    private lateinit var logScrollView: ScrollView
    private lateinit var btnConnect: Button
    private lateinit var btnDisconnect: Button
    private lateinit var btnSendData: Button
    private lateinit var btnClearLog: Button

    private var accessoryKernel: UsbStandardAccessoryKernel? = null
    private val dateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_usb_accessory_demo)

        initViews()
        initAccessoryKernel()
        setupListeners()
        
        // 处理 USB Accessory 连接 Intent
        handleUsbAccessoryIntent(intent)
    }
    
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // 处理新的 USB Accessory 连接 Intent
        handleUsbAccessoryIntent(intent)
    }
    
    private fun handleUsbAccessoryIntent(intent: Intent?) {
        if (intent?.action == UsbManager.ACTION_USB_ACCESSORY_ATTACHED) {
            val accessory: UsbAccessory? = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(UsbManager.EXTRA_ACCESSORY, UsbAccessory::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(UsbManager.EXTRA_ACCESSORY)
            }
            
            if (accessory != null) {
                log("收到 USB Accessory 连接: ${accessory.manufacturer} ${accessory.model}")
                // Kernel 会通过 BroadcastReceiver 自动处理，这里只是记录日志
                // 如果需要自动连接，可以在这里调用 connect()
            } else {
                log("收到 USB Accessory 连接 Intent，但 accessory 为 null")
            }
        }
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

    private fun initAccessoryKernel() {
        val pendingIntent = UsbStandardAccessoryKernel.createRequestPermissionPendingIntent(this)

        val usbStandardInfo = UsbStandardProtocol.UsbStandardInfo(
            manufacturer = "SUNMI",
            model = "TAPLINK_SDK",
            description = "Taplink SDK Demo",
            version = "1.0",
            uri = "https://www.sunmi.com",
            serial = "12345"
        )

        accessoryKernel = UsbStandardAccessoryKernel(
            context = this,
            usbStandardInfo = usbStandardInfo,
            permissionAction = UsbStandardAccessoryKernel.ACTION_USB_STANDARD_ACCESSORY_KERNEL,
            permissionPendingIntent = pendingIntent
        )

        accessoryKernel?.registerDataReceiver { data ->
            runOnUiThread {
                data?.let {
                    val dataStr = String(it, Charsets.UTF_8)
                    log("收到数据: $dataStr (${it.size} bytes)")
                }
            }
        }

        log("USB Accessory Kernel 初始化完成")
        log("等待 Host 设备连接...")
    }

    private fun setupListeners() {
        btnConnect.setOnClickListener {
            log("开始连接...")

            val parseResult = ProtocolParseResult.UsbProtocol()

            val connectionCallback = object : ConnectionCallback {
                override fun onConnected(extraInfoMap: Map<String, String?>?) {
                    runOnUiThread {
                        val deviceId = extraInfoMap?.get("deviceId") ?: "unknown"
                        log("连接成功: deviceId=$deviceId")
                        updateButtonStates(true)
                    }
                }

                override fun onWaitingConnect() {
                    runOnUiThread {
                        log("等待连接...")
                    }
                }

                override fun onDisconnected(code: String, msg: String) {
                    runOnUiThread {
                        log("连接断开: [$code] $msg")
                        updateButtonStates(false)
                    }
                }
            }

            accessoryKernel?.connect("usb://", connectionCallback)
        }

        btnDisconnect.setOnClickListener {
            log("断开连接...")
            accessoryKernel?.disconnect()
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
        val testData = "Hello from USB Accessory at ${dateFormat.format(Date())}"
        log("发送数据: $testData")

        accessoryKernel?.sendData(
            System.currentTimeMillis().toString(),
            testData.toByteArray(Charsets.UTF_8),
            object : InnerCallback {
                override fun onError(code: String, msg: String) {
                    runOnUiThread {
                        log("数据发送失败: [$code] $msg")
                    }
                }

                override fun onResponse(response: String) {
                    runOnUiThread {
                        log("数据发送成功")
                    }
                }
            })
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
        accessoryKernel?.disconnect()
        accessoryKernel?.release()
    }
}
