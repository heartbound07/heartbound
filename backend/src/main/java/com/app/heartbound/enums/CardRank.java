package com.app.heartbound.enums;

/**
 * Represents the thirteen ranks in a standard deck of playing cards.
 */
public enum CardRank {
    ACE(1, "A"),
    TWO(2, "2"),
    THREE(3, "3"),
    FOUR(4, "4"),
    FIVE(5, "5"),
    SIX(6, "6"),
    SEVEN(7, "7"),
    EIGHT(8, "8"),
    NINE(9, "9"),
    TEN(10, "10"),
    JACK(11, "J"),
    QUEEN(12, "Q"),
    KING(13, "K");

    private final int value;
    private final String shortName;

    CardRank(int value, String shortName) {
        this.value = value;
        this.shortName = shortName;
    }

    public int getValue() {
        return value;
    }

    public String getShortName() {
        return shortName;
    }

    /**
     * Get the blackjack value for this rank.
     * Face cards (J, Q, K) are worth 10.
     * Aces are worth 11 by default (soft value), but can be 1 (hard value).
     * 
     * @return the blackjack value
     */
    public int getBlackjackValue() {
        if (this == ACE) {
            return 11; // Default to soft ace
        } else if (this == JACK || this == QUEEN || this == KING) {
            return 10;
        } else {
            return value;
        }
    }

    /**
     * Get the hard blackjack value for this rank (Ace = 1).
     * 
     * @return the hard blackjack value
     */
    public int getHardBlackjackValue() {
        if (this == ACE) {
            return 1;
        } else if (this == JACK || this == QUEEN || this == KING) {
            return 10;
        } else {
            return value;
        }
    }
} 