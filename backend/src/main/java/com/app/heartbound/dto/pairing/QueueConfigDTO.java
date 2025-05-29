package com.app.heartbound.dto.pairing;

import com.fasterxml.jackson.annotation.JsonProperty;

public class QueueConfigDTO {
    @JsonProperty("queueEnabled")
    private boolean queueEnabled;
    
    @JsonProperty("message")
    private String message;
    
    @JsonProperty("updatedBy")
    private String updatedBy;
    
    @JsonProperty("timestamp")
    private String timestamp;

    public QueueConfigDTO() {}

    public QueueConfigDTO(boolean queueEnabled, String message, String updatedBy) {
        this.queueEnabled = queueEnabled;
        this.message = message;
        this.updatedBy = updatedBy;
        this.timestamp = java.time.Instant.now().toString();
    }

    // Getters and setters
    public boolean isQueueEnabled() {
        return queueEnabled;
    }

    public void setQueueEnabled(boolean queueEnabled) {
        this.queueEnabled = queueEnabled;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }
} 