package com.sunmi.tapro.taplink.communication.lan

import org.junit.Test
import org.junit.Assert.*

/**
 * Covers detection of a Tapro terminal that restarted its LAN server.
 *
 * Tapro re-registers its mDNS service with a fresh timestamp in the instance name every time it
 * starts, so a restart shows up as a new instance name advertising the same host:port. Because the
 * address is unchanged, the address-change path ignores it, and because the peer vanished without
 * a WebSocket close handshake the socket stays half-open and keeps reporting CONNECTED while
 * silently dropping every request. Recognising this pattern is what lets the SDK invalidate the
 * stale link and reconnect.
 */
class LanClientKernelPeerRestartTest {

    private val host = "10.23.76.75"
    private val port = 8443

    @Test
    fun `new instance name at same address is treated as peer restart`() {
        assertTrue(
            LanClientKernel.isPeerRestart(
                previousName = "Taplink-Server-1787900514782-PA12P58T10075",
                previousHost = host,
                previousPort = port,
                newName = "Taplink-Server-1787908618928-PA12P58T10075",
                newHost = host,
                newPort = port
            )
        )
    }

    @Test
    fun `same instance name is not a restart`() {
        val name = "Taplink-Server-1787900514782-PA12P58T10075"
        assertFalse(
            LanClientKernel.isPeerRestart(
                previousName = name,
                previousHost = host,
                previousPort = port,
                newName = name,
                newHost = host,
                newPort = port
            )
        )
    }

    @Test
    fun `changed host is an address change, not a restart`() {
        assertFalse(
            LanClientKernel.isPeerRestart(
                previousName = "Taplink-Server-1787900514782-PA12P58T10075",
                previousHost = host,
                previousPort = port,
                newName = "Taplink-Server-1787908618928-PA12P58T10075",
                newHost = "10.23.76.82",
                newPort = port
            )
        )
    }

    @Test
    fun `changed port is an address change, not a restart`() {
        assertFalse(
            LanClientKernel.isPeerRestart(
                previousName = "Taplink-Server-1787900514782-PA12P58T10075",
                previousHost = host,
                previousPort = port,
                newName = "Taplink-Server-1787908618928-PA12P58T10075",
                newHost = host,
                newPort = 9443
            )
        )
    }

    // ==================== parseServiceSerial ====================
    // 锁定 LAN 设备身份识别的唯一 serial 解析入口：SDK 层用它把断开错误归因到正确设备，
    // 并按 serial 精确判定「同设备重连」而非走保守策略（见 ConnectionManager.analyzeDeviceIdentity）。

    @Test
    fun `parseServiceSerial extracts trailing serial segment`() {
        assertEquals(
            "PD09251101944",
            LanClientKernel.parseServiceSerial("Taplink-Server-1788244709643-PD09251101944")
        )
    }

    @Test
    fun `parseServiceSerial is case insensitive on prefix`() {
        assertEquals(
            "PA12P58T10075",
            LanClientKernel.parseServiceSerial("taplink-server-1787900514782-PA12P58T10075")
        )
    }

    @Test
    fun `parseServiceSerial returns null for non-taplink service name`() {
        assertNull(LanClientKernel.parseServiceSerial("SomeOther-Service-123-ABC"))
    }

    @Test
    fun `parseServiceSerial returns null when serial segment empty`() {
        assertNull(LanClientKernel.parseServiceSerial("Taplink-Server-1788244709643-"))
    }

    @Test
    fun `two instances of same device share the same parsed serial`() {
        // 对端重启会换实例名（时间戳变化）但 serial 不变——正是「同设备」判定的依据。
        val a = LanClientKernel.parseServiceSerial("Taplink-Server-1788243774964-PD09251101944")
        val b = LanClientKernel.parseServiceSerial("Taplink-Server-1788244709643-PD09251101944")
        assertEquals(a, b)
        assertEquals("PD09251101944", a)
    }
}
