package com.app.heartbound.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Entity
@Table(name = "daily_voice_activity_stats", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"user_id", "date"})
})
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DailyVoiceActivityStat {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    
    @Column(name = "date", nullable = false)
    private LocalDate date;
    
    @Column(name = "voice_minutes", nullable = false)
    private Long voiceMinutes = 0L;
} 