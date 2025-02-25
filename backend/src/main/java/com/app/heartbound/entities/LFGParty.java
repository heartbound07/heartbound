package com.app.heartbound.entities;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Embedded;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import org.hibernate.annotations.UuidGenerator;
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
 */
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

    @Column(nullable = false)
    private String status = "open";

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "lfg_party_participants", joinColumns = @JoinColumn(name = "lfg_party_id"))
    @Column(name = "participant_id")
    private Set<String> participants = new HashSet<>();

    // Constructors

    public LFGParty() {
    }

    public LFGParty(String userId, String game, String title, String description,
                    PartyRequirements requirements, int expiresIn, int maxPlayers,
                    Instant createdAt, Instant expiresAt) {
        this.userId = userId;
        this.game = game;
        this.title = title;
        this.description = description;
        this.requirements = requirements;
        this.expiresIn = expiresIn;
        this.maxPlayers = maxPlayers;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.status = "open";
    }

    // Getters and Setters

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getGame() {
        return game;
    }

    public void setGame(String game) {
        this.game = game;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public PartyRequirements getRequirements() {
        return requirements;
    }

    public void setRequirements(PartyRequirements requirements) {
        this.requirements = requirements;
    }

    public int getExpiresIn() {
        return expiresIn;
    }

    public void setExpiresIn(int expiresIn) {
        this.expiresIn = expiresIn;
    }

    public int getMaxPlayers() {
        return maxPlayers;
    }

    public void setMaxPlayers(int maxPlayers) {
        this.maxPlayers = maxPlayers;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Set<String> getParticipants() {
        return participants;
    }

    public void setParticipants(Set<String> participants) {
        this.participants = participants;
    }

    /**
     * PartyRequirements
     *
     * Embeddable class to encapsulate party requirements.
     * Contains:
     * - rank: Minimum required rank.
     * - region: Preferred region.
     * - voiceChat: Flag indicating if voice chat is required.
     */
    @Embeddable
    public static class PartyRequirements {

        @Column(name = "req_rank", nullable = false)
        private String rank;

        @Column(name = "req_region", nullable = false)
        private String region;

        @Column(name = "req_voice_chat", nullable = false)
        private boolean voiceChat;

        public PartyRequirements() {
        }

        public PartyRequirements(String rank, String region, boolean voiceChat) {
            this.rank = rank;
            this.region = region;
            this.voiceChat = voiceChat;
        }

        public String getRank() {
            return rank;
        }

        public void setRank(String rank) {
            this.rank = rank;
        }

        public String getRegion() {
            return region;
        }

        public void setRegion(String region) {
            this.region = region;
        }

        public boolean isVoiceChat() {
            return voiceChat;
        }

        public void setVoiceChat(boolean voiceChat) {
            this.voiceChat = voiceChat;
        }
    }
}
