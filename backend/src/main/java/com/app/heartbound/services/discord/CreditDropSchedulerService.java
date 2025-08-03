package com.app.heartbound.services.discord;

import com.app.heartbound.entities.DiscordBotSettings;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.ScheduledFuture;

@Service
@RequiredArgsConstructor
@Slf4j
public class CreditDropSchedulerService {

    private final DiscordBotSettingsService discordBotSettingsService;
    private final DropStateService dropStateService;
    private final JDA jda;
    private final Random random = new Random();
    private final ReentrantLock creditDropLock = new ReentrantLock();
    private final ScheduledExecutorService expirationScheduler = Executors.newSingleThreadScheduledExecutor();

    @Scheduled(fixedRate = 60000) // Runs every minute
    public void scheduledCreditDrop() {
        DiscordBotSettings settings = discordBotSettingsService.getDiscordBotSettings();
        if (settings == null || !settings.getCreditDropEnabled()) {
            log.debug("[CreditDropScheduler] Credit drops are disabled. Skipping run.");
            return;
        }

        if (random.nextDouble() > settings.getCreditDropChance()) {
             log.debug("[CreditDropScheduler] No credit drop this time. Skipping.");
             return;
        }
        
        if (!creditDropLock.tryLock()) {
            log.warn("[CreditDropScheduler] Previous credit drop run still in progress. Skipping this cycle.");
            return;
        }
        
        try {
            String channelId = settings.getCreditDropChannelId();
            if (channelId == null || channelId.isBlank()) {
                log.warn("[CreditDropScheduler] Credit drop channel ID is not configured.");
                return;
            }

            if (dropStateService.hasActiveDrop(channelId)) {
                log.debug("[CreditDropScheduler] An active drop already exists in channel {}. Skipping.", channelId);
                return;
            }

            int minAmount = settings.getCreditDropMinAmount();
            int maxAmount = settings.getCreditDropMaxAmount();
            int amount = random.nextInt(maxAmount - minAmount + 1) + minAmount;

            TextChannel channel = jda.getTextChannelById(channelId);
            if (channel == null) {
                log.warn("[CreditDropScheduler] Credit drop channel with ID {} not found.", channelId);
                return;
            }

            EmbedBuilder embed = new EmbedBuilder()
                .setDescription("Someone has dropped 🪙 **" + amount + "** credits!")
                .setFooter("type 'grab' to collect the credits!")
                .setColor(new Color(52, 152, 219)); // A pleasant blue color

            channel.sendMessageEmbeds(embed.build()).queue(message -> {
                // Schedule expiration
                ScheduledFuture<?> expirationTask = expirationScheduler.schedule(() -> {
                    dropStateService.expireDrop(channelId, message.getId()).ifPresent(expiredDrop -> {
                        message.delete().queue(
                            success -> log.info("[CreditDropScheduler] Expired and deleted credit drop message {}.", message.getId()),
                            error -> log.error("[CreditDropScheduler] Failed to delete expired drop message {}: {}", message.getId(), error.getMessage())
                        );
                    });
                }, 4, TimeUnit.SECONDS);

                dropStateService.startDrop(channelId, message.getId(), DropStateService.DropType.CREDIT, amount, expirationTask);
                log.info("[CreditDropScheduler] Dropped {} credits in channel {}.", amount, channelId);

            }, error -> {
                log.error("[CreditDropScheduler] Failed to send credit drop message to channel {}: {}", channelId, error.getMessage());
            });

        } catch (Exception e) {
            log.error("[CreditDropScheduler] Error during scheduled credit drop: {}", e.getMessage(), e);
        } finally {
            creditDropLock.unlock();
        }
    }
} 