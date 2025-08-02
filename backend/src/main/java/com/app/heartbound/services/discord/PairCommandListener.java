package com.app.heartbound.services.discord;

import com.app.heartbound.dto.pairing.CreatePairingRequestDTO;
import com.app.heartbound.services.UserService;
import com.app.heartbound.services.UserValidationService;
import com.app.heartbound.services.pairing.PairingService;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import javax.annotation.Nonnull;
import java.awt.*;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
public class PairCommandListener extends ListenerAdapter {

    private static final Color WARNING_COLOR = new Color(255, 193, 7); // Bootstrap warning yellow

    private final PairingService pairingService;
    private final UserService userService;
    private final UserValidationService userValidationService;
    private final TermsOfServiceService termsOfServiceService;
    private JDA jdaInstance;
    private boolean isRegistered = false;

    private final ConcurrentHashMap<String, PairRequest> pendingRequests = new ConcurrentHashMap<>();

    public PairCommandListener(@Lazy PairingService pairingService, UserService userService,
                               UserValidationService userValidationService,
                               TermsOfServiceService termsOfServiceService) {
        this.pairingService = pairingService;
        this.userService = userService;
        this.userValidationService = userValidationService;
        this.termsOfServiceService = termsOfServiceService;
    }

    public void registerWithJDA(JDA jda) {
        if (jda != null && !isRegistered) {
            this.jdaInstance = jda;
            jda.addEventListener(this);
            isRegistered = true;
            log.info("PairCommandListener registered with JDA");
        }
    }

    @PreDestroy
    public void cleanup() {
        if (isRegistered && jdaInstance != null) {
            jdaInstance.removeEventListener(this);
            log.info("PairCommandListener unregistered from JDA");
        }
    }

    @Override
    public void onSlashCommandInteraction(@Nonnull SlashCommandInteractionEvent event) {
        if (!event.getName().equals("pair")) return;

        log.info("User {} requested /pair command", event.getUser().getId());

        // Require Terms of Service agreement from initiator before proceeding
        termsOfServiceService.requireAgreement(event, user -> {
            // ToS check passed, continue with pair command logic
            handlePairCommand(event, user);
        });
    }

    /**
     * Handles the main pair command logic after ToS agreement is confirmed.
     */
    private void handlePairCommand(@Nonnull SlashCommandInteractionEvent event, com.app.heartbound.entities.User requester) {
        event.deferReply(true).queue();

        User requesterUser = event.getUser();

        OptionMapping userOption = event.getOption("user");
        if (userOption == null) {
            event.getHook().sendMessage("You must specify a user to pair with.").queue();
            return;
        }
        User targetUser = userOption.getAsUser();

        if (targetUser.isBot() || targetUser.equals(requesterUser)) {
            event.getHook().sendMessage("You cannot pair with a bot or yourself.").queue();
            return;
        }

        try {
            // Requester is already validated by ToS service, get target user
            com.app.heartbound.entities.User target = userService.getUserById(targetUser.getId());

            if (target == null) {
                event.getHook().sendMessage("The user you want to pair with must be registered in the system first.").queue();
                return;
            }
            
            if (pairingService.getCurrentPairing(requester.getId()).isPresent() || pairingService.getCurrentPairing(target.getId()).isPresent()) {
                event.getHook().sendMessage("One of the users is already in a pairing.").queue();
                return;
            }

            if (pairingService.checkBlacklistStatus(requester.getId(), target.getId()).isBlacklisted()) {
                event.getHook().sendMessage("You are unable to pair with this user.").queue();
                return;
            }
            
            String requestKey = getRequestKey(requester.getId(), target.getId());
            if (pendingRequests.containsKey(requestKey)) {
                event.getHook().sendMessage("There is already a pending pair request between you and this user.").queue();
                return;
            }

            long timestamp = Instant.now().toEpochMilli();
            Button acceptButton = Button.success("pair_accept_" + requester.getId() + "_" + target.getId() + "_" + timestamp, "✅");
            Button rejectButton = Button.danger("pair_reject_" + requester.getId() + "_" + target.getId() + "_" + timestamp, "❌");

            EmbedBuilder embedBuilder = new EmbedBuilder()
                    .setDescription("Hey " + targetUser.getAsMention() + ", " + requesterUser.getAsMention() + " wants to pair with you!")
                    .setColor(new Color(0x5865F2));

            event.getChannel().sendMessage(targetUser.getAsMention()).addEmbeds(embedBuilder.build()).setActionRow(acceptButton, rejectButton).queue(
                message -> {
                    pendingRequests.put(requestKey, new PairRequest(requester.getId(), target.getId(), message.getIdLong(), Instant.now()));
                    event.getHook().deleteOriginal().queue(); // Delete the "Thinking..." message
                },
                failure -> {
                    log.warn("Failed to send pair request message in channel {}: {}", event.getChannel().getId(), failure.getMessage());
                    event.getHook().sendMessage("❌ I couldn't send the request. Please make sure I have permission to post in this channel.").queue();
                }
            );

        } catch (Exception e) {
            log.error("Error in /pair command", e);
            event.getHook().sendMessage("An error occurred while sending the pair request.").queue();
        }
    }

    @Override
    public void onButtonInteraction(@Nonnull ButtonInteractionEvent event) {
        String componentId = event.getComponentId();
        if (!componentId.startsWith("pair_")) return;

        event.deferEdit().queue();
        
        String[] parts = componentId.split("_");
        String action = parts[1];
        String requesterId = parts[2];
        String targetId = parts[3];
        long timestamp = Long.parseLong(parts[4]);

        if (!event.getUser().getId().equals(targetId)) {
            event.getHook().sendMessage("You are not the recipient of this pair request.").setEphemeral(true).queue();
            return;
        }
        
        if (Instant.ofEpochMilli(timestamp).isBefore(Instant.now().minusSeconds(300))) {
            EmbedBuilder embedBuilder = new EmbedBuilder()
                    .setTitle("Expired!")
                    .setColor(WARNING_COLOR);
            event.getHook().editOriginalEmbeds(embedBuilder.build()).setComponents().queue();
            return;
        }

        String requestKey = getRequestKey(requesterId, targetId);
        PairRequest request = pendingRequests.remove(requestKey);

        if (request == null) {
            event.getHook().sendMessage("This request is no longer valid.").setEphemeral(true).queue();
            disableButtons(event.getChannel(), event.getMessageIdLong(), "Request invalid");
            return;
        }

        if ("reject".equals(action)) {
            EmbedBuilder embedBuilder = new EmbedBuilder()
                    .setDescription(event.getUser().getAsMention() + " has rejected the request! :<")
                    .setColor(Color.RED);
            event.getHook().editOriginalEmbeds(embedBuilder.build()).setComponents().queue();
            return;
        }

        try {
            com.app.heartbound.entities.User requester = userService.getUserById(requesterId);
            com.app.heartbound.entities.User target = userService.getUserById(targetId);

            CreatePairingRequestDTO createRequest = CreatePairingRequestDTO.builder()
                .user1Id(requester.getId())
                .user2Id(target.getId())
                .user1DiscordId(requester.getId())
                .user2DiscordId(target.getId())
                .compatibilityScore(75) // Default for manual pair
                .build();
            
            pairingService.createPairing(createRequest);

            // Get the JDA User object for the target (who clicked the button)
            User targetDiscordUser = event.getUser();

            // Asynchronously retrieve the requester's Discord User object to build the mention string
            jdaInstance.retrieveUserById(requesterId).queue(requesterDiscordUser -> {
                // On success, use mentions for both users
                EmbedBuilder embedBuilder = new EmbedBuilder()
                        .setDescription(requesterDiscordUser.getAsMention() + " and " + targetDiscordUser.getAsMention() + " have been paired together!")
                        .setColor(Color.GREEN);
                event.getHook().editOriginalEmbeds(embedBuilder.build()).setComponents().queue();
            }, failure -> {
                // Fallback if the requester's Discord profile can't be fetched
                log.warn("Could not retrieve requester's Discord profile (ID: {}). Falling back to database display names.", requesterId, failure);
                EmbedBuilder embedBuilder = new EmbedBuilder()
                        .setDescription(requester.getDisplayName() + " and " + target.getDisplayName() + " have been paired together!")
                        .setColor(Color.GREEN);
                event.getHook().editOriginalEmbeds(embedBuilder.build()).setComponents().queue();
            });
            
        } catch (Exception e) {
            log.error("Error creating pairing from /pair command", e);
            event.getHook().sendMessage("Failed to create pairing: " + e.getMessage()).setEphemeral(true).queue();
        }
    }

    private String getRequestKey(String id1, String id2) {
        return id1.compareTo(id2) < 0 ? id1 + ":" + id2 : id2 + ":" + id1;
    }

    private void disableButtons(MessageChannel channel, long messageId, String reason) {
        channel.retrieveMessageById(messageId).queue(message -> {
            message.editMessageComponents().setComponents().queue();
            // Optionally edit embed to show it's expired/invalid
        });
    }
} 