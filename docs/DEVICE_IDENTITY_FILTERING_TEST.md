# 设备身份过滤机制测试指南

## 测试概述

本文档提供了设备身份过滤机制的完整测试指南，确保只有同一设备的地址变化才会触发重连，避免对不同设备的错误重连。

## 测试环境准备

### 1. 测试设备配置

```kotlin
// 测试设备配置
data class TestDevice(
    val deviceId: String,
    val serviceName: String,
    val host: String,
    val port: Int
)

val deviceA = TestDevice(
    deviceId = "ABC123",
    serviceName = "TaproService_ABC123",
    host = "192.168.1.100",
    port = 8080
)

val deviceB = TestDevice(
    deviceId = "XYZ789",
    serviceName = "TaproService_XYZ789",
    host = "192.168.1.101",
    port = 8080
)

val deviceUnknown = TestDevice(
    deviceId = "unknown",
    serviceName = "TaproService_unknown",
    host = "192.168.1.102",
    port = 8080
)
```

### 2. 测试工具类

```kotlin
class DeviceIdentityFilteringTestHelper {
    
    fun simulateServiceDiscovery(
        connectionManager: ConnectionManager,
        device: TestDevice,
        oldHost: String = "",
        oldPort: Int = -1
    ): Boolean {
        return connectionManager.onServiceAddressChanged(
            newServiceName = device.serviceName,
            newHost = device.host,
            newPort = device.port,
            oldHost = oldHost,
            oldPort = oldPort
        )
    }
    
    fun setCurrentDevice(
        connectionManager: ConnectionManager,
        deviceId: String?
    ) {
        // 通过反射或测试接口设置当前连接的设备ID
        connectionManager.connectedDeviceId = deviceId
    }
    
    fun setLastConnectedDevice(
        reconnectManager: ReconnectManager,
        deviceId: String?
    ) {
        // 设置上次连接的设备ID
        reconnectManager.setLastConnectedDeviceId(deviceId)
    }
}
```

## 核心测试用例

### 1. 同一设备地址变化测试

#### 测试用例 1.1: 同一设备IP地址变化
```kotlin
@Test
fun testSameDeviceIpAddressChange() {
    val helper = DeviceIdentityFilteringTestHelper()
    val connectionManager = createConnectionManager()
    
    // 设置当前连接的设备
    helper.setCurrentDevice(connectionManager, "ABC123")
    
    // 模拟同一设备的IP地址变化
    val shouldReconnect = helper.simulateServiceDiscovery(
        connectionManager = connectionManager,
        device = deviceA.copy(host = "192.168.1.105"), // IP变化
        oldHost = "192.168.1.100",
        oldPort = 8080
    )
    
    assertTrue("应该重连到同一设备的新IP地址", shouldReconnect)
}
```

#### 测试用例 1.2: 同一设备端口变化
```kotlin
@Test
fun testSameDevicePortChange() {
    val helper = DeviceIdentityFilteringTestHelper()
    val connectionManager = createConnectionManager()
    
    helper.setCurrentDevice(connectionManager, "ABC123")
    
    val shouldReconnect = helper.simulateServiceDiscovery(
        connectionManager = connectionManager,
        device = deviceA.copy(port = 8081), // 端口变化
        oldHost = "192.168.1.100",
        oldPort = 8080
    )
    
    assertTrue("应该重连到同一设备的新端口", shouldReconnect)
}
```

#### 测试用例 1.3: 同一设备IP和端口都变化
```kotlin
@Test
fun testSameDeviceIpAndPortChange() {
    val helper = DeviceIdentityFilteringTestHelper()
    val connectionManager = createConnectionManager()
    
    helper.setCurrentDevice(connectionManager, "ABC123")
    
    val shouldReconnect = helper.simulateServiceDiscovery(
        connectionManager = connectionManager,
        device = deviceA.copy(host = "192.168.1.105", port = 8081),
        oldHost = "192.168.1.100",
        oldPort = 8080
    )
    
    assertTrue("应该重连到同一设备的新地址", shouldReconnect)
}
```

### 2. 不同设备过滤测试

#### 测试用例 2.1: 不同设备发现应被忽略
```kotlin
@Test
fun testDifferentDeviceIgnored() {
    val helper = DeviceIdentityFilteringTestHelper()
    val connectionManager = createConnectionManager()
    
    // 当前连接到设备A
    helper.setCurrentDevice(connectionManager, "ABC123")
    
    // 发现设备B
    val shouldReconnect = helper.simulateServiceDiscovery(
        connectionManager = connectionManager,
        device = deviceB, // 不同设备
        oldHost = "192.168.1.100",
        oldPort = 8080
    )
    
    assertFalse("不应该重连到不同的设备", shouldReconnect)
}
```

#### 测试用例 2.2: 多个不同设备发现
```kotlin
@Test
fun testMultipleDifferentDevicesIgnored() {
    val helper = DeviceIdentityFilteringTestHelper()
    val connectionManager = createConnectionManager()
    
    helper.setCurrentDevice(connectionManager, "ABC123")
    
    val devices = listOf(
        deviceB,
        TestDevice("DEF456", "TaproService_DEF456", "192.168.1.103", 8080),
        TestDevice("GHI789", "TaproService_GHI789", "192.168.1.104", 8080)
    )
    
    devices.forEach { device ->
        val shouldReconnect = helper.simulateServiceDiscovery(
            connectionManager = connectionManager,
            device = device,
            oldHost = "192.168.1.100",
            oldPort = 8080
        )
        
        assertFalse("不应该重连到不同的设备: ${device.deviceId}", shouldReconnect)
    }
}
```

### 3. 设备身份匹配策略测试

#### 测试用例 3.1: 精确服务名匹配
```kotlin
@Test
fun testExactServiceNameMatch() {
    val helper = DeviceIdentityFilteringTestHelper()
    val connectionManager = createConnectionManager()
    
    helper.setCurrentDevice(connectionManager, "ABC123")
    
    // 精确的服务名格式
    val shouldReconnect = helper.simulateServiceDiscovery(
        connectionManager = connectionManager,
        device = TestDevice("ABC123", "TaproService_ABC123", "192.168.1.105", 8080),
        oldHost = "192.168.1.100",
        oldPort = 8080
    )
    
    assertTrue("精确服务名匹配应该成功", shouldReconnect)
}
```

#### 测试用例 3.2: 服务名后缀匹配
```kotlin
@Test
fun testServiceNameSuffixMatch() {
    val helper = DeviceIdentityFilteringTestHelper()
    val connectionManager = createConnectionManager()
    
    helper.setCurrentDevice(connectionManager, "ABC123")
    
    val testCases = listOf(
        "CustomService_ABC123",
        "AnyPrefix_ABC123",
        "ABC123" // 直接设备ID
    )
    
    testCases.forEach { serviceName ->
        val shouldReconnect = helper.simulateServiceDiscovery(
            connectionManager = connectionManager,
            device = TestDevice("ABC123", serviceName, "192.168.1.105", 8080),
            oldHost = "192.168.1.100",
            oldPort = 8080
        )
        
        assertTrue("服务名后缀匹配应该成功: $serviceName", shouldReconnect)
    }
}
```

#### 测试用例 3.3: 服务名模式匹配
```kotlin
@Test
fun testServiceNamePatternMatch() {
    val helper = DeviceIdentityFilteringTestHelper()
    val connectionManager = createConnectionManager()
    
    helper.setCurrentDevice(connectionManager, "ABC123")
    
    val testCases = listOf(
        "Tapro_ABC123",
        "TaproDevice_ABC123_v2",
        "ABC123_TaproService"
    )
    
    testCases.forEach { serviceName ->
        val shouldReconnect = helper.simulateServiceDiscovery(
            connectionManager = connectionManager,
            device = TestDevice("ABC123", serviceName, "192.168.1.105", 8080),
            oldHost = "192.168.1.100",
            oldPort = 8080
        )
        
        assertTrue("服务名模式匹配应该成功: $serviceName", shouldReconnect)
    }
}
```

### 4. 边界情况测试

#### 测试用例 4.1: 未知设备ID处理
```kotlin
@Test
fun testUnknownDeviceIdHandling() {
    val helper = DeviceIdentityFilteringTestHelper()
    val connectionManager = createConnectionManager()
    
    // 设置当前设备ID为空
    helper.setCurrentDevice(connectionManager, null)
    helper.setLastConnectedDevice(reconnectManager, null)
    
    // 模拟断开状态下的服务发现
    connectionManager.updateConnectionStatus(ConnectionStatus.DISCONNECTED)
    
    val shouldReconnect = helper.simulateServiceDiscovery(
        connectionManager = connectionManager,
        device = TestDevice("ABC123", "TaproService_ABC123", "192.168.1.100", 8080)
    )
    
    assertTrue("断开状态下发现Tapro服务应该尝试连接", shouldReconnect)
}
```

#### 测试用例 4.2: 空服务名处理
```kotlin
@Test
fun testEmptyServiceNameHandling() {
    val helper = DeviceIdentityFilteringTestHelper()
    val connectionManager = createConnectionManager()
    
    helper.setCurrentDevice(connectionManager, "ABC123")
    
    val shouldReconnect = helper.simulateServiceDiscovery(
        connectionManager = connectionManager,
        device = TestDevice("ABC123", "", "192.168.1.105", 8080),
        oldHost = "192.168.1.100",
        oldPort = 8080
    )
    
    assertFalse("空服务名不应该触发重连", shouldReconnect)
}
```

#### 测试用例 4.3: 无效地址处理
```kotlin
@Test
fun testInvalidAddressHandling() {
    val helper = DeviceIdentityFilteringTestHelper()
    val connectionManager = createConnectionManager()
    
    helper.setCurrentDevice(connectionManager, "ABC123")
    
    val invalidAddressCases = listOf(
        TestDevice("ABC123", "TaproService_ABC123", "", 8080), // 空IP
        TestDevice("ABC123", "TaproService_ABC123", "192.168.1.100", -1), // 无效端口
        TestDevice("ABC123", "TaproService_ABC123", "invalid.ip", 8080) // 无效IP格式
    )
    
    invalidAddressCases.forEach { device ->
        val shouldReconnect = helper.simulateServiceDiscovery(
            connectionManager = connectionManager,
            device = device,
            oldHost = "192.168.1.100",
            oldPort = 8080
        )
        
        assertFalse("无效地址不应该触发重连: ${device.host}:${device.port}", shouldReconnect)
    }
}
```

### 5. 连接状态相关测试

#### 测试用例 5.1: 已连接状态下的相同地址
```kotlin
@Test
fun testSameAddressWhenConnected() {
    val helper = DeviceIdentityFilteringTestHelper()
    val connectionManager = createConnectionManager()
    
    helper.setCurrentDevice(connectionManager, "ABC123")
    connectionManager.updateConnectionStatus(ConnectionStatus.CONNECTED)
    
    // 发现相同的地址
    val shouldReconnect = helper.simulateServiceDiscovery(
        connectionManager = connectionManager,
        device = deviceA, // 相同设备相同地址
        oldHost = "192.168.1.100",
        oldPort = 8080
    )
    
    assertFalse("已连接状态下发现相同地址不应该重连", shouldReconnect)
}
```

#### 测试用例 5.2: 断开状态下的相同地址
```kotlin
@Test
fun testSameAddressWhenDisconnected() {
    val helper = DeviceIdentityFilteringTestHelper()
    val connectionManager = createConnectionManager()
    
    helper.setCurrentDevice(connectionManager, "ABC123")
    connectionManager.updateConnectionStatus(ConnectionStatus.DISCONNECTED)
    
    val shouldReconnect = helper.simulateServiceDiscovery(
        connectionManager = connectionManager,
        device = deviceA,
        oldHost = "192.168.1.100",
        oldPort = 8080
    )
    
    assertTrue("断开状态下发现相同设备的相同地址应该重连", shouldReconnect)
}
```

#### 测试用例 5.3: 错误状态下的地址变化
```kotlin
@Test
fun testAddressChangeWhenError() {
    val helper = DeviceIdentityFilteringTestHelper()
    val connectionManager = createConnectionManager()
    
    helper.setCurrentDevice(connectionManager, "ABC123")
    connectionManager.updateConnectionStatus(ConnectionStatus.ERROR)
    
    val shouldReconnect = helper.simulateServiceDiscovery(
        connectionManager = connectionManager,
        device = deviceA.copy(host = "192.168.1.105"),
        oldHost = "192.168.1.100",
        oldPort = 8080
    )
    
    assertTrue("错误状态下发现同一设备的新地址应该重连", shouldReconnect)
}
```

## 性能测试

### 1. 大量设备发现性能测试

```kotlin
@Test
fun testMassiveDeviceDiscoveryPerformance() {
    val helper = DeviceIdentityFilteringTestHelper()
    val connectionManager = createConnectionManager()
    
    helper.setCurrentDevice(connectionManager, "ABC123")
    
    val startTime = System.currentTimeMillis()
    
    // 模拟发现100个不同设备
    repeat(100) { index ->
        val device = TestDevice(
            deviceId = "DEVICE_$index",
            serviceName = "TaproService_DEVICE_$index",
            host = "192.168.1.$index",
            port = 8080
        )
        
        val shouldReconnect = helper.simulateServiceDiscovery(
            connectionManager = connectionManager,
            device = device,
            oldHost = "192.168.1.100",
            oldPort = 8080
        )
        
        assertFalse("不应该重连到不同设备: DEVICE_$index", shouldReconnect)
    }
    
    val endTime = System.currentTimeMillis()
    val duration = endTime - startTime
    
    assertTrue("100个设备过滤应该在1秒内完成", duration < 1000)
    LogUtil.d("DeviceFilteringTest", "100 devices filtered in ${duration}ms")
}
```

### 2. 匹配策略性能测试

```kotlin
@Test
fun testMatchingStrategyPerformance() {
    val helper = DeviceIdentityFilteringTestHelper()
    val connectionManager = createConnectionManager()
    
    helper.setCurrentDevice(connectionManager, "ABC123")
    
    val testCases = listOf(
        "TaproService_ABC123", // 精确匹配
        "CustomService_ABC123", // 后缀匹配
        "Tapro_ABC123_Extended", // 模式匹配
        "CompletelyDifferentService_XYZ789" // 不匹配
    )
    
    val startTime = System.currentTimeMillis()
    
    repeat(1000) { iteration ->
        testCases.forEach { serviceName ->
            helper.simulateServiceDiscovery(
                connectionManager = connectionManager,
                device = TestDevice("ABC123", serviceName, "192.168.1.100", 8080),
                oldHost = "192.168.1.99",
                oldPort = 8080
            )
        }
    }
    
    val endTime = System.currentTimeMillis()
    val duration = endTime - startTime
    
    assertTrue("4000次匹配操作应该在2秒内完成", duration < 2000)
    LogUtil.d("DeviceFilteringTest", "4000 matching operations completed in ${duration}ms")
}
```

## 集成测试

### 1. 完整连接流程测试

```kotlin
@Test
fun testCompleteConnectionFlowWithDeviceFiltering() {
    val helper = DeviceIdentityFilteringTestHelper()
    val connectionManager = createConnectionManager()
    
    // 1. 初始连接到设备A
    connectionManager.connect(createConnectionConfig(deviceA))
    helper.setCurrentDevice(connectionManager, "ABC123")
    connectionManager.updateConnectionStatus(ConnectionStatus.CONNECTED)
    
    // 2. 发现设备B（应该被忽略）
    val shouldReconnectToB = helper.simulateServiceDiscovery(
        connectionManager = connectionManager,
        device = deviceB,
        oldHost = deviceA.host,
        oldPort = deviceA.port
    )
    assertFalse("不应该重连到不同设备B", shouldReconnectToB)
    
    // 3. 设备A地址变化（应该重连）
    val shouldReconnectToA = helper.simulateServiceDiscovery(
        connectionManager = connectionManager,
        device = deviceA.copy(host = "192.168.1.105"),
        oldHost = deviceA.host,
        oldPort = deviceA.port
    )
    assertTrue("应该重连到设备A的新地址", shouldReconnectToA)
    
    // 4. 验证连接状态
    assertEquals("应该保持连接状态", ConnectionStatus.CONNECTED, connectionManager.connectionStatus)
}
```

### 2. 网络环境变化测试

```kotlin
@Test
fun testNetworkEnvironmentChange() {
    val helper = DeviceIdentityFilteringTestHelper()
    val connectionManager = createConnectionManager()
    
    // 模拟网络环境变化：设备从WiFi网络切换到热点网络
    helper.setCurrentDevice(connectionManager, "ABC123")
    
    // WiFi网络中的设备地址
    val wifiDevice = TestDevice("ABC123", "TaproService_ABC123", "192.168.1.100", 8080)
    
    // 热点网络中的设备地址
    val hotspotDevice = TestDevice("ABC123", "TaproService_ABC123", "192.168.43.100", 8080)
    
    // 从WiFi切换到热点
    val shouldReconnect = helper.simulateServiceDiscovery(
        connectionManager = connectionManager,
        device = hotspotDevice,
        oldHost = wifiDevice.host,
        oldPort = wifiDevice.port
    )
    
    assertTrue("网络环境变化时应该重连到同一设备的新地址", shouldReconnect)
}
```

## 测试执行指南

### 1. 自动化测试执行

```bash
# 执行所有设备身份过滤测试
./gradlew :lib_taplink_sdk:testDebugUnitTest --tests "*DeviceIdentityFiltering*"

# 执行性能测试
./gradlew :lib_taplink_sdk:testDebugUnitTest --tests "*Performance*"

# 执行集成测试
./gradlew :lib_taplink_sdk:testDebugUnitTest --tests "*Integration*"
```

### 2. 手动测试步骤

1. **准备测试环境**
   - 启动两个Tapro设备（设备A和设备B）
   - 确保设备在同一网络中
   - 记录设备ID和服务名

2. **执行基础测试**
   - 连接到设备A
   - 改变设备A的IP地址
   - 验证自动重连到设备A的新地址
   - 启动设备B
   - 验证不会重连到设备B

3. **执行边界测试**
   - 测试设备ID为空的情况
   - 测试服务名格式异常的情况
   - 测试网络断开重连的情况

### 3. 测试结果验证

```kotlin
class TestResultValidator {
    
    fun validateDeviceFiltering(testResults: List<TestResult>) {
        val sameDeviceTests = testResults.filter { it.testType == "SAME_DEVICE" }
        val differentDeviceTests = testResults.filter { it.testType == "DIFFERENT_DEVICE" }
        
        // 验证同一设备测试结果
        val sameDeviceSuccessRate = sameDeviceTests.count { it.shouldReconnect } / sameDeviceTests.size.toFloat()
        assertTrue("同一设备重连成功率应该 > 95%", sameDeviceSuccessRate > 0.95f)
        
        // 验证不同设备测试结果
        val differentDeviceRejectRate = differentDeviceTests.count { !it.shouldReconnect } / differentDeviceTests.size.toFloat()
        assertTrue("不同设备拒绝率应该 > 99%", differentDeviceRejectRate > 0.99f)
    }
    
    fun validatePerformance(performanceResults: List<PerformanceResult>) {
        performanceResults.forEach { result ->
            when (result.testType) {
                "MASS_DEVICE_DISCOVERY" -> {
                    assertTrue("大量设备发现应该在1秒内完成", result.duration < 1000)
                }
                "MATCHING_STRATEGY" -> {
                    assertTrue("匹配策略应该在2秒内完成", result.duration < 2000)
                }
            }
        }
    }
}
```

## 预期测试结果

### 1. 功能测试预期结果

- ✅ 同一设备地址变化：100% 重连成功
- ✅ 不同设备发现：100% 被正确忽略
- ✅ 设备身份匹配：95% 以上准确率
- ✅ 边界情况处理：100% 正确处理
- ✅ 连接状态相关：100% 符合预期

### 2. 性能测试预期结果

- ✅ 100个设备过滤：< 1秒
- ✅ 4000次匹配操作：< 2秒
- ✅ 内存使用：< 10MB 增长
- ✅ CPU使用：< 5% 峰值

### 3. 集成测试预期结果

- ✅ 完整连接流程：100% 成功
- ✅ 网络环境变化：100% 正确处理
- ✅ 多设备环境：100% 正确识别

通过这些全面的测试，可以确保设备身份过滤机制能够准确识别同一设备，避免对不同设备的错误重连，提升连接的稳定性和用户体验。