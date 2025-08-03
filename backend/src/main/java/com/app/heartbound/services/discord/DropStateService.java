package com.app.heartbound.services.discord;

import lombok.Getter;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Service
public class DropStateService {

    public enum DropType {
        CREDIT,
        ITEM
    }

    @Getter
    public static class ActiveDrop {
        private final String messageId;
        private final DropType type;
        private final Object value; // Integer for credits, UUID for item ID
        private final ScheduledFuture<?> expirationTask;

        public ActiveDrop(String messageId, DropType type, Object value, ScheduledFuture<?> expirationTask) {
            this.messageId = messageId;
            this.type = type;
            this.value = value;
            this.expirationTask = expirationTask;
        }
    }

    private final ConcurrentHashMap<String, ActiveDrop> activeDrops = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> recentlyExpiredChannels = new ConcurrentHashMap<>();
    private final ScheduledExecutorService cleanupScheduler = Executors.newSingleThreadScheduledExecutor();

    public void startDrop(String channelId, String messageId, DropType type, Object value, ScheduledFuture<?> expirationTask) {
        activeDrops.put(channelId, new ActiveDrop(messageId, type, value, expirationTask));
    }

    public Optional<ActiveDrop> claimDrop(String channelId) {
        return Optional.ofNullable(activeDrops.remove(channelId));
    }

    public Optional<ActiveDrop> expireDrop(String channelId, String messageId) {
        final ActiveDrop[] removedDrop = {null};
        activeDrops.computeIfPresent(channelId, (key, existingDrop) -> {
            if (existingDrop.getMessageId().equals(messageId)) {
                removedDrop[0] = existingDrop;
                // Track this channel as having a recently expired drop
                recentlyExpiredChannels.put(channelId, System.currentTimeMillis());
                // Schedule cleanup of this entry after 10 seconds
                cleanupScheduler.schedule(() -> recentlyExpiredChannels.remove(channelId), 10, TimeUnit.SECONDS);
                return null; // remove the mapping
            }
            return existingDrop; // keep the existing mapping
        });
        return Optional.ofNullable(removedDrop[0]);
    }

    public boolean hadRecentExpiration(String channelId) {
        Long expiredTime = recentlyExpiredChannels.get(channelId);
        if (expiredTime == null) {
            return false;
        }
        // Consider a drop recently expired if it was within the last 10 seconds
        return (System.currentTimeMillis() - expiredTime) < 10000;
    }

    public boolean hasActiveDrop(String channelId) {
        return activeDrops.containsKey(channelId);
    }
} 