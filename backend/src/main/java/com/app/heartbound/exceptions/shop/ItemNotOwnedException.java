package com.app.heartbound.exceptions.shop;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.FORBIDDEN)
public class ItemNotOwnedException extends RuntimeException {
    public ItemNotOwnedException(String message) {
        super(message);
    }

    public ItemNotOwnedException(String message, Throwable cause) {
        super(message, cause);
    }
} 