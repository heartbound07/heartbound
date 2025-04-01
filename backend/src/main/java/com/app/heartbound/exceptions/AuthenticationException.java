package com.app.heartbound.exceptions;

public class AuthenticationException extends RuntimeException {
    
    public AuthenticationException() {
        super();
    }
    
    public AuthenticationException(String message) {
        super(message);
    }
    
    public AuthenticationException(String message, Throwable cause) {
        super(message, cause);
    }
} 