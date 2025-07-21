package com.app.heartbound.services.discord.challenge;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.springframework.stereotype.Component;

import javax.annotation.Nonnull;

@Component
public class MultiplierCommandListener extends ListenerAdapter {

    private final ChallengeService challengeService;

    public MultiplierCommandListener(ChallengeService challengeService) {
        this.challengeService = challengeService;
    }

    @Override
    public void onSlashCommandInteraction(@Nonnull SlashCommandInteractionEvent event) {
        if (!event.getName().equals("multiplier")) {
            return;
        }

        if (event.getMember() == null || !event.getMember().hasPermission(Permission.ADMINISTRATOR)) {
            event.reply("You do not have permission to use this command.").setEphemeral(true).queue();
            return;
        }

        boolean enabled = event.getOption("enabled").getAsBoolean();
        challengeService.setMultiplierActive(enabled);

        event.reply("✅ Success! The 2x message multiplier for teams #4-7 has been " + (enabled ? "ENABLED" : "DISABLED") + ".")
                .setEphemeral(true)
                .queue();
    }
} 