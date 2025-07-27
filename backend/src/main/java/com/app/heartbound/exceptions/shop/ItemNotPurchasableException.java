package com.app.heartbound.exceptions.shop;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.FORBIDDEN)
public class ItemNotPurchasableException extends RuntimeException {
    public ItemNotPurchasableException(String message) {
        super(message);
    }
} 