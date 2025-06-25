package com.app.heartbound.controllers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.beans.factory.annotation.Value;
import jakarta.validation.constraints.Pattern;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

@RestController
public class ResourceController {
    private static final Logger logger = LoggerFactory.getLogger(ResourceController.class);
    
    @Value("${spring.web.resources.static-locations:classpath:/static/}")
    private String[] staticLocations;
    
    @GetMapping(value = "/images/ranks/{rank}.png", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> getRankImage(
            @PathVariable @Pattern(regexp = "^[a-zA-Z0-9_-]+$", message = "Invalid rank format") String rank) {
        // First try loading from classpath
        String imagePath = "static/images/ranks/" + rank.toLowerCase() + ".png";
        logger.debug("Attempting to load image from classpath: {}", imagePath);
        
        try {
            // Try classpath resources first
            Resource resource = new ClassPathResource(imagePath);
            
            if (!resource.exists()) {
                logger.warn("Image not found in classpath: {}", imagePath);
                
                // Try file system path as backup
                String filePath = System.getProperty("user.dir") + "/src/main/resources/" + imagePath;
                logger.debug("Attempting to load from file system: {}", filePath);
                
                File file = new File(filePath);
                if (file.exists()) {
                    logger.debug("Found image at file system path: {}", filePath);
                    return ResponseEntity.ok().body(Files.readAllBytes(file.toPath()));
                }
                
                logger.error("Image not found anywhere: {}", rank);
                return ResponseEntity.notFound().build();
            }
            
            byte[] imageBytes = StreamUtils.copyToByteArray(resource.getInputStream());
            logger.debug("Successfully loaded image from classpath: {} ({} bytes)", rank, imageBytes.length);
            return ResponseEntity.ok().body(imageBytes);
        } catch (IOException e) {
            logger.error("Error loading image {}: {}", rank, e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }
} 