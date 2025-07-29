package com.app.heartbound.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "item_instances")
public class ItemInstance {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "base_item_id", nullable = false)
    private Shop baseItem;

    @Column(name = "serial_number")
    private Long serialNumber;

    @Column(name = "durability")
    private Integer durability;

    @Column(name = "max_durability")
    private Integer maxDurability;

    @Column(name = "experience")
    private Long experience;

    @Builder.Default
    @Column(name = "level")
    private Integer level = 1;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    // Equipped Parts for Fishing Rods
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "equipped_rod_shaft_id", unique = true)
    private ItemInstance equippedRodShaft;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "equipped_reel_id", unique = true)
    private ItemInstance equippedReel;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "equipped_fishing_line_id", unique = true)
    private ItemInstance equippedFishingLine;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "equipped_hook_id", unique = true)
    private ItemInstance equippedHook;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "equipped_grip_id", unique = true)
    private ItemInstance equippedGrip;
} 