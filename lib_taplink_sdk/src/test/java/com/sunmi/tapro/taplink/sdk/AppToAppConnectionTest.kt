package com.sunmi.tapro.taplink.sdk

import android.content.Context
import android.util.Log
import com.sunmi.tapro.taplink.sdk.api.TaplinkApi
import com.sunmi.tapro.taplink.sdk.callback.ConnectionListener
import com.sunmi.tapro.taplink.sdk.config.TaplinkConfig
import com.sunmi.tapro.taplink.sdk.enums.ConnectionMode
import com.sunmi.tapro.taplink.sdk.enums.LogLevel
import com.sunmi.tapro.taplink.sdk.error.ConnectionError
import com.sunmi.tapro.taplink.sdk.impl.TaplinkApiImpl
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * APP TO APP 连接测试
 * 
 * 用于测试和诊断 APP TO APP 连接问题
 */
class AppToAppConnectionTest {

    companion object {
        private const val TAG = "AppToAppConnectionTest"
        private const val TEST_APP_ID = "test_app_id"
        private const val TEST_MERCHANT_ID = "test_merchant_id"
        private const val TEST_SECRET_KEY = "test_secret_key"
        private const val CONNECTION_TIMEOUT_SECONDS = 30L
    }

    @Mock
    private lateinit var mockContext: Context

    private lateinit var taplinkApi: TaplinkApi

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        taplinkApi = TaplinkApiImpl()
    }

    /**
     * 测试基本的 APP TO APP 连接
     */
    @Test
    fun testBasicAppToAppConnection() {
        Log.d(TAG, "=== Testing Basic APP TO APP Connection ===")

        // 创建配置
        val config = TaplinkConfig(
            appId = TEST_APP_ID,
            merchantId = TEST_MERCHANT_ID,
            secretKey = TEST_SECRET_KEY
        )
            .setConnectionMode(ConnectionMode.APP_TO_APP)
            .setLogEnabled(true)
            .setLogLevel(LogLevel.DEBUG)

        // 初始化 SDK
        taplinkApi.init(mockContext, config)

        // 创建连接监听器
        val connectionLatch = CountDownLatch(1)
        var connectionResult: ConnectionResult? = null

        val connectionListener = object : ConnectionListener {
            override fun onConnected(deviceId: String, taproVersion: String) {
                Log.d(TAG, "✅ Connection successful!")
                Log.d(TAG, "Device ID: $deviceId")
                Log.d(TAG, "Tapro Version: $taproVersion")
                connectionResult = ConnectionResult.Success(deviceId, taproVersion)
                connectionLatch.countDown()
            }

            override fun onError(error: ConnectionError) {
                Log.e(TAG, "❌ Connection failed!")
                Log.e(TAG, "Error Code: ${error.code}")
                Log.e(TAG, "Error Message: ${error.message}")
                connectionResult = ConnectionResult.Error(error)
                connectionLatch.countDown()
            }

            override fun onDisconnected(reason: String) {
                Log.w(TAG, "⚠️ Connection disconnected: $reason")
            }

            override fun onReconnecting(currentAttempt: Int, maxAttempts: Int) {
                Log.i(TAG, "🔄 Reconnecting... ($currentAttempt/$maxAttempts)")
            }
        }

        // 开始连接
        Log.d(TAG, "Starting connection...")
        taplinkApi.connect(null, connectionListener)

        // 等待连接结果
        try {
            val completed = connectionLatch.await(CONNECTION_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            
            if (completed) {
                when (val result = connectionResult) {
                    is ConnectionResult.Success -> {
                        Log.d(TAG, "✅ Test PASSED: Connection successful")
                        Log.d(TAG, "Connected to device: ${result.deviceId}")
                        Log.d(TAG, "Tapro version: ${result.taproVersion}")
                        
                        // 验证连接状态
                        assert(taplinkApi.isConnected()) { "SDK should report as connected" }
                        assert(taplinkApi.getConnectedDeviceId() == result.deviceId) { "Device ID should match" }
                        assert(taplinkApi.getConnectionMode() == ConnectionMode.APP_TO_APP.name) { "Connection mode should be APP_TO_APP" }
                    }
                    
                    is ConnectionResult.Error -> {
                        Log.e(TAG, "❌ Test FAILED: Connection error")
                        Log.e(TAG, "Error: ${result.error.code} - ${result.error.message}")
                        
                        // 分析错误类型并提供建议
                        analyzeConnectionError(result.error)
                        
                        throw AssertionError("Connection failed: ${result.error.message}")
                    }
                    
                    null -> {
                        Log.e(TAG, "❌ Test FAILED: No connection result received")
                        throw AssertionError("No connection result received")
                    }
                }
            } else {
                Log.e(TAG, "❌ Test FAILED: Connection timeout after ${CONNECTION_TIMEOUT_SECONDS} seconds")
                Log.e(TAG, "Possible causes:")
                Log.e(TAG, "1. Tapro app is not installed")
                Log.e(TAG, "2. Tapro service is not responding")
                Log.e(TAG, "3. Service binding failed")
                Log.e(TAG, "4. INIT command is stuck")
                
                throw AssertionError("Connection timeout")
            }
        } catch (e: InterruptedException) {
            Log.e(TAG, "❌ Test interrupted", e)
            throw AssertionError("Test interrupted", e)
        } finally {
            // 清理连接
            taplinkApi.disconnect()
        }
    }

    /**
     * 测试连接状态检查
     */
    @Test
    fun testConnectionStatusCheck() {
        Log.d(TAG, "=== Testing Connection Status Check ===")

        // 初始状态应该是未连接
        assert(!taplinkApi.isConnected()) { "Should not be connected initially" }
        assert(taplinkApi.getConnectedDeviceId() == null) { "Device ID should be null initially" }
        assert(taplinkApi.getConnectionMode() == null) { "Connection mode should be null initially" }

        Log.d(TAG, "✅ Initial state check passed")
    }

    /**
     * 测试重复连接
     */
    @Test
    fun testMultipleConnections() {
        Log.d(TAG, "=== Testing Multiple Connections ===")

        val config = TaplinkConfig(
            appId = TEST_APP_ID,
            merchantId = TEST_MERCHANT_ID,
            secretKey = TEST_SECRET_KEY
        ).setConnectionMode(ConnectionMode.APP_TO_APP)

        taplinkApi.init(mockContext, config)

        // 第一次连接
        val firstConnectionLatch = CountDownLatch(1)
        taplinkApi.connect(null, object : ConnectionListener {
            override fun onConnected(deviceId: String, taproVersion: String) {
                Log.d(TAG, "First connection successful")
                firstConnectionLatch.countDown()
            }

            override fun onError(error: ConnectionError) {
                Log.e(TAG, "First connection failed: ${error.message}")
                firstConnectionLatch.countDown()
            }

            override fun onDisconnected(reason: String) {}
            override fun onReconnecting(currentAttempt: Int, maxAttempts: Int) {}
        })

        // 等待第一次连接完成
        firstConnectionLatch.await(10, TimeUnit.SECONDS)

        // 断开连接
        taplinkApi.disconnect()
        assert(!taplinkApi.isConnected()) { "Should be disconnected after disconnect()" }

        // 第二次连接
        val secondConnectionLatch = CountDownLatch(1)
        taplinkApi.connect(null, object : ConnectionListener {
            override fun onConnected(deviceId: String, taproVersion: String) {
                Log.d(TAG, "Second connection successful")
                secondConnectionLatch.countDown()
            }

            override fun onError(error: ConnectionError) {
                Log.e(TAG, "Second connection failed: ${error.message}")
                secondConnectionLatch.countDown()
            }

            override fun onDisconnected(reason: String) {}
            override fun onReconnecting(currentAttempt: Int, maxAttempts: Int) {}
        })

        secondConnectionLatch.await(10, TimeUnit.SECONDS)
        
        Log.d(TAG, "✅ Multiple connections test completed")
    }

    /**
     * 分析连接错误并提供建议
     */
    private fun analyzeConnectionError(error: ConnectionError) {
        Log.d(TAG, "=== Analyzing Connection Error ===")
        
        when (error.code) {
            "C20" -> {
                Log.e(TAG, "SDK not initialized - call init() first")
            }
            
            "C22" -> {
                Log.e(TAG, "Service not found - check if Tapro app is installed")
                Log.e(TAG, "Suggestions:")
                Log.e(TAG, "1. Install Tapro app")
                Log.e(TAG, "2. Check if service is declared in AndroidManifest.xml")
                Log.e(TAG, "3. Verify package name and action")
            }
            
            "C25" -> {
                Log.e(TAG, "Service binding failed")
                Log.e(TAG, "Suggestions:")
                Log.e(TAG, "1. Check if Tapro app is running")
                Log.e(TAG, "2. Restart Tapro app")
                Log.e(TAG, "3. Check device logs for crashes")
            }
            
            "C36" -> {
                Log.e(TAG, "Service connection died")
                Log.e(TAG, "Suggestions:")
                Log.e(TAG, "1. Tapro service crashed - check logs")
                Log.e(TAG, "2. AIDL interface mismatch")
                Log.e(TAG, "3. Service returned null binder")
            }
            
            "INIT_FAILED" -> {
                Log.e(TAG, "INIT command failed")
                Log.e(TAG, "Suggestions:")
                Log.e(TAG, "1. Check appId and secretKey")
                Log.e(TAG, "2. Verify authentication with Tapro team")
                Log.e(TAG, "3. Check Tapro app authentication logic")
            }
            
            else -> {
                Log.e(TAG, "Unknown error code: ${error.code}")
                Log.e(TAG, "Message: ${error.message}")
            }
        }
    }

    /**
     * 连接结果密封类
     */
    sealed class ConnectionResult {
        data class Success(val deviceId: String, val taproVersion: String) : ConnectionResult()
        data class Error(val error: ConnectionError) : ConnectionResult()
    }
}

/**
 * 手动测试工具类
 * 
 * 可以在实际设备上运行的测试工具
 */
class AppToAppConnectionTester {
    
    companion object {
        private const val TAG = "AppToAppTester"
        
        /**
         * 运行完整的连接测试
         */
        fun runFullTest(context: Context, appId: String, merchantId: String, secretKey: String) {
            Log.d(TAG, "🚀 Starting APP TO APP Connection Test")
            Log.d(TAG, "========================================")
            
            val tester = AppToAppConnectionTester()
            tester.testConnection(context, appId, merchantId, secretKey)
        }
    }
    
    private fun testConnection(context: Context, appId: String, merchantId: String, secretKey: String) {
        val taplinkApi = TaplinkApiImpl()
        
        // 1. 初始化测试
        Log.d(TAG, "1️⃣ Testing SDK initialization...")
        val config = TaplinkConfig(
            appId = appId,
            merchantId = merchantId,
            secretKey = secretKey
        )
            .setConnectionMode(ConnectionMode.APP_TO_APP)
            .setLogEnabled(true)
            .setLogLevel(LogLevel.DEBUG)
        
        try {
            taplinkApi.init(context, config)
            Log.d(TAG, "✅ SDK initialization successful")
        } catch (e: Exception) {
            Log.e(TAG, "❌ SDK initialization failed", e)
            return
        }
        
        // 2. 连接测试
        Log.d(TAG, "2️⃣ Testing APP TO APP connection...")
        val connectionLatch = CountDownLatch(1)
        var testResult = "UNKNOWN"
        
        val startTime = System.currentTimeMillis()
        
        taplinkApi.connect(null, object : ConnectionListener {
            override fun onConnected(deviceId: String, taproVersion: String) {
                val duration = System.currentTimeMillis() - startTime
                Log.d(TAG, "✅ Connection successful in ${duration}ms")
                Log.d(TAG, "Device ID: $deviceId")
                Log.d(TAG, "Tapro Version: $taproVersion")
                Log.d(TAG, "Connection Mode: ${taplinkApi.getConnectionMode()}")
                testResult = "SUCCESS"
                connectionLatch.countDown()
            }
            
            override fun onError(error: ConnectionError) {
                val duration = System.currentTimeMillis() - startTime
                Log.e(TAG, "❌ Connection failed after ${duration}ms")
                Log.e(TAG, "Error Code: ${error.code}")
                Log.e(TAG, "Error Message: ${error.message}")
                testResult = "FAILED: ${error.code} - ${error.message}"
                connectionLatch.countDown()
            }
            
            override fun onDisconnected(reason: String) {
                Log.w(TAG, "⚠️ Disconnected: $reason")
            }
            
            override fun onReconnecting(currentAttempt: Int, maxAttempts: Int) {
                Log.i(TAG, "🔄 Reconnecting... ($currentAttempt/$maxAttempts)")
            }
        })
        
        // 等待结果
        try {
            val completed = connectionLatch.await(30, TimeUnit.SECONDS)
            if (!completed) {
                Log.e(TAG, "❌ Connection timeout after 30 seconds")
                testResult = "TIMEOUT"
            }
        } catch (e: InterruptedException) {
            Log.e(TAG, "❌ Test interrupted", e)
            testResult = "INTERRUPTED"
        }
        
        // 3. 结果总结
        Log.d(TAG, "3️⃣ Test Summary")
        Log.d(TAG, "========================================")
        Log.d(TAG, "Result: $testResult")
        Log.d(TAG, "SDK Version: ${taplinkApi.getVersion()}")
        Log.d(TAG, "Is Connected: ${taplinkApi.isConnected()}")
        
        if (taplinkApi.isConnected()) {
            Log.d(TAG, "Connected Device: ${taplinkApi.getConnectedDeviceId()}")
            Log.d(TAG, "Connection Mode: ${taplinkApi.getConnectionMode()}")
            Log.d(TAG, "Tapro Version: ${taplinkApi.getTaproVersion()}")
        }
        
        // 清理
        taplinkApi.disconnect()
        Log.d(TAG, "🏁 Test completed")
    }
}