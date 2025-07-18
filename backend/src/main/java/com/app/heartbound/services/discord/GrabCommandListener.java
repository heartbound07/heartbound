package com.app.heartbound.services.discord;

import com.app.heartbound.entities.DiscordBotSettings;
import com.app.heartbound.entities.User;
import com.app.heartbound.services.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.springframework.stereotype.Component;

import javax.annotation.Nonnull;
import java.awt.Color;

@Component
@RequiredArgsConstructor
@Slf4j
public class GrabCommandListener extends ListenerAdapter {

    private final CreditDropStateService creditDropStateService;
    private final DiscordBotSettingsService discordBotSettingsService;
    private final UserService userService;

    @Override
    public void onSlashCommandInteraction(@Nonnull SlashCommandInteractionEvent event) {
        if (!event.getName().equals("grab")) {
            return;
        }

        DiscordBotSettings settings = discordBotSettingsService.getDiscordBotSettings();
        if (settings == null || !settings.getCreditDropEnabled() || !event.getChannel().getId().equals(settings.getCreditDropChannelId())) {
            event.reply("You can't do that here.").setEphemeral(true).queue();
            return;
        }

        creditDropStateService.claimDrop(event.getChannel().getId()).ifPresentOrElse(
            activeDrop -> {
                User user = userService.getUserById(event.getUser().getId());
                if (user == null) {
                    event.reply("You must be registered to claim credits.").setEphemeral(true).queue();
                    // Since the drop was claimed, we need to put it back if the user is not registered.
                    creditDropStateService.startDrop(event.getChannel().getId(), activeDrop.messageId(), activeDrop.amount());
                    return;
                }
                
                int newCredits = user.getCredits() + activeDrop.amount();
                user.setCredits(newCredits);
                userService.updateUser(user);

                event.reply("You grabbed " + activeDrop.amount() + " credits!").queue();

                // Edit original message
                event.getChannel().retrieveMessageById(activeDrop.messageId()).queue(message -> {
                    EmbedBuilder embed = new EmbedBuilder()
                        .setDescription("The credits were claimed by " + event.getUser().getAsMention() + "!")
                        .setColor(Color.GRAY);
                    message.editMessageEmbeds(embed.build()).queue();
                });
            },
            () -> event.reply("Too late! Someone else already grabbed the credits.").setEphemeral(true).queue()
        );
    }
} 