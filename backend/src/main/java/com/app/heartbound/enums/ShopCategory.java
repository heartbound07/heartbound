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
    FISHING_ROD,
    FISHING_ROD_PART;

    public boolean isTradable() {
        return this != CASE;
    }

    public boolean isStackable() {
        return this == CASE || this == FISHING_ROD_PART;
    }

    public boolean isEquippable() {
        return this != CASE && this != FISHING_ROD_PART;
    }
} 