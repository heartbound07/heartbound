package com.app.heartbound.exceptions.shop;

/**
 * Exception thrown when a case is not found
 */
public class CaseNotFoundException extends RuntimeException {
    public CaseNotFoundException(String message) {
        super(message);
    }
    
    public CaseNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
} 