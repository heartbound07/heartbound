package com.app.heartbound.services.discord;

import lombok.Data;
import net.dv8tion.jda.api.interactions.InteractionHook;
import java.util.concurrent.ScheduledFuture;

@Data
public class MinesGame {
    private final String userId;
    private final InteractionHook hook;
    private final int betAmount;
    private final int mineCount;
    private final boolean[][] mines = new boolean[3][3];
    private final boolean[][] revealed = new boolean[3][3];
    private double currentMultiplier = 1.0;
    private final long gameStartTime = System.currentTimeMillis();
    private int safeTilesRevealed = 0;
    private ScheduledFuture<?> expirationTask;

    public MinesGame(String userId, InteractionHook hook, int betAmount, int mineCount) {
        this.userId = userId;
        this.hook = hook;
        this.betAmount = betAmount;
        this.mineCount = mineCount;
    }

    public int getTotalSafeTiles() {
        return 9 - mineCount;
    }
} 