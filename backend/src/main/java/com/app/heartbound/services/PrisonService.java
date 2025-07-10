package com.app.heartbound.services;

import com.app.heartbound.config.CacheConfig;
import com.app.heartbound.entities.User;
import com.app.heartbound.exceptions.ResourceNotFoundException;
import com.app.heartbound.repositories.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class PrisonService {

    private static final Logger logger = LoggerFactory.getLogger(PrisonService.class);

    private final UserRepository userRepository;
    private final CacheConfig cacheConfig;

    @Autowired
    public PrisonService(UserRepository userRepository, CacheConfig cacheConfig) {
        this.userRepository = userRepository;
        this.cacheConfig = cacheConfig;
    }

    /**
     * Puts a user in prison by storing their original roles in the database.
     * This method is transactional.
     *
     * @param userId the ID of the user to prison
     * @param roleIds the list of role IDs to store
     * @return the updated User entity
     */
    @Transactional
    public User prisonUser(String userId, List<String> roleIds) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + userId));
        user.setOriginalRoleIds(roleIds);
        user.setPrisonedAt(LocalDateTime.now());
        User savedUser = userRepository.save(user);
        cacheConfig.invalidateUserProfileCache(userId);
        logger.info("User {} has been imprisoned in the database. Roles stored.", userId);
        return savedUser;
    }

    /**
     * Releases a user from prison by clearing their stored roles from the database.
     * This method is transactional.
     *
     * @param userId the ID of the user to release
     * @return the updated User entity
     */
    @Transactional
    public User releaseUser(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + userId));
        user.getOriginalRoleIds().clear();
        user.setPrisonedAt(null);
        User savedUser = userRepository.save(user);
        cacheConfig.invalidateUserProfileCache(userId);
        logger.info("User {} has been released from prison in the database. Stored roles cleared.", userId);
        return savedUser;
    }
} 