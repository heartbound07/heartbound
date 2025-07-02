package com.app.heartbound.exceptions.shop;

/**
 * Exception thrown when an item cannot be deleted due to business rules or constraints
 */
public class ItemDeletionException extends RuntimeException {
    public ItemDeletionException(String message) {
        super(message);
    }
    
    public ItemDeletionException(String message, Throwable cause) {
        super(message, cause);
    }
} 