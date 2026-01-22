package com.sunmi.tapro.taplink.demo.model

/**
 * Transaction Status Enum
 * 
 * Defines various transaction statuses
 */
enum class TransactionStatus {
    /**
     * Pending - Transaction created but not yet sent
     */
    PENDING,
    
    /**
     * Processing - Transaction in progress
     */
    PROCESSING,
    
    /**
     * Success - Transaction completed successfully
     */
    SUCCESS,
    
    /**
     * Failed - Transaction failed
     */
    FAILED,
    
    /**
     * Cancelled - Transaction cancelled
     */
    CANCELLED
}
