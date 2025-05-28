package com.app.heartbound.dto.pairing;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QueueStatusDTO {
    private boolean inQueue;
    private LocalDateTime queuedAt;
    private int estimatedWaitTime; // in minutes
    private int queuePosition;
    private int totalQueueSize;
} 