# ServiceDiscoveryManager 并发测试指南

## 测试目的

验证 `ServiceDiscoveryManager` 在并发场景下的稳定性和正确性，确保修复后的代码能够正确处理：

1. 重复服务发现
2. 并发服务解析
3. 服务信息更新的一致性

## 测试环境

- **设备**: Android 设备，支持 WiFi 和 mDNS
- **网络**: 局域网环境，有多个 mDNS 服务
- **工具**: 可以使用 Bonjour Browser 或类似工具创建测试服务

## 测试用例

### 测试1: 重复服务发现防护

**目标**: 验证同一服务被多次发现时，只进行一次解析

**测试步骤**:
1. 启动服务发现
2. 模拟同一服务被快速多次发现
3. 观察日志输出

**预期结果**:
```
ServiceDiscoveryManager: Service found: test-service
ServiceDiscoveryManager: Starting to resolve service: test-service
ServiceDiscoveryManager: Service found: test-service
ServiceDiscoveryManager: Service test-service is already being resolved, skipping
ServiceDiscoveryManager: Service resolved: test-service
ServiceDiscoveryManager: New service discovered: test-service at 192.168.1.100:8443
```

**验证点**:
- ✅ 只有一次 "Starting to resolve service" 日志
- ✅ 出现 "already being resolved, skipping" 日志
- ✅ 只有一次 "New service discovered" 回调

### 测试2: 并发服务解析

**目标**: 验证多个不同服务同时被发现时的处理

**测试步骤**:
1. 在网络中启动多个 mDNS 服务
2. 同时开始服务发现
3. 观察所有服务都能正确被发现和解析

**预期结果**:
```
ServiceDiscoveryManager: Service found: service-1
ServiceDiscoveryManager: Service found: service-2
ServiceDiscoveryManager: Service found: service-3
ServiceDiscoveryManager: Starting to resolve service: service-1
ServiceDiscoveryManager: Starting to resolve service: service-2
ServiceDiscoveryManager: Starting to resolve service: service-3
ServiceDiscoveryManager: Service resolved: service-1
ServiceDiscoveryManager: Service resolved: service-2
ServiceDiscoveryManager: Service resolved: service-3
ServiceDiscoveryManager: New service discovered: service-1 at 192.168.1.100:8443
ServiceDiscoveryManager: New service discovered: service-2 at 192.168.1.101:8443
ServiceDiscoveryManager: New service discovered: service-3 at 192.168.1.102:8443
```

**验证点**:
- ✅ 所有服务都被正确发现
- ✅ 每个服务只解析一次
- ✅ 所有服务信息正确更新

### 测试3: 服务信息更新一致性

**目标**: 验证服务信息变化时的更新一致性

**测试步骤**:
1. 发现一个服务
2. 重启该服务，使其IP地址发生变化
3. 观察服务信息更新

**预期结果**:
```
ServiceDiscoveryManager: New service discovered: test-service at 192.168.1.100:8443
// 服务重启，IP变化
ServiceDiscoveryManager: Service found: test-service
ServiceDiscoveryManager: Starting to resolve service: test-service
ServiceDiscoveryManager: Service resolved: test-service
ServiceDiscoveryManager: Service address changed: test-service, 192.168.1.100:8443 -> 192.168.1.101:8443
```

**验证点**:
- ✅ 正确检测到服务地址变化
- ✅ 触发 `onServiceUpdated` 回调
- ✅ 服务信息正确更新

### 测试4: 解析失败处理

**目标**: 验证服务解析失败时的资源清理

**测试步骤**:
1. 发现一个服务
2. 在解析过程中断开网络或停止服务
3. 观察解析失败的处理

**预期结果**:
```
ServiceDiscoveryManager: Service found: test-service
ServiceDiscoveryManager: Starting to resolve service: test-service
ServiceDiscoveryManager: Resolve failed: test-service, error: 3
// 再次发现同一服务应该能够重新解析
ServiceDiscoveryManager: Service found: test-service
ServiceDiscoveryManager: Starting to resolve service: test-service
```

**验证点**:
- ✅ 解析失败时记录错误日志
- ✅ 清理解析状态，允许重新解析
- ✅ 不会导致内存泄漏

### 测试5: 高频服务变化

**目标**: 验证网络不稳定环境下的稳定性

**测试步骤**:
1. 创建一个脚本，快速启动和停止 mDNS 服务
2. 运行服务发现
3. 观察系统稳定性

**预期结果**:
- ✅ 系统保持稳定，不会崩溃
- ✅ 正确处理服务的出现和消失
- ✅ 内存使用保持稳定

## 自动化测试代码

### 单元测试示例

```kotlin
class ServiceDiscoveryManagerTest {
    
    private lateinit var context: Context
    private lateinit var serviceDiscoveryManager: ServiceDiscoveryManager
    private lateinit var mockListener: ServiceChangeListener
    
    @Before
    fun setup() {
        context = mockk()
        serviceDiscoveryManager = ServiceDiscoveryManager(context)
        mockListener = mockk(relaxed = true)
    }
    
    @Test
    fun testDuplicateServiceDiscoveryPrevention() {
        // 模拟同一服务被多次发现
        val serviceInfo = createMockNsdServiceInfo("test-service")
        
        // 并发调用 onServiceFound
        val threads = (1..10).map {
            thread {
                // 通过反射调用私有方法进行测试
                callOnServiceFound(serviceInfo)
            }
        }
        
        threads.forEach { it.join() }
        
        // 验证只有一次服务发现回调
        verify(exactly = 1) { mockListener.onServiceFound(any()) }
    }
    
    @Test
    fun testConcurrentDifferentServices() {
        val services = (1..5).map { createMockNsdServiceInfo("service-$it") }
        
        // 并发发现多个不同服务
        val threads = services.map { serviceInfo ->
            thread {
                callOnServiceFound(serviceInfo)
            }
        }
        
        threads.forEach { it.join() }
        
        // 验证所有服务都被发现
        verify(exactly = 5) { mockListener.onServiceFound(any()) }
    }
    
    private fun createMockNsdServiceInfo(name: String): NsdServiceInfo {
        return mockk<NsdServiceInfo>().apply {
            every { serviceName } returns name
            every { host } returns InetAddress.getByName("192.168.1.100")
            every { port } returns 8443
            every { serviceType } returns "_taplink._tcp"
        }
    }
    
    private fun callOnServiceFound(serviceInfo: NsdServiceInfo) {
        // 使用反射调用私有方法
        val method = ServiceDiscoveryManager::class.java.getDeclaredMethod(
            "onServiceFound", 
            NsdServiceInfo::class.java
        )
        method.isAccessible = true
        method.invoke(serviceDiscoveryManager, serviceInfo)
    }
}
```

### 集成测试示例

```kotlin
@RunWith(AndroidJUnit4::class)
class ServiceDiscoveryIntegrationTest {
    
    @Test
    fun testRealNetworkServiceDiscovery() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val serviceDiscoveryManager = ServiceDiscoveryManager(context)
        
        val discoveredServices = mutableListOf<ServiceInfo>()
        val listener = object : ServiceChangeListener {
            override fun onServiceFound(service: ServiceInfo) {
                discoveredServices.add(service)
            }
            
            override fun onServiceLost(service: ServiceInfo) {
                discoveredServices.remove(service)
            }
            
            override fun onServiceUpdated(oldService: ServiceInfo, newService: ServiceInfo) {
                val index = discoveredServices.indexOf(oldService)
                if (index >= 0) {
                    discoveredServices[index] = newService
                }
            }
            
            // 其他回调方法的实现...
        }
        
        // 开始服务发现
        serviceDiscoveryManager.startServiceMonitoring("_taplink._tcp", listener)
        
        // 等待发现完成
        Thread.sleep(10000)
        
        // 验证结果
        assertTrue("Should discover at least one service", discoveredServices.isNotEmpty())
        
        // 停止服务发现
        serviceDiscoveryManager.stopServiceMonitoring()
    }
}
```

## 性能测试

### 内存泄漏测试

```kotlin
@Test
fun testMemoryLeak() {
    val initialMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()
    
    repeat(100) {
        val manager = ServiceDiscoveryManager(context)
        manager.startServiceMonitoring("_test._tcp", mockListener)
        Thread.sleep(100)
        manager.stopServiceMonitoring()
    }
    
    System.gc()
    Thread.sleep(1000)
    
    val finalMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()
    val memoryIncrease = finalMemory - initialMemory
    
    assertTrue("Memory increase should be minimal", memoryIncrease < 1024 * 1024) // 1MB
}
```

### 性能基准测试

```kotlin
@Test
fun testPerformanceBenchmark() {
    val startTime = System.currentTimeMillis()
    
    repeat(1000) {
        val serviceInfo = createMockNsdServiceInfo("service-$it")
        callOnServiceFound(serviceInfo)
    }
    
    val endTime = System.currentTimeMillis()
    val duration = endTime - startTime
    
    assertTrue("Should handle 1000 services in less than 5 seconds", duration < 5000)
}
```

## 故障排除

### 常见问题

1. **服务解析超时**
   - 检查网络连接
   - 增加解析超时时间
   - 检查 mDNS 服务是否正常运行

2. **重复服务回调**
   - 检查是否正确应用了并发修复
   - 查看日志确认重复解析防护是否生效

3. **服务信息不一致**
   - 检查服务更新锁是否正常工作
   - 验证服务信息比较逻辑

### 调试技巧

1. **启用详细日志**:
   ```kotlin
   // 在测试前设置日志级别
   LogUtil.setLogLevel(LogUtil.DEBUG)
   ```

2. **监控关键指标**:
   ```kotlin
   // 添加性能监控
   val resolvingCount = serviceDiscoveryManager.getResolvingServicesCount()
   val discoveredCount = serviceDiscoveryManager.getDiscoveredServicesCount()
   ```

3. **使用网络工具**:
   - Wireshark 抓包分析 mDNS 流量
   - Bonjour Browser 验证服务广播

## 测试报告模板

```
测试日期: ____
测试环境: ____
测试设备: ____

测试结果:
□ 测试1 - 重复服务发现防护: 通过/失败
□ 测试2 - 并发服务解析: 通过/失败
□ 测试3 - 服务信息更新一致性: 通过/失败
□ 测试4 - 解析失败处理: 通过/失败
□ 测试5 - 高频服务变化: 通过/失败

性能指标:
- 内存使用: ____
- 响应时间: ____
- 成功率: ____

问题记录:
- 问题描述: ____
- 复现步骤: ____
- 解决方案: ____

总体评价: ____
```

## 总结

通过这些测试用例，可以全面验证 `ServiceDiscoveryManager` 并发修复的有效性：

1. ✅ **重复解析防护**: 确保同一服务只解析一次
2. ✅ **并发安全**: 多个服务并发发现时的正确处理
3. ✅ **状态一致性**: 服务信息更新的原子性
4. ✅ **错误处理**: 解析失败时的资源清理
5. ✅ **性能稳定**: 高频变化场景下的系统稳定性

建议在每次修改 `ServiceDiscoveryManager` 后都运行这些测试，确保并发安全性不被破坏。