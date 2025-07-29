package com.app.heartbound.utils;

public class LevelingUtil {

    public static final int MAX_ROD_LEVEL = 50;

    /**
     * Calculates the total XP required to reach the next level for a fishing rod.
     * The formula is: 50 * (level^2) + (150 * level).
     *
     * @param level The current level of the rod.
     * @return The amount of XP required for the next level, or Long.MAX_VALUE if max level is reached.
     */
    public static long calculateXpForRodLevel(int level) {
        if (level >= MAX_ROD_LEVEL) {
            return Long.MAX_VALUE;
        }
        return (long) (50 * Math.pow(level, 2) + (150 * level));
    }
} 