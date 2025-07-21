package com.app.heartbound.services.discord.challenge;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.springframework.stereotype.Component;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.entities.Member;

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

        Member member = event.getMember();
        if (member == null || !member.hasPermission(Permission.ADMINISTRATOR)) {
            event.reply("You do not have permission to use this command.").setEphemeral(true).queue();
            return;
        }

        OptionMapping enabledOption = event.getOption("enabled");
        if (enabledOption == null) {
            event.reply("Error: The 'enabled' option is missing. Please provide true or false.").setEphemeral(true).queue();
            return;
        }

        boolean enabled = enabledOption.getAsBoolean();
        challengeService.setMultiplierActive(enabled);

        event.reply("✅ Success! The 2x message multiplier for teams #4-7 has been " + (enabled ? "ENABLED" : "DISABLED") + ".")
                .setEphemeral(true)
                .queue();
    }
} 