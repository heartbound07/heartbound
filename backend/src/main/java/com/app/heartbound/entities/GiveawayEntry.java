package com.app.heartbound.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Entity
@Table(name = "giveaway_entries")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GiveawayEntry {
    
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "giveaway_id", nullable = false)
    private Giveaway giveaway;
    
    @Column(nullable = false)
    private String userId; // Discord user ID
    
    @Column(nullable = false)
    private String username; // Username for display
    
    @Column(nullable = false)
    private Integer entryNumber; // Entry number (1, 2, 3, etc. for multiple entries)
    
    @Column(nullable = false)
    private Integer creditsPaid; // Credits paid for this entry
    
    @Column(nullable = false)
    private LocalDateTime entryDate;
    
    // Helper methods
    public boolean isFreeEntry() {
        return creditsPaid == 0;
    }
} 