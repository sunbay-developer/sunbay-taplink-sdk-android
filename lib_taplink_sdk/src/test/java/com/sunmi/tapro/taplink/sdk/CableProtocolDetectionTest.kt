package com.sunmi.tapro.taplink.sdk

import android.content.Context
import android.hardware.usb.UsbAccessory
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import com.sunmi.tapro.taplink.sdk.config.ConnectionConfig
import com.sunmi.tapro.taplink.sdk.config.TaplinkConfig
import com.sunmi.tapro.taplink.sdk.enums.CableProtocol
import com.sunmi.tapro.taplink.sdk.enums.ConnectionMode
import com.sunmi.tapro.taplink.sdk.util.CableProtocolDetector
import com.sunmi.tapro.taplink.sdk.util.ProtocolBuilder
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.MockitoAnnotations

/**
 * Cable protocol detection tests
 * 
 * Tests automatic cable protocol detection functionality
 * 
 * @author TaPro Team
 * @since 2025-12-22
 */
class CableProtocolDetectionTest {

    @Mock
    private lateinit var mockContext: Context
    
    @Mock
    private lateinit var mockUsbManager: UsbManager
    
    @Mock
    private lateinit var mockUsbAccessory: UsbAccessory
    
    @Mock
    private lateinit var mockUsbDevice: UsbDevice

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        `when`(mockContext.getSystemService(Context.USB_SERVICE)).thenReturn(mockUsbManager)
    }

    @Test
    fun testDetectUsbAoa_WithAccessory() = runBlocking {
        // Setup mock USB accessory
        `when`(mockUsbAccessory.manufacturer).thenReturn("Sunmi")
        `when`(mockUsbAccessory.model).thenReturn("TapLink Device")
        `when`(mockUsbManager.accessoryList).thenReturn(arrayOf(mockUsbAccessory))
        `when`(mockUsbManager.deviceList).thenReturn(emptyMap())

        val detector = CableProtocolDetector(mockContext)
        val result = detector.detectProtocol()

        assertEquals(CableProtocol.USB_AOA, result.protocol)
        assertTrue(result.confidence > 0.8f)
        assertTrue(result.deviceInfo?.contains("Sunmi TapLink Device") == true)
    }

    @Test
    fun testDetectUsbAoa_WithCompatibleDevice() = runBlocking {
        // Setup mock USB device with Google AOA vendor ID
        `when`(mockUsbDevice.vendorId).thenReturn(0x18D1) // Google AOA vendor ID
        `when`(mockUsbDevice.deviceName).thenReturn("USB Device")
        `when`(mockUsbManager.accessoryList).thenReturn(null)
        `when`(mockUsbManager.deviceList).thenReturn(mapOf("device1" to mockUsbDevice))

        val detector = CableProtocolDetector(mockContext)
        val result = detector.detectProtocol()

        assertEquals(CableProtocol.USB_AOA, result.protocol)
        assertTrue(result.confidence > 0.7f)
    }

    @Test
    fun testDetectUsbVsp_WithFtdiDevice() = runBlocking {
        // Setup mock USB device with FTDI vendor ID
        `when`(mockUsbDevice.vendorId).thenReturn(0x0403) // FTDI vendor ID
        `when`(mockUsbDevice.deviceName).thenReturn("FTDI Device")
        `when`(mockUsbManager.accessoryList).thenReturn(null)
        `when`(mockUsbManager.deviceList).thenReturn(mapOf("device1" to mockUsbDevice))

        val detector = CableProtocolDetector(mockContext)
        val result = detector.detectProtocol()

        assertEquals(CableProtocol.USB_VSP, result.protocol)
        assertTrue(result.confidence > 0.6f)
    }

    @Test
    fun testDetectDefault_NoDevices() = runBlocking {
        // Setup no devices
        `when`(mockUsbManager.accessoryList).thenReturn(null)
        `when`(mockUsbManager.deviceList).thenReturn(emptyMap())

        val detector = CableProtocolDetector(mockContext)
        val result = detector.detectProtocol()

        assertEquals(CableProtocol.USB_AOA, result.protocol)
        assertTrue(result.confidence < 0.5f)
        assertTrue(result.deviceInfo?.contains("No device detected") == true)
    }

    @Test
    fun testProtocolBuilder_CableMode_AutoDetection() {
        val taplinkConfig = TaplinkConfig(
            appId = "test_app",
            merchantId = "test_merchant",
            secretKey = "test_secret"
        ).setConnectionMode(ConnectionMode.CABLE)

        // Test with empty connection config (should trigger auto-detection)
        val connectionConfig = ConnectionConfig.createDefault()
        
        val (protocol, mode) = ProtocolBuilder.buildProtocol(
            connectionConfig, 
            taplinkConfig, 
            mockContext
        )

        // Should return a valid protocol (detection will run)
        assertNotNull(protocol)
        assertNotNull(mode)
        // Mode should be one of the cable protocols
        assertTrue(
            mode == CableProtocol.USB_AOA.name || 
            mode == CableProtocol.USB_VSP.name || 
            mode == CableProtocol.RS232.name
        )
    }

    @Test
    fun testProtocolBuilder_ExplicitAutoProtocol() {
        val taplinkConfig = TaplinkConfig(
            appId = "test_app",
            merchantId = "test_merchant", 
            secretKey = "test_secret"
        )

        val connectionConfig = ConnectionConfig()
            .setCableProtocol(CableProtocol.AUTO)

        val (protocol, mode) = ProtocolBuilder.buildProtocol(
            connectionConfig,
            taplinkConfig,
            mockContext
        )

        assertNotNull(protocol)
        assertNotNull(mode)
    }

    @Test
    fun testProtocolBuilder_SpecificProtocol_NoDetection() {
        val taplinkConfig = TaplinkConfig(
            appId = "test_app",
            merchantId = "test_merchant",
            secretKey = "test_secret"
        )

        val connectionConfig = ConnectionConfig()
            .setCableProtocol(CableProtocol.USB_AOA)

        val (protocol, mode) = ProtocolBuilder.buildProtocol(
            connectionConfig,
            taplinkConfig,
            mockContext
        )

        // Should return USB AOA protocol without detection
        assertTrue(protocol.contains("accessory"))
        assertEquals(CableProtocol.USB_AOA.name, mode)
    }

    @Test
    fun testProtocolBuilder_NoContext_FallbackToAoa() {
        val taplinkConfig = TaplinkConfig(
            appId = "test_app",
            merchantId = "test_merchant",
            secretKey = "test_secret"
        ).setConnectionMode(ConnectionMode.CABLE)

        val connectionConfig = ConnectionConfig.createDefault()

        // No context provided - should fallback to AOA
        val (protocol, mode) = ProtocolBuilder.buildProtocol(
            connectionConfig,
            taplinkConfig,
            null // No context
        )

        assertTrue(protocol.contains("accessory"))
        assertEquals(CableProtocol.USB_AOA.name, mode)
    }

    @Test
    fun testDetectionPriority() = runBlocking {
        // Setup multiple device types
        `when`(mockUsbAccessory.manufacturer).thenReturn("Test")
        `when`(mockUsbAccessory.model).thenReturn("Accessory")
        `when`(mockUsbManager.accessoryList).thenReturn(arrayOf(mockUsbAccessory))
        
        `when`(mockUsbDevice.vendorId).thenReturn(0x0403) // FTDI
        `when`(mockUsbDevice.deviceName).thenReturn("FTDI Device")
        `when`(mockUsbManager.deviceList).thenReturn(mapOf("device1" to mockUsbDevice))

        val detector = CableProtocolDetector(mockContext)
        val result = detector.detectProtocol()

        // Should prefer USB AOA (accessory) over USB VSP (FTDI device)
        assertEquals(CableProtocol.USB_AOA, result.protocol)
    }

    @Test
    fun testGetDeviceInfo() {
        // Setup mock devices
        `when`(mockUsbAccessory.manufacturer).thenReturn("Test Manufacturer")
        `when`(mockUsbAccessory.model).thenReturn("Test Model")
        `when`(mockUsbAccessory.version).thenReturn("1.0")
        `when`(mockUsbManager.accessoryList).thenReturn(arrayOf(mockUsbAccessory))
        
        `when`(mockUsbDevice.deviceName).thenReturn("Test USB Device")
        `when`(mockUsbDevice.vendorId).thenReturn(0x1234)
        `when`(mockUsbDevice.productId).thenReturn(0x5678)
        `when`(mockUsbManager.deviceList).thenReturn(mapOf("device1" to mockUsbDevice))

        val detector = CableProtocolDetector(mockContext)
        val deviceInfo = detector.getDeviceInfo()

        assertTrue(deviceInfo.contains("USB Accessories:"))
        assertTrue(deviceInfo.contains("Test Manufacturer Test Model"))
        assertTrue(deviceInfo.contains("USB Devices:"))
        assertTrue(deviceInfo.contains("Test USB Device"))
        assertTrue(deviceInfo.contains("1234"))
        assertTrue(deviceInfo.contains("5678"))
    }
}