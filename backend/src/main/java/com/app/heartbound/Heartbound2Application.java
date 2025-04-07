package com.app.heartbound;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class Heartbound2Application {

	private static final Logger logger = LoggerFactory.getLogger(Heartbound2Application.class);

	@Value("${discord.token:NOT_SET}") // Read the token, provide default if missing
	private String discordToken;

	public static void main(String[] args) {
		SpringApplication.run(Heartbound2Application.class, args);
	}

	@PostConstruct
	public void checkDiscordToken() {
		// Log the token value Spring Boot sees AFTER properties are loaded
		logger.info("Discord Token read by Spring: [{}]", discordToken);
		if ("NOT_SET".equals(discordToken) || discordToken == null || discordToken.trim().isEmpty()) {
			logger.error("CRITICAL: Discord token is missing or empty in application properties/environment!");
		} else {
			logger.info("Discord token seems to be loaded correctly.");
		}
	}

}
