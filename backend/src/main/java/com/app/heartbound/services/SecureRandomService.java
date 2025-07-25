package com.app.heartbound.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SecureRandom;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * SecureRandomService
 * 
 * Provides cryptographically secure random number generation for all gambling/loot mechanics.
 * Uses SecureRandom with proper entropy sources and implements performance optimizations.
 */
@Service
public class SecureRandomService {
    
    private static final Logger logger = LoggerFactory.getLogger(SecureRandomService.class);
    
    @Value("${secure.random.algorithm:SHA1PRNG}")
    private String algorithm;
    
    @Value("${secure.random.provider:SUN}")
    private String provider;
    
    @Value("${secure.random.seed.refresh.interval:3600000}")
    private long seedRefreshInterval;
    
    @Value("${secure.random.pool.size:1000}")
    private int poolSize;
    
    private SecureRandom secureRandom;
    private final ConcurrentLinkedQueue<Integer> randomPool = new ConcurrentLinkedQueue<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private final AtomicLong operationsCount = new AtomicLong(0);
    private final AtomicLong poolRefreshCount = new AtomicLong(0);
    
    @PostConstruct
    public void initialize() {
        try {
            // Initialize SecureRandom with specified algorithm and provider
            if (provider != null && !provider.isEmpty()) {
                secureRandom = SecureRandom.getInstance(algorithm, provider);
            } else {
                secureRandom = SecureRandom.getInstance(algorithm);
            }
            
            // Force initial seeding
            secureRandom.nextBytes(new byte[20]);
            
            logger.info("SecureRandomService initialized with algorithm: {}, provider: {}", 
                       algorithm, provider != null ? provider : "default");
            
            // Pre-populate the random pool
            refillRandomPool();
            
            // Schedule periodic seed refresh and pool refill
            scheduler.scheduleAtFixedRate(this::refreshSeed, seedRefreshInterval, 
                                        seedRefreshInterval, TimeUnit.MILLISECONDS);
            
            scheduler.scheduleAtFixedRate(this::refillRandomPool, 60000, 60000, TimeUnit.MILLISECONDS);
            
        } catch (NoSuchAlgorithmException | NoSuchProviderException e) {
            logger.error("Failed to initialize SecureRandom with algorithm: {}, provider: {}", 
                        algorithm, provider, e);
            // Fallback to default SecureRandom
            secureRandom = new SecureRandom();
            logger.warn("Using default SecureRandom as fallback");
        }
    }
    
    /**
     * Generate a cryptographically secure random integer within the specified bound
     * @param bound The upper bound (exclusive)
     * @return Secure random integer between 0 (inclusive) and bound (exclusive)
     */
    public int getSecureInt(int bound) {
        if (bound <= 0) {
            throw new IllegalArgumentException("Bound must be positive");
        }
        
        operationsCount.incrementAndGet();
        
        // The effective upper bound of pooled numbers is Integer.MAX_VALUE
        int maxPooledValue = Integer.MAX_VALUE;
        // The threshold for rejection sampling. Values >= threshold will be rejected.
        int threshold = maxPooledValue - (maxPooledValue % bound);

        while (true) {
            Integer pooledRandom = randomPool.poll();
            if (pooledRandom != null) {
                // Perform rejection sampling on the pooled value to avoid modulo bias.
                if (pooledRandom < threshold) {
                    return pooledRandom % bound;
                }
                // If the value is in the biased range (>= threshold), it's rejected.
                // The loop continues to poll the next value.
            } else {
                // If the pool is exhausted, fall back to direct, secure generation.
                logger.warn("SecureRandomService pool is empty, falling back to direct generation.");
                return secureRandom.nextInt(bound);
            }
        }
    }
    
    /**
     * Generate a cryptographically secure random double between 0.0 and 1.0
     * @return Secure random double
     */
    public double getSecureDouble() {
        operationsCount.incrementAndGet();
        return secureRandom.nextDouble();
    }

    public SecureRandom getSecureRandom() {
        return secureRandom;
    }
    
    /**
     * Perform weighted random selection from a list of items
     * @param items List of items with weights (must sum to totalWeight)
     * @param totalWeight Total weight of all items
     * @param weightExtractor Function to extract weight from item
     * @return Selected item
     */
    public <T> T selectWeightedRandom(List<T> items, int totalWeight, 
                                     java.util.function.ToIntFunction<T> weightExtractor) {
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("Items list cannot be null or empty");
        }
        
        if (totalWeight <= 0) {
            throw new IllegalArgumentException("Total weight must be positive");
        }
        
        int roll = getSecureInt(totalWeight);
        int cumulative = 0;
        
        for (T item : items) {
            cumulative += weightExtractor.applyAsInt(item);
            if (roll < cumulative) {
                logger.debug("Weighted selection: roll={}, cumulative={}, selected item", roll, cumulative);
                return item;
            }
        }
        
        // Fallback to last item (should never happen with valid weights)
        logger.warn("Weighted selection fell through, returning last item. Roll: {}, Total: {}", 
                   roll, totalWeight);
        return items.get(items.size() - 1);
    }
    
    /**
     * Perform weighted random selection from a list of items using a pre-generated roll value
     * This overload is used for animation synchronization to ensure frontend and backend use the same roll
     * @param items List of items with weights (must sum to totalWeight)
     * @param totalWeight Total weight of all items
     * @param rollValue Pre-generated roll value (0 to totalWeight-1)
     * @param weightExtractor Function to extract weight from item
     * @return Selected item
     */
    public <T> T selectWeightedRandom(List<T> items, int totalWeight, int rollValue,
                                     java.util.function.ToIntFunction<T> weightExtractor) {
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("Items list cannot be null or empty");
        }
        
        if (totalWeight <= 0) {
            throw new IllegalArgumentException("Total weight must be positive");
        }
        
        if (rollValue < 0 || rollValue >= totalWeight) {
            throw new IllegalArgumentException("Roll value must be between 0 and " + (totalWeight - 1));
        }
        
        operationsCount.incrementAndGet(); // Count this as a random operation for metrics
        
        int cumulative = 0;
        
        for (T item : items) {
            cumulative += weightExtractor.applyAsInt(item);
            if (rollValue < cumulative) {
                logger.debug("Weighted selection with pre-generated roll: roll={}, cumulative={}, selected item", 
                           rollValue, cumulative);
                return item;
            }
        }
        
        // Fallback to last item (should never happen with valid weights)
        logger.warn("Weighted selection with pre-generated roll fell through, returning last item. Roll: {}, Total: {}", 
                   rollValue, totalWeight);
        return items.get(items.size() - 1);
    }
    
    /**
     * Generate a secure random seed for roll verification
     * @return Base64 encoded random seed
     */
    public String generateRollSeed() {
        byte[] seedBytes = new byte[32]; // 256-bit seed
        secureRandom.nextBytes(seedBytes);
        return java.util.Base64.getEncoder().encodeToString(seedBytes);
    }
    
    /**
     * Generate random bytes for cryptographic operations
     * @param length Number of bytes to generate
     * @return Array of random bytes
     */
    public byte[] getSecureBytes(int length) {
        if (length <= 0) {
            throw new IllegalArgumentException("Length must be positive");
        }
        
        byte[] bytes = new byte[length];
        secureRandom.nextBytes(bytes);
        operationsCount.incrementAndGet();
        return bytes;
    }
    
    /**
     * Get service statistics for monitoring
     * @return ServiceStats object with current metrics
     */
    public ServiceStats getServiceStats() {
        return new ServiceStats(
            operationsCount.get(),
            poolRefreshCount.get(),
            randomPool.size(),
            algorithm,
            provider != null ? provider : "default"
        );
    }
    
    /**
     * Refresh the SecureRandom seed to maintain entropy
     */
    private void refreshSeed() {
        try {
            // Re-seed the SecureRandom instance
            secureRandom.setSeed(secureRandom.generateSeed(32));
            logger.debug("SecureRandom seed refreshed");
        } catch (Exception e) {
            logger.warn("Failed to refresh SecureRandom seed: {}", e.getMessage());
        }
    }
    
    /**
     * Refill the random number pool for performance optimization
     */
    private void refillRandomPool() {
        try {
            // Only refill if pool is below half capacity
            int currentSize = randomPool.size();
            if (currentSize < poolSize / 2) {
                int toGenerate = poolSize - currentSize;
                
                for (int i = 0; i < toGenerate; i++) {
                    // Generate random integers with full range
                    randomPool.offer(secureRandom.nextInt(Integer.MAX_VALUE));
                }
                
                poolRefreshCount.incrementAndGet();
                logger.debug("Random pool refilled: {} -> {} entries", currentSize, randomPool.size());
            }
        } catch (Exception e) {
            logger.warn("Failed to refill random pool: {}", e.getMessage());
        }
    }
    
    /**
     * Service statistics class for monitoring
     */
    public static class ServiceStats {
        private final long operationsCount;
        private final long poolRefreshCount;
        private final int currentPoolSize;
        private final String algorithm;
        private final String provider;
        
        public ServiceStats(long operationsCount, long poolRefreshCount, int currentPoolSize, 
                          String algorithm, String provider) {
            this.operationsCount = operationsCount;
            this.poolRefreshCount = poolRefreshCount;
            this.currentPoolSize = currentPoolSize;
            this.algorithm = algorithm;
            this.provider = provider;
        }
        
        public long getOperationsCount() { return operationsCount; }
        public long getPoolRefreshCount() { return poolRefreshCount; }
        public int getCurrentPoolSize() { return currentPoolSize; }
        public String getAlgorithm() { return algorithm; }
        public String getProvider() { return provider; }
    }
} 