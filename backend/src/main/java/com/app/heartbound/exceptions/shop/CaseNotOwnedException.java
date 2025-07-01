package com.app.heartbound.exceptions.shop;

/**
 * Exception thrown when a user tries to open a case they don't own
 */
public class CaseNotOwnedException extends RuntimeException {
    public CaseNotOwnedException(String message) {
        super(message);
    }
    
    public CaseNotOwnedException(String message, Throwable cause) {
        super(message, cause);
    }
} 