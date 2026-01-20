package com.sunmi.tapro.taplink.sdk.demo

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

/**
 * 主界面 - 选择测试模式
 */
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<Button>(R.id.btn_host_kernel_test).setOnClickListener {
            startActivity(Intent(this, UsbHostKernelTestActivity::class.java))
        }

        findViewById<Button>(R.id.btn_accessory_kernel_test).setOnClickListener {
            startActivity(Intent(this, UsbAccessoryKernelTestActivity::class.java))
        }
    }
}
