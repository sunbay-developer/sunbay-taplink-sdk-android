//package com.sunmi.tapro.taplink.communication
//
//import android.content.Context
//import android.net.nsd.NsdManager
//import android.net.nsd.NsdServiceInfo
//import com.sunmi.tapro.taplink.communication.interfaces.ConnectionCallback
//import com.sunmi.tapro.taplink.communication.lan.WebSocketClientKernel
//import io.mockk.*
//import kotlinx.coroutines.*
//import kotlinx.coroutines.test.*
//import org.junit.After
//import org.junit.Before
//import org.junit.Test
//import org.junit.Assert.*
//import java.net.InetAddress
//
///**
// * Test class for NSD service discovery functionality
// *
// * Tests the improved NSD discovery logic in WebSocketClientKernel
// */
//@OptIn(ExperimentalCoroutinesApi::class)
//class NsdServiceDiscoveryTest {
//
//    private lateinit var context: Context
//    private lateinit var nsdManager: NsdManager
//    private lateinit var webSocketClientKernel: WebSocketClientKernel
//    private lateinit var connectionCallback: ConnectionCallback
//
//    private val testDispatcher = StandardTestDispatcher()
//
//    @Before
//    fun setup() {
//        Dispatchers.setMain(testDispatcher)
//
//        // Mock dependencies
//        context = mockk(relaxed = true)
//        nsdManager = mockk(relaxed = true)
//        connectionCallback = mockk(relaxed = true)
//
//        // Setup context to return mocked NSD manager
//        every { context.getSystemService(Context.NSD_SERVICE) } returns nsdManager
//
//        // Create kernel instance
//        webSocketClientKernel = WebSocketClientKernel("testApp", "testSecret", context)
//    }
//
//    @After
//    fun tearDown() {
//        Dispatchers.resetMain()
//    }
//
//    @Test
//    fun `test successful service discovery and connection`() = runTest {
//        // Arrange
//        val testServiceInfo = createTestServiceInfo("taplink-device-123", "192.168.1.100", 8080)
//        var discoveryListener: NsdManager.DiscoveryListener? = null
//        var resolveListener: NsdManager.ResolveListener? = null
//
//        // Capture the discovery listener
//        every {
//            nsdManager.discoverServices(any(), any(), capture(slot<NsdManager.DiscoveryListener>()))
//        } answers {
//            discoveryListener = captured
//            Unit
//        }
//
//        // Capture the resolve listener
//        every {
//            nsdManager.resolveService(any(), capture(slot<NsdManager.ResolveListener>()))
//        } answers {
//            resolveListener = captured
//            Unit
//        }
//
//        // Mock successful connection callback
//        every {
//            connectionCallback.onServiceAddressChanged(any(), any(), any(), any(), any())
//        } returns true
//
//        // Act
//        val connectJob = launch {
//            webSocketClientKernel.connect("lan://192.168.1.100:8080", connectionCallback)
//        }
//
//        // Simulate discovery lifecycle
//        advanceTimeBy(100)
//        discoveryListener?.onDiscoveryStarted("_taplink._tcp")
//
//        advanceTimeBy(100)
//        discoveryListener?.onServiceFound(testServiceInfo)
//
//        advanceTimeBy(100)
//        resolveListener?.onServiceResolved(testServiceInfo)
//
//        // Wait for discovery timeout
//        advanceTimeBy(35000) // More than NSD_DISCOVERY_TIMEOUT
//
//        connectJob.join()
//
//        // Assert
//        verify {
//            connectionCallback.onServiceAddressChanged(
//                "taplink-device-123",
//                "192.168.1.100",
//                8080,
//                any(),
//                any()
//            )
//        }
//    }
//
//    @Test
//    fun `test service discovery timeout with no services found`() = runTest {
//        // Arrange
//        var discoveryListener: NsdManager.DiscoveryListener? = null
//
//        every {
//            nsdManager.discoverServices(any(), any(), capture(slot<NsdManager.DiscoveryListener>()))
//        } answers {
//            discoveryListener = captured
//            Unit
//        }
//
//        // Mock connection error callback
//        every { connectionCallback.onDisconnected(any(), any()) } just Runs
//
//        // Act
//        val connectJob = launch {
//            webSocketClientKernel.connect("lan://192.168.1.100:8080", connectionCallback)
//        }
//
//        // Simulate discovery start but no services found
//        advanceTimeBy(100)
//        discoveryListener?.onDiscoveryStarted("_taplink._tcp")
//
//        // Wait for discovery timeout
//        advanceTimeBy(35000)
//
//        connectJob.join()
//
//        // Assert
//        verify {
//            connectionCallback.onDisconnected(
//                match { it.contains("C35") },
//                match { it.contains("No available Taplink services found") }
//            )
//        }
//    }
//
//    @Test
//    fun `test service discovery with resolution failure`() = runTest {
//        // Arrange
//        val testServiceInfo = createTestServiceInfo("taplink-device-123", "192.168.1.100", 8080)
//        var discoveryListener: NsdManager.DiscoveryListener? = null
//        var resolveListener: NsdManager.ResolveListener? = null
//
//        every {
//            nsdManager.discoverServices(any(), any(), capture(slot<NsdManager.DiscoveryListener>()))
//        } answers {
//            discoveryListener = captured
//            Unit
//        }
//
//        every {
//            nsdManager.resolveService(any(), capture(slot<NsdManager.ResolveListener>()))
//        } answers {
//            resolveListener = captured
//            Unit
//        }
//
//        every { connectionCallback.onDisconnected(any(), any()) } just Runs
//
//        // Act
//        val connectJob = launch {
//            webSocketClientKernel.connect("lan://192.168.1.100:8080", connectionCallback)
//        }
//
//        // Simulate discovery with resolution failure
//        advanceTimeBy(100)
//        discoveryListener?.onDiscoveryStarted("_taplink._tcp")
//
//        advanceTimeBy(100)
//        discoveryListener?.onServiceFound(testServiceInfo)
//
//        advanceTimeBy(100)
//        resolveListener?.onResolveFailed(testServiceInfo, NsdManager.FAILURE_INTERNAL_ERROR)
//
//        // Wait for discovery timeout
//        advanceTimeBy(35000)
//
//        connectJob.join()
//
//        // Assert - should fail because no services were successfully resolved
//        verify {
//            connectionCallback.onDisconnected(
//                match { it.contains("C35") },
//                match { it.contains("No available Taplink services found") }
//            )
//        }
//    }
//
//    @Test
//    fun `test service lost during discovery`() = runTest {
//        // Arrange
//        val testServiceInfo = createTestServiceInfo("taplink-device-123", "192.168.1.100", 8080)
//        var discoveryListener: NsdManager.DiscoveryListener? = null
//        var resolveListener: NsdManager.ResolveListener? = null
//
//        every {
//            nsdManager.discoverServices(any(), any(), capture(slot<NsdManager.DiscoveryListener>()))
//        } answers {
//            discoveryListener = captured
//            Unit
//        }
//
//        every {
//            nsdManager.resolveService(any(), capture(slot<NsdManager.ResolveListener>()))
//        } answers {
//            resolveListener = captured
//            Unit
//        }
//
//        every { connectionCallback.onDisconnected(any(), any()) } just Runs
//
//        // Act
//        val connectJob = launch {
//            webSocketClientKernel.connect("lan://192.168.1.100:8080", connectionCallback)
//        }
//
//        // Simulate service found, resolved, then lost
//        advanceTimeBy(100)
//        discoveryListener?.onDiscoveryStarted("_taplink._tcp")
//
//        advanceTimeBy(100)
//        discoveryListener?.onServiceFound(testServiceInfo)
//
//        advanceTimeBy(100)
//        resolveListener?.onServiceResolved(testServiceInfo)
//
//        advanceTimeBy(100)
//        discoveryListener?.onServiceLost(testServiceInfo)
//
//        // Wait for discovery timeout
//        advanceTimeBy(35000)
//
//        connectJob.join()
//
//        // Assert - should still fail because service was lost
//        verify {
//            connectionCallback.onDisconnected(
//                match { it.contains("C35") },
//                match { it.contains("No available Taplink services found") }
//            )
//        }
//    }
//
//    @Test
//    fun `test multiple services discovered`() = runTest {
//        // Arrange
//        val service1 = createTestServiceInfo("taplink-device-1", "192.168.1.100", 8080)
//        val service2 = createTestServiceInfo("taplink-device-2", "192.168.1.101", 8081)
//
//        var discoveryListener: NsdManager.DiscoveryListener? = null
//        val resolveListeners = mutableListOf<NsdManager.ResolveListener>()
//
//        every {
//            nsdManager.discoverServices(any(), any(), capture(slot<NsdManager.DiscoveryListener>()))
//        } answers {
//            discoveryListener = captured
//            Unit
//        }
//
//        every {
//            nsdManager.resolveService(any(), capture(slot<NsdManager.ResolveListener>()))
//        } answers {
//            resolveListeners.add(captured)
//            Unit
//        }
//
//        // Mock first service connection succeeds
//        every {
//            connectionCallback.onServiceAddressChanged("taplink-device-1", "192.168.1.100", 8080, any(), any())
//        } returns true
//
//        // Act
//        val connectJob = launch {
//            webSocketClientKernel.connect("lan://192.168.1.100:8080", connectionCallback)
//        }
//
//        // Simulate multiple services discovery
//        advanceTimeBy(100)
//        discoveryListener?.onDiscoveryStarted("_taplink._tcp")
//
//        advanceTimeBy(100)
//        discoveryListener?.onServiceFound(service1)
//        discoveryListener?.onServiceFound(service2)
//
//        advanceTimeBy(100)
//        resolveListeners[0].onServiceResolved(service1)
//        resolveListeners[1].onServiceResolved(service2)
//
//        // Wait for discovery timeout
//        advanceTimeBy(35000)
//
//        connectJob.join()
//
//        // Assert - should connect to first service
//        verify {
//            connectionCallback.onServiceAddressChanged("taplink-device-1", "192.168.1.100", 8080, any(), any())
//        }
//
//        // Second service should not be tried since first succeeded
//        verify(exactly = 0) {
//            connectionCallback.onServiceAddressChanged("taplink-device-2", "192.168.1.101", 8081, any(), any())
//        }
//    }
//
//    @Test
//    fun `test discovery start failure`() = runTest {
//        // Arrange
//        var discoveryListener: NsdManager.DiscoveryListener? = null
//
//        every {
//            nsdManager.discoverServices(any(), any(), capture(slot<NsdManager.DiscoveryListener>()))
//        } answers {
//            discoveryListener = captured
//            Unit
//        }
//
//        every { connectionCallback.onDisconnected(any(), any()) } just Runs
//
//        // Act
//        val connectJob = launch {
//            webSocketClientKernel.connect("lan://192.168.1.100:8080", connectionCallback)
//        }
//
//        // Simulate discovery start failure
//        advanceTimeBy(100)
//        discoveryListener?.onStartDiscoveryFailed("_taplink._tcp", NsdManager.FAILURE_INTERNAL_ERROR)
//
//        connectJob.join()
//
//        // Assert
//        verify {
//            connectionCallback.onDisconnected(
//                match { it.contains("C35") },
//                match { it.contains("serviceType:_taplink._tcp") }
//            )
//        }
//    }
//
//    @Test
//    fun `test service attributes with empty keys`() = runTest {
//        // Arrange
//        val serviceInfo = mockk<NsdServiceInfo>(relaxed = true)
//        val attributes = mapOf(
//            "deviceId" to "P303D3BM10048".toByteArray(),
//            "" to "invalid".toByteArray(),  // 空键名
//            "version" to "1.0".toByteArray(),
//            "protocol" to "taplink".toByteArray()
//        )
//        every { serviceInfo.serviceName } returns "Taplink-Server-P303D3BM10048"
//        every { serviceInfo.host } returns InetAddress.getByName("10.14.79.125")
//        every { serviceInfo.port } returns 8443
//        every { serviceInfo.attributes } returns attributes
//
//        val discoveryManager = DeviceDiscoveryManager(context)
//
//        // Act - 使用反射访问私有方法进行测试
//        val extractAttributesMethod = DeviceDiscoveryManager::class.java.getDeclaredMethod(
//            "extractAttributes",
//            NsdServiceInfo::class.java
//        )
//        extractAttributesMethod.isAccessible = true
//
//        @Suppress("UNCHECKED_CAST")
//        val result = extractAttributesMethod.invoke(discoveryManager, serviceInfo) as Map<String, String>
//
//        // Assert
//        assertEquals(3, result.size) // 应该只包含3个有效属性
//        assertEquals("P303D3BM10048", result["deviceId"])
//        assertEquals("1.0", result["version"])
//        assertEquals("taplink", result["protocol"])
//        assertNull(result[""]) // 空键名应被过滤
//    }
//
//    @Test
//    fun `test service attributes with null keys and values`() = runTest {
//        // Arrange
//        val serviceInfo = mockk<NsdServiceInfo>(relaxed = true)
//        val attributes = mapOf(
//            "deviceId" to "P303D3BM10048".toByteArray(),
//            "nullValue" to null,  // null值
//            "version" to "1.0".toByteArray()
//        )
//        every { serviceInfo.serviceName } returns "Taplink-Server-Test"
//        every { serviceInfo.host } returns InetAddress.getByName("192.168.1.100")
//        every { serviceInfo.port } returns 8080
//        every { serviceInfo.attributes } returns attributes
//
//        val discoveryManager = DeviceDiscoveryManager(context)
//
//        // Act
//        val extractAttributesMethod = DeviceDiscoveryManager::class.java.getDeclaredMethod(
//            "extractAttributes",
//            NsdServiceInfo::class.java
//        )
//        extractAttributesMethod.isAccessible = true
//
//        @Suppress("UNCHECKED_CAST")
//        val result = extractAttributesMethod.invoke(discoveryManager, serviceInfo) as Map<String, String>
//
//        // Assert
//        assertEquals(2, result.size) // 应该只包含2个有效属性
//        assertEquals("P303D3BM10048", result["deviceId"])
//        assertEquals("1.0", result["version"])
//        assertNull(result["nullValue"]) // null值应被过滤
//    }
//
//    @Test
//    fun `test service attributes with malformed data`() = runTest {
//        // Arrange
//        val serviceInfo = mockk<NsdServiceInfo>(relaxed = true)
//        val malformedBytes = byteArrayOf(-1, -2, -3) // 无效的UTF-8字节
//        val attributes = mapOf(
//            "deviceId" to "P303D3BM10048".toByteArray(),
//            "malformed" to malformedBytes,
//            "version" to "1.0".toByteArray()
//        )
//        every { serviceInfo.serviceName } returns "Taplink-Server-Malformed"
//        every { serviceInfo.host } returns InetAddress.getByName("192.168.1.101")
//        every { serviceInfo.port } returns 8081
//        every { serviceInfo.attributes } returns attributes
//
//        val discoveryManager = DeviceDiscoveryManager(context)
//
//        // Act
//        val extractAttributesMethod = DeviceDiscoveryManager::class.java.getDeclaredMethod(
//            "extractAttributes",
//            NsdServiceInfo::class.java
//        )
//        extractAttributesMethod.isAccessible = true
//
//        @Suppress("UNCHECKED_CAST")
//        val result = extractAttributesMethod.invoke(discoveryManager, serviceInfo) as Map<String, String>
//
//        // Assert - 应该能处理格式错误的数据而不崩溃
//        assertTrue(result.size >= 2) // 至少包含有效的属性
//        assertEquals("P303D3BM10048", result["deviceId"])
//        assertEquals("1.0", result["version"])
//        // malformed属性可能被跳过或包含错误信息，但不应导致崩溃
//    }
//
//    /**
//     * Helper method to create test NsdServiceInfo
//     */
//    private fun createTestServiceInfo(serviceName: String, host: String, port: Int): NsdServiceInfo {
//        val serviceInfo = mockk<NsdServiceInfo>(relaxed = true)
//        every { serviceInfo.serviceName } returns serviceName
//        every { serviceInfo.host } returns InetAddress.getByName(host)
//        every { serviceInfo.port } returns port
//        every { serviceInfo.attributes } returns null
//        return serviceInfo
//    }
//}