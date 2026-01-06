package com.sunmi.tapro.taplink.communication.protocol

import com.sunmi.tapro.taplink.communication.util.LogUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue

/**
 * 十六进制帧缓冲区管理器
 *
 * 负责管理接收数据的缓冲、帧提取和超时处理
 * 使用单线程顺序处理，确保数据按接收顺序处理，避免并发导致的数据错乱
 *
 * 功能特性：
 * - 单线程顺序处理，保证FIFO顺序
 * - 自动帧提取和校验
 * - 缓冲区溢出保护
 * - 消息超时检测
 * - 线程安全
 *
 * @param scope 协程作用域，用于超时检测
 * @param onFrameReceived 完整帧接收回调
 * @param maxBufferSize 最大缓冲区大小（字符数）
 * @param messageTimeoutMs 消息超时时间（毫秒）
 *
 * @author TaPro Team
 * @since 2025-01-06
 */
class HexFrameBuffer(
    private val scope: CoroutineScope,
    private val onFrameReceived: (ByteArray) -> Unit,
    private val maxBufferSize: Int = 128 * 1024, // 128KB
    private val messageTimeoutMs: Long = 5000L
) {
    private val TAG = "HexFrameBuffer"

    /**
     * 单线程数据处理执行器，确保数据按接收顺序处理
     */
    private val dataProcessingExecutor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "HexFrameProcessor").apply {
            isDaemon = true
        }
    }

    /**
     * 数据队列，保证FIFO顺序处理
     */
    private val dataQueue = LinkedBlockingQueue<ByteArray>()

    /**
     * 十六进制数据缓冲区
     */
    private val hexBuffer = StringBuilder()

    /**
     * 最后接收数据的时间戳
     */
    private var lastDataTimestamp = 0L

    /**
     * 是否已启动
     */
    private var isStarted = false

    init {
        startSequentialDataProcessor()
        startBufferTimeoutChecker()
    }

    /**
     * 添加接收到的数据到处理队列
     *
     * @param data 接收到的数据
     * @return 是否成功加入队列
     */
    fun addData(data: ByteArray): Boolean {
        if (!isStarted) {
            LogUtil.w(TAG, "Buffer not started, ignoring data")
            return false
        }

        val success = dataQueue.offer(data)
        if (!success) {
            LogUtil.w(TAG, "Data queue is full, dropping data: ${String(data)}")
        }
        return success
    }

    /**
     * 清空缓冲区
     */
    fun clear() {
        synchronized(hexBuffer) {
            hexBuffer.clear()
            lastDataTimestamp = 0L
            LogUtil.d(TAG, "Buffer cleared")
        }
    }

    /**
     * 获取当前缓冲区大小
     */
    fun getBufferSize(): Int {
        synchronized(hexBuffer) {
            return hexBuffer.length
        }
    }

    /**
     * 获取最后接收数据的时间戳
     */
    fun getLastDataTimestamp(): Long {
        synchronized(hexBuffer) {
            return lastDataTimestamp
        }
    }

    /**
     * 停止缓冲区处理
     */
    fun stop() {
        isStarted = false
        dataProcessingExecutor.shutdown()
        dataQueue.clear()
        clear()
        LogUtil.d(TAG, "Buffer stopped")
    }

    /**
     * 启动顺序数据处理器
     * 使用单线程执行器确保数据按接收顺序处理，解决并发导致的数据错乱问题
     */
    private fun startSequentialDataProcessor() {
        isStarted = true

        dataProcessingExecutor.execute {
            LogUtil.d(TAG, "Sequential hex data processor started")

            while (!Thread.currentThread().isInterrupted && isStarted) {
                try {
                    // 阻塞等待数据，保证FIFO顺序
                    val data = dataQueue.take()
                    processDataInOrder(data)
                } catch (e: InterruptedException) {
                    LogUtil.d(TAG, "Hex data processor interrupted")
                    Thread.currentThread().interrupt()
                    break
                } catch (e: Exception) {
                    LogUtil.e(TAG, "Error in sequential hex data processing: ${e.message}")
                }
            }

            LogUtil.d(TAG, "Sequential hex data processor stopped")
        }
    }

    /**
     * 按顺序处理接收到的数据片段
     * 替代原来的协程并发处理，确保数据顺序正确
     */
    private fun processDataInOrder(data: ByteArray) {
        val dataString = String(data, Charsets.UTF_8)
        LogUtil.d(TAG, "Processing hex data in order: $dataString (${data.size} bytes)")

        synchronized(hexBuffer) {
            // 更新时间戳
            lastDataTimestamp = System.currentTimeMillis()

            // 检查缓冲区大小
            if (hexBuffer.length + dataString.length > maxBufferSize) {
                LogUtil.w(TAG, "Hex buffer overflow, clearing buffer")
                hexBuffer.clear()
            }

            // 添加到缓冲区
            hexBuffer.append(dataString)

            // 尝试提取完整的十六进制帧
            extractCompleteFrames()
        }
    }

    /**
     * 从缓冲区提取完整的十六进制帧
     */
    private fun extractCompleteFrames() {
        val bufferContent = hexBuffer.toString()

        // 使用HexFrameProtocol解码
        val result = HexFrameProtocol.decode(bufferContent)

        // 处理解码出的帧
        for (frame in result.frames) {
            try {
                onFrameReceived(frame)
            } catch (e: Exception) {
                LogUtil.e(TAG, "Error in frame received callback: ${e.message}")
            }
        }

        // 清理已处理的数据
        if (result.processedChars > 0) {
            hexBuffer.delete(0, result.processedChars)
            LogUtil.d(TAG, "Hex buffer cleaned, remaining: ${hexBuffer.length} chars")
        }
    }

    /**
     * 启动缓冲区超时检查器
     */
    private fun startBufferTimeoutChecker() {
        scope.launch {
            while (isStarted) {
                delay(1000) // 每秒检查一次

                synchronized(hexBuffer) {
                    val currentTime = System.currentTimeMillis()

                    // 检查是否有超时的不完整消息
                    if (hexBuffer.isNotEmpty() &&
                        lastDataTimestamp > 0 &&
                        currentTime - lastDataTimestamp > messageTimeoutMs
                    ) {

                        LogUtil.w(TAG, "Hex message timeout, clearing buffer: ${hexBuffer.toString()}")
                        hexBuffer.clear()
                        lastDataTimestamp = 0L
                    }
                }
            }
        }
    }
}
