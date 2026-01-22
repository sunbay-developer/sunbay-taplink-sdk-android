package com.sunmi.tapro.taplink.communication.cable.aoa.core

import android.app.PendingIntent
import android.content.Context
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager
import android.os.SystemClock
import com.sunmi.tapro.taplink.communication.enums.InnerErrorCode
import com.sunmi.tapro.taplink.communication.interfaces.AsyncServiceKernel
import com.sunmi.tapro.taplink.communication.protocol.ProtocolParseResult
import com.sunmi.tapro.taplink.communication.protocol.UsbStandardProtocol
import com.sunmi.tapro.taplink.communication.interfaces.ConnectionCallback
import com.sunmi.tapro.taplink.communication.util.LogUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.withLock
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * USB AOA connection session phase
 * 
 * Used to distinguish different phases of connection flow, correctly handle DETACHED events during AOA switching
 */
enum class AoaSessionPhase {
    IDLE,                           // Idle
    REQUEST_ORIGINAL_PERMISSION,    // Request original device permission
    SENDING_AOA,                    // Sending AOA protocol
    WAIT_AOA_ATTACH,                // Wait for AOA device to appear (original device detach allowed at this time)
    REQUEST_AOA_PERMISSION,          // Request AOA device permission
    OPENING_IO,                     // Opening IO
    RUNNING,                        // Running (data communication)
    CLOSING                         // Closing
}

/**
 * USB AOA IO resource bundle
 * 
 * Note:
 * - connection: Used in Host mode (UsbDevice), null in Accessory mode (uses ParcelFileDescriptor)
 * - inputStream/outputStream: Used in Accessory mode, null in Host mode (uses bulkTransfer)
 */
data class AoaIoBundle(
    val connection: UsbDeviceConnection?,  // Used in Host mode, null in Accessory mode
    val inputStream: InputStream?,         // Used in Accessory mode, null in Host mode
    val outputStream: OutputStream?        // Used in Accessory mode, null in Host mode
)

/**
 * USB AOA connection session
 * 
 * Core: Create a Session for each connection flow (contains token, scope, resources, phase),
 * all coroutines must be launched within session.scope, automatically cancel all child tasks when session ends.
 */
data class UsbAoaSession(
    val token: Long,
    val scope: CoroutineScope,
    val createdAtMs: Long = SystemClock.uptimeMillis(),
    val phase: AtomicReference<AoaSessionPhase> = AtomicReference(AoaSessionPhase.IDLE),
    val io: AtomicReference<AoaIoBundle?> = AtomicReference(null)
) {
    /**
     * Check if session is still active
     */
    fun isActive(currentToken: Long): Boolean = token == currentToken && token != 0L
}

/**
 * USB AOA kernel common base class
 *
 * Provides common functionality for Host and Accessory modes:
 * - USB manager initialization
 * - Protocol validation
 * - Basic connection management
 * - Data receive task management
 * - Session lifecycle management (prevent old coroutines/old broadcasts from affecting new connections)
 *
 * @author TaPro Team
 * @since 2025-01-XX
 */
abstract class BaseUsbAoaKernel(
    appId: String,
    appSecretKey: String,
    protected val context: Context,
    protected val usbStandardInfo: UsbStandardProtocol.UsbStandardInfo,
    protected val permissionAction: String,
    protected val permissionPendingIntent: PendingIntent,
) : AsyncServiceKernel(appId, appSecretKey) {

    protected open val TAG = this::class.simpleName ?: "BaseUsbAoaKernel"

    protected val usbManager: UsbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager

    // ==================== Session Lifecycle Management ====================

    /**
     * Operation sequence number generator (uses AtomicLong to ensure thread-safe and unique)
     */
    private val opSeq = AtomicLong(0L)

    /**
     * Current active session (connection session)
     * 
     * Used to prevent old coroutines/old broadcasts from affecting new connections:
     * - Create new session for each connection
     * - All coroutines launched within session.scope
     * - Automatically cancel all child tasks when session ends
     * - IO resources belong to session, avoid old session closing new session resources
     */
    private val sessionRef = AtomicReference<UsbAoaSession?>(null)

    /**
     * IO resource mutex lock
     * 
     * Used to ensure atomicity of open/close, avoid resource leaks from concurrent operations
     */
    protected val ioMutex = kotlinx.coroutines.sync.Mutex()

    /**
     * Create new session
     * 
     * @param reason Operation reason (for logging)
     * @return New session
     */
    protected open fun createSession(reason: String): UsbAoaSession {
        val token = opSeq.incrementAndGet()
        val sessionScope = CoroutineScope(SupervisorJob() + scope.coroutineContext)
        val session = UsbAoaSession(
            token = token,
            scope = sessionScope,
            phase = AtomicReference(AoaSessionPhase.IDLE)
        )
        LogUtil.i(TAG, "=== SESSION CREATED: token=${session.token}, reason=$reason ===")
        return session
    }

    /**
     * End session with specified token
     * 
     * @param token Session token
     * @param reason End reason (for logging)
     */
    protected suspend fun endSession(token: Long, reason: String) {
        val session = sessionRef.get()
        if (session != null && session.token == token) {
            sessionRef.compareAndSet(session, null)
            LogUtil.i(TAG, "=== SESSION ENDED: token=$token, reason=$reason ===")
            // First cancel all child tasks (give child tasks chance to exit normally)
            session.scope.cancel("Session ended: $reason")
            // Then close IO resources
            ioMutex.withLock {
                val io = session.io.getAndSet(null)
                io?.let {
                    // Close inputStream (used in Accessory mode, null in Host mode)
                    try {
                        it.inputStream?.close()
                    } catch (e: Exception) {
                        LogUtil.w(TAG, "Failed to close input stream: ${e.message}")
                    }
                    // Close outputStream (used in Accessory mode, null in Host mode)
                    try {
                        it.outputStream?.close()
                    } catch (e: Exception) {
                        LogUtil.w(TAG, "Failed to close output stream: ${e.message}")
                    }
                    // Close connection (used in Host mode, null in Accessory mode)
                    it.connection?.let { conn ->
                        try {
                            conn.close()
                        } catch (e: Exception) {
                            LogUtil.w(TAG, "Failed to close connection: ${e.message}")
                        }
                    }
                }
            }
        } else {
            LogUtil.d(TAG, "endSession: session not found or token mismatch (requested=$token, current=${session?.token})")
        }
    }

    /**
     * Get current active session
     */
    protected fun currentSession(): UsbAoaSession? = sessionRef.get()

    /**
     * Get current active operation token (backward compatible)
     */
    protected fun currentOpToken(): Long = currentSession()?.token ?: 0L

    /**
     * Check if operation token is active (whether it's current connection)
     */
    protected fun isTokenActive(token: Long): Boolean {
        val session = currentSession()
        return session != null && session.token == token && token != 0L
    }

    /**
     * Check if session is still active
     */
    protected fun isSessionActive(session: UsbAoaSession?): Boolean {
        return session != null && sessionRef.get() == session
    }

    // ==================== Data Receive Task Management ====================

    // Data receive task (deprecated, use session.scope to manage)
    @Deprecated("Use session.scope to manage receive job", ReplaceWith("session.scope.launch { ... }"))
    protected var dataReceiveJob: Job? = null

    /**
     * Restart data receive task (deprecated, use session.scope to manage)
     */
    @Deprecated("Use session.scope to manage receive job", ReplaceWith("session.scope.launch { ... }"))
    protected fun restartDataReceive(start: () -> Job) {
        dataReceiveJob?.cancel()
        dataReceiveJob = start()
    }

    // ==================== AsyncServiceKernel Abstract Method Implementation ====================

    /**
     * Get service type name (for logging)
     */
    override fun getServiceType(): String {
        return "USB AOA"
    }

    /**
     * Get expected protocol type description
     */
    override fun getExpectedProtocolType(): String {
        return "USB"
    }

    /**
     * Validate if protocol type is valid
     */
    override fun isValidProtocolType(parseResult: ProtocolParseResult): Boolean {
        return parseResult is ProtocolParseResult.UsbProtocol
    }

    /**
     * Execute specific connection logic
     * 
     * Optimizations:
     * 1. Create new session, cancel old session, avoid concurrent connections
     * 2. [Fix] Immediately set session to sessionRef, ensure requestPermission() can get valid token
     * 3. Launch connection flow within session.scope
     * 4. Unified exception fallback, ensure exceptions always callback error
     */
    override fun performConnect(parseResult: ProtocolParseResult, connectionCallback: ConnectionCallback) {
        if (parseResult !is ProtocolParseResult.UsbProtocol) {
            handleConnectionError(InnerErrorCode.E254.description, connectionCallback, InnerErrorCode.E254.code)
            return
        }

        // Cancel old connection task and old session
        connectJob?.cancel()
        val oldSession = sessionRef.getAndSet(null)
        oldSession?.let {
            scope.launch {
                endSession(it.token, "new connect started")
            }
        }

        // [Fix P0-1] Immediately create new session and set to sessionRef
        // This ensures that when requestPermission() is called later, currentOpToken() can return valid value
        // Avoid pendingPermissionOpToken = 0 causing permission broadcast to be misjudged as stale
        val session = createSession("performConnect")
        sessionRef.set(session)
        LogUtil.i(TAG, "Session created and set: token=${session.token}")

        // Launch connection flow within session.scope (single layer launch)
        connectJob = session.scope.launch {
            LogUtil.i(TAG, "Starting performUsbConnect in coroutine, token=${session.token}")
            runCatching {
                performUsbConnect(parseResult, connectionCallback, session)
            }.onFailure { t ->
                LogUtil.e(TAG, "performUsbConnect failed: ${t.message}")
                t.printStackTrace()
                // Unified exception fallback, ensure exceptions always callback error
                handleConnectionError(
                    t.message ?: InnerErrorCode.E254.description,
                    connectionCallback,
                    InnerErrorCode.E254.code
                )
                // End session (call suspend function within coroutine)
                endSession(session.token, "connect failed: ${t.message}")
            }
        }
    }

    /**
     * Execute USB connection logic (implemented by subclass)
     * 
     * @param parseResult USB protocol parse result
     * @param connectionCallback Connection callback
     * @param session Connection session, contains token, scope, phase, io, etc.
     */
    protected abstract suspend fun performUsbConnect(
        parseResult: ProtocolParseResult.UsbProtocol,
        connectionCallback: ConnectionCallback,
        session: UsbAoaSession
    )

    /**
     * Start data receive (implemented by subclass)
     * 
     * Note: Subclass should launch receive task within session.scope, ensure automatic cancellation when session ends
     * 
     * @param session Connection session
     * @param inputStream Input stream
     */
    protected abstract fun startDataReceive(session: UsbAoaSession, inputStream: InputStream?)

    /**
     * Disconnect connection (internal method, implemented by subclass)
     * 
     * @param token Session token, used to verify disconnecting is current session
     * @param reason Disconnect reason (for logging)
     */
    protected abstract suspend fun disconnectInternal(token: Long, reason: String)

    /**
     * Execute specific disconnect logic
     * 
     * Note: connectJob cancellation is already done in AsyncServiceKernel.disconnect(), no need to cancel again here
     */
    override fun performDisconnect() {
        val session = currentSession()
        if (session != null) {
            scope.launch {
                disconnectInternal(session.token, "manual disconnect")
            }
        }
    }

    /**
     * Clean up common resources
     * 
     * Note: connectJob cleanup is already done in AsyncServiceKernel.cleanupCommonResources()
     * Only need to clean up USB AOA specific session here
     */
    override fun cleanupCommonResources() {
        super.cleanupCommonResources()

        // End current session (will automatically cancel all child tasks and close IO resources)
        // Use runBlocking to call endSession, avoid duplicate close IO
        val session = sessionRef.getAndSet(null)
        session?.let {
            runBlocking {
                endSession(it.token, "cleanup")
            }
        }
    }

    /**
     * Clean up resources (implemented by subclass)
     */
    abstract fun release()
}
