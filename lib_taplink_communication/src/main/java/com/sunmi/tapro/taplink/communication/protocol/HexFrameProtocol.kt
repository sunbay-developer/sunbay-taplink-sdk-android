package com.sunmi.tapro.taplink.communication.protocol

import com.sunmi.tapro.taplink.communication.util.LogUtil

/**
 * 十六进制帧传输协议
 *
 * 协议格式：FF + 长度(4位) + 数据(十六进制) + 校验和(2位) + FE
 *
 * 帧结构说明：
 * - 帧头：FF (1字节，固定)
 * - 数据长度：4位十六进制字符，表示原始数据的字节数 (例如：0010 表示16字节)
 * - 数据内容：原始数据转换为十六进制字符串
 * - 校验和：2位十六进制字符，数据字节和的低8位
 * - 帧尾：FE (1字节，固定)
 *
 * 优势：
 * - 固定的帧边界标记（FF/FE），易于识别完整帧
 * - 校验和验证，确保数据完整性
 * - 高传输效率，避免JSON分片问题
 * - 支持二进制数据传输
 *
 * 使用场景：
 * - RS232串口通信
 * - USB AOA通信
 * - 其他需要可靠数据传输的场景
 *
 * @author TaPro Team
 * @since 2025-01-06
 */
class HexFrameProtocol {

    companion object {
        private const val TAG = "HexFrameProtocol"

        // 帧标记
        private const val FRAME_START = "FF"
        private const val FRAME_END = "FE"

        // 长度字段大小（十六进制字符数）
        private const val LENGTH_FIELD_SIZE = 4

        // 校验和字段大小（十六进制字符数）
        private const val CHECKSUM_FIELD_SIZE = 2

        /**
         * 将原始数据编码为十六进制帧
         *
         * @param data 原始数据字节数组
         * @return 编码后的十六进制帧字节数组
         */
        fun encode(data: ByteArray): ByteArray {
            // 转换为十六进制字符串
            val hexString = bytesToHex(data)

            // 计算校验和
            val checksum = calculateChecksum(data)
            val checksumHex = String.format("%02X", checksum.toInt() and 0xFF)

            // 构建帧：FF + 长度(4位) + 数据(十六进制) + 校验和(2位) + FE
            val frameLength = String.format("%04X", data.size)
            val frameData = "$FRAME_START$frameLength$hexString$checksumHex$FRAME_END"

            LogUtil.d(TAG, "Encoded frame: $frameData (${frameData.length} chars)")
            LogUtil.d(TAG, "  Original size: ${data.size}, Hex size: ${hexString.length}, Frame size: ${frameData.length}")

            return frameData.toByteArray()
        }

        /**
         * 从缓冲区解码十六进制帧
         *
         * @param buffer 数据缓冲区
         * @return 解码结果，包含解码的数据列表和已处理的字符数
         */
        fun decode(buffer: String): DecodeResult {
            val decodedFrames = mutableListOf<ByteArray>()
            var processedChars = 0
            var startIndex = 0

            while (true) {
                // 查找帧开始标记 FF
                val frameStart = buffer.indexOf(FRAME_START, startIndex)
                if (frameStart == -1) break

                // 检查是否有足够的数据读取长度
                if (frameStart + 2 + LENGTH_FIELD_SIZE > buffer.length) break

                try {
                    // 读取数据长度（4位十六进制）
                    val lengthHex = buffer.substring(frameStart + 2, frameStart + 2 + LENGTH_FIELD_SIZE)
                    val dataLength = lengthHex.toInt(16)

                    LogUtil.d(TAG, "Found frame start at $frameStart, data length: $dataLength")

                    // 计算完整帧的长度：FF(2) + 长度(4) + 数据(dataLength*2) + 校验和(2) + FE(2)
                    val totalFrameLength = 2 + LENGTH_FIELD_SIZE + (dataLength * 2) + CHECKSUM_FIELD_SIZE + 2

                    if (frameStart + totalFrameLength > buffer.length) {
                        LogUtil.d(TAG, "Frame incomplete, waiting for more data")
                        break // 帧不完整
                    }

                    // 检查帧尾标记 FE
                    val frameEndPos = frameStart + totalFrameLength - 2
                    if (!buffer.substring(frameEndPos, frameEndPos + 2).equals(FRAME_END, ignoreCase = true)) {
                        LogUtil.w(TAG, "Invalid frame end marker at position $frameEndPos")
                        startIndex = frameStart + 2
                        continue
                    }

                    // 提取十六进制数据和校验和
                    val hexDataStart = frameStart + 2 + LENGTH_FIELD_SIZE
                    val hexDataEnd = frameEndPos - CHECKSUM_FIELD_SIZE
                    val hexData = buffer.substring(hexDataStart, hexDataEnd)
                    val checksumHex = buffer.substring(hexDataEnd, frameEndPos)

                    LogUtil.d(TAG, "Extracted hex data: $hexData, checksum: $checksumHex")

                    // 转换为原始数据
                    val originalData = hexToBytes(hexData)
                    if (originalData.isEmpty()) {
                        LogUtil.w(TAG, "Failed to decode hex data: $hexData")
                        startIndex = frameStart + totalFrameLength
                        continue
                    }

                    // 验证校验和
                    val calculatedChecksum = calculateChecksum(originalData)
                    val expectedChecksum = checksumHex.toInt(16).toByte()

                    if (calculatedChecksum != expectedChecksum) {
                        LogUtil.w(TAG, "Checksum mismatch: calculated=${String.format("%02X", calculatedChecksum.toInt() and 0xFF)}, expected=$checksumHex")
                        startIndex = frameStart + totalFrameLength
                        continue
                    }

                    LogUtil.d(TAG, "Complete hex frame decoded successfully: ${String(originalData)} (${originalData.size} bytes)")

                    // 添加到解码结果
                    decodedFrames.add(originalData)
                    processedChars = frameStart + totalFrameLength
                    startIndex = processedChars

                } catch (e: Exception) {
                    LogUtil.e(TAG, "Failed to decode hex frame: ${e.message}")
                    startIndex = frameStart + 2
                }
            }

            return DecodeResult(decodedFrames, processedChars)
        }

        /**
         * 字节数组转十六进制字符串
         */
        private fun bytesToHex(bytes: ByteArray): String {
            return bytes.joinToString("") { "%02X".format(it) }
        }

        /**
         * 十六进制字符串转字节数组
         */
        private fun hexToBytes(hex: String): ByteArray {
            return try {
                hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            } catch (e: Exception) {
                LogUtil.e(TAG, "Failed to convert hex to bytes: $hex")
                byteArrayOf()
            }
        }

        /**
         * 计算校验和（数据字节和的低8位）
         */
        private fun calculateChecksum(data: ByteArray): Byte {
            return (data.sum() and 0xFF).toByte()
        }
    }

    /**
     * 解码结果
     *
     * @param frames 解码出的完整帧数据列表
     * @param processedChars 已处理的字符数（用于清理缓冲区）
     */
    data class DecodeResult(
        val frames: List<ByteArray>,
        val processedChars: Int
    )
}
