package com.sunmi.tapro.taplink.sdk.util

import com.sunmi.tapro.taplink.communication.util.LogUtil
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Signature Utility Class
 *
 * Provides HMAC-SHA256 signature functionality
 *
 * @author TaPro Team
 * @since 2025-01-XX
 */
object SignUtil {

    private const val TAG = "SignUtil"
    private const val HMAC_SHA256 = "HmacSHA256"

    /**
     * Generate signature using HMAC-SHA256 algorithm
     *
     * @param data Data to be signed (usually sorted JSON string, excluding appSign field)
     * @param secretKey Secret key
     * @return String Signed hexadecimal string (uppercase)
     */
    fun generateHMACSHA256(data: String, secretKey: String): String {
        return try {
            val mac = Mac.getInstance(HMAC_SHA256)
            val secretKeySpec = SecretKeySpec(secretKey.toByteArray(Charsets.UTF_8), HMAC_SHA256)
            mac.init(secretKeySpec)
            val hash = mac.doFinal(data.toByteArray(Charsets.UTF_8))
            bytesToHex(hash)
        } catch (e: Exception) {
            LogUtil.e(TAG, "Failed to generate HMAC-SHA256 signature: ${e.message}")
            ""
        }
    }

    /**
     * Verify signature
     *
     * @param data Original data
     * @param secretKey Secret key
     * @param signature Signature to be verified
     * @return Boolean Whether the signature is valid
     */
    fun verifySignature(data: String, secretKey: String, signature: String): Boolean {
        val calculatedSignature = generateHMACSHA256(data, secretKey)
        return calculatedSignature.equals(signature, ignoreCase = true)
    }

    /**
     * Convert byte array to hexadecimal string (uppercase)
     *
     * @param bytes Byte array
     * @return String Hexadecimal string
     */
    private fun bytesToHex(bytes: ByteArray): String {
        val hexChars = CharArray(bytes.size * 2)
        for (i in bytes.indices) {
            val v = bytes[i].toInt() and 0xFF
            hexChars[i * 2] = hexArray[v ushr 4]
            hexChars[i * 2 + 1] = hexArray[v and 0x0F]
        }
        return String(hexChars)
    }

    private val hexArray = "0123456789ABCDEF".toCharArray()

    /**
     * Signature exception
     */
    class SignException(message: String, cause: Throwable? = null) : Exception(message, cause)
}













