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
        lateinit var tvTransactionType: TextView
        lateinit var tvAmount: TextView
        lateinit var tvOrderId: TextView
        lateinit var tvTransactionTime: TextView
        lateinit var tvStatus: TextView
        lateinit var tvTransactionId: TextView
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
            holder.tvTransactionType = view.findViewById(R.id.tv_transaction_type)
            holder.tvAmount = view.findViewById(R.id.tv_amount)
            holder.tvOrderId = view.findViewById(R.id.tv_order_id)
            holder.tvTransactionTime = view.findViewById(R.id.tv_transaction_time)
            holder.tvStatus = view.findViewById(R.id.tv_status)
            holder.tvTransactionId = view.findViewById(R.id.tv_transaction_id)
            
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
        holder.tvTransactionType.text = transaction.getDisplayName()
        
        // Set amount - for batch close, display batchCloseInfo totalAmount; for others, display total amount (transAmount) if available, otherwise display base amount
        val displayAmount = if (transaction.type == TransactionType.BATCH_CLOSE && transaction.batchCloseInfo != null) {
            transaction.batchCloseInfo.totalAmount
        } else {
            transaction.totalAmount ?: transaction.amount
        }
        holder.tvAmount.text = String.format("$%.2f", displayAmount)
        
        // Set order ID
        holder.tvOrderId.text = "Order: ${transaction.referenceOrderId}"
        
        // Set transaction time
        holder.tvTransactionTime.text = dateFormat.format(Date(transaction.timestamp))
        
        // Set transaction status
        holder.tvStatus.text = transaction.getStatusDisplayName()
        holder.tvStatus.setTextColor(getStatusColor(transaction.status))
        
        // Set transaction ID
        val transactionIdText = if (transaction.transactionId != null) {
            "Transaction ID: ${transaction.transactionId}"
        } else {
            "Transaction ID: ${transaction.transactionRequestId}"
        }
        holder.tvTransactionId.text = transactionIdText
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
     * Clear data
     */
    fun clearData() {
        this.transactions = emptyList()
        notifyDataSetChanged()
    }
}