package com.app.heartbound.entities;

import com.app.heartbound.enums.Rank;
import com.app.heartbound.enums.Region;
import com.app.heartbound.enums.TrackingStatus;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Embedded;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import org.hibernate.annotations.UuidGenerator;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * LFGParty
 *
 * Represents a Looking For Group (LFG) party post.
 *
 * Key fields:
 * - id: Primary key (UUID generated).
 * - userId: Identifier for the party creator.
 * - game: Name of the game (e.g., "Valorant").
 * - title: Party title.
 * - description: Details about the party.
 * - requirements: Embedded party requirements (rank, region, voiceChat).
 * - expiresIn: Duration (in minutes) until the party expires.
 * - maxPlayers: Maximum allowed players.
 * - status: Current status of the party (default "open").
 * - createdAt: Timestamp when the party was created.
 * - expiresAt: Timestamp when the party will expire (computed).
 * - participants: Collection of user IDs who have joined the party.
 * - trackingStatus: Current status of match tracking.
 * - currentTrackedMatchId: ID of the match currently being tracked.
 * - lastTrackedMatchCompletionTime: When the last tracked match completed.
 * - processedMatchIds: Set of match IDs that have been processed for rewards.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "lfg_parties")
public class LFGParty {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(nullable = false)
    private String game;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Embedded
    private PartyRequirements requirements;

    @Column(name = "expires_in", nullable = false)
    private int expiresIn;

    @Column(name = "max_players", nullable = false)
    private int maxPlayers;

    @Builder.Default
    @Column(nullable = false)
    private String status = "open";

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Builder.Default
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "lfg_party_participants", joinColumns = @JoinColumn(name = "lfg_party_id"))
    @Column(name = "participant_id")
    private Set<String> participants = new HashSet<>();

    // New fields for additional group information
    @Column(name = "match_type", nullable = false)
    private String matchType;

    @Column(name = "game_mode", nullable = false)
    private String gameMode;

    @Column(name = "team_size", nullable = false)
    private String teamSize;

    @Column(name = "voice_preference", nullable = false)
    private String voicePreference;

    @Column(name = "age_restriction", nullable = false)
    private String ageRestriction;

    // Collection of invited user IDs
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
        name = "lfg_party_invited_users",
        joinColumns = @JoinColumn(name = "party_id")
    )
    @Column(name = "invited_user_id")
    private Set<String> invitedUsers = new HashSet<>();

    // Collection of join requests
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
        name = "lfg_party_join_requests",
        joinColumns = @JoinColumn(name = "party_id")
    )
    @Column(name = "requesting_user_id")
    private Set<String> joinRequests = new HashSet<>();

    @Column(name = "discord_channel_id")
    private String discordChannelId;

    @Column(name = "discord_invite_url")
    private String discordInviteUrl;

    // --- New Fields for Game Tracking ---
    @Enumerated(EnumType.STRING)
    @Column(name = "tracking_status", nullable = false)
    @Builder.Default
    private TrackingStatus trackingStatus = TrackingStatus.IDLE;

    @Column(name = "current_tracked_match_id")
    private String currentTrackedMatchId;

    @Column(name = "last_tracked_match_completion_time")
    private Instant lastTrackedMatchCompletionTime;

    @ElementCollection(fetch = FetchType.LAZY) // Use LAZY fetch for potentially large collections
    @CollectionTable(
        name = "lfg_party_processed_matches",
        joinColumns = @JoinColumn(name = "party_id")
    )
    @Column(name = "match_id")
    @Builder.Default
    private Set<String> processedMatchIds = new HashSet<>();

    /**
     * PartyRequirements
     *
     * Embeddable class to encapsulate party requirements.
     * Contains:
     * - rank: Minimum required rank.
     * - region: Preferred region.
     * - inviteOnly: Flag indicating if the party is invite only.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Embeddable
    public static class PartyRequirements {
        @Enumerated(EnumType.STRING)
        @Column(name = "req_rank", nullable = false)
        private Rank rank;

        @Enumerated(EnumType.STRING)
        @Column(name = "req_region", nullable = false)
        private Region region;

        @Column(name = "req_invite_only", nullable = false)
        private boolean inviteOnly;
    }
}
