package com.app.heartbound.exceptions.shop;

/**
 * Exception thrown when case contents are invalid (e.g., drop rates don't sum to 100%)
 */
public class InvalidCaseContentsException extends RuntimeException {
    public InvalidCaseContentsException(String message) {
        super(message);
    }
    
    public InvalidCaseContentsException(String message, Throwable cause) {
        super(message, cause);
    }
} 