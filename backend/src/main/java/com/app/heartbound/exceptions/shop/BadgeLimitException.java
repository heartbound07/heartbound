package com.app.heartbound.exceptions.shop;

public class BadgeLimitException extends RuntimeException {
    public BadgeLimitException() {
        super();
    }
    
    public BadgeLimitException(String message) {
        super(message);
    }
    
    public BadgeLimitException(String message, Throwable cause) {
        super(message, cause);
    }
} 