package com.app.heartbound.services.discord;

import com.app.heartbound.dto.LeaderboardEntryDTO;
import com.app.heartbound.services.UserService;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import jakarta.annotation.Nonnull;
import jakarta.annotation.PreDestroy;
import java.awt.Color;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
public class LeaderboardCommandListener extends ListenerAdapter {
    
    private static final Logger logger = LoggerFactory.getLogger(LeaderboardCommandListener.class);
    private static final int PAGE_SIZE = 10; // Show 10 users per page
    private static final Color EMBED_COLOR = new Color(88, 101, 242); // Discord Blurple

    private final UserService userService;
    
    @Value("${frontend.base.url}")
    private String frontendBaseUrl;

    // Discord display name cache - short TTL since display names can change frequently
    private final Cache<String, String> discordDisplayNameCache;
    
    // Add field to track registration status and JDA instance (like other listeners)
    private boolean isRegistered = false;
    private JDA jdaInstance;

    public LeaderboardCommandListener(@Lazy UserService userService) {
        this.userService = userService;
        
        // Initialize Discord display name cache with 5-minute TTL for performance
        this.discordDisplayNameCache = Caffeine.newBuilder()
                .maximumSize(10000)
                .expireAfterWrite(5, TimeUnit.MINUTES)
                .recordStats()
                .build();
                
        logger.info("LeaderboardCommandListener initialized with Discord display name caching");
    }
    
    /**
     * Register this listener with the JDA instance.
     * This method is called by DiscordConfig after JDA is initialized.
     * 
     * @param jda The JDA instance to register with
     */
    public void registerWithJDA(JDA jda) {
        if (jda != null && !isRegistered) {
            this.jdaInstance = jda;
            jda.addEventListener(this);
            this.isRegistered = true;
            logger.debug("LeaderboardCommandListener registered with JDA");
        }
    }
    
    /**
     * Clean up method called before bean destruction.
     * Ensures this listener is removed from JDA to prevent events during shutdown.
     */
    @PreDestroy
    public void cleanup() {
        logger.debug("LeaderboardCommandListener cleanup started");
        if (isRegistered && jdaInstance != null) {
            try {
                jdaInstance.removeEventListener(this);
                logger.info("LeaderboardCommandListener successfully unregistered from JDA");
            } catch (Exception e) {
                logger.warn("Error while unregistering LeaderboardCommandListener: {}", e.getMessage());
            }
            isRegistered = false;
            jdaInstance = null;
        }
        logger.debug("LeaderboardCommandListener cleanup completed");
    }

    @Override
    public void onSlashCommandInteraction(@Nonnull SlashCommandInteractionEvent event) {
        if (!event.getName().equals("leaderboard")) {
            return; // Not our command
        }

        logger.debug("Leaderboard command received from user: {}", event.getUser().getId());
        
        // Get the user ID who executed the command for security validation
        String commandUserId = event.getUser().getId();
        
        // Get the leaderboard type (levels, credits, messages, or voice)
        OptionMapping typeOption = event.getOption("type");
        String leaderboardType = typeOption == null ? "levels" : typeOption.getAsString();
        // The service expects "level", but the command provides "levels"
        String serviceSortBy = "levels".equalsIgnoreCase(leaderboardType) ? "level" : leaderboardType;

        // Get the guild for Discord display name resolution
        Guild guild = event.getGuild();
        
        // Acknowledge the interaction quickly to prevent timeout
        event.deferReply().queue();
        
        try {
            // Fetch the full leaderboard from UserService
            List<LeaderboardEntryDTO> leaderboardUsers = userService.getLeaderboardUsers(serviceSortBy);
            
            if (leaderboardUsers == null || leaderboardUsers.isEmpty()) {
                // Handle empty leaderboard
                event.getHook().sendMessage("No users found for the leaderboard.").setEphemeral(true).queue();
                return;
            }
            
            
            // Calculate total pages
            int totalPages = (int) Math.ceil((double) leaderboardUsers.size() / PAGE_SIZE);
            int currentPage = 1; // Start with page 1
            
            // Build the initial embed for page 1
            MessageEmbed embed = buildLeaderboardEmbed(leaderboardUsers, currentPage, totalPages, leaderboardType, guild);
            
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
    public void onButtonInteraction(@Nonnull ButtonInteractionEvent event) {
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
            
            // Get the guild for Discord display name resolution
            Guild guild = event.getGuild();
            
            // User is authorized, acknowledge the button click
            event.deferEdit().queue();
            
            boolean isNext = componentId.startsWith("leaderboard_next:");
            
            // Calculate target page
            int tempTargetPage = isNext ? currentPage + 1 : currentPage - 1;

            // The service expects "level", but the command provides "levels"
            String serviceSortBy = "levels".equalsIgnoreCase(leaderboardType) ? "level" : leaderboardType;
            
            // Fetch the full leaderboard again
            List<LeaderboardEntryDTO> leaderboardUsers = userService.getLeaderboardUsers(serviceSortBy);
            
            if (leaderboardUsers == null || leaderboardUsers.isEmpty()) {
                // Handle empty leaderboard (rare edge case)
                event.getHook().editOriginal("No users found for the leaderboard.").queue();
                return;
            }
            
            
            // Calculate total pages
            int totalPages = (int) Math.ceil((double) leaderboardUsers.size() / PAGE_SIZE);
            
            // Safety check for valid page number
            if (tempTargetPage < 1) tempTargetPage = 1;
            if (tempTargetPage > totalPages) tempTargetPage = totalPages;
            
            // Create final variable for use in lambda
            final int targetPage = tempTargetPage;
            
            // Build the new embed for the target page
            MessageEmbed embed = buildLeaderboardEmbed(leaderboardUsers, targetPage, totalPages, leaderboardType, guild);
            
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
     * Retrieves the Discord display name for a user using JDA with caching.
     * This method attempts to get the user's effective name from Discord,
     * falling back to stored username if the user is not found in the server.
     * 
     * @param userId The Discord user ID
     * @param storedUsername The stored username as fallback
     * @param storedDisplayName The stored display name as fallback
     * @param guild The Discord guild to search for the member
     * @return The effective display name (Discord display name or fallback)
     */
    private String getDiscordDisplayName(String userId, String storedUsername, String storedDisplayName, Guild guild) {
        if (userId == null || guild == null) {
            // Fallback to stored data if we can't lookup Discord info
            return (storedDisplayName != null && !storedDisplayName.isEmpty()) ? storedDisplayName : storedUsername;
        }
        
        // Check cache first for performance
        String cachedName = discordDisplayNameCache.getIfPresent(userId);
        if (cachedName != null) {
            return cachedName;
        }
        
        try {
            // Attempt to get the member from the guild
            Member member = guild.getMemberById(userId);
            
            if (member != null) {
                // Use Discord's effective name (display name if set, otherwise username)
                String effectiveName = member.getEffectiveName();
                
                // Cache the result for future requests
                discordDisplayNameCache.put(userId, effectiveName);
                
                logger.debug("Retrieved Discord display name for user {}: {}", userId, effectiveName);
                return effectiveName;
            } else {
                // User not found in guild, use stored fallback and cache it
                String fallbackName = (storedDisplayName != null && !storedDisplayName.isEmpty()) 
                                    ? storedDisplayName : storedUsername;
                
                // Cache the fallback to avoid repeated failed lookups
                discordDisplayNameCache.put(userId, fallbackName);
                
                logger.debug("User {} not found in Discord guild, using stored fallback: {}", userId, fallbackName);
                return fallbackName;
            }
        } catch (Exception e) {
            // Discord API error, use stored fallback and cache it
            String fallbackName = (storedDisplayName != null && !storedDisplayName.isEmpty()) 
                                ? storedDisplayName : storedUsername;
            
            // Cache the fallback to avoid repeated failed API calls
            discordDisplayNameCache.put(userId, fallbackName);
            
            logger.warn("Error retrieving Discord display name for user {}: {}. Using fallback: {}", 
                       userId, e.getMessage(), fallbackName);
            return fallbackName;
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
     * Now uses Discord display names via JDA instead of stored username/displayName.
     *
     * @param users The full list of users sorted by the selected type
     * @param page The current page (1-based)
     * @param totalPages The total number of pages
     * @param type The leaderboard type (levels, credits, messages, or voice)
     * @param guild The Discord guild for display name resolution
     * @return A MessageEmbed containing the formatted leaderboard
     */
    private MessageEmbed buildLeaderboardEmbed(List<LeaderboardEntryDTO> users, int page, int totalPages, String type, Guild guild) {
        // Calculate start and end indices for the current page
        int startIndex = (page - 1) * PAGE_SIZE;
        int endIndex = Math.min(startIndex + PAGE_SIZE, users.size());
        
        // Get sublist for current page
        List<LeaderboardEntryDTO> pageUsers = users.subList(startIndex, endIndex);
        
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
            case "fish":
                embed.setTitle("Fish Caught Leaderboard");
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
            LeaderboardEntryDTO user = pageUsers.get(i);
            int rank = user.getRank(); // Use rank from DTO
            
            // Use Discord display name instead of stored username/displayName
            String displayName = getDiscordDisplayName(
                user.getId(), 
                user.getUsername(), 
                user.getDisplayName(), 
                guild
            );
            
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
                case "fish":
                    int fishCount = user.getFishCaughtCount() != null ? user.getFishCaughtCount() : 0;
                    valueDisplay = String.format("%,d 🐟", fishCount);
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