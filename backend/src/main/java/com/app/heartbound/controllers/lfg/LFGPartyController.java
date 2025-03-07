package com.app.heartbound.controllers.lfg;

import com.app.heartbound.dto.lfg.CreatePartyRequestDTO;
import com.app.heartbound.dto.lfg.LFGPartyResponseDTO;
import com.app.heartbound.dto.lfg.UpdatePartyRequestDTO;
import com.app.heartbound.dto.lfg.LFGPartyEventDTO;
import com.app.heartbound.entities.LFGParty;
import com.app.heartbound.services.lfg.LFGPartyService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/lfg/parties")
public class LFGPartyController {

    private final LFGPartyService partyService;
    private final SimpMessagingTemplate messagingTemplate;

    public LFGPartyController(LFGPartyService partyService, SimpMessagingTemplate messagingTemplate) {
        this.partyService = partyService;
        this.messagingTemplate = messagingTemplate;
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
    public void deleteParty(@PathVariable UUID id) {
        partyService.deleteParty(id);
        LFGPartyEventDTO event = LFGPartyEventDTO.builder()
              .eventType("PARTY_DELETED")
              .message("Party update: Party " + id + " has been deleted.")
              .build();
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
}
