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
}
