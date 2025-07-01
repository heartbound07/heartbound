package com.app.heartbound.exceptions.shop;

/**
 * Exception thrown when a case has no contents to roll
 */
public class EmptyCaseException extends RuntimeException {
    public EmptyCaseException(String message) {
        super(message);
    }
    
    public EmptyCaseException(String message, Throwable cause) {
        super(message, cause);
    }
} 