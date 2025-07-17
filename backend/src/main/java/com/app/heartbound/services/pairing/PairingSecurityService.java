package com.app.heartbound.services.pairing;

import com.app.heartbound.entities.Pairing;
import com.app.heartbound.repositories.pairing.PairingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * PairingSecurityService
 * 
 * Service for handling pairing-related security validations.
 * Ensures only authorized users can perform actions on specific pairings.
 * ADMIN users have access to all pairings for management purposes.
 */
@Service("pairingSecurityService")
@RequiredArgsConstructor
@Slf4j
public class PairingSecurityService {

    private final PairingRepository pairingRepository;

    /**
     * Check if the authenticated user is involved in the specified pairing OR has ADMIN role
     * 
     * @param authentication The current authentication object
     * @param pairingId The ID of the pairing to check
     * @return true if the user is involved in the pairing OR has ADMIN role, false otherwise
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
            // CRITICAL FIX: Check if user has ADMIN role first
            boolean hasAdminRole = authentication.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_ADMIN"));
            if (hasAdminRole) {
                log.info("ADMIN user {} granted access to pairing {} for management purposes", currentUserId, pairingId);
                return true;
            }

            // Check if pairing exists
            Optional<Pairing> pairingOpt = pairingRepository.findById(pairingId);
            if (pairingOpt.isEmpty()) {
                log.warn("Pairing not found with ID: {} - denying access", pairingId);
                return false;
            }

            // Check if user is involved in the pairing
            Pairing pairing = pairingOpt.get();
            boolean isInvolved = pairing.involvesUser(currentUserId);
            
            if (isInvolved) {
                log.info("User {} is authorized to access pairing {} (member of pairing)", currentUserId, pairingId);
            } else {
                log.warn("User {} is NOT authorized to access pairing {} (not a member and not admin) - users in pairing: {} and {}", 
                        currentUserId, pairingId, pairing.getUser1Id(), pairing.getUser2Id());
            }
            
            return isInvolved;
            
        } catch (Exception e) {
            log.error("Error checking pairing access for user {} and pairing {}: {}", 
                     currentUserId, pairingId, e.getMessage());
            return false;
        }
    }

    /**
     * Check if the authenticated user has ADMIN role
     * 
     * @param authentication The current authentication object
     * @return true if the user has ADMIN role, false otherwise
     */
    public boolean hasAdminRole(Authentication authentication) {
        if (authentication == null) {
            return false;
        }
        
        return authentication.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }
} 