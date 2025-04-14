package com.app.heartbound.services.discord;

import com.app.heartbound.dto.UserProfileDTO;
import com.app.heartbound.services.UserService;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.util.List;

@Component
public class LeaderboardCommandListener extends ListenerAdapter {
    
    private static final Logger logger = LoggerFactory.getLogger(LeaderboardCommandListener.class);
    private static final int PAGE_SIZE = 10; // Show 10 users per page
    private static final Color EMBED_COLOR = new Color(88, 101, 242); // Discord Blurple

    private final UserService userService;

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
            
            // Calculate total pages
            int totalPages = (int) Math.ceil((double) leaderboardUsers.size() / PAGE_SIZE);
            int currentPage = 1; // Start with page 1
            
            // Build the initial embed for page 1
            MessageEmbed embed = buildLeaderboardEmbed(leaderboardUsers, currentPage, totalPages);
            
            // Create pagination buttons
            Button prevButton = Button.secondary("leaderboard_prev:" + currentPage, "◀️").withDisabled(true); // Disabled on page 1
            Button nextButton = Button.secondary("leaderboard_next:" + currentPage, "▶️")
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
            // Parse current page and navigation direction
            int currentPage;
            boolean isNext = componentId.startsWith("leaderboard_next:");
            String pageStr = componentId.substring(componentId.indexOf(':') + 1);
            currentPage = Integer.parseInt(pageStr);
            
            // Calculate target page
            int tempTargetPage = isNext ? currentPage + 1 : currentPage - 1;
            
            // Fetch the full leaderboard again
            List<UserProfileDTO> leaderboardUsers = userService.getLeaderboardUsers();
            
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
            MessageEmbed embed = buildLeaderboardEmbed(leaderboardUsers, targetPage, totalPages);
            
            // Create updated pagination buttons
            Button prevButton = Button.secondary("leaderboard_prev:" + targetPage, "◀️")
                               .withDisabled(targetPage <= 1); // Disabled on page 1
            Button nextButton = Button.secondary("leaderboard_next:" + targetPage, "▶️")
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
    private MessageEmbed buildLeaderboardEmbed(List<UserProfileDTO> users, int page, int totalPages) {
        // Calculate start and end indices for the current page
        int startIndex = (page - 1) * PAGE_SIZE;
        int endIndex = Math.min(startIndex + PAGE_SIZE, users.size());
        
        // Get sublist for current page
        List<UserProfileDTO> pageUsers = users.subList(startIndex, endIndex);
        
        // Build the embed
        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle("User Credits Leaderboard");
        embed.setDescription("Top users ranked by credits");
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
            
            // Format each entry with rank, name, and credits
            content.append(String.format("**#%d** | **%s** - %d credits\n", 
                          rank, displayName, user.getCredits()));
        }
        
        embed.addField("Rankings", content.toString(), false);
        embed.setFooter("Page " + page + " / " + totalPages);
        
        return embed.build();
    }
}
