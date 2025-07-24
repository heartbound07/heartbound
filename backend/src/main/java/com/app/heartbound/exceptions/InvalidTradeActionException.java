package com.app.heartbound.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InvalidTradeActionException extends RuntimeException {
    public InvalidTradeActionException(String message) {
        super(message);
    }
} 