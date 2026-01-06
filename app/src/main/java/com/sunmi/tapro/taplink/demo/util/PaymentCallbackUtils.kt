package com.sunmi.tapro.taplink.demo.util

import android.app.ProgressDialog
import com.sunmi.tapro.taplink.demo.service.PaymentCallback
import com.sunmi.tapro.taplink.demo.service.PaymentResult

/**
 * Utility class for creating common payment callback patterns
 * 
 * Eliminates code duplication by providing standardized callback creation methods
 */
object PaymentCallbackUtils {

    /**
     * Create a payment callback that handles progress dialog dismissal
     */
    fun createProgressDialogCallback(
        progressDialog: ProgressDialog,
        onSuccess: (PaymentResult) -> Unit,
        onFailure: (String, String) -> Unit
    ): PaymentCallback {
        return object : PaymentCallback {
            override fun onSuccess(result: PaymentResult) {
                progressDialog.dismiss()
                onSuccess(result)
            }

            override fun onFailure(code: String, message: String) {
                progressDialog.dismiss()
                onFailure(code, message)
            }

            override fun onProgress(status: String, message: String) {
                // Default implementation - can be overridden if needed
            }
        }
    }

    /**
     * Create a simple payment callback with UI thread handling
     */
    fun createUIThreadCallback(
        onSuccess: (PaymentResult) -> Unit,
        onFailure: (String, String) -> Unit,
        onProgress: ((String, String) -> Unit)? = null
    ): PaymentCallback {
        return object : PaymentCallback {
            override fun onSuccess(result: PaymentResult) {
                onSuccess(result)
            }

            override fun onFailure(code: String, message: String) {
                onFailure(code, message)
            }

            override fun onProgress(status: String, message: String) {
                onProgress?.invoke(status, message)
            }
        }
    }

    /**
     * Create a payment callback with automatic progress dialog management and UI thread handling
     */
    fun createManagedCallback(
        progressDialog: ProgressDialog,
        onSuccess: (PaymentResult) -> Unit,
        onFailure: (String, String) -> Unit,
        onProgress: ((String, String) -> Unit)? = null
    ): PaymentCallback {
        return object : PaymentCallback {
            override fun onSuccess(result: PaymentResult) {
                try {
                    progressDialog.dismiss()
                } catch (e: Exception) {
                    // Ignore if dialog is already dismissed or activity is finishing
                }
                onSuccess(result)
            }

            override fun onFailure(code: String, message: String) {
                try {
                    progressDialog.dismiss()
                } catch (e: Exception) {
                    // Ignore if dialog is already dismissed or activity is finishing
                }
                onFailure(code, message)
            }

            override fun onProgress(status: String, message: String) {
                onProgress?.invoke(status, message)
            }
        }
    }
}