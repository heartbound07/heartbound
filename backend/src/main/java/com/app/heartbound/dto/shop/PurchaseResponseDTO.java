package com.app.heartbound.dto.shop;

import com.app.heartbound.dto.UserProfileDTO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents the response after a successful item purchase.
 * This DTO combines the updated user profile with the details of the purchased item,
 * allowing the frontend to update its state without requiring additional API calls.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PurchaseResponseDTO {
    
    /**
     * The user's profile after the transaction, reflecting updated credits.
     */
    private UserProfileDTO userProfile;
    
    /**
     * The shop item that was purchased, reflecting its new 'owned' status.
     * This is null for failed transactions.
     */
    private ShopDTO purchasedItem;
} 