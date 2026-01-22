package com.sunmi.tapro.taplink.demo.util

import android.app.AlertDialog
import android.content.Context
import android.text.InputType
import android.util.Log
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity

/**
 * Utility class for creating common dialog patterns
 * 
 * Eliminates code duplication by providing standardized dialog creation methods
 */
object DialogUtils {

    private const val TAG = "DialogUtils"

    /**
     * Safely show a dialog, checking if the activity is still valid
     * 
     * @param activity The activity context
     * @param dialog The dialog to show
     * @return true if dialog was shown successfully, false otherwise
     */
    fun safeShowDialog(activity: AppCompatActivity, dialog: AlertDialog): Boolean {
        return try {
            if (activity.isFinishing || activity.isDestroyed) {
                Log.w(TAG, "Activity is finishing or destroyed, cannot show dialog")
                false
            } else {
                dialog.show()
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show dialog", e)
            false
        }
    }

    /**
     * Create and safely show a dialog
     * 
     * @param activity The activity context
     * @param dialogBuilder Function that creates the dialog
     * @param onFailure Optional callback when dialog cannot be shown
     * @return true if dialog was shown successfully, false otherwise
     */
    fun createAndShowDialog(
        activity: AppCompatActivity,
        dialogBuilder: () -> AlertDialog,
        onFailure: (() -> Unit)? = null
    ): Boolean {
        return try {
            if (activity.isFinishing || activity.isDestroyed) {
                Log.w(TAG, "Activity is finishing or destroyed, cannot show dialog")
                onFailure?.invoke()
                false
            } else {
                val dialog = dialogBuilder()
                dialog.show()
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create and show dialog", e)
            onFailure?.invoke()
            false
        }
    }

    /**
     * Create a simple information dialog
     */
    fun createInfoDialog(
        context: Context,
        title: String,
        message: String,
        positiveButtonText: String = "OK",
        onPositiveClick: (() -> Unit)? = null,
        onDismiss: (() -> Unit)? = null
    ): AlertDialog {
        return AlertDialog.Builder(context)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(positiveButtonText) { dialog, _ ->
                onPositiveClick?.invoke()
                dialog.dismiss()
            }
            .setOnDismissListener {
                onDismiss?.invoke()
            }
            .setCancelable(true)
            .create()
    }

    /**
     * Create a confirmation dialog with Yes/No buttons
     */
    fun createConfirmationDialog(
        context: Context,
        title: String,
        message: String,
        positiveButtonText: String = "Yes",
        negativeButtonText: String = "No",
        onPositiveClick: () -> Unit,
        onNegativeClick: (() -> Unit)? = null
    ): AlertDialog {
        return AlertDialog.Builder(context)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(positiveButtonText) { dialog, _ ->
                onPositiveClick.invoke()
                dialog.dismiss()
            }
            .setNegativeButton(negativeButtonText) { dialog, _ ->
                onNegativeClick?.invoke()
                dialog.dismiss()
            }
            .setCancelable(true)
            .create()
    }

    /**
     * Create an error dialog with retry option
     */
    fun createErrorDialog(
        context: Context,
        title: String,
        message: String,
        errorCode: String? = null,
        onRetryClick: (() -> Unit)? = null,
        onCancelClick: (() -> Unit)? = null
    ): AlertDialog {
        val fullMessage = if (errorCode != null) {
            "$message\n\nError Code: $errorCode"
        } else {
            message
        }

        val builder = AlertDialog.Builder(context)
            .setTitle(title)
            .setMessage(fullMessage)

        if (onRetryClick != null) {
            builder.setPositiveButton("Retry") { dialog, _ ->
                onRetryClick.invoke()
                dialog.dismiss()
            }
        }

        builder.setNegativeButton("Cancel") { dialog, _ ->
            onCancelClick?.invoke()
            dialog.dismiss()
        }

        return builder.setCancelable(true).create()
    }

    /**
     * Create an input dialog for numeric values
     */
    fun createNumericInputDialog(
        context: Context,
        title: String,
        message: String,
        hint: String? = null,
        allowDecimals: Boolean = true,
        onInputConfirmed: (String) -> Unit,
        onCancelled: (() -> Unit)? = null
    ): AlertDialog {
        val input = EditText(context)
        input.inputType = if (allowDecimals) {
            InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        } else {
            InputType.TYPE_CLASS_NUMBER
        }
        hint?.let { input.hint = it }

        return AlertDialog.Builder(context)
            .setTitle(title)
            .setMessage(message)
            .setView(input)
            .setPositiveButton("OK") { dialog, _ ->
                val inputText = input.text.toString().trim()
                if (inputText.isNotEmpty()) {
                    onInputConfirmed.invoke(inputText)
                }
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                onCancelled?.invoke()
                dialog.dismiss()
            }
            .setCancelable(true)
            .create()
    }

    /**
     * Create a text input dialog
     */
    fun createTextInputDialog(
        context: Context,
        title: String,
        message: String,
        hint: String? = null,
        onInputConfirmed: (String) -> Unit,
        onCancelled: (() -> Unit)? = null
    ): AlertDialog {
        val input = EditText(context)
        input.inputType = InputType.TYPE_CLASS_TEXT
        hint?.let { input.hint = it }

        return AlertDialog.Builder(context)
            .setTitle(title)
            .setMessage(message)
            .setView(input)
            .setPositiveButton("OK") { dialog, _ ->
                val inputText = input.text.toString().trim()
                if (inputText.isNotEmpty()) {
                    onInputConfirmed.invoke(inputText)
                }
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                onCancelled?.invoke()
                dialog.dismiss()
            }
            .setCancelable(true)
            .create()
    }
}