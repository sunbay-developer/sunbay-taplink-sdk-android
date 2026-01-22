package com.sunmi.tapro.taplink.communication.protocol

import com.sunmi.tapro.taplink.communication.util.LogUtil

/**
 * Hexadecimal frame transmission protocol
 *
 * Protocol format: FF + Length(4 digits) + Data(hexadecimal) + Checksum(2 digits) + FE
 *
 * Frame structure:
 * - Frame header: FF (1 byte, fixed)
 * - Data length: 4 hexadecimal characters, represents byte count of original data (e.g., 0010 means 16 bytes)
 * - Data content: Original data converted to hexadecimal string
 * - Checksum: 2 hexadecimal characters, lower 8 bits of data byte sum
 * - Frame tail: FE (1 byte, fixed)
 *
 * Advantages:
 * - Fixed frame boundary markers (FF/FE), easy to identify complete frames
 * - Checksum verification, ensures data integrity
 * - High transmission efficiency, avoids JSON fragmentation issues
 * - Supports binary data transmission
 *
 * Use cases:
 * - RS232 serial port communication
 * - USB AOA communication
 * - Other scenarios requiring reliable data transmission
 *
 * @author TaPro Team
 * @since 2025-01-06
 */
class HexFrameProtocol {

    companion object {
        private const val TAG = "HexFrameProtocol"

        // Frame markers
        private const val FRAME_START = "FF"
        private const val FRAME_END = "FE"

        // Length field size (hexadecimal character count)
        private const val LENGTH_FIELD_SIZE = 4

        // Checksum field size (hexadecimal character count)
        private const val CHECKSUM_FIELD_SIZE = 2

        /**
         * Encode original data to hexadecimal frame
         *
         * @param data Original data byte array
         * @return Encoded hexadecimal frame byte array
         */
        fun encode(data: ByteArray): ByteArray {
            // Convert to hexadecimal string
            val hexString = bytesToHex(data)

            // Calculate checksum
            val checksum = calculateChecksum(data)
            val checksumHex = String.format("%02X", checksum.toInt() and 0xFF)

            // Build frame: FF + Length(4 digits) + Data(hexadecimal) + Checksum(2 digits) + FE
            val frameLength = String.format("%04X", data.size)
            val frameData = "$FRAME_START$frameLength$hexString$checksumHex$FRAME_END"

            LogUtil.d(TAG, "Encoded frame: $frameData (${frameData.length} chars)")
            LogUtil.d(TAG, "  Original size: ${data.size}, Hex size: ${hexString.length}, Frame size: ${frameData.length}")

            return frameData.toByteArray()
        }

        /**
         * Decode hexadecimal frames from buffer
         *
         * @param buffer Data buffer
         * @return Decode result, contains decoded data list and processed character count
         */
        fun decode(buffer: String): DecodeResult {
            val decodedFrames = mutableListOf<ByteArray>()
            var processedChars = 0
            var startIndex = 0

            while (true) {
                // Find frame start marker FF
                val frameStart = buffer.indexOf(FRAME_START, startIndex)
                if (frameStart == -1) break

                // Check if there's enough data to read length
                if (frameStart + 2 + LENGTH_FIELD_SIZE > buffer.length) break

                try {
                    // Read data length (4 hexadecimal digits)
                    val lengthHex = buffer.substring(frameStart + 2, frameStart + 2 + LENGTH_FIELD_SIZE)
                    val dataLength = lengthHex.toInt(16)

                    LogUtil.d(TAG, "Found frame start at $frameStart, data length: $dataLength")

                    // Calculate complete frame length: FF(2) + Length(4) + Data(dataLength*2) + Checksum(2) + FE(2)
                    val totalFrameLength = 2 + LENGTH_FIELD_SIZE + (dataLength * 2) + CHECKSUM_FIELD_SIZE + 2

                    if (frameStart + totalFrameLength > buffer.length) {
                        LogUtil.d(TAG, "Frame incomplete, waiting for more data")
                        break // Frame incomplete
                    }

                    // Check frame tail marker FE
                    val frameEndPos = frameStart + totalFrameLength - 2
                    if (!buffer.substring(frameEndPos, frameEndPos + 2).equals(FRAME_END, ignoreCase = true)) {
                        LogUtil.w(TAG, "Invalid frame end marker at position $frameEndPos")
                        startIndex = frameStart + 2
                        continue
                    }

                    // Extract hexadecimal data and checksum
                    val hexDataStart = frameStart + 2 + LENGTH_FIELD_SIZE
                    val hexDataEnd = frameEndPos - CHECKSUM_FIELD_SIZE
                    val hexData = buffer.substring(hexDataStart, hexDataEnd)
                    val checksumHex = buffer.substring(hexDataEnd, frameEndPos)

                    LogUtil.d(TAG, "Extracted hex data: $hexData, checksum: $checksumHex")

                    // Convert to original data
                    val originalData = hexToBytes(hexData)
                    if (originalData.isEmpty()) {
                        LogUtil.w(TAG, "Failed to decode hex data: $hexData")
                        startIndex = frameStart + totalFrameLength
                        continue
                    }

                    // Verify checksum
                    val calculatedChecksum = calculateChecksum(originalData)
                    val expectedChecksum = checksumHex.toInt(16).toByte()

                    if (calculatedChecksum != expectedChecksum) {
                        LogUtil.w(TAG, "Checksum mismatch: calculated=${String.format("%02X", calculatedChecksum.toInt() and 0xFF)}, expected=$checksumHex")
                        startIndex = frameStart + totalFrameLength
                        continue
                    }

                    LogUtil.d(TAG, "Complete hex frame decoded successfully: ${String(originalData)} (${originalData.size} bytes)")

                    // Add to decode result
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
         * Convert byte array to hexadecimal string
         */
        private fun bytesToHex(bytes: ByteArray): String {
            return bytes.joinToString("") { "%02X".format(it) }
        }

        /**
         * Convert hexadecimal string to byte array
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
         * Calculate checksum (lower 8 bits of data byte sum)
         */
        private fun calculateChecksum(data: ByteArray): Byte {
            return (data.sum() and 0xFF).toByte()
        }
    }

    /**
     * Decode result
     *
     * @param frames List of decoded complete frame data
     * @param processedChars Processed character count (for buffer cleanup)
     */
    data class DecodeResult(
        val frames: List<ByteArray>,
        val processedChars: Int
    )
}
