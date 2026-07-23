package com.sunmi.tapro.taplink.sdk.scanner

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.util.Size
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.sunmi.tapro.taplink.communication.util.LogUtil
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * SDK built-in QR code scanner Activity.
 *
 * Uses CameraX + ZXing for QR decoding. Only accepts lan://host/port format.
 * Handles camera permission requests internally.
 *
 * This is a plain Android Activity (no Compose dependency) to keep SDK size minimal.
 */
class QrScannerActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "QrScannerActivity"
        private const val CAMERA_PERMISSION_REQUEST = 1001
    }

    private var previewView: PreviewView? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private val resultHandled = AtomicBoolean(false)
    private var lastInvalidToastTime = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        QrScanCoordinator.registerActivity(this)

        // Build layout programmatically (no XML needed)
        val root = FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.BLACK)
        }

        // Camera preview
        previewView = PreviewView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
        root.addView(previewView)

        // Top bar with back button
        val topBar = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                dpToPx(56)
            ).apply { gravity = Gravity.TOP }
            setBackgroundColor(Color.argb(128, 0, 0, 0))
        }

        val backButton = ImageButton(this).apply {
            layoutParams = FrameLayout.LayoutParams(dpToPx(48), dpToPx(48)).apply {
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
                marginStart = dpToPx(8)
            }
            setBackgroundColor(Color.TRANSPARENT)
            setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            setColorFilter(Color.WHITE)
            contentDescription = "Close scanner"
            setOnClickListener { handleCancel() }
        }
        topBar.addView(backButton)

        val titleText = TextView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply { gravity = Gravity.CENTER }
            text = "Scan Taplink QR"
            setTextColor(Color.WHITE)
            textSize = 18f
        }
        topBar.addView(titleText)

        root.addView(topBar)

        // Bottom hint
        val bottomHint = LinearLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.BOTTOM
            }
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.argb(128, 0, 0, 0))
            setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(32))
            gravity = Gravity.CENTER_HORIZONTAL
        }

        val hintText = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            text = "Point camera at the QR code on the Taplink terminal"
            setTextColor(Color.WHITE)
            textSize = 14f
            gravity = Gravity.CENTER
        }
        bottomHint.addView(hintText)
        root.addView(bottomHint)

        setContentView(root)

        // Check permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                CAMERA_PERMISSION_REQUEST
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_REQUEST) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startCamera()
            } else {
                QrScanCoordinator.onCameraPermissionDenied()
                finish()
            }
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            try {
                val provider = cameraProviderFuture.get()
                cameraProvider = provider

                val preview = Preview.Builder().build().also { preview ->
                    previewView?.surfaceProvider?.let { surfaceProvider ->
                        preview.setSurfaceProvider(surfaceProvider)
                    }
                }

                val imageAnalysis = ImageAnalysis.Builder()
                    .setTargetResolution(Size(1280, 720))
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { analysis ->
                        analysis.setAnalyzer(Executors.newSingleThreadExecutor()) { imageProxy ->
                            if (resultHandled.get()) {
                                imageProxy.close()
                                return@setAnalyzer
                            }
                            QrCodeAnalyzer.analyze(imageProxy) { qrData ->
                                handleScanResult(qrData)
                            }
                        }
                    }

                provider.unbindAll()

                // Prefer the back camera, then the front camera. On many POS terminals
                // (e.g. Sunmi D3_PRO) the camera reports no lens facing
                // (CameraValidator logs lensFacingInteger: null), so both DEFAULT_*
                // selectors resolve to nothing. In that case fall back to selecting the
                // first available camera regardless of facing. Only report an error when
                // the device truly exposes no camera at all.
                val cameraSelector = when {
                    provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) ->
                        CameraSelector.DEFAULT_BACK_CAMERA
                    provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) ->
                        CameraSelector.DEFAULT_FRONT_CAMERA
                    provider.availableCameraInfos.isNotEmpty() -> {
                        LogUtil.d(TAG, "No lens-facing camera; selecting first available camera")
                        CameraSelector.Builder()
                            .addCameraFilter { infos ->
                                if (infos.isEmpty()) infos else listOf(infos.first())
                            }
                            .build()
                    }
                    else -> {
                        LogUtil.e(TAG, "No usable camera found on this device")
                        QrScanCoordinator.onCameraUnavailable()
                        finish()
                        return@addListener
                    }
                }
                provider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis)

                LogUtil.d(TAG, "Camera started successfully")
            } catch (e: Exception) {
                LogUtil.e(TAG, "Failed to start camera: ${e.message}")
                QrScanCoordinator.onCameraUnavailable()
                finish()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun handleScanResult(qrData: String) {
        if (resultHandled.get()) return

        val accepted = QrScanCoordinator.onScanResult(qrData)
        if (accepted) {
            resultHandled.set(true)
            runOnUiThread { finish() }
        } else {
            // Invalid format — show toast with debounce (3 seconds)
            val now = System.currentTimeMillis()
            if (now - lastInvalidToastTime > 3000) {
                lastInvalidToastTime = now
                runOnUiThread {
                    Toast.makeText(
                        this,
                        "Invalid QR code. Expected Taplink connection code.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun handleCancel() {
        if (resultHandled.compareAndSet(false, true)) {
            QrScanCoordinator.onScanCancelled()
        }
        finish()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        handleCancel()
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            cameraProvider?.unbindAll()
        } catch (e: Exception) {
            LogUtil.w(TAG, "Error unbinding camera: ${e.message}")
        }
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }
}
