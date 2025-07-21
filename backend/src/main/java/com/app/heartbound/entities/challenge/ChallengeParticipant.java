package com.app.heartbound.entities.challenge;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "challenge_participants")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChallengeParticipant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String userId;

    @Column(nullable = false)
    private String teamId;

    @Column(nullable = false)
    private String teamName;

    @Column(nullable = false)
    @Builder.Default
    private long messageCount = 0L;

    @Column(nullable = false)
    private String challengePeriod;
} 