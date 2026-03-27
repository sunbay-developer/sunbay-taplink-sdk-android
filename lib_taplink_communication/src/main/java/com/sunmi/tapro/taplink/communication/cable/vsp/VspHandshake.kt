package com.sunmi.tapro.taplink.communication.cable.vsp

/**
 * VSP handshake protocol constants and utilities.
 *
 * After the physical serial port is opened, a lightweight application-level
 * handshake is performed to verify that both ends are ready to communicate.
 *
 * Protocol:
 *   Initiator  -->  ##TAPLINK_HSK_REQ##  -->  Responder
 *   Initiator  <--  ##TAPLINK_HSK_ACK##  <--  Responder
 *
 * Either side may act as initiator; the other side must reply with ACK.
 * The markers are chosen so they will never collide with normal JSON traffic.
 */
object VspHandshake {

    const val REQ = "##TAPLINK_HSK_REQ##"
    const val ACK = "##TAPLINK_HSK_ACK##"

    /** Default interval between handshake request retries (ms). */
    const val DEFAULT_INTERVAL_MS = 1_000L

    /** Default total timeout for the handshake phase (ms). */
    const val DEFAULT_TIMEOUT_MS = 30_000L

    /** Polling interval while waiting for a response (ms). */
    const val POLL_INTERVAL_MS = 50L

    /**
     * Returns `true` if [data] contains a handshake request marker.
     */
    fun containsReq(data: String): Boolean = data.contains(REQ)

    /**
     * Returns `true` if [data] contains a handshake acknowledgment marker.
     */
    fun containsAck(data: String): Boolean = data.contains(ACK)

    /**
     * Returns `true` if [data] contains any handshake marker (REQ or ACK).
     */
    fun isHandshakeMessage(data: String): Boolean = containsReq(data) || containsAck(data)

    /**
     * Strips all handshake markers from [data] and returns the remaining
     * payload, or `null` if nothing is left after stripping.
     */
    fun stripHandshakeMarkers(data: String): String? {
        val stripped = data.replace(REQ, "").replace(ACK, "").trim()
        return stripped.ifEmpty { null }
    }
}
