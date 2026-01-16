# LanClientKernel Race Condition Test Plan

## 测试目标

验证 LanClientKernel 中 `initCommand` 和 `connect` 操作之间的竞态条件问题，确保：
1. 连接回调不会被错误替换
2. 状态更新保持一致性
3. 操作不会相互干扰

## 测试环境准备

### 1. 测试设备配置
- **客户端设备**: Android 设备或模拟器
- **服务端设备**: 支持 Taplink 服务的设备
- **网络环境**: 稳定的 WiFi 网络

### 2. 日志配置
```kotlin
// 在测试前启用详细日志
LogUtil.setLogLevel(LogUtil.DEBUG)
LogUtil.enableFileLogging(true)
```

### 3. 测试工具类
```kotlin
class RaceConditionTestHelper {
    private val testResults = mutableListOf<TestResult>()
    
    data class TestResult(
        val testName: String,
        val success: Boolean,
        val details: String,
        val timestamp: Long
    )
    
    fun recordResult(testName: String, success: Boolean, details: String) {
        testResults.add(TestResult(testName, success, details, System.currentTimeMillis()))
    }
    
    fun generateReport(): String {
        val successCount = testResults.count { it.success }
        val totalCount = testResults.size
        
        return buildString {
            appendLine("=== Race Condition Test Report ===")
            appendLine("Total Tests: $totalCount")
            appendLine("Passed: $successCount")
            appendLine("Failed: ${totalCount - successCount}")
            appendLine("Success Rate: ${(successCount * 100.0 / totalCount).format(2)}%")
            appendLine()
            
            testResults.forEach { result ->
                appendLine("${if (result.success) "✓" else "✗"} ${result.testName}")
                appendLine("  Details: ${result.details}")
                appendLine("  Time: ${Date(result.timestamp)}")
                appendLine()
            }
        }
    }
}
```

## 核心测试用例

### 测试用例 1: initCommand 期间触发 connect

#### 测试目的
验证在 `initCommand` 执行过程中触发 `connect` 是否会导致回调替换问题。

#### 测试步骤
```kotlin
@Test
fun testInitCommandConnectRaceCondition() {
    val testHelper = RaceConditionTestHelper()
    val latch = CountDownLatch(2)
    
    var initCommandCallbackTriggered = false
    var connectCallbackTriggered = false
    var initCommandResult: String? = null
    var connectResult: String? = null
    
    // 创建 LanClientKernel 实例
    val kernel = LanClientKernel(TEST_APP_ID, TEST_SECRET_KEY, context)
    
    // 步骤1: 启动 initCommand
    val initCommandCallback = object : ConnectionCallback {
        override fun onConnected(data: Map<String, Any?>) {
            initCommandCallbackTriggered = true
            initCommandResult = "SUCCESS"
            latch.countDown()
        }
        
        override fun onError(code: Int, message: String) {
            initCommandCallbackTriggered = true
            initCommandResult = "ERROR: $message"
            latch.countDown()
        }
        
        override fun onServiceAddressChanged(
            serviceName: String, newHost: String, newPort: Int,
            oldHost: String, oldPort: Int
        ): Boolean {
            return true
        }
    }
    
    // 步骤2: 在另一个线程中延迟触发 connect
    val connectCallback = object : ConnectionCallback {
        override fun onConnected(data: Map<String, Any?>) {
            connectCallbackTriggered = true
            connectResult = "SUCCESS"
            latch.countDown()
        }
        
        override fun onError(code: Int, message: String) {
            connectCallbackTriggered = true
            connectResult = "ERROR: $message"
            latch.countDown()
        }
        
        override fun onServiceAddressChanged(
            serviceName: String, newHost: String, newPort: Int,
            oldHost: String, oldPort: Int
        ): Boolean {
            return true
        }
    }
    
    // 同时启动两个操作
    Thread {
        kernel.initCommand("test_command", initCommandCallback)
    }.start()
    
    Thread {
        Thread.sleep(100) // 短暂延迟，模拟在 initCommand 执行期间触发
        val parseResult = ProtocolParseResult.LanProtocol("192.168.1.100", 8080)
        kernel.connect(parseResult, connectCallback)
    }.start()
    
    // 等待结果
    val completed = latch.await(10, TimeUnit.SECONDS)
    
    // 验证结果
    val success = completed && 
                  initCommandCallbackTriggered && 
                  connectCallbackTriggered &&
                  initCommandResult?.contains("SUCCESS") == true &&
                  connectResult?.contains("SUCCESS") == true
    
    testHelper.recordResult(
        "initCommand_connect_race",
        success,
        "InitCommand: $initCommandResult, Connect: $connectResult"
    )
    
    assertTrue("Race condition test failed", success)
}
```

### 测试用例 2: 快速连续连接操作

#### 测试目的
验证快速连续执行多个连接操作是否会导致状态混乱。

#### 测试步骤
```kotlin
@Test
fun testRapidConsecutiveConnections() {
    val testHelper = RaceConditionTestHelper()
    val kernel = LanClientKernel(TEST_APP_ID, TEST_SECRET_KEY, context)
    
    val connectionCount = 5
    val latch = CountDownLatch(connectionCount)
    val results = ConcurrentHashMap<Int, String>()
    
    // 快速连续发起多个连接
    repeat(connectionCount) { index ->
        Thread {
            val callback = object : ConnectionCallback {
                override fun onConnected(data: Map<String, Any?>) {
                    results[index] = "SUCCESS"
                    latch.countDown()
                }
                
                override fun onError(code: Int, message: String) {
                    results[index] = "ERROR: $message"
                    latch.countDown()
                }
                
                override fun onServiceAddressChanged(
                    serviceName: String, newHost: String, newPort: Int,
                    oldHost: String, oldPort: Int
                ): Boolean = true
            }
            
            val parseResult = ProtocolParseResult.LanProtocol("192.168.1.100", 8080)
            kernel.connect(parseResult, callback)
        }.start()
    }
    
    // 等待所有操作完成
    val completed = latch.await(15, TimeUnit.SECONDS)
    
    // 分析结果
    val successCount = results.values.count { it == "SUCCESS" }
    val errorCount = results.values.count { it.startsWith("ERROR") }
    
    val success = completed && results.size == connectionCount
    
    testHelper.recordResult(
        "rapid_consecutive_connections",
        success,
        "Completed: $completed, Success: $successCount, Errors: $errorCount"
    )
    
    assertTrue("Rapid connections test failed", success)
}
```

### 测试用例 3: 状态一致性验证

#### 测试目的
验证并发操作下连接状态的一致性。

#### 测试步骤
```kotlin
@Test
fun testConnectionStatusConsistency() {
    val testHelper = RaceConditionTestHelper()
    val kernel = LanClientKernel(TEST_APP_ID, TEST_SECRET_KEY, context)
    
    val statusHistory = Collections.synchronizedList(mutableListOf<InnerConnectionStatus>())
    val operationCount = 10
    val latch = CountDownLatch(operationCount)
    
    // 监控状态变化
    val statusMonitor = Thread {
        repeat(100) { // 监控10秒
            statusHistory.add(kernel.getCurrentStatus())
            Thread.sleep(100)
        }
    }
    statusMonitor.start()
    
    // 并发执行多个操作
    repeat(operationCount) { index ->
        Thread {
            val callback = object : ConnectionCallback {
                override fun onConnected(data: Map<String, Any?>) {
                    latch.countDown()
                }
                
                override fun onError(code: Int, message: String) {
                    latch.countDown()
                }
                
                override fun onServiceAddressChanged(
                    serviceName: String, newHost: String, newPort: Int,
                    oldHost: String, oldPort: Int
                ): Boolean = true
            }
            
            if (index % 2 == 0) {
                // 偶数索引执行连接
                val parseResult = ProtocolParseResult.LanProtocol("192.168.1.100", 8080)
                kernel.connect(parseResult, callback)
            } else {
                // 奇数索引执行断开
                Thread.sleep(50) // 短暂延迟
                kernel.disconnect()
                latch.countDown()
            }
        }.start()
    }
    
    // 等待操作完成
    latch.await(15, TimeUnit.SECONDS)
    statusMonitor.join(1000)
    
    // 分析状态变化
    val invalidTransitions = findInvalidStatusTransitions(statusHistory)
    val success = invalidTransitions.isEmpty()
    
    testHelper.recordResult(
        "status_consistency",
        success,
        "Invalid transitions: ${invalidTransitions.size}, History size: ${statusHistory.size}"
    )
    
    assertTrue("Status consistency test failed", success)
}

private fun findInvalidStatusTransitions(
    statusHistory: List<InnerConnectionStatus>
): List<Pair<InnerConnectionStatus, InnerConnectionStatus>> {
    val invalidTransitions = mutableListOf<Pair<InnerConnectionStatus, InnerConnectionStatus>>()
    
    for (i in 1 until statusHistory.size) {
        val prev = statusHistory[i - 1]
        val current = statusHistory[i]
        
        // 定义无效的状态转换
        val isInvalid = when (prev) {
            InnerConnectionStatus.DISCONNECTED -> current == InnerConnectionStatus.ERROR
            InnerConnectionStatus.CONNECTING -> current == InnerConnectionStatus.DISCONNECTED
            InnerConnectionStatus.CONNECTED -> current == InnerConnectionStatus.CONNECTING
            else -> false
        }
        
        if (isInvalid) {
            invalidTransitions.add(prev to current)
        }
    }
    
    return invalidTransitions
}
```

### 测试用例 4: 服务发现干扰测试

#### 测试目的
验证服务发现回调是否会干扰正在进行的连接操作。

#### 测试步骤
```kotlin
@Test
fun testServiceDiscoveryInterference() {
    val testHelper = RaceConditionTestHelper()
    val kernel = LanClientKernel(TEST_APP_ID, TEST_SECRET_KEY, context)
    
    val latch = CountDownLatch(1)
    var connectionResult: String? = null
    var serviceDiscoveryTriggered = false
    
    // 启动连接操作
    val callback = object : ConnectionCallback {
        override fun onConnected(data: Map<String, Any?>) {
            connectionResult = "SUCCESS"
            latch.countDown()
        }
        
        override fun onError(code: Int, message: String) {
            connectionResult = "ERROR: $message"
            latch.countDown()
        }
        
        override fun onServiceAddressChanged(
            serviceName: String, newHost: String, newPort: Int,
            oldHost: String, oldPort: Int
        ): Boolean {
            serviceDiscoveryTriggered = true
            return false // 不触发重连，避免干扰测试
        }
    }
    
    // 启动连接
    Thread {
        val parseResult = ProtocolParseResult.LanProtocol("192.168.1.100", 8080)
        kernel.connect(parseResult, callback)
    }.start()
    
    // 模拟服务发现触发
    Thread {
        Thread.sleep(200) // 在连接过程中触发
        // 这里需要模拟服务发现回调，实际实现可能需要 mock
        // 或者通过反射调用内部方法
    }.start()
    
    // 等待结果
    val completed = latch.await(10, TimeUnit.SECONDS)
    
    val success = completed && connectionResult?.contains("SUCCESS") == true
    
    testHelper.recordResult(
        "service_discovery_interference",
        success,
        "Connection: $connectionResult, Service discovery triggered: $serviceDiscoveryTriggered"
    )
    
    assertTrue("Service discovery interference test failed", success)
}
```

## 压力测试

### 长时间并发测试
```kotlin
@Test
fun testLongTermConcurrency() {
    val testHelper = RaceConditionTestHelper()
    val kernel = LanClientKernel(TEST_APP_ID, TEST_SECRET_KEY, context)
    
    val testDurationMs = 60000 // 1分钟
    val startTime = System.currentTimeMillis()
    val results = ConcurrentHashMap<String, Int>()
    
    val executor = Executors.newFixedThreadPool(10)
    
    while (System.currentTimeMillis() - startTime < testDurationMs) {
        executor.submit {
            val operationType = listOf("connect", "disconnect", "initCommand").random()
            
            try {
                when (operationType) {
                    "connect" -> {
                        val parseResult = ProtocolParseResult.LanProtocol("192.168.1.100", 8080)
                        kernel.connect(parseResult, createTestCallback())
                    }
                    "disconnect" -> {
                        kernel.disconnect()
                    }
                    "initCommand" -> {
                        kernel.initCommand("test", createTestCallback())
                    }
                }
                
                results.merge("${operationType}_success", 1, Int::plus)
            } catch (e: Exception) {
                results.merge("${operationType}_error", 1, Int::plus)
            }
        }
        
        Thread.sleep(100)
    }
    
    executor.shutdown()
    executor.awaitTermination(10, TimeUnit.SECONDS)
    
    val totalOperations = results.values.sum()
    val errorCount = results.filterKeys { it.contains("error") }.values.sum()
    val errorRate = errorCount.toDouble() / totalOperations
    
    val success = errorRate < 0.1 // 错误率低于10%认为成功
    
    testHelper.recordResult(
        "long_term_concurrency",
        success,
        "Total ops: $totalOperations, Errors: $errorCount, Error rate: ${(errorRate * 100).format(2)}%"
    )
    
    assertTrue("Long term concurrency test failed", success)
}

private fun createTestCallback(): ConnectionCallback {
    return object : ConnectionCallback {
        override fun onConnected(data: Map<String, Any?>) {}
        override fun onError(code: Int, message: String) {}
        override fun onServiceAddressChanged(
            serviceName: String, newHost: String, newPort: Int,
            oldHost: String, oldPort: Int
        ): Boolean = true
    }
}
```

## 测试执行指南

### 1. 测试环境设置
```bash
# 确保测试设备连接到稳定网络
adb shell settings put global wifi_sleep_policy 2

# 启用详细日志
adb shell setprop log.tag.LanClientKernel DEBUG
adb shell setprop log.tag.ConnectionManager DEBUG
```

### 2. 执行测试
```bash
# 运行单个测试
./gradlew :lib_taplink_communication:testDebugUnitTest --tests "*RaceConditionTest*"

# 运行所有相关测试
./gradlew :lib_taplink_communication:testDebugUnitTest --tests "*LanClient*"
```

### 3. 日志收集
```bash
# 收集测试期间的日志
adb logcat -s LanClientKernel,ConnectionManager,ServiceDiscoveryManager > race_condition_test.log
```

### 4. 结果分析
- 检查所有测试是否通过
- 分析日志中的异常和错误
- 验证状态转换是否正确
- 确认回调是否按预期触发

## 预期结果

### 成功标准
1. 所有测试用例通过率 > 95%
2. 没有回调替换问题
3. 状态转换保持一致性
4. 没有资源泄漏

### 失败指标
1. 回调未触发或触发错误
2. 状态转换异常
3. 连接泄漏或资源未释放
4. 异常或崩溃

## 问题修复验证

在实施修复方案后，重新运行所有测试用例，确保：
1. 竞态条件问题得到解决
2. 不引入新的问题
3. 性能没有显著下降
4. 功能保持正常