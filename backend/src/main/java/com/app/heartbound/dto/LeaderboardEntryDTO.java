package com.app.heartbound.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Builder;

/**
 * Data transfer object for a single entry in the leaderboard.
 * Contains only the fields essential for rendering the leaderboard to optimize performance.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LeaderboardEntryDTO {
    private String id;
    private String username;
    private String displayName;
    private String avatar;
    private Integer credits;
    private Integer level;
    private Integer experience;
    private Integer voiceTimeMinutesTotal;
    private Long messageCount;
    private Integer rank;
    private boolean banned;

    /**
     * Constructor used by JPA's constructor expression to map query results directly.
     * This avoids the overhead of fetching the full User entity.
     */
    public LeaderboardEntryDTO(String id, String username, String displayName, String avatar, Integer credits, Integer level, Integer experience, Integer voiceTimeMinutesTotal, Long messageCount, Boolean banned) {
        this.id = id;
        this.username = username;
        this.displayName = displayName;
        this.avatar = avatar;
        this.credits = credits;
        this.level = level;
        this.experience = experience;
        this.voiceTimeMinutesTotal = voiceTimeMinutesTotal;
        this.messageCount = messageCount;
        this.banned = banned != null && banned;
    }
} 