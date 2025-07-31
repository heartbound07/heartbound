package com.app.heartbound.services.discord;

import lombok.Getter;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

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

        public ActiveDrop(String messageId, DropType type, Object value) {
            this.messageId = messageId;
            this.type = type;
            this.value = value;
        }
    }

    private final ConcurrentHashMap<String, ActiveDrop> activeDrops = new ConcurrentHashMap<>();

    public void startDrop(String channelId, String messageId, DropType type, Object value) {
        activeDrops.put(channelId, new ActiveDrop(messageId, type, value));
    }

    public Optional<ActiveDrop> claimDrop(String channelId) {
        return Optional.ofNullable(activeDrops.remove(channelId));
    }

    public Optional<ActiveDrop> expireDrop(String channelId, String messageId) {
        final ActiveDrop[] removedDrop = {null};
        activeDrops.computeIfPresent(channelId, (key, existingDrop) -> {
            if (existingDrop.getMessageId().equals(messageId)) {
                removedDrop[0] = existingDrop;
                return null; // remove the mapping
            }
            return existingDrop; // keep the existing mapping
        });
        return Optional.ofNullable(removedDrop[0]);
    }

    public boolean hasActiveDrop(String channelId) {
        return activeDrops.containsKey(channelId);
    }
} 