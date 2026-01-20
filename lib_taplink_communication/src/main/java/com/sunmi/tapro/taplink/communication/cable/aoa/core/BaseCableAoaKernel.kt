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
 * USB AOA 连接会话阶段
 * 
 * 用于区分连接流程的不同阶段，正确处理 AOA 切换期的 DETACHED 事件
 */
enum class AoaSessionPhase {
    IDLE,                           // 空闲
    REQUEST_ORIGINAL_PERMISSION,    // 请求原始设备权限
    SENDING_AOA,                    // 发送 AOA 协议
    WAIT_AOA_ATTACH,                // 等待 AOA 设备出现（此时允许原始设备 detach）
    REQUEST_AOA_PERMISSION,          // 请求 AOA 设备权限
    OPENING_IO,                     // 正在打开 IO
    RUNNING,                        // 运行中（数据通信）
    CLOSING                         // 正在关闭
}

/**
 * USB AOA IO 资源包
 * 
 * 注意：
 * - connection: Host 模式使用（UsbDevice），Accessory 模式为 null（使用 ParcelFileDescriptor）
 * - inputStream/outputStream: Accessory 模式使用，Host 模式为 null（使用 bulkTransfer）
 */
data class AoaIoBundle(
    val connection: UsbDeviceConnection?,  // Host 模式使用，Accessory 模式为 null
    val inputStream: InputStream?,         // Accessory 模式使用，Host 模式为 null
    val outputStream: OutputStream?        // Accessory 模式使用，Host 模式为 null
)

/**
 * USB AOA 连接会话
 * 
 * 核心：每次连接流程创建一个 Session（包含 token、scope、资源、阶段），
 * 所有协程都必须在 session.scope 内启动，session 结束时自动 cancel 全部子任务。
 */
data class UsbAoaSession(
    val token: Long,
    val scope: CoroutineScope,
    val createdAtMs: Long = SystemClock.uptimeMillis(),
    val phase: AtomicReference<AoaSessionPhase> = AtomicReference(AoaSessionPhase.IDLE),
    val io: AtomicReference<AoaIoBundle?> = AtomicReference(null)
) {
    /**
     * 检查会话是否仍然活跃
     */
    fun isActive(currentToken: Long): Boolean = token == currentToken && token != 0L
}

/**
 * USB AOA内核共同基类
 *
 * 提供Host和Accessory模式的共同功能：
 * - USB管理器初始化
 * - 协议验证
 * - 基础连接管理
 * - 数据接收任务管理
 * - Session 生命周期管理（防止旧协程/旧广播误伤新连接）
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

    // ==================== Session 生命周期管理 ====================

    /**
     * 操作序列号生成器（使用 AtomicLong 确保线程安全且唯一）
     */
    private val opSeq = AtomicLong(0L)

    /**
     * 当前活跃的会话（连接会话）
     * 
     * 用于防止旧协程/旧广播误伤新连接：
     * - 每次连接时创建新的 session
     * - 所有协程都在 session.scope 内启动
     * - session 结束时自动 cancel 全部子任务
     * - IO 资源归 session 所有，避免旧会话 close 新会话资源
     */
    private val sessionRef = AtomicReference<UsbAoaSession?>(null)

    /**
     * IO 资源互斥锁
     * 
     * 用于保证 open/close 的原子性，避免并发操作导致资源泄漏
     */
    protected val ioMutex = kotlinx.coroutines.sync.Mutex()

    /**
     * 创建新的会话
     * 
     * @param reason 操作原因（用于日志）
     * @return 新的会话
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
     * 结束指定 token 的会话
     * 
     * @param token 会话 token
     * @param reason 结束原因（用于日志）
     */
    protected suspend fun endSession(token: Long, reason: String) {
        val session = sessionRef.get()
        if (session != null && session.token == token) {
            sessionRef.compareAndSet(session, null)
            LogUtil.i(TAG, "=== SESSION ENDED: token=$token, reason=$reason ===")
            // 先取消所有子任务（让子任务有机会正常退出）
            session.scope.cancel("Session ended: $reason")
            // 然后关闭 IO 资源
            ioMutex.withLock {
                val io = session.io.getAndSet(null)
                io?.let {
                    // 关闭 inputStream（Accessory 模式使用，Host 模式为 null）
                    try {
                        it.inputStream?.close()
                    } catch (e: Exception) {
                        LogUtil.w(TAG, "Failed to close input stream: ${e.message}")
                    }
                    // 关闭 outputStream（Accessory 模式使用，Host 模式为 null）
                    try {
                        it.outputStream?.close()
                    } catch (e: Exception) {
                        LogUtil.w(TAG, "Failed to close output stream: ${e.message}")
                    }
                    // 关闭 connection（Host 模式使用，Accessory 模式为 null）
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
     * 获取当前活跃的会话
     */
    protected fun currentSession(): UsbAoaSession? = sessionRef.get()

    /**
     * 获取当前活跃的操作令牌（向后兼容）
     */
    protected fun currentOpToken(): Long = currentSession()?.token ?: 0L

    /**
     * 检查操作令牌是否活跃（是否为当前连接）
     */
    protected fun isTokenActive(token: Long): Boolean {
        val session = currentSession()
        return session != null && session.token == token && token != 0L
    }

    /**
     * 检查会话是否仍然活跃
     */
    protected fun isSessionActive(session: UsbAoaSession?): Boolean {
        return session != null && sessionRef.get() == session
    }

    // ==================== 数据接收任务管理 ====================

    // 数据接收任务（已废弃，使用 session.scope 管理）
    @Deprecated("Use session.scope to manage receive job", ReplaceWith("session.scope.launch { ... }"))
    protected var dataReceiveJob: Job? = null

    /**
     * 重启数据接收任务（已废弃，使用 session.scope 管理）
     */
    @Deprecated("Use session.scope to manage receive job", ReplaceWith("session.scope.launch { ... }"))
    protected fun restartDataReceive(start: () -> Job) {
        dataReceiveJob?.cancel()
        dataReceiveJob = start()
    }

    // ==================== AsyncServiceKernel抽象方法实现 ====================

    /**
     * 获取服务类型名称（用于日志）
     */
    override fun getServiceType(): String {
        return "USB AOA"
    }

    /**
     * 获取期望的协议类型描述
     */
    override fun getExpectedProtocolType(): String {
        return "USB"
    }

    /**
     * 验证协议类型是否有效
     */
    override fun isValidProtocolType(parseResult: ProtocolParseResult): Boolean {
        return parseResult is ProtocolParseResult.UsbProtocol
    }

    /**
     * 执行具体的连接逻辑
     * 
     * 优化：
     * 1. 创建新 session，取消旧 session，避免并发连接
     * 2. 【修复】立即设置 session 到 sessionRef，确保 requestPermission() 能获取到有效 token
     * 3. 在 session.scope 内启动连接流程
     * 4. 统一异常兜底，确保异常一定回调错误
     */
    override fun performConnect(parseResult: ProtocolParseResult, connectionCallback: ConnectionCallback) {
        if (parseResult !is ProtocolParseResult.UsbProtocol) {
            handleConnectionError(InnerErrorCode.E254.description, connectionCallback, InnerErrorCode.E254.code)
            return
        }

        // 取消旧连接任务和旧 session
        connectJob?.cancel()
        val oldSession = sessionRef.getAndSet(null)
        oldSession?.let {
            scope.launch {
                endSession(it.token, "new connect started")
            }
        }

        // 【修复 P0-1】立即创建新 session 并设置到 sessionRef
        // 这样可以确保后续的 requestPermission() 调用时，currentOpToken() 能返回有效值
        // 避免 pendingPermissionOpToken = 0 导致 permission 广播被误判为 stale
        val session = createSession("performConnect")
        sessionRef.set(session)
        LogUtil.i(TAG, "Session created and set: token=${session.token}")

        // 在 session.scope 内启动连接流程（单层 launch）
        connectJob = session.scope.launch {
            LogUtil.i(TAG, "Starting performUsbConnect in coroutine, token=${session.token}")
            runCatching {
                performUsbConnect(parseResult, connectionCallback, session)
            }.onFailure { t ->
                LogUtil.e(TAG, "performUsbConnect failed: ${t.message}")
                t.printStackTrace()
                // 统一异常兜底，确保异常一定回调错误
                handleConnectionError(
                    t.message ?: InnerErrorCode.E254.description,
                    connectionCallback,
                    InnerErrorCode.E254.code
                )
                // 结束 session（在协程内调用 suspend 函数）
                endSession(session.token, "connect failed: ${t.message}")
            }
        }
    }

    /**
     * 执行USB连接逻辑（由子类实现）
     * 
     * @param parseResult USB协议解析结果
     * @param connectionCallback 连接回调
     * @param session 连接会话，包含 token、scope、phase、io 等
     */
    protected abstract suspend fun performUsbConnect(
        parseResult: ProtocolParseResult.UsbProtocol,
        connectionCallback: ConnectionCallback,
        session: UsbAoaSession
    )

    /**
     * 启动数据接收（由子类实现）
     * 
     * 注意：子类应该在 session.scope 内启动接收任务，确保 session 结束时自动取消
     * 
     * @param session 连接会话
     * @param inputStream 输入流
     */
    protected abstract fun startDataReceive(session: UsbAoaSession, inputStream: InputStream?)

    /**
     * 断开连接（内部方法，由子类实现）
     * 
     * @param token 会话 token，用于验证断开的是当前会话
     * @param reason 断开原因（用于日志）
     */
    protected abstract suspend fun disconnectInternal(token: Long, reason: String)

    /**
     * 执行具体的断开连接逻辑
     * 
     * 注意：connectJob 的取消已经在 AsyncServiceKernel.disconnect() 中完成，这里不需要重复取消
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
     * 清理公共资源
     * 
     * 注意：connectJob 的清理已经在 AsyncServiceKernel.cleanupCommonResources() 中完成
     * 这里只需要清理 USB AOA 特有的 session
     */
    override fun cleanupCommonResources() {
        super.cleanupCommonResources()

        // 结束当前 session（会自动取消所有子任务和关闭 IO 资源）
        // 使用 runBlocking 调用 endSession，避免重复 close IO
        val session = sessionRef.getAndSet(null)
        session?.let {
            runBlocking {
                endSession(it.token, "cleanup")
            }
        }
    }

    /**
     * 清理资源（由子类实现）
     */
    abstract fun release()
}
