package com.app.heartbound.services.discord;

import com.app.heartbound.entities.DiscordBotSettings;
import com.app.heartbound.entities.User;
import com.app.heartbound.services.UserInventoryService;
import com.app.heartbound.services.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
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
    public void onMessageReceived(@Nonnull MessageReceivedEvent event) {
        if (event.getAuthor().isBot() || !event.getMessage().getContentRaw().equalsIgnoreCase("grab")) {
            return;
        }

        DiscordBotSettings settings = discordBotSettingsService.getDiscordBotSettings();
        if (settings == null) {
            event.getMessage().reply("Bot settings are not configured.").queue();
            return;
        }

        boolean isCreditDropChannel = settings.getCreditDropEnabled() && event.getChannel().getId().equals(settings.getCreditDropChannelId());
        boolean isPartDropChannel = settings.getPartDropEnabled() != null && settings.getPartDropEnabled() && event.getChannel().getId().equals(settings.getPartDropChannelId());

        if (!isCreditDropChannel && !isPartDropChannel) {
            // Do not reply to avoid spamming channels where the command is not applicable.
            return;
        }

        dropStateService.claimDrop(event.getChannel().getId()).ifPresentOrElse(
            activeDrop -> {
                // Immediately cancel the scheduled expiration task for this drop
                activeDrop.getExpirationTask().cancel(false);

                User user = userService.getUserById(event.getAuthor().getId());
                if (user == null) {
                    event.getMessage().reply("You must be registered to claim drops.").queue();
                    // Do NOT put the drop back, as it has been claimed and its timer cancelled.
                    // The original drop message will be handled by the subsequent logic.
                    event.getChannel().deleteMessageById(activeDrop.getMessageId()).queue();
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
            () -> {
                // Only send "too late" message if the drop was claimed by another user,
                // not if it expired (expired drops should be silent)
                if (!dropStateService.hadRecentExpiration(event.getChannel().getId())) {
                    event.getMessage().reply("Too late! Someone else already grabbed the drop.").queue();
                }
                // If hadRecentExpiration() returns true, remain silent - the absence of the drop message is sufficient feedback
            }
        );
    }

    private void handleCreditDrop(MessageReceivedEvent event, User user, DropStateService.ActiveDrop activeDrop) {
        int amount = (Integer) activeDrop.getValue();
        boolean success = userService.updateCreditsAtomic(user.getId(), amount);

        if (!success) {
            event.getMessage().reply("An error occurred while adding credits to your account.").queue();
            // Do NOT put the drop back. The drop has been claimed.
            // The original message will be deleted to prevent confusion.
            event.getChannel().deleteMessageById(activeDrop.getMessageId()).queue(
                s -> log.info("Deleted original drop message after a failed claim."),
                e -> log.warn("Failed to delete original drop message after a failed claim.")
            );
            return;
        }

        // Edit original message
        event.getChannel().retrieveMessageById(activeDrop.getMessageId()).queue(message -> {
            EmbedBuilder embed = new EmbedBuilder()
                .setDescription("The credits were claimed by " + event.getAuthor().getAsMention() + "!")
                .setColor(Color.GRAY);
            message.editMessageEmbeds(embed.build()).queue();
        });
    }

    private void handleItemDrop(MessageReceivedEvent event, User user, DropStateService.ActiveDrop activeDrop) {
        UUID itemId = (UUID) activeDrop.getValue();
        try {
            // This is a simplified call. You might need a more specific method in UserInventoryService
            // that doesn't require a full ShopDTO and doesn't involve payment.
            // For now, let's assume a method like `giveItemToUser(userId, itemId)` exists.
            // We will need to create it.
            String itemName = userInventoryService.giveItemToUser(user.getId(), itemId);

            // Edit original message
            event.getChannel().retrieveMessageById(activeDrop.getMessageId()).queue(message -> {
                EmbedBuilder embed = new EmbedBuilder()
                    .setDescription(event.getAuthor().getAsMention() + " collected a **" + itemName + "**!")
                    .setColor(Color.GRAY);
                message.editMessageEmbeds(embed.build()).setComponents().queue();
            });
        } catch (Exception e) {
            log.error("Error giving item {} to user {}: {}", itemId, user.getId(), e.getMessage(), e);
            event.getMessage().reply("An error occurred while giving you the item.").queue();
            // Do NOT put the drop back. The drop has been claimed.
            // The original message will be deleted to prevent confusion.
            event.getChannel().deleteMessageById(activeDrop.getMessageId()).queue(
                s -> log.info("Deleted original drop message after a failed item claim."),
                err -> log.warn("Failed to delete original drop message after a failed item claim.")
            );
        }
    }
} 