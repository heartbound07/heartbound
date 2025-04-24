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
        
        // Get the leaderboard type (levels or credits)
        OptionMapping typeOption = event.getOption("type");
        String leaderboardType = typeOption == null ? "levels" : typeOption.getAsString();
        
        // Acknowledge the interaction quickly to prevent timeout
        event.deferReply().queue();
        
        try {
            // Fetch the full leaderboard from UserService
            List<UserProfileDTO> leaderboardUsers = userService.getLeaderboardUsers();
            
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
            } else {
                // Sort by credits descending (already done in the service, but let's ensure it)
                leaderboardUsers.sort(
                    Comparator.comparing(UserProfileDTO::getCredits, Comparator.nullsFirst(Comparator.reverseOrder()))
                );
                logger.debug("[LEADERBOARD DEBUG] Sorted by credits. Top user: {}({} credits)", 
                           !leaderboardUsers.isEmpty() ? leaderboardUsers.get(0).getUsername() : "none",
                           !leaderboardUsers.isEmpty() ? leaderboardUsers.get(0).getCredits() : 0);
            }
            
            // Calculate total pages
            int totalPages = (int) Math.ceil((double) leaderboardUsers.size() / PAGE_SIZE);
            int currentPage = 1; // Start with page 1
            
            // Build the initial embed for page 1
            MessageEmbed embed = buildLeaderboardEmbed(leaderboardUsers, currentPage, totalPages, leaderboardType);
            
            // Create pagination buttons
            Button prevButton = Button.secondary("leaderboard_prev:" + leaderboardType + ":" + currentPage, "◀️").withDisabled(true); // Disabled on page 1
            Button nextButton = Button.secondary("leaderboard_next:" + leaderboardType + ":" + currentPage, "▶️")
                               .withDisabled(totalPages <= 1); // Disabled if only 1 page
            
            // Send the initial response with buttons
            event.getHook().sendMessageEmbeds(embed)
                .addActionRow(prevButton, nextButton)
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
        
        // Acknowledge the button click immediately
        event.deferEdit().queue();
        
        try {
            // Parse component ID: format is leaderboard_<direction>:<type>:<page>
            String[] parts = componentId.split(":");
            if (parts.length != 3) {
                logger.error("Invalid component ID format: {}", componentId);
                return;
            }
            
            String leaderboardType = parts[1];
            int currentPage = Integer.parseInt(parts[2]);
            boolean isNext = componentId.startsWith("leaderboard_next:");
            
            // Calculate target page
            int tempTargetPage = isNext ? currentPage + 1 : currentPage - 1;
            
            // Fetch the full leaderboard again
            List<UserProfileDTO> leaderboardUsers = userService.getLeaderboardUsers();
            
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
            } else {
                // Sort by credits descending
                leaderboardUsers.sort(
                    Comparator.comparing(UserProfileDTO::getCredits, Comparator.nullsFirst(Comparator.reverseOrder()))
                );
                logger.debug("[LEADERBOARD DEBUG] Sorted by credits. Top user: {}({} credits)", 
                           !leaderboardUsers.isEmpty() ? leaderboardUsers.get(0).getUsername() : "none",
                           !leaderboardUsers.isEmpty() ? leaderboardUsers.get(0).getCredits() : 0);
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
            
            // Create updated pagination buttons
            Button prevButton = Button.secondary("leaderboard_prev:" + leaderboardType + ":" + targetPage, "◀️")
                               .withDisabled(targetPage <= 1); // Disabled on page 1
            Button nextButton = Button.secondary("leaderboard_next:" + leaderboardType + ":" + targetPage, "▶️")
                               .withDisabled(targetPage >= totalPages); // Disabled on last page
            
            // Update the original message
            event.getHook().editOriginalEmbeds(embed)
                .setActionRow(prevButton, nextButton)
                .queue(success -> logger.debug("Pagination updated to page {}", targetPage),
                      error -> logger.error("Failed to update pagination", error));
            
        } catch (Exception e) {
            logger.error("Error processing leaderboard pagination", e);
            // The interaction acknowledgment already happened, so just log the error
        }
    }
    
    /**
     * Builds a Discord embed for displaying a page of the leaderboard.
     *
     * @param users The full list of users sorted by credits
     * @param page The current page (1-based)
     * @param totalPages The total number of pages
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
        if ("levels".equals(type)) {
            embed.setTitle("Level Leaderboard");
        } else {
            embed.setTitle("Credit Leaderboard");
        }
        
        // Create a clickable link to the web leaderboard
        String leaderboardUrl = frontendBaseUrl + "/dashboard/leaderboard";
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
            
            // Format each entry with rank, name, and depending on the type, either level/xp or credits
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
            
            if ("levels".equals(type)) {
                int level = user.getLevel() != null ? user.getLevel() : 1;
                int xp = user.getExperience() != null ? user.getExperience() : 0;
                content.append(String.format("%s | **%s** - Level %d (%d XP)\n", 
                              rankDisplay, displayName, level, xp));
            } else {
                int credits = user.getCredits() != null ? user.getCredits() : 0;
                content.append(String.format("%s | **%s** - %d credits\n", 
                              rankDisplay, displayName, credits));
            }
        }
        
        embed.addField("Rankings", content.toString(), false);
        embed.setFooter("Page " + page + " / " + totalPages + " • " + (type.equals("levels") ? "Levels" : "Credits") + " Leaderboard");
        
        logger.debug("[LEADERBOARD DEBUG] Building leaderboard embed: page={}/{}, type={}, users={}", 
                   page, totalPages, type, users.size());
        
        return embed.build();
    }
}
