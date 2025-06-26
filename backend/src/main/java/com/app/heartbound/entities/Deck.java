package com.app.heartbound.entities;

import com.app.heartbound.enums.CardRank;
import com.app.heartbound.enums.Suit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Represents a deck of playing cards that can be shuffled and dealt from.
 */
public class Deck {
    private final List<Card> cards;

    /**
     * Creates a new standard 52-card deck.
     */
    public Deck() {
        this.cards = new ArrayList<>();
        initializeDeck();
    }

    /**
     * Initialize the deck with all 52 cards.
     */
    private void initializeDeck() {
        for (Suit suit : Suit.values()) {
            for (CardRank rank : CardRank.values()) {
                cards.add(new Card(suit, rank));
            }
        }
    }

    /**
     * Shuffle the deck randomly.
     */
    public void shuffle() {
        Collections.shuffle(cards);
    }

    /**
     * Deal a card from the top of the deck.
     * @return the dealt card
     * @throws IllegalStateException if the deck is empty
     */
    public Card dealCard() {
        if (cards.isEmpty()) {
            throw new IllegalStateException("Cannot deal from an empty deck");
        }
        return cards.remove(cards.size() - 1);
    }

    /**
     * Check if the deck has any remaining cards.
     * @return true if there are cards left in the deck
     */
    public boolean hasCards() {
        return !cards.isEmpty();
    }

    /**
     * Get the number of cards remaining in the deck.
     * @return the number of cards left
     */
    public int getCardsRemaining() {
        return cards.size();
    }

    /**
     * Reset the deck to a full 52-card deck and shuffle.
     */
    public void reset() {
        cards.clear();
        initializeDeck();
        shuffle();
    }
} 