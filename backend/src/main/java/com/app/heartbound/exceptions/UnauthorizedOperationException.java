package com.app.heartbound.exceptions;

public class UnauthorizedOperationException extends RuntimeException {
    
    public UnauthorizedOperationException() {
        super();
    }
    
    public UnauthorizedOperationException(String message) {
        super(message);
    }
    
    public UnauthorizedOperationException(String message, Throwable cause) {
        super(message, cause);
    }
}
