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

        LFGParty party = new LFGParty();
        party.setUserId(userId);
        party.setGame(dto.getGame());
        party.setTitle(dto.getTitle());
        party.setDescription(dto.getDescription());
        // Map the party requirements from the DTO
        LFGParty.PartyRequirements req = new LFGParty.PartyRequirements(
                dto.getRequirements().getRank(),
                dto.getRequirements().getRegion(),
                dto.getRequirements().getVoiceChat()
        );
        party.setRequirements(req);
        party.setExpiresIn(dto.getExpiresIn());
        party.setMaxPlayers(dto.getMaxPlayers());
        party.setStatus("open");
        Instant now = Instant.now();
        party.setCreatedAt(now);
        party.setExpiresAt(now.plus(dto.getExpiresIn(), ChronoUnit.MINUTES));
        party.setParticipants(new HashSet<>());

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
            if (dto.getRequirements().getVoiceChat() != null) {
                req.setVoiceChat(dto.getRequirements().getVoiceChat());
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

        LFGParty updatedParty = lfgPartyRepository.save(party);
        return mapToResponseDTO(updatedParty);
    }

    /**
     * Deletes a party.
     *
     * @param id the UUID of the party to delete
     */
    public void deleteParty(UUID id) {
        String userId = getCurrentUserId();

        LFGParty party = lfgPartyRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Party not found with id: " + id));
        if (!party.getUserId().equals(userId)) {
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

        if (!"open".equalsIgnoreCase(party.getStatus())) {
            throw new IllegalStateException("Party is not open for joining");
        }
        if (party.getExpiresAt().isBefore(Instant.now())) {
            throw new IllegalStateException("Party has expired");
        }
        if (party.getUserId().equals(userId)) {
            throw new IllegalArgumentException("Party owner cannot join their own party");
        }
        if (party.getParticipants().size() >= party.getMaxPlayers()) {
            throw new IllegalStateException("Party is full");
        }
        if (party.getParticipants().contains(userId)) {
            throw new IllegalArgumentException("User has already joined the party");
        }
        party.getParticipants().add(userId);
        // If party reaches maximum capacity, mark it as closed.
        if (party.getParticipants().size() >= party.getMaxPlayers()) {
            party.setStatus("closed");
        }
        lfgPartyRepository.save(party);
        return "Join request successful. You have joined the party.";
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
                        .voiceChat(party.getRequirements().isVoiceChat())
                        .build())
                .expiresIn(party.getExpiresIn())
                .maxPlayers(party.getMaxPlayers())
                .status(party.getStatus())
                .createdAt(party.getCreatedAt())
                .expiresAt(party.getExpiresAt())
                .participants(party.getParticipants())
                .build();
    }
}
