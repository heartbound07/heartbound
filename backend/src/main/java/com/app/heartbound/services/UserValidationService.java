package com.app.heartbound.services;

import com.app.heartbound.entities.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserValidationService {

    public void validateUserForPairing(User user) {
        // Basic validation can be added here in the future if needed
        // Currently no validation is required since role-based validation was removed
    }
} 