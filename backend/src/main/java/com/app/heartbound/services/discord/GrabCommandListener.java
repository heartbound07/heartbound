package com.app.heartbound.services.discord;

import com.app.heartbound.entities.DiscordBotSettings;
import com.app.heartbound.entities.User;
import com.app.heartbound.services.UserInventoryService;
import com.app.heartbound.services.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.springframework.stereotype.Component;

import javax.annotation.Nonnull;
import java.awt.Color;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class GrabCommandListener extends ListenerAdapter {

    private final DropStateService dropStateService;
    private final DiscordBotSettingsService discordBotSettingsService;
    private final UserService userService;
    private final UserInventoryService userInventoryService;

    @Override
    public void onSlashCommandInteraction(@Nonnull SlashCommandInteractionEvent event) {
        if (!event.getName().equals("grab")) {
            return;
        }

        DiscordBotSettings settings = discordBotSettingsService.getDiscordBotSettings();
        if (settings == null) {
            event.reply("Bot settings are not configured.").setEphemeral(true).queue();
            return;
        }

        boolean isCreditDropChannel = settings.getCreditDropEnabled() && event.getChannel().getId().equals(settings.getCreditDropChannelId());
        boolean isPartDropChannel = settings.getPartDropEnabled() != null && settings.getPartDropEnabled() && event.getChannel().getId().equals(settings.getPartDropChannelId());

        if (!isCreditDropChannel && !isPartDropChannel) {
            event.reply("You can't do that here.").setEphemeral(true).queue();
            return;
        }

        dropStateService.claimDrop(event.getChannel().getId()).ifPresentOrElse(
            activeDrop -> {
                User user = userService.getUserById(event.getUser().getId());
                if (user == null) {
                    event.reply("You must be registered to claim drops.").setEphemeral(true).queue();
                    // Put the drop back since the user is not registered.
                    dropStateService.startDrop(event.getChannel().getId(), activeDrop.getMessageId(), activeDrop.getType(), activeDrop.getValue());
                    return;
                }

                switch (activeDrop.getType()) {
                    case CREDIT:
                        handleCreditDrop(event, user, activeDrop);
                        break;
                    case ITEM:
                        handleItemDrop(event, user, activeDrop);
                        break;
                }
            },
            () -> event.reply("Too late! Someone else already grabbed the drop.").setEphemeral(true).queue()
        );
    }

    private void handleCreditDrop(SlashCommandInteractionEvent event, User user, DropStateService.ActiveDrop activeDrop) {
        int amount = (Integer) activeDrop.getValue();
        boolean success = userService.updateCreditsAtomic(user.getId(), amount);

        if (!success) {
            event.reply("An error occurred while adding credits to your account.").setEphemeral(true).queue();
            // Put the drop back.
            dropStateService.startDrop(event.getChannel().getId(), activeDrop.getMessageId(), activeDrop.getType(), activeDrop.getValue());
            return;
        }

        event.reply("You grabbed " + amount + " credits!").queue();

        // Edit original message
        event.getChannel().retrieveMessageById(activeDrop.getMessageId()).queue(message -> {
            EmbedBuilder embed = new EmbedBuilder()
                .setDescription("The credits were claimed by " + event.getUser().getAsMention() + "!")
                .setColor(Color.GRAY);
            message.editMessageEmbeds(embed.build()).queue();
        });
    }

    private void handleItemDrop(SlashCommandInteractionEvent event, User user, DropStateService.ActiveDrop activeDrop) {
        UUID itemId = (UUID) activeDrop.getValue();
        try {
            // This is a simplified call. You might need a more specific method in UserInventoryService
            // that doesn't require a full ShopDTO and doesn't involve payment.
            // For now, let's assume a method like `giveItemToUser(userId, itemId)` exists.
            // We will need to create it.
            String itemName = userInventoryService.giveItemToUser(user.getId(), itemId);

            event.reply("You grabbed a **" + itemName + "**!").queue();

            // Edit original message
            event.getChannel().retrieveMessageById(activeDrop.getMessageId()).queue(message -> {
                EmbedBuilder embed = new EmbedBuilder()
                    .setDescription("The **" + itemName + "** was claimed by " + event.getUser().getAsMention() + "!")
                    .setColor(Color.GRAY);
                message.editMessageEmbeds(embed.build()).queue();
            });
        } catch (Exception e) {
            log.error("Error giving item {} to user {}: {}", itemId, user.getId(), e.getMessage(), e);
            event.reply("An error occurred while giving you the item.").setEphemeral(true).queue();
            // Put the drop back.
            dropStateService.startDrop(event.getChannel().getId(), activeDrop.getMessageId(), activeDrop.getType(), activeDrop.getValue());
        }
    }
} 