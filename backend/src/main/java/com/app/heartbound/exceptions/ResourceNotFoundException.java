package com.app.heartbound.exceptions;

public class ResourceNotFoundException extends RuntimeException {
    
    public ResourceNotFoundException() {
        super();
    }
    
    public ResourceNotFoundException(String message) {
        super(message);
    }
    
    public ResourceNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
