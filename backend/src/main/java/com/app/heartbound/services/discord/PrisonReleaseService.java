package com.app.heartbound.services.discord;

import com.app.heartbound.entities.User;
import com.app.heartbound.repositories.UserRepository;
import com.app.heartbound.services.PrisonService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@Slf4j
public class PrisonReleaseService {

    private final UserRepository userRepository;
    private final PrisonService prisonService;
    private final JDA jda;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    private static final String PRISON_ROLE_ID = "1387934212216328202";

    @Autowired
    public PrisonReleaseService(UserRepository userRepository, PrisonService prisonService, @Lazy JDA jda) {
        this.userRepository = userRepository;
        this.prisonService = prisonService;
        this.jda = jda;
    }

    @PostConstruct
    public void reconcilePendingReleases() {
        log.info("Reconciling pending prison releases on startup...");
        List<User> usersToRelease = userRepository.findByPrisonReleaseAtIsNotNull();
        LocalDateTime now = LocalDateTime.now();

        for (User user : usersToRelease) {
            if (user.getPrisonReleaseAt().isBefore(now) || user.getPrisonReleaseAt().isEqual(now)) {
                log.info("Found past-due release for user {}. Releasing immediately.", user.getId());
                releaseUser(user.getId());
            } else {
                log.info("Found future release for user {}. Scheduling release.", user.getId());
                scheduleRelease(user);
            }
        }
        log.info("Finished reconciling {} pending releases.", usersToRelease.size());
    }

    public void scheduleRelease(User user) {
        if (user.getPrisonReleaseAt() == null) {
            return;
        }

        long delay = Duration.between(LocalDateTime.now(), user.getPrisonReleaseAt()).toMillis();
        if (delay <= 0) {
            releaseUser(user.getId());
        } else {
            scheduler.schedule(() -> releaseUser(user.getId()), delay, TimeUnit.MILLISECONDS);
            log.info("Scheduled release for user {} in {} ms.", user.getId(), delay);
        }
    }

    private void releaseUser(String userId) {
        log.info("Attempting to automatically release user {}", userId);
        try {
            Guild guild = jda.getGuilds().get(0); // Assuming the bot is in one guild
            if (guild == null) {
                log.error("Cannot release user {}: Bot is not in any guild.", userId);
                return;
            }

            // Must use retrieveMemberById to get an up-to-date member object
            guild.retrieveMemberById(userId).queue(
                member -> {
                    if (member == null) {
                        log.warn("User {} to be released was not found in the guild. Releasing from database.", userId);
                        prisonService.releaseUser(userId);
                        return;
                    }

                    Role prisonRole = guild.getRoleById(PRISON_ROLE_ID);
                    if (prisonRole == null) {
                        log.error("Prison role {} not found. Cannot release user {}.", PRISON_ROLE_ID, userId);
                        return;
                    }

                    // Get original roles from database
                    User user = userRepository.findById(userId).orElse(null);
                    if (user == null || user.getOriginalRoleIds() == null) {
                        log.warn("No stored roles found for user {} during auto-release. Removing prison role only.", userId);
                        guild.removeRoleFromMember(member, prisonRole).queue(
                            success -> {
                                prisonService.releaseUser(userId);
                                log.info("Removed prison role from {} as no roles were stored.", member.getEffectiveName());
                            },
                            error -> log.error("Failed to remove prison role from {}", member.getEffectiveName(), error)
                        );
                        return;
                    }

                    List<Role> rolesToRestore = user.getOriginalRoleIds().stream()
                        .map(guild::getRoleById)
                        .filter(role -> role != null && guild.getSelfMember().canInteract(role))
                        .collect(Collectors.toList());

                    // Restore roles and remove prison role
                    guild.modifyMemberRoles(member, rolesToRestore, List.of(prisonRole)).queue(
                        success -> {
                            prisonService.releaseUser(userId);
                            log.info("Successfully auto-released user {}", userId);
                        },
                        error -> {
                            log.error("Failed to modify roles for auto-release of user {}.", userId, error);
                        }
                    );
                },
                failure -> {
                     log.warn("User {} to be released was not found in the guild (on failure). Releasing from database only.", userId);
                     prisonService.releaseUser(userId);
                }
            );
        } catch (Exception e) {
            log.error("An unexpected error occurred during auto-release for user {}", userId, e);
        }
    }

    @PreDestroy
    public void shutdown() {
        log.info("Shutting down PrisonReleaseService scheduler.");
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS))
                    log.error("Scheduler did not terminate.");
            }
        } catch (InterruptedException ie) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
} 