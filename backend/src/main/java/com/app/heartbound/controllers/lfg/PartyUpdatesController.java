package com.app.heartbound.controllers.lfg;

import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;

/**
 * PartyUpdatesController is a WebSocket controller that handles incoming party update messages
 * from clients and broadcasts them to all subscribed clients.
 */
@Controller
public class PartyUpdatesController {

    /**
     * Receives a party update message on the /app/party/update destination and
     * broadcasts it to the /topic/party destination.
     *
     * @param updateMessage the incoming update message from the client
     * @return the processed update message to be broadcast to all subscribers
     */
    @MessageMapping("/party/update")
    @SendTo("/topic/party")
    public String broadcastPartyUpdate(String updateMessage) {
        // Process the update message as necessary (e.g., logging, additional formatting, etc.)
        return updateMessage;
    }
}
