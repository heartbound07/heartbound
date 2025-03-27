package com.app.heartbound.services.lfg;

import com.app.heartbound.dto.lfg.CreatePartyRequestDTO;
import com.app.heartbound.dto.lfg.LFGPartyResponseDTO;
import com.app.heartbound.dto.lfg.UpdatePartyRequestDTO;
import com.app.heartbound.entities.LFGParty;
import com.app.heartbound.exceptions.ResourceNotFoundException;
import com.app.heartbound.exceptions.UnauthorizedOperationException;
import com.app.heartbound.repositories.lfg.LFGPartyRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.UUID;

/**
 * LFGPartyService
 *
 * Service layer that encapsulates business logic for LFG party operations.
 * Ensures proper security checks (e.g., owner verifications) and validations.
 *
 * This service provides methods for:
 * - Creating a new party.
 * - Updating an existing party.
 * - Deleting a party.
 * - Retrieving party details.
 * - Listing parties with dynamic filtering and pagination.
 * - Joining a party.
 */
@Service
public class LFGPartyService {

    private final LFGPartyRepository lfgPartyRepository;

    public LFGPartyService(LFGPartyRepository lfgPartyRepository) {
        this.lfgPartyRepository = lfgPartyRepository;
    }

    /**
     * Helper method to fetch the current authenticated user's ID
     *
     * @return the user ID from the Security Context
     */
    private String getCurrentUserId() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof String) {
            return (String) principal;
        }
        throw new IllegalStateException("User not authenticated");
    }

    /**
     * Creates a new LFG party.
     *
     * @param dto the party creation request data
     * @return the created party as LFGPartyResponseDTO
     */
    public LFGPartyResponseDTO createParty(CreatePartyRequestDTO dto) {
        String userId = getCurrentUserId();

        if (lfgPartyRepository.findByUserId(userId).isPresent()) {
            throw new IllegalStateException("You can only create one party");
        }

        LFGParty party = new LFGParty();
        party.setUserId(userId);
        party.setGame(dto.getGame());
        party.setTitle(dto.getTitle());
        party.setDescription(dto.getDescription());
        // Map the party requirements from the DTO
        LFGParty.PartyRequirements req = new LFGParty.PartyRequirements(
                dto.getRequirements().getRank(),
                dto.getRequirements().getRegion(),
                dto.getRequirements().getInviteOnly()
        );
        party.setRequirements(req);
        party.setExpiresIn(dto.getExpiresIn());
        party.setMaxPlayers(dto.getMaxPlayers());
        party.setStatus("open");
        Instant now = Instant.now();
        party.setCreatedAt(now);
        party.setExpiresAt(now.plus(dto.getExpiresIn(), ChronoUnit.MINUTES));
        party.setParticipants(new HashSet<>());
        party.getParticipants().add(userId);
        party.setMatchType(dto.getMatchType());
        party.setGameMode(dto.getGameMode());
        party.setTeamSize(dto.getTeamSize());
        party.setVoicePreference(dto.getVoicePreference());
        party.setAgeRestriction(dto.getAgeRestriction());

        LFGParty savedParty = lfgPartyRepository.save(party);
        return mapToResponseDTO(savedParty);
    }

    /**
     * Retrieves party details by ID.
     *
     * @param id the party UUID
     * @return party details as LFGPartyResponseDTO
     */
    public LFGPartyResponseDTO getPartyById(UUID id) {
        LFGParty party = lfgPartyRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Party not found with id: " + id));
        return mapToResponseDTO(party);
    }

    /**
     * Lists parties using dynamic filtering and pagination.
     *
     * @param spec dynamic filtering criteria
     * @param pageable pagination details
     * @return a paginated list of LFGPartyResponseDTO
     */
    public Page<LFGPartyResponseDTO> listParties(Specification<LFGParty> spec, Pageable pageable) {
        Page<LFGParty> parties = lfgPartyRepository.findAll(spec, pageable);
        return parties.map(this::mapToResponseDTO);
    }

    /**
     * Updates an existing party.
     *
     * @param id the UUID of the party to update
     * @param dto the update request data
     * @return the updated party as LFGPartyResponseDTO
     */
    public LFGPartyResponseDTO updateParty(UUID id, UpdatePartyRequestDTO dto) {
        String userId = getCurrentUserId();

        LFGParty party = lfgPartyRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Party not found with id: " + id));

        // Verify that the user is the owner of the party
        if (!party.getUserId().equals(userId)) {
            throw new UnauthorizedOperationException("You are not authorized to update this party");
        }

        if (dto.getGame() != null) {
            party.setGame(dto.getGame());
        }
        if (dto.getTitle() != null) {
            party.setTitle(dto.getTitle());
        }
        if (dto.getDescription() != null) {
            party.setDescription(dto.getDescription());
        }
        if (dto.getRequirements() != null) {
            LFGParty.PartyRequirements req = party.getRequirements();
            if (req == null) {
                req = new LFGParty.PartyRequirements();
            }
            if (dto.getRequirements().getRank() != null) {
                req.setRank(dto.getRequirements().getRank());
            }
            if (dto.getRequirements().getRegion() != null) {
                req.setRegion(dto.getRequirements().getRegion());
            }
            if (dto.getRequirements().getInviteOnly() != null) {
                req.setInviteOnly(dto.getRequirements().getInviteOnly());
            }
            party.setRequirements(req);
        }
        if (dto.getExpiresIn() != null) {
            party.setExpiresIn(dto.getExpiresIn());
            party.setExpiresAt(party.getCreatedAt().plus(dto.getExpiresIn(), ChronoUnit.MINUTES));
        }
        if (dto.getMaxPlayers() != null) {
            party.setMaxPlayers(dto.getMaxPlayers());
        }

        // Update additional fields if provided
        if (dto.getMatchType() != null) {
            party.setMatchType(dto.getMatchType());
        }
        if (dto.getGameMode() != null) {
            party.setGameMode(dto.getGameMode());
        }
        if (dto.getTeamSize() != null) {
            party.setTeamSize(dto.getTeamSize());
        }
        if (dto.getVoicePreference() != null) {
            party.setVoicePreference(dto.getVoicePreference());
        }
        if (dto.getAgeRestriction() != null) {
            party.setAgeRestriction(dto.getAgeRestriction());
        }

        LFGParty updatedParty = lfgPartyRepository.save(party);
        return mapToResponseDTO(updatedParty);
    }

    /**
     * Deletes a party. Only the party leader or users with ADMIN/MODERATOR roles can do this.
     *
     * @param id the UUID of the party to delete
     */
    public void deleteParty(UUID id) {
        String currentUserId = getCurrentUserId();
        LFGParty party = lfgPartyRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Party not found with id: " + id));
        
        // Check if current user is the party leader or has admin/moderator role
        boolean isPartyLeader = party.getUserId().equals(currentUserId);
        boolean hasAdminRole = hasRole("ADMIN") || hasRole("MODERATOR");
        
        if (!isPartyLeader && !hasAdminRole) {
            throw new UnauthorizedOperationException("You are not authorized to delete this party");
        }
        
        lfgPartyRepository.delete(party);
    }

    /**
     * Allows an authenticated user to join a party.
     *
     * @param id the UUID of the party to join
     * @return success message if join succeeds
     */
    public String joinParty(UUID id) {
        String userId = getCurrentUserId();

        LFGParty party = lfgPartyRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Party not found with id: " + id));

        // If the party's status is anything but "open" (e.g., "closed"), joining is not allowed.
        if (!"open".equalsIgnoreCase(party.getStatus())) {
            throw new IllegalStateException("Party is not open for joining");
        }

        // Additional validations
        if (party.getUserId().equals(userId)) {
            throw new IllegalArgumentException("Party owner cannot join their own party");
        }
        if (party.getParticipants().contains(userId)) {
            throw new IllegalArgumentException("User has already joined the party");
        }
        if (party.getParticipants().size() >= party.getMaxPlayers()) {
            throw new IllegalStateException("Party is full");
        }

        party.getParticipants().add(userId);

        // If the party reaches maximum capacity, mark it as closed.
        if (party.getParticipants().size() >= party.getMaxPlayers()) {
            party.setStatus("closed");
        }
        lfgPartyRepository.save(party);
        return "Join request successful. You have joined the party.";
    }

    /**
     * Allows an authenticated user to leave a party.
     *
     * @param id the UUID of the party to leave
     * @return success message if leave succeeds
     */
    public String leaveParty(UUID id) {
        String userId = getCurrentUserId();
        LFGParty party = lfgPartyRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Party not found with id: " + id));

        // Prevent party leaders from "leaving" – they should delete the party instead.
        if (party.getUserId().equals(userId)) {
            throw new UnauthorizedOperationException("Party owner cannot leave the party. Delete the party instead.");
        }

        // Remove the user from participants.
        if (party.getParticipants() != null && party.getParticipants().contains(userId)) {
            party.getParticipants().remove(userId);
            // Reset status to "open" if the party now has available slots.
            if (party.getParticipants().size() < party.getMaxPlayers()) {
                party.setStatus("open");
            }
            lfgPartyRepository.save(party);
            return "Left party successfully.";
        }
        return "User was not a participant of this party.";
    }

    /**
     * Kicks a user from a party. Only the party leader or users with ADMIN/MODERATOR roles can do this.
     *
     * @param id the UUID of the party
     * @param userIdToKick the ID of the user to kick
     * @return success message if kick succeeds
     */
    public String kickUserFromParty(UUID id, String userIdToKick) {
        String currentUserId = getCurrentUserId();
        LFGParty party = lfgPartyRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Party not found with id: " + id));
        
        // Check if current user is the party leader or has admin/moderator role
        boolean isPartyLeader = party.getUserId().equals(currentUserId);
        boolean hasAdminRole = hasRole("ADMIN") || hasRole("MODERATOR");
        
        if (!isPartyLeader && !hasAdminRole) {
            throw new UnauthorizedOperationException("Only the party leader or administrators can kick users.");
        }
        
        // Party leader cannot be kicked
        if (party.getUserId().equals(userIdToKick)) {
            throw new UnauthorizedOperationException("Party leader cannot be kicked from their own party.");
        }
        
        // Check if the user to kick is actually in the party
        if (!party.getParticipants().contains(userIdToKick)) {
            throw new IllegalArgumentException("User is not in this party.");
        }
        
        // Remove user from participants
        party.getParticipants().remove(userIdToKick);
        
        // If party was full and is now not full, update status to open
        if (party.getStatus().equals("closed") && party.getParticipants().size() < party.getMaxPlayers()) {
            party.setStatus("open");
        }
        
        lfgPartyRepository.save(party);
        return "User has been kicked from the party.";
    }

    // Helper method to check if current user has a role
    private boolean hasRole(String role) {
        // This would be implemented based on your security context architecture
        // For example, you might check against UserDetails or a custom authentication token
        // This is a placeholder implementation
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_" + role));
    }

    /**
     * Helper method to convert an LFGParty entity to its response DTO.
     *
     * @param party the LFGParty entity
     * @return the mapped LFGPartyResponseDTO
     */
    private LFGPartyResponseDTO mapToResponseDTO(LFGParty party) {
        return LFGPartyResponseDTO.builder()
                .id(party.getId())
                .userId(party.getUserId())
                .game(party.getGame())
                .title(party.getTitle())
                .description(party.getDescription())
                .requirements(LFGPartyResponseDTO.PartyRequirementsDTO.builder()
                        .rank(party.getRequirements().getRank())
                        .region(party.getRequirements().getRegion())
                        .inviteOnly(party.getRequirements().isInviteOnly())
                        .build())
                .expiresIn(party.getExpiresIn())
                .maxPlayers(party.getMaxPlayers())
                .status(party.getStatus())
                .createdAt(party.getCreatedAt())
                .expiresAt(party.getExpiresAt())
                .participants(party.getParticipants())
                .matchType(party.getMatchType())
                .gameMode(party.getGameMode())
                .teamSize(party.getTeamSize())
                .voicePreference(party.getVoicePreference())
                .ageRestriction(party.getAgeRestriction())
                .build();
    }
}
