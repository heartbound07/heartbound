package com.app.heartbound.controllers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import jakarta.validation.constraints.Pattern;

import java.io.IOException;
import java.io.InputStream;

@RestController
public class ResourceController {
    private static final Logger logger = LoggerFactory.getLogger(ResourceController.class);

    @Autowired
    private ResourceLoader resourceLoader;

    @GetMapping(value = "/images/ranks/{rank}.png", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> getRankImage(
            @PathVariable @Pattern(regexp = "^[a-zA-Z0-9_-]+$", message = "Invalid rank format") String rank) {
        String imagePath = "static/images/ranks/" + rank.toLowerCase() + ".png";
        logger.debug("Attempting to load image from classpath: {}", imagePath);

        Resource resource = resourceLoader.getResource("classpath:" + imagePath);

        if (resource.exists() && resource.isReadable()) {
            try (InputStream inputStream = resource.getInputStream()) {
                byte[] imageBytes = StreamUtils.copyToByteArray(inputStream);
                logger.debug("Successfully loaded image from classpath: {} ({} bytes)", rank, imageBytes.length);
                return ResponseEntity.ok().body(imageBytes);
            } catch (IOException e) {
                logger.error("Error loading image {}: {}", rank, e.getMessage(), e);
                return ResponseEntity.internalServerError().build();
            }
        } else {
            // Fallback for default avatar if rank image is not found
            String defaultAvatarPath = "static/images/ranks/default-avatar.png";
            logger.warn("Rank image not found: {}. Falling back to default avatar.", imagePath);
            Resource defaultAvatarResource = resourceLoader.getResource("classpath:" + defaultAvatarPath);
            if (defaultAvatarResource.exists() && defaultAvatarResource.isReadable()) {
                try (InputStream inputStream = defaultAvatarResource.getInputStream()) {
                    return ResponseEntity.ok().body(StreamUtils.copyToByteArray(inputStream));
                } catch (IOException e) {
                    logger.error("Error loading default avatar: {}", e.getMessage(), e);
                    return ResponseEntity.status(500).build();
                }
            }
            logger.error("Default avatar not found at {}", defaultAvatarPath);
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping(value = "/images/default-avatar.png", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> getDefaultAvatar() {
        String imagePath = "static/images/ranks/default-avatar.png";
        logger.debug("Attempting to load default avatar from classpath: {}", imagePath);

        Resource resource = new ClassPathResource(imagePath);

        if (resource.exists() && resource.isReadable()) {
            try (InputStream inputStream = resource.getInputStream()) {
                byte[] imageBytes = StreamUtils.copyToByteArray(inputStream);
                logger.debug("Successfully loaded default avatar from classpath: {} bytes", imageBytes.length);
                return ResponseEntity.ok().body(imageBytes);
            } catch (IOException e) {
                logger.error("Error loading default avatar: {}", e.getMessage());
                return ResponseEntity.internalServerError().build();
            }
        } else {
             logger.error("Default avatar not found anywhere.");
             return ResponseEntity.notFound().build();
        }
    }
} 