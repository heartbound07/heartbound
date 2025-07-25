package com.app.heartbound.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class ItemEquippedException extends RuntimeException {
    public ItemEquippedException(String message) {
        super(message);
    }
} 