package com.app.heartbound.services.discord;

import org.springframework.stereotype.Service;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Optional;

@Service
public class CreditDropStateService {

    public record ActiveDrop(String messageId, int amount) {}

    private final ConcurrentHashMap<String, ActiveDrop> activeDrops = new ConcurrentHashMap<>();

    public void startDrop(String channelId, String messageId, int amount) {
        activeDrops.put(channelId, new ActiveDrop(messageId, amount));
    }

    public Optional<ActiveDrop> claimDrop(String channelId) {
        return Optional.ofNullable(activeDrops.remove(channelId));
    }

    public Optional<ActiveDrop> expireDrop(String channelId, String messageId) {
        final ActiveDrop[] removedDrop = {null};
        activeDrops.computeIfPresent(channelId, (key, existingDrop) -> {
            if (existingDrop.messageId().equals(messageId)) {
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