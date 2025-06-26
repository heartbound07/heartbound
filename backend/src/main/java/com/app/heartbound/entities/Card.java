package com.app.heartbound.entities;

import com.app.heartbound.enums.CardRank;
import com.app.heartbound.enums.Suit;

/**
 * Represents a playing card with a suit and rank.
 */
public class Card {
    private final Suit suit;
    private final CardRank rank;

    public Card(Suit suit, CardRank rank) {
        this.suit = suit;
        this.rank = rank;
    }

    public Suit getSuit() {
        return suit;
    }

    public CardRank getRank() {
        return rank;
    }

    /**
     * Get the blackjack value of this card.
     * @return the blackjack value (Ace defaults to 11)
     */
    public int getBlackjackValue() {
        return rank.getBlackjackValue();
    }

    /**
     * Get the hard blackjack value of this card (Ace = 1).
     * @return the hard blackjack value
     */
    public int getHardBlackjackValue() {
        return rank.getHardBlackjackValue();
    }

    /**
     * Get the Unicode representation of this card.
     * @return Unicode string for the card
     */
    public String getUnicode() {
        return suit.getCardUnicode(rank.getValue());
    }

    /**
     * Check if this card is an Ace.
     * @return true if this card is an Ace
     */
    public boolean isAce() {
        return rank == CardRank.ACE;
    }

    @Override
    public String toString() {
        return rank.getShortName() + suit.getSymbol();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Card card = (Card) obj;
        return suit == card.suit && rank == card.rank;
    }

    @Override
    public int hashCode() {
        return suit.hashCode() * 31 + rank.hashCode();
    }
} 