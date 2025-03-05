package com.app.heartbound.services;

import com.app.heartbound.dto.UserDTO;
import com.app.heartbound.dto.UpdateProfileDTO;
import com.app.heartbound.dto.UserProfileDTO;
import com.app.heartbound.entities.User;
import com.app.heartbound.repositories.UserRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class UserService {

    private final UserRepository userRepository;

    // Constructor-based dependency injection for UserRepository
    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Creates a new user or updates an existing user using the provided UserDTO data.
     *
     * @param userDTO the data transfer object containing user details
     * @return the saved User entity
     */
    public User createOrUpdateUser(UserDTO userDTO) {
        Optional<User> existingUserOpt = userRepository.findById(userDTO.getId());
        if (existingUserOpt.isPresent()) {
            User existingUser = existingUserOpt.get();
            existingUser.setUsername(userDTO.getUsername());
            existingUser.setDiscriminator(userDTO.getDiscriminator());
            existingUser.setEmail(userDTO.getEmail());
            
            // Only update avatar if it's null, default avatar, or from Discord
            // This preserves custom uploaded avatars
            if (shouldUpdateAvatar(existingUser.getAvatar(), userDTO.getAvatar())) {
                existingUser.setAvatar(userDTO.getAvatar());
            }
            
            return userRepository.save(existingUser);
        } else {
            User newUser = User.builder()
                    .id(userDTO.getId())
                    .username(userDTO.getUsername())
                    .discriminator(userDTO.getDiscriminator())
                    .avatar(userDTO.getAvatar())
                    .email(userDTO.getEmail())
                    .build();
            return userRepository.save(newUser);
        }
    }

    /**
     * Determines if the avatar should be updated based on current value.
     * 
     * @param currentAvatar the existing avatar in the database
     * @param newAvatar the avatar value from OAuth
     * @return true if avatar should be updated, false if it should be preserved
     */
    private boolean shouldUpdateAvatar(String currentAvatar, String newAvatar) {
        // If current avatar is null or empty, update it
        if (currentAvatar == null || currentAvatar.isEmpty()) {
            return true;
        }
        
        // If current avatar is the default, update it
        if (currentAvatar.equals("/default-avatar.png")) {
            return true;
        }
        
        // If current avatar is from Discord, update it
        if (currentAvatar.contains("cdn.discordapp.com")) {
            return true;
        }
        
        // Otherwise, it's a custom avatar, so don't update it
        return false;
    }

    /**
     * Retrieves a user by their ID.
     *
     * @param id the user identifier
     * @return the User entity if found, otherwise null
     */
    public User getUserById(String id) {
        return userRepository.findById(id).orElse(null);
    }

    /**
     * Updates profile information for a user.
     *
     * @param userId the ID of the user to update
     * @param profileDTO the profile data to update
     * @return the updated User entity or null if user not found
     */
    public User updateUserProfile(String userId, UpdateProfileDTO profileDTO) {
        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            
            // Log the incoming avatar URL
            System.out.println("Updating user profile for " + userId);
            System.out.println("Received avatar URL: " + profileDTO.getAvatar());
            
            // Update only the fields provided in the DTO
            if (profileDTO.getDisplayName() != null) {
                user.setDisplayName(profileDTO.getDisplayName());
            }
            
            if (profileDTO.getPronouns() != null) {
                user.setPronouns(profileDTO.getPronouns());
            }
            
            if (profileDTO.getAbout() != null) {
                user.setAbout(profileDTO.getAbout());
            }
            
            if (profileDTO.getBannerColor() != null) {
                user.setBannerColor(profileDTO.getBannerColor());
            }
            
            if (profileDTO.getAvatar() != null) {
                System.out.println("Setting avatar to: " + profileDTO.getAvatar());
                user.setAvatar(profileDTO.getAvatar());
            }
            
            if (profileDTO.getBannerUrl() != null) {
                System.out.println("Setting banner URL to: " + profileDTO.getBannerUrl());
                user.setBannerUrl(profileDTO.getBannerUrl());
            }
            
            User savedUser = userRepository.save(user);
            System.out.println("Saved user with avatar: " + savedUser.getAvatar());
            return savedUser;
        }
        
        return null;
    }

    /**
     * Enhanced mapToProfileDTO method that includes all profile fields.
     */
    public UserProfileDTO mapToProfileDTO(User user) {
        return UserProfileDTO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .avatar(user.getAvatar() != null ? user.getAvatar() : "/default-avatar.png")
                .displayName(user.getDisplayName())
                .pronouns(user.getPronouns())
                .about(user.getAbout())
                .bannerColor(user.getBannerColor())
                .bannerUrl(user.getBannerUrl())
                .build();
    }
}
