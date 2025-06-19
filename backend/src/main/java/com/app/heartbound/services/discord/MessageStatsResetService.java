package com.app.heartbound.services.discord;

import com.app.heartbound.entities.User;
import com.app.heartbound.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * MessageStatsResetService
 * 
 * Service responsible for resetting time-based message counters at appropriate intervals.
 * Runs scheduled tasks to ensure daily, weekly, and bi-weekly counters are reset properly.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MessageStatsResetService {
    
    private final UserRepository userRepository;
    
    /**
     * Reset daily message counters every day at midnight
     */
    @Scheduled(cron = "0 0 0 * * ?") // Every day at midnight
    @Transactional
    public void resetDailyCounters() {
        log.info("Starting daily message counter reset task");
        LocalDateTime now = LocalDateTime.now();
        
        try {
            List<User> users = userRepository.findAll();
            int resetCount = 0;
            
            for (User user : users) {
                if (shouldResetDailyCounter(user, now)) {
                    user.setMessagesToday(0);
                    user.setLastDailyReset(now);
                    userRepository.save(user);
                    resetCount++;
                }
            }
            
            log.info("Daily message counter reset completed. Reset {} user counters", resetCount);
        } catch (Exception e) {
            log.error("Error during daily message counter reset: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Reset weekly message counters every Monday at midnight
     */
    @Scheduled(cron = "0 0 0 ? * MON") // Every Monday at midnight
    @Transactional
    public void resetWeeklyCounters() {
        log.info("Starting weekly message counter reset task");
        LocalDateTime now = LocalDateTime.now();
        
        try {
            List<User> users = userRepository.findAll();
            int resetCount = 0;
            
            for (User user : users) {
                if (shouldResetWeeklyCounter(user, now)) {
                    user.setMessagesThisWeek(0);
                    user.setLastWeeklyReset(now);
                    userRepository.save(user);
                    resetCount++;
                }
            }
            
            log.info("Weekly message counter reset completed. Reset {} user counters", resetCount);
        } catch (Exception e) {
            log.error("Error during weekly message counter reset: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Check bi-weekly counters and reset as needed (runs daily at 1 AM)
     */
    @Scheduled(cron = "0 0 1 * * ?") // Every day at 1 AM
    @Transactional
    public void checkAndResetBiWeeklyCounters() {
        log.info("Starting bi-weekly message counter check task");
        LocalDateTime now = LocalDateTime.now();
        
        try {
            List<User> users = userRepository.findAll();
            int resetCount = 0;
            
            for (User user : users) {
                if (shouldResetBiWeeklyCounter(user, now)) {
                    user.setMessagesThisTwoWeeks(0);
                    user.setLastBiWeeklyReset(now);
                    userRepository.save(user);
                    resetCount++;
                }
            }
            
            if (resetCount > 0) {
                log.info("Bi-weekly message counter reset completed. Reset {} user counters", resetCount);
            } else {
                log.debug("No bi-weekly counters needed reset");
            }
        } catch (Exception e) {
            log.error("Error during bi-weekly message counter check: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Check if daily counter should be reset (new day)
     */
    private boolean shouldResetDailyCounter(User user, LocalDateTime now) {
        if (user.getLastDailyReset() == null) {
            return true; // First time, needs initialization
        }
        return !user.getLastDailyReset().toLocalDate().equals(now.toLocalDate());
    }
    
    /**
     * Check if weekly counter should be reset (new week - Monday)
     */
    private boolean shouldResetWeeklyCounter(User user, LocalDateTime now) {
        if (user.getLastWeeklyReset() == null) {
            return true; // First time, needs initialization
        }
        
        // Get the start of this week (Monday)
        LocalDateTime startOfThisWeek = now.with(java.time.DayOfWeek.MONDAY).withHour(0).withMinute(0).withSecond(0).withNano(0);
        return user.getLastWeeklyReset().isBefore(startOfThisWeek);
    }
    
    /**
     * Check if bi-weekly counter should be reset (every 2 weeks from last reset)
     */
    private boolean shouldResetBiWeeklyCounter(User user, LocalDateTime now) {
        if (user.getLastBiWeeklyReset() == null) {
            return true; // First time, needs initialization
        }
        
        // Reset every 14 days from the last reset
        return user.getLastBiWeeklyReset().plusDays(14).isBefore(now) || 
               user.getLastBiWeeklyReset().plusDays(14).toLocalDate().equals(now.toLocalDate());
    }
} 