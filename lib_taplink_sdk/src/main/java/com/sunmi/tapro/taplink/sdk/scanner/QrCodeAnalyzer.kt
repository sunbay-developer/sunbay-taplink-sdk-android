package com.sunmi.tapro.taplink.sdk.scanner

import androidx.camera.core.ImageProxy
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.sunmi.tapro.taplink.communication.util.LogUtil

/**
 * QR code analyzer using ZXing MultiFormatReader.
 *
 * Designed for use with CameraX ImageAnalysis. Decodes only QR codes
 * from the camera preview's Y plane (luminance data).
 */
object QrCodeAnalyzer {

    private const val TAG = "QrCodeAnalyzer"

    private val reader = MultiFormatReader().apply {
        val hints = mapOf<DecodeHintType, Any>(
            DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
            DecodeHintType.TRY_HARDER to true
        )
        setHints(hints)
    }

    /**
     * Analyze an image proxy for QR codes.
     *
     * @param imageProxy CameraX image proxy (YUV_420_888 expected)
     * @param onResult Called on the analyzer thread with the decoded QR text
     */
    fun analyze(imageProxy: ImageProxy, onResult: (String) -> Unit) {
        try {
            val buffer = imageProxy.planes[0].buffer
            val data = ByteArray(buffer.remaining())
            buffer.get(data)

            val width = imageProxy.width
            val height = imageProxy.height

            val source = PlanarYUVLuminanceSource(
                data, width, height,
                0, 0, width, height,
                false
            )
            val bitmap = BinaryBitmap(HybridBinarizer(source))

            try {
                val result = reader.decodeWithState(bitmap)
                if (result.text.isNotEmpty()) {
                    onResult(result.text)
                }
            } catch (e: com.google.zxing.NotFoundException) {
                // No QR code found in this frame — normal, ignore
            } catch (e: Exception) {
                // Other decode errors — log but don't crash
                LogUtil.w(TAG, "Decode error: ${e.message}")
            } finally {
                reader.reset()
            }
        } catch (e: Exception) {
            LogUtil.e(TAG, "Analyze error: ${e.message}")
        } finally {
            imageProxy.close()
        }
    }
}
