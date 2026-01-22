package com.sunmi.tapro.taplink.sdk.util

import com.sunmi.tapro.taplink.communication.util.LogUtil
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * 签名工具类
 *
 * 提供HMAC-SHA256签名功能
 *
 * @author TaPro Team
 * @since 2025-01-XX
 */
object SignUtil {

    private const val TAG = "SignUtil"
    private const val HMAC_SHA256 = "HmacSHA256"

    /**
     * 使用HMAC-SHA256算法生成签名
     *
     * @param data 待签名的数据（通常是排序后的JSON字符串，排除appSign字段）
     * @param secretKey 密钥
     * @return String 签名的十六进制字符串（大写）
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
     * 验证签名
     *
     * @param data 原始数据
     * @param secretKey 密钥
     * @param signature 待验证的签名
     * @return Boolean 签名是否有效
     */
    fun verifySignature(data: String, secretKey: String, signature: String): Boolean {
        val calculatedSignature = generateHMACSHA256(data, secretKey)
        return calculatedSignature.equals(signature, ignoreCase = true)
    }

    /**
     * 将字节数组转换为十六进制字符串（大写）
     *
     * @param bytes 字节数组
     * @return String 十六进制字符串
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
     * 签名异常
     */
    class SignException(message: String, cause: Throwable? = null) : Exception(message, cause)
}













