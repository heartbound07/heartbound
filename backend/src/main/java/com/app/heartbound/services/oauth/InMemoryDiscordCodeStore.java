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
        codeStore.put(code, new CodeEntry(userId, expiryTime));
        logger.debug("Stored single-use code for user ID: {}", userId);
    }

    @Override
    public String consumeCode(String code) {
        CodeEntry entry = codeStore.remove(code); // Atomically remove the code

        if (entry == null) {
            logger.warn("Attempted to consume non-existent code: {}", code);
            return null; // Code doesn't exist or already consumed
        }

        if (System.currentTimeMillis() > entry.expiryTime) {
            logger.warn("Attempted to consume expired code for user ID: {}", entry.userId);
            // Even though removed, log that it was expired
            return null; // Code expired
        }

        logger.info("Successfully consumed code for user ID: {}", entry.userId);
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