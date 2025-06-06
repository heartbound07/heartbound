package com.app.heartbound.services.security;

import com.app.heartbound.entities.Pairing;
import com.app.heartbound.repositories.UserRepository;
import com.app.heartbound.repositories.pairing.PairingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * PairingSecurityService
 * 
 * Service for handling pairing-related security validations.
 * Ensures only authorized users can perform actions on specific pairings.
 */
@Service("pairingSecurityService")
@RequiredArgsConstructor
@Slf4j
public class PairingSecurityService {

    private final PairingRepository pairingRepository;
    private final UserRepository userRepository;

    /**
     * Check if the authenticated user is involved in the specified pairing
     * 
     * @param authentication The current authentication object
     * @param pairingId The ID of the pairing to check
     * @return true if the user is involved in the pairing, false otherwise
     */
    public boolean isUserInPairing(Authentication authentication, Long pairingId) {
        if (authentication == null || pairingId == null) {
            log.warn("Authentication or pairingId is null - denying access");
            return false;
        }

        String currentUserId = authentication.getName();
        if (currentUserId == null || currentUserId.trim().isEmpty()) {
            log.warn("No user ID found in authentication - denying access");
            return false;
        }

        try {
            Optional<Pairing> pairingOpt = pairingRepository.findById(pairingId);
            if (pairingOpt.isEmpty()) {
                log.warn("Pairing not found with ID: {} - denying access", pairingId);
                return false;
            }

            Pairing pairing = pairingOpt.get();
            boolean isInvolved = pairing.involvesUser(currentUserId);
            
            if (isInvolved) {
                log.info("User {} is authorized to access pairing {}", currentUserId, pairingId);
            } else {
                log.warn("User {} is NOT authorized to access pairing {} (users: {} and {})", 
                        currentUserId, pairingId, pairing.getUser1Id(), pairing.getUser2Id());
            }
            
            return isInvolved;
            
        } catch (Exception e) {
            log.error("Error checking pairing access for user {} and pairing {}: {}", 
                     currentUserId, pairingId, e.getMessage());
            return false;
        }
    }
} 