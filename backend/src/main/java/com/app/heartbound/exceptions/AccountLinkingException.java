package com.app.heartbound.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Exception thrown when there's an issue with linking external accounts
 * (e.g., Riot, Discord) to a user account.
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class AccountLinkingException extends RuntimeException {
    
    public AccountLinkingException(String message) {
        super(message);
    }
    
    public AccountLinkingException(String message, Throwable cause) {
        super(message, cause);
    }
}
