package com.app.heartbound.services;

import com.app.heartbound.dto.UserDTO;
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
            existingUser.setAvatar(userDTO.getAvatar());
            existingUser.setEmail(userDTO.getEmail());
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
     * Retrieves a user by their ID.
     *
     * @param id the user identifier
     * @return the User entity if found, otherwise null
     */
    public User getUserById(String id) {
        return userRepository.findById(id).orElse(null);
    }
}
