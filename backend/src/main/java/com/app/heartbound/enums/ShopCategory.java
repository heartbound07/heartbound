package com.app.heartbound.enums;

/**
 * Represents the categories of items available in the shop.
 */
public enum ShopCategory {
    USER_COLOR,
    LISTING,
    ACCENT,
    BADGE,
    CASE,
    FISHING_ROD;

    public boolean isTradable() {
        return this != CASE && this != FISHING_ROD;
    }

    public boolean isStackable() {
        return this == CASE;
    }
} 