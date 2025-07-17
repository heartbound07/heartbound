package com.app.heartbound.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Data
@Entity
@Table(name = "giveaways")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Giveaway {
    
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;
    
    @Column(nullable = false)
    private String hostUserId; // Discord user ID of the admin who created it
    
    @Column(nullable = false)
    private String hostUsername; // Username for display
    
    @Column(nullable = false)
    private String prize;
    
    @Column(nullable = false)
    private Integer numberOfWinners;
    
    @Column(nullable = false)
    private LocalDateTime endDate;
    
    @Column(nullable = false)
    private String channelId; // Discord channel where giveaway was posted
    
    @Column(nullable = false)
    private String messageId; // Discord message ID of the giveaway embed
    
    // Restrictions
    @Builder.Default
    private Boolean boostersOnly = false;
    @Builder.Default
    private Boolean levelRestricted = false; // Level 5+ users only
    @Builder.Default
    private Boolean noRestrictions = false;
    
    // Entry configuration
    @Builder.Default
    private Integer maxEntriesPerUser = 0; // null = unlimited
    @Builder.Default
    private Integer entryPrice = 0; // Credits cost per entry, 0 = free
    
    // Status tracking
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private GiveawayStatus status = GiveawayStatus.ACTIVE;
    
    @Column(nullable = false)
    private LocalDateTime createdAt;
    
    private LocalDateTime completedAt;
    
    // Relationship to entries
    @OneToMany(mappedBy = "giveaway", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private Set<GiveawayEntry> entries = new HashSet<>();
    
    // Helper methods
    public boolean isExpired() {
        return LocalDateTime.now().isAfter(endDate);
    }
    
    public boolean isActive() {
        return status == GiveawayStatus.ACTIVE && !isExpired();
    }
    
    public int getTotalEntries() {
        return entries != null ? entries.size() : 0;
    }
    
    public boolean hasRestrictions() {
        return boostersOnly || levelRestricted;
    }
    
    public enum GiveawayStatus {
        ACTIVE,
        COMPLETED,
        CANCELLED
    }
} 