package com.app.heartbound.services.discord.challenge;

import com.app.heartbound.entities.challenge.ChallengeParticipant;
import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import org.springframework.stereotype.Component;

import javax.annotation.Nonnull;
import java.awt.Color;
import java.util.List;

@Component
@RequiredArgsConstructor
public class ChallengeCommandListener extends ListenerAdapter {

    private final ChallengeService challengeService;
    private static final Color EMBED_COLOR = new Color(88, 101, 242);

    @Override
    public void onSlashCommandInteraction(@Nonnull SlashCommandInteractionEvent event) {
        if (!event.getName().equals("challenge")) {
            return;
        }

        event.deferReply().queue();

        String commandUserId = event.getUser().getId();
        MessageEmbed embed = buildTeamLeaderboardEmbed();
        List<Button> buttons = createPaginationButtons(0, commandUserId);

        event.getHook().sendMessageEmbeds(embed).setActionRow(buttons).queue();
    }

    @Override
    public void onButtonInteraction(@Nonnull ButtonInteractionEvent event) {
        String componentId = event.getComponentId();
        if (!componentId.startsWith("challenge_nav:")) {
            return;
        }

        String[] parts = componentId.split(":");
        if (parts.length != 3) return;

        int pageIndex = Integer.parseInt(parts[1]);
        String originalUserId = parts[2];
        String interactorId = event.getUser().getId();

        if (!originalUserId.equals(interactorId)) {
            event.reply("You cannot interact with this button.").setEphemeral(true).queue();
            return;
        }

        event.deferEdit().queue();

        MessageEmbed embed;
        if (pageIndex == 0) {
            embed = buildTeamLeaderboardEmbed();
        } else {
            List<String> teamIds = challengeService.getTeamIds();
            String teamId = teamIds.get(pageIndex - 1);
            embed = buildUserLeaderboardEmbedForTeam(teamId);
        }

        List<Button> buttons = createPaginationButtons(pageIndex, originalUserId);
        event.getHook().editOriginalEmbeds(embed).setActionRow(buttons).queue();
    }

    private List<Button> createPaginationButtons(int currentPage, String userId) {
        int nextPage = (currentPage + 1) % 8;
        int prevPage = (currentPage - 1 + 8) % 8;

        Button prevButton = Button.secondary("challenge_nav:" + prevPage + ":" + userId, "◀️");
        Button nextButton = Button.secondary("challenge_nav:" + nextPage + ":" + userId, "▶️");

        return List.of(prevButton, nextButton);
    }

    private MessageEmbed buildTeamLeaderboardEmbed() {
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("July Team Challenge!")
                .setColor(EMBED_COLOR);

        List<ChallengeService.TeamLeaderboardEntry> teams = challengeService.getTeamLeaderboard();
        StringBuilder description = new StringBuilder();
        for (int i = 0; i < teams.size(); i++) {
            ChallengeService.TeamLeaderboardEntry team = teams.get(i);
            description.append(String.format("%s | **%s** - %d messages%n", getMedal(i), team.teamName(), team.totalMessageCount()));
        }

        embed.setDescription(description.toString());
        return embed.build();
    }

    private MessageEmbed buildUserLeaderboardEmbedForTeam(String teamId) {
        String teamName = challengeService.getTeamNameById(teamId);
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle(teamName)
                .setColor(EMBED_COLOR);

        List<ChallengeParticipant> users = challengeService.getUserLeaderboardForTeam(teamId);
        StringBuilder description = new StringBuilder();
        long totalMessages = 0;
        int limit = Math.min(users.size(), 10);

        for (int i = 0; i < limit; i++) {
            ChallengeParticipant user = users.get(i);
            description.append(String.format("%s | <@%s> - %d messages%n", getMedal(i), user.getUserId(), user.getMessageCount()));
        }

        totalMessages = users.stream().mapToLong(ChallengeParticipant::getMessageCount).sum();

        embed.setDescription(description.toString());
        embed.setFooter("Total Message Count: " + totalMessages);
        return embed.build();
    }

    private String getMedal(int index) {
        return switch (index) {
            case 0 -> "🥇";
            case 1 -> "🥈";
            case 2 -> "🥉";
            default -> "**#" + (index + 1) + "**";
        };
    }
} 