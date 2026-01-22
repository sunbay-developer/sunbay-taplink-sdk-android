package com.sunmi.tapro.taplink.demo.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.TextView
import com.sunmi.tapro.taplink.demo.R
import com.sunmi.tapro.taplink.demo.model.Transaction
import com.sunmi.tapro.taplink.demo.model.TransactionType
import com.sunmi.tapro.taplink.demo.model.TransactionStatus
import java.text.SimpleDateFormat
import java.util.*

/**
 * Transaction List Adapter
 * 
 * Implements transaction list display using BaseAdapter
 * Uses ViewHolder pattern for performance optimization
 */
class TransactionAdapter(
    private val context: Context,
    private var transactions: List<Transaction>
) : BaseAdapter() {

    private val inflater: LayoutInflater = LayoutInflater.from(context)
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    /**
     * ViewHolder pattern, caches view references
     */
    private class ViewHolder {
        lateinit var transactionTypeText: TextView
        lateinit var amountText: TextView
        lateinit var orderIdText: TextView
        lateinit var transactionTimeText: TextView
        lateinit var statusText: TextView
        lateinit var transactionIdText: TextView
    }

    override fun getCount(): Int = transactions.size

    override fun getItem(position: Int): Transaction = transactions[position]

    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val view: View
        val holder: ViewHolder

        if (convertView == null) {
            // Create new view
            view = inflater.inflate(R.layout.item_transaction, parent, false)
            holder = ViewHolder()
            
            // Bind view references
            holder.transactionTypeText = view.findViewById(R.id.tv_transaction_type)
            holder.amountText = view.findViewById(R.id.tv_amount)
            holder.orderIdText = view.findViewById(R.id.tv_order_id)
            holder.transactionTimeText = view.findViewById(R.id.tv_transaction_time)
            holder.statusText = view.findViewById(R.id.tv_status)
            holder.transactionIdText = view.findViewById(R.id.tv_transaction_id)
            
            view.tag = holder
        } else {
            // Reuse view
            view = convertView
            holder = view.tag as ViewHolder
        }

        // Bind data
        val transaction = getItem(position)
        bindData(holder, transaction)

        // Make sure the root view doesn't intercept clicks
        view.isClickable = false
        view.isFocusable = false

        return view
    }

    /**
     * Bind transaction data to view
     */
    private fun bindData(holder: ViewHolder, transaction: Transaction) {
        // Set transaction type text
        holder.transactionTypeText.text = transaction.getDisplayName()
        
        // Set amount - for batch close, display batchCloseInfo totalAmount; for others, display total amount (transAmount) if available, otherwise display base amount
        val displayAmount = if (transaction.type == TransactionType.BATCH_CLOSE && transaction.batchCloseInfo != null) {
            transaction.batchCloseInfo.totalAmount
        } else {
            transaction.totalAmount ?: transaction.amount
        }
        holder.amountText.text = String.format("$%,.2f", displayAmount)
        
        // Set order ID - truncate long order IDs for better display
        val orderId = transaction.referenceOrderId ?: "N/A"
        val displayOrderId = if (orderId != "N/A" && orderId.length > 20) {
            "Order: ${orderId.take(8)}...${orderId.takeLast(6)}"
        } else {
            "Order: $orderId"
        }
        holder.orderIdText.text = displayOrderId
        
        // Set transaction time
        holder.transactionTimeText.text = dateFormat.format(Date(transaction.timestamp))
        
        // Set transaction status
        holder.statusText.text = transaction.getStatusDisplayName()
        holder.statusText.setTextColor(getStatusColor(transaction.status))
        
        // Set transaction ID - truncate long IDs for better display
        val transactionIdText = if (transaction.transactionId != null) {
            val id = transaction.transactionId
            if (id.length > 20) {
                "ID: ${id.take(8)}...${id.takeLast(8)}"
            } else {
                "ID: $id"
            }
        } else {
            val requestId = transaction.transactionRequestId
            if (requestId.length > 20) {
                "Request ID: ${requestId.take(8)}...${requestId.takeLast(8)}"
            } else {
                "Request ID: $requestId"
            }
        }
        holder.transactionIdText.text = transactionIdText
    }

    /**
     * Get color for transaction status
     */
    private fun getStatusColor(status: TransactionStatus): Int {
        return when (status) {
            TransactionStatus.SUCCESS -> 0xFF4CAF50.toInt() // Green
            TransactionStatus.FAILED -> 0xFFF44336.toInt()   // Red
            TransactionStatus.PENDING -> 0xFFFF9800.toInt()  // Orange
            TransactionStatus.PROCESSING -> 0xFF2196F3.toInt() // Blue
            TransactionStatus.CANCELLED -> 0xFF9E9E9E.toInt() // Gray
        }
    }

    /**
     * Update data source
     */
    fun updateData(newTransactions: List<Transaction>) {
        this.transactions = newTransactions
        notifyDataSetChanged()
    }

    /**
     * Update transactions - alias for updateData for compatibility
     */
    fun updateTransactions(newTransactions: List<Transaction>) {
        updateData(newTransactions)
    }

    /**
     * Clear data
     */
    fun clearData() {
        this.transactions = emptyList()
        notifyDataSetChanged()
    }
}