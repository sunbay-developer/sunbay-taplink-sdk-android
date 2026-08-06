package com.sunmi.tapro.taplink.sdk.model.request.transaction.settlement

import com.sunmi.tapro.taplink.sdk.enums.PrintReceipt
import com.sunmi.tapro.taplink.sdk.model.request.transaction.BaseTransactionRequest
import com.sunmi.tapro.taplink.sdk.model.request.transaction.TransactionRequestValidator
import com.sunmi.tapro.taplink.sdk.model.request.transaction.TransactionRequestValidationException
import com.sunmi.tapro.taplink.sdk.model.request.transaction.ValidationResult

/**
 * Batch close transaction request
 *
 * Used to close the current batch and complete the settlement process
 *
 * @param transactionRequestId Transaction request ID (required)
 * @param description Close description (optional, max 128 characters)
 * @param requestTimeout Request timeout (optional, in seconds)
 * @param printReceipt Batch report type (AUTO, BOTH, TOTAL, DETAIL, or NONE; default AUTO).
 *   `MERCHANT` and `CUSTOMER` are meaningless for a batch report and are treated as `AUTO`
 *   (the terminal's batch close configuration is used) instead of being rejected.
 *
 * @author TaPro Team
 * @since 2025-01-XX
 */
data class BatchCloseRequest(
    val transactionRequestId: String,
    val description: String? = null,
    val requestTimeout: Long? = null,
    val printReceipt: PrintReceipt?
) : BaseTransactionRequest() {

    /**
     * Preserves binary compatibility with SDK versions where BatchCloseRequest had three fields.
     */
    constructor(
        transactionRequestId: String,
        description: String? = null,
        requestTimeout: Long? = null
    ) : this(transactionRequestId, description, requestTimeout, PrintReceipt.AUTO)

    /**
     * Preserves the three-field data-class copy API from earlier SDK versions.
     */
    fun copy(
        transactionRequestId: String = this.transactionRequestId,
        description: String? = this.description,
        requestTimeout: Long? = this.requestTimeout
    ): BatchCloseRequest {
        return BatchCloseRequest(
            transactionRequestId,
            description,
            requestTimeout,
            printReceipt
        )
    }

    /**
     * The batch report type actually applied.
     *
     * `MERCHANT` and `CUSTOMER` describe receipt copies rather than a batch report, so they carry
     * no meaning here and fall back to [PrintReceipt.AUTO] (the terminal's batch close
     * configuration).
     */
    val resolvedPrintReceipt: PrintReceipt
        get() = when (printReceipt) {
            PrintReceipt.BOTH,
            PrintReceipt.TOTAL,
            PrintReceipt.DETAIL,
            PrintReceipt.NONE -> printReceipt

            else -> PrintReceipt.AUTO
        }

    override fun validate(): ValidationResult {
        val descriptionValidation = if (description != null) {
            TransactionRequestValidator.validateDescription(description)
        } else {
            ValidationResult.success()
        }

        return TransactionRequestValidator.combineResults(
            TransactionRequestValidator.validateTransactionRequestId(transactionRequestId),
            descriptionValidation
        )
    }

    companion object {
        /**
         * Create BatchCloseRequest builder
         */
        fun builder(): Builder = Builder()
    }

    /**
     * BatchCloseRequest builder
     */
    class Builder {
        private var transactionRequestId: String? = null
        private var description: String? = null
        private var requestTimeout: Long? = null
        private var printReceipt: PrintReceipt? = PrintReceipt.AUTO

        /**
         * Set transaction request ID
         */
        fun setTransactionRequestId(transactionRequestId: String): Builder {
            this.transactionRequestId = transactionRequestId
            return this
        }

        /**
         * Set close description
         */
        fun setDescription(description: String?): Builder {
            this.description = description
            return this
        }

        /**
         * Set request timeout
         */
        fun setRequestTimeout(requestTimeout: Long): Builder {
            this.requestTimeout = requestTimeout
            return this
        }

        /**
         * Set the batch report type.
         */
        fun setPrintReceipt(printReceipt: PrintReceipt): Builder {
            this.printReceipt = printReceipt
            return this
        }

        /**
         * Build BatchCloseRequest instance
         * 
         * @throws TransactionRequestValidationException if validation fails
         */
        fun build(): BatchCloseRequest {
            val request = BatchCloseRequest(
                transactionRequestId = requireNotNull(transactionRequestId) { "transactionRequestId is required" },
                description = description,
                requestTimeout = requestTimeout,
                printReceipt = printReceipt
            )

            val validationResult = request.validate()
            if (!validationResult.isValid) {
                throw TransactionRequestValidationException(validationResult.errors)
            }

            return request
        }
    }
}