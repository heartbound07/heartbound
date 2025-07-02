package com.app.heartbound.exceptions.shop;

import java.util.List;
import java.util.UUID;

/**
 * Exception thrown when attempting to delete an item that is referenced in cases
 */
public class ItemReferencedInCasesException extends ItemDeletionException {
    private final List<UUID> referencingCaseIds;
    
    public ItemReferencedInCasesException(String message, List<UUID> referencingCaseIds) {
        super(message);
        this.referencingCaseIds = referencingCaseIds;
    }
    
    public ItemReferencedInCasesException(String message, List<UUID> referencingCaseIds, Throwable cause) {
        super(message, cause);
        this.referencingCaseIds = referencingCaseIds;
    }
    
    public List<UUID> getReferencingCaseIds() {
        return referencingCaseIds;
    }
} 