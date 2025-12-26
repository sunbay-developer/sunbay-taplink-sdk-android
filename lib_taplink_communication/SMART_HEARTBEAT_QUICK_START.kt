package com.sunmi.tapro.taplink.demo

import android.app.Application
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.sunmi.tapro.taplink.communication.utils.AppStateMonitor
import com.sunmi.tapro.taplink.communication.utils.SmartHeartbeatConfig
import com.sunmi.tapro.taplink.communication.utils.NetworkQualityMonitor
import com.sunmi.tapro.taplink.communication.util.LogUtil

/**
 * 智能心跳机制快速集成示例
 * 
 * 这个文件展示了如何在您的应用中快速集成智能心跳机制
 */

// ============================================
// 步骤 1: 在 Application 中初始化
// ============================================

class MyApplication : Application() {
    
    override fun onCreate() {
        super.onCreate()
        
        // 必须：初始化应用状态监听器
        AppStateMonitor.getInstance().initialize(this)
        
        LogUtil.d("MyApplication", "Smart heartbeat system initialized")
        
        // 可选：自定义心跳配置
        configureHeartbeat()
    }
    
    /**
     * 可选：根据业务需求自定义心跳配置
     */
    private fun configureHeartbeat() {
        // 示例 1: 标准配置（推荐用于大多数应用）
        SmartHeartbeatConfig.getInstance().setCustomConfig(
            foregroundInterval = 30_000L,    // 前台30秒
            backgroundInterval = 60_000L,    // 后台60秒
            lowMemoryInterval = 120_000L,    // 低内存120秒
            timeout = 15_000L,               // 超时15秒
            maxRetryCount = 3                // 最大重试3次
        )
        
        // 示例 2: 实时性要求高的应用（如支付、即时通讯）
        /*
        SmartHeartbeatConfig.getInstance().setCustomConfig(
            foregroundInterval = 15_000L,    // 前台15秒
            backgroundInterval = 30_000L,    // 后台30秒
            timeout = 10_000L,               // 超时10秒
            maxRetryCount = 2                // 快速失败检测
        )
        */
        
        // 示例 3: 省电优先的应用
        /*
        SmartHeartbeatConfig.getInstance().setCustomConfig(
            foregroundInterval = 45_000L,    // 前台45秒
            backgroundInterval = 120_000L,   // 后台2分钟
            lowMemoryInterval = 300_000L,    // 低内存5分钟
            timeout = 20_000L,               // 超时20秒
            maxRetryCount = 5                // 更多重试机会
        )
        */
    }
}

// ============================================
// 步骤 2: 在 Activity 中使用（可选）
// ============================================

class MainActivity : AppCompatActivity() {
    
    private var heartbeatConfigListener: SmartHeartbeatConfig.HeartbeatConfigChangeListener? = null
    private var networkQualityListener: NetworkQualityMonitor.NetworkQualityChangeListener? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 可选：监听心跳配置变化
        setupHeartbeatMonitoring()
        
        // 可选：监听网络质量变化
        setupNetworkQualityMonitoring()
    }
    
    /**
     * 可选：监听心跳配置变化
     */
    private fun setupHeartbeatMonitoring() {
        heartbeatConfigListener = object : SmartHeartbeatConfig.HeartbeatConfigChangeListener {
            override fun onHeartbeatConfigChanged(newConfig: SmartHeartbeatConfig.HeartbeatConfig) {
                LogUtil.d("MainActivity", "Heartbeat config changed:")
                LogUtil.d("MainActivity", "  - Current interval: ${SmartHeartbeatConfig.getInstance().getCurrentInterval()}ms")
                LogUtil.d("MainActivity", "  - Timeout: ${newConfig.timeout}ms")
                LogUtil.d("MainActivity", "  - Max retry: ${newConfig.maxRetryCount}")
                
                // 可以在这里更新 UI 或执行其他逻辑
                updateConnectionStatusUI()
            }
        }
        
        SmartHeartbeatConfig.getInstance().addConfigChangeListener(heartbeatConfigListener!!)
    }
    
    /**
     * 可选：监听网络质量变化
     */
    private fun setupNetworkQualityMonitoring() {
        networkQualityListener = object : NetworkQualityMonitor.NetworkQualityChangeListener {
            override fun onNetworkQualityChanged(
                oldQuality: SmartHeartbeatConfig.NetworkQuality,
                newQuality: SmartHeartbeatConfig.NetworkQuality
            ) {
                LogUtil.d("MainActivity", "Network quality changed: $oldQuality -> $newQuality")
                
                // 根据网络质量更新 UI
                when (newQuality) {
                    SmartHeartbeatConfig.NetworkQuality.EXCELLENT -> {
                        showNetworkStatus("网络优秀", android.graphics.Color.GREEN)
                    }
                    SmartHeartbeatConfig.NetworkQuality.GOOD -> {
                        showNetworkStatus("网络良好", android.graphics.Color.GREEN)
                    }
                    SmartHeartbeatConfig.NetworkQuality.FAIR -> {
                        showNetworkStatus("网络一般", android.graphics.Color.YELLOW)
                    }
                    SmartHeartbeatConfig.NetworkQuality.POOR -> {
                        showNetworkStatus("网络较差", android.graphics.Color.rgb(255, 165, 0))
                    }
                    SmartHeartbeatConfig.NetworkQuality.VERY_POOR -> {
                        showNetworkStatus("网络很差", android.graphics.Color.RED)
                    }
                }
            }
        }
        
        NetworkQualityMonitor.getInstance(this).addQualityChangeListener(networkQualityListener!!)
    }
    
    override fun onDestroy() {
        super.onDestroy()
        
        // 清理监听器
        heartbeatConfigListener?.let {
            SmartHeartbeatConfig.getInstance().removeConfigChangeListener(it)
        }
        
        networkQualityListener?.let {
            NetworkQualityMonitor.getInstance(this).removeQualityChangeListener(it)
        }
    }
    
    /**
     * 更新连接状态 UI
     */
    private fun updateConnectionStatusUI() {
        // 实现您的 UI 更新逻辑
        // 例如：更新状态栏、显示提示信息等
    }
    
    /**
     * 显示网络状态
     */
    private fun showNetworkStatus(message: String, color: Int) {
        // 实现您的网络状态显示逻辑
        // 例如：Toast、Snackbar、状态指示器等
        LogUtil.d("MainActivity", "Network status: $message")
    }
}

// ============================================
// 步骤 3: 高级用法示例
// ============================================

/**
 * 示例：在服务中使用智能心跳
 */
class ConnectionService : android.app.Service() {
    
    private val heartbeatListener = object : SmartHeartbeatConfig.HeartbeatConfigChangeListener {
        override fun onHeartbeatConfigChanged(newConfig: SmartHeartbeatConfig.HeartbeatConfig) {
            // 心跳配置变化时的处理
            LogUtil.d("ConnectionService", "Heartbeat config updated in service")
        }
    }
    
    override fun onCreate() {
        super.onCreate()
        SmartHeartbeatConfig.getInstance().addConfigChangeListener(heartbeatListener)
    }
    
    override fun onDestroy() {
        super.onDestroy()
        SmartHeartbeatConfig.getInstance().removeConfigChangeListener(heartbeatListener)
    }
    
    override fun onBind(intent: android.content.Intent?): android.os.IBinder? = null
}

/**
 * 示例：动态调整心跳配置
 */
class DynamicHeartbeatManager {
    
    /**
     * 根据业务场景动态调整心跳配置
     */
    fun adjustHeartbeatForScenario(scenario: BusinessScenario) {
        when (scenario) {
            BusinessScenario.PAYMENT_IN_PROGRESS -> {
                // 支付进行中：使用更频繁的心跳
                SmartHeartbeatConfig.getInstance().setCustomConfig(
                    foregroundInterval = 10_000L,
                    maxRetryCount = 2
                )
                LogUtil.d("DynamicHeartbeatManager", "Adjusted heartbeat for payment scenario")
            }
            
            BusinessScenario.IDLE -> {
                // 空闲状态：使用省电配置
                SmartHeartbeatConfig.getInstance().setCustomConfig(
                    foregroundInterval = 60_000L,
                    backgroundInterval = 180_000L
                )
                LogUtil.d("DynamicHeartbeatManager", "Adjusted heartbeat for idle scenario")
            }
            
            BusinessScenario.NORMAL -> {
                // 正常状态：恢复默认配置
                SmartHeartbeatConfig.getInstance().resetToDefault()
                LogUtil.d("DynamicHeartbeatManager", "Reset heartbeat to default")
            }
        }
    }
    
    enum class BusinessScenario {
        PAYMENT_IN_PROGRESS,  // 支付进行中
        IDLE,                 // 空闲
        NORMAL                // 正常
    }
}

/**
 * 示例：手动触发网络质量检测
 */
class NetworkDiagnostics(private val context: android.content.Context) {
    
    /**
     * 在关键操作前检查网络质量
     */
    fun checkNetworkBeforeCriticalOperation(onComplete: (SmartHeartbeatConfig.NetworkQuality) -> Unit) {
        val monitor = NetworkQualityMonitor.getInstance(context)
        
        // 添加临时监听器
        val listener = object : NetworkQualityMonitor.NetworkQualityChangeListener {
            override fun onNetworkQualityChanged(
                oldQuality: SmartHeartbeatConfig.NetworkQuality,
                newQuality: SmartHeartbeatConfig.NetworkQuality
            ) {
                onComplete(newQuality)
                monitor.removeQualityChangeListener(this)
            }
        }
        
        monitor.addQualityChangeListener(listener)
        monitor.checkQualityNow()
    }
}

// ============================================
// 使用说明
// ============================================

/*
 * 最小集成步骤：
 * 
 * 1. 在 Application.onCreate() 中调用：
 *    AppStateMonitor.getInstance().initialize(this)
 * 
 * 2. 完成！智能心跳会自动工作
 * 
 * 
 * 可选的高级功能：
 * 
 * - 自定义心跳配置
 * - 监听配置变化
 * - 监听网络质量变化
 * - 动态调整配置
 * - 手动触发网络检测
 * 
 * 
 * 日志监控：
 * 
 * 使用 logcat 过滤以下标签查看智能心跳日志：
 * - AppStateMonitor
 * - SmartHeartbeatConfig
 * - NetworkQualityMonitor
 * - WebSocketClientKernel
 * 
 * 命令示例：
 * adb logcat | grep -E "AppStateMonitor|SmartHeartbeatConfig|NetworkQualityMonitor"
 */