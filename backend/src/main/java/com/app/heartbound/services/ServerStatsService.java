package com.app.heartbound.services;

import com.app.heartbound.dto.DailyActivityDataDTO;
import com.app.heartbound.dto.LeaderboardEntryDTO;
import com.app.heartbound.dto.ServerStatsDTO;
import com.app.heartbound.dto.UserProfileDTO;
import com.app.heartbound.entities.User;
import com.app.heartbound.repositories.DailyMessageStatRepository;
import com.app.heartbound.repositories.DailyVoiceActivityStatRepository;
import com.app.heartbound.repositories.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
@Transactional(readOnly = true)
public class ServerStatsService {
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private DailyMessageStatRepository dailyMessageStatRepository;
    
    @Autowired
    private DailyVoiceActivityStatRepository dailyVoiceActivityStatRepository;
    
    @Autowired
    private UserService userService;
    
    public ServerStatsDTO getUserStats(String userId) {
        log.info("Fetching statistics for user: {}", userId);
        
        ServerStatsDTO stats = new ServerStatsDTO();
        
        try {
            // Get user profile
            UserProfileDTO userProfile = userService.mapToProfileDTO(userService.getUserById(userId));
            
            if (userProfile == null) {
                log.warn("User {} not found", userId);
                return stats;
            }
            
            // Get total user statistics
            stats.setTotalMessages(userProfile.getMessageCount() != null ? userProfile.getMessageCount() : 0);
            stats.setTotalVoiceMinutes(userProfile.getVoiceTimeMinutesTotal() != null ? userProfile.getVoiceTimeMinutesTotal() : 0);
            stats.setTotalFishCaught(userProfile.getFishCaughtCount() != null ? userProfile.getFishCaughtCount() : 0);
            
            // Get time-windowed statistics from the user profile
            stats.setMessagesToday(userProfile.getMessagesToday() != null ? userProfile.getMessagesToday() : 0);
            stats.setMessagesThisWeek(userProfile.getMessagesThisWeek() != null ? userProfile.getMessagesThisWeek() : 0);
            stats.setMessagesThisTwoWeeks(userProfile.getMessagesThisTwoWeeks() != null ? userProfile.getMessagesThisTwoWeeks() : 0);
            
            stats.setVoiceMinutesToday(userProfile.getVoiceTimeMinutesToday() != null ? userProfile.getVoiceTimeMinutesToday() : 0);
            stats.setVoiceMinutesThisWeek(userProfile.getVoiceTimeMinutesThisWeek() != null ? userProfile.getVoiceTimeMinutesThisWeek() : 0);
            stats.setVoiceMinutesThisTwoWeeks(userProfile.getVoiceTimeMinutesThisTwoWeeks() != null ? userProfile.getVoiceTimeMinutesThisTwoWeeks() : 0);
            
            // Get user's rank in leaderboards
            List<LeaderboardEntryDTO> messageLeaderboard = userService.getLeaderboardUsers("messages");
            int messageRank = messageLeaderboard.stream()
                .map(LeaderboardEntryDTO::getId)
                .collect(Collectors.toList())
                .indexOf(userId) + 1;
            stats.setTopMessageUser(messageRank > 0 ? "#" + messageRank : "Unranked");
            
            List<LeaderboardEntryDTO> voiceLeaderboard = userService.getLeaderboardUsers("voice");
            int voiceRank = voiceLeaderboard.stream()
                .map(LeaderboardEntryDTO::getId)
                .collect(Collectors.toList())
                .indexOf(userId) + 1;
            stats.setTopVoiceUser(voiceRank > 0 ? "#" + voiceRank : "Unranked");
            
            // Get 30-day activity chart data for this user
            List<DailyActivityDataDTO> messageActivity = userService.getUserDailyActivity(userId, 30);
            List<DailyActivityDataDTO> voiceActivity = userService.getUserDailyVoiceActivity(userId, 30);
            
            stats.setDailyActivity(combineDailyActivity(messageActivity, voiceActivity));
            
            log.info("Successfully fetched user statistics for {}", userId);
            
        } catch (Exception e) {
            log.error("Error fetching user statistics for {}: {}", userId, e.getMessage(), e);
            // Return partially populated stats rather than failing completely
        }
        
        return stats;
    }
    
    private List<ServerStatsDTO.DailyActivity> combineDailyActivity(
            List<DailyActivityDataDTO> messageActivity,
            List<DailyActivityDataDTO> voiceActivity) {
        
        List<ServerStatsDTO.DailyActivity> combined = new ArrayList<>();
        
        // Create a map for easy lookup of voice data
        var voiceMap = voiceActivity.stream()
            .collect(Collectors.toMap(DailyActivityDataDTO::getDate, DailyActivityDataDTO::getCount));
        
        // Combine message and voice data
        for (DailyActivityDataDTO msgData : messageActivity) {
            String date = msgData.getDate();
            long messages = msgData.getCount() != null ? msgData.getCount() : 0;
            long voiceMinutes = voiceMap.getOrDefault(date, 0L);
            
            combined.add(new ServerStatsDTO.DailyActivity(date, messages, voiceMinutes));
        }
        
        // Add any voice-only dates (days with voice but no messages)
        for (DailyActivityDataDTO voiceData : voiceActivity) {
            String date = voiceData.getDate();
            boolean dateExists = combined.stream().anyMatch(d -> d.getDate().equals(date));
            if (!dateExists) {
                long voiceMinutes = voiceData.getCount() != null ? voiceData.getCount() : 0;
                combined.add(new ServerStatsDTO.DailyActivity(date, 0, voiceMinutes));
            }
        }
        
        // Sort by date
        combined.sort((a, b) -> a.getDate().compareTo(b.getDate()));
        
        return combined;
    }
} 