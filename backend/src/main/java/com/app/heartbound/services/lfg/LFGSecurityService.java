package com.app.heartbound.services.lfg;

import com.app.heartbound.entities.LFGParty;
import com.app.heartbound.repositories.lfg.LFGPartyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service("lfgSecurityService")
@RequiredArgsConstructor
public class LFGSecurityService {

    private final LFGPartyRepository partyRepository;

    /**
     * Checks if the authenticated user is the leader of the specified party.
     * @param authentication The user's authentication object.
     * @param partyId The UUID of the party to check.
     * @return true if the user is the party leader, false otherwise.
     */
    public boolean isPartyLeader(Authentication authentication, UUID partyId) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        String userId = authentication.getName();
        return partyRepository.findById(partyId)
                .map(LFGParty::getLeaderId)
                .map(leaderId -> leaderId.equals(userId))
                .orElse(false); // If party not found, deny access.
    }
} 