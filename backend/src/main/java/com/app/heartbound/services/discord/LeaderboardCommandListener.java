package com.app.heartbound.services.discord;

import com.app.heartbound.dto.UserProfileDTO;
import com.app.heartbound.services.UserService;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.util.Comparator;
import java.util.List;

@Component
public class LeaderboardCommandListener extends ListenerAdapter {
    
    private static final Logger logger = LoggerFactory.getLogger(LeaderboardCommandListener.class);
    private static final int PAGE_SIZE = 10; // Show 10 users per page
    private static final Color EMBED_COLOR = new Color(88, 101, 242); // Discord Blurple

    private final UserService userService;
    
    @Value("${frontend.base.url}")
    private String frontendBaseUrl;

    @Autowired
    public LeaderboardCommandListener(UserService userService) {
        this.userService = userService;
        logger.info("LeaderboardCommandListener initialized");
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!event.getName().equals("leaderboard")) {
            return; // Not our command
        }

        logger.debug("Leaderboard command received from user: {}", event.getUser().getId());
        
        // Get the user ID who executed the command for security validation
        String commandUserId = event.getUser().getId();
        
        // Get the leaderboard type (levels, credits, messages, or voice)
        OptionMapping typeOption = event.getOption("type");
        String leaderboardType = typeOption == null ? "levels" : typeOption.getAsString();
        
        // Acknowledge the interaction quickly to prevent timeout
        event.deferReply().queue();
        
        try {
            // Fetch the full leaderboard from UserService
            List<UserProfileDTO> leaderboardUsers = userService.getLeaderboardUsers(leaderboardType);
            
            if (leaderboardUsers == null || leaderboardUsers.isEmpty()) {
                // Handle empty leaderboard
                event.getHook().sendMessage("No users found for the leaderboard.").setEphemeral(true).queue();
                return;
            }
            
            // Sort based on the selected type
            if ("levels".equals(leaderboardType)) {
                // Sort by level (desc), then by experience (desc) with null-safe comparison
                leaderboardUsers.sort((a, b) -> {
                    Integer levelA = a.getLevel() != null ? a.getLevel() : 1;
                    Integer levelB = b.getLevel() != null ? b.getLevel() : 1;
                    
                    int levelCompare = levelB.compareTo(levelA); // Descending order
                    if (levelCompare != 0) {
                        return levelCompare;
                    }
                    
                    Integer xpA = a.getExperience() != null ? a.getExperience() : 0;
                    Integer xpB = b.getExperience() != null ? b.getExperience() : 0;
                    return xpB.compareTo(xpA); // Descending order
                });
                
                logger.debug("[LEADERBOARD DEBUG] Sorted by levels. Top user: {}(Lvl {}, XP {})", 
                           !leaderboardUsers.isEmpty() ? leaderboardUsers.get(0).getUsername() : "none",
                           !leaderboardUsers.isEmpty() ? (leaderboardUsers.get(0).getLevel() != null ? leaderboardUsers.get(0).getLevel() : 1) : 0,
                           !leaderboardUsers.isEmpty() ? (leaderboardUsers.get(0).getExperience() != null ? leaderboardUsers.get(0).getExperience() : 0) : 0);
            } else if ("credits".equals(leaderboardType)) {
                // Sort by credits descending (already done in the service, but let's ensure it)
                leaderboardUsers.sort(
                    Comparator.comparing(UserProfileDTO::getCredits, Comparator.nullsFirst(Comparator.reverseOrder()))
                );
                logger.debug("[LEADERBOARD DEBUG] Sorted by credits. Top user: {}({} credits)", 
                           !leaderboardUsers.isEmpty() ? leaderboardUsers.get(0).getUsername() : "none",
                           !leaderboardUsers.isEmpty() ? leaderboardUsers.get(0).getCredits() : 0);
            } else if ("messages".equals(leaderboardType)) {
                // Sort by message count descending with null-safe comparison
                leaderboardUsers.sort((a, b) -> {
                    Long messagesA = a.getMessageCount() != null ? a.getMessageCount() : 0L;
                    Long messagesB = b.getMessageCount() != null ? b.getMessageCount() : 0L;
                    return messagesB.compareTo(messagesA); // Descending order
                });
                logger.debug("[LEADERBOARD DEBUG] Sorted by messages. Top user: {}({} messages)", 
                           !leaderboardUsers.isEmpty() ? leaderboardUsers.get(0).getUsername() : "none",
                           !leaderboardUsers.isEmpty() ? (leaderboardUsers.get(0).getMessageCount() != null ? leaderboardUsers.get(0).getMessageCount() : 0) : 0);
            } else if ("voice".equals(leaderboardType)) {
                // Sort by voice time descending with null-safe comparison
                leaderboardUsers.sort((a, b) -> {
                    Integer voiceA = a.getVoiceTimeMinutesTotal() != null ? a.getVoiceTimeMinutesTotal() : 0;
                    Integer voiceB = b.getVoiceTimeMinutesTotal() != null ? b.getVoiceTimeMinutesTotal() : 0;
                    return voiceB.compareTo(voiceA); // Descending order
                });
                logger.debug("[LEADERBOARD DEBUG] Sorted by voice time. Top user: {}({} minutes)", 
                           !leaderboardUsers.isEmpty() ? leaderboardUsers.get(0).getUsername() : "none",
                           !leaderboardUsers.isEmpty() ? (leaderboardUsers.get(0).getVoiceTimeMinutesTotal() != null ? leaderboardUsers.get(0).getVoiceTimeMinutesTotal() : 0) : 0);
            }
            
            // Calculate total pages
            int totalPages = (int) Math.ceil((double) leaderboardUsers.size() / PAGE_SIZE);
            int currentPage = 1; // Start with page 1
            
            // Build the initial embed for page 1
            MessageEmbed embed = buildLeaderboardEmbed(leaderboardUsers, currentPage, totalPages, leaderboardType);
            
            // Create pagination buttons with user ID included for security
            Button prevButton = Button.secondary("leaderboard_prev:" + leaderboardType + ":1:" + commandUserId, "◀️")
                               .withDisabled(true); // Disabled on page 1
            Button pageIndicator = Button.secondary("leaderboard_page_indicator", "1/" + totalPages)
                               .withDisabled(true); // Always disabled - just an indicator
            Button nextButton = Button.secondary("leaderboard_next:" + leaderboardType + ":1:" + commandUserId, "▶️")
                               .withDisabled(totalPages <= 1); // Disabled if only one page
            
            // Send the initial response with buttons
            event.getHook().sendMessageEmbeds(embed)
                .setActionRow(prevButton, pageIndicator, nextButton)
                .queue(success -> logger.debug("Leaderboard displayed successfully"),
                      error -> logger.error("Failed to send leaderboard", error));
            
        } catch (Exception e) {
            logger.error("Error processing leaderboard command", e);
            event.getHook().sendMessage("An error occurred while fetching the leaderboard data.")
                .setEphemeral(true).queue();
        }
    }
    
    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        String componentId = event.getComponentId();
        
        if (!componentId.startsWith("leaderboard_")) {
            return; // Not our button
        }
        
        try {
            // Parse component ID: format is leaderboard_<direction>:<type>:<page>:<userId>
            String[] parts = componentId.split(":");
            if (parts.length != 4) {
                logger.error("Invalid component ID format: {}", componentId);
                return;
            }
            
            String leaderboardType = parts[1];
            int currentPage = Integer.parseInt(parts[2]);
            String originalUserId = parts[3];
            String interactorId = event.getUser().getId();
            
            // Security check: Only the user who initiated the command can interact with the buttons
            if (!originalUserId.equals(interactorId)) {
                event.reply("Only the user who initiated the command can change the leaderboard page.")
                     .setEphemeral(true)
                     .queue();
                logger.debug("Unauthorized pagination attempt by user {} on command from user {}", 
                           interactorId, originalUserId);
                return;
            }
            
            // User is authorized, acknowledge the button click
            event.deferEdit().queue();
            
            boolean isNext = componentId.startsWith("leaderboard_next:");
            
            // Calculate target page
            int tempTargetPage = isNext ? currentPage + 1 : currentPage - 1;
            
            // Fetch the full leaderboard again
            List<UserProfileDTO> leaderboardUsers = userService.getLeaderboardUsers(leaderboardType);
            
            if (leaderboardUsers == null || leaderboardUsers.isEmpty()) {
                // Handle empty leaderboard (rare edge case)
                event.getHook().editOriginal("No users found for the leaderboard.").queue();
                return;
            }
            
            // Sort based on the selected type
            if ("levels".equals(leaderboardType)) {
                // Sort by level (desc), then by experience (desc) with null-safe comparison
                leaderboardUsers.sort((a, b) -> {
                    Integer levelA = a.getLevel() != null ? a.getLevel() : 1;
                    Integer levelB = b.getLevel() != null ? b.getLevel() : 1;
                    
                    int levelCompare = levelB.compareTo(levelA); // Descending order
                    if (levelCompare != 0) {
                        return levelCompare;
                    }
                    
                    Integer xpA = a.getExperience() != null ? a.getExperience() : 0;
                    Integer xpB = b.getExperience() != null ? b.getExperience() : 0;
                    return xpB.compareTo(xpA); // Descending order
                });
                
                logger.debug("[LEADERBOARD DEBUG] Sorted by levels. Top user: {}(Lvl {}, XP {})", 
                           !leaderboardUsers.isEmpty() ? leaderboardUsers.get(0).getUsername() : "none",
                           !leaderboardUsers.isEmpty() ? (leaderboardUsers.get(0).getLevel() != null ? leaderboardUsers.get(0).getLevel() : 1) : 0,
                           !leaderboardUsers.isEmpty() ? (leaderboardUsers.get(0).getExperience() != null ? leaderboardUsers.get(0).getExperience() : 0) : 0);
            } else if ("credits".equals(leaderboardType)) {
                // Sort by credits descending
                leaderboardUsers.sort(
                    Comparator.comparing(UserProfileDTO::getCredits, Comparator.nullsFirst(Comparator.reverseOrder()))
                );
                logger.debug("[LEADERBOARD DEBUG] Sorted by credits. Top user: {}({} credits)", 
                           !leaderboardUsers.isEmpty() ? leaderboardUsers.get(0).getUsername() : "none",
                           !leaderboardUsers.isEmpty() ? leaderboardUsers.get(0).getCredits() : 0);
            } else if ("messages".equals(leaderboardType)) {
                // Sort by message count descending with null-safe comparison
                leaderboardUsers.sort((a, b) -> {
                    Long messagesA = a.getMessageCount() != null ? a.getMessageCount() : 0L;
                    Long messagesB = b.getMessageCount() != null ? b.getMessageCount() : 0L;
                    return messagesB.compareTo(messagesA); // Descending order
                });
                logger.debug("[LEADERBOARD DEBUG] Sorted by messages. Top user: {}({} messages)", 
                           !leaderboardUsers.isEmpty() ? leaderboardUsers.get(0).getUsername() : "none",
                           !leaderboardUsers.isEmpty() ? (leaderboardUsers.get(0).getMessageCount() != null ? leaderboardUsers.get(0).getMessageCount() : 0) : 0);
            } else if ("voice".equals(leaderboardType)) {
                // Sort by voice time descending with null-safe comparison
                leaderboardUsers.sort((a, b) -> {
                    Integer voiceA = a.getVoiceTimeMinutesTotal() != null ? a.getVoiceTimeMinutesTotal() : 0;
                    Integer voiceB = b.getVoiceTimeMinutesTotal() != null ? b.getVoiceTimeMinutesTotal() : 0;
                    return voiceB.compareTo(voiceA); // Descending order
                });
                logger.debug("[LEADERBOARD DEBUG] Sorted by voice time. Top user: {}({} minutes)", 
                           !leaderboardUsers.isEmpty() ? leaderboardUsers.get(0).getUsername() : "none",
                           !leaderboardUsers.isEmpty() ? (leaderboardUsers.get(0).getVoiceTimeMinutesTotal() != null ? leaderboardUsers.get(0).getVoiceTimeMinutesTotal() : 0) : 0);
            }
            
            // Calculate total pages
            int totalPages = (int) Math.ceil((double) leaderboardUsers.size() / PAGE_SIZE);
            
            // Safety check for valid page number
            if (tempTargetPage < 1) tempTargetPage = 1;
            if (tempTargetPage > totalPages) tempTargetPage = totalPages;
            
            // Create final variable for use in lambda
            final int targetPage = tempTargetPage;
            
            // Build the new embed for the target page
            MessageEmbed embed = buildLeaderboardEmbed(leaderboardUsers, targetPage, totalPages, leaderboardType);
            
            // Create updated pagination buttons with user ID included for security
            Button prevButton = Button.secondary("leaderboard_prev:" + leaderboardType + ":" + targetPage + ":" + originalUserId, "◀️")
                               .withDisabled(targetPage <= 1); // Disabled on page 1
            Button pageIndicator = Button.secondary("leaderboard_page_indicator", targetPage + "/" + totalPages)
                               .withDisabled(true); // Always disabled - just an indicator
            Button nextButton = Button.secondary("leaderboard_next:" + leaderboardType + ":" + targetPage + ":" + originalUserId, "▶️")
                               .withDisabled(targetPage >= totalPages); // Disabled on last page
            
            // Update the original message
            event.getHook().editOriginalEmbeds(embed)
                .setActionRow(prevButton, pageIndicator, nextButton)
                .queue(success -> logger.debug("Pagination updated to page {}", targetPage),
                      error -> logger.error("Failed to update pagination", error));
            
        } catch (Exception e) {
            logger.error("Error processing leaderboard pagination", e);
            // If we haven't deferred yet, we need to respond to avoid timeout
            if (!event.isAcknowledged()) {
                event.reply("An error occurred while processing the pagination request.")
                     .setEphemeral(true)
                     .queue();
            }
        }
    }
    
    /**
     * Format voice time from minutes to readable format (e.g., "2h 30m", "45m")
     * 
     * @param minutes Total minutes of voice time
     * @return Formatted string representation
     */
    private String formatVoiceTime(int minutes) {
        if (minutes == 0) {
            return "0m";
        }
        
        int hours = minutes / 60;
        int remainingMinutes = minutes % 60;
        
        if (hours > 0 && remainingMinutes > 0) {
            return String.format("%dh %dm", hours, remainingMinutes);
        } else if (hours > 0) {
            return String.format("%dh", hours);
        } else {
            return String.format("%dm", remainingMinutes);
        }
    }
    
    /**
     * Builds a Discord embed for displaying a page of the leaderboard.
     *
     * @param users The full list of users sorted by the selected type
     * @param page The current page (1-based)
     * @param totalPages The total number of pages
     * @param type The leaderboard type (levels, credits, messages, or voice)
     * @return A MessageEmbed containing the formatted leaderboard
     */
    private MessageEmbed buildLeaderboardEmbed(List<UserProfileDTO> users, int page, int totalPages, String type) {
        // Calculate start and end indices for the current page
        int startIndex = (page - 1) * PAGE_SIZE;
        int endIndex = Math.min(startIndex + PAGE_SIZE, users.size());
        
        // Get sublist for current page
        List<UserProfileDTO> pageUsers = users.subList(startIndex, endIndex);
        
        // Build the embed
        EmbedBuilder embed = new EmbedBuilder();
        
        // Set title based on leaderboard type
        switch (type) {
            case "levels":
                embed.setTitle("Level Leaderboard");
                break;
            case "credits":
                embed.setTitle("Credit Leaderboard");
                break;
            case "messages":
                embed.setTitle("Message Leaderboard");
                break;
            case "voice":
                embed.setTitle("Voice Time Leaderboard");
                break;
            default:
                embed.setTitle("Leaderboard");
                break;
        }
        
        // Create a clickable link to the web leaderboard
        String leaderboardUrl = frontendBaseUrl + "/leaderboard";
        embed.setDescription("View the [online leaderboard](" + leaderboardUrl + ")");
        embed.setColor(EMBED_COLOR);
        
        // Optional: Set thumbnail to top user's avatar if available
        if (!pageUsers.isEmpty() && pageUsers.get(0).getAvatar() != null) {
            String avatarUrl = pageUsers.get(0).getAvatar();
            if (avatarUrl.startsWith("http")) {
                embed.setThumbnail(avatarUrl);
            }
        }
        
        // Build the formatted leaderboard entries
        StringBuilder content = new StringBuilder();
        for (int i = 0; i < pageUsers.size(); i++) {
            UserProfileDTO user = pageUsers.get(i);
            int rank = startIndex + i + 1; // Calculate the global rank
            
            String displayName = user.getDisplayName() != null && !user.getDisplayName().isEmpty() 
                               ? user.getDisplayName() 
                               : user.getUsername();
            
            // Format each entry with rank, name, and depending on the type, show appropriate value
            // Use medal emojis for top 3 ranks
            String rankDisplay;
            if (rank == 1) {
                rankDisplay = "🥇";
            } else if (rank == 2) {
                rankDisplay = "🥈";
            } else if (rank == 3) {
                rankDisplay = "🥉";
            } else {
                rankDisplay = "**#" + rank + "**";
            }
            
            // Format the value display based on leaderboard type
            String valueDisplay;
            switch (type) {
                case "levels":
                    int level = user.getLevel() != null ? user.getLevel() : 1;
                    int xp = user.getExperience() != null ? user.getExperience() : 0;
                    valueDisplay = String.format("Level %d (%d XP)", level, xp);
                    break;
                case "credits":
                    int credits = user.getCredits() != null ? user.getCredits() : 0;
                    valueDisplay = String.format("%d 🪙", credits);
                    break;
                case "messages":
                    long messageCount = user.getMessageCount() != null ? user.getMessageCount() : 0L;
                    valueDisplay = String.format("%,d 📝", messageCount);
                    break;
                case "voice":
                    int voiceMinutes = user.getVoiceTimeMinutesTotal() != null ? user.getVoiceTimeMinutesTotal() : 0;
                    valueDisplay = String.format("%s 🎙️", formatVoiceTime(voiceMinutes));
                    break;
                default:
                    valueDisplay = "N/A";
                    break;
            }
            
            content.append(String.format("%s | **%s** - %s\n", 
                          rankDisplay, displayName, valueDisplay));
        }
        
        embed.addField("Rankings", content.toString(), false);
        
        logger.debug("[LEADERBOARD DEBUG] Building leaderboard embed: page={}/{}, type={}, users={}", 
                   page, totalPages, type, users.size());
        
        return embed.build();
    }
}