package com.sunmi.tapro.taplink.communication.protocol

import com.sunmi.tapro.taplink.communication.util.LogUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue

/**
 * Hexadecimal frame buffer manager
 *
 * Responsible for managing buffering, frame extraction, and timeout handling of received data
 * Uses single-threaded sequential processing to ensure data is processed in receiving order, avoiding data corruption caused by concurrency
 *
 * Features:
 * - Single-threaded sequential processing, guarantees FIFO order
 * - Automatic frame extraction and validation
 * - Buffer overflow protection
 * - Message timeout detection
 * - Thread-safe
 *
 * @param scope Coroutine scope for timeout detection
 * @param onFrameReceived Complete frame received callback
 * @param maxBufferSize Maximum buffer size (character count)
 * @param messageTimeoutMs Message timeout time (milliseconds)
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
     * Single-threaded data processing executor, ensures data is processed in receiving order
     */
    private val dataProcessingExecutor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "HexFrameProcessor").apply {
            isDaemon = true
        }
    }

    /**
     * Data queue, guarantees FIFO sequential processing
     */
    private val dataQueue = LinkedBlockingQueue<ByteArray>()

    /**
     * Hexadecimal data buffer
     */
    private val hexBuffer = StringBuilder()

    /**
     * Timestamp of last received data
     */
    private var lastDataTimestamp = 0L

    /**
     * Whether started
     */
    private var isStarted = false

    init {
        startSequentialDataProcessor()
        startBufferTimeoutChecker()
    }

    /**
     * Add received data to processing queue
     *
     * @param data Received data
     * @return Whether successfully added to queue
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
     * Clear buffer
     */
    fun clear() {
        synchronized(hexBuffer) {
            hexBuffer.clear()
            lastDataTimestamp = 0L
            LogUtil.d(TAG, "Buffer cleared")
        }
    }

    /**
     * Get current buffer size
     */
    fun getBufferSize(): Int {
        synchronized(hexBuffer) {
            return hexBuffer.length
        }
    }

    /**
     * Get timestamp of last received data
     */
    fun getLastDataTimestamp(): Long {
        synchronized(hexBuffer) {
            return lastDataTimestamp
        }
    }

    /**
     * Stop buffer processing
     */
    fun stop() {
        isStarted = false
        dataProcessingExecutor.shutdown()
        dataQueue.clear()
        clear()
        LogUtil.d(TAG, "Buffer stopped")
    }

    /**
     * Start sequential data processor
     * Uses single-threaded executor to ensure data is processed in receiving order, solves data corruption issues caused by concurrency
     */
    private fun startSequentialDataProcessor() {
        isStarted = true

        dataProcessingExecutor.execute {
            LogUtil.d(TAG, "Sequential hex data processor started")

            while (!Thread.currentThread().isInterrupted && isStarted) {
                try {
                    // Block waiting for data, guarantees FIFO order
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
     * Process received data fragments in order
     * Replaces original coroutine concurrent processing, ensures correct data order
     */
    private fun processDataInOrder(data: ByteArray) {
        val dataString = String(data, Charsets.UTF_8)
        LogUtil.d(TAG, "Processing hex data in order: $dataString (${data.size} bytes)")

        synchronized(hexBuffer) {
            // Update timestamp
            lastDataTimestamp = System.currentTimeMillis()

            // Check buffer size
            if (hexBuffer.length + dataString.length > maxBufferSize) {
                LogUtil.w(TAG, "Hex buffer overflow, clearing buffer")
                hexBuffer.clear()
            }

            // Add to buffer
            hexBuffer.append(dataString)

            // Try to extract complete hexadecimal frames
            extractCompleteFrames()
        }
    }

    /**
     * Extract complete hexadecimal frames from buffer
     */
    private fun extractCompleteFrames() {
        val bufferContent = hexBuffer.toString()

        // Use HexFrameProtocol to decode
        val result = HexFrameProtocol.decode(bufferContent)

        // Process decoded frames
        for (frame in result.frames) {
            try {
                onFrameReceived(frame)
            } catch (e: Exception) {
                LogUtil.e(TAG, "Error in frame received callback: ${e.message}")
            }
        }

        // Clean up processed data
        if (result.processedChars > 0) {
            hexBuffer.delete(0, result.processedChars)
            LogUtil.d(TAG, "Hex buffer cleaned, remaining: ${hexBuffer.length} chars")
        }
    }

    /**
     * Start buffer timeout checker
     */
    private fun startBufferTimeoutChecker() {
        scope.launch {
            while (isStarted) {
                delay(1000) // Check every second

                synchronized(hexBuffer) {
                    val currentTime = System.currentTimeMillis()

                    // Check if there are incomplete messages that have timed out
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
