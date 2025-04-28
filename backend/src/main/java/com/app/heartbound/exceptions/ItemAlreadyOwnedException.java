package com.app.heartbound.exceptions;

public class ItemAlreadyOwnedException extends RuntimeException {
    public ItemAlreadyOwnedException(String message) {
        super(message);
    }
} 