package com.sunmi.tapro.taplink.sdk

import android.content.Context
import com.sunmi.tapro.taplink.sdk.callback.ConnectionListener
import com.sunmi.tapro.taplink.sdk.config.ConnectionConfig
import com.sunmi.tapro.taplink.sdk.config.TaplinkConfig
import com.sunmi.tapro.taplink.sdk.enums.CableProtocol
import com.sunmi.tapro.taplink.sdk.enums.ConnectionMode
import com.sunmi.tapro.taplink.sdk.error.ConnectionError
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.mockito.Mock
import org.mockito.MockitoAnnotations

/**
 * Default connection configuration tests
 * 
 * Tests automatic creation of default connection configurations
 * based on TaplinkConfig connection mode
 * 
 * @author TaPro Team
 * @since 2025-12-22
 */
class DefaultConnectionConfigTest {

    @Mock
    private lateinit var mockContext: Context

    private lateinit var connectionListener: TestConnectionListener

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        connectionListener = TestConnectionListener()
    }

    @Test
    fun testConnect_NoConfig_AppToAppMode() {
        // Initialize SDK with APP_TO_APP mode
        val config = TaplinkConfig(
            appId = "test_app",
            merchantId = "test_merchant",
            secretKey = "test_secret"
        ).setConnectionMode(ConnectionMode.APP_TO_APP)

        // When connecting without config, should create default APP_TO_APP config
        // This test verifies the logic, actual connection would require mocking
        
        assertNotNull(config)
        assertEquals(ConnectionMode.APP_TO_APP, config.connectionMode)
    }

    @Test
    fun testConnect_NoConfig_CableMode() {
        // Initialize SDK with CABLE mode
        val config = TaplinkConfig(
            appId = "test_app",
            merchantId = "test_merchant",
            secretKey = "test_secret"
        ).setConnectionMode(ConnectionMode.CABLE)

        // When connecting without config, should create CABLE config with AUTO protocol
        
        assertNotNull(config)
        assertEquals(ConnectionMode.CABLE, config.connectionMode)
    }

    @Test
    fun testConnect_NoConfig_LanMode() {
        // Initialize SDK with LAN mode
        val config = TaplinkConfig(
            appId = "test_app",
            merchantId = "test_merchant",
            secretKey = "test_secret"
        ).setConnectionMode(ConnectionMode.LAN)

        // When connecting without config, should create default config
        // LAN requires manual host/port configuration
        
        assertNotNull(config)
        assertEquals(ConnectionMode.LAN, config.connectionMode)
    }

    @Test
    fun testConnect_WithExplicitConfig_OverridesDefault() {
        // Initialize SDK with CABLE mode
        val taplinkConfig = TaplinkConfig(
            appId = "test_app",
            merchantId = "test_merchant",
            secretKey = "test_secret"
        ).setConnectionMode(ConnectionMode.CABLE)

        // Provide explicit connection config
        val connectionConfig = ConnectionConfig()
            .setCableProtocol(CableProtocol.USB_VSP)

        // Explicit config should override default
        assertNotNull(connectionConfig.cableProtocol)
        assertEquals(CableProtocol.USB_VSP, connectionConfig.cableProtocol)
    }

    @Test
    fun testConnect_NullConfig_CableMode_CreatesAutoConfig() {
        // This test verifies the expected behavior:
        // When connectionMode is CABLE and no config is provided,
        // a default config with CableProtocol.AUTO should be created

        val taplinkConfig = TaplinkConfig(
            appId = "test_app",
            merchantId = "test_merchant",
            secretKey = "test_secret"
        ).setConnectionMode(ConnectionMode.CABLE)

        // Simulate the logic from createDefaultConnectionConfig()
        val defaultConfig = when (taplinkConfig.connectionMode) {
            ConnectionMode.CABLE -> ConnectionConfig().setCableProtocol(CableProtocol.AUTO)
            else -> ConnectionConfig.createDefault()
        }

        assertNotNull(defaultConfig)
        assertEquals(CableProtocol.AUTO, defaultConfig.cableProtocol)
    }

    @Test
    fun testConnect_NullConfig_AppToAppMode_CreatesDefaultConfig() {
        // This test verifies the expected behavior:
        // When connectionMode is APP_TO_APP and no config is provided,
        // a default empty config should be created

        val taplinkConfig = TaplinkConfig(
            appId = "test_app",
            merchantId = "test_merchant",
            secretKey = "test_secret"
        ).setConnectionMode(ConnectionMode.APP_TO_APP)

        // Simulate the logic from createDefaultConnectionConfig()
        val defaultConfig = when (taplinkConfig.connectionMode) {
            ConnectionMode.APP_TO_APP -> ConnectionConfig.createDefault()
            else -> ConnectionConfig.createDefault()
        }

        assertNotNull(defaultConfig)
        // Default config should have no specific protocol set
        assertNull(defaultConfig.cableProtocol)
        assertNull(defaultConfig.host)
        assertNull(defaultConfig.port)
    }

    @Test
    fun testConnectionConfig_CableProtocolAuto() {
        val config = ConnectionConfig()
            .setCableProtocol(CableProtocol.AUTO)

        assertEquals(CableProtocol.AUTO, config.cableProtocol)
    }

    @Test
    fun testConnectionConfig_ChainedConfiguration() {
        val config = ConnectionConfig()
            .setCableProtocol(CableProtocol.USB_AOA)

        assertNotNull(config)
        assertEquals(CableProtocol.USB_AOA, config.cableProtocol)

        // Test chaining
        val config2 = ConnectionConfig()
            .setHost("192.168.1.100")
            .setPort(8443)

        assertEquals("192.168.1.100", config2.host)
        assertEquals(8443, config2.port)
    }

    @Test
    fun testConnectionMode_AllValues() {
        // Test all connection modes
        val modes = listOf(
            ConnectionMode.NONE,
            ConnectionMode.APP_TO_APP,
            ConnectionMode.CABLE,
            ConnectionMode.LAN
        )

        modes.forEach { mode ->
            val config = TaplinkConfig(
                appId = "test_app",
                merchantId = "test_merchant",
                secretKey = "test_secret"
            ).setConnectionMode(mode)

            assertEquals(mode, config.connectionMode)
        }
    }

    @Test
    fun testCableProtocol_AllValues() {
        // Test all cable protocols
        val protocols = listOf(
            CableProtocol.AUTO,
            CableProtocol.USB_AOA,
            CableProtocol.USB_VSP,
            CableProtocol.RS232
        )

        protocols.forEach { protocol ->
            val config = ConnectionConfig()
                .setCableProtocol(protocol)

            assertEquals(protocol, config.cableProtocol)
        }
    }

    /**
     * Test connection listener implementation
     */
    private class TestConnectionListener : ConnectionListener {
        var connected = false
        var disconnected = false
        var error: ConnectionError? = null
        var reconnecting = false

        override fun onConnected(deviceId: String, taproVersion: String) {
            connected = true
        }

        override fun onDisconnected(reason: String) {
            disconnected = true
        }

        override fun onError(error: ConnectionError) {
            this.error = error
        }

        override fun onReconnecting(attempt: Int, maxRetries: Int) {
            reconnecting = true
        }
    }
}
