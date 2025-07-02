package com.app.heartbound.services;

import com.app.heartbound.entities.RollAudit;
import com.app.heartbound.repositories.RollAuditRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * RollVerificationService
 * 
 * Provides statistical verification and anomaly detection for the case roll system.
 * Ensures fairness and detects potential exploitation attempts.
 */
@Service
public class RollVerificationService {
    
    private static final Logger logger = LoggerFactory.getLogger(RollVerificationService.class);
    
    @Autowired
    private RollAuditRepository rollAuditRepository;
    
    @Value("${roll.verification.chi-square.threshold:10.83}")
    private double chiSquareThreshold;
    
    @Value("${roll.verification.win-rate.tolerance:5.0}")
    private double winRateTolerance;
    
    @Value("${roll.verification.high-frequency.threshold:50}")
    private long highFrequencyThreshold;
    
    @Value("${roll.verification.pattern.window.hours:24}")
    private int patternWindowHours;
    
    /**
     * Perform comprehensive verification of roll fairness
     */
    @Async
    public CompletableFuture<VerificationResult> performFairnessVerification(LocalDateTime startTime, LocalDateTime endTime) {
        logger.info("Starting fairness verification for period: {} to {}", startTime, endTime);
        
        try {
            VerificationResult result = new VerificationResult();
            
            // 1. Chi-square test for uniform distribution
            ChiSquareResult chiSquareResult = performChiSquareTest(startTime, endTime);
            result.setChiSquareResult(chiSquareResult);
            
            // 2. Win rate analysis
            WinRateAnalysis winRateAnalysis = analyzeWinRates(startTime, endTime);
            result.setWinRateAnalysis(winRateAnalysis);
            
            // 3. Pattern detection
            PatternAnalysis patternAnalysis = detectSuspiciousPatterns(startTime, endTime);
            result.setPatternAnalysis(patternAnalysis);
            
            // 4. Frequency analysis
            FrequencyAnalysis frequencyAnalysis = analyzeRollFrequency(startTime, endTime);
            result.setFrequencyAnalysis(frequencyAnalysis);
            
            // 5. Overall assessment
            result.setOverallFairness(calculateOverallFairness(result));
            result.setVerificationTimestamp(LocalDateTime.now());
            
            logger.info("Fairness verification completed. Overall fairness: {}", result.getOverallFairness().getLevel());
            
            return CompletableFuture.completedFuture(result);
            
        } catch (Exception e) {
            logger.error("Error during fairness verification: {}", e.getMessage(), e);
            return CompletableFuture.completedFuture(VerificationResult.error(e.getMessage()));
        }
    }
    
    /**
     * Perform Chi-square test for uniform distribution of roll values
     */
    private ChiSquareResult performChiSquareTest(LocalDateTime startTime, LocalDateTime endTime) {
        logger.debug("Performing Chi-square test for roll distribution");
        
        List<Object[]> rollDistribution = rollAuditRepository.getRollDistribution(startTime, endTime);
        
        if (rollDistribution.size() < 10) {
            return new ChiSquareResult(0.0, 0.0, false, "Insufficient data for Chi-square test");
        }
        
        // Calculate expected frequency (should be uniform for 0-99)
        long totalRolls = rollDistribution.stream()
            .mapToLong(row -> (Long) row[1])
            .sum();
        
        double expectedFrequency = totalRolls / 100.0;
        double chiSquareStatistic = 0.0;
        
        // Create frequency map
        Map<Integer, Long> frequencies = rollDistribution.stream()
            .collect(Collectors.toMap(
                row -> (Integer) row[0],
                row -> (Long) row[1]
            ));
        
        // Calculate Chi-square statistic
        for (int i = 0; i < 100; i++) {
            long observedFrequency = frequencies.getOrDefault(i, 0L);
            double difference = observedFrequency - expectedFrequency;
            chiSquareStatistic += (difference * difference) / expectedFrequency;
        }
        
        boolean isPassing = chiSquareStatistic <= chiSquareThreshold;
        String analysis = String.format("Chi-square statistic: %.2f, Threshold: %.2f, Total rolls: %d", 
                                       chiSquareStatistic, chiSquareThreshold, totalRolls);
        
        logger.debug("Chi-square test result: {} ({})", isPassing ? "PASS" : "FAIL", analysis);
        
        return new ChiSquareResult(chiSquareStatistic, chiSquareThreshold, isPassing, analysis);
    }
    
    /**
     * Analyze win rates against expected drop rates
     */
    private WinRateAnalysis analyzeWinRates(LocalDateTime startTime, LocalDateTime endTime) {
        logger.debug("Analyzing win rates for fairness");
        
        List<Object[]> anomalousWinRates = rollAuditRepository.getAnomalousWinRates(
            startTime, endTime, winRateTolerance);
        
        List<WinRateAnomaly> anomalies = anomalousWinRates.stream()
            .map(row -> new WinRateAnomaly(
                (UUID) row[0],     // caseId
                (String) row[1],   // caseName
                (UUID) row[2],     // wonItemId
                (String) row[3],   // wonItemName
                ((Number) row[4]).longValue(), // winCount
                (Integer) row[5]   // dropRate
            ))
            .collect(Collectors.toList());
        
        boolean isPassing = anomalies.isEmpty();
        String summary = String.format("Found %d anomalous win rates (tolerance: %.1f%%)", 
                                      anomalies.size(), winRateTolerance);
        
        logger.debug("Win rate analysis: {} anomalies found", anomalies.size());
        
        return new WinRateAnalysis(anomalies, isPassing, summary);
    }
    
    /**
     * Detect suspicious patterns in user rolling behavior
     */
    private PatternAnalysis detectSuspiciousPatterns(LocalDateTime startTime, LocalDateTime endTime) {
        logger.debug("Detecting suspicious rolling patterns");
        
        List<Object[]> highFrequencyUsers = rollAuditRepository.getHighFrequencyUsers(
            startTime, endTime, highFrequencyThreshold);
        
        List<SuspiciousPattern> patterns = new ArrayList<>();
        
        for (Object[] row : highFrequencyUsers) {
            String userId = (String) row[0];
            long rollCount = ((Number) row[1]).longValue();
            
            // Analyze timing patterns for this user
            List<RollAudit> userRolls = rollAuditRepository.getUserRollsInTimeframe(
                userId, startTime, endTime);
            
            SuspiciousPattern pattern = analyzeUserRollPattern(userId, userRolls, rollCount);
            if (pattern != null) {
                patterns.add(pattern);
            }
        }
        
        boolean isPassing = patterns.isEmpty();
        String summary = String.format("Detected %d suspicious patterns", patterns.size());
        
        logger.debug("Pattern analysis: {} suspicious patterns detected", patterns.size());
        
        return new PatternAnalysis(patterns, isPassing, summary);
    }
    
    /**
     * Analyze individual user roll patterns
     */
    private SuspiciousPattern analyzeUserRollPattern(String userId, List<RollAudit> userRolls, long rollCount) {
        if (userRolls.size() < 10) {
            return null; // Not enough data
        }
        
        // Check for extremely rapid rolling (potential bot behavior)
        List<Long> timeDifferences = new ArrayList<>();
        for (int i = 1; i < userRolls.size(); i++) {
            LocalDateTime prev = userRolls.get(i - 1).getRollTimestamp();
            LocalDateTime curr = userRolls.get(i).getRollTimestamp();
            long diffSeconds = java.time.Duration.between(prev, curr).getSeconds();
            timeDifferences.add(diffSeconds);
        }
        
        // Check for unusually consistent timing (bot-like behavior)
        double avgTimeDiff = timeDifferences.stream().mapToLong(Long::longValue).average().orElse(0.0);
        double variance = timeDifferences.stream()
            .mapToDouble(diff -> Math.pow(diff - avgTimeDiff, 2))
            .average().orElse(0.0);
        double stdDeviation = Math.sqrt(variance);
        
        List<String> suspiciousIndicators = new ArrayList<>();
        
        // Bot-like consistent timing
        if (stdDeviation < 2.0 && avgTimeDiff < 10.0) {
            suspiciousIndicators.add("Extremely consistent timing (possible bot)");
        }
        
        // Extremely high frequency
        if (rollCount > highFrequencyThreshold * 2) {
            suspiciousIndicators.add("Abnormally high roll frequency");
        }
        
        // Check for unusual win patterns
        long winCount = userRolls.stream()
            .mapToLong(roll -> roll.getAlreadyOwned() ? 0 : 1)
            .sum();
        double winRate = (double) winCount / userRolls.size();
        
        if (winRate > 0.8) { // Unusually high win rate
            suspiciousIndicators.add("Suspiciously high win rate");
        }
        
        if (!suspiciousIndicators.isEmpty()) {
            return new SuspiciousPattern(userId, rollCount, avgTimeDiff, stdDeviation, 
                                       winRate, suspiciousIndicators);
        }
        
        return null;
    }
    
    /**
     * Analyze overall roll frequency
     */
    private FrequencyAnalysis analyzeRollFrequency(LocalDateTime startTime, LocalDateTime endTime) {
        logger.debug("Analyzing roll frequency");
        
        long totalRolls = rollAuditRepository.getRollsCountSince(startTime);
        long hoursInPeriod = java.time.Duration.between(startTime, endTime).toHours();
        double avgRollsPerHour = hoursInPeriod > 0 ? (double) totalRolls / hoursInPeriod : 0.0;
        
        // Get hourly breakdown
        Map<Integer, Long> hourlyDistribution = new HashMap<>();
        List<RollAudit> allRolls = rollAuditRepository.findByRollTimestampBetweenOrderByRollTimestampDesc(
            startTime, endTime);
        
        for (RollAudit roll : allRolls) {
            int hour = roll.getRollTimestamp().getHour();
            hourlyDistribution.merge(hour, 1L, Long::sum);
        }
        
        return new FrequencyAnalysis(totalRolls, avgRollsPerHour, hourlyDistribution);
    }
    
    /**
     * Calculate overall fairness score
     */
    private FairnessScore calculateOverallFairness(VerificationResult result) {
        int passedTests = 0;
        int totalTests = 0;
        
        List<String> issues = new ArrayList<>();
        
        // Chi-square test
        totalTests++;
        if (result.getChiSquareResult().isPassing()) {
            passedTests++;
        } else {
            issues.add("Chi-square test failed - distribution not uniform");
        }
        
        // Win rate analysis
        totalTests++;
        if (result.getWinRateAnalysis().isPassing()) {
            passedTests++;
        } else {
            issues.add("Win rate anomalies detected");
        }
        
        // Pattern analysis
        totalTests++;
        if (result.getPatternAnalysis().isPassing()) {
            passedTests++;
        } else {
            issues.add("Suspicious user patterns detected");
        }
        
        double score = (double) passedTests / totalTests * 100.0;
        FairnessLevel level;
        
        if (score >= 100.0) {
            level = FairnessLevel.EXCELLENT;
        } else if (score >= 75.0) {
            level = FairnessLevel.GOOD;
        } else if (score >= 50.0) {
            level = FairnessLevel.CONCERNING;
        } else {
            level = FairnessLevel.CRITICAL;
        }
        
        return new FairnessScore(score, level, passedTests, totalTests, issues);
    }
    
    /**
     * Generate statistical hash for roll verification
     */
    public String generateStatisticalHash(RollAudit rollAudit) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String data = String.format("%s-%s-%d-%d-%s",
                rollAudit.getUserId(),
                rollAudit.getCaseId(),
                rollAudit.getRollValue(),
                rollAudit.getRollTimestamp().toEpochSecond(java.time.ZoneOffset.UTC),
                rollAudit.getRollSeedHash()
            );
            
            byte[] hash = digest.digest(data.getBytes());
            return Base64.getEncoder().encodeToString(hash);
            
        } catch (NoSuchAlgorithmException e) {
            logger.error("Failed to generate statistical hash: {}", e.getMessage());
            return "";
        }
    }
    
    // Inner classes for result structures
    
    public static class VerificationResult {
        private ChiSquareResult chiSquareResult;
        private WinRateAnalysis winRateAnalysis;
        private PatternAnalysis patternAnalysis;
        private FrequencyAnalysis frequencyAnalysis;
        private FairnessScore overallFairness;
        private LocalDateTime verificationTimestamp;
        private String errorMessage;
        
        public static VerificationResult error(String message) {
            VerificationResult result = new VerificationResult();
            result.errorMessage = message;
            return result;
        }
        
        // Getters and setters
        public ChiSquareResult getChiSquareResult() { return chiSquareResult; }
        public void setChiSquareResult(ChiSquareResult chiSquareResult) { this.chiSquareResult = chiSquareResult; }
        
        public WinRateAnalysis getWinRateAnalysis() { return winRateAnalysis; }
        public void setWinRateAnalysis(WinRateAnalysis winRateAnalysis) { this.winRateAnalysis = winRateAnalysis; }
        
        public PatternAnalysis getPatternAnalysis() { return patternAnalysis; }
        public void setPatternAnalysis(PatternAnalysis patternAnalysis) { this.patternAnalysis = patternAnalysis; }
        
        public FrequencyAnalysis getFrequencyAnalysis() { return frequencyAnalysis; }
        public void setFrequencyAnalysis(FrequencyAnalysis frequencyAnalysis) { this.frequencyAnalysis = frequencyAnalysis; }
        
        public FairnessScore getOverallFairness() { return overallFairness; }
        public void setOverallFairness(FairnessScore overallFairness) { this.overallFairness = overallFairness; }
        
        public LocalDateTime getVerificationTimestamp() { return verificationTimestamp; }
        public void setVerificationTimestamp(LocalDateTime verificationTimestamp) { this.verificationTimestamp = verificationTimestamp; }
        
        public String getErrorMessage() { return errorMessage; }
    }
    
    public static class ChiSquareResult {
        private final double statistic;
        private final double threshold;
        private final boolean passing;
        private final String analysis;
        
        public ChiSquareResult(double statistic, double threshold, boolean passing, String analysis) {
            this.statistic = statistic;
            this.threshold = threshold;
            this.passing = passing;
            this.analysis = analysis;
        }
        
        public double getStatistic() { return statistic; }
        public double getThreshold() { return threshold; }
        public boolean isPassing() { return passing; }
        public String getAnalysis() { return analysis; }
    }
    
    public static class WinRateAnalysis {
        private final List<WinRateAnomaly> anomalies;
        private final boolean passing;
        private final String summary;
        
        public WinRateAnalysis(List<WinRateAnomaly> anomalies, boolean passing, String summary) {
            this.anomalies = anomalies;
            this.passing = passing;
            this.summary = summary;
        }
        
        public List<WinRateAnomaly> getAnomalies() { return anomalies; }
        public boolean isPassing() { return passing; }
        public String getSummary() { return summary; }
    }
    
    public static class WinRateAnomaly {
        private final UUID caseId;
        private final String caseName;
        private final UUID wonItemId;
        private final String wonItemName;
        private final long winCount;
        private final int expectedDropRate;
        
        public WinRateAnomaly(UUID caseId, String caseName, UUID wonItemId, 
                            String wonItemName, long winCount, int expectedDropRate) {
            this.caseId = caseId;
            this.caseName = caseName;
            this.wonItemId = wonItemId;
            this.wonItemName = wonItemName;
            this.winCount = winCount;
            this.expectedDropRate = expectedDropRate;
        }
        
        public UUID getCaseId() { return caseId; }
        public String getCaseName() { return caseName; }
        public UUID getWonItemId() { return wonItemId; }
        public String getWonItemName() { return wonItemName; }
        public long getWinCount() { return winCount; }
        public int getExpectedDropRate() { return expectedDropRate; }
    }
    
    public static class PatternAnalysis {
        private final List<SuspiciousPattern> patterns;
        private final boolean passing;
        private final String summary;
        
        public PatternAnalysis(List<SuspiciousPattern> patterns, boolean passing, String summary) {
            this.patterns = patterns;
            this.passing = passing;
            this.summary = summary;
        }
        
        public List<SuspiciousPattern> getPatterns() { return patterns; }
        public boolean isPassing() { return passing; }
        public String getSummary() { return summary; }
    }
    
    public static class SuspiciousPattern {
        private final String userId;
        private final long rollCount;
        private final double avgTimeBetweenRolls;
        private final double timingVariance;
        private final double winRate;
        private final List<String> indicators;
        
        public SuspiciousPattern(String userId, long rollCount, double avgTimeBetweenRolls, 
                               double timingVariance, double winRate, List<String> indicators) {
            this.userId = userId;
            this.rollCount = rollCount;
            this.avgTimeBetweenRolls = avgTimeBetweenRolls;
            this.timingVariance = timingVariance;
            this.winRate = winRate;
            this.indicators = indicators;
        }
        
        public String getUserId() { return userId; }
        public long getRollCount() { return rollCount; }
        public double getAvgTimeBetweenRolls() { return avgTimeBetweenRolls; }
        public double getTimingVariance() { return timingVariance; }
        public double getWinRate() { return winRate; }
        public List<String> getIndicators() { return indicators; }
    }
    
    public static class FrequencyAnalysis {
        private final long totalRolls;
        private final double avgRollsPerHour;
        private final Map<Integer, Long> hourlyDistribution;
        
        public FrequencyAnalysis(long totalRolls, double avgRollsPerHour, Map<Integer, Long> hourlyDistribution) {
            this.totalRolls = totalRolls;
            this.avgRollsPerHour = avgRollsPerHour;
            this.hourlyDistribution = hourlyDistribution;
        }
        
        public long getTotalRolls() { return totalRolls; }
        public double getAvgRollsPerHour() { return avgRollsPerHour; }
        public Map<Integer, Long> getHourlyDistribution() { return hourlyDistribution; }
    }
    
    public static class FairnessScore {
        private final double score;
        private final FairnessLevel level;
        private final int passedTests;
        private final int totalTests;
        private final List<String> issues;
        
        public FairnessScore(double score, FairnessLevel level, int passedTests, int totalTests, List<String> issues) {
            this.score = score;
            this.level = level;
            this.passedTests = passedTests;
            this.totalTests = totalTests;
            this.issues = issues;
        }
        
        public double getScore() { return score; }
        public FairnessLevel getLevel() { return level; }
        public int getPassedTests() { return passedTests; }
        public int getTotalTests() { return totalTests; }
        public List<String> getIssues() { return issues; }
    }
    
    public enum FairnessLevel {
        EXCELLENT, GOOD, CONCERNING, CRITICAL
    }
} 