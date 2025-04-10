package com.app.heartbound.enums;

/**
 * Represents the status of Valorant match tracking for an LFG party.
 */
public enum TrackingStatus {
    /** Initial state, tracking not active. */
    IDLE,
    /** Actively searching for a common game among participants. */
    SEARCHING,
    /** A potential common game has been detected, awaiting confirmation/start. */
    GAME_DETECTED,
    /** Confirmed game is in progress. */
    GAME_IN_PROGRESS,
    /** Game completed, awaiting reward processing. */
    GAME_COMPLETED,
    /** Game completed and credits successfully rewarded. */
    REWARDED,
    /** An error occurred during tracking. */
    TRACKING_FAILED
} 