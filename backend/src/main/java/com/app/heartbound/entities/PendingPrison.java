package com.app.heartbound.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.List;

/**
 * PendingPrison Entity
 *
 * Stores prison records for Discord users who haven't registered on the website yet.
 * When they register, these records are migrated to their main User profile.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "pending_prisons",
       indexes = {
           @Index(name = "idx_pending_prison_user_id", columnList = "discord_user_id"),
           @Index(name = "idx_pending_prison_updated_at", columnList = "updated_at")
       })
public class PendingPrison {

    @Id
    @Column(name = "discord_user_id")
    private String discordUserId;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "pending_prison_original_roles", joinColumns = @JoinColumn(name = "discord_user_id"))
    @Column(name = "role_id")
    private List<String> originalRoleIds;

    private LocalDateTime prisonedAt;

    private LocalDateTime prisonReleaseAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
} 