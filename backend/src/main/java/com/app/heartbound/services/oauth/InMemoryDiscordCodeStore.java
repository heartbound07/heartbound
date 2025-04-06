package com.app.heartbound.services.oauth;

import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.concurrent.*;

@Component
public class InMemoryDiscordCodeStore implements DiscordCodeStore {

    private static final Logger logger = LoggerFactory.getLogger(InMemoryDiscordCodeStore.class);
    private static final long CODE_EXPIRY_MS = TimeUnit.MINUTES.toMillis(5); // Code valid for 5 minutes
    private static final long CLEANUP_INTERVAL_MS = TimeUnit.MINUTES.toMillis(1);

    private final ConcurrentMap<String, CodeEntry> codeStore = new ConcurrentHashMap<>();
    private final SecureRandom secureRandom = new SecureRandom();
    private ScheduledExecutorService cleanupScheduler;

    private static class CodeEntry {
        final String userId;
        final long expiryTime;

        CodeEntry(String userId, long expiryTime) {
            this.userId = userId;
            this.expiryTime = expiryTime;
        }
    }

    @PostConstruct
    public void startCleanupTask() {
        cleanupScheduler = Executors.newSingleThreadScheduledExecutor();
        cleanupScheduler.scheduleAtFixedRate(this::cleanupExpiredCodes,
                CLEANUP_INTERVAL_MS, CLEANUP_INTERVAL_MS, TimeUnit.MILLISECONDS);
        logger.info("Started InMemoryDiscordCodeStore cleanup task.");
    }

    @PreDestroy
    public void stopCleanupTask() {
        if (cleanupScheduler != null) {
            cleanupScheduler.shutdownNow();
            logger.info("Stopped InMemoryDiscordCodeStore cleanup task.");
        }
    }

    @Override
    public void storeCode(String code, String userId) {
        long expiryTime = System.currentTimeMillis() + CODE_EXPIRY_MS;
        CodeEntry entry = new CodeEntry(userId, expiryTime);
        // Log BEFORE putting into map
        logger.info("InMemoryDiscordCodeStore: Attempting to store code [{}] for userId [{}] with expiry {}", code, userId, expiryTime);
        codeStore.put(code, entry);
        // Log confirmation AFTER successful put
        logger.info("InMemoryDiscordCodeStore: Successfully stored code [{}] for userId [{}]", code, userId);
    }

    @Override
    public String consumeCode(String code) {
        logger.info("InMemoryDiscordCodeStore: Attempting to consume code [{}]", code); // Log attempt
        CodeEntry entry = codeStore.remove(code); // Atomically remove the code

        if (entry == null) {
            logger.warn("InMemoryDiscordCodeStore: Consume failed - code [{}] not found or already consumed.", code);
            return null; // Code doesn't exist or already consumed
        }

        long now = System.currentTimeMillis();
        if (now > entry.expiryTime) {
            logger.warn("InMemoryDiscordCodeStore: Consume failed - code [{}] for user ID [{}] expired. Expiry: {}, Current: {}", code, entry.userId, entry.expiryTime, now);
            return null; // Code expired
        }

        logger.info("InMemoryDiscordCodeStore: Successfully consumed code [{}] for user ID: [{}]", code, entry.userId);
        return entry.userId; // Code valid and consumed
    }

    private void cleanupExpiredCodes() {
        long now = System.currentTimeMillis();
        int removedCount = 0;
        for (ConcurrentMap.Entry<String, CodeEntry> entry : codeStore.entrySet()) {
            if (now > entry.getValue().expiryTime) {
                if (codeStore.remove(entry.getKey(), entry.getValue())) {
                    removedCount++;
                }
            }
        }
        if (removedCount > 0) {
            logger.debug("Cleaned up {} expired codes.", removedCount);
        }
    }

    // Helper to generate secure codes if needed elsewhere, though OAuthController will generate its own
    public String generateSecureCode() {
        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }
} 