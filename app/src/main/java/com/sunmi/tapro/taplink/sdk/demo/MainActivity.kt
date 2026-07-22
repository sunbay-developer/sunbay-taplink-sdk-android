package com.sunmi.tapro.taplink.sdk.demo

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.sunmi.tapro.taplink.demo.R

/**
 * 主界面 - 选择测试模式
 */
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        bindLaunchButton("btn_host_kernel_test", UsbHostKernelTestActivity::class.java)
        bindLaunchButton("btn_accessory_kernel_test", UsbAccessoryKernelTestActivity::class.java)
    }

    private fun bindLaunchButton(idName: String, target: Class<*>) {
        val buttonId = resources.getIdentifier(idName, "id", packageName)
        if (buttonId != 0) {
            findViewById<Button>(buttonId)?.setOnClickListener {
                startActivity(Intent(this, target))
            }
        }
    }
}
