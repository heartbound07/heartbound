package com.app.heartbound.entities;

/**
 * Represents a complete Blackjack game state for a single player.
 */
public class BlackjackGame {
    private final String userId;
    private final int betAmount;
    private final Deck deck;
    private final BlackjackHand playerHand;
    private final BlackjackHand dealerHand;
    private boolean gameEnded;
    private boolean dealerTurn;

    public BlackjackGame(String userId, int betAmount) {
        this.userId = userId;
        this.betAmount = betAmount;
        this.deck = new Deck();
        this.playerHand = new BlackjackHand();
        this.dealerHand = new BlackjackHand();
        this.gameEnded = false;
        this.dealerTurn = false;
        
        // Initialize the game
        deck.shuffle();
        dealInitialCards();
    }

    /**
     * Deal the initial two cards to both player and dealer.
     */
    private void dealInitialCards() {
        // Deal two cards to player
        playerHand.addCard(deck.dealCard());
        playerHand.addCard(deck.dealCard());
        
        // Deal two cards to dealer
        dealerHand.addCard(deck.dealCard());
        dealerHand.addCard(deck.dealCard());
    }

    /**
     * Player hits (takes another card).
     * @return the dealt card
     */
    public Card playerHit() {
        if (gameEnded || dealerTurn) {
            throw new IllegalStateException("Cannot hit when game is ended or it's dealer's turn");
        }
        
        Card card = deck.dealCard();
        playerHand.addCard(card);
        
        // Check if player busted
        if (playerHand.isBusted()) {
            gameEnded = true;
        }
        
        return card;
    }

    /**
     * Player stands, dealer plays automatically.
     */
    public void playerStand() {
        if (gameEnded) {
            throw new IllegalStateException("Cannot stand when game is already ended");
        }
        
        dealerTurn = true;
        
        // Dealer must hit on 16 and below, stand on 17 and above
        while (dealerHand.getValue() < 17) {
            dealerHand.addCard(deck.dealCard());
        }
        
        gameEnded = true;
    }

    /**
     * Determine the game result.
     * @return GameResult enum indicating the outcome
     */
    public GameResult getResult() {
        if (!gameEnded) {
            return GameResult.IN_PROGRESS;
        }
        
        int playerValue = playerHand.getValue();
        int dealerValue = dealerHand.getValue();
        boolean playerBlackjack = playerHand.isBlackjack();
        boolean dealerBlackjack = dealerHand.isBlackjack();
        
        // Check for busts first
        if (playerHand.isBusted()) {
            return GameResult.DEALER_WIN; // Player busted
        }
        
        if (dealerHand.isBusted()) {
            return GameResult.PLAYER_WIN; // Dealer busted
        }
        
        // ENHANCED FIX: Handle all 21-21 scenarios with comprehensive logic
        if (playerValue == 21 && dealerValue == 21) {
            // Both have natural blackjack (21 with exactly 2 cards each) - PUSH
            if (playerBlackjack && dealerBlackjack) {
                return GameResult.PUSH;
            }
            
            // Player has natural blackjack, dealer has 21 but not natural - Player wins
            if (playerBlackjack && !dealerBlackjack) {
                return GameResult.PLAYER_BLACKJACK;
            }
            
            // Dealer has natural blackjack, player has 21 but not natural - Dealer wins
            if (!playerBlackjack && dealerBlackjack) {
                return GameResult.DEALER_WIN;
            }
            
            // Both have 21 but neither is natural blackjack (both have >2 cards) - PUSH
            return GameResult.PUSH;
        }
        
        // Handle blackjack scenarios when not both 21
        if (playerBlackjack && dealerValue != 21) {
            return GameResult.PLAYER_BLACKJACK; // Player blackjack wins
        }
        
        if (dealerBlackjack && playerValue != 21) {
            return GameResult.DEALER_WIN; // Dealer blackjack wins
        }
        
        // Compare values for all other scenarios
        if (playerValue > dealerValue) {
            return GameResult.PLAYER_WIN;
        } else if (dealerValue > playerValue) {
            return GameResult.DEALER_WIN;
        } else {
            // Values are equal but not 21 (since 21-21 handled above)
            return GameResult.PUSH;
        }
    }

    /**
     * Calculate the credit payout for the player based on the game result.
     * @return the credit change (positive for win, zero for push, negative for loss)
     */
    public int calculatePayout() {
        GameResult result = getResult();
        
        switch (result) {
            case PLAYER_BLACKJACK:
                return (int) (betAmount * 1.5); // Blackjack pays 3:2
            case PLAYER_WIN:
                return betAmount; // Normal win pays 1:1
            case PUSH:
                return 0; // Push returns bet (no net change since bet was already deducted)
            case DEALER_WIN:
                return -betAmount; // Loss (but bet was already deducted, so this represents the total loss)
            case IN_PROGRESS:
            default:
                return 0;
        }
    }

    // Getters
    public String getUserId() {
        return userId;
    }

    public int getBetAmount() {
        return betAmount;
    }

    public BlackjackHand getPlayerHand() {
        return playerHand;
    }

    public BlackjackHand getDealerHand() {
        return dealerHand;
    }

    public boolean isGameEnded() {
        return gameEnded;
    }

    public boolean isDealerTurn() {
        return dealerTurn;
    }

    public Deck getDeck() {
        return deck;
    }

    // Add setter methods for dramatic effects
    public void setDealerTurn(boolean dealerTurn) {
        this.dealerTurn = dealerTurn;
    }

    public void setGameEnded(boolean gameEnded) {
        this.gameEnded = gameEnded;
    }

    /**
     * Enum representing possible game results.
     */
    public enum GameResult {
        IN_PROGRESS,
        PLAYER_WIN,
        PLAYER_BLACKJACK,
        DEALER_WIN,
        PUSH
    }
} 