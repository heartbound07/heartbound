package com.app.heartbound.controllers.lfg;

import com.app.heartbound.dto.lfg.CreatePartyRequestDTO;
import com.app.heartbound.dto.lfg.LFGPartyResponseDTO;
import com.app.heartbound.dto.lfg.UpdatePartyRequestDTO;
import com.app.heartbound.dto.lfg.LFGPartyEventDTO;
import com.app.heartbound.entities.LFGParty;
import com.app.heartbound.services.lfg.LFGPartyService;
import com.app.heartbound.services.lfg.LFGSecurityService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;
import java.util.Set;

@RestController
@RequestMapping("/lfg/parties")
@PreAuthorize("hasRole('USER')")
public class LFGPartyController {

    private final LFGPartyService partyService;
    private final SimpMessagingTemplate messagingTemplate;
    private final LFGSecurityService lfgSecurityService;

    public LFGPartyController(LFGPartyService partyService, SimpMessagingTemplate messagingTemplate, LFGSecurityService lfgSecurityService) {
        this.partyService = partyService;
        this.messagingTemplate = messagingTemplate;
        this.lfgSecurityService = lfgSecurityService;
    }

    /**
     * Create a new party.
     *
     * @param dto the party creation request DTO
     * @return the created party details
     */
    @PostMapping
    public LFGPartyResponseDTO createParty(@RequestBody CreatePartyRequestDTO dto) {
        LFGPartyResponseDTO createdParty = partyService.createParty(dto);
        LFGPartyEventDTO event = LFGPartyEventDTO.builder()
              .eventType("PARTY_CREATED")
              .party(createdParty)
              .message("Party update: New party created: " + createdParty.getId())
              .build();
        messagingTemplate.convertAndSend("/topic/party", event);
        return createdParty;
    }

    /**
     * Retrieve a party by its UUID.
     *
     * @param id the UUID of the party
     * @return the party details
     */
    @GetMapping("/{id}")
    public LFGPartyResponseDTO getParty(@PathVariable UUID id) {
        return partyService.getPartyById(id);
    }

    /**
     * List parties with dynamic filtering and pagination.
     *
     * @param pageable pagination details
     * @param game optional filter by game name
     * @param title optional filter by title keyword
     * @param status optional filter by party status
     * @return paginated list of party responses
     */
    @GetMapping
    public Page<LFGPartyResponseDTO> listParties(Pageable pageable,
                                                 @RequestParam(required = false) String game,
                                                 @RequestParam(required = false) String title,
                                                 @RequestParam(required = false) String status) {
        Specification<LFGParty> spec = Specification.where(null);

        if (game != null && !game.trim().isEmpty()) {
            spec = spec.and((root, query, criteriaBuilder) ->
                    criteriaBuilder.equal(root.get("game"), game));
        }
        if (title != null && !title.trim().isEmpty()) {
            spec = spec.and((root, query, criteriaBuilder) ->
                    criteriaBuilder.like(root.get("title"), "%" + title + "%"));
        }
        if (status != null && !status.trim().isEmpty()) {
            spec = spec.and((root, query, criteriaBuilder) ->
                    criteriaBuilder.equal(root.get("status"), status));
        }
        return partyService.listParties(spec, pageable);
    }

    /**
     * Update an existing party.
     *
     * @param id  the UUID of the party to update
     * @param dto the party update request DTO
     * @return the updated party details
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('USER') and @lfgSecurityService.isPartyLeader(authentication, #id)")
    public LFGPartyResponseDTO updateParty(@PathVariable UUID id,
                                           @RequestBody UpdatePartyRequestDTO dto) {
        LFGPartyResponseDTO updatedParty = partyService.updateParty(id, dto);
        LFGPartyEventDTO event = LFGPartyEventDTO.builder()
              .eventType("PARTY_UPDATED")
              .party(updatedParty)
              .message("Party update: Party " + id + " has been updated.")
              .build();
        messagingTemplate.convertAndSend("/topic/party", event);
        return updatedParty;
    }

    /**
     * Delete a party.
     *
     * @param id the UUID of the party to delete
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('USER') and @lfgSecurityService.isPartyLeader(authentication, #id)")
    public void deleteParty(@PathVariable UUID id) {
        // Get party details before deletion to include in the event
        LFGPartyResponseDTO partyToDelete = partyService.getPartyById(id);
        
        
        // Delete the party
        partyService.deleteParty(id);
        
        // Create the event with the deleted party details
        LFGPartyEventDTO event = LFGPartyEventDTO.builder()
                .eventType("PARTY_DELETED")
                .party(partyToDelete)  // Include the full party object so frontend has the ID
                .message("Party update: Party " + id + " has been deleted.")
                .build();
        
        // Broadcast to all subscribers
        messagingTemplate.convertAndSend("/topic/party", event);
    }

    /**
     * Join a party.
     *
     * @param id the UUID of the party to join
     * @return success message confirming party join
     */
    @PostMapping("/{id}/join")
    public String joinParty(@PathVariable UUID id) {
        String result = partyService.joinParty(id);
        LFGPartyResponseDTO updatedParty = partyService.getPartyById(id);
        LFGPartyEventDTO event = LFGPartyEventDTO.builder()
              .eventType("PARTY_JOINED")
              .party(updatedParty)
              .message("Party update: User joined party " + id)
              .build();
        messagingTemplate.convertAndSend("/topic/party", event);
        return result;
    }

    @PostMapping("/{id}/leave")
    public String leaveParty(@PathVariable UUID id) {
        String result = partyService.leaveParty(id);
        LFGPartyResponseDTO updatedParty = partyService.getPartyById(id);
        LFGPartyEventDTO event = LFGPartyEventDTO.builder()
              .eventType("PARTY_LEFT")
              .party(updatedParty)
              .message("Party update: User left party " + id)
              .build();
        messagingTemplate.convertAndSend("/topic/party", event);
        return result;
    }

    @PostMapping("/{id}/kick/{userId}")
    @PreAuthorize("hasRole('USER') and @lfgSecurityService.isPartyLeader(authentication, #id)")
    public String kickUserFromParty(@PathVariable UUID id, @PathVariable String userId) {
        String result = partyService.kickUserFromParty(id, userId);
        LFGPartyResponseDTO updatedParty = partyService.getPartyById(id);
        LFGPartyEventDTO event = LFGPartyEventDTO.builder()
              .eventType("PARTY_USER_KICKED")
              .party(updatedParty)
              .message("Party update: User was kicked from party " + id)
              .build();
        messagingTemplate.convertAndSend("/topic/party", event);
        return result;
    }

    /**
     * Invite a user to join a party
     */
    @PostMapping("/{id}/invite/{userId}")
    @PreAuthorize("hasRole('USER') and @lfgSecurityService.isPartyLeader(authentication, #id)")
    public String inviteUserToParty(@PathVariable UUID id, @PathVariable String userId) {
        String result = partyService.inviteUserToParty(id, userId);
        LFGPartyResponseDTO updatedParty = partyService.getPartyById(id);
        LFGPartyEventDTO event = LFGPartyEventDTO.builder()
              .eventType("PARTY_USER_INVITED")
              .party(updatedParty)
              .message("Party update: User was invited to party " + id)
              .build();
        messagingTemplate.convertAndSend("/topic/party", event);
        return result;
    }

    /**
     * Accept an invitation to join a party
     */
    @PostMapping("/{id}/accept-invite")
    public String acceptInvitation(@PathVariable UUID id) {
        String result = partyService.acceptInvitation(id);
        LFGPartyResponseDTO updatedParty = partyService.getPartyById(id);
        LFGPartyEventDTO event = LFGPartyEventDTO.builder()
              .eventType("PARTY_INVITATION_ACCEPTED")
              .party(updatedParty)
              .message("Party update: User accepted invitation to party " + id)
              .build();
        messagingTemplate.convertAndSend("/topic/party", event);
        return result;
    }

    /**
     * Get all invited users for a party
     */
    @GetMapping("/{id}/invites")
    public Set<String> getInvitedUsers(@PathVariable UUID id) {
        return partyService.getInvitedUsers(id);
    }

    /**
     * Request to join a party when it's invite-only
     */
    @PostMapping("/{id}/request-join")
    public String requestToJoinParty(@PathVariable UUID id) {
        String result = partyService.requestToJoinParty(id);
        
        // Get the updated party details - wrap this in a try-catch to prevent 500 error
        try {
            LFGPartyResponseDTO updatedParty = partyService.getPartyById(id);
            LFGPartyEventDTO event = LFGPartyEventDTO.builder()
                   .eventType("PARTY_JOIN_REQUEST")
                   .party(updatedParty)
                   .message("Party update: User has requested to join party " + id)
                   .build();
            messagingTemplate.convertAndSend("/topic/party", event);
        } catch (Exception e) {
            // Log the error but don't fail the entire request
            System.err.println("Error sending party join request event: " + e.getMessage());
        }
        
        return result;
    }

    /**
     * Accept a join request for a party.
     *
     * @param id     the UUID of the party
     * @param userId the ID of the user whose request is being accepted
     * @return success message if acceptance succeeds
     */
    @PostMapping("/{id}/accept-join-request/{userId}")
    @PreAuthorize("hasRole('USER') and @lfgSecurityService.isPartyLeader(authentication, #id)")
    public String acceptJoinRequest(@PathVariable UUID id, @PathVariable String userId) {
        String result = partyService.acceptJoinRequest(id, userId);
        LFGPartyResponseDTO updatedParty = partyService.getPartyById(id);
        LFGPartyEventDTO event = LFGPartyEventDTO.builder()
                .eventType("PARTY_JOIN_REQUEST_ACCEPTED")
                .party(updatedParty)
                .targetUserId(userId)
                .message("Party update: Join request accepted for user " + userId + " in party " + id)
                .build();
        messagingTemplate.convertAndSend("/topic/party", event);
        return result;
    }

    /**
     * Reject a join request for a party.
     *
     * @param id     the UUID of the party
     * @param userId the ID of the user whose request is being rejected
     * @return success message if rejection succeeds
     */
    @PostMapping("/{id}/reject-join-request/{userId}")
    @PreAuthorize("hasRole('USER') and @lfgSecurityService.isPartyLeader(authentication, #id)")
    public String rejectJoinRequest(@PathVariable UUID id, @PathVariable String userId) {
        String result = partyService.rejectJoinRequest(id, userId);
        LFGPartyResponseDTO updatedParty = partyService.getPartyById(id);
        LFGPartyEventDTO event = LFGPartyEventDTO.builder()
                .eventType("PARTY_JOIN_REQUEST_REJECTED")
                .party(updatedParty)
                .targetUserId(userId)
                .message("Party update: Join request rejected for user " + userId + " in party " + id)
                .build();
        messagingTemplate.convertAndSend("/topic/party", event);
        return result;
    }
}
