package com.app.heartbound.enums;

/**
 * AchievementType Enum
 * 
 * Defines the different types of achievements that can be earned by pairs.
 * Each type has its own calculation logic and requirements.
 */
public enum AchievementType {
    /**
     * Achievements based on message count milestones
     * e.g., 1000 messages, 5000 messages, 10000 messages
     */
    MESSAGE_MILESTONE,
    
    /**
     * Achievements based on weekly activity streaks
     * e.g., 1 week active, 4 weeks active, 12 weeks active
     */
    WEEKLY_ACTIVITY,
    
    /**
     * Achievements based on voice channel usage streaks
     * e.g., 3 days in a row, 7 days in a row, 14 days in a row
     */
    VOICE_STREAK,
    
    /**
     * Achievements based on time spent in voice channels
     * e.g., 10 hours, 50 hours, 100 hours
     */
    VOICE_TIME,
    
    /**
     * Achievements based on compatibility milestones
     * e.g., maintaining high compatibility, perfect compatibility streaks
     */
    COMPATIBILITY,
    
    /**
     * Special achievements for unique events or admin-awarded
     * e.g., first pair, community events, special recognitions
     */
    SPECIAL,
    
    /**
     * Achievements based on word count milestones
     * e.g., 50k words, 100k words, 1M words
     */
    WORD_COUNT,
    
    /**
     * Achievements based on emoji usage
     * e.g., emoji enthusiast, emoji master
     */
    EMOJI_COUNT,
    
    /**
     * Achievements based on relationship longevity
     * e.g., 1 month together, 3 months together, 6 months together
     */
    LONGEVITY
} 