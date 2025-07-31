package com.app.heartbound.services.discord;

import com.app.heartbound.entities.DiscordBotSettings;
import com.app.heartbound.entities.Shop;
import com.app.heartbound.enums.ShopCategory;
import com.app.heartbound.repositories.shop.ShopRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

@Service
@RequiredArgsConstructor
@Slf4j
public class PartDropSchedulerService {

    private final DiscordBotSettingsService discordBotSettingsService;
    private final DropStateService dropStateService;
    private final ShopRepository shopRepository;
    private final JDA jda;
    private final Random random = new Random();
    private final ReentrantLock partDropLock = new ReentrantLock();
    private final ScheduledExecutorService expirationScheduler = Executors.newSingleThreadScheduledExecutor();

    @Scheduled(fixedRate = 60000) // Runs every minute
    public void scheduledPartDrop() {
        DiscordBotSettings settings = discordBotSettingsService.getDiscordBotSettings();
        if (settings == null || settings.getPartDropEnabled() == null || !settings.getPartDropEnabled()) {
            log.debug("[PartDropScheduler] Part drops are disabled. Skipping run.");
            return;
        }

        if (random.nextDouble() > settings.getPartDropChance()) {
            log.debug("[PartDropScheduler] No part drop this time. Skipping.");
            return;
        }

        if (!partDropLock.tryLock()) {
            log.warn("[PartDropScheduler] Previous part drop run still in progress. Skipping this cycle.");
            return;
        }

        try {
            String channelId = settings.getPartDropChannelId();
            if (channelId == null || channelId.isBlank()) {
                log.warn("[PartDropScheduler] Part drop channel ID is not configured.");
                return;
            }

            if (dropStateService.hasActiveDrop(channelId)) {
                log.debug("[PartDropScheduler] An active drop already exists in channel {}. Skipping.", channelId);
                return;
            }

            List<Shop> availableParts = shopRepository.findByCategoryAndIsActiveTrue(ShopCategory.FISHING_ROD_PART);
            if (availableParts.isEmpty()) {
                log.debug("[PartDropScheduler] No active fishing rod parts available to drop.");
                return;
            }

            Shop partToDrop = availableParts.get(random.nextInt(availableParts.size()));

            TextChannel channel = jda.getTextChannelById(channelId);
            if (channel == null) {
                log.warn("[PartDropScheduler] Part drop channel with ID {} not found.", channelId);
                return;
            }

            EmbedBuilder embed = new EmbedBuilder()
                .setDescription("A Fishing Rod part has washed up on shore!")
                .setFooter("type 'grab' to claim the item!")
                .setColor(new Color(46, 204, 113)); // A pleasant green color

            channel.sendMessageEmbeds(embed.build()).queue(message -> {
                dropStateService.startDrop(channelId, message.getId(), DropStateService.DropType.ITEM, partToDrop.getId());
                log.info("[PartDropScheduler] Dropped part {} ({}) in channel {}.", partToDrop.getName(), partToDrop.getId(), channelId);

                expirationScheduler.schedule(() -> {
                    dropStateService.expireDrop(channelId, message.getId()).ifPresent(expiredDrop -> {
                        message.delete().queue(
                            success -> log.info("[PartDropScheduler] Expired and deleted part drop message {}.", message.getId()),
                            error -> log.error("[PartDropScheduler] Failed to delete expired drop message {}: {}", message.getId(), error.getMessage())
                        );
                    });
                }, 30, TimeUnit.SECONDS);
            }, error -> {
                log.error("[PartDropScheduler] Failed to send part drop message to channel {}: {}", channelId, error.getMessage());
            });

        } catch (Exception e) {
            log.error("[PartDropScheduler] Error during scheduled part drop: {}", e.getMessage(), e);
        } finally {
            partDropLock.unlock();
        }
    }
} 