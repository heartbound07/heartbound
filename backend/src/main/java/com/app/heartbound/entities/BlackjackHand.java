package com.app.heartbound.entities;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Represents a hand of cards in Blackjack with proper value calculation.
 */
public class BlackjackHand {
    private final List<Card> cards;

    public BlackjackHand() {
        this.cards = new ArrayList<>();
    }

    /**
     * Add a card to the hand.
     * @param card the card to add
     */
    public void addCard(Card card) {
        cards.add(card);
    }

    /**
     * Get all cards in the hand.
     * @return list of cards
     */
    public List<Card> getCards() {
        return new ArrayList<>(cards);
    }

    /**
     * Get the number of cards in the hand.
     * @return the number of cards
     */
    public int getSize() {
        return cards.size();
    }

    /**
     * Calculate the best possible value for this hand.
     * Uses "soft" aces (11) when possible, "hard" aces (1) when necessary to avoid busting.
     * @return the best hand value
     */
    public int getValue() {
        int value = 0;
        int aces = 0;

        // First, calculate with all aces as 1
        for (Card card : cards) {
            if (card.isAce()) {
                aces++;
                value += 1;
            } else {
                value += card.getBlackjackValue();
            }
        }

        // Try to use aces as 11 (soft aces) without busting
        while (aces > 0 && value + 10 <= 21) {
            value += 10; // Convert one ace from 1 to 11
            aces--;
        }

        return value;
    }

    /**
     * Check if this hand is busted (over 21).
     * @return true if the hand value is over 21
     */
    public boolean isBusted() {
        return getValue() > 21;
    }

    /**
     * Check if this hand is a blackjack (21 with exactly 2 cards).
     * @return true if this is a natural blackjack
     */
    public boolean isBlackjack() {
        return cards.size() == 2 && getValue() == 21;
    }

    /**
     * Check if this hand has a soft ace (an ace counted as 11).
     * @return true if there's a soft ace
     */
    public boolean hasSoftAce() {
        if (cards.stream().noneMatch(Card::isAce)) {
            return false;
        }

        // Calculate the minimum value (all aces as 1)
        int minValue = cards.stream()
                .mapToInt(Card::getHardBlackjackValue)
                .sum();

        // If current value is greater than minimum value, we have a soft ace
        return getValue() > minValue;
    }

    /**
     * Get the Unicode representation of all cards in the hand.
     * @return string with all card unicodes
     */
    public String getCardsUnicode() {
        return cards.stream()
                .map(Card::getUnicode)
                .collect(Collectors.joining(" "));
    }

    /**
     * Get the Unicode representation with the first card hidden (for dealer).
     * @param hideFirst whether to hide the first card
     * @return string with card unicodes (first card shown as back if hidden)
     */
    public String getCardsUnicode(boolean hideFirst) {
        if (!hideFirst || cards.isEmpty()) {
            return getCardsUnicode();
        }

        StringBuilder sb = new StringBuilder();
        sb.append("<:backside:1387834096213819462>"); // Card back for first card

        if (cards.size() > 1) {
            sb.append(" ");
            sb.append(cards.subList(1, cards.size()).stream()
                    .map(Card::getUnicode)
                    .collect(Collectors.joining(" ")));
        }

        return sb.toString();
    }

    /**
     * Get the value of the first card only (for dealer showing).
     * @return the value of the first card
     */
    public int getFirstCardValue() {
        if (cards.isEmpty()) {
            return 0;
        }
        return cards.get(0).getBlackjackValue();
    }

    /**
     * Clear all cards from the hand.
     */
    public void clear() {
        cards.clear();
    }

    @Override
    public String toString() {
        return cards.stream()
                .map(Card::toString)
                .collect(Collectors.joining(", ")) + " (Value: " + getValue() + ")";
    }
} 