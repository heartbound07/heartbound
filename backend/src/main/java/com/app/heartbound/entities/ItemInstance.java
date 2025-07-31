package com.app.heartbound.entities;

import com.app.heartbound.config.security.Views;
import com.fasterxml.jackson.annotation.JsonView;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "item_instances")
public class ItemInstance {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @JsonView(Views.Public.class)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "base_item_id", nullable = false)
    @JsonView(Views.Public.class)
    private Shop baseItem;

    @Column(name = "serial_number")
    @JsonView(Views.Public.class)
    private Long serialNumber;

    @Column(name = "durability")
    @JsonView(Views.Public.class)
    private Integer durability;

    @Column(name = "max_durability")
    @JsonView(Views.Public.class)
    private Integer maxDurability;

    @Column(name = "experience")
    @JsonView(Views.Public.class)
    private Long experience;

    @Builder.Default
    @Column(name = "level")
    @JsonView(Views.Public.class)
    private Integer level = 1;

    @Builder.Default
    @Column(name = "repair_count")
    @JsonView(Views.Public.class)
    private Integer repairCount = 0;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    @JsonView(Views.Public.class)
    private Instant createdAt;

    // Equipped Parts for Fishing Rods
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "equipped_rod_shaft_id", unique = true)
    @JsonView(Views.Public.class)
    private ItemInstance equippedRodShaft;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "equipped_reel_id", unique = true)
    @JsonView(Views.Public.class)
    private ItemInstance equippedReel;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "equipped_fishing_line_id", unique = true)
    @JsonView(Views.Public.class)
    private ItemInstance equippedFishingLine;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "equipped_hook_id", unique = true)
    @JsonView(Views.Public.class)
    private ItemInstance equippedHook;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "equipped_grip_id", unique = true)
    @JsonView(Views.Public.class)
    private ItemInstance equippedGrip;
} 