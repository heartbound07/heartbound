package com.app.heartbound.entities;

import com.app.heartbound.config.security.Views;
import com.app.heartbound.enums.TradeStatus;
import com.fasterxml.jackson.annotation.JsonView;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "trades")
public class Trade {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @JsonView(Views.Public.class)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "initiator_id", nullable = false)
    @JsonView(Views.Public.class)
    private User initiator;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "receiver_id", nullable = false)
    @JsonView(Views.Public.class)
    private User receiver;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    @JsonView(Views.Public.class)
    private TradeStatus status = TradeStatus.PENDING;

    @Builder.Default
    @JsonView(Views.Public.class)
    private Boolean initiatorLocked = false;

    @Builder.Default
    @JsonView(Views.Public.class)
    private Boolean receiverLocked = false;
    
    @Builder.Default
    @JsonView(Views.Public.class)
    private Boolean initiatorAccepted = false;

    @Builder.Default
    @JsonView(Views.Public.class)
    private Boolean receiverAccepted = false;

    @JsonView(Views.Public.class)
    private Instant expiresAt;

    private String discordMessageId;
    
    private String discordChannelId;

    @OneToMany(mappedBy = "trade", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    @JsonView(Views.Public.class)
    private List<TradeItem> items = new ArrayList<>();

    @CreationTimestamp
    @JsonView(Views.Public.class)
    private Instant createdAt;

    @UpdateTimestamp
    @JsonView(Views.Public.class)
    private Instant updatedAt;
} 