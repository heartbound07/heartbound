package com.app.heartbound.entities;

import com.app.heartbound.services.SecureRandomService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Represents a complete Blackjack game state for a single player.
 */
public class BlackjackGame {
    private static final Logger logger = LoggerFactory.getLogger(BlackjackGame.class);
    
    private final String userId;
    private int betAmount; // Changed from final to support doubling down
    private final double roleMultiplier;
    private final Deck deck;
    private final BlackjackHand playerHand;
    private final BlackjackHand dealerHand;
    private boolean gameEnded;
    private boolean dealerTurn;
    
    // Double down state
    private boolean isDoubledDown;
    
    // Split state management
    private boolean isSplit;
    private BlackjackHand secondHand;
    private boolean isPlayingFirstHand;
    private boolean firstHandCompleted;
    private boolean splitAces; // Special rule for splitting aces
    
    // Action tracking for first-action-only options
    private boolean hasPlayerActed;

    public BlackjackGame(String userId, int betAmount, double roleMultiplier, SecureRandomService secureRandomService) {
        this.userId = userId;
        this.betAmount = betAmount;
        this.roleMultiplier = roleMultiplier;
        this.deck = new Deck(secureRandomService);
        this.playerHand = new BlackjackHand();
        this.dealerHand = new BlackjackHand();
        this.gameEnded = false;
        this.dealerTurn = false;
        
        // Initialize new state variables
        this.isDoubledDown = false;
        this.isSplit = false;
        this.secondHand = null;
        this.isPlayingFirstHand = true;
        this.firstHandCompleted = false;
        this.splitAces = false;
        this.hasPlayerActed = false;
        
        // Initialize the game with secure shuffling
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
     * Check if player can double down.
     * Available only on first action with exactly 2 cards and sufficient credits.
     */
    public boolean canDoubleDown() {
        return !hasPlayerActed && 
               !gameEnded && 
               !dealerTurn && 
               !isSplit &&
               playerHand.getSize() == 2 && 
               !playerHand.isBlackjack();
    }
    
    /**
     * Execute double down action.
     * Doubles the bet, deals one card, and automatically stands.
     */
    public Card doubleDown() {
        if (!canDoubleDown()) {
            throw new IllegalStateException("Cannot double down in current game state");
        }
        
        // Double the bet amount
        this.betAmount *= 2;
        this.isDoubledDown = true;
        this.hasPlayerActed = true;
        
        // Deal exactly one card
        Card card = deck.dealCard();
        playerHand.addCard(card);
        
        // Check if player busted
        if (playerHand.isBusted()) {
            gameEnded = true;
        } else {
            // Automatically stand after double down
            playerStand();
        }
        
        return card;
    }
    
    /**
     * Check if player can split their hand.
     * Available only on first action with a pair and sufficient credits.
     */
    public boolean canSplit() {
        if (hasPlayerActed || gameEnded || dealerTurn || isSplit || playerHand.getSize() != 2) {
            return false;
        }
        
        // Check if the two cards have the same rank
        List<Card> cards = playerHand.getCards();
        return cards.get(0).getRank() == cards.get(1).getRank();
    }
    
    /**
     * Execute split action.
     * Creates two hands from the pair and deals new second cards.
     */
    public void split() {
        if (!canSplit()) {
            throw new IllegalStateException("Cannot split in current game state");
        }
        
        List<Card> cards = playerHand.getCards();
        Card firstCard = cards.get(0);
        Card secondCard = cards.get(1);
        
        // Check if splitting aces
        this.splitAces = firstCard.isAce();
        
        // Double the bet amount for the second hand
        this.betAmount *= 2;
        this.isSplit = true;
        this.hasPlayerActed = true;
        this.isPlayingFirstHand = true;
        this.firstHandCompleted = false;
        
        // Clear the original hand and add the first card back
        playerHand.clear();
        playerHand.addCard(firstCard);
        
        // Create second hand with the second card
        this.secondHand = new BlackjackHand();
        secondHand.addCard(secondCard);
        
        // Deal second cards to both hands
        playerHand.addCard(deck.dealCard());
        secondHand.addCard(deck.dealCard());
        
        // Check if first hand is 21 and auto-stand (applies to all splits including aces)
        if (playerHand.getValue() == 21) {
            this.firstHandCompleted = true;
            this.isPlayingFirstHand = false;
            // Don't end the game - player still needs to play second hand
            logger.debug("First hand has 21, auto-standing and switching to second hand");
            
            // Check if second hand is also 21
            if (secondHand.getValue() == 21) {
                logger.debug("Second hand also has 21, both hands complete");
                this.dealerTurn = true;
                this.gameEnded = true;
            }
        }
    }
    
    /**
     * Get the currently active hand (for split games).
     */
    public BlackjackHand getActiveHand() {
        if (!isSplit) {
            return playerHand;
        }
        return isPlayingFirstHand ? playerHand : secondHand;
    }
    
    /**
     * Check if the current active hand can take actions.
     */
    public boolean canActiveHandAct() {
        if (gameEnded || dealerTurn) {
            return false;
        }
        
        if (!isSplit) {
            return true;
        }
        
        // For split hands
        BlackjackHand activeHand = getActiveHand();
        return !activeHand.isBusted();
    }

    /**
     * Player hits (takes another card) on the active hand.
     * @return the dealt card
     */
    public Card playerHit() {
        if (!canActiveHandAct()) {
            throw new IllegalStateException("Cannot hit in current game state");
        }
        
        this.hasPlayerActed = true;
        BlackjackHand activeHand = getActiveHand();
        
        Card card = deck.dealCard();
        activeHand.addCard(card);
        
        // Check if active hand busted
        if (activeHand.isBusted()) {
            if (isSplit && isPlayingFirstHand && !firstHandCompleted) {
                // First hand busted, move to second hand
                this.firstHandCompleted = true;
                this.isPlayingFirstHand = false;
            } else if (isSplit && !isPlayingFirstHand) {
                // Second hand busted, check if first hand is also busted
                if (playerHand.isBusted()) {
                    // Both hands busted, end game immediately
                    this.gameEnded = true;
                } else {
                    // First hand is still valid, dealer must play
                    this.dealerTurn = true;
                    this.gameEnded = true;
                }
            } else {
                // Single hand busted
                this.gameEnded = true;
            }
        } else if (activeHand.getValue() == 21) {
            // FIX: Auto-stand when hand reaches 21
            if (isSplit && isPlayingFirstHand && !firstHandCompleted) {
                // First hand hit 21, move to second hand
                this.firstHandCompleted = true;
                this.isPlayingFirstHand = false;
            } else {
                // Either single hand hit 21, or second hand hit 21 - end player's turn
                this.dealerTurn = true;
                this.gameEnded = true;
            }
        }
        
        return card;
    }

    /**
     * Player stands on the active hand.
     */
    public void playerStand() {
        if (gameEnded) {
            throw new IllegalStateException("Cannot stand when game is already ended");
        }
        
        this.hasPlayerActed = true;
        
        if (isSplit && isPlayingFirstHand && !firstHandCompleted) {
            // First hand stands, move to second hand
            this.firstHandCompleted = true;
            this.isPlayingFirstHand = false;
        } else {
            // Either single hand stands, or second hand stands.
            // The listener will handle the dealer's turn and set gameEnded.
            this.dealerTurn = true;
            this.gameEnded = true; // Mark game as ended here to signal dealer's turn can start.
        }
    }

    /**
     * Determine the game result for single hand games.
     * For split games, use getSplitResults() instead.
     */
    public GameResult getResult() {
        if (isSplit) {
            throw new IllegalStateException("Use getSplitResults() for split games");
        }
        
        if (!gameEnded) {
            return GameResult.IN_PROGRESS;
        }
        
        int playerValue = playerHand.getValue();
        int dealerValue = dealerHand.getValue();
        boolean playerBlackjack = playerHand.isBlackjack() && !isDoubledDown; // Double down can't be blackjack
        boolean dealerBlackjack = dealerHand.isBlackjack();
        
        // Check for busts first
        if (playerHand.isBusted()) {
            return GameResult.DEALER_WIN; // Player busted
        }
        
        if (dealerHand.isBusted()) {
            return playerBlackjack ? GameResult.PLAYER_BLACKJACK : GameResult.PLAYER_WIN; // Dealer busted
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
     * Get the results for both hands in a split game.
     * @return array with [firstHandResult, secondHandResult]
     */
    public GameResult[] getSplitResults() {
        if (!isSplit) {
            throw new IllegalStateException("Use getResult() for non-split games");
        }
        
        if (!gameEnded) {
            return new GameResult[]{GameResult.IN_PROGRESS, GameResult.IN_PROGRESS};
        }
        
        GameResult firstResult = calculateHandResult(playerHand);
        GameResult secondResult = calculateHandResult(secondHand);
        
        return new GameResult[]{firstResult, secondResult};
    }
    
    /**
     * Calculate result for a specific hand against dealer.
     */
    private GameResult calculateHandResult(BlackjackHand hand) {
        int handValue = hand.getValue();
        int dealerValue = dealerHand.getValue();
        boolean handBlackjack = hand.isBlackjack() && !splitAces; // Split aces can't make blackjack
        boolean dealerBlackjack = dealerHand.isBlackjack();
        
        // Check for busts first
        if (hand.isBusted()) {
            return GameResult.DEALER_WIN;
        }
        
        if (dealerHand.isBusted()) {
            return handBlackjack ? GameResult.PLAYER_BLACKJACK : GameResult.PLAYER_WIN;
        }
        
        // Handle 21-21 scenarios
        if (handValue == 21 && dealerValue == 21) {
            if (handBlackjack && dealerBlackjack) {
                return GameResult.PUSH;
            }
            if (handBlackjack && !dealerBlackjack) {
                return GameResult.PLAYER_BLACKJACK;
            }
            if (!handBlackjack && dealerBlackjack) {
                return GameResult.DEALER_WIN;
            }
            return GameResult.PUSH;
        }
        
        // Handle blackjack scenarios
        if (handBlackjack && dealerValue != 21) {
            return GameResult.PLAYER_BLACKJACK;
        }
        
        if (dealerBlackjack && handValue != 21) {
            return GameResult.DEALER_WIN;
        }
        
        // Compare values
        if (handValue > dealerValue) {
            return GameResult.PLAYER_WIN;
        } else if (dealerValue > handValue) {
            return GameResult.DEALER_WIN;
        } else {
            return GameResult.PUSH;
        }
    }

    /**
     * Calculate the credit payout for the player based on the game result.
     * For split games, returns total payout for both hands.
     */
    public int calculatePayout() {
        if (isSplit) {
            return calculateSplitPayout();
        }
        
        GameResult result = getResult();
        int baseBet = isDoubledDown ? betAmount / 2 : betAmount; // Get original bet amount
        
        switch (result) {
            case PLAYER_BLACKJACK:
                return (int) (baseBet * 1.5 * this.roleMultiplier * (isDoubledDown ? 2 : 1)); // Blackjack pays 3:2, doubled if doubled down
            case PLAYER_WIN:
                return isDoubledDown ? baseBet * 2 : baseBet; // Double down wins pay 2:1 on original bet
            case PUSH:
                return 0; // Push returns bet (no net change since bet was already deducted)
            case DEALER_WIN:
                return -baseBet; // Loss (but bet was already deducted, so this represents the total loss)
            case IN_PROGRESS:
            default:
                return 0;
        }
    }
    
    /**
     * Calculate payout for split games.
     */
    private int calculateSplitPayout() {
        GameResult[] results = getSplitResults();
        int originalBet = betAmount / 2; // Each hand gets half the total bet
        int totalPayout = 0;
        
        for (GameResult result : results) {
            switch (result) {
                case PLAYER_BLACKJACK:
                    totalPayout += (int) (originalBet * 1.5 * this.roleMultiplier);
                    break;
                case PLAYER_WIN:
                    totalPayout += originalBet;
                    break;
                case PUSH:
                    // No change for push
                    break;
                case DEALER_WIN:
                    totalPayout -= originalBet;
                    break;
                case IN_PROGRESS:
                default:
                    // No payout change for in-progress or unexpected states
                    break;
            }
        }
        
        return totalPayout;
    }

    // Getters
    public String getUserId() {
        return userId;
    }

    public int getBetAmount() {
        return betAmount;
    }

    public double getRoleMultiplier() {
        return roleMultiplier;
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

    // Getters for new fields
    public boolean isDoubledDown() {
        return isDoubledDown;
    }
    
    public boolean isSplit() {
        return isSplit;
    }
    
    public BlackjackHand getSecondHand() {
        return secondHand;
    }
    
    public boolean isPlayingFirstHand() {
        return isPlayingFirstHand;
    }
    
    public boolean isFirstHandCompleted() {
        return firstHandCompleted;
    }
    
    public boolean isSplitAces() {
        return splitAces;
    }
    
    public boolean hasPlayerActed() {
        return hasPlayerActed;
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