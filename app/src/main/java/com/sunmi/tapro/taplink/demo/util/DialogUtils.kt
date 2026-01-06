package com.sunmi.tapro.taplink.demo.util

import android.app.AlertDialog
import android.content.Context
import android.text.InputType
import android.widget.EditText

/**
 * Utility class for creating common dialog patterns
 * 
 * Eliminates code duplication by providing standardized dialog creation methods
 */
object DialogUtils {

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