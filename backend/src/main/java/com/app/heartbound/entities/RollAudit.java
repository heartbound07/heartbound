package com.app.heartbound.entities;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * RollAudit Entity
 * 
 * Tracks all case roll operations for security auditing and fairness verification.
 * Provides complete audit trail for gambling mechanics.
 */
@Entity
@Table(name = "roll_audits", indexes = {
    @Index(name = "idx_roll_audit_user_id", columnList = "user_id"),
    @Index(name = "idx_roll_audit_case_id", columnList = "case_id"),
    @Index(name = "idx_roll_audit_timestamp", columnList = "roll_timestamp"),
    @Index(name = "idx_roll_audit_session", columnList = "session_id")
})
public class RollAudit {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @Column(name = "user_id", nullable = false, length = 50)
    private String userId;
    
    @Column(name = "case_id", nullable = false)
    private UUID caseId;
    
    @Column(name = "case_name", nullable = false, length = 100)
    private String caseName;
    
    @Column(name = "won_item_id", nullable = false)
    private UUID wonItemId;
    
    @Column(name = "won_item_name", nullable = false, length = 100)
    private String wonItemName;
    
    @Column(name = "roll_value", nullable = false)
    private Integer rollValue;
    
    @Column(name = "roll_seed_hash", nullable = false, length = 64)
    private String rollSeedHash;
    
    @Column(name = "drop_rate", nullable = false)
    private Integer dropRate;
    
    @Column(name = "total_drop_rates", nullable = false)
    private Integer totalDropRates;
    
    @Column(name = "case_items_count", nullable = false)
    private Integer caseItemsCount;
    
    @Column(name = "already_owned", nullable = false)
    private Boolean alreadyOwned;
    
    @Column(name = "client_ip", length = 45)
    private String clientIp;
    
    @Column(name = "user_agent", length = 500)
    private String userAgent;
    
    @Column(name = "session_id", length = 100)
    private String sessionId;
    
    @CreationTimestamp
    @Column(name = "roll_timestamp", nullable = false)
    private LocalDateTime rollTimestamp;
    
    @Column(name = "processing_time_ms")
    private Long processingTimeMs;
    
    @Column(name = "user_credits_before", nullable = false)
    private Integer userCreditsBefore;
    
    @Column(name = "user_credits_after", nullable = false)
    private Integer userCreditsAfter;
    
    @Column(name = "verification_status", length = 20)
    @Enumerated(EnumType.STRING)
    private VerificationStatus verificationStatus = VerificationStatus.PENDING;
    
    @Column(name = "anomaly_flags", length = 500)
    private String anomalyFlags;
    
    @Column(name = "statistical_hash", length = 64)
    private String statisticalHash;
    
    // Constructors
    public RollAudit() {}
    
    public RollAudit(String userId, UUID caseId, String caseName, UUID wonItemId, 
                    String wonItemName, Integer rollValue, String rollSeedHash,
                    Integer dropRate, Integer totalDropRates, Integer caseItemsCount,
                    Boolean alreadyOwned, String clientIp, String userAgent, 
                    String sessionId, Integer userCreditsBefore, Integer userCreditsAfter) {
        this.userId = userId;
        this.caseId = caseId;
        this.caseName = caseName;
        this.wonItemId = wonItemId;
        this.wonItemName = wonItemName;
        this.rollValue = rollValue;
        this.rollSeedHash = rollSeedHash;
        this.dropRate = dropRate;
        this.totalDropRates = totalDropRates;
        this.caseItemsCount = caseItemsCount;
        this.alreadyOwned = alreadyOwned;
        this.clientIp = clientIp;
        this.userAgent = userAgent;
        this.sessionId = sessionId;
        this.userCreditsBefore = userCreditsBefore;
        this.userCreditsAfter = userCreditsAfter;
    }
    
    // Getters and Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    
    public UUID getCaseId() { return caseId; }
    public void setCaseId(UUID caseId) { this.caseId = caseId; }
    
    public String getCaseName() { return caseName; }
    public void setCaseName(String caseName) { this.caseName = caseName; }
    
    public UUID getWonItemId() { return wonItemId; }
    public void setWonItemId(UUID wonItemId) { this.wonItemId = wonItemId; }
    
    public String getWonItemName() { return wonItemName; }
    public void setWonItemName(String wonItemName) { this.wonItemName = wonItemName; }
    
    public Integer getRollValue() { return rollValue; }
    public void setRollValue(Integer rollValue) { this.rollValue = rollValue; }
    
    public String getRollSeedHash() { return rollSeedHash; }
    public void setRollSeedHash(String rollSeedHash) { this.rollSeedHash = rollSeedHash; }
    
    public Integer getDropRate() { return dropRate; }
    public void setDropRate(Integer dropRate) { this.dropRate = dropRate; }
    
    public Integer getTotalDropRates() { return totalDropRates; }
    public void setTotalDropRates(Integer totalDropRates) { this.totalDropRates = totalDropRates; }
    
    public Integer getCaseItemsCount() { return caseItemsCount; }
    public void setCaseItemsCount(Integer caseItemsCount) { this.caseItemsCount = caseItemsCount; }
    
    public Boolean getAlreadyOwned() { return alreadyOwned; }
    public void setAlreadyOwned(Boolean alreadyOwned) { this.alreadyOwned = alreadyOwned; }
    
    public String getClientIp() { return clientIp; }
    public void setClientIp(String clientIp) { this.clientIp = clientIp; }
    
    public String getUserAgent() { return userAgent; }
    public void setUserAgent(String userAgent) { this.userAgent = userAgent; }
    
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    
    public LocalDateTime getRollTimestamp() { return rollTimestamp; }
    public void setRollTimestamp(LocalDateTime rollTimestamp) { this.rollTimestamp = rollTimestamp; }
    
    public Long getProcessingTimeMs() { return processingTimeMs; }
    public void setProcessingTimeMs(Long processingTimeMs) { this.processingTimeMs = processingTimeMs; }
    
    public Integer getUserCreditsBefore() { return userCreditsBefore; }
    public void setUserCreditsBefore(Integer userCreditsBefore) { this.userCreditsBefore = userCreditsBefore; }
    
    public Integer getUserCreditsAfter() { return userCreditsAfter; }
    public void setUserCreditsAfter(Integer userCreditsAfter) { this.userCreditsAfter = userCreditsAfter; }
    
    public VerificationStatus getVerificationStatus() { return verificationStatus; }
    public void setVerificationStatus(VerificationStatus verificationStatus) { this.verificationStatus = verificationStatus; }
    
    public String getAnomalyFlags() { return anomalyFlags; }
    public void setAnomalyFlags(String anomalyFlags) { this.anomalyFlags = anomalyFlags; }
    
    public String getStatisticalHash() { return statisticalHash; }
    public void setStatisticalHash(String statisticalHash) { this.statisticalHash = statisticalHash; }
    
    /**
     * Verification status enum for roll audits
     */
    public enum VerificationStatus {
        PENDING,
        VERIFIED,
        SUSPICIOUS,
        FLAGGED
    }
} 