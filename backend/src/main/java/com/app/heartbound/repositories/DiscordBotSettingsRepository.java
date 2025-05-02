package com.app.heartbound.repositories;

import com.app.heartbound.entities.DiscordBotSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DiscordBotSettingsRepository extends JpaRepository<DiscordBotSettings, Long> {
} 